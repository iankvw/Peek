package com.iank.im.peek.data.remote.gemini

import com.iank.im.peek.data.local.datastore.SecureKeyStorage
import com.iank.im.peek.data.local.db.dao.RemoteFileCacheDao
import com.iank.im.peek.data.local.db.entity.RemoteFileCacheEntity
import com.iank.im.peek.data.local.image.InternalImageStorage
import com.iank.im.peek.data.remote.common.ApiErrorMapper
import com.iank.im.peek.data.remote.gemini.dto.GeminiFileDisplayNameDto
import com.iank.im.peek.data.remote.gemini.dto.GeminiFileMetadataDto
import com.iank.im.peek.data.remote.gemini.dto.GeminiFileMetadataWrapperDto
import com.iank.im.peek.data.remote.gemini.dto.GeminiFileUploadStartRequest
import com.iank.im.peek.domain.model.ApiException
import com.iank.im.peek.domain.model.MessageAttachment
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

sealed interface GeminiUploadResult {
    data class ActiveFile(
        val fileName: String,
        val fileUri: String,
        val mimeType: String
    ) : GeminiUploadResult

    data object FallbackToBase64 : GeminiUploadResult
}

@Singleton
class GeminiFileUploader @Inject constructor(
    private val apiService: GeminiApiService,
    private val remoteFileCacheDao: RemoteFileCacheDao,
    private val secureKeyStorage: SecureKeyStorage,
    private val imageStorage: InternalImageStorage,
    private val errorMapper: ApiErrorMapper,
    private val json: Json
) {
    private val inFlightMutexes = ConcurrentHashMap<String, Mutex>()

    suspend fun ensureUploaded(
        attachment: MessageAttachment,
        payloadRelPath: String,
        payloadMimeType: String,
        apiKey: String
    ): GeminiUploadResult {
        val fingerprint = secureKeyStorage.computeCredentialFingerprint(apiKey)
        val lockKey = "${attachment.id}:$fingerprint"
        val mutex = inFlightMutexes.getOrPut(lockKey) { Mutex() }

        return mutex.withLock {
            val now = System.currentTimeMillis()
            val cached = remoteFileCacheDao.get(attachment.id, fingerprint)
            if (cached != null &&
                cached.payloadRevision == attachment.payloadRevision &&
                cached.state == STATE_ACTIVE &&
                (cached.expiresAt - now) > REUSE_EXPIRATION_MARGIN_MS
            ) {
                return@withLock GeminiUploadResult.ActiveFile(
                    fileName = cached.fileName,
                    fileUri = cached.fileUri,
                    mimeType = cached.mimeType
                )
            }

            val file = imageStorage.resolveIfExists(payloadRelPath)
                ?: throw ApiException(errorMapper.mapThrowable(IllegalStateException("Missing payload file")))

            uploadAndCache(
                attachment = attachment,
                file = file,
                mimeType = payloadMimeType,
                apiKey = apiKey,
                fingerprint = fingerprint
            )
        }
    }

    suspend fun invalidateCacheEntry(attachmentId: String, apiKey: String) {
        val fingerprint = secureKeyStorage.computeCredentialFingerprint(apiKey)
        remoteFileCacheDao.delete(attachmentId, fingerprint)
    }

    suspend fun deleteRemoteFileBestEffort(fileName: String, apiKey: String): Boolean {
        val normalizedName = fileName.trimStart('/')
        val url = "$GEMINI_FILES_BASE_URL/$normalizedName"
        return try {
            val response = apiService.deleteFile(url = url, apiKey = apiKey)
            response.isSuccessful || response.code() == 404
        } catch (_: Throwable) {
            false
        }
    }

    private suspend fun uploadAndCache(
        attachment: MessageAttachment,
        file: File,
        mimeType: String,
        apiKey: String,
        fingerprint: String
    ): GeminiUploadResult {
        val startJson = json.encodeToString(
            GeminiFileUploadStartRequest.serializer(),
            GeminiFileUploadStartRequest(GeminiFileDisplayNameDto(displayName = attachment.id))
        )
        val startResponse = try {
            apiService.startResumableUpload(
                url = GEMINI_UPLOAD_URL,
                apiKey = apiKey,
                contentLength = file.length(),
                contentType = mimeType,
                body = startJson.toRequestBody(JSON_MEDIA_TYPE)
            )
        } catch (t: Throwable) {
            throw ApiException(errorMapper.mapThrowable(t))
        }

        if (!startResponse.isSuccessful) {
            val raw = startResponse.errorBody()?.string()
            throw ApiException(errorMapper.mapHttpFailure(startResponse.code(), startResponse.headers(), raw))
        }

        val uploadUrl = startResponse.headers()["x-goog-upload-url"]
            ?: startResponse.headers()["X-Goog-Upload-URL"]
            ?: throw ApiException(
                errorMapper.mapHttpFailure(
                    500,
                    startResponse.headers(),
                    "Missing x-goog-upload-url header"
                )
            )

        val uploadResponse = try {
            apiService.uploadResumableBytes(
                uploadUrl = uploadUrl,
                apiKey = apiKey,
                offset = 0L,
                command = "upload, finalize",
                fileBody = file.asRequestBody(mimeType.toMediaType())
            )
        } catch (t: Throwable) {
            throw ApiException(errorMapper.mapThrowable(t))
        }

        if (!uploadResponse.isSuccessful) {
            val raw = uploadResponse.errorBody()?.string()
            throw ApiException(errorMapper.mapHttpFailure(uploadResponse.code(), uploadResponse.headers(), raw))
        }

        val uploadBodyText = uploadResponse.body()?.string().orEmpty()
        var metadata = parseMetadata(uploadBodyText)
            ?: throw ApiException(errorMapper.mapThrowable(IllegalStateException("Invalid Gemini file upload response")))

        var serverOffsetMs = computeServerOffsetMillis(uploadResponse.headers()["Date"])

        if (metadata.state == STATE_PROCESSING && !metadata.name.isNullOrBlank()) {
            metadata = pollUntilActive(metadata.name!!, apiKey) { dateHeader ->
                serverOffsetMs = computeServerOffsetMillis(dateHeader)
            }
        }

        val state = metadata.state ?: STATE_ACTIVE
        if (state == STATE_FAILED) {
            return GeminiUploadResult.FallbackToBase64
        }

        val fileName = metadata.name ?: return GeminiUploadResult.FallbackToBase64
        val fileUri = metadata.uri ?: return GeminiUploadResult.FallbackToBase64
        val now = System.currentTimeMillis()
        val rawExpirationMs = parseExpirationMillis(metadata.expirationTime)
            ?: (now + DEFAULT_FALLBACK_TTL_MS)
        val localAdjustedExpirationMs = rawExpirationMs - serverOffsetMs

        remoteFileCacheDao.upsert(
            RemoteFileCacheEntity(
                attachmentId = attachment.id,
                credentialFingerprint = fingerprint,
                payloadRevision = attachment.payloadRevision,
                fileName = fileName,
                fileUri = fileUri,
                mimeType = metadata.mimeType ?: mimeType,
                state = state,
                expiresAt = localAdjustedExpirationMs,
                createdAt = now
            )
        )

        return if (state == STATE_ACTIVE) {
            GeminiUploadResult.ActiveFile(
                fileName = fileName,
                fileUri = fileUri,
                mimeType = metadata.mimeType ?: mimeType
            )
        } else {
            GeminiUploadResult.FallbackToBase64
        }
    }

    private suspend fun pollUntilActive(
        fileName: String,
        apiKey: String,
        onDateHeader: (String?) -> Unit
    ): GeminiFileMetadataDto {
        val normalizedName = fileName.trimStart('/')
        val metadataUrl = "$GEMINI_FILES_BASE_URL/$normalizedName"
        var elapsedMs = 0L
        var backoffMs = 500L
        var latest = GeminiFileMetadataDto(name = fileName, state = STATE_PROCESSING)

        while (elapsedMs < MAX_POLL_WAIT_MS) {
            delay(backoffMs)
            elapsedMs += backoffMs
            backoffMs = (backoffMs * 2L).coerceAtMost(2500L)

            val response = try {
                apiService.getFileMetadata(url = metadataUrl, apiKey = apiKey)
            } catch (_: Throwable) {
                continue
            }
            if (!response.isSuccessful) {
                continue
            }
            onDateHeader(response.headers()["Date"])
            val body = response.body()?.string().orEmpty()
            val parsed = parseMetadata(body) ?: continue
            latest = parsed
            if (parsed.state == STATE_ACTIVE || parsed.state == STATE_FAILED) {
                return parsed
            }
        }
        return latest
    }

    private fun parseMetadata(body: String): GeminiFileMetadataDto? {
        if (body.isBlank()) return null
        return runCatching {
            val wrapped = json.decodeFromString(GeminiFileMetadataWrapperDto.serializer(), body)
            wrapped.file ?: json.decodeFromString(GeminiFileMetadataDto.serializer(), body)
        }.getOrNull()
    }

    private fun parseExpirationMillis(expirationTime: String?): Long? {
        if (expirationTime.isNullOrBlank()) return null
        return runCatching { Instant.parse(expirationTime.trim()).toEpochMilli() }.getOrNull()
    }

    private fun computeServerOffsetMillis(dateHeader: String?): Long {
        if (dateHeader.isNullOrBlank()) return 0L
        return runCatching {
            val serverInstant = ZonedDateTime.parse(dateHeader.trim(), DateTimeFormatter.RFC_1123_DATE_TIME)
                .toInstant()
                .toEpochMilli()
            serverInstant - System.currentTimeMillis()
        }.getOrDefault(0L)
    }

    companion object {
        const val GEMINI_API_BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
        private const val GEMINI_UPLOAD_URL = "https://generativelanguage.googleapis.com/upload/v1beta/files"
        private const val GEMINI_FILES_BASE_URL = "https://generativelanguage.googleapis.com/v1beta"

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val STATE_ACTIVE = "ACTIVE"
        private const val STATE_PROCESSING = "PROCESSING"
        private const val STATE_FAILED = "FAILED"
        private const val MAX_POLL_WAIT_MS = 10_000L
        private const val REUSE_EXPIRATION_MARGIN_MS = 2L * 60L * 60L * 1000L
        private const val DEFAULT_FALLBACK_TTL_MS = 47L * 60L * 60L * 1000L
    }
}

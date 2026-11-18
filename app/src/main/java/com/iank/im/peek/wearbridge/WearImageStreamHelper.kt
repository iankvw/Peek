package com.iank.im.peek.wearbridge

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.Wearable
import com.iank.im.peek.data.local.image.ImagePreprocessor
import com.iank.im.peek.data.local.image.InternalImageStorage
import com.iank.im.peek.di.ApplicationScope
import com.iank.im.peek.domain.model.ImageResolutionPreset
import com.iank.im.peek.domain.repository.ConversationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WearImageStreamHelper @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val conversationRepository: ConversationRepository,
    private val imageStorage: InternalImageStorage,
    private val imagePreprocessor: ImagePreprocessor,
    @param:ApplicationScope private val applicationScope: CoroutineScope
) {
    private val wearImageCacheDir: File by lazy {
        File(appContext.cacheDir, CACHE_DIR_NAME).apply { mkdirs() }
    }
    private val cacheMutex = Mutex()

    suspend fun handleImageRequest(
        sourceNodeId: String,
        request: WearImageRequest
    ): WearImageRequestAck {
        val channelPath = "${WearBridgeConstants.PATH_CHANNEL_IMAGE_PREFIX}${request.requestId}"
        val attachment = conversationRepository.getAttachment(request.attachmentId)
            ?: return WearImageRequestAck(
                accepted = false,
                requestId = request.requestId,
                channelPath = channelPath,
                errorReason = "ATTACHMENT_NOT_FOUND"
            )

        val isThumbnail = request.kind == WearBridgeConstants.IMAGE_KIND_THUMBNAIL
        val existingThumb = if (isThumbnail) {
            imageStorage.resolveIfExists(attachment.thumbnailRelPath)
        } else {
            null
        }
        val originalFile = imageStorage.resolveIfExists(attachment.originalRelPath)
        if (existingThumb == null && originalFile == null) {
            return WearImageRequestAck(
                accepted = false,
                requestId = request.requestId,
                channelPath = channelPath,
                errorReason = "IMAGE_FILE_MISSING"
            )
        }

        applicationScope.launch(Dispatchers.IO) {
            runCatching {
                val fileToStream = prepareFileForStreaming(
                    attachmentId = attachment.id,
                    payloadRevision = attachment.payloadRevision,
                    thumbnailRelPath = attachment.thumbnailRelPath,
                    originalRelPath = attachment.originalRelPath,
                    payloadRelPath = attachment.payloadRelPath,
                    payloadMaxSide = attachment.payloadMaxSide,
                    kind = request.kind,
                    qualityPreset = request.qualityPreset
                )
                streamFileOverChannel(sourceNodeId, channelPath, fileToStream)
                trimCacheIfNeeded(excludeFile = fileToStream)
            }.onFailure { t ->
                Log.w(TAG, "Failed streaming Wear image request ${request.requestId}", t)
            }
        }

        return WearImageRequestAck(
            accepted = true,
            requestId = request.requestId,
            channelPath = channelPath,
            mimeType = "image/webp"
        )
    }

    private suspend fun prepareFileForStreaming(
        attachmentId: String,
        payloadRevision: Int,
        thumbnailRelPath: String,
        originalRelPath: String,
        payloadRelPath: String?,
        payloadMaxSide: Int?,
        kind: String,
        qualityPreset: String
    ): File {
        if (kind == WearBridgeConstants.IMAGE_KIND_THUMBNAIL) {
            imageStorage.resolveIfExists(thumbnailRelPath)?.let { return it }
            val thumbCacheFile = File(wearImageCacheDir, "${attachmentId}_r${payloadRevision}_thumb.webp")
            cacheMutex.withLock {
                if (thumbCacheFile.exists() && thumbCacheFile.length() > 0L) {
                    thumbCacheFile.setLastModified(System.currentTimeMillis())
                    return thumbCacheFile
                }
            }
            val originalFile = imageStorage.resolveIfExists(originalRelPath)
                ?: throw IOException("Original missing for thumbnail $attachmentId")
            return cacheMutex.withLock {
                if (thumbCacheFile.exists() && thumbCacheFile.length() > 0L) {
                    thumbCacheFile.setLastModified(System.currentTimeMillis())
                    return@withLock thumbCacheFile
                }
                imagePreprocessor.encodeFileForWearCache(
                    sourceFile = originalFile,
                    destFile = thumbCacheFile,
                    targetMaxSide = ImagePreprocessor.THUMBNAIL_MAX_SIDE_PX,
                    quality = ImagePreprocessor.THUMBNAIL_WEBP_QUALITY
                ).also {
                    it.setLastModified(System.currentTimeMillis())
                }
            }
        }

        val targetMaxSide = resolveMaxSideForPreset(qualityPreset)
        if (payloadMaxSide == targetMaxSide) {
            imageStorage.resolveIfExists(payloadRelPath)?.let { return it }
        }

        val fullCacheFile = File(wearImageCacheDir, "${attachmentId}_r${payloadRevision}_${qualityPreset}.webp")
        cacheMutex.withLock {
            if (fullCacheFile.exists() && fullCacheFile.length() > 0L) {
                fullCacheFile.setLastModified(System.currentTimeMillis())
                return fullCacheFile
            }
        }
        val originalFile = imageStorage.resolveIfExists(originalRelPath)
            ?: throw IOException("Original missing for full image $attachmentId")
        return cacheMutex.withLock {
            if (fullCacheFile.exists() && fullCacheFile.length() > 0L) {
                fullCacheFile.setLastModified(System.currentTimeMillis())
                return@withLock fullCacheFile
            }
            imagePreprocessor.encodeFileForWearCache(
                sourceFile = originalFile,
                destFile = fullCacheFile,
                targetMaxSide = targetMaxSide,
                quality = ImagePreprocessor.PAYLOAD_WEBP_QUALITY
            ).also {
                it.setLastModified(System.currentTimeMillis())
            }
        }
    }

    private suspend fun streamFileOverChannel(
        nodeId: String,
        channelPath: String,
        file: File
    ) {
        val channelClient = Wearable.getChannelClient(appContext)
        val channel = withTimeout(CHANNEL_STREAM_TIMEOUT_MS) {
            channelClient.openChannel(nodeId, channelPath).await()
        }
        try {
            withTimeout(CHANNEL_STREAM_TIMEOUT_MS) {
                val outputStream = channelClient.getOutputStream(channel).await()
                outputStream.use { out ->
                    file.inputStream().use { input ->
                        val buffer = ByteArray(STREAM_BUFFER_BYTES)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            out.write(buffer, 0, read)
                        }
                        out.flush()
                    }
                }
            }
        } finally {
            withContext(NonCancellable) {
                runCatching { channelClient.close(channel).await() }
            }
        }
    }

    private fun resolveMaxSideForPreset(qualityPreset: String): Int = when (qualityPreset) {
        WearBridgeConstants.QUALITY_ORIGINAL -> ImageResolutionPreset.MAX_WEBP_DIMENSION
        WearBridgeConstants.QUALITY_HIGH_4000 -> 4000
        WearBridgeConstants.QUALITY_STANDARD_1000 -> 1000
        else -> 2000
    }

    private suspend fun trimCacheIfNeeded(excludeFile: File? = null) {
        cacheMutex.withLock {
            val files = wearImageCacheDir.listFiles()?.filter { it.isFile && !it.name.endsWith(".tmp") } ?: return
            var totalBytes = files.sumOf { it.length() }
            if (totalBytes <= MAX_CACHE_BYTES) return
            val sortedOldest = files.sortedBy { it.lastModified() }
            for (file in sortedOldest) {
                if (totalBytes <= MAX_CACHE_BYTES) break
                if (excludeFile != null && file.absolutePath == excludeFile.absolutePath) continue
                val len = file.length()
                if (file.delete()) {
                    totalBytes -= len
                }
            }
        }
    }

    companion object {
        private const val TAG = "WearImageStreamHelper"
        private const val CACHE_DIR_NAME = "wear_image_cache"
        private const val STREAM_BUFFER_BYTES = 16 * 1024
        private const val MAX_CACHE_BYTES = 64L * 1024L * 1024L
        private const val CHANNEL_STREAM_TIMEOUT_MS = 45_000L
    }
}

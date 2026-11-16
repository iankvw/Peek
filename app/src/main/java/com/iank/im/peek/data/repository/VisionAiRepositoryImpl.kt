package com.iank.im.peek.data.repository

import com.iank.im.peek.data.local.datastore.SecureKeyStorage
import com.iank.im.peek.data.local.db.dao.ApiPresetDao
import com.iank.im.peek.data.local.db.dao.FileDeletionQueueDao
import com.iank.im.peek.data.local.image.InternalImageStorage
import com.iank.im.peek.data.remote.common.ApiErrorMapper
import com.iank.im.peek.data.remote.common.JsonBodySegment
import com.iank.im.peek.data.remote.common.PayloadSizeEstimator
import com.iank.im.peek.data.remote.common.StreamingJsonBody
import com.iank.im.peek.data.remote.gemini.GeminiApiService
import com.iank.im.peek.data.remote.gemini.GeminiFileUploader
import com.iank.im.peek.data.remote.gemini.GeminiUploadResult
import com.iank.im.peek.data.remote.gemini.dto.GeminiGenerateContentResponse
import com.iank.im.peek.data.remote.gemini.dto.GeminiStoredMetadata
import com.iank.im.peek.data.remote.openai.BaseUrlNormalizationResult
import com.iank.im.peek.data.remote.openai.BaseUrlNormalizer
import com.iank.im.peek.data.remote.openai.OpenAiApiService
import com.iank.im.peek.data.remote.openai.dto.OpenAiChatCompletionResponse
import com.iank.im.peek.data.remote.openai.dto.OpenAiChatRequestHeaderDto
import com.iank.im.peek.data.remote.openai.dto.OpenAiMessageDto
import com.iank.im.peek.domain.model.ApiError
import com.iank.im.peek.domain.model.ApiException
import com.iank.im.peek.domain.model.ApiPreset
import com.iank.im.peek.domain.model.ApiProvider
import com.iank.im.peek.domain.model.AppSettings
import com.iank.im.peek.domain.model.ImageSendMethod
import com.iank.im.peek.domain.model.MessageAttachment
import com.iank.im.peek.domain.model.MessageRole
import com.iank.im.peek.domain.model.PreflightNotice
import com.iank.im.peek.domain.model.PromptPart
import com.iank.im.peek.domain.model.PromptTurn
import com.iank.im.peek.domain.model.VisionResponseResult
import com.iank.im.peek.domain.repository.VisionAiRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VisionAiRepositoryImpl @Inject constructor(
    private val geminiApiService: GeminiApiService,
    private val geminiFileUploader: GeminiFileUploader,
    private val openAiApiService: OpenAiApiService,
    private val imageStorage: InternalImageStorage,
    private val payloadSizeEstimator: PayloadSizeEstimator,
    private val errorMapper: ApiErrorMapper,
    private val secureKeyStorage: SecureKeyStorage,
    private val apiPresetDao: ApiPresetDao,
    private val fileDeletionQueueDao: FileDeletionQueueDao,
    private val json: Json
) : VisionAiRepository {

    override suspend fun generateVisionResponse(
        preset: ApiPreset,
        apiKey: String,
        systemPrompt: String,
        turns: List<PromptTurn>,
        settings: AppSettings,
        effectiveSendMethod: ImageSendMethod,
        onNotice: suspend (PreflightNotice) -> Unit
    ): VisionResponseResult {
        return when (preset.provider) {
            ApiProvider.GEMINI -> executeGeminiWithRecovery(
                preset = preset,
                apiKey = apiKey,
                systemPrompt = systemPrompt,
                turns = turns,
                sendMethod = effectiveSendMethod
            )
            ApiProvider.OPENAI -> executeOpenAiWithRetry(
                preset = preset,
                apiKey = apiKey,
                systemPrompt = systemPrompt,
                turns = turns,
                settings = settings
            )
        }
    }

    override suspend fun testConnection(preset: ApiPreset, apiKey: String): Result<Unit> {
        return when (preset.provider) {
            ApiProvider.GEMINI -> testGeminiConnection(preset, apiKey)
            ApiProvider.OPENAI -> testOpenAiConnection(preset, apiKey)
        }
    }

    override suspend fun generateImageCaption(
        preset: ApiPreset,
        apiKey: String,
        attachment: MessageAttachment,
        modelIdOverride: String?,
        settings: AppSettings,
        effectiveSendMethod: ImageSendMethod
    ): String? {
        val payloadPath = attachment.payloadRelPath ?: return null
        val captionModel = (modelIdOverride?.trim()?.takeIf { it.isNotBlank() } ?: preset.modelId.trim())
            .removePrefix("models/")
        val captionPreset = preset.copy(modelId = captionModel)
        val mimeType = preset.imagePayloadFormat.mimeType
        val singleTurn = listOf(
            PromptTurn(
                messageId = "caption_${attachment.id}",
                role = MessageRole.USER,
                parts = listOf(
                    PromptPart.ImageRef(
                        attachment = attachment,
                        payloadRelPath = payloadPath,
                        payloadMimeType = mimeType
                    ),
                    PromptPart.Text(CAPTION_PROMPT)
                ),
                presetIdUsed = preset.id,
                providerMetadataJson = null
            )
        )
        val result = when (captionPreset.provider) {
            ApiProvider.GEMINI -> executeGeminiOnce(
                preset = captionPreset,
                apiKey = apiKey,
                systemPrompt = "",
                turns = singleTurn,
                sendMethod = effectiveSendMethod,
                forceInlineBase64Ids = emptySet()
            ).first
            ApiProvider.OPENAI -> executeOpenAiOnce(
                preset = captionPreset,
                apiKey = apiKey,
                systemPrompt = "",
                turns = singleTurn,
                settings = settings
            )
        }
        return (result as? VisionResponseResult.Success)
            ?.textContent
            ?.trim()
            ?.take(MAX_CAPTION_CHARS)
            ?.takeIf { it.isNotBlank() }
    }

    override suspend fun processPendingRemoteDeletions() {
        val cutoff = System.currentTimeMillis() - REMOTE_DELETION_EXPIRY_MS
        fileDeletionQueueDao.removeExpiredRemoteDeletions(cutoff)
        val pending = fileDeletionQueueDao.getPendingRemoteDeletions()
        if (pending.isEmpty()) return

        val presets = apiPresetDao.getAll()
        val keyByFingerprint = mutableMapOf<String, String>()
        for (preset in presets) {
            if (preset.provider != ApiProvider.GEMINI) continue
            val key = secureKeyStorage.getApiKey(preset.id) ?: continue
            val fp = secureKeyStorage.computeCredentialFingerprint(key)
            keyByFingerprint[fp] = key
        }

        for (item in pending) {
            val apiKey = keyByFingerprint[item.credentialFingerprint]
            if (apiKey == null) {
                // Associated key no longer exists; remote file will auto-expire in 48h.
                fileDeletionQueueDao.removeRemoteDeletion(item.fileName, item.credentialFingerprint)
                continue
            }
            val deleted = geminiFileUploader.deleteRemoteFileBestEffort(item.fileName, apiKey)
            if (deleted) {
                fileDeletionQueueDao.removeRemoteDeletion(item.fileName, item.credentialFingerprint)
            }
        }
    }

    private suspend fun executeGeminiWithRecovery(
        preset: ApiPreset,
        apiKey: String,
        systemPrompt: String,
        turns: List<PromptTurn>,
        sendMethod: ImageSendMethod
    ): VisionResponseResult {
        var usedUploadIds = emptyList<String>()
        var forceInlineIds = emptySet<String>()
        var fileRetryAttempted = false

        while (true) {
            val (result, uploadedIds) = executeWithRetryBackoff {
                executeGeminiOnce(
                    preset = preset,
                    apiKey = apiKey,
                    systemPrompt = systemPrompt,
                    turns = turns,
                    sendMethod = sendMethod,
                    forceInlineBase64Ids = forceInlineIds
                )
            }
            usedUploadIds = uploadedIds

            if (result is VisionResponseResult.Failure &&
                result.error is ApiError.FileUnavailable &&
                usedUploadIds.isNotEmpty()
            ) {
                usedUploadIds.forEach { attachmentId ->
                    geminiFileUploader.invalidateCacheEntry(attachmentId, apiKey)
                }
                if (!fileRetryAttempted) {
                    fileRetryAttempted = true
                    continue
                }
                // One re-upload failed; fallback to inline Base64 if within payload limit
                val estimatedInline = payloadSizeEstimator.estimateInlineRequestBytes(turns, systemPrompt)
                val limit = payloadSizeEstimator.resolveMaxRequestBytes(preset, ImageSendMethod.BASE64)
                if (estimatedInline <= limit) {
                    forceInlineIds = usedUploadIds.toSet()
                    val (fallbackResult, _) = executeGeminiOnce(
                        preset = preset,
                        apiKey = apiKey,
                        systemPrompt = systemPrompt,
                        turns = turns,
                        sendMethod = ImageSendMethod.BASE64,
                        forceInlineBase64Ids = forceInlineIds
                    )
                    return fallbackResult
                }
            }
            return result
        }
    }

    private suspend fun executeGeminiOnce(
        preset: ApiPreset,
        apiKey: String,
        systemPrompt: String,
        turns: List<PromptTurn>,
        sendMethod: ImageSendMethod,
        forceInlineBase64Ids: Set<String>
    ): Pair<VisionResponseResult, List<String>> {
        val uploadedAttachmentIds = mutableListOf<String>()
        val resolvedPartsByMessage = mutableMapOf<String, List<ResolvedGeminiPart>>()

        for (turn in turns) {
            val parts = mutableListOf<ResolvedGeminiPart>()
            for (part in turn.parts) {
                when (part) {
                    is PromptPart.Text -> parts += ResolvedGeminiPart.Text(part.text)
                    is PromptPart.ImageRef -> {
                        val useFileApi = sendMethod == ImageSendMethod.FILE_API &&
                            part.attachment.id !in forceInlineBase64Ids
                        if (useFileApi) {
                            val uploadOutcome = try {
                                geminiFileUploader.ensureUploaded(
                                    attachment = part.attachment,
                                    payloadRelPath = part.payloadRelPath,
                                    payloadMimeType = part.payloadMimeType,
                                    apiKey = apiKey
                                )
                            } catch (e: ApiException) {
                                return VisionResponseResult.Failure(e.error) to uploadedAttachmentIds
                            }
                            when (uploadOutcome) {
                                is GeminiUploadResult.ActiveFile -> {
                                    uploadedAttachmentIds += part.attachment.id
                                    parts += ResolvedGeminiPart.FileUri(
                                        mimeType = uploadOutcome.mimeType,
                                        fileUri = uploadOutcome.fileUri
                                    )
                                }
                                GeminiUploadResult.FallbackToBase64 -> {
                                    val file = imageStorage.resolveIfExists(part.payloadRelPath)
                                        ?: return VisionResponseResult.Failure(
                                            ApiError.Unknown("Missing image payload file")
                                        ) to uploadedAttachmentIds
                                    parts += ResolvedGeminiPart.InlineFile(
                                        mimeType = part.payloadMimeType,
                                        file = file
                                    )
                                }
                            }
                        } else {
                            val file = imageStorage.resolveIfExists(part.payloadRelPath)
                                ?: return VisionResponseResult.Failure(
                                    ApiError.Unknown("Missing image payload file")
                                ) to uploadedAttachmentIds
                            parts += ResolvedGeminiPart.InlineFile(
                                mimeType = part.payloadMimeType,
                                file = file
                            )
                        }
                    }
                }
            }
            resolvedPartsByMessage[turn.messageId] = parts
        }

        val requestBody = buildGeminiStreamingBody(
            systemPrompt = systemPrompt,
            turns = turns,
            resolvedPartsByMessage = resolvedPartsByMessage,
            currentPresetId = preset.id
        )
        val normalizedModelId = preset.modelId.trim().removePrefix("models/")
        val url = "${GeminiFileUploader.GEMINI_API_BASE_URL}/models/$normalizedModelId:generateContent"

        val response = try {
            geminiApiService.generateContent(
                url = url,
                apiKey = apiKey,
                body = requestBody
            )
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            return VisionResponseResult.Failure(errorMapper.mapThrowable(t)) to uploadedAttachmentIds
        }

        if (!response.isSuccessful) {
            val rawErr = response.errorBody()?.string()
            val mapped = errorMapper.mapHttpFailure(response.code(), response.headers(), rawErr)
            return VisionResponseResult.Failure(mapped) to uploadedAttachmentIds
        }

        val rawBody = response.body()?.string().orEmpty()
        return parseGeminiResponse(rawBody) to uploadedAttachmentIds
    }

    private fun buildGeminiStreamingBody(
        systemPrompt: String,
        turns: List<PromptTurn>,
        resolvedPartsByMessage: Map<String, List<ResolvedGeminiPart>>,
        currentPresetId: String
    ): StreamingJsonBody {
        val segments = mutableListOf<JsonBodySegment>()
        fun raw(text: String) {
            segments += JsonBodySegment.RawBytes(text)
        }
        fun jsonStr(text: String) {
            segments += JsonBodySegment.RawBytes(json.encodeToString(String.serializer(), text))
        }

        raw("{")
        var hasRootField = false

        if (systemPrompt.isNotBlank()) {
            raw("\"system_instruction\":{\"parts\":[{\"text\":")
            jsonStr(systemPrompt)
            raw("}]}")
            hasRootField = true
        }

        if (hasRootField) raw(",")
        raw("\"contents\":[")

        turns.forEachIndexed { turnIdx, turn ->
            if (turnIdx > 0) raw(",")
            val role = when (turn.role) {
                MessageRole.USER -> "user"
                MessageRole.ASSISTANT -> "model"
            }
            raw("{\"role\":")
            jsonStr(role)
            raw(",\"parts\":[")

            val metadataForSameProvider = if (turn.role == MessageRole.ASSISTANT && turn.presetIdUsed == currentPresetId) {
                parseGeminiStoredMetadata(turn.providerMetadataJson)
            } else {
                null
            }

            if (metadataForSameProvider != null && metadataForSameProvider.rawModelParts.isNotEmpty()) {
                metadataForSameProvider.rawModelParts.forEachIndexed { idx, element ->
                    if (idx > 0) raw(",")
                    segments += JsonBodySegment.RawBytes(
                        json.encodeToString(JsonElement.serializer(), element)
                    )
                }
            } else {
                val parts = resolvedPartsByMessage[turn.messageId].orEmpty()
                if (parts.isEmpty()) {
                    raw("{\"text\":\"\"}")
                } else {
                    parts.forEachIndexed { pIdx, part ->
                        if (pIdx > 0) raw(",")
                        when (part) {
                            is ResolvedGeminiPart.Text -> {
                                raw("{\"text\":")
                                jsonStr(part.text)
                                raw("}")
                            }
                            is ResolvedGeminiPart.FileUri -> {
                                raw("{\"file_data\":{\"mime_type\":")
                                jsonStr(part.mimeType)
                                raw(",\"file_uri\":")
                                jsonStr(part.fileUri)
                                raw("}}")
                            }
                            is ResolvedGeminiPart.InlineFile -> {
                                raw("{\"inline_data\":{\"mime_type\":")
                                jsonStr(part.mimeType)
                                raw(",\"data\":\"")
                                segments += JsonBodySegment.Base64File(part.file)
                                raw("\"}}")
                            }
                        }
                    }
                }
            }
            raw("]}")
        }
        raw("]}")
        return StreamingJsonBody(segments)
    }

    private fun parseGeminiResponse(rawBody: String): VisionResponseResult {
        if (rawBody.isBlank()) {
            return VisionResponseResult.Failure(ApiError.Parse("Empty response body"))
        }
        val parsed = try {
            json.decodeFromString(GeminiGenerateContentResponse.serializer(), rawBody)
        } catch (t: Throwable) {
            return VisionResponseResult.Failure(
                ApiError.Parse(
                    snippet = rawBody.take(180),
                    detailMessage = t.message
                )
            )
        }

        if (parsed.promptFeedback?.blockReason != null) {
            return VisionResponseResult.Failure(
                ApiError.Blocked(parsed.promptFeedback.blockReasonMessage ?: parsed.promptFeedback.blockReason)
            )
        }

        val candidate = parsed.candidates.firstOrNull()
        if (candidate == null) {
            return VisionResponseResult.Failure(ApiError.Parse("No candidates returned"))
        }

        val finishReason = candidate.finishReason
        if (finishReason == "SAFETY" || finishReason == "RECITATION" || finishReason == "PROHIBITED_CONTENT") {
            return VisionResponseResult.Failure(
                ApiError.Blocked(candidate.finishMessage ?: finishReason)
            )
        }

        val rawParts = candidate.content?.parts.orEmpty()
        val visibleText = buildString {
            for (partEl in rawParts) {
                val obj = partEl as? JsonObject ?: continue
                val isThought = (obj["thought"] as? JsonPrimitive)?.booleanOrNull == true
                if (isThought) continue
                val text = (obj["text"] as? JsonPrimitive)?.contentOrNull
                if (!text.isNullOrEmpty()) {
                    append(text)
                }
            }
        }

        if (visibleText.isBlank() && rawParts.isEmpty()) {
            return VisionResponseResult.Failure(ApiError.Parse("Empty candidate content"))
        }

        val thoughtSignatures = rawParts.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            (obj["thoughtSignature"] as? JsonPrimitive)?.contentOrNull
                ?: (obj["thought_signature"] as? JsonPrimitive)?.contentOrNull
        }

        val metadataJson = if (rawParts.isNotEmpty() && thoughtSignatures.isNotEmpty()) {
            json.encodeToString(
                GeminiStoredMetadata.serializer(),
                GeminiStoredMetadata(
                    thoughtSignatures = thoughtSignatures,
                    rawModelParts = rawParts
                )
            )
        } else {
            null
        }

        return VisionResponseResult.Success(
            textContent = visibleText.ifBlank { candidate.finishMessage.orEmpty() },
            providerMetadataJson = metadataJson
        )
    }

    private fun parseGeminiStoredMetadata(metadataJson: String?): GeminiStoredMetadata? {
        if (metadataJson.isNullOrBlank()) return null
        return runCatching {
            val parsed = json.decodeFromString(GeminiStoredMetadata.serializer(), metadataJson)
            parsed.takeIf { it.provider == "GEMINI" }
        }.getOrNull()
    }

    private suspend fun executeOpenAiWithRetry(
        preset: ApiPreset,
        apiKey: String,
        systemPrompt: String,
        turns: List<PromptTurn>,
        settings: AppSettings
    ): VisionResponseResult {
        return executeWithRetryBackoff {
            executeOpenAiOnce(
                preset = preset,
                apiKey = apiKey,
                systemPrompt = systemPrompt,
                turns = turns,
                settings = settings
            ) to Unit
        }.first
    }

    private suspend fun executeOpenAiOnce(
        preset: ApiPreset,
        apiKey: String,
        systemPrompt: String,
        turns: List<PromptTurn>,
        settings: AppSettings
    ): VisionResponseResult {
        val urlInfo = BaseUrlNormalizer.normalize(
            rawInput = preset.customBaseUrl,
            customEnabled = preset.customBaseUrlEnabled
        )
        if (urlInfo !is BaseUrlNormalizationResult.Valid) {
            return VisionResponseResult.Failure(ApiError.NotFound("Invalid OpenAI Base URL"))
        }

        val requestBody = try {
            buildOpenAiStreamingBody(
                modelId = preset.modelId.trim(),
                systemPrompt = systemPrompt,
                turns = turns,
                settings = settings
            )
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            return VisionResponseResult.Failure(errorMapper.mapThrowable(t))
        }

        val authHeader = apiKey.trim().takeIf { it.isNotEmpty() }?.let { "Bearer $it" }

        val response = try {
            openAiApiService.createChatCompletion(
                url = urlInfo.chatCompletionsUrl,
                authHeader = authHeader,
                body = requestBody
            )
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            return VisionResponseResult.Failure(errorMapper.mapThrowable(t))
        }

        if (!response.isSuccessful) {
            val rawErr = response.errorBody()?.string()
            return VisionResponseResult.Failure(
                errorMapper.mapHttpFailure(response.code(), response.headers(), rawErr)
            )
        }

        val rawBody = response.body()?.string().orEmpty()
        return parseOpenAiResponse(rawBody)
    }

    private fun buildOpenAiStreamingBody(
        modelId: String,
        systemPrompt: String,
        turns: List<PromptTurn>,
        settings: AppSettings
    ): StreamingJsonBody {
        val segments = mutableListOf<JsonBodySegment>()
        fun raw(text: String) {
            segments += JsonBodySegment.RawBytes(text)
        }
        fun jsonStr(text: String) {
            segments += JsonBodySegment.RawBytes(json.encodeToString(String.serializer(), text))
        }

        raw("{\"model\":")
        jsonStr(modelId)
        raw(",\"messages\":[")

        var needComma = false
        if (systemPrompt.isNotBlank()) {
            val sysRole = if (settings.openAiUseDeveloperRole) "developer" else "system"
            raw("{\"role\":")
            jsonStr(sysRole)
            raw(",\"content\":")
            jsonStr(systemPrompt)
            raw("}")
            needComma = true
        }

        for (turn in turns) {
            if (needComma) raw(",")
            needComma = true

            val role = when (turn.role) {
                MessageRole.USER -> "user"
                MessageRole.ASSISTANT -> "assistant"
            }
            raw("{\"role\":")
            jsonStr(role)
            raw(",\"content\":")

            val imageParts = turn.parts.filterIsInstance<PromptPart.ImageRef>()
            val textParts = turn.parts.filterIsInstance<PromptPart.Text>()
            val combinedText = textParts.joinToString(separator = "\n") { it.text }

            if (imageParts.isEmpty() || turn.role == MessageRole.ASSISTANT) {
                jsonStr(combinedText)
            } else {
                raw("[")
                var partComma = false
                if (combinedText.isNotEmpty()) {
                    raw("{\"type\":\"text\",\"text\":")
                    jsonStr(combinedText)
                    raw("}")
                    partComma = true
                }
                for (img in imageParts) {
                    val file = imageStorage.resolveIfExists(img.payloadRelPath)
                        ?: throw IllegalStateException("Missing payload file: ${img.payloadRelPath}")
                    if (partComma) raw(",")
                    partComma = true
                    raw("{\"type\":\"image_url\",\"image_url\":{\"url\":\"data:${img.payloadMimeType};base64,")
                    segments += JsonBodySegment.Base64File(file)
                    raw("\",\"detail\":")
                    jsonStr(settings.openAiImageDetail.apiValue)
                    raw("}}")
                }
                raw("]")
            }
            raw("}")
        }
        raw("]}")
        return StreamingJsonBody(segments)
    }

    private fun parseOpenAiResponse(rawBody: String): VisionResponseResult {
        if (rawBody.isBlank()) {
            return VisionResponseResult.Failure(ApiError.Parse("Empty response body"))
        }
        val parsed = try {
            json.decodeFromString(OpenAiChatCompletionResponse.serializer(), rawBody)
        } catch (t: Throwable) {
            return VisionResponseResult.Failure(
                ApiError.Parse(
                    snippet = rawBody.take(180),
                    detailMessage = t.message
                )
            )
        }

        if (parsed.error != null) {
            return VisionResponseResult.Failure(ApiError.Unknown(parsed.error.message))
        }

        val choice = parsed.choices.firstOrNull()
        if (choice == null) {
            return VisionResponseResult.Failure(ApiError.Parse("Empty choices array"))
        }

        if (choice.finishReason == "content_filter") {
            return VisionResponseResult.Failure(
                ApiError.Blocked(choice.message?.refusal ?: "content_filter")
            )
        }

        val text = choice.message?.extractVisibleText().orEmpty()
        if (text.isBlank()) {
            val refusal = choice.message?.refusal
            return if (!refusal.isNullOrBlank()) {
                VisionResponseResult.Failure(ApiError.Blocked(refusal))
            } else {
                VisionResponseResult.Failure(ApiError.Parse("Empty completion content"))
            }
        }

        return VisionResponseResult.Success(textContent = text, providerMetadataJson = null)
    }

    private suspend fun testGeminiConnection(preset: ApiPreset, apiKey: String): Result<Unit> {
        val url = "${GeminiFileUploader.GEMINI_API_BASE_URL}/models?pageSize=1"
        return try {
            val response = geminiApiService.listModels(url = url, apiKey = apiKey)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                val rawErr = response.errorBody()?.string()
                Result.failure(
                    ApiException(errorMapper.mapHttpFailure(response.code(), response.headers(), rawErr))
                )
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            Result.failure(ApiException(errorMapper.mapThrowable(t)))
        }
    }

    private suspend fun testOpenAiConnection(preset: ApiPreset, apiKey: String): Result<Unit> {
        val urlInfo = BaseUrlNormalizer.normalize(preset.customBaseUrl, preset.customBaseUrlEnabled)
        if (urlInfo !is BaseUrlNormalizationResult.Valid) {
            return Result.failure(ApiException(ApiError.NotFound("Invalid Base URL")))
        }
        val authHeader = apiKey.trim().takeIf { it.isNotEmpty() }?.let { "Bearer $it" }

        return try {
            val modelsResp = openAiApiService.getModels(
                url = urlInfo.modelsUrl,
                authHeader = authHeader
            )
            if (modelsResp.isSuccessful) {
                return Result.success(Unit)
            }
            if (modelsResp.code() in setOf(401, 403)) {
                val err = modelsResp.errorBody()?.string()
                return Result.failure(
                    ApiException(errorMapper.mapHttpFailure(modelsResp.code(), modelsResp.headers(), err))
                )
            }

            // Some OpenAI-compatible proxies do not expose GET /models; test minimal chat request
            val pingBody = json.encodeToString(
                OpenAiChatRequestHeaderDto.serializer(),
                OpenAiChatRequestHeaderDto(
                    model = preset.modelId.trim().ifBlank { "gpt-4o-mini" },
                    messages = listOf(OpenAiMessageDto(role = "user", content = "hi")),
                    maxTokens = 1
                )
            ).toRequestBody(JSON_MEDIA_TYPE)
            val chatResp = openAiApiService.createChatCompletion(
                url = urlInfo.chatCompletionsUrl,
                authHeader = authHeader,
                body = pingBody
            )
            if (chatResp.isSuccessful) {
                Result.success(Unit)
            } else {
                val err = chatResp.errorBody()?.string()
                Result.failure(
                    ApiException(errorMapper.mapHttpFailure(chatResp.code(), chatResp.headers(), err))
                )
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            Result.failure(ApiException(errorMapper.mapThrowable(t)))
        }
    }

    private suspend fun <T> executeWithRetryBackoff(
        block: suspend () -> Pair<VisionResponseResult, T>
    ): Pair<VisionResponseResult, T> {
        var attempt = 0
        var backoffMs = INITIAL_RETRY_BACKOFF_MS
        while (true) {
            val resultPair = block()
            val result = resultPair.first
            if (result !is VisionResponseResult.Failure) {
                return resultPair
            }
            val err = result.error
            val shouldRetry = attempt < MAX_AUTO_RETRIES && (
                err is ApiError.RateLimited ||
                    (err is ApiError.Server && err.httpCode == 503) ||
                    err is ApiError.Network
                )
            if (!shouldRetry) {
                return resultPair
            }
            val delayMs = if (err is ApiError.RateLimited && err.retryAfterSeconds != null) {
                err.retryAfterSeconds * 1000L
            } else {
                backoffMs
            }
            delay(delayMs.coerceAtMost(MAX_RETRY_BACKOFF_MS))
            backoffMs *= 2L
            attempt++
        }
    }

    private sealed interface ResolvedGeminiPart {
        data class Text(val text: String) : ResolvedGeminiPart
        data class FileUri(val mimeType: String, val fileUri: String) : ResolvedGeminiPart
        data class InlineFile(val mimeType: String, val file: File) : ResolvedGeminiPart
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val MAX_AUTO_RETRIES = 2
        private const val INITIAL_RETRY_BACKOFF_MS = 1200L
        private const val MAX_RETRY_BACKOFF_MS = 15_000L
        private const val REMOTE_DELETION_EXPIRY_MS = 48L * 60L * 60L * 1000L
        private const val CAPTION_PROMPT =
            "Provide a concise one-sentence visual summary of this image for conversation memory."
        private const val MAX_CAPTION_CHARS = 280
    }
}

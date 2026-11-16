package com.iank.im.peek.domain.usecase

import com.iank.im.peek.R
import com.iank.im.peek.di.ApplicationScope
import com.iank.im.peek.domain.model.ApiError
import com.iank.im.peek.domain.model.ApiPreset
import com.iank.im.peek.domain.model.PendingAttachment
import com.iank.im.peek.domain.model.PreflightNotice
import com.iank.im.peek.domain.model.VisionResponseResult
import com.iank.im.peek.domain.repository.ConversationRepository
import com.iank.im.peek.domain.repository.SettingsRepository
import com.iank.im.peek.domain.repository.VisionAiRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SendChatMessageUseCase @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val settingsRepository: SettingsRepository,
    private val visionAiRepository: VisionAiRepository,
    private val commitAttachmentsUseCase: CommitAttachmentsUseCase,
    private val buildApiPayloadUseCase: BuildApiPayloadUseCase,
    private val generateImageCaptionUseCase: GenerateImageCaptionUseCase,
    private val activeSendRegistry: ActiveSendRegistry,
    private val resources: LocalizedResourceProvider,
    @param:ApplicationScope private val applicationScope: CoroutineScope
) {
    private val _preflightNotices = MutableSharedFlow<PreflightNotice>(extraBufferCapacity = 16)
    val preflightNotices: SharedFlow<PreflightNotice> = _preflightNotices.asSharedFlow()

    suspend fun sendNewTurn(
        conversationId: String?,
        userText: String,
        pendingAttachments: List<PendingAttachment>,
        preset: ApiPreset,
        apiKey: String
    ): String {
        val trimmedText = userText.trim()
        val isPhotoOnlyFirstTurn = conversationId == null &&
            trimmedText.isEmpty() &&
            pendingAttachments.isNotEmpty()
        val title = when {
            trimmedText.isNotEmpty() -> trimmedText.replace(Regex("\\s+"), " ").take(48)
            pendingAttachments.isNotEmpty() -> resources.getString(R.string.default_photo_conversation_title)
            else -> resources.getString(R.string.default_text_conversation_title)
        }

        val committedAttachments = commitAttachmentsUseCase.commitStaged(pendingAttachments)
        val userMessageId = UUID.randomUUID().toString()
        val assistantMessageId = UUID.randomUUID().toString()

        val resolvedConversationId = conversationRepository.commitTurn(
            conversationId = conversationId,
            conversationTitle = title,
            userMessageId = userMessageId,
            assistantMessageId = assistantMessageId,
            userText = trimmedText,
            committedAttachments = committedAttachments,
            presetId = preset.id,
            modelId = preset.modelId
        )

        launchVisionRequest(
            conversationId = resolvedConversationId,
            userMessageId = userMessageId,
            assistantMessageId = assistantMessageId,
            preset = preset,
            apiKey = apiKey,
            autoTitlePhotoInquiry = isPhotoOnlyFirstTurn
        )
        return resolvedConversationId
    }

    suspend fun retryAssistantMessage(
        assistantMessageId: String,
        preset: ApiPreset,
        apiKey: String
    ) {
        val userMessage = conversationRepository.prepareRetryAssistantMessage(
            assistantMessageId = assistantMessageId,
            presetId = preset.id,
            modelId = preset.modelId
        ) ?: return

        launchVisionRequest(
            conversationId = userMessage.conversationId,
            userMessageId = userMessage.id,
            assistantMessageId = assistantMessageId,
            preset = preset,
            apiKey = apiKey
        )
    }

    fun launchVisionRequestForExistingTurn(
        conversationId: String,
        userMessageId: String,
        assistantMessageId: String,
        preset: ApiPreset,
        apiKey: String
    ) {
        launchVisionRequest(
            conversationId = conversationId,
            userMessageId = userMessageId,
            assistantMessageId = assistantMessageId,
            preset = preset,
            apiKey = apiKey
        )
    }

    fun cancelConversationRequest(conversationId: String) {
        activeSendRegistry.cancel(conversationId)
    }

    private fun launchVisionRequest(
        conversationId: String,
        userMessageId: String,
        assistantMessageId: String,
        preset: ApiPreset,
        apiKey: String,
        autoTitlePhotoInquiry: Boolean = false
    ) {
        activeSendRegistry.cancel(conversationId)

        lateinit var launchedJob: Job
        launchedJob = applicationScope.launch {
            try {
                val settings = settingsRepository.getSettings()
                val firstBuild = buildApiPayloadUseCase(
                    conversationId = conversationId,
                    targetUserMessageId = userMessageId,
                    preset = preset,
                    settings = settings,
                    forceAggressiveDegrade = false
                )

                when (firstBuild) {
                    BuildPayloadResult.PayloadTooLarge -> {
                        conversationRepository.updateAssistantError(
                            messageId = assistantMessageId,
                            error = ApiError.PayloadTooLarge()
                        )
                        return@launch
                    }
                    is BuildPayloadResult.Ready -> {
                        firstBuild.notices.forEach { _preflightNotices.tryEmit(it) }

                        var response = visionAiRepository.generateVisionResponse(
                            preset = preset,
                            apiKey = apiKey,
                            systemPrompt = firstBuild.systemPrompt,
                            turns = firstBuild.turns,
                            settings = settings,
                            effectiveSendMethod = firstBuild.effectiveSendMethod
                        )

                        // Handle unexpected remote 413 once by forcing one step further degradation
                        if (response is VisionResponseResult.Failure &&
                            response.error is ApiError.PayloadTooLarge
                        ) {
                            val degradedBuild = buildApiPayloadUseCase(
                                conversationId = conversationId,
                                targetUserMessageId = userMessageId,
                                preset = preset,
                                settings = settings,
                                forceAggressiveDegrade = true
                            )
                            if (degradedBuild is BuildPayloadResult.Ready) {
                                degradedBuild.notices.forEach { _preflightNotices.tryEmit(it) }
                                response = visionAiRepository.generateVisionResponse(
                                    preset = preset,
                                    apiKey = apiKey,
                                    systemPrompt = degradedBuild.systemPrompt,
                                    turns = degradedBuild.turns,
                                    settings = settings,
                                    effectiveSendMethod = degradedBuild.effectiveSendMethod
                                )
                            }
                        }

                        when (val finalRes = response) {
                            is VisionResponseResult.Success -> {
                                conversationRepository.updateAssistantSuccess(
                                    messageId = assistantMessageId,
                                    textContent = finalRes.textContent,
                                    presetId = preset.id,
                                    modelId = preset.modelId,
                                    providerMetadataJson = finalRes.providerMetadataJson
                                )
                                val userMsg = conversationRepository.getMessage(userMessageId)
                                val currentConv = conversationRepository.getConversation(conversationId)
                                val defaultPhotoTitle = resources.getString(R.string.default_photo_conversation_title)
                                val shouldAutoTitle = autoTitlePhotoInquiry ||
                                    (userMsg != null &&
                                        userMsg.turnIndex == 1 &&
                                        userMsg.textContent.isBlank() &&
                                        userMsg.attachments.isNotEmpty() &&
                                        currentConv?.title == defaultPhotoTitle)

                                if (shouldAutoTitle) {
                                    val fallbackTitle = sanitizeAutoTitle(finalRes.textContent, 30)
                                    if (fallbackTitle.isNotBlank()) {
                                        conversationRepository.updateConversationTitle(
                                            conversationId = conversationId,
                                            title = fallbackTitle
                                        )
                                    }
                                }

                                if (settings.backgroundCaptionEnabled && userMsg != null && userMsg.attachments.isNotEmpty()) {
                                    applicationScope.launch {
                                        generateImageCaptionUseCase(
                                            conversationId = conversationId,
                                            attachments = userMsg.attachments,
                                            preset = preset,
                                            apiKey = apiKey,
                                            settings = settings,
                                            updateConversationTitleFromCaption = shouldAutoTitle
                                        )
                                    }
                                }
                            }
                            is VisionResponseResult.Failure -> {
                                conversationRepository.updateAssistantError(
                                    messageId = assistantMessageId,
                                    error = finalRes.error
                                )
                            }
                        }
                    }
                }
            } catch (ce: CancellationException) {
                conversationRepository.updateAssistantCancelled(assistantMessageId)
                throw ce
            } catch (t: Throwable) {
                conversationRepository.updateAssistantError(
                    messageId = assistantMessageId,
                    error = ApiError.Unknown(t.message)
                )
            } finally {
                activeSendRegistry.unregister(conversationId, assistantMessageId, launchedJob)
            }
        }
        activeSendRegistry.register(conversationId, assistantMessageId, launchedJob)
    }

    companion object {
        fun sanitizeAutoTitle(raw: String, maxLength: Int = 30): String {
            val withoutCodeBlocks = raw.replace(Regex("```[\\s\\S]*?```"), " ")
            val withoutLinks = withoutCodeBlocks.replace(Regex("\\[([^\\]]+)]\\([^)]*\\)"), "$1")
            val cleaned = withoutLinks
                .lineSequence()
                .map { line ->
                    line.trim()
                        .replace(Regex("^(#{1,6}|>|[-*+]|\\d+\\.)\\s+"), "")
                        .replace(Regex("[*_`~\\[\\]]"), "")
                        .trim()
                }
                .firstOrNull { it.isNotBlank() }
                .orEmpty()
                .replace(Regex("\\s+"), " ")
                .trim()

            if (cleaned.isEmpty()) return ""
            return if (cleaned.length > maxLength) {
                cleaned.take(maxLength).trimEnd()
            } else {
                cleaned
            }
        }
    }
}

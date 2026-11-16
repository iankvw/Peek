package com.iank.im.peek.domain.usecase

import com.iank.im.peek.R
import com.iank.im.peek.di.ApplicationScope
import com.iank.im.peek.domain.model.ApiError
import com.iank.im.peek.domain.model.ApiPreset
import com.iank.im.peek.domain.model.ApiProvider
import com.iank.im.peek.domain.model.ExecutionTarget
import com.iank.im.peek.domain.model.GroupRoutingMode
import com.iank.im.peek.domain.model.ModelGroup
import com.iank.im.peek.domain.model.PendingAttachment
import com.iank.im.peek.domain.model.PreflightNotice
import com.iank.im.peek.domain.model.RoundRobinSubMode
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
import kotlin.random.Random

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

    suspend fun prepareExecutionTarget(targetId: String?): ExecutionTargetResolution {
        val presets = settingsRepository.getPresets()
        val groups = settingsRepository.getModelGroups()
        val settings = settingsRepository.getSettings()

        if (presets.isEmpty()) {
            return ExecutionTargetResolution.NoPresetConfigured
        }

        val presetById = presets.associateBy { it.id }
        val groupById = groups.associateBy { it.id }

        val effectiveId = targetId?.takeIf { it in presetById || it in groupById }
            ?: settings.selectedPresetId?.takeIf { it in presetById || it in groupById }
            ?: presets.first().id

        val matchedGroup = groupById[effectiveId]
        if (matchedGroup != null) {
            return buildGroupExecutionTarget(matchedGroup, presetById)
        }

        val matchedPreset = presetById[effectiveId] ?: presets.first()
        if (matchedPreset.modelId.isBlank()) {
            return ExecutionTargetResolution.MissingModelId
        }
        val apiKey = settingsRepository.getApiKey(matchedPreset.id).orEmpty()
        val keyRequired = !(matchedPreset.provider == ApiProvider.OPENAI &&
            matchedPreset.customBaseUrlEnabled)
        if (keyRequired && apiKey.isBlank()) {
            return ExecutionTargetResolution.MissingApiKey
        }
        return ExecutionTargetResolution.Ready(
            ExecutionTarget.Single(preset = matchedPreset, apiKey = apiKey)
        )
    }

    private suspend fun buildGroupExecutionTarget(
        group: ModelGroup,
        presetById: Map<String, ApiPreset>
    ): ExecutionTargetResolution {
        val validMembers = mutableListOf<Pair<ApiPreset, String>>()
        var anyMissingKey = false
        var anyMissingModel = false

        for (memberId in group.memberPresetIds) {
            val preset = presetById[memberId] ?: continue
            if (preset.modelId.isBlank()) {
                anyMissingModel = true
                continue
            }
            val apiKey = settingsRepository.getApiKey(preset.id).orEmpty()
            val keyRequired = !(preset.provider == ApiProvider.OPENAI && preset.customBaseUrlEnabled)
            if (keyRequired && apiKey.isBlank()) {
                anyMissingKey = true
                continue
            }
            validMembers += (preset to apiKey)
        }

        if (validMembers.isEmpty()) {
            return when {
                anyMissingKey -> ExecutionTargetResolution.MissingApiKey
                anyMissingModel -> ExecutionTargetResolution.MissingModelId
                else -> ExecutionTargetResolution.NoPresetConfigured
            }
        }

        val orderedCandidates = when (group.routingMode) {
            GroupRoutingMode.FALLBACK -> validMembers
            GroupRoutingMode.ROUND_ROBIN -> {
                val startIndex = when (group.roundRobinSubMode) {
                    RoundRobinSubMode.SEQUENTIAL -> {
                        val nextIdx = (group.lastUsedIndex + 1).mod(validMembers.size)
                        settingsRepository.updateModelGroupLastUsedIndex(group.id, nextIdx)
                        nextIdx
                    }
                    RoundRobinSubMode.RANDOM -> Random.Default.nextInt(validMembers.size)
                }
                if (group.failoverOnRoundRobinError) {
                    (0 until validMembers.size).map { offset ->
                        validMembers[(startIndex + offset) % validMembers.size]
                    }
                } else {
                    listOf(validMembers[startIndex])
                }
            }
        }

        return ExecutionTargetResolution.Ready(
            ExecutionTarget.Group(group = group, orderedCandidates = orderedCandidates)
        )
    }

    suspend fun sendNewTurn(
        conversationId: String?,
        userText: String,
        pendingAttachments: List<PendingAttachment>,
        executionTarget: ExecutionTarget
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
        val firstCandidate = executionTarget.orderedCandidates.first().first

        val resolvedConversationId = conversationRepository.commitTurn(
            conversationId = conversationId,
            conversationTitle = title,
            activeTargetId = executionTarget.targetId,
            userMessageId = userMessageId,
            assistantMessageId = assistantMessageId,
            userText = trimmedText,
            committedAttachments = committedAttachments,
            presetId = firstCandidate.id,
            modelId = firstCandidate.modelId
        )

        launchVisionRequest(
            conversationId = resolvedConversationId,
            userMessageId = userMessageId,
            assistantMessageId = assistantMessageId,
            executionTarget = executionTarget,
            autoTitlePhotoInquiry = isPhotoOnlyFirstTurn
        )
        return resolvedConversationId
    }

    suspend fun retryAssistantMessage(
        assistantMessageId: String,
        executionTarget: ExecutionTarget
    ) {
        val firstCandidate = executionTarget.orderedCandidates.first().first
        val userMessage = conversationRepository.prepareRetryAssistantMessage(
            assistantMessageId = assistantMessageId,
            presetId = firstCandidate.id,
            modelId = firstCandidate.modelId
        ) ?: return

        launchVisionRequest(
            conversationId = userMessage.conversationId,
            userMessageId = userMessage.id,
            assistantMessageId = assistantMessageId,
            executionTarget = executionTarget
        )
    }

    fun launchVisionRequestForExistingTurn(
        conversationId: String,
        userMessageId: String,
        assistantMessageId: String,
        executionTarget: ExecutionTarget
    ) {
        launchVisionRequest(
            conversationId = conversationId,
            userMessageId = userMessageId,
            assistantMessageId = assistantMessageId,
            executionTarget = executionTarget
        )
    }

    fun cancelConversationRequest(conversationId: String) {
        activeSendRegistry.cancel(conversationId)
    }

    private fun launchVisionRequest(
        conversationId: String,
        userMessageId: String,
        assistantMessageId: String,
        executionTarget: ExecutionTarget,
        autoTitlePhotoInquiry: Boolean = false
    ) {
        activeSendRegistry.cancel(conversationId)

        lateinit var launchedJob: Job
        launchedJob = applicationScope.launch {
            try {
                val settings = settingsRepository.getSettings()
                val candidates = executionTarget.orderedCandidates
                var lastError: ApiError = ApiError.Unknown()

                for ((index, candidatePair) in candidates.withIndex()) {
                    val (preset, apiKey) = candidatePair
                    val firstBuild = buildApiPayloadUseCase(
                        conversationId = conversationId,
                        targetUserMessageId = userMessageId,
                        preset = preset,
                        settings = settings,
                        forceAggressiveDegrade = false,
                        forceInlineBase64 = executionTarget.forceInlineBase64
                    )

                    when (firstBuild) {
                        BuildPayloadResult.PayloadTooLarge -> {
                            lastError = ApiError.PayloadTooLarge()
                            if (index == candidates.lastIndex) {
                                conversationRepository.updateAssistantError(
                                    messageId = assistantMessageId,
                                    error = lastError
                                )
                            }
                            continue
                        }
                        is BuildPayloadResult.Ready -> {
                            if (index == 0) {
                                firstBuild.notices.forEach { _preflightNotices.tryEmit(it) }
                            }

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
                                    forceAggressiveDegrade = true,
                                    forceInlineBase64 = executionTarget.forceInlineBase64
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
                                    return@launch
                                }
                                is VisionResponseResult.Failure -> {
                                    lastError = finalRes.error
                                }
                            }
                        }
                    }
                }

                conversationRepository.updateAssistantError(
                    messageId = assistantMessageId,
                    error = lastError
                )
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

sealed interface ExecutionTargetResolution {
    data class Ready(val target: ExecutionTarget) : ExecutionTargetResolution
    data object NoPresetConfigured : ExecutionTargetResolution
    data object MissingModelId : ExecutionTargetResolution
    data object MissingApiKey : ExecutionTargetResolution
}

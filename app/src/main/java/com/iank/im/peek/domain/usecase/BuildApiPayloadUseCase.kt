package com.iank.im.peek.domain.usecase

import com.iank.im.peek.R
import com.iank.im.peek.data.remote.common.PayloadSizeEstimator
import com.iank.im.peek.domain.model.ApiPreset
import com.iank.im.peek.domain.model.ApiProvider
import com.iank.im.peek.domain.model.AppSettings
import com.iank.im.peek.domain.model.ChatMessage
import com.iank.im.peek.domain.model.ContextRetentionMode
import com.iank.im.peek.domain.model.ImageResolutionPreset
import com.iank.im.peek.domain.model.ImageSendMethod
import com.iank.im.peek.domain.model.MessageAttachment
import com.iank.im.peek.domain.model.MessageRole
import com.iank.im.peek.domain.model.MessageStatus
import com.iank.im.peek.domain.model.PreflightNotice
import com.iank.im.peek.domain.model.PromptPart
import com.iank.im.peek.domain.model.PromptTurn
import com.iank.im.peek.domain.repository.ConversationRepository
import javax.inject.Inject
import javax.inject.Singleton

sealed interface BuildPayloadResult {
    data class Ready(
        val turns: List<PromptTurn>,
        val effectiveSendMethod: ImageSendMethod,
        val systemPrompt: String,
        val notices: List<PreflightNotice>
    ) : BuildPayloadResult

    data object PayloadTooLarge : BuildPayloadResult
}

@Singleton
class BuildApiPayloadUseCase @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val ensurePayloadUseCase: EnsurePayloadUseCase,
    private val payloadSizeEstimator: PayloadSizeEstimator,
    private val resources: LocalizedResourceProvider
) {
    suspend operator fun invoke(
        conversationId: String,
        targetUserMessageId: String,
        preset: ApiPreset,
        settings: AppSettings,
        forceAggressiveDegrade: Boolean = false
    ): BuildPayloadResult {
        val conversation = conversationRepository.getConversation(conversationId)
        val allMessages = conversationRepository.getMessages(conversationId)
        val systemPrompt = settings.systemPrompt.ifBlank {
            resources.getString(R.string.default_system_prompt)
        }

        // Step 0: History sanitization
        val groupedByTurn = allMessages
            .sortedBy { it.position }
            .groupBy { it.turnIndex }
            .toSortedMap()

        data class SanitizedTurn(
            val turnIndex: Int,
            val userMessage: ChatMessage,
            val assistantMessage: ChatMessage?
        )

        val sanitizedTurns = mutableListOf<SanitizedTurn>()
        for ((turnIdx, msgs) in groupedByTurn) {
            val userMsg = msgs.firstOrNull { it.role == MessageRole.USER } ?: continue
            val assistantMsg = msgs.firstOrNull { it.role == MessageRole.ASSISTANT }
            if (userMsg.id == targetUserMessageId) {
                sanitizedTurns += SanitizedTurn(turnIdx, userMsg, null)
            } else if (assistantMsg != null && assistantMsg.status == MessageStatus.SUCCESS) {
                sanitizedTurns += SanitizedTurn(turnIdx, userMsg, assistantMsg)
            }
        }

        // Step 1: Context retention filtering (always starts on USER)
        val windowedTurns = when (val mode = settings.contextRetentionMode) {
            ContextRetentionMode.All -> sanitizedTurns
            is ContextRetentionMode.SlidingWindow -> {
                val n = mode.turns.coerceAtLeast(1)
                if (sanitizedTurns.size <= n) sanitizedTurns else sanitizedTurns.takeLast(n)
            }
        }

        // Step 2: Determine which attachments keep image binaries vs text replacement
        val recentImageTurnIndices = windowedTurns
            .filter { it.userMessage.attachments.isNotEmpty() }
            .takeLast(settings.keepRecentImageTurns.coerceAtLeast(1))
            .map { it.turnIndex }
            .toSet()

        val notices = mutableListOf<PreflightNotice>()
        var effectiveSendMethod = preset.effectiveSendMethod(settings.imageSendMethod)
        val conversationOverride = conversation?.overrideResolutionPreset

        data class MutableImageState(
            val turnIndex: Int,
            val attachment: MessageAttachment,
            var useTextReplacement: Boolean,
            var resolvedAttachment: MessageAttachment? = null
        )

        val imageStates = mutableMapOf<String, MutableImageState>()
        for (turn in windowedTurns) {
            val isRecent = turn.turnIndex in recentImageTurnIndices
            for (att in turn.userMessage.attachments) {
                val shouldKeepImage = !att.isTextReplaced &&
                    (isRecent || !settings.autoTextReplaceOldImages)
                imageStates[att.id] = MutableImageState(
                    turnIndex = turn.turnIndex,
                    attachment = att,
                    useTextReplacement = !shouldKeepImage
                )
            }
        }

        // Ensure payload files ONLY for attachments that actually stay as images
        for (state in imageStates.values) {
            if (!state.useTextReplacement) {
                state.resolvedAttachment = ensurePayloadUseCase(
                    attachment = state.attachment,
                    conversationOverride = conversationOverride,
                    globalDefault = settings.defaultResolutionPreset,
                    payloadFormat = preset.imagePayloadFormat
                )
            }
        }

        fun buildTurns(): List<PromptTurn> {
            val result = mutableListOf<PromptTurn>()
            for (turn in windowedTurns) {
                val userParts = mutableListOf<PromptPart>()
                if (turn.userMessage.textContent.isNotBlank()) {
                    userParts += PromptPart.Text(turn.userMessage.textContent)
                }
                for (att in turn.userMessage.attachments) {
                    val st = imageStates[att.id] ?: continue
                    val resolved = st.resolvedAttachment ?: st.attachment
                    if (!st.useTextReplacement && resolved.payloadRelPath != null) {
                        userParts += PromptPart.ImageRef(
                            attachment = resolved,
                            payloadRelPath = resolved.payloadRelPath,
                            payloadMimeType = preset.imagePayloadFormat.mimeType
                        )
                    } else {
                        userParts += PromptPart.Text(
                            buildTextReplacement(
                                attachment = resolved,
                                userMessage = turn.userMessage,
                                assistantMessage = turn.assistantMessage
                            )
                        )
                    }
                }
                if (userParts.isEmpty()) {
                    userParts += PromptPart.Text(" ")
                }
                result += PromptTurn(
                    messageId = turn.userMessage.id,
                    role = MessageRole.USER,
                    parts = userParts,
                    presetIdUsed = turn.userMessage.presetIdUsed,
                    providerMetadataJson = null
                )

                turn.assistantMessage?.let { assistant ->
                    result += PromptTurn(
                        messageId = assistant.id,
                        role = MessageRole.ASSISTANT,
                        parts = listOf(PromptPart.Text(assistant.textContent)),
                        presetIdUsed = assistant.presetIdUsed,
                        providerMetadataJson = assistant.providerMetadataJson
                    )
                }
            }
            return result
        }

        fun currentEstimatedBytes(turns: List<PromptTurn>): Long {
            return if (preset.provider == ApiProvider.GEMINI && effectiveSendMethod == ImageSendMethod.FILE_API) {
                // File URI references are tiny JSON metadata; count text & JSON overhead only
                val textOnlyTurns = turns.map { t ->
                    t.copy(parts = t.parts.filterIsInstance<PromptPart.Text>())
                }
                payloadSizeEstimator.estimateInlineRequestBytes(textOnlyTurns, systemPrompt) +
                    (turns.sumOf { t -> t.parts.count { it is PromptPart.ImageRef } } * 256L)
            } else {
                payloadSizeEstimator.estimateInlineRequestBytes(turns, systemPrompt)
            }
        }

        // Step 3: Preflight size estimation & 4-stage degradation
        var currentTurns = buildTurns()
        var maxBytes = payloadSizeEstimator.resolveMaxRequestBytes(preset, effectiveSendMethod)

        if (forceAggressiveDegrade || currentEstimatedBytes(currentTurns) > maxBytes) {
            // Stage 1: Switch Gemini BASE64 -> FILE_API
            if (preset.provider == ApiProvider.GEMINI && effectiveSendMethod == ImageSendMethod.BASE64) {
                effectiveSendMethod = ImageSendMethod.FILE_API
                maxBytes = payloadSizeEstimator.resolveMaxRequestBytes(preset, effectiveSendMethod)
                notices += PreflightNotice.SwitchedToFileApi
                currentTurns = buildTurns()
            }

            // Stage 2: Replace older images with text summaries
            if (forceAggressiveDegrade || currentEstimatedBytes(currentTurns) > maxBytes) {
                val activeImageStates = imageStates.values
                    .filter { !it.useTextReplacement }
                    .sortedBy { it.turnIndex }
                var replacedAny = false
                for (candidate in activeImageStates) {
                    val remainingActiveCount = imageStates.values.count { !it.useTextReplacement }
                    if (remainingActiveCount <= 1 && !forceAggressiveDegrade) break
                    if (candidate.turnIndex == windowedTurns.lastOrNull()?.turnIndex && remainingActiveCount <= 1) {
                        break
                    }
                    candidate.useTextReplacement = true
                    replacedAny = true
                    currentTurns = buildTurns()
                    if (!forceAggressiveDegrade && currentEstimatedBytes(currentTurns) <= maxBytes) break
                }
                if (replacedAny) {
                    notices += PreflightNotice.ReplacedOlderImagesWithText
                }
            }

            // Stage 3: Downscale remaining images step-by-step (e.g. >4000 -> 4000 -> 2000 -> 1000)
            if (forceAggressiveDegrade || currentEstimatedBytes(currentTurns) > maxBytes) {
                var lastDownscaledPx: Int? = null
                while (forceAggressiveDegrade || currentEstimatedBytes(currentTurns) > maxBytes) {
                    val activeStates = imageStates.values.filter { !it.useTextReplacement }
                    if (activeStates.isEmpty()) break

                    val currentMax = activeStates.maxOfOrNull {
                        it.resolvedAttachment?.payloadMaxSide ?: 2000
                    } ?: break

                    val nextPreset = ImageResolutionPreset.lowerStep(currentMax) ?: break
                    val nextMaxSide = nextPreset.maxSidePx ?: break

                    for (state in activeStates) {
                        val currentSide = state.resolvedAttachment?.payloadMaxSide ?: Int.MAX_VALUE
                        if (currentSide > nextMaxSide) {
                            state.resolvedAttachment = ensurePayloadUseCase(
                                attachment = state.attachment,
                                conversationOverride = conversationOverride,
                                globalDefault = settings.defaultResolutionPreset,
                                payloadFormat = preset.imagePayloadFormat,
                                maxSideOverride = nextMaxSide
                            )
                        }
                    }
                    lastDownscaledPx = nextMaxSide
                    currentTurns = buildTurns()
                    if (forceAggressiveDegrade && currentEstimatedBytes(currentTurns) <= maxBytes) {
                        break
                    }
                }
                lastDownscaledPx?.let { notices += PreflightNotice.DownscaledImages(it) }
            }

            // Stage 4: Still exceeds limit -> block cleanly
            if (currentEstimatedBytes(currentTurns) > maxBytes) {
                return BuildPayloadResult.PayloadTooLarge
            }
        }

        return BuildPayloadResult.Ready(
            turns = currentTurns,
            effectiveSendMethod = effectiveSendMethod,
            systemPrompt = systemPrompt,
            notices = notices.distinct()
        )
    }

    private fun buildTextReplacement(
        attachment: MessageAttachment,
        userMessage: ChatMessage,
        assistantMessage: ChatMessage?
    ): String {
        val caption = attachment.imageCaption?.trim()
        if (!caption.isNullOrBlank()) {
            return resources.getString(R.string.context_image_summary_template, caption)
        }
        val questionPart = userMessage.textContent.trim().take(120)
        val answerPart = assistantMessage?.textContent?.trim()?.take(200).orEmpty()
        val combinedContext = buildString {
            if (questionPart.isNotEmpty()) append(questionPart)
            if (answerPart.isNotEmpty()) {
                if (isNotEmpty()) append(" / ")
                append(answerPart)
            }
            if (isEmpty()) append("image")
        }
        return resources.getString(R.string.context_image_question_template, combinedContext)
    }
}

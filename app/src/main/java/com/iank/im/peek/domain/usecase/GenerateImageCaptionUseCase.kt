package com.iank.im.peek.domain.usecase

import com.iank.im.peek.domain.model.ApiPreset
import com.iank.im.peek.domain.model.AppSettings
import com.iank.im.peek.domain.model.MessageAttachment
import com.iank.im.peek.domain.repository.ConversationRepository
import com.iank.im.peek.domain.repository.VisionAiRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GenerateImageCaptionUseCase @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val ensurePayloadUseCase: EnsurePayloadUseCase,
    private val visionAiRepository: VisionAiRepository
) {
    suspend operator fun invoke(
        conversationId: String,
        attachments: List<MessageAttachment>,
        preset: ApiPreset,
        apiKey: String,
        settings: AppSettings
    ) {
        if (!settings.backgroundCaptionEnabled || attachments.isEmpty()) return
        val conversationOverride = conversationRepository.getConversation(conversationId)?.overrideResolutionPreset
        val modelOverride = settings.captionModelId.trim().takeIf { it.isNotBlank() }

        for (attachment in attachments) {
            if (!attachment.imageCaption.isNullOrBlank()) continue
            runCatching {
                val ensured = ensurePayloadUseCase(
                    attachment = attachment,
                    conversationOverride = conversationOverride,
                    globalDefault = settings.defaultResolutionPreset,
                    payloadFormat = preset.imagePayloadFormat
                )
                val caption = visionAiRepository.generateImageCaption(
                    preset = preset,
                    apiKey = apiKey,
                    attachment = ensured,
                    modelIdOverride = modelOverride
                )
                if (!caption.isNullOrBlank()) {
                    conversationRepository.updateAttachmentCaption(attachment.id, caption)
                }
            }
        }
    }
}

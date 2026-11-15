package com.iank.im.peek.domain.usecase

import com.iank.im.peek.data.local.image.ImagePreprocessor
import com.iank.im.peek.data.local.image.InternalImageStorage
import com.iank.im.peek.domain.model.ImagePayloadFormat
import com.iank.im.peek.domain.model.ImageResolutionPreset
import com.iank.im.peek.domain.model.MessageAttachment
import com.iank.im.peek.domain.repository.ConversationRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EnsurePayloadUseCase @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val imagePreprocessor: ImagePreprocessor,
    private val imageStorage: InternalImageStorage
) {
    private val attachmentMutexes = ConcurrentHashMap<String, Mutex>()

    suspend operator fun invoke(
        attachment: MessageAttachment,
        conversationOverride: ImageResolutionPreset?,
        globalDefault: ImageResolutionPreset,
        payloadFormat: ImagePayloadFormat,
        maxSideOverride: Int? = null
    ): MessageAttachment {
        val mutex = attachmentMutexes.getOrPut(attachment.id) { Mutex() }
        return mutex.withLock {
            val latest = conversationRepository.getAttachment(attachment.id) ?: attachment
            val targetMaxSide = maxSideOverride ?: ImageResolutionPreset.resolveMaxSidePx(
                attachmentPreset = latest.requestedPreset,
                conversationOverride = conversationOverride,
                globalDefault = globalDefault
            )

            val existingPath = latest.payloadRelPath
            val existingFile = imageStorage.resolveIfExists(existingPath)
            val formatMatches = existingPath != null &&
                existingPath.endsWith(".${payloadFormat.fileExtension}", ignoreCase = true)

            if (existingFile != null && latest.payloadMaxSide == targetMaxSide && formatMatches) {
                return@withLock latest
            }

            val nextRevision = latest.payloadRevision + 1
            val encoded = imagePreprocessor.createPayload(
                attachmentId = latest.id,
                originalRelPath = latest.originalRelPath,
                targetMaxSide = targetMaxSide,
                revision = nextRevision,
                format = payloadFormat
            )

            conversationRepository.updateAttachmentPayload(
                attachmentId = latest.id,
                newPayloadRelPath = encoded.payloadRelPath,
                newPayloadMaxSide = encoded.actualMaxSide,
                expectedPreviousRevision = latest.payloadRevision
            )

            conversationRepository.getAttachment(latest.id) ?: latest.copy(
                payloadRelPath = encoded.payloadRelPath,
                payloadMaxSide = encoded.actualMaxSide,
                payloadRevision = nextRevision
            )
        }
    }
}

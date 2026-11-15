package com.iank.im.peek.domain.usecase

import com.iank.im.peek.di.ApplicationScope
import com.iank.im.peek.domain.model.ImageResolutionPreset
import com.iank.im.peek.domain.repository.ConversationRepository
import com.iank.im.peek.domain.repository.VisionAiRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReprocessAttachmentResolutionUseCase @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val visionAiRepository: VisionAiRepository,
    @param:ApplicationScope private val applicationScope: CoroutineScope
) {
    private val _isUpdatingResolution = MutableStateFlow(false)
    val isUpdatingResolution: StateFlow<Boolean> = _isUpdatingResolution.asStateFlow()

    suspend fun applyToSingleAttachment(
        attachmentId: String,
        preset: ImageResolutionPreset
    ) {
        _isUpdatingResolution.value = true
        try {
            conversationRepository.updateSingleAttachmentResolution(attachmentId, preset)
            applicationScope.launch {
                visionAiRepository.processPendingRemoteDeletions()
            }
        } finally {
            _isUpdatingResolution.value = false
        }
    }

    suspend fun applyToConversation(
        conversationId: String,
        preset: ImageResolutionPreset
    ) {
        _isUpdatingResolution.value = true
        try {
            conversationRepository.updateConversationResolution(conversationId, preset)
            applicationScope.launch {
                visionAiRepository.processPendingRemoteDeletions()
            }
        } finally {
            _isUpdatingResolution.value = false
        }
    }
}

package com.iank.im.peek.domain.usecase

import com.iank.im.peek.di.ApplicationScope
import com.iank.im.peek.domain.repository.ConversationRepository
import com.iank.im.peek.domain.repository.VisionAiRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeleteConversationUseCase @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val visionAiRepository: VisionAiRepository,
    private val activeSendRegistry: ActiveSendRegistry,
    @param:ApplicationScope private val applicationScope: CoroutineScope
) {
    suspend operator fun invoke(conversationId: String) {
        activeSendRegistry.cancel(conversationId)
        conversationRepository.deleteConversation(conversationId)
        applicationScope.launch {
            visionAiRepository.processPendingRemoteDeletions()
        }
    }
}

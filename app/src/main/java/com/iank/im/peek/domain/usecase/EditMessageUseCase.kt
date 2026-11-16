package com.iank.im.peek.domain.usecase

import com.iank.im.peek.di.ApplicationScope
import com.iank.im.peek.domain.model.ExecutionTarget
import com.iank.im.peek.domain.model.MessageRole
import com.iank.im.peek.domain.repository.ConversationRepository
import com.iank.im.peek.domain.repository.VisionAiRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EditMessageUseCase @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val sendChatMessageUseCase: SendChatMessageUseCase,
    private val visionAiRepository: VisionAiRepository,
    @param:ApplicationScope private val applicationScope: CoroutineScope
) {
    suspend operator fun invoke(
        messageId: String,
        newText: String,
        executionTarget: ExecutionTarget?
    ) {
        val message = conversationRepository.getMessage(messageId) ?: return
        val trimmed = newText.trim()

        when (message.role) {
            MessageRole.ASSISTANT -> {
                conversationRepository.updateMessageText(messageId, trimmed)
            }
            MessageRole.USER -> {
                val target = executionTarget ?: return
                val firstCandidate = target.orderedCandidates.first().first
                sendChatMessageUseCase.cancelConversationRequest(message.conversationId)

                val assistantMessageId = UUID.randomUUID().toString()
                val conversationId = conversationRepository.truncateAfterAndReplaceUserMessage(
                    messageId = messageId,
                    newText = trimmed,
                    assistantMessageId = assistantMessageId,
                    presetId = firstCandidate.id,
                    modelId = firstCandidate.modelId
                ) ?: return

                applicationScope.launch {
                    visionAiRepository.processPendingRemoteDeletions()
                }

                sendChatMessageUseCase.launchVisionRequestForExistingTurn(
                    conversationId = conversationId,
                    userMessageId = message.id,
                    assistantMessageId = assistantMessageId,
                    executionTarget = target
                )
            }
        }
    }
}

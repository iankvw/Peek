package com.iank.im.peek.domain.repository

import com.iank.im.peek.domain.model.ApiError
import com.iank.im.peek.domain.model.ChatMessage
import com.iank.im.peek.domain.model.CommittedAttachmentInput
import com.iank.im.peek.domain.model.Conversation
import com.iank.im.peek.domain.model.ImageResolutionPreset
import com.iank.im.peek.domain.model.MessageAttachment
import kotlinx.coroutines.flow.Flow

interface ConversationRepository {
    fun observeConversations(query: String): Flow<List<Conversation>>
    fun observeConversation(conversationId: String): Flow<Conversation?>
    suspend fun getConversation(conversationId: String): Conversation?

    fun observeMessages(conversationId: String): Flow<List<ChatMessage>>
    suspend fun getMessages(conversationId: String): List<ChatMessage>
    suspend fun getMessage(messageId: String): ChatMessage?
    suspend fun getAttachment(attachmentId: String): MessageAttachment?

    suspend fun commitTurn(
        conversationId: String?,
        conversationTitle: String,
        activeTargetId: String?,
        userMessageId: String,
        assistantMessageId: String,
        userText: String,
        committedAttachments: List<CommittedAttachmentInput>,
        presetId: String,
        modelId: String
    ): String

    suspend fun prepareRetryAssistantMessage(
        assistantMessageId: String,
        presetId: String,
        modelId: String
    ): ChatMessage?

    suspend fun updateAssistantSuccess(
        messageId: String,
        textContent: String,
        presetId: String,
        modelId: String,
        servedGroupName: String?,
        servedPresetName: String?,
        servedModelName: String?,
        providerMetadataJson: String?
    )

    suspend fun updateAssistantError(messageId: String, error: ApiError)
    suspend fun updateAssistantCancelled(messageId: String)
    suspend fun updateMessageText(messageId: String, newText: String)

    suspend fun truncateAfterAndReplaceUserMessage(
        messageId: String,
        newText: String,
        assistantMessageId: String,
        presetId: String,
        modelId: String
    ): String?

    suspend fun updateAttachmentPayload(
        attachmentId: String,
        newPayloadRelPath: String,
        newPayloadMaxSide: Int,
        expectedPreviousRevision: Int
    ): Boolean

    suspend fun replaceAttachment(
        attachmentId: String,
        newOriginalRelPath: String,
        newMimeType: String,
        newWidth: Int,
        newHeight: Int,
        newThumbnailRelPath: String
    ): Boolean

    suspend fun toggleAttachmentTextReplaced(attachmentId: String)
    suspend fun updateAttachmentCaption(attachmentId: String, caption: String)
    suspend fun updateConversationTitle(conversationId: String, title: String)
    suspend fun updateConversationActiveTarget(conversationId: String, targetId: String?)
    suspend fun updateSingleAttachmentResolution(attachmentId: String, preset: ImageResolutionPreset)
    suspend fun updateConversationResolution(conversationId: String, preset: ImageResolutionPreset)
    suspend fun deleteConversation(conversationId: String)
    suspend fun processPendingFileDeletions()
}

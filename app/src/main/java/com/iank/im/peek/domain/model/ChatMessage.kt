package com.iank.im.peek.domain.model

enum class MessageRole {
    USER,
    ASSISTANT
}

enum class MessageStatus {
    LOADING,
    SUCCESS,
    ERROR,
    CANCELLED
}

data class ChatMessage(
    val id: String,
    val conversationId: String,
    val position: Int,
    val turnIndex: Int,
    val role: MessageRole,
    val textContent: String,
    val status: MessageStatus,
    val errorCode: String?,
    val errorMessage: String?,
    val presetIdUsed: String?,
    val modelIdUsed: String?,
    val providerMetadataJson: String?,
    val createdAt: Long,
    val attachments: List<MessageAttachment> = emptyList()
)

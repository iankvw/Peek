package com.iank.im.peek.wear.domain.model

enum class WearMessageRole {
    USER,
    ASSISTANT;

    companion object {
        fun fromWire(value: String): WearMessageRole =
            if (value.equals("USER", ignoreCase = true)) USER else ASSISTANT
    }
}

enum class WearMessageStatus {
    LOADING,
    SUCCESS,
    ERROR,
    CANCELLED;

    companion object {
        fun fromWire(value: String): WearMessageStatus = when (value.uppercase()) {
            "LOADING" -> LOADING
            "SUCCESS" -> SUCCESS
            "ERROR" -> ERROR
            "CANCELLED" -> CANCELLED
            else -> ERROR
        }
    }
}

data class WearConversationItem(
    val id: String,
    val title: String,
    val activeTargetId: String?,
    val activeTargetDisplayName: String?,
    val updatedAt: Long,
    val isGenerating: Boolean
)

data class WearAttachmentItem(
    val id: String,
    val sortIndex: Int,
    val originalWidth: Int,
    val originalHeight: Int,
    val originalMimeType: String,
    val isTextReplaced: Boolean,
    val payloadRevision: Int = 0
)

data class WearMessageItem(
    val id: String,
    val conversationId: String,
    val position: Int,
    val turnIndex: Int,
    val role: WearMessageRole,
    val textContent: String,
    val status: WearMessageStatus,
    val errorCode: String?,
    val errorMessage: String?,
    val servedTargetSummary: String?,
    val createdAt: Long,
    val isReported: Boolean,
    val attachments: List<WearAttachmentItem>
)

data class WearTargetItem(
    val id: String,
    val displayName: String,
    val subtitle: String,
    val isGroup: Boolean
)

data class WearSyncedConfig(
    val targets: List<WearTargetItem> = emptyList(),
    val selectedTargetId: String? = null,
    val activeGeneratingConversationIds: Set<String> = emptySet(),
    val updatedAt: Long = 0L
) {
    val selectedTarget: WearTargetItem?
        get() = targets.firstOrNull { it.id == selectedTargetId } ?: targets.firstOrNull()
}

data class WearConversationsPage(
    val items: List<WearConversationItem>,
    val hasMore: Boolean,
    val nextCursorUpdatedAt: Long?,
    val nextCursorId: String?
)

data class WearMessagesPage(
    val conversationId: String,
    val conversationTitle: String,
    val boundTargetId: String?,
    val boundTargetDisplayName: String?,
    val messages: List<WearMessageItem>,
    val hasOlder: Boolean,
    val nextBeforePosition: Int?
)

data class WearSendTurnResult(
    val accepted: Boolean,
    val conversationId: String?,
    val boundTargetDisplayName: String?,
    val userMessage: WearMessageItem?,
    val assistantPlaceholder: WearMessageItem?,
    val rejectionCode: String?
)

data class WearChatUpdate(
    val conversationId: String,
    val conversationTitle: String,
    val updatedMessage: WearMessageItem,
    val isGenerating: Boolean
)

data class WearSimpleResult(
    val success: Boolean,
    val errorCode: String? = null,
    val errorMessage: String? = null
)

package com.iank.im.peek.wear.data.bridge

import kotlinx.serialization.Serializable

@Serializable
data class WearConversationListRequest(
    val beforeUpdatedAt: Long? = null,
    val beforeId: String? = null,
    val pageSize: Int = WearBridgeConstants.DEFAULT_CONVERSATIONS_PAGE_SIZE
)

@Serializable
data class WearConversationSummaryDto(
    val id: String,
    val title: String,
    val activeTargetId: String? = null,
    val activeTargetDisplayName: String? = null,
    val updatedAt: Long,
    val isGenerating: Boolean
)

@Serializable
data class WearConversationListResponse(
    val items: List<WearConversationSummaryDto>,
    val hasMore: Boolean,
    val nextCursorUpdatedAt: Long? = null,
    val nextCursorId: String? = null
)

@Serializable
data class WearMessageListRequest(
    val conversationId: String,
    val beforePosition: Int? = null,
    val pageSize: Int = WearBridgeConstants.DEFAULT_MESSAGES_PAGE_SIZE
)

@Serializable
data class WearAttachmentDto(
    val id: String,
    val sortIndex: Int,
    val originalWidth: Int,
    val originalHeight: Int,
    val originalMimeType: String,
    val isTextReplaced: Boolean,
    val payloadRevision: Int = 0
)

@Serializable
data class WearMessageDto(
    val id: String,
    val conversationId: String,
    val position: Int,
    val turnIndex: Int,
    val role: String,
    val textContent: String,
    val status: String,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val servedTargetSummary: String? = null,
    val createdAt: Long,
    val isReported: Boolean = false,
    val attachments: List<WearAttachmentDto> = emptyList()
)

@Serializable
data class WearMessageListResponse(
    val conversationId: String,
    val conversationTitle: String,
    val boundTargetId: String? = null,
    val boundTargetDisplayName: String? = null,
    val messages: List<WearMessageDto>,
    val hasOlder: Boolean,
    val nextBeforePosition: Int? = null
)

@Serializable
data class WearSendTurnRequest(
    val conversationId: String? = null,
    val userText: String
)

@Serializable
data class WearSendTurnAcceptedResponse(
    val accepted: Boolean,
    val conversationId: String? = null,
    val boundTargetDisplayName: String? = null,
    val userMessage: WearMessageDto? = null,
    val assistantPlaceholder: WearMessageDto? = null,
    val rejectionCode: String? = null
)

@Serializable
data class WearRetryTurnRequest(
    val conversationId: String,
    val assistantMessageId: String
)

@Serializable
data class WearCancelTurnRequest(
    val conversationId: String
)

@Serializable
data class WearDeleteConversationRequest(
    val conversationId: String
)

@Serializable
data class WearReportMessageRequest(
    val messageId: String,
    val reason: String
)

@Serializable
data class WearSelectTargetRequest(
    val targetId: String
)

@Serializable
data class WearSimpleAckResponse(
    val success: Boolean,
    val errorCode: String? = null,
    val errorMessage: String? = null
)

@Serializable
data class WearChatUpdateEvent(
    val conversationId: String,
    val conversationTitle: String,
    val updatedMessage: WearMessageDto,
    val isGenerating: Boolean
)

@Serializable
data class WearTargetDto(
    val id: String,
    val displayName: String,
    val subtitle: String,
    val isGroup: Boolean
)

@Serializable
data class WearSyncedConfigDto(
    val targets: List<WearTargetDto>,
    val selectedTargetId: String? = null,
    val activeGeneratingConversationIds: Set<String> = emptySet(),
    val updatedAt: Long
)

@Serializable
data class WearImageRequest(
    val requestId: String,
    val attachmentId: String,
    val kind: String,
    val qualityPreset: String
)

@Serializable
data class WearImageRequestAck(
    val accepted: Boolean,
    val requestId: String,
    val channelPath: String,
    val mimeType: String? = null,
    val errorReason: String? = null
)

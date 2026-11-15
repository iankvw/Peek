package com.iank.im.peek.domain.model

data class CommittedAttachmentInput(
    val id: String,
    val originalRelPath: String,
    val originalMimeType: String,
    val originalWidth: Int,
    val originalHeight: Int,
    val thumbnailRelPath: String
)

sealed interface PromptPart {
    data class Text(val text: String) : PromptPart
    data class ImageRef(
        val attachment: MessageAttachment,
        val payloadRelPath: String,
        val payloadMimeType: String
    ) : PromptPart
}

data class PromptTurn(
    val messageId: String,
    val role: MessageRole,
    val parts: List<PromptPart>,
    val presetIdUsed: String?,
    val providerMetadataJson: String?
)

sealed interface PreflightNotice {
    data object SwitchedToFileApi : PreflightNotice
    data object ReplacedOlderImagesWithText : PreflightNotice
    data class DownscaledImages(val targetMaxSidePx: Int) : PreflightNotice
}

sealed interface VisionResponseResult {
    data class Success(
        val textContent: String,
        val providerMetadataJson: String? = null
    ) : VisionResponseResult

    data class Failure(
        val error: ApiError
    ) : VisionResponseResult
}

class ApiException(val error: ApiError) : Exception(error.detailMessage ?: error.code)

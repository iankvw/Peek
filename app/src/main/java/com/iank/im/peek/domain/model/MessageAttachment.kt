package com.iank.im.peek.domain.model

data class MessageAttachment(
    val id: String,
    val messageId: String,
    val sortIndex: Int,
    val originalRelPath: String,
    val originalMimeType: String,
    val originalWidth: Int,
    val originalHeight: Int,
    val thumbnailRelPath: String,
    val payloadRelPath: String?,
    val payloadMaxSide: Int?,
    val payloadRevision: Int,
    val requestedPreset: ImageResolutionPreset,
    val isTextReplaced: Boolean,
    val imageCaption: String?
)

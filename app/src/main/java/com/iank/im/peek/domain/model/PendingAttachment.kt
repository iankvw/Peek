package com.iank.im.peek.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class PendingAttachment(
    val id: String,
    val stagingOriginalRelPath: String,
    val stagingThumbnailRelPath: String,
    val mimeType: String,
    val width: Int,
    val height: Int
)

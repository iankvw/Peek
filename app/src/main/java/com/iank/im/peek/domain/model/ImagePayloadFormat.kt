package com.iank.im.peek.domain.model

enum class ImagePayloadFormat(
    val mimeType: String,
    val fileExtension: String
) {
    WEBP(mimeType = "image/webp", fileExtension = "webp"),
    JPEG(mimeType = "image/jpeg", fileExtension = "jpg")
}

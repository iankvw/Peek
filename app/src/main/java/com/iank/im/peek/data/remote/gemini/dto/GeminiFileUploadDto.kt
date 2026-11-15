package com.iank.im.peek.data.remote.gemini.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GeminiFileUploadStartRequest(
    val file: GeminiFileDisplayNameDto
)

@Serializable
data class GeminiFileDisplayNameDto(
    @SerialName("display_name") val displayName: String
)

@Serializable
data class GeminiFileMetadataWrapperDto(
    val file: GeminiFileMetadataDto? = null
)

@Serializable
data class GeminiFileMetadataDto(
    val name: String? = null,
    @SerialName("displayName") val displayName: String? = null,
    @SerialName("mimeType") val mimeType: String? = null,
    val sizeBytes: String? = null,
    val createTime: String? = null,
    val updateTime: String? = null,
    val expirationTime: String? = null,
    val uri: String? = null,
    val state: String? = null
)

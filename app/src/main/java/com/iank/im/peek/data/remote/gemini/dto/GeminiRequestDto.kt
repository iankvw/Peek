package com.iank.im.peek.data.remote.gemini.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class GeminiGenerateContentRequest(
    @SerialName("system_instruction") val systemInstruction: GeminiContentDto? = null,
    val contents: List<GeminiContentDto>
)

@Serializable
data class GeminiContentDto(
    val role: String? = null,
    val parts: List<GeminiPartDto>
)

@Serializable
data class GeminiPartDto(
    val text: String? = null,
    @SerialName("inline_data") val inlineData: GeminiInlineDataDto? = null,
    @SerialName("file_data") val fileData: GeminiFileDataDto? = null,
    val thought: Boolean? = null,
    @SerialName("thoughtSignature") val thoughtSignature: String? = null,
    @SerialName("thought_signature") val thoughtSignatureSnake: String? = null
)

@Serializable
data class GeminiInlineDataDto(
    @SerialName("mime_type") val mimeType: String,
    val data: String
)

@Serializable
data class GeminiFileDataDto(
    @SerialName("mime_type") val mimeType: String,
    @SerialName("file_uri") val fileUri: String
)

@Serializable
data class GeminiStoredMetadata(
    val provider: String = "GEMINI",
    val thoughtSignatures: List<String> = emptyList(),
    val rawModelParts: List<JsonElement> = emptyList()
)

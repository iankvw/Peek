package com.iank.im.peek.data.remote.gemini.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class GeminiGenerateContentResponse(
    val candidates: List<GeminiCandidateDto> = emptyList(),
    @SerialName("promptFeedback") val promptFeedback: GeminiPromptFeedbackDto? = null,
    val error: GeminiErrorBodyDto? = null
)

@Serializable
data class GeminiCandidateDto(
    val content: GeminiResponseContentDto? = null,
    val finishReason: String? = null,
    val finishMessage: String? = null
)

@Serializable
data class GeminiResponseContentDto(
    val role: String? = null,
    val parts: List<JsonElement> = emptyList()
)

@Serializable
data class GeminiPromptFeedbackDto(
    val blockReason: String? = null,
    val blockReasonMessage: String? = null
)

@Serializable
data class GeminiErrorBodyDto(
    val code: Int? = null,
    val message: String? = null,
    val status: String? = null
)

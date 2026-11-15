package com.iank.im.peek.data.remote.openai.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenAiChatRequestHeaderDto(
    val model: String,
    val messages: List<OpenAiMessageDto>,
    @SerialName("max_tokens") val maxTokens: Int? = null
)

@Serializable
data class OpenAiMessageDto(
    val role: String,
    val content: String
)

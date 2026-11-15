package com.iank.im.peek.data.remote.openai.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Serializable
data class OpenAiChatCompletionResponse(
    val id: String? = null,
    val choices: List<OpenAiChoiceDto> = emptyList(),
    val error: OpenAiErrorDto? = null
)

@Serializable
data class OpenAiChoiceDto(
    val index: Int = 0,
    val message: OpenAiResponseMessageDto? = null,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class OpenAiResponseMessageDto(
    val role: String? = null,
    val content: JsonElement? = null,
    @SerialName("reasoning_content") val reasoningContent: String? = null,
    val refusal: String? = null
) {
    fun extractVisibleText(): String {
        val el = content ?: return ""
        return when (el) {
            is JsonNull -> ""
            is JsonPrimitive -> el.contentOrNull.orEmpty()
            is JsonArray -> buildString {
                for (item in el) {
                    val obj = item as? JsonObject ?: continue
                    val type = (obj["type"] as? JsonPrimitive)?.contentOrNull
                    if (type == null || type == "text" || type == "output_text") {
                        val text = (obj["text"] as? JsonPrimitive)?.contentOrNull
                            ?: ((obj["text"] as? JsonObject)?.get("value") as? JsonPrimitive)?.contentOrNull
                        if (!text.isNullOrEmpty()) {
                            append(text)
                        }
                    }
                }
            }
            is JsonObject -> {
                (el["text"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            }
        }
    }
}

@Serializable
data class OpenAiErrorDto(
    val message: String? = null,
    val type: String? = null,
    val code: String? = null
)

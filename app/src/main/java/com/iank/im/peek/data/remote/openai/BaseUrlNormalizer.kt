package com.iank.im.peek.data.remote.openai

import java.net.URI

sealed interface BaseUrlNormalizationResult {
    data class Valid(
        val normalizedBaseUrl: String,
        val chatCompletionsUrl: String,
        val modelsUrl: String,
        val isCleartextHttp: Boolean
    ) : BaseUrlNormalizationResult

    data object MissingSchemeOrInvalid : BaseUrlNormalizationResult
}

object BaseUrlNormalizer {
    const val DEFAULT_OPENAI_BASE_URL = "https://api.openai.com/v1"

    fun normalize(rawInput: String?, customEnabled: Boolean): BaseUrlNormalizationResult {
        if (!customEnabled) {
            return BaseUrlNormalizationResult.Valid(
                normalizedBaseUrl = DEFAULT_OPENAI_BASE_URL,
                chatCompletionsUrl = "$DEFAULT_OPENAI_BASE_URL/chat/completions",
                modelsUrl = "$DEFAULT_OPENAI_BASE_URL/models",
                isCleartextHttp = false
            )
        }

        val trimmed = rawInput?.trim().orEmpty()
        if (trimmed.isBlank()) return BaseUrlNormalizationResult.MissingSchemeOrInvalid

        val lower = trimmed.lowercase()
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return BaseUrlNormalizationResult.MissingSchemeOrInvalid
        }

        var cleaned = trimmed.trimEnd('/')
        if (cleaned.endsWith("/chat/completions", ignoreCase = true)) {
            cleaned = cleaned.dropLast("/chat/completions".length).trimEnd('/')
        }

        val parsed = runCatching { URI(cleaned) }.getOrNull()
            ?: return BaseUrlNormalizationResult.MissingSchemeOrInvalid
        val host = parsed.host
        if (host.isNullOrBlank()) return BaseUrlNormalizationResult.MissingSchemeOrInvalid

        val isHttp = parsed.scheme.equals("http", ignoreCase = true)
        return BaseUrlNormalizationResult.Valid(
            normalizedBaseUrl = cleaned,
            chatCompletionsUrl = "$cleaned/chat/completions",
            modelsUrl = "$cleaned/models",
            isCleartextHttp = isHttp
        )
    }

    fun previewEndpoint(rawInput: String?, customEnabled: Boolean): String? {
        return when (val result = normalize(rawInput, customEnabled)) {
            is BaseUrlNormalizationResult.Valid -> result.chatCompletionsUrl
            BaseUrlNormalizationResult.MissingSchemeOrInvalid -> null
        }
    }
}

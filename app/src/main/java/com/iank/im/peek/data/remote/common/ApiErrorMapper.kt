package com.iank.im.peek.data.remote.common

import com.iank.im.peek.domain.model.ApiError
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers
import java.io.IOException
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiErrorMapper @Inject constructor(
    private val json: Json
) {
    fun mapHttpFailure(
        httpCode: Int,
        headers: Headers,
        rawBody: String?
    ): ApiError {
        val bodyTrimmed = rawBody?.trim().orEmpty()
        val parsedMessage = extractErrorMessageFromBody(bodyTrimmed)
        val detail = parsedMessage ?: bodyTrimmed.take(MAX_DETAIL_CHARS).ifBlank { null }

        if (looksLikeHtmlOrNonJsonProxyError(httpCode, bodyTrimmed, parsedMessage)) {
            val snippet = bodyTrimmed
                .replace(Regex("\\s+"), " ")
                .take(MAX_PARSE_SNIPPET_CHARS)
            if (httpCode != 401 && httpCode != 403 && httpCode != 404 && httpCode != 413 && httpCode != 429) {
                return ApiError.Parse(
                    snippet = "HTTP $httpCode: $snippet",
                    detailMessage = "HTTP $httpCode: $snippet"
                )
            }
        }

        if (isFileReferenceError(httpCode, bodyTrimmed)) {
            return ApiError.FileUnavailable(detail)
        }

        return when (httpCode) {
            401, 403 -> ApiError.Auth(detail)
            404 -> ApiError.NotFound(detail)
            413 -> ApiError.PayloadTooLarge(detail)
            429 -> ApiError.RateLimited(
                retryAfterSeconds = parseRetryAfterSeconds(headers),
                detailMessage = detail
            )
            in 500..599 -> ApiError.Server(httpCode = httpCode, detailMessage = detail)
            400 -> {
                if (isSafetyBlockedMessage(bodyTrimmed)) {
                    ApiError.Blocked(detail)
                } else {
                    ApiError.Unknown(detail)
                }
            }
            else -> ApiError.Unknown(detail)
        }
    }

    fun mapThrowable(throwable: Throwable): ApiError {
        return when (throwable) {
            is SocketTimeoutException -> ApiError.Timeout(throwable.message)
            is SerializationException -> ApiError.Parse(
                snippet = throwable.message?.take(MAX_PARSE_SNIPPET_CHARS).orEmpty(),
                detailMessage = throwable.message
            )
            is IOException -> ApiError.Network(throwable.message)
            else -> ApiError.Unknown(throwable.message)
        }
    }

    fun isFileReferenceError(httpCode: Int, body: String): Boolean {
        if (httpCode !in setOf(400, 403, 404)) return false
        val lower = body.lowercase()
        return lower.contains("files/") ||
            lower.contains("file_uri") ||
            lower.contains("file uri") ||
            lower.contains("file not found") ||
            lower.contains("permission denied on file") ||
            lower.contains("expired") && lower.contains("file")
    }

    private fun extractErrorMessageFromBody(body: String): String? {
        if (body.isBlank() || !body.startsWith("{")) return null
        return runCatching {
            val root = json.parseToJsonElement(body).jsonObject
            val errorObj = root["error"] as? JsonObject
            errorObj?.get("message")?.jsonPrimitive?.content
                ?: root["message"]?.jsonPrimitive?.content
                ?: root["detail"]?.jsonPrimitive?.content
        }.getOrNull()
    }

    private fun looksLikeHtmlOrNonJsonProxyError(
        httpCode: Int,
        body: String,
        parsedJsonMessage: String?
    ): Boolean {
        if (body.isBlank() || parsedJsonMessage != null) return false
        val trimmed = body.trimStart()
        return !trimmed.startsWith("{") && !trimmed.startsWith("[") && httpCode >= 400
    }

    private fun isSafetyBlockedMessage(body: String): Boolean {
        val lower = body.lowercase()
        return lower.contains("safety") ||
            lower.contains("content_filter") ||
            lower.contains("prohibited") ||
            lower.contains("blocked")
    }

    private fun parseRetryAfterSeconds(headers: Headers): Long? {
        val raw = headers["Retry-After"]?.trim() ?: return null
        return raw.toLongOrNull()?.coerceIn(1L, 60L)
    }

    companion object {
        private const val MAX_DETAIL_CHARS = 300
        private const val MAX_PARSE_SNIPPET_CHARS = 180
    }
}

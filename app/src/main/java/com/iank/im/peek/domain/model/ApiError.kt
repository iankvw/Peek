package com.iank.im.peek.domain.model

sealed class ApiError(
    val code: String,
    val retryable: Boolean,
    open val detailMessage: String? = null
) {
    data class Auth(override val detailMessage: String? = null) :
        ApiError(code = CODE_AUTH, retryable = false, detailMessage = detailMessage)

    data class NotFound(override val detailMessage: String? = null) :
        ApiError(code = CODE_NOT_FOUND, retryable = false, detailMessage = detailMessage)

    data class PayloadTooLarge(override val detailMessage: String? = null) :
        ApiError(code = CODE_PAYLOAD_TOO_LARGE, retryable = false, detailMessage = detailMessage)

    data class RateLimited(
        val retryAfterSeconds: Long? = null,
        override val detailMessage: String? = null
    ) : ApiError(code = CODE_RATE_LIMITED, retryable = true, detailMessage = detailMessage)

    data class Server(
        val httpCode: Int = 500,
        override val detailMessage: String? = null
    ) : ApiError(code = CODE_SERVER, retryable = httpCode == 503 || httpCode == 502 || httpCode == 504, detailMessage = detailMessage)

    data class Blocked(override val detailMessage: String? = null) :
        ApiError(code = CODE_BLOCKED, retryable = false, detailMessage = detailMessage)

    data class FileUnavailable(override val detailMessage: String? = null) :
        ApiError(code = CODE_FILE_UNAVAILABLE, retryable = false, detailMessage = detailMessage)

    data class Network(override val detailMessage: String? = null) :
        ApiError(code = CODE_NETWORK, retryable = true, detailMessage = detailMessage)

    data class Timeout(override val detailMessage: String? = null) :
        ApiError(code = CODE_TIMEOUT, retryable = false, detailMessage = detailMessage)

    data class Parse(
        val snippet: String,
        override val detailMessage: String? = snippet
    ) : ApiError(code = CODE_PARSE, retryable = false, detailMessage = detailMessage)

    data object InterruptedByExit :
        ApiError(code = CODE_INTERRUPTED_BY_EXIT, retryable = true, detailMessage = null)

    data class Unknown(override val detailMessage: String? = null) :
        ApiError(code = CODE_UNKNOWN, retryable = false, detailMessage = detailMessage)

    companion object {
        const val CODE_AUTH = "AUTH"
        const val CODE_NOT_FOUND = "NOT_FOUND"
        const val CODE_PAYLOAD_TOO_LARGE = "PAYLOAD_TOO_LARGE"
        const val CODE_RATE_LIMITED = "RATE_LIMITED"
        const val CODE_SERVER = "SERVER"
        const val CODE_BLOCKED = "BLOCKED"
        const val CODE_FILE_UNAVAILABLE = "FILE_UNAVAILABLE"
        const val CODE_NETWORK = "NETWORK"
        const val CODE_TIMEOUT = "TIMEOUT"
        const val CODE_PARSE = "PARSE"
        const val CODE_INTERRUPTED_BY_EXIT = "INTERRUPTED_BY_EXIT"
        const val CODE_UNKNOWN = "UNKNOWN"
    }
}

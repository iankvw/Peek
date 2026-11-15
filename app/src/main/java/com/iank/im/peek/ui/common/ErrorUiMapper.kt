package com.iank.im.peek.ui.common

import com.iank.im.peek.R
import com.iank.im.peek.domain.model.ApiError
import com.iank.im.peek.domain.model.PreflightNotice

object ErrorUiMapper {
    fun toUiText(error: ApiError): UiText = when (error) {
        is ApiError.Auth -> UiText.StringResource(R.string.error_auth)
        is ApiError.NotFound -> UiText.StringResource(R.string.error_not_found)
        is ApiError.PayloadTooLarge -> UiText.StringResource(R.string.error_payload_too_large)
        is ApiError.RateLimited -> UiText.StringResource(R.string.error_rate_limited)
        is ApiError.Server -> UiText.StringResource(R.string.error_server, error.httpCode)
        is ApiError.Blocked -> UiText.StringResource(R.string.error_blocked)
        is ApiError.FileUnavailable -> UiText.StringResource(R.string.error_file_unavailable)
        is ApiError.Network -> UiText.StringResource(R.string.error_network)
        is ApiError.Timeout -> UiText.StringResource(R.string.error_timeout)
        is ApiError.Parse -> UiText.StringResource(R.string.error_parse, error.snippet)
        ApiError.InterruptedByExit -> UiText.StringResource(R.string.error_interrupted_by_exit)
        is ApiError.Unknown -> error.detailMessage
            ?.takeIf { it.isNotBlank() }
            ?.let { UiText.Dynamic(it) }
            ?: UiText.StringResource(R.string.error_unknown)
    }

    fun fromPersistedError(errorCode: String?, detailMessage: String?): UiText = when (errorCode) {
        ApiError.CODE_AUTH -> UiText.StringResource(R.string.error_auth)
        ApiError.CODE_NOT_FOUND -> UiText.StringResource(R.string.error_not_found)
        ApiError.CODE_PAYLOAD_TOO_LARGE -> UiText.StringResource(R.string.error_payload_too_large)
        ApiError.CODE_RATE_LIMITED -> UiText.StringResource(R.string.error_rate_limited)
        ApiError.CODE_SERVER -> UiText.StringResource(R.string.error_server, 500)
        ApiError.CODE_BLOCKED -> UiText.StringResource(R.string.error_blocked)
        ApiError.CODE_FILE_UNAVAILABLE -> UiText.StringResource(R.string.error_file_unavailable)
        ApiError.CODE_NETWORK -> UiText.StringResource(R.string.error_network)
        ApiError.CODE_TIMEOUT -> UiText.StringResource(R.string.error_timeout)
        ApiError.CODE_PARSE -> UiText.StringResource(
            R.string.error_parse,
            detailMessage?.take(160).orEmpty()
        )
        ApiError.CODE_INTERRUPTED_BY_EXIT -> UiText.StringResource(R.string.error_interrupted_by_exit)
        else -> detailMessage?.takeIf { it.isNotBlank() }
            ?.let { UiText.Dynamic(it) }
            ?: UiText.StringResource(R.string.error_unknown)
    }

    fun toUiText(notice: PreflightNotice): UiText = when (notice) {
        PreflightNotice.SwitchedToFileApi ->
            UiText.StringResource(R.string.preflight_switched_to_file_api)
        PreflightNotice.ReplacedOlderImagesWithText ->
            UiText.StringResource(R.string.preflight_text_replaced_old_images)
        is PreflightNotice.DownscaledImages ->
            UiText.StringResource(R.string.preflight_downscaled_images, notice.targetMaxSidePx)
    }
}

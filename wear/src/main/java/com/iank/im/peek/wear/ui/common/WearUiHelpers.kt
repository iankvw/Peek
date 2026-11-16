package com.iank.im.peek.wear.ui.common

import android.content.Context
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.iank.im.peek.wear.R
import com.iank.im.peek.wear.ui.theme.PeekWearOnPrimaryContainer
import com.iank.im.peek.wear.ui.theme.PeekWearSurfaceContainer

object WearUiHelpers {

    fun mapErrorMessage(
        context: Context,
        errorCode: String?,
        errorMessage: String?
    ): String = when (errorCode) {
        "AUTH" -> context.getString(R.string.error_auth)
        "NOT_FOUND" -> context.getString(R.string.error_not_found)
        "PAYLOAD_TOO_LARGE" -> context.getString(R.string.error_payload_too_large)
        "RATE_LIMITED" -> context.getString(R.string.error_rate_limited)
        "SERVER" -> context.getString(R.string.error_server, 500)
        "BLOCKED" -> context.getString(R.string.error_blocked)
        "FILE_UNAVAILABLE" -> context.getString(R.string.error_file_unavailable)
        "NETWORK" -> context.getString(R.string.error_network)
        "TIMEOUT" -> context.getString(R.string.error_timeout)
        "PARSE" -> context.getString(R.string.error_parse, errorMessage?.take(120).orEmpty())
        "INTERRUPTED_BY_EXIT" -> context.getString(R.string.error_interrupted_by_exit)
        else -> errorMessage?.takeIf { it.isNotBlank() } ?: context.getString(R.string.error_unknown)
    }

    fun mapRejectionMessage(context: Context, rejectionCode: String?): String = when (rejectionCode) {
        "NO_PRESET" -> context.getString(R.string.chat_validation_no_preset)
        "MISSING_MODEL" -> context.getString(R.string.chat_validation_missing_model)
        "MISSING_API_KEY" -> context.getString(R.string.chat_validation_missing_api_key)
        "DISCONNECTED" -> context.getString(R.string.chat_send_failed_phone_disconnected)
        else -> context.getString(R.string.error_unknown)
    }

    fun formatRelativeTime(context: Context, timestampMs: Long, nowMs: Long = System.currentTimeMillis()): String {
        val diffMs = (nowMs - timestampMs).coerceAtLeast(0L)
        val minutes = diffMs / 60_000L
        val hours = minutes / 60L
        val days = hours / 24L
        return when {
            minutes < 1L -> context.getString(R.string.home_time_just_now)
            minutes < 60L -> context.getString(R.string.home_time_minutes_ago, minutes.toInt())
            hours < 24L -> context.getString(R.string.home_time_hours_ago, hours.toInt())
            else -> context.getString(R.string.home_time_days_ago, days.toInt())
        }
    }

    fun formatWearMarkdown(raw: String): AnnotatedString {
        if (raw.isBlank()) return AnnotatedString("")
        val normalized = raw
            .replace("\r\n", "\n")
            .lines()
            .joinToString("\n") { line ->
                val trimmed = line.trimStart()
                when {
                    trimmed.startsWith("### ") -> "**${trimmed.removePrefix("### ").trim()}**"
                    trimmed.startsWith("## ") -> "**${trimmed.removePrefix("## ").trim()}**"
                    trimmed.startsWith("# ") -> "**${trimmed.removePrefix("# ").trim()}**"
                    trimmed.startsWith("- ") -> "• ${trimmed.removePrefix("- ")}"
                    trimmed.startsWith("* ") -> "• ${trimmed.removePrefix("* ")}"
                    trimmed.startsWith("> ") -> trimmed.removePrefix("> ")
                    else -> line
                }
            }

        return buildAnnotatedString {
            var index = 0
            while (index < normalized.length) {
                when {
                    normalized.startsWith("**", index) -> {
                        val end = normalized.indexOf("**", index + 2)
                        if (end > index + 2) {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                append(normalized.substring(index + 2, end))
                            }
                            index = end + 2
                        } else {
                            append("**")
                            index += 2
                        }
                    }
                    normalized.startsWith("`", index) -> {
                        val end = normalized.indexOf("`", index + 1)
                        if (end > index + 1) {
                            withStyle(
                                SpanStyle(
                                    fontFamily = FontFamily.Monospace,
                                    background = PeekWearSurfaceContainer,
                                    color = PeekWearOnPrimaryContainer
                                )
                            ) {
                                append(normalized.substring(index + 1, end))
                            }
                            index = end + 1
                        } else {
                            append("`")
                            index += 1
                        }
                    }
                    normalized.startsWith("*", index) && !normalized.startsWith("**", index) -> {
                        val end = normalized.indexOf("*", index + 1)
                        if (end > index + 1) {
                            withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                                append(normalized.substring(index + 1, end))
                            }
                            index = end + 1
                        } else {
                            append("*")
                            index += 1
                        }
                    }
                    else -> {
                        append(normalized[index])
                        index += 1
                    }
                }
            }
        }
    }
}

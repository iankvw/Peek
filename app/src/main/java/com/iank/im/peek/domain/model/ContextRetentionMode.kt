package com.iank.im.peek.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface ContextRetentionMode {
    @Serializable
    @SerialName("ALL")
    data object All : ContextRetentionMode

    @Serializable
    @SerialName("SLIDING_WINDOW")
    data class SlidingWindow(val turns: Int = DEFAULT_TURNS) : ContextRetentionMode

    companion object {
        const val DEFAULT_TURNS = 10
    }
}

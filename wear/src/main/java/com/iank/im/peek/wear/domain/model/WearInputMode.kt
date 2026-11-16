package com.iank.im.peek.wear.domain.model

enum class WearInputMode {
    TEXT_KEYBOARD,
    VOICE_FIRST;

    companion object {
        fun fromStored(value: String?): WearInputMode =
            entries.firstOrNull { it.name == value } ?: TEXT_KEYBOARD
    }
}

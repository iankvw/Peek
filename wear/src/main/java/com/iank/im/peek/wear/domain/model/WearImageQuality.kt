package com.iank.im.peek.wear.domain.model

enum class WearImageQuality(val protocolValue: String) {
    ORIGINAL("ORIGINAL"),
    HIGH_4000("HIGH_4000"),
    BALANCED_2000("BALANCED_2000"),
    STANDARD_1000("STANDARD_1000");

    companion object {
        fun fromStored(value: String?): WearImageQuality =
            entries.firstOrNull { it.name == value } ?: BALANCED_2000
    }
}

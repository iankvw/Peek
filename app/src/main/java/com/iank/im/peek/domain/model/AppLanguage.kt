package com.iank.im.peek.domain.model

enum class AppLanguage(val languageTag: String?) {
    SYSTEM(null),
    EN_US("en-US"),
    KO("ko");

    companion object {
        fun fromStoredName(value: String?): AppLanguage =
            entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

package com.iank.im.peek.ui.common

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.iank.im.peek.domain.model.AppLanguage

object AppLocaleManager {
    fun applyLanguage(language: AppLanguage) {
        val locales = when (language.languageTag) {
            null -> LocaleListCompat.getEmptyLocaleList()
            else -> LocaleListCompat.forLanguageTags(language.languageTag)
        }
        if (AppCompatDelegate.getApplicationLocales() != locales) {
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }

    fun currentSelectedLanguage(): AppLanguage {
        val tags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        return when {
            tags.isBlank() -> AppLanguage.SYSTEM
            tags.startsWith("ko", ignoreCase = true) -> AppLanguage.KO
            tags.startsWith("en", ignoreCase = true) -> AppLanguage.EN_US
            else -> AppLanguage.SYSTEM
        }
    }
}

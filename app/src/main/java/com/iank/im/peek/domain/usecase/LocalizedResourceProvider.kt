package com.iank.im.peek.domain.usecase

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalizedResourceProvider @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    fun getString(@StringRes resId: Int, vararg args: Any): String {
        val localizedContext = resolveLocalizedContext()
        return if (args.isEmpty()) {
            localizedContext.getString(resId)
        } else {
            localizedContext.getString(resId, *args)
        }
    }

    private fun resolveLocalizedContext(): Context {
        val appLocales = AppCompatDelegate.getApplicationLocales()
        if (appLocales.isEmpty) return context
        val targetLocale: Locale = appLocales[0] ?: return context
        val config = Configuration(context.resources.configuration).apply {
            setLocale(targetLocale)
        }
        return context.createConfigurationContext(config)
    }
}

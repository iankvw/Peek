package com.iank.im.peek.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = PeekLightPrimary,
    onPrimary = PeekLightOnPrimary,
    primaryContainer = PeekLightPrimaryContainer,
    onPrimaryContainer = PeekLightOnPrimaryContainer,
    secondary = PeekLightSecondary,
    onSecondary = PeekLightOnSecondary,
    secondaryContainer = PeekLightSecondaryContainer,
    onSecondaryContainer = PeekLightOnSecondaryContainer,
    tertiary = PeekLightTertiary,
    onTertiary = PeekLightOnTertiary,
    tertiaryContainer = PeekLightTertiaryContainer,
    onTertiaryContainer = PeekLightOnTertiaryContainer,
    error = PeekLightError,
    onError = PeekLightOnError,
    errorContainer = PeekLightErrorContainer,
    onErrorContainer = PeekLightOnErrorContainer,
    background = PeekLightBackground,
    onBackground = PeekLightOnBackground,
    surface = PeekLightSurface,
    onSurface = PeekLightOnSurface,
    surfaceVariant = PeekLightSurfaceVariant,
    onSurfaceVariant = PeekLightOnSurfaceVariant,
    surfaceContainerLowest = PeekLightSurfaceContainerLowest,
    surfaceContainerLow = PeekLightSurfaceContainerLow,
    surfaceContainer = PeekLightSurfaceContainer,
    surfaceContainerHigh = PeekLightSurfaceContainerHigh,
    surfaceContainerHighest = PeekLightSurfaceContainerHighest,
    outline = PeekLightOutline,
    outlineVariant = PeekLightOutlineVariant
)

private val DarkColorScheme = darkColorScheme(
    primary = PeekDarkPrimary,
    onPrimary = PeekDarkOnPrimary,
    primaryContainer = PeekDarkPrimaryContainer,
    onPrimaryContainer = PeekDarkOnPrimaryContainer,
    secondary = PeekDarkSecondary,
    onSecondary = PeekDarkOnSecondary,
    secondaryContainer = PeekDarkSecondaryContainer,
    onSecondaryContainer = PeekDarkOnSecondaryContainer,
    tertiary = PeekDarkTertiary,
    onTertiary = PeekDarkOnTertiary,
    tertiaryContainer = PeekDarkTertiaryContainer,
    onTertiaryContainer = PeekDarkOnTertiaryContainer,
    error = PeekDarkError,
    onError = PeekDarkOnError,
    errorContainer = PeekDarkErrorContainer,
    onErrorContainer = PeekDarkOnErrorContainer,
    background = PeekDarkBackground,
    onBackground = PeekDarkOnBackground,
    surface = PeekDarkSurface,
    onSurface = PeekDarkOnSurface,
    surfaceDim = PeekDarkSurfaceDim,
    surfaceBright = PeekDarkSurfaceBright,
    surfaceContainerLowest = PeekDarkSurfaceContainerLowest,
    surfaceContainerLow = PeekDarkSurfaceContainerLow,
    surfaceContainer = PeekDarkSurfaceContainer,
    surfaceContainerHigh = PeekDarkSurfaceContainerHigh,
    surfaceContainerHighest = PeekDarkSurfaceContainerHighest,
    surfaceVariant = PeekDarkSurfaceVariant,
    onSurfaceVariant = PeekDarkOnSurfaceVariant,
    outline = PeekDarkOutline,
    outlineVariant = PeekDarkOutlineVariant
)

@Composable
fun PeekTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = PeekTypography,
        content = content
    )
}

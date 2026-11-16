package com.iank.im.peek.wear.ui.theme

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

private val PeekWearColorScheme = ColorScheme(
    primary = PeekWearPrimary,
    onPrimary = PeekWearOnPrimary,
    primaryContainer = PeekWearPrimaryContainer,
    onPrimaryContainer = PeekWearOnPrimaryContainer,
    secondary = PeekWearOnPrimaryContainer,
    onSecondary = PeekWearOnPrimary,
    secondaryContainer = PeekWearSurfaceContainerHigh,
    onSecondaryContainer = PeekWearOnBackground,
    tertiary = PeekWearPrimary,
    onTertiary = PeekWearOnPrimary,
    tertiaryContainer = PeekWearPrimaryContainer,
    onTertiaryContainer = PeekWearOnPrimaryContainer,
    surfaceContainerLow = PeekWearSurface,
    surfaceContainer = PeekWearSurfaceContainer,
    surfaceContainerHigh = PeekWearSurfaceContainerHigh,
    onSurface = PeekWearOnBackground,
    onSurfaceVariant = PeekWearOnSurfaceVariant,
    outline = PeekWearOutlineVariant,
    outlineVariant = PeekWearOutlineVariant,
    background = PeekWearBackground,
    onBackground = PeekWearOnBackground,
    error = PeekWearError,
    onError = PeekWearOnError,
    errorContainer = PeekWearErrorContainer,
    onErrorContainer = PeekWearOnErrorContainer
)

@Composable
fun PeekWearTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = PeekWearColorScheme,
        content = content
    )
}

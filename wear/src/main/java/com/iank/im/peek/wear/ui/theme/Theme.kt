package com.iank.im.peek.wear.ui.theme

import androidx.compose.runtime.Composable
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

private val PeekWearColors = Colors(
    primary = PeekWearPrimary,
    primaryVariant = PeekWearPrimaryContainer,
    secondary = PeekWearOnPrimaryContainer,
    secondaryVariant = PeekWearPrimaryContainer,
    background = PeekWearBackground,
    surface = PeekWearSurfaceContainer,
    error = PeekWearError,
    onPrimary = PeekWearOnPrimary,
    onSecondary = PeekWearOnPrimary,
    onBackground = PeekWearOnBackground,
    onSurface = PeekWearOnBackground,
    onSurfaceVariant = PeekWearOnSurfaceVariant,
    onError = PeekWearOnError
)

@Composable
fun PeekWearTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colors = PeekWearColors,
        content = content
    )
}

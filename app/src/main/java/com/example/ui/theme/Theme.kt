package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val StressDarkColorScheme = darkColorScheme(
    primary = StressCoralGlow,
    onPrimary = StressWhite,
    secondary = StressCoralHighlight,
    onSecondary = StressWhite,
    background = StressObsidian,
    onBackground = StressWhite,
    surface = StressDomeSurface,
    onSurface = StressWhite,
    surfaceVariant = StressPillSurface,
    onSurfaceVariant = StressMutedWhite,
    outline = StressSubtleStroke
)

@Composable
fun StressTimerTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = StressDarkColorScheme,
        typography = StressTypography,
        content = content
    )
}

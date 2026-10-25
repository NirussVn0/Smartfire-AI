package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val FriendlyLightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = Color.White,
    primaryContainer = PrimaryBlueLight,
    onPrimaryContainer = PrimaryBlueVariant,
    secondary = CyanAccent,
    onSecondary = Color.White,
    secondaryContainer = CyanAccentLight,
    onSecondaryContainer = CyanAccent,
    background = LightBackground,
    onBackground = TextPrimary,
    surface = LightSurface,
    onSurface = TextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    error = CriticalAlarmRed,
    onError = Color.White,
    outline = LightBorder
)

@Composable
fun SmartFireTheme(
    darkTheme: Boolean = false, // Default to friendly, bright light theme
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = FriendlyLightColorScheme,
        typography = AppTypography,
        content = content
    )
}

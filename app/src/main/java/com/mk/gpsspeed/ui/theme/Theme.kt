package com.mk.gpsspeed.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val GpsSpeedColorScheme = darkColorScheme(
    primary = PrimaryAccent,
    background = AppBackground,
    surface = CardBackground,
)

@Composable
fun GpsSpeedTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GpsSpeedColorScheme,
        typography = Typography,
        content = content,
    )
}

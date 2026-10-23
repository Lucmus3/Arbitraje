package com.lucas.arbitraje.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Pitch = Color(0xFF0B3D2E)
val PitchLight = Color(0xFF14543F)
val Accent = Color(0xFF7CE0A3)

private val colors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF00281A),
    secondary = Color(0xFFFDD835),
    background = Pitch,
    onBackground = Color.White,
    surface = PitchLight,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1C6A50),
    onSurfaceVariant = Color(0xFFD5EDE2),
    surfaceContainer = PitchLight,
    surfaceContainerHigh = Color(0xFF1A604A),
    surfaceContainerHighest = Color(0xFF1C6A50),
    surfaceContainerLow = Color(0xFF104A38),
)

@Composable
fun ArbitrajeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}

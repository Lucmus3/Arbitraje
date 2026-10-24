package com.lucas.arbitraje.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Estética "asistente arbitral": fondo azul marino, tarjetas gris claro, botones grises redondeados.
val Navy = Color(0xFF1F2A3C)
val NavyLight = Color(0xFF2C3A52)
val CardGray = Color(0xFFE5E7EB)
val ButtonGray = Color(0xFFD1D5DB)
val Ink = Color(0xFF1F2937)
val InkSoft = Color(0xFF4B5563)
/** Texto secundario sobre el fondo azul. */
val Muted = Color(0xFFB4BFD0)

// Nombres anteriores, usados en todas las pantallas.
val Pitch = Navy
val Accent = Navy

private val colors = lightColorScheme(
    primary = Navy,
    onPrimary = Color.White,
    primaryContainer = ButtonGray,
    onPrimaryContainer = Ink,
    secondary = InkSoft,
    onSecondary = Color.White,
    secondaryContainer = ButtonGray,
    onSecondaryContainer = Ink,
    background = Navy,
    onBackground = Color.White,
    surface = CardGray,
    onSurface = Ink,
    surfaceVariant = ButtonGray,
    onSurfaceVariant = InkSoft,
    surfaceContainerLowest = Color(0xFFF3F4F6),
    surfaceContainerLow = CardGray,
    surfaceContainer = CardGray,
    surfaceContainerHigh = CardGray,
    surfaceContainerHighest = CardGray,
    outline = Color(0xFF9CA3AF),
    outlineVariant = ButtonGray,
    error = Color(0xFFC0392B),
)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

/** Botón gris con texto oscuro, para usar sobre el fondo azul. */
@Composable
fun grayButtonColors() = ButtonDefaults.buttonColors(
    containerColor = ButtonGray,
    contentColor = Ink,
    disabledContainerColor = NavyLight,
    disabledContentColor = Muted,
)

// Algunas fuentes del sistema (p. ej. Samsung) tienen colas de g/p más largas que Roboto:
// con el padding de fuente activado no se cortan.
private val safeText = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = true))

private val typography = Typography().run {
    copy(
        displayLarge = displayLarge.merge(safeText), displayMedium = displayMedium.merge(safeText), displaySmall = displaySmall.merge(safeText),
        headlineLarge = headlineLarge.merge(safeText), headlineMedium = headlineMedium.merge(safeText), headlineSmall = headlineSmall.merge(safeText),
        titleLarge = titleLarge.merge(safeText), titleMedium = titleMedium.merge(safeText), titleSmall = titleSmall.merge(safeText),
        bodyLarge = bodyLarge.merge(safeText), bodyMedium = bodyMedium.merge(safeText), bodySmall = bodySmall.merge(safeText),
        labelLarge = labelLarge.merge(safeText), labelMedium = labelMedium.merge(safeText), labelSmall = labelSmall.merge(safeText),
    )
}

@Composable
fun ArbitrajeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, shapes = shapes, typography = typography, content = content)
}

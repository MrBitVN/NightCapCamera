package com.nightcap.camera.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val PureBlack = Color(0xFF000000)
val DarkSurface = Color(0xFF0D0D0D)
val DarkSurfaceVariant = Color(0xFF1A1A1A)

// Standard NightCap color palette (NightCap Gold / Cyan / White)
val StandardPrimary = Color(0xFFFFCC00)
val StandardAccent = Color(0xFF00E5FF)
val StandardText = Color(0xFFFFFFFF)
val StandardTextSecondary = Color(0xFFB0B0B0)

// Astronomy Red Vision Mode palette
// Preserves eye dark adaptation during night sky observation
val AstroRedPrimary = Color(0xFFFF1744)
val AstroRedAccent = Color(0xFFD50000)
val AstroRedText = Color(0xFFFF5252)
val AstroRedTextSecondary = Color(0xFFB71C1C)

@Composable
fun NightCapTheme(
    isRedVision: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = darkColorScheme(
        background = PureBlack,
        surface = DarkSurface,
        surfaceVariant = DarkSurfaceVariant,
        primary = if (isRedVision) AstroRedPrimary else StandardPrimary,
        secondary = if (isRedVision) AstroRedAccent else StandardAccent,
        onBackground = if (isRedVision) AstroRedText else StandardText,
        onSurface = if (isRedVision) AstroRedText else StandardText
    )

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

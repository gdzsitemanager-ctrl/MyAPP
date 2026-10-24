package com.webapk.builder

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val Bg = Color(0xFF0B1020)
val Surface1 = Color(0xFF131A33)
val Surface2 = Color(0xFF1E2750)
val Accent = Color(0xFF7C5CFF)
val Accent2 = Color(0xFF22D3EE)
val Ok = Color(0xFF34D399)
val Err = Color(0xFFF87171)
val Warn = Color(0xFFFBBF24)
val TextHi = Color(0xFFEAF0FF)
val TextLo = Color(0xFF93A0C8)
val Brand = Brush.horizontalGradient(listOf(Accent, Accent2))

@Composable
fun BuilderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent, onPrimary = Color.White, secondary = Accent2,
            background = Bg, onBackground = TextHi, surface = Surface1, onSurface = TextHi,
            surfaceVariant = Surface2, onSurfaceVariant = TextLo, error = Err,
        ),
        content = content,
    )
}

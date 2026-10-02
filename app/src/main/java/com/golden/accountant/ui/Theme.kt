package com.golden.accountant.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object Gold {
    val Primary = Color(0xFFB8860B)      // ذهبي داكن
    val PrimaryDark = Color(0xFF7A5806)
    val Accent = Color(0xFFE6B422)
    val Light = Color(0xFFFFF3CF)
    val Surface = Color(0xFFFFFBF2)
    val Ink = Color(0xFF2B2108)
    val header = Brush.verticalGradient(listOf(PrimaryDark, Primary, Accent))
}

@Composable
fun GoldenTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = lightColorScheme(
        primary = Gold.Primary, onPrimary = Color.White,
        secondary = Gold.Accent, background = Gold.Surface, surface = Color.White,
        primaryContainer = Gold.Light, onPrimaryContainer = Gold.Ink,
    ),
    content = content,
)

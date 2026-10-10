package com.astadeca.warehouse.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Warna disamakan dengan web (primary #0e6b76, aksen cyan) agar konsisten.
private val Primary = Color(0xFF0E6B76)
private val PrimaryDark = Color(0xFF7FD4DE)
private val Warning = Color(0xFFB45309)
private val Danger = Color(0xFFB91C1C)
private val Success = Color(0xFF15803D)

private val LightColors = lightColorScheme(
    primary = Primary,
    secondary = Color(0xFF0891B2),
    error = Danger,
)

private val DarkColors = darkColorScheme(
    primary = PrimaryDark,
    secondary = Color(0xFF67E8F9),
    error = Color(0xFFF87171),
)

@Composable
fun AstadecaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}

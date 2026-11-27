package com.mobin.tigerfiles.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFFAE6506),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFE4B0),
    onPrimaryContainer = Color(0xFF3D2500),
    secondary = Color(0xFF4E657E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE1EAF3),
    onSecondaryContainer = Color(0xFF1A2B3E),
    tertiary = Color(0xFFB64E37),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFE9EDF3),
    onBackground = Color(0xFF141D29),
    surface = Color(0xFFF7F9FC),
    onSurface = Color(0xFF141D29),
    surfaceVariant = Color(0xFFDCE4EE),
    onSurfaceVariant = Color(0xFF536174),
    outline = Color(0xFF7E8DA2),
    outlineVariant = Color(0xFFC9D3E0)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB72E),
    onPrimary = Color(0xFF211300),
    primaryContainer = Color(0xFF4B310C),
    onPrimaryContainer = Color(0xFFFFE4AC),
    secondary = Color(0xFFAABDD2),
    onSecondary = Color(0xFF1A2B3D),
    secondaryContainer = Color(0xFF1D2D40),
    onSecondaryContainer = Color(0xFFDDE8F5),
    tertiary = Color(0xFFFFA98E),
    onTertiary = Color(0xFF4C1C10),
    background = Color(0xFF080D16),
    onBackground = Color(0xFFEAF0F8),
    surface = Color(0xFF121C2B),
    onSurface = Color(0xFFEAF0F8),
    surfaceVariant = Color(0xFF253246),
    onSurfaceVariant = Color(0xFFB2C0D2),
    outline = Color(0xFF71839A),
    outlineVariant = Color(0xFF344357)
)

@Composable
fun TigerFilesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}

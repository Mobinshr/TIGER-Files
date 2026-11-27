package com.mobin.tigerfiles.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFFB86B08),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFE6B3),
    onPrimaryContainer = Color(0xFF452600),
    secondary = Color(0xFF52677F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE5EDF5),
    onSecondaryContainer = Color(0xFF1B2C40),
    tertiary = Color(0xFFB14E35),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFEBEFF4),
    onBackground = Color(0xFF151D28),
    surface = Color(0xFFF8FAFC),
    onSurface = Color(0xFF151D28),
    surfaceVariant = Color(0xFFDDE5EE),
    onSurfaceVariant = Color(0xFF536174),
    outline = Color(0xFF8291A4),
    outlineVariant = Color(0xFFCBD4DF)
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
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}

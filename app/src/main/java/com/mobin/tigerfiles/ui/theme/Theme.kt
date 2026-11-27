package com.mobin.tigerfiles.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFFC17A08),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFE9BD),
    onPrimaryContainer = Color(0xFF4A2B00),
    secondary = Color(0xFF38566A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE3EDF3),
    onSecondaryContainer = Color(0xFF172F40),
    tertiary = Color(0xFFB64F35),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF6F4EF),
    onBackground = Color(0xFF1B2025),
    surface = Color(0xFFFFFEFC),
    onSurface = Color(0xFF1B2025),
    surfaceVariant = Color(0xFFEDE9E1),
    onSurfaceVariant = Color(0xFF56534D),
    outline = Color(0xFFBDB6AA),
    outlineVariant = Color(0xFFE4DED4)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFC15A),
    onPrimary = Color(0xFF342000),
    primaryContainer = Color(0xFF624314),
    onPrimaryContainer = Color(0xFFFFE6BD),
    secondary = Color(0xFFA9C9DD),
    onSecondary = Color(0xFF183342),
    secondaryContainer = Color(0xFF2A4352),
    onSecondaryContainer = Color(0xFFD9EAF4),
    tertiary = Color(0xFFFFB49D),
    onTertiary = Color(0xFF5B2113),
    background = Color(0xFF111920),
    onBackground = Color(0xFFE9EDF1),
    surface = Color(0xFF1B2731),
    onSurface = Color(0xFFE9EDF1),
    surfaceVariant = Color(0xFF2B3944),
    onSurfaceVariant = Color(0xFFC3CDD5),
    outline = Color(0xFF64727D),
    outlineVariant = Color(0xFF354550)
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

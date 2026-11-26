package com.mobin.tigerfiles.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFFB97900),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF36566B),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFFB6533B),
    background = Color(0xFFF5F6F8),
    onBackground = Color(0xFF171C22),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF171C22),
    surfaceVariant = Color(0xFFE9EDF1),
    onSurfaceVariant = Color(0xFF4A535D)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFBE4D),
    onPrimary = Color(0xFF281900),
    secondary = Color(0xFFA9C8DD),
    onSecondary = Color(0xFF102B3B),
    tertiary = Color(0xFFFFB59F),
    background = Color(0xFF101820),
    onBackground = Color(0xFFE9EDF2),
    surface = Color(0xFF19232D),
    onSurface = Color(0xFFE9EDF2),
    surfaceVariant = Color(0xFF283642),
    onSurfaceVariant = Color(0xFFC0CAD4)
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

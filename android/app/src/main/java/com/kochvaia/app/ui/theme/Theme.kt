package com.kochvaia.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = StarAmber,
    onPrimary = Slate,
    secondary = Mint,
    onSecondary = Slate,
    tertiary = Sky,
    background = Cream,
    onBackground = Slate,
    surface = Cream,
    onSurface = Slate,
    surfaceVariant = Cream,
    onSurfaceVariant = SlateMuted,
)

private val DarkColors = darkColorScheme(
    primary = StarAmber,
    onPrimary = Slate,
    secondary = Mint,
    onSecondary = Slate,
    tertiary = Sky,
)

@Composable
fun KochvaiaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = MaterialTheme.typography,
        content = content,
    )
}

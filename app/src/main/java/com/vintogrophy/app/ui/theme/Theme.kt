package com.vintogrophy.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = Amber,
    onPrimary = Espresso,
    secondary = Espresso,
    onSecondary = Cream,
    background = WarmWhite,
    onBackground = Charcoal,
    surface = WarmWhite,
    onSurface = Charcoal,
)

private val DarkColors = darkColorScheme(
    primary = Amber,
    onPrimary = Espresso,
    secondary = Cream,
    onSecondary = Espresso,
    background = Charcoal,
    onBackground = Cream,
    surface = Charcoal,
    onSurface = Cream,
)

@Composable
fun VintogrophyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}

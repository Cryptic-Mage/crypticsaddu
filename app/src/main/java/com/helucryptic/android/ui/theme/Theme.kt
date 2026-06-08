package com.helucryptic.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    background       = DarkBackground,
    surface          = DarkSurface,
    surfaceVariant   = DarkSurfaceRaised,
    primary          = DarkAccent,
    onPrimary        = DarkTextPrimary,
    onSurface        = DarkTextPrimary,
    onBackground     = DarkTextPrimary,
    onSurfaceVariant = DarkTextSecondary,
    error            = DarkDestructive,
    outline          = DarkBorder
)

private val LightColorScheme = lightColorScheme(
    background       = LightBackground,
    surface          = LightSurface,
    surfaceVariant   = LightSurfaceRaised,
    primary          = LightAccent,
    onPrimary        = LightOnPrimary,
    onSurface        = LightTextPrimary,
    onBackground     = LightTextPrimary,
    onSurfaceVariant = LightTextSecondary,
    error            = LightDestructive,
    outline          = LightBorder
)

@Composable
fun HelucrypticTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography  = HelucrypticTypography,
        content     = content
    )
}

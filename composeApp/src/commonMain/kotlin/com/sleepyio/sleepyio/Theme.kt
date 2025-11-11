package com.sleepyio.sleepyio

import androidx.compose.ui.graphics.Color
import com.composeunstyled.theme.ThemeProperty
import com.composeunstyled.theme.ThemeToken
import com.composeunstyled.theme.buildTheme

val colors = ThemeProperty<Color>("colors")
val background = ThemeToken<Color>("background")
val onBackground = ThemeToken<Color>("on_background")
val primary = ThemeToken<Color>("primary")
val secondary = ThemeToken<Color>("secondary")
val error = ThemeToken<Color>("error")
val surface = ThemeToken<Color>("surface")
val onSurface = ThemeToken<Color>("on_surface")

val MyTheme = buildTheme {
    properties[colors] = mapOf(
        background to Color(0xFF000000),      // Pure black background
        onBackground to Color(0xFFFFFFFF),   // Pure white text for maximum contrast
        primary to Color(0xFF00FF00),        // Bright green
        secondary to Color(0xFFFF6600),      // Bright orange
        error to Color(0xFFFF0000),          // Pure red for errors
        surface to Color(0xFF0066FF),        // Bright blue surface
        onSurface to Color(0xFFFFFF00),      // Bright yellow for surface text
    )
}
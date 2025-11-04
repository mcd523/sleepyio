package com.sleepyio.sleepyio

import androidx.compose.ui.graphics.Color
import com.composeunstyled.theme.ThemeProperty
import com.composeunstyled.theme.ThemeToken
import com.composeunstyled.theme.buildTheme

val colors = ThemeProperty<Color>("colors")
val background = ThemeToken<Color>("background")
val onBackground = ThemeToken<Color>("on_background")

val MyTheme = buildTheme {
    properties[colors] = mapOf(
        background to Color(0x0f1069),      // Subtle off-white background
        onBackground to Color(0xFF2E2E2E),   // Dark gray text for readability
        ThemeToken<Color>("primary") to Color(0xFF6366F1),      // Modern indigo
        ThemeToken<Color>("secondary") to Color(0xFF8B5CF6),    // Soft purple accent
        ThemeToken<Color>("error") to Color(0xFFEF4444),        // Modern red for errors
        ThemeToken<Color>("surface") to Color(0x00000000),      // Clean white surface
        ThemeToken<Color>("on_surface") to Color(0xFF374151),   // Medium gray for surface text
    )
}
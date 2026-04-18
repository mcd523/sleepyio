package com.sleepyio.sleepyio

import androidx.compose.ui.graphics.Color
import com.composeunstyled.theme.ThemeProperty
import com.composeunstyled.theme.ThemeToken
import com.composeunstyled.theme.buildTheme

// ---------------------------------------------------------------------------
// sleepy.io design-system tokens (dark theme primary surface).
//
// Token names are intentionally stable — App.kt, LeagueTable.kt, TeamsView.kt,
// LeagueBfsView.kt all read values as `Theme[colors][<token>]`. Adding new
// tokens here is safe; renaming or removing the existing ones is not.
// See docs/ux/DESIGN_SYSTEM.md for usage guidance and contrast ratios.
// ---------------------------------------------------------------------------

val colors = ThemeProperty<Color>("colors")

// Existing tokens (must remain — consumed by rest of the app)
val background = ThemeToken<Color>("background")
val onBackground = ThemeToken<Color>("on_background")
val primary = ThemeToken<Color>("primary")
val secondary = ThemeToken<Color>("secondary")
val error = ThemeToken<Color>("error")
val surface = ThemeToken<Color>("surface")
val onSurface = ThemeToken<Color>("on_surface")

// New semantic tokens
val surfaceElevated = ThemeToken<Color>("surface_elevated")
val onSurfaceMuted = ThemeToken<Color>("on_surface_muted")
val onPrimary = ThemeToken<Color>("on_primary")
val positive = ThemeToken<Color>("positive")
val negative = ThemeToken<Color>("negative")
val warning = ThemeToken<Color>("warning")
val info = ThemeToken<Color>("info")
val outline = ThemeToken<Color>("outline")

val MyTheme = buildTheme {
    properties[colors] = mapOf(
        // Base surfaces
        background to Color(0xFF0B0F14),        // Near-black, slight blue cast
        onBackground to Color(0xFFE6EAF0),      // High-contrast off-white
        surface to Color(0xFF111822),           // Elevated from background
        surfaceElevated to Color(0xFF1A2330),   // Cards on top of surface
        onSurface to Color(0xFFE6EAF0),         // Primary text on surface
        onSurfaceMuted to Color(0xFF98A2B3),    // Secondary/labels on surface
        outline to Color(0xFF2A3442),           // Dividers, borders, chip strokes

        // Brand
        primary to Color(0xFFF5A524),           // Warm amber accent
        onPrimary to Color(0xFF0B0F14),         // Text/icons rendered on primary
        secondary to Color(0xFF5B8DEF),         // Calm blue for secondary actions

        // Semantic / status
        positive to Color(0xFF16A34A),          // Start / green-light
        negative to Color(0xFFDC2626),          // Sit / red-light / risk
        warning to Color(0xFFEAB308),           // Questionable / caution
        info to Color(0xFF3B82F6),              // Informational
        error to Color(0xFFEF4444),             // Errors (alias of negative-ish)
    )
}

package com.sleepyio.sleepyio

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Sleeper-inspired color palette
private val SleeperNavy = Color(0xFF09274B)
private val SleeperDarkNavy = Color(0xFF061A33)
private val SleeperSlate = Color(0xFF313C55)
private val SleeperTeal = Color(0xFF1ABC9C)
private val SleeperTealDark = Color(0xFF16A085)
private val SleeperLightGray = Color(0xFFE0E0E0)
private val SleeperMediumGray = Color(0xFF8E99A4)
private val SleeperSurface = Color(0xFF0D2F56)
private val SleeperSurfaceVariant = Color(0xFF1A3A5C)
private val SleeperError = Color(0xFFEF5350)
private val SleeperErrorContainer = Color(0xFF3D1517)
private val SleeperTertiary = Color(0xFF4CAF50)
private val SleeperTertiaryContainer = Color(0xFF1B3A1D)
private val SleeperSecondary = Color(0xFFFF9800)
private val SleeperSecondaryContainer = Color(0xFF3D2800)

private val SleeperColorScheme = darkColorScheme(
    primary = SleeperTeal,
    onPrimary = Color.White,
    primaryContainer = SleeperSlate,
    onPrimaryContainer = SleeperLightGray,
    secondary = SleeperSecondary,
    onSecondary = Color.Black,
    secondaryContainer = SleeperSecondaryContainer,
    onSecondaryContainer = SleeperLightGray,
    tertiary = SleeperTertiary,
    onTertiary = Color.Black,
    tertiaryContainer = SleeperTertiaryContainer,
    onTertiaryContainer = SleeperLightGray,
    error = SleeperError,
    onError = Color.White,
    errorContainer = SleeperErrorContainer,
    onErrorContainer = SleeperLightGray,
    background = SleeperDarkNavy,
    onBackground = SleeperLightGray,
    surface = SleeperSurface,
    onSurface = SleeperLightGray,
    surfaceVariant = SleeperSurfaceVariant,
    onSurfaceVariant = SleeperMediumGray,
)

@Composable
fun SleeperTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SleeperColorScheme,
        content = content
    )
}

object SleeperSpacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val xxl = 32.dp
}

object SleeperType {
    val displayLarge = 36.sp
    val displayMedium = 32.sp
    val headline = 24.sp
    val titleLarge = 20.sp
    val titleMedium = 18.sp
    val body = 14.sp
    val caption = 12.sp
    val statValue = 28.sp
}

object BfsDepthColors {
    private val colors = listOf(
        Color(0xFF2196F3), // Blue
        Color(0xFF4CAF50), // Green
        Color(0xFFFF9800), // Orange
        Color(0xFF9C27B0), // Purple
        Color(0xFFF44336), // Red
    )

    fun getDepthColor(depth: Int): Color {
        return colors[depth % colors.size]
    }
}

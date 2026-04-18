package com.sleepyio.sleepyio.platform.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo

/**
 * Material3 window-size-class buckets, reimplemented locally so we don't
 * depend on the (still-KMP-incomplete) `material3-window-size-class`
 * artifact. Break-points match Google's public guidance.
 */
enum class WindowWidthClass {
    /** < 600dp — phones in portrait, small foldables folded. */
    COMPACT,

    /** 600dp..840dp — most phones in landscape, small tablets in portrait. */
    MEDIUM,

    /** > 840dp — large tablets, desktops, split-screen power users. */
    EXPANDED,
}

enum class WindowHeightClass {
    /** < 480dp — phones in landscape. */
    COMPACT,

    /** 480dp..900dp — typical phone portrait / tablet landscape. */
    MEDIUM,

    /** > 900dp — tablets in portrait, desktop. */
    EXPANDED,
}

/**
 * Resolved size of the current window plus its two classification axes.
 *
 * Use this instead of `LocalConfiguration` (Android-only) or hard-coded
 * pixel thresholds; it works identically on every target because it
 * derives from [LocalWindowInfo].
 */
data class WindowSize(
    val widthDp: Int,
    val heightDp: Int,
    val widthClass: WindowWidthClass,
    val heightClass: WindowHeightClass,
)

/**
 * @return the current window size and classification. Recomposes on
 *   window resize (desktop drag, foldable unfold, orientation change).
 */
@Composable
fun rememberWindowSize(): WindowSize {
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val sizePx = windowInfo.containerSize
    return remember(sizePx.width, sizePx.height, density.density) {
        val widthDp = with(density) { sizePx.width.toDp().value.toInt() }
        val heightDp = with(density) { sizePx.height.toDp().value.toInt() }
        WindowSize(
            widthDp = widthDp,
            heightDp = heightDp,
            widthClass = when {
                widthDp < 600 -> WindowWidthClass.COMPACT
                widthDp < 840 -> WindowWidthClass.MEDIUM
                else -> WindowWidthClass.EXPANDED
            },
            heightClass = when {
                heightDp < 480 -> WindowHeightClass.COMPACT
                heightDp < 900 -> WindowHeightClass.MEDIUM
                else -> WindowHeightClass.EXPANDED
            },
        )
    }
}

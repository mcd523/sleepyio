package com.sleepyio.sleepyio.ui.recommendation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Responsive size bundle for advisor-area screens. Mirrors the shape of
 * [com.sleepyio.sleepyio.ui.league.LeagueStateScreen]'s `getResponsiveSizes()`
 * so new screens get the same breakpoint behavior without hand-rolled `sp`.
 */
data class AdvisorSizes(
    val displaySize: TextUnit,
    val headlineLarge: TextUnit,
    val headlineSmall: TextUnit,
    val titleMedium: TextUnit,
    val bodyLarge: TextUnit,
    val bodyMedium: TextUnit,
    val labelLarge: TextUnit,
    val labelSmall: TextUnit,
    val gutter: Dp,
    val cardPadding: Dp,
    val spacingXs: Dp,
    val spacingSm: Dp,
    val spacingMd: Dp,
    val spacingLg: Dp,
    val cardRadius: Dp,
    val chipRadius: Dp,
    val pillRadius: Dp,
)

@Composable
fun rememberAdvisorSizes(): AdvisorSizes {
    val screenWidth = LocalWindowInfo.current.containerSize.width
    return when {
        screenWidth >= 1200 -> AdvisorSizes(
            displaySize = 32.sp,
            headlineLarge = 24.sp,
            headlineSmall = 20.sp,
            titleMedium = 16.sp,
            bodyLarge = 15.sp,
            bodyMedium = 14.sp,
            labelLarge = 13.sp,
            labelSmall = 11.sp,
            gutter = 20.dp,
            cardPadding = 20.dp,
            spacingXs = 4.dp,
            spacingSm = 8.dp,
            spacingMd = 12.dp,
            spacingLg = 16.dp,
            cardRadius = 12.dp,
            chipRadius = 8.dp,
            pillRadius = 12.dp,
        )
        screenWidth >= 800 -> AdvisorSizes(
            displaySize = 30.sp,
            headlineLarge = 22.sp,
            headlineSmall = 18.sp,
            titleMedium = 15.sp,
            bodyLarge = 14.sp,
            bodyMedium = 13.sp,
            labelLarge = 12.sp,
            labelSmall = 11.sp,
            gutter = 16.dp,
            cardPadding = 16.dp,
            spacingXs = 4.dp,
            spacingSm = 8.dp,
            spacingMd = 12.dp,
            spacingLg = 16.dp,
            cardRadius = 12.dp,
            chipRadius = 8.dp,
            pillRadius = 12.dp,
        )
        else -> AdvisorSizes(
            displaySize = 28.sp,
            headlineLarge = 20.sp,
            headlineSmall = 17.sp,
            titleMedium = 14.sp,
            bodyLarge = 13.sp,
            bodyMedium = 12.sp,
            labelLarge = 12.sp,
            labelSmall = 10.sp,
            gutter = 12.dp,
            cardPadding = 12.dp,
            spacingXs = 4.dp,
            spacingSm = 6.dp,
            spacingMd = 10.dp,
            spacingLg = 14.dp,
            cardRadius = 12.dp,
            chipRadius = 8.dp,
            pillRadius = 12.dp,
        )
    }
}

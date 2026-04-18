package com.sleepyio.sleepyio.ui.recommendation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.composeunstyled.theme.Theme
import com.sleepyio.sleepyio.colors
import com.sleepyio.sleepyio.insight.model.MatchupGrade
import com.sleepyio.sleepyio.negative
import com.sleepyio.sleepyio.onSurfaceMuted
import com.sleepyio.sleepyio.outline
import com.sleepyio.sleepyio.positive

/**
 * Five-segment horizontal grade bar. Segments are filled with the grade's
 * sentiment color (positive for ELITE/GOOD, negative for TOUGH/NIGHTMARE) and
 * empty segments are outline-stroked so the bar still reads without color.
 */
@Composable
fun MatchupGradeBar(
    grade: MatchupGrade,
    modifier: Modifier = Modifier,
) {
    val spec = when (grade) {
        MatchupGrade.ELITE -> Spec(filled = 5, color = Theme[colors][positive])
        MatchupGrade.GOOD -> Spec(filled = 4, color = Theme[colors][positive])
        MatchupGrade.NEUTRAL -> Spec(filled = 0, color = Theme[colors][onSurfaceMuted])
        MatchupGrade.TOUGH -> Spec(filled = 2, color = Theme[colors][negative])
        MatchupGrade.NIGHTMARE -> Spec(filled = 5, color = Theme[colors][negative])
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(5) { index ->
            val isFilled = when (grade) {
                MatchupGrade.NEUTRAL -> false
                else -> index < spec.filled
            }
            Segment(
                filled = isFilled,
                color = spec.color,
                modifier = Modifier
                    .weight(1f)
                    .height(12.dp),
            )
        }
    }
}

private data class Spec(val filled: Int, val color: Color)

@Composable
private fun Segment(
    filled: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(4.dp)
    if (filled) {
        Box(
            modifier = modifier
                .clip(shape)
                .background(color),
        )
    } else {
        Box(
            modifier = modifier
                .clip(shape)
                .border(1.dp, Theme[colors][outline], shape),
        )
    }
}

package com.sleepyio.sleepyio.ui.recommendation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composeunstyled.theme.Theme
import com.sleepyio.sleepyio.colors
import com.sleepyio.sleepyio.insight.model.ProjectionRange
import com.sleepyio.sleepyio.onSurface
import com.sleepyio.sleepyio.onSurfaceMuted
import com.sleepyio.sleepyio.outline
import com.sleepyio.sleepyio.surfaceElevated

/**
 * Single pill showing `floor - ceiling` above a bold median. Width grows
 * with content; caller provides outer padding/alignment.
 */
@Composable
fun ProjectionRangePill(
    range: ProjectionRange,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Theme[colors][surfaceElevated])
            .border(1.dp, Theme[colors][outline], RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = formatRange(range.floor, range.ceiling),
            color = Theme[colors][onSurfaceMuted],
        )
        Text(
            text = "${formatOneDecimal(range.median)} median",
            color = Theme[colors][onSurface],
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun formatRange(floor: Double, ceiling: Double): String =
    "${formatOneDecimal(floor)} \u2013 ${formatOneDecimal(ceiling)}"

private fun formatOneDecimal(value: Double): String {
    val rounded = kotlin.math.round(value * 10.0) / 10.0
    val whole = rounded.toLong()
    val frac = kotlin.math.round((rounded - whole) * 10.0).toInt()
    val fracAbs = if (frac < 0) -frac else frac
    return "$whole.$fracAbs"
}

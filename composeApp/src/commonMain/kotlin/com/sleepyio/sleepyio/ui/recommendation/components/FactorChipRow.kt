package com.sleepyio.sleepyio.ui.recommendation.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composeunstyled.theme.Theme
import com.sleepyio.sleepyio.colors
import com.sleepyio.sleepyio.negative
import com.sleepyio.sleepyio.onSurface
import com.sleepyio.sleepyio.onSurfaceMuted
import com.sleepyio.sleepyio.outline
import com.sleepyio.sleepyio.positive
import com.sleepyio.sleepyio.recommendation.model.Factor
import com.sleepyio.sleepyio.recommendation.model.FactorDirection

/**
 * Renders a list of [Factor] as outlined chips. Positive gets a green border
 * + upward glyph, negative red border + downward glyph, neutral outline + dot.
 * Collapses beyond the [maxVisible] cap to a compact `+N more` chip.
 *
 * Horizontal scroll is enabled so narrow screens never overflow.
 */
@Composable
fun FactorChipRow(
    factors: List<Factor>,
    modifier: Modifier = Modifier,
    maxVisible: Int = 5,
) {
    if (factors.isEmpty()) return
    val visible = factors.take(maxVisible)
    val overflow = factors.size - visible.size

    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        visible.forEach { FactorChip(it) }
        if (overflow > 0) {
            OverflowChip(overflow)
        }
    }
}

@Composable
private fun FactorChip(factor: Factor) {
    val (border: Color, text: Color, glyph: String) = when (factor.direction) {
        FactorDirection.POSITIVE -> Triple(Theme[colors][positive], Theme[colors][onSurface], "\u25B2")
        FactorDirection.NEGATIVE -> Triple(Theme[colors][negative], Theme[colors][onSurface], "\u25BC")
        FactorDirection.NEUTRAL -> Triple(Theme[colors][outline], Theme[colors][onSurfaceMuted], "\u2022")
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = glyph, color = border, fontWeight = FontWeight.Bold)
        Text(text = factor.label, color = text)
    }
}

@Composable
private fun OverflowChip(count: Int) {
    Text(
        text = "+$count more",
        color = Theme[colors][onSurfaceMuted],
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, Theme[colors][outline], RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

package com.sleepyio.sleepyio.ui.recommendation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composeunstyled.theme.Theme
import com.sleepyio.sleepyio.colors
import com.sleepyio.sleepyio.onPrimary
import com.sleepyio.sleepyio.onSurfaceMuted
import com.sleepyio.sleepyio.outline
import com.sleepyio.sleepyio.positive
import com.sleepyio.sleepyio.recommendation.model.Confidence
import com.sleepyio.sleepyio.warning

/**
 * Filled pill rendering a [Confidence] tier per DESIGN_SYSTEM.md.
 *
 * HIGH = positive bg, MED = warning bg, LOW = outline bg with muted fg.
 */
@Composable
fun ConfidenceBadge(
    confidence: Confidence,
    modifier: Modifier = Modifier,
) {
    val (bg: Color, fg: Color, label: String) = when (confidence) {
        Confidence.HIGH -> Triple(Theme[colors][positive], Theme[colors][onPrimary], "HIGH")
        Confidence.MEDIUM -> Triple(Theme[colors][warning], Theme[colors][onPrimary], "MED")
        Confidence.LOW -> Triple(Theme[colors][outline], Theme[colors][onSurfaceMuted], "LOW")
    }
    Text(
        text = "Confidence: $label",
        color = fg,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

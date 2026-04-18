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
import com.sleepyio.sleepyio.insight.model.InjuryStatus
import com.sleepyio.sleepyio.negative
import com.sleepyio.sleepyio.onPrimary
import com.sleepyio.sleepyio.warning

/**
 * Tiny uppercase tag rendering the player's injury designation.
 *
 * HEALTHY / null hides the tag entirely so healthy players get no visual noise.
 * Q uses warning bg; D uses 70%-opacity negative; O/IR uses full negative.
 */
@Composable
fun InjuryTag(
    status: InjuryStatus,
    modifier: Modifier = Modifier,
) {
    val designation = status.designation?.uppercase()?.trim().orEmpty()
    val (bg: Color, fg: Color, label: String) = when {
        designation.isEmpty() || designation == "HEALTHY" -> return
        designation.startsWith("Q") ->
            Triple(Theme[colors][warning], Theme[colors][onPrimary], "Q")
        designation.startsWith("D") ->
            Triple(Theme[colors][negative].copy(alpha = 0.7f), Theme[colors][onPrimary], "D")
        designation.startsWith("O") || designation.startsWith("IR") ->
            Triple(Theme[colors][negative], Theme[colors][onPrimary], if (designation.startsWith("IR")) "IR" else "O")
        else ->
            Triple(Theme[colors][warning], Theme[colors][onPrimary], designation.take(3))
    }
    Text(
        text = label,
        color = fg,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

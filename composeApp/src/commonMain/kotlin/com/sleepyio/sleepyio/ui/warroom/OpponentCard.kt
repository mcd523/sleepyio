package com.sleepyio.sleepyio.ui.warroom

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sleepyio.sleepyio.SleeperSpacing
import com.sleepyio.sleepyio.SleeperType
import com.sleepyio.sleepyio.client.model.user.SleeperUser

private val ThreatRed = Color(0xFFEF5350)
private val ThreatOrange = Color(0xFFFF9800)
private val ThreatTeal = Color(0xFF1ABC9C)

private fun threatColor(score: Int): Color = when {
    score > 70 -> ThreatRed
    score > 40 -> ThreatOrange
    else -> ThreatTeal
}

private val IntelChipColors = mapOf(
    "TRADE" to Color(0xFFFF9800),
    "WAIVER" to Color(0xFF1ABC9C),
    "CONVERGENT" to Color(0xFF7C3AED),
    "SHADOW" to Color(0xFF5A67D8),
    "ACTIVE" to Color(0xFF4CAF50),
    "RIVAL" to Color(0xFFEF5350)
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OpponentCard(
    user: SleeperUser,
    sharedLeagueCount: Int,
    shadowLeagueCount: Int,
    threatScore: Int,
    intelChips: List<String>,
    isThisWeeksOpponent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isThisWeeksOpponent) ThreatRed else MaterialTheme.colorScheme.surfaceVariant
    val borderWidth = if (isThisWeeksOpponent) 2.dp else 1.dp

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(borderWidth, borderColor, RoundedCornerShape(12.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SleeperSpacing.md),
            verticalAlignment = Alignment.Top
        ) {
            // Avatar circle
            val displayChar = (user.displayName ?: user.userName ?: "?").first().uppercaseChar()
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(threatColor(threatScore).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = displayChar.toString(),
                    fontSize = SleeperType.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = threatColor(threatScore)
                )
            }

            Spacer(modifier = Modifier.width(SleeperSpacing.md))

            // Info column
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.displayName ?: user.userName ?: "Unknown",
                    fontSize = SleeperType.body,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (isThisWeeksOpponent) {
                    Spacer(modifier = Modifier.height(SleeperSpacing.xxs))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = ThreatRed.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "THIS WEEK'S OPPONENT",
                            fontSize = SleeperType.caption,
                            fontWeight = FontWeight.ExtraBold,
                            color = ThreatRed,
                            modifier = Modifier.padding(
                                horizontal = SleeperSpacing.xs,
                                vertical = SleeperSpacing.xxs
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(SleeperSpacing.xs))

                Text(
                    text = "$sharedLeagueCount shared  \u00B7  $shadowLeagueCount shadow",
                    fontSize = SleeperType.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (intelChips.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(SleeperSpacing.sm))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(SleeperSpacing.xs),
                        verticalArrangement = Arrangement.spacedBy(SleeperSpacing.xxs)
                    ) {
                        intelChips.forEach { chip ->
                            IntelChip(chip)
                        }
                    }
                }
            }

            // Threat score
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(start = SleeperSpacing.sm)
            ) {
                Text(
                    text = "$threatScore",
                    fontSize = SleeperType.statValue,
                    fontWeight = FontWeight.ExtraBold,
                    color = threatColor(threatScore)
                )
                Text(
                    text = "THREAT",
                    fontSize = SleeperType.caption,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun IntelChip(label: String) {
    val chipColor = IntelChipColors[label.uppercase()] ?: MaterialTheme.colorScheme.primary

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = chipColor.copy(alpha = 0.15f)
    ) {
        Text(
            text = label.uppercase(),
            fontSize = SleeperType.caption,
            fontWeight = FontWeight.SemiBold,
            color = chipColor,
            modifier = Modifier.padding(horizontal = SleeperSpacing.xs, vertical = SleeperSpacing.xxs)
        )
    }
}

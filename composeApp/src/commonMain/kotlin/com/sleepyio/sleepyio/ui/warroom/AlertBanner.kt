package com.sleepyio.sleepyio.ui.warroom

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sleepyio.sleepyio.SleeperSpacing
import com.sleepyio.sleepyio.SleeperType

data class Alert(
    val message: String,
    val severity: AlertSeverity,
    val timestamp: Long
)

enum class AlertSeverity { HIGH, MEDIUM, LOW }

@Composable
fun AlertBanner(
    alerts: List<Alert>,
    modifier: Modifier = Modifier
) {
    if (alerts.isEmpty()) return

    var expanded by remember { mutableStateOf(false) }
    val sortedAlerts = alerts.sortedByDescending { it.severity.ordinal * -1 } // HIGH first

    val topAlert = sortedAlerts.first()
    val gradient = when (topAlert.severity) {
        AlertSeverity.HIGH -> Brush.horizontalGradient(
            listOf(Color(0xFF8B0000), Color(0xFFB22222), Color(0xFF8B0000))
        )
        AlertSeverity.MEDIUM -> Brush.horizontalGradient(
            listOf(Color(0xFF8B4513), Color(0xFFCC7722), Color(0xFF8B4513))
        )
        AlertSeverity.LOW -> Brush.horizontalGradient(
            listOf(Color(0xFF1A3A5C), Color(0xFF2A4A6C), Color(0xFF1A3A5C))
        )
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .background(gradient)
                .clickable { expanded = !expanded }
                .padding(SleeperSpacing.md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SleeperSpacing.sm),
                    modifier = Modifier.weight(1f)
                ) {
                    AlertBadge(topAlert.severity)
                    Text(
                        text = topAlert.message,
                        fontSize = SleeperType.body,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }

                if (alerts.size > 1) {
                    Text(
                        text = if (expanded) "COLLAPSE" else "+${alerts.size - 1} MORE",
                        fontSize = SleeperType.caption,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded && alerts.size > 1,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(top = SleeperSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(SleeperSpacing.xs)
                ) {
                    sortedAlerts.drop(1).forEach { alert ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(SleeperSpacing.sm)
                        ) {
                            AlertBadge(alert.severity)
                            Text(
                                text = alert.message,
                                fontSize = SleeperType.caption,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertBadge(severity: AlertSeverity) {
    val badgeColor = when (severity) {
        AlertSeverity.HIGH -> Color(0xFFFF4444)
        AlertSeverity.MEDIUM -> Color(0xFFFF9800)
        AlertSeverity.LOW -> Color(0xFF1ABC9C)
    }

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = badgeColor
    ) {
        Text(
            text = "ALERT",
            fontSize = SleeperType.caption,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            modifier = Modifier.padding(horizontal = SleeperSpacing.xs, vertical = SleeperSpacing.xxs)
        )
    }
}

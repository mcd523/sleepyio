package com.sleepyio.sleepyio.ui.warroom

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sleepyio.sleepyio.SleeperSpacing
import com.sleepyio.sleepyio.SleeperType
import com.sleepyio.sleepyio.intel.model.ActivityType

private val TradeColor = Color(0xFFFF9800)
private val WaiverColor = Color(0xFF1ABC9C)
private val FreeAgentColor = Color(0xFF4CAF50)
private val DropColor = Color(0xFFEF5350)
private val ShadowColor = Color(0xFF5A67D8)

data class ShadowFeedEvent(
    val type: ActivityType,
    val userName: String,
    val leagueName: String,
    val isShadowLeague: Boolean,
    val description: String,
    val annotation: String?,
    val timestamp: Long
)

@Composable
fun ShadowActivityFeed(
    events: List<ShadowFeedEvent>,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = SleeperSpacing.sm)
        ) {
            Text(
                text = "Shadow Activity",
                fontSize = SleeperType.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (isLoading) {
                Spacer(modifier = Modifier.width(SleeperSpacing.sm))
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp
                )
            }
        }

        when {
            events.isEmpty() && !isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = SleeperSpacing.xxl),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No shadow activity detected",
                        fontSize = SleeperType.body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            events.isEmpty() && isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = SleeperSpacing.xxl),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(SleeperSpacing.sm))
                        Text(
                            text = "Scanning shadow leagues...",
                            fontSize = SleeperType.body,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            else -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(SleeperSpacing.sm)
                ) {
                    items(events) { event ->
                        ShadowFeedEventCard(event)
                    }
                }
            }
        }
    }
}

@Composable
private fun ShadowFeedEventCard(event: ShadowFeedEvent) {
    val borderColor = when (event.type) {
        ActivityType.TRADE -> TradeColor
        ActivityType.WAIVER_CLAIM -> WaiverColor
        ActivityType.FREE_AGENT_ADD -> FreeAgentColor
        ActivityType.DROP -> DropColor
    }

    val typeLabel = when (event.type) {
        ActivityType.TRADE -> "TRADE"
        ActivityType.WAIVER_CLAIM -> "WAIVER"
        ActivityType.FREE_AGENT_ADD -> "FREE AGENT"
        ActivityType.DROP -> "DROP"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 0.dp, topEnd = 12.dp, bottomEnd = 12.dp, bottomStart = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Surface(
                modifier = Modifier
                    .width(3.dp)
                    .defaultMinSize(minHeight = 60.dp),
                color = borderColor
            ) {}

            Column(modifier = Modifier.padding(SleeperSpacing.sm + SleeperSpacing.xs)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = typeLabel,
                        fontSize = SleeperType.caption,
                        fontWeight = FontWeight.SemiBold,
                        color = borderColor
                    )

                    if (event.isShadowLeague) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = ShadowColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "SHADOW",
                                fontSize = SleeperType.caption,
                                fontWeight = FontWeight.Bold,
                                color = ShadowColor,
                                modifier = Modifier.padding(
                                    horizontal = SleeperSpacing.xs,
                                    vertical = SleeperSpacing.xxs
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(SleeperSpacing.xxs))

                Text(
                    text = event.leagueName,
                    fontSize = SleeperType.caption,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(SleeperSpacing.xs))

                Text(
                    text = "${event.userName}: ${event.description}",
                    fontSize = SleeperType.body,
                    color = MaterialTheme.colorScheme.onSurface
                )

                event.annotation?.let { annotation ->
                    Spacer(modifier = Modifier.height(SleeperSpacing.xs))
                    Text(
                        text = annotation,
                        fontSize = SleeperType.caption,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

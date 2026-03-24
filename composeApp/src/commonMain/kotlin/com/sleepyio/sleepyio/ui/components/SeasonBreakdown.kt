package com.sleepyio.sleepyio.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.sleepyio.sleepyio.SleeperSpacing
import com.sleepyio.sleepyio.SleeperType

@Composable
fun <T> SeasonBreakdown(
    seasonData: Map<String, T>,
    renderSeason: @Composable (season: String, data: T) -> Unit
) {
    if (seasonData.size <= 1) return

    var expanded by remember { mutableStateOf(false) }

    Column {
        Spacer(modifier = Modifier.height(SleeperSpacing.sm))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = SleeperSpacing.xs),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (expanded) "Hide per-season breakdown" else "Show per-season breakdown (${seasonData.size} seasons)",
                fontSize = SleeperType.caption,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = if (expanded) "^" else "v",
                fontSize = SleeperType.caption,
                color = MaterialTheme.colorScheme.primary
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(SleeperSpacing.sm)) {
                for ((season, data) in seasonData.entries.sortedByDescending { it.key }) {
                    renderSeason(season, data)
                }
            }
        }
    }
}

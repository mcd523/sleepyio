package com.sleepyio.sleepyio.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sleepyio.sleepyio.SleeperSpacing
import com.sleepyio.sleepyio.SleeperType
import com.sleepyio.sleepyio.client.model.league.SleeperLeague
import com.sleepyio.sleepyio.intel.InsightModule
import com.sleepyio.sleepyio.intel.LeaguemateRepository

@Composable
fun <T> InsightCard(
    module: InsightModule<T>,
    targetUserId: String,
    myUserId: String,
    leagueHistory: Map<Long, List<SleeperLeague>>,
    repository: LeaguemateRepository,
    modifier: Modifier = Modifier
) {
    var state by remember { mutableStateOf<InsightCardState<T>>(InsightCardState.Loading()) }

    LaunchedEffect(targetUserId, module.id) {
        state = InsightCardState.Loading()
        try {
            val result = module.analyze(targetUserId, myUserId, leagueHistory, repository)
            state = if (result != null) {
                InsightCardState.Loaded(result)
            } else {
                InsightCardState.Empty()
            }
        } catch (e: Exception) {
            state = InsightCardState.Error(e.message ?: "Analysis failed")
        }
    }

    when (val currentState = state) {
        is InsightCardState.Loading -> {
            Card(
                modifier = modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(SleeperSpacing.xl),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.height(SleeperSpacing.sm))
                        Text(
                            text = "Loading ${module.displayName}...",
                            fontSize = SleeperType.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        is InsightCardState.Loaded -> {
            module.Render(currentState.data, modifier)
        }

        is InsightCardState.Empty -> {
            // Don't render anything for modules with no data
        }

        is InsightCardState.Error -> {
            Card(
                modifier = modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Column(modifier = Modifier.padding(SleeperSpacing.md)) {
                    Text(
                        text = module.displayName,
                        fontSize = SleeperType.body,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(SleeperSpacing.xs))
                    Text(
                        text = currentState.message,
                        fontSize = SleeperType.caption,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}

private sealed class InsightCardState<T> {
    class Loading<T> : InsightCardState<T>()
    data class Loaded<T>(val data: T) : InsightCardState<T>()
    class Empty<T> : InsightCardState<T>()
    data class Error<T>(val message: String) : InsightCardState<T>()
}

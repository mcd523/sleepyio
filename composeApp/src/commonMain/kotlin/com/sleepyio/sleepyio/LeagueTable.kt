package com.sleepyio.sleepyio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight

import androidx.compose.material3.MaterialTheme
import com.sleepyio.sleepyio.client.SleeperClient
import com.sleepyio.sleepyio.client.model.league.SleeperLeague
import com.sleepyio.sleepyio.client.model.user.SleeperUser
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun LeagueTable(
    user: SleeperUser,
    onLeagueClick: (SleeperLeague) -> Unit = {},
    preloadedLeagues: List<SleeperLeague>? = null
) {
    val client = remember { SleeperClient }
    val scope = rememberCoroutineScope()
    var leagues: List<SleeperLeague> by remember { mutableStateOf(preloadedLeagues ?: listOf()) }
    var isLoading by remember { mutableStateOf(preloadedLeagues == null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Load leagues when component is first created
    LaunchedEffect(user.userId) {
        if (preloadedLeagues != null) return@LaunchedEffect
        scope.launch {
            try {
                isLoading = true
                errorMessage = null
                leagues = client.getLeaguesForUser(user.userId.toString(), "nfl", "2025")
            } catch (e: Exception) {
                errorMessage = "Failed to load leagues: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    Surface(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface)
                .safeContentPadding()
                .fillMaxSize()
        ) {
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        androidx.compose.material3.CircularProgressIndicator()
                    }
                }

                errorMessage != null -> {
                    androidx.compose.material3.Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(SleeperSpacing.md),
                        colors = androidx.compose.material3.CardDefaults.cardColors(
                            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = errorMessage!!,
                            modifier = Modifier.padding(SleeperSpacing.md),
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                leagues.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        Text("No leagues found for the 2025 season")
                    }
                }

                else -> {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        item {
                            androidx.compose.material3.Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = SleeperSpacing.sm),
                                elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = SleeperSpacing.xs)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(SleeperSpacing.md)
                                ) {
                                    Text("League Name", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    Text("League ID", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                        items(leagues) { league ->
                            androidx.compose.material3.Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = SleeperSpacing.xs)
                                    .clickable { onLeagueClick(league) },
                                elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = SleeperSpacing.xxs)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(SleeperSpacing.md)
                                ) {
                                    Text(
                                        text = league.leagueName ?: "Unnamed League",
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = league.leagueId.toString(),
                                        modifier = Modifier.weight(1f),
                                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
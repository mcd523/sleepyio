package com.sleepyio.sleepyio

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.core.rememberScrollAreaState
import com.sleepyio.sleepyio.client.SleeperClient
import com.sleepyio.sleepyio.client.model.league.SleeperLeague
import com.sleepyio.sleepyio.client.model.user.SleeperUser
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun LeagueTable(user: SleeperUser, onLeagueClick: (String) -> Unit = {}) {
    val client = remember { SleeperClient }
    val scope = rememberCoroutineScope()
    var leagues: List<SleeperLeague> by remember { mutableStateOf(listOf()) }
    MyTheme {
        Surface(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .safeContentPadding(),
            ) {
//                val scrollState = rememberScrollState()
                scope.launch {
                    leagues = client.getLeaguesForUser(user.userId.toString(), "nfl", "2025")
                }
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    item {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text("League Name", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text("League ID", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        }
                    }
                    items(leagues) { league ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onLeagueClick(league.leagueId.toString()) }
                                .padding(vertical = 8.dp, horizontal = 4.dp)
                        ) {
                            Text(league.leagueName, modifier = Modifier.weight(1f))
                            Text(league.leagueId.toString(), modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}
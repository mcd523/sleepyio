package com.sleepyio.sleepyio

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.composeunstyled.theme.Theme
import com.sleepyio.sleepyio.cache.SleeperCache
import com.sleepyio.sleepyio.client.SleeperClient
import com.sleepyio.sleepyio.client.model.user.SleeperUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview

import sleepyio.composeapp.generated.resources.Res
import sleepyio.composeapp.generated.resources.sleeper_logo

@Composable
@Preview
fun App() {
    val scope = rememberCoroutineScope()
    val client = remember { SleeperClient }
    val cache by produceState(initialValue = SleeperCache, producer = {
        SleeperCache.init()
        value = SleeperCache
    })

    MyTheme {
        var username by remember { mutableStateOf("thehippokid") }
        var user: SleeperUser? by remember { mutableStateOf(null) }
        var selectedLeagueId: String? by remember { mutableStateOf(null) }
        Column(
            modifier = Modifier
                .background(Theme[colors][background])
                .safeContentPadding()
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val getUserOnClick: () -> Unit = {
                scope.launch {
                    withContext(Dispatchers.Default) {
                        user = client.getUser(username)
                    }
                }
            }

            TextField(
                modifier = Modifier
                    .background(Theme[colors][background]),
                value = username,
                onValueChange = { username = it },
                label = { com.composeunstyled.Text("Enter Sleeper Username") }
            )
            Button(
                modifier = Modifier
                    .background(Theme[colors][background]),
                onClick = getUserOnClick
            ) {
                com.composeunstyled.Text("Click me!")
            }
            AnimatedVisibility(user != null) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Image(painterResource(Res.drawable.sleeper_logo), null)
                        Text("User: ${user?.userName}")
                        user?.let {
                            if (selectedLeagueId == null) {
                                LeagueTable(it) { leagueId ->
                                    selectedLeagueId = leagueId
                                }
                            } else {
                                TeamsView(selectedLeagueId!!) {
                                    selectedLeagueId = null
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
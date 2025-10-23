package com.sleepyio.sleepyio

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.sleepyio.sleepyio.client.SleeperClient
import com.sleepyio.sleepyio.client.model.user.SleeperUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview

import sleepyio.composeapp.generated.resources.Res
import sleepyio.composeapp.generated.resources.compose_multiplatform

@Composable
@Preview
fun App() {
    val client = remember { SleeperClient }
    val scope = rememberCoroutineScope()
    MaterialTheme {
        var user: SleeperUser? by remember { mutableStateOf(null) }
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primaryContainer)
                .safeContentPadding()
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val getUserOnClick: () -> Unit = {
                scope.launch {
                    withContext(Dispatchers.Default) {
                        user = client.getUser()
                    }
                }
            }

            Button(onClick = getUserOnClick) {
                Text("Click me!")
            }
            AnimatedVisibility(user != null) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(painterResource(Res.drawable.compose_multiplatform), null)
                    Text("Compose: ${user?.userName}")
                }
            }
        }
    }
}
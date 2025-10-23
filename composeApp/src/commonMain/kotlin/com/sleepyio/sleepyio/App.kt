package com.sleepyio.sleepyio

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.gestures.snapping.snapFlingBehavior
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.composeunstyled.theme.ThemeProperty
import com.composeunstyled.theme.ThemeToken
import com.composeunstyled.theme.buildTheme
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
    val colors = ThemeProperty<Color>("colors")
    val background = ThemeToken<Color>("background")
    val onBackground = ThemeToken<Color>("on_background")

    val MyTheme = buildTheme {
        properties[colors] = mapOf(
            background to Color(0xFFFAFAFA),
            onBackground to Color(0XFF0C0A09),
        )
    }
    MyTheme {
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
                com.composeunstyled.Text("Click me!")
            }
            AnimatedVisibility(user != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .scrollable(
                            state = ScrollableState { delta -> delta },
                            orientation = Orientation.Vertical,
                            enabled = true,
                            flingBehavior = ScrollableDefaults.flingBehavior()
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(painterResource(Res.drawable.compose_multiplatform), null)
                    Text("User: ${user?.userName}")
                    user?.let { LeagueTable(it) }
                }
            }
        }
    }
}
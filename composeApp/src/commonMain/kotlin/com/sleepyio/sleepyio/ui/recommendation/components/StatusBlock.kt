package com.sleepyio.sleepyio.ui.recommendation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composeunstyled.theme.Theme
import com.sleepyio.sleepyio.colors
import com.sleepyio.sleepyio.negative
import com.sleepyio.sleepyio.onPrimary
import com.sleepyio.sleepyio.onSurface
import com.sleepyio.sleepyio.onSurfaceMuted
import com.sleepyio.sleepyio.outline
import com.sleepyio.sleepyio.primary
import com.sleepyio.sleepyio.surface

/** Centered spinner for tab loading states. */
@Composable
fun LoadingBlock(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().padding(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = Theme[colors][primary])
    }
}

/** Empty-state block with one line of explanatory copy. */
@Composable
fun EmptyBlock(
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Theme[colors][surface])
            .border(1.dp, Theme[colors][outline], RoundedCornerShape(12.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            color = Theme[colors][onSurfaceMuted],
        )
    }
}

/** Inline failure banner with a Retry button. */
@Composable
fun FailedBlock(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Theme[colors][surface])
            .border(1.dp, Theme[colors][negative], RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = message,
            color = Theme[colors][onSurface],
            fontWeight = FontWeight.SemiBold,
        )
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(
                containerColor = Theme[colors][primary],
                contentColor = Theme[colors][onPrimary],
            ),
        ) {
            Text("Retry")
        }
    }
}

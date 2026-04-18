package com.sleepyio.sleepyio.ui.recommendation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composeunstyled.theme.Theme
import com.sleepyio.sleepyio.colors
import com.sleepyio.sleepyio.onPrimary
import com.sleepyio.sleepyio.onSurface
import com.sleepyio.sleepyio.onSurfaceMuted
import com.sleepyio.sleepyio.outline
import com.sleepyio.sleepyio.positive
import com.sleepyio.sleepyio.primary
import com.sleepyio.sleepyio.recommendation.RecommendationService
import com.sleepyio.sleepyio.recommendation.model.StartSitRecommendation
import com.sleepyio.sleepyio.surface
import com.sleepyio.sleepyio.surfaceElevated
import com.sleepyio.sleepyio.ui.recommendation.components.ConfidenceBadge
import com.sleepyio.sleepyio.ui.recommendation.components.EmptyBlock
import com.sleepyio.sleepyio.ui.recommendation.components.FailedBlock
import com.sleepyio.sleepyio.ui.recommendation.components.FactorChipRow
import com.sleepyio.sleepyio.ui.recommendation.components.LoadingBlock

/**
 * Start/Sit tab: user pastes two player IDs, we render a head-to-head
 * verdict card with rationale chips and side-by-side projections.
 *
 * Note: [leagueId] and [rosterId] are accepted for API uniformity with the
 * other tabs but the service call only needs the two player IDs + week.
 */
@Composable
fun StartSitTab(
    service: RecommendationService,
    leagueId: Long,
    rosterId: Long,
    week: Int,
    modifier: Modifier = Modifier,
) {
    @Suppress("UNUSED_VARIABLE") val lg = leagueId
    @Suppress("UNUSED_VARIABLE") val rg = rosterId

    val vm = remember(service) { StartSitViewModel(service) }
    val state by vm.state.collectAsState()
    val sizes = rememberAdvisorSizes()

    var playerA by remember { mutableStateOf("") }
    var playerB by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(sizes.cardPadding),
        verticalArrangement = Arrangement.spacedBy(sizes.spacingLg),
    ) {
        // TODO: searchable picker — placeholder accepts raw Sleeper player IDs.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(sizes.spacingMd),
        ) {
            OutlinedTextField(
                value = playerA,
                onValueChange = { playerA = it },
                label = { Text("Player A id") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                colors = fieldColors(),
            )
            OutlinedTextField(
                value = playerB,
                onValueChange = { playerB = it },
                label = { Text("Player B id") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                colors = fieldColors(),
            )
        }

        Button(
            onClick = { vm.load(playerA.trim(), playerB.trim(), week) },
            enabled = playerA.isNotBlank() && playerB.isNotBlank(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Theme[colors][primary],
                contentColor = Theme[colors][onPrimary],
            ),
        ) {
            Text("Compare (week $week)")
        }

        when (val s = state) {
            is ScreenState.Empty -> EmptyBlock(
                message = "Enter two player IDs to get a verdict.",
            )
            is ScreenState.Loading -> LoadingBlock()
            is ScreenState.Failed -> FailedBlock(message = s.message, onRetry = s.retry)
            is ScreenState.Content -> StartSitVerdict(
                recommendation = s.value,
                sizes = sizes,
            )
        }
    }
}

@Composable
private fun StartSitVerdict(
    recommendation: StartSitRecommendation,
    sizes: AdvisorSizes,
) {
    val startName = rememberPlayerName(recommendation.startPlayerId)
    Column(verticalArrangement = Arrangement.spacedBy(sizes.spacingLg)) {
        // Big verdict card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(sizes.cardRadius))
                .background(Theme[colors][surfaceElevated])
                .border(1.dp, Theme[colors][positive], RoundedCornerShape(sizes.cardRadius))
                .padding(sizes.cardPadding),
            verticalArrangement = Arrangement.spacedBy(sizes.spacingSm),
        ) {
            Text(
                text = "START: $startName",
                color = Theme[colors][onSurface],
                fontSize = sizes.headlineLarge,
                fontWeight = FontWeight.Bold,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ConfidenceBadge(confidence = recommendation.confidence)
                Text(
                    text = "Score ${recommendation.score}",
                    color = Theme[colors][onSurfaceMuted],
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        // Why section
        SectionHeader(text = "Why", sizes = sizes)
        Text(
            text = recommendation.rationale.summary,
            color = Theme[colors][onSurface],
            fontSize = sizes.bodyLarge,
        )
        FactorChipRow(factors = recommendation.rationale.factors)

        // Side-by-side — projection data isn't carried on StartSitRecommendation,
        // so we surface the player ids in two labelled columns and leave the
        // numeric detail to the PlayerInsight tab (design spec placeholder).
        SectionHeader(text = "Side-by-side", sizes = sizes)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(sizes.cardRadius))
                .background(Theme[colors][surface])
                .border(1.dp, Theme[colors][outline], RoundedCornerShape(sizes.cardRadius))
                .padding(sizes.cardPadding),
            horizontalArrangement = Arrangement.spacedBy(sizes.spacingMd),
        ) {
            SideCell(
                label = "Player A",
                name = rememberPlayerName(recommendation.playerAId),
                isPick = recommendation.startPlayerId == recommendation.playerAId,
                sizes = sizes,
                modifier = Modifier.weight(1f),
            )
            SideCell(
                label = "Player B",
                name = rememberPlayerName(recommendation.playerBId),
                isPick = recommendation.startPlayerId == recommendation.playerBId,
                sizes = sizes,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SideCell(
    label: String,
    name: String,
    isPick: Boolean,
    sizes: AdvisorSizes,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(sizes.chipRadius))
            .background(
                if (isPick) Theme[colors][surfaceElevated] else Theme[colors][surface],
            )
            .border(
                1.dp,
                if (isPick) Theme[colors][positive] else Theme[colors][outline],
                RoundedCornerShape(sizes.chipRadius),
            )
            .padding(sizes.cardPadding),
        verticalArrangement = Arrangement.spacedBy(sizes.spacingXs),
    ) {
        Text(
            text = label,
            color = Theme[colors][onSurfaceMuted],
            fontSize = sizes.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = name,
            color = Theme[colors][onSurface],
            fontSize = sizes.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (isPick) {
            Text(
                text = "\u25B2 Recommended start",
                color = Theme[colors][positive],
                fontSize = sizes.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String, sizes: AdvisorSizes) {
    Text(
        text = text,
        color = Theme[colors][onSurface],
        fontSize = sizes.headlineSmall,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun fieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Theme[colors][surface],
    unfocusedContainerColor = Theme[colors][surface],
    focusedTextColor = Theme[colors][onSurface],
    unfocusedTextColor = Theme[colors][onSurface],
    focusedLabelColor = Theme[colors][onSurfaceMuted],
    unfocusedLabelColor = Theme[colors][onSurfaceMuted],
    focusedIndicatorColor = Theme[colors][primary],
    unfocusedIndicatorColor = Theme[colors][outline],
    cursorColor = Theme[colors][primary],
)

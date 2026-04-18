package com.sleepyio.sleepyio.ui.recommendation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composeunstyled.theme.Theme
import com.sleepyio.sleepyio.cache.SleeperCache
import com.sleepyio.sleepyio.colors
import com.sleepyio.sleepyio.negative
import com.sleepyio.sleepyio.onPrimary
import com.sleepyio.sleepyio.onSurface
import com.sleepyio.sleepyio.onSurfaceMuted
import com.sleepyio.sleepyio.outline
import com.sleepyio.sleepyio.primary
import com.sleepyio.sleepyio.recommendation.RecommendationService
import com.sleepyio.sleepyio.recommendation.model.LineupRecommendation
import com.sleepyio.sleepyio.recommendation.model.LineupSlot
import com.sleepyio.sleepyio.recommendation.model.StartSitRecommendation
import com.sleepyio.sleepyio.recommendation.model.WaiverTarget
import com.sleepyio.sleepyio.recommendation.model.WeeklyReport
import com.sleepyio.sleepyio.surface
import com.sleepyio.sleepyio.surfaceElevated
import com.sleepyio.sleepyio.ui.recommendation.components.ConfidenceBadge
import com.sleepyio.sleepyio.ui.recommendation.components.EmptyBlock
import com.sleepyio.sleepyio.ui.recommendation.components.FailedBlock
import com.sleepyio.sleepyio.ui.recommendation.components.FactorChipRow
import com.sleepyio.sleepyio.ui.recommendation.components.LoadingBlock
import com.sleepyio.sleepyio.warning

/**
 * Weekly Report tab — the default advisor surface.
 *
 * Layout follows DESIGN_SYSTEM.md: headline callout, projection card,
 * optimal lineup, start/sit decisions, risky starters, waiver targets.
 */
@Composable
fun WeeklyReportTab(
    service: RecommendationService,
    leagueId: Long,
    rosterId: Long,
    week: Int,
    modifier: Modifier = Modifier,
) {
    val vm = remember(service) { WeeklyReportViewModel(service) }
    val state by vm.state.collectAsState()
    val sizes = rememberAdvisorSizes()

    LaunchedEffect(leagueId, rosterId, week) {
        vm.load(leagueId, rosterId, week)
    }

    when (val s = state) {
        is ScreenState.Loading -> LoadingBlock(modifier)
        is ScreenState.Empty -> EmptyBlock(
            message = "Pick a week to see your report.",
            modifier = modifier.padding(sizes.cardPadding),
        )
        is ScreenState.Failed -> FailedBlock(
            message = s.message,
            onRetry = s.retry,
            modifier = modifier.padding(sizes.cardPadding),
        )
        is ScreenState.Content -> WeeklyReportContent(
            report = s.value,
            sizes = sizes,
            modifier = modifier,
        )
    }
}

@Composable
private fun WeeklyReportContent(
    report: WeeklyReport,
    sizes: AdvisorSizes,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(sizes.cardPadding),
        verticalArrangement = Arrangement.spacedBy(sizes.spacingLg),
    ) {
        item { HeadlineCallout(text = report.headlineAdvice, sizes = sizes) }
        item { ProjectionTotalCard(lineup = report.lineup, sizes = sizes) }
        item {
            SectionTitle(text = "Optimal lineup", sizes = sizes)
            LineupCard(lineup = report.lineup, sizes = sizes)
        }
        if (report.startSitDecisions.isNotEmpty()) {
            item {
                SectionTitle(
                    text = "Start/Sit decisions (${report.startSitDecisions.size})",
                    sizes = sizes,
                )
            }
            items(report.startSitDecisions) { decision ->
                StartSitCompact(decision = decision, sizes = sizes)
            }
        }
        if (report.riskyStarters.isNotEmpty()) {
            item {
                SectionTitle(text = "Risky starters", sizes = sizes)
                RiskyStartersRow(playerIds = report.riskyStarters, sizes = sizes)
            }
        }
        if (report.waiverTargets.isNotEmpty()) {
            item {
                SectionTitle(
                    text = "Waiver targets (${report.waiverTargets.size})",
                    sizes = sizes,
                )
            }
            items(report.waiverTargets) { target ->
                WaiverTargetCard(target = target, sizes = sizes)
            }
        }
    }
}

@Composable
private fun HeadlineCallout(text: String, sizes: AdvisorSizes) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(sizes.cardRadius))
            .background(Theme[colors][primary])
            .padding(sizes.cardPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = Theme[colors][onPrimary],
            fontWeight = FontWeight.Bold,
            fontSize = sizes.headlineSmall,
        )
    }
}

@Composable
private fun ProjectionTotalCard(
    lineup: LineupRecommendation,
    sizes: AdvisorSizes,
) {
    // LineupRecommendation carries a 0..100 score + rationale summary — no
    // aggregate median on the shared model, so we surface those two and defer
    // to player-level projections in the lineup card below.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(sizes.cardRadius))
            .background(Theme[colors][surfaceElevated])
            .border(1.dp, Theme[colors][outline], RoundedCornerShape(sizes.cardRadius))
            .padding(sizes.cardPadding),
        verticalArrangement = Arrangement.spacedBy(sizes.spacingSm),
    ) {
        Text(
            text = "Projected total",
            color = Theme[colors][onSurfaceMuted],
            fontSize = sizes.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "${lineup.score}",
            color = Theme[colors][onSurface],
            fontSize = sizes.displaySize,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = lineup.rationale.summary,
            color = Theme[colors][onSurfaceMuted],
            fontSize = sizes.bodyMedium,
        )
        ConfidenceBadge(confidence = lineup.confidence)
    }
}

@Composable
private fun LineupCard(
    lineup: LineupRecommendation,
    sizes: AdvisorSizes,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(sizes.cardRadius))
            .background(Theme[colors][surface])
            .border(1.dp, Theme[colors][outline], RoundedCornerShape(sizes.cardRadius))
            .padding(sizes.cardPadding),
        verticalArrangement = Arrangement.spacedBy(sizes.spacingSm),
    ) {
        lineup.starters.forEach { slot ->
            LineupSlotRow(slot = slot, sizes = sizes)
        }
    }
}

@Composable
private fun LineupSlotRow(slot: LineupSlot, sizes: AdvisorSizes) {
    val playerName = rememberPlayerName(slot.playerId)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(sizes.spacingSm),
    ) {
        Text(
            text = slot.slot,
            color = Theme[colors][primary],
            fontWeight = FontWeight.Bold,
            fontSize = sizes.labelLarge,
            modifier = Modifier.padding(end = sizes.spacingSm),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playerName,
                color = Theme[colors][onSurface],
                fontSize = sizes.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = slot.reason,
                color = Theme[colors][onSurfaceMuted],
                fontSize = sizes.bodyMedium,
            )
        }
    }
}

@Composable
private fun StartSitCompact(
    decision: StartSitRecommendation,
    sizes: AdvisorSizes,
) {
    val startName = rememberPlayerName(decision.startPlayerId)
    val benchId = if (decision.startPlayerId == decision.playerAId) decision.playerBId else decision.playerAId
    val benchName = rememberPlayerName(benchId)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(sizes.cardRadius))
            .background(Theme[colors][surface])
            .border(1.dp, Theme[colors][outline], RoundedCornerShape(sizes.cardRadius))
            .padding(sizes.cardPadding),
        verticalArrangement = Arrangement.spacedBy(sizes.spacingSm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Start: $startName  over  $benchName",
                color = Theme[colors][onSurface],
                fontSize = sizes.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            ConfidenceBadge(confidence = decision.confidence)
        }
        Text(
            text = decision.rationale.summary,
            color = Theme[colors][onSurfaceMuted],
            fontSize = sizes.bodyMedium,
        )
        FactorChipRow(factors = decision.rationale.factors)
    }
}

@Composable
private fun RiskyStartersRow(
    playerIds: List<String>,
    sizes: AdvisorSizes,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(sizes.spacingXs),
    ) {
        playerIds.forEach { id ->
            val name = rememberPlayerName(id)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(sizes.chipRadius))
                    .border(1.dp, Theme[colors][warning], RoundedCornerShape(sizes.chipRadius))
                    .padding(horizontal = sizes.spacingMd, vertical = sizes.spacingSm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(sizes.spacingSm),
            ) {
                Text(
                    text = "!",
                    color = Theme[colors][warning],
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = name,
                    color = Theme[colors][onSurface],
                    fontSize = sizes.bodyLarge,
                )
            }
        }
    }
}

@Composable
private fun WaiverTargetCard(
    target: WaiverTarget,
    sizes: AdvisorSizes,
) {
    val name = rememberPlayerName(target.playerId)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(sizes.cardRadius))
            .background(Theme[colors][surface])
            .border(1.dp, Theme[colors][outline], RoundedCornerShape(sizes.cardRadius))
            .padding(sizes.cardPadding),
        verticalArrangement = Arrangement.spacedBy(sizes.spacingSm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = name,
                color = Theme[colors][onSurface],
                fontSize = sizes.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "FAAB ${target.suggestedFaabPct}%",
                color = Theme[colors][primary],
                fontWeight = FontWeight.Bold,
                fontSize = sizes.labelLarge,
            )
        }
        Text(
            text = target.rationale.summary,
            color = Theme[colors][onSurfaceMuted],
            fontSize = sizes.bodyMedium,
        )
    }
}

@Composable
private fun SectionTitle(text: String, sizes: AdvisorSizes) {
    Text(
        text = text,
        color = Theme[colors][onSurface],
        fontSize = sizes.headlineSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

/**
 * Resolves a Sleeper player-id to a display name via [SleeperCache]. Runs as a
 * suspending lookup inside `produceState` so the cache call stays off the main
 * composition thread; falls back to the raw id until the value is loaded.
 */
@Composable
internal fun rememberPlayerName(playerId: String): String {
    val name by produceState(initialValue = playerId, playerId) {
        value = try {
            val player = SleeperCache.getPlayer(playerId)
            val first = player?.firstName.orEmpty()
            val last = player?.lastName.orEmpty()
            val full = "$first $last".trim()
            full.ifEmpty { playerId }
        } catch (e: Exception) {
            playerId
        }
    }
    return name
}

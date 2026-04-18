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
import com.sleepyio.sleepyio.info
import com.sleepyio.sleepyio.insight.model.FantasyImpact
import com.sleepyio.sleepyio.insight.model.GameEnvironment
import com.sleepyio.sleepyio.insight.model.InjuryStatus
import com.sleepyio.sleepyio.insight.model.NewsBlurb
import com.sleepyio.sleepyio.insight.model.PlayerInsight
import com.sleepyio.sleepyio.insight.model.UsageTrend
import com.sleepyio.sleepyio.negative
import com.sleepyio.sleepyio.onPrimary
import com.sleepyio.sleepyio.onSurface
import com.sleepyio.sleepyio.onSurfaceMuted
import com.sleepyio.sleepyio.outline
import com.sleepyio.sleepyio.positive
import com.sleepyio.sleepyio.primary
import com.sleepyio.sleepyio.recommendation.RecommendationService
import com.sleepyio.sleepyio.surface
import com.sleepyio.sleepyio.ui.recommendation.components.EmptyBlock
import com.sleepyio.sleepyio.ui.recommendation.components.FailedBlock
import com.sleepyio.sleepyio.ui.recommendation.components.InjuryTag
import com.sleepyio.sleepyio.ui.recommendation.components.LoadingBlock
import com.sleepyio.sleepyio.ui.recommendation.components.MatchupGradeBar
import com.sleepyio.sleepyio.ui.recommendation.components.ProjectionRangePill

/**
 * Player Insight tab: enter a player id, see a full [PlayerInsight] dump —
 * projection pill, matchup grade bar, usage row, injury, game environment,
 * and recent news.
 */
@Composable
fun PlayerInsightTab(
    service: RecommendationService,
    leagueId: Long,
    rosterId: Long,
    week: Int,
    modifier: Modifier = Modifier,
) {
    @Suppress("UNUSED_VARIABLE") val lg = leagueId
    @Suppress("UNUSED_VARIABLE") val rg = rosterId

    val vm = remember(service) { PlayerInsightViewModel(service) }
    val state by vm.state.collectAsState()
    val sizes = rememberAdvisorSizes()
    var playerId by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(sizes.cardPadding),
        verticalArrangement = Arrangement.spacedBy(sizes.spacingLg),
    ) {
        // TODO: searchable picker — placeholder accepts raw Sleeper player IDs.
        OutlinedTextField(
            value = playerId,
            onValueChange = { playerId = it },
            label = { Text("Player id") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = insightFieldColors(),
        )
        Button(
            onClick = { vm.load(playerId.trim(), week) },
            enabled = playerId.isNotBlank(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Theme[colors][primary],
                contentColor = Theme[colors][onPrimary],
            ),
        ) {
            Text("Load (week $week)")
        }

        when (val s = state) {
            is ScreenState.Empty -> EmptyBlock(message = "Enter a player id to view insights.")
            is ScreenState.Loading -> LoadingBlock()
            is ScreenState.Failed -> FailedBlock(message = s.message, onRetry = s.retry)
            is ScreenState.Content -> PlayerInsightContent(insight = s.value, sizes = sizes)
        }
    }
}

@Composable
private fun PlayerInsightContent(
    insight: PlayerInsight,
    sizes: AdvisorSizes,
) {
    Column(verticalArrangement = Arrangement.spacedBy(sizes.spacingLg)) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(sizes.spacingSm),
        ) {
            Text(
                text = insight.fullName,
                color = Theme[colors][onSurface],
                fontSize = sizes.headlineLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            InjuryTag(status = insight.injury)
        }
        Text(
            text = buildString {
                append(insight.position)
                insight.team?.let { append(" · $it") }
                insight.opponent?.let { append(" vs $it") }
                append(" · Week ${insight.week}")
            },
            color = Theme[colors][onSurfaceMuted],
            fontSize = sizes.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )

        ProjectionRangePill(range = insight.projection)

        // Matchup
        SectionHeader(text = "Matchup grade · ${insight.matchup.grade.name}", sizes = sizes)
        MatchupGradeBar(grade = insight.matchup.grade)
        insight.matchup.opponentRankVsPos?.let { rank ->
            Text(
                text = "Opponent rank vs position: #$rank",
                color = Theme[colors][onSurfaceMuted],
                fontSize = sizes.bodyMedium,
            )
        }
        insight.matchup.notes?.let { notes ->
            Text(text = notes, color = Theme[colors][onSurface], fontSize = sizes.bodyMedium)
        }

        // Usage
        SectionHeader(text = "Usage (last 3 weeks)", sizes = sizes)
        UsageRow(usage = insight.usage, sizes = sizes)

        // Injury
        SectionHeader(text = "Injury", sizes = sizes)
        InjuryBlock(status = insight.injury, sizes = sizes)

        // Game environment
        SectionHeader(text = "Game environment", sizes = sizes)
        GameEnvironmentBlock(env = insight.gameEnvironment, sizes = sizes)

        // News
        SectionHeader(text = "Recent news", sizes = sizes)
        if (insight.recentNews.isEmpty()) {
            Text(
                text = "No notable news.",
                color = Theme[colors][onSurfaceMuted],
                fontSize = sizes.bodyMedium,
            )
        } else {
            insight.recentNews.forEach { blurb ->
                NewsRow(blurb = blurb, sizes = sizes)
            }
        }
    }
}

@Composable
private fun UsageRow(usage: UsageTrend, sizes: AdvisorSizes) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(sizes.cardRadius))
            .background(Theme[colors][surface])
            .border(1.dp, Theme[colors][outline], RoundedCornerShape(sizes.cardRadius))
            .padding(sizes.cardPadding),
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        UsageCell("Snap %", formatPct(usage.snapPctLast3), sizes)
        UsageCell("Target %", formatPct(usage.targetShareLast3), sizes)
        UsageCell("Carry %", formatPct(usage.carryShareLast3), sizes)
        UsageCell("RZ opp", usage.redZoneOppLast3?.toString() ?: "—", sizes)
    }
}

@Composable
private fun UsageCell(label: String, value: String, sizes: AdvisorSizes) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = Theme[colors][onSurface],
            fontSize = sizes.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            color = Theme[colors][onSurfaceMuted],
            fontSize = sizes.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun InjuryBlock(status: InjuryStatus, sizes: AdvisorSizes) {
    val designation = status.designation?.takeIf { it.isNotBlank() } ?: "Healthy"
    val body = buildString {
        append(designation)
        status.bodyPart?.let { append(" · $it") }
        status.practiceStatus?.let { append(" · $it") }
    }
    Text(
        text = body,
        color = Theme[colors][onSurface],
        fontSize = sizes.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(sizes.cardRadius))
            .background(Theme[colors][surface])
            .border(1.dp, Theme[colors][outline], RoundedCornerShape(sizes.cardRadius))
            .padding(sizes.cardPadding),
    )
}

@Composable
private fun GameEnvironmentBlock(env: GameEnvironment, sizes: AdvisorSizes) {
    val parts = buildList {
        env.impliedTeamTotal?.let { add("Total ${formatOneDecimalDouble(it)}") }
        env.spread?.let { add("Spread ${formatSignedOneDecimal(it)}") }
        if (env.dome) add("Dome") else {
            env.temperatureF?.let { add("${it}°F") }
            env.windMph?.let { add("${it} mph wind") }
            env.precipitationPct?.let { add("${it}% precip") }
        }
    }
    Text(
        text = if (parts.isEmpty()) "No game environment data." else parts.joinToString("   ·   "),
        color = Theme[colors][onSurface],
        fontSize = sizes.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(sizes.cardRadius))
            .background(Theme[colors][surface])
            .border(1.dp, Theme[colors][outline], RoundedCornerShape(sizes.cardRadius))
            .padding(sizes.cardPadding),
    )
}

@Composable
private fun NewsRow(blurb: NewsBlurb, sizes: AdvisorSizes) {
    val (glyph, tint) = when (blurb.fantasyImpact) {
        FantasyImpact.POSITIVE -> "\u25B2" to Theme[colors][positive]
        FantasyImpact.NEGATIVE -> "\u25BC" to Theme[colors][negative]
        FantasyImpact.NEUTRAL -> "\u2022" to Theme[colors][info]
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(sizes.chipRadius))
            .background(Theme[colors][surface])
            .border(1.dp, Theme[colors][outline], RoundedCornerShape(sizes.chipRadius))
            .padding(sizes.cardPadding),
        horizontalArrangement = Arrangement.spacedBy(sizes.spacingSm),
    ) {
        Text(text = glyph, color = tint, fontWeight = FontWeight.Bold)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = blurb.headline,
                color = Theme[colors][onSurface],
                fontSize = sizes.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = blurb.body,
                color = Theme[colors][onSurface],
                fontSize = sizes.bodyMedium,
            )
            Text(
                text = blurb.source,
                color = Theme[colors][onSurfaceMuted],
                fontSize = sizes.labelSmall,
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
private fun insightFieldColors() = TextFieldDefaults.colors(
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

private fun formatPct(value: Double?): String =
    if (value == null) "—" else "${(value * 100).toInt()}%"

private fun formatOneDecimalDouble(value: Double): String {
    val rounded = kotlin.math.round(value * 10.0) / 10.0
    val whole = rounded.toLong()
    val frac = kotlin.math.round((rounded - whole) * 10.0).toInt()
    val fracAbs = if (frac < 0) -frac else frac
    return "$whole.$fracAbs"
}

private fun formatSignedOneDecimal(value: Double): String {
    val sign = if (value >= 0) "+" else "-"
    val abs = kotlin.math.abs(value)
    return "$sign${formatOneDecimalDouble(abs)}"
}

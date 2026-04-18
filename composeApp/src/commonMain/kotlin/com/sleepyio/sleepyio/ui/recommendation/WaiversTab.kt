package com.sleepyio.sleepyio.ui.recommendation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composeunstyled.theme.Theme
import com.sleepyio.sleepyio.cache.SleeperCache
import com.sleepyio.sleepyio.colors
import com.sleepyio.sleepyio.onPrimary
import com.sleepyio.sleepyio.onSurface
import com.sleepyio.sleepyio.onSurfaceMuted
import com.sleepyio.sleepyio.outline
import com.sleepyio.sleepyio.primary
import com.sleepyio.sleepyio.recommendation.RecommendationService
import com.sleepyio.sleepyio.recommendation.model.WaiverTarget
import com.sleepyio.sleepyio.surface
import com.sleepyio.sleepyio.ui.recommendation.components.ConfidenceBadge
import com.sleepyio.sleepyio.ui.recommendation.components.EmptyBlock
import com.sleepyio.sleepyio.ui.recommendation.components.FactorChipRow
import com.sleepyio.sleepyio.ui.recommendation.components.FailedBlock
import com.sleepyio.sleepyio.ui.recommendation.components.LoadingBlock

private val POSITION_FILTERS = listOf("All", "QB", "RB", "WR", "TE", "DST", "K")

/**
 * Waiver-wire tab: position filter chip row + list of [WaiverTarget] cards.
 * Tapping a card expands it to show drop candidates resolved to player names.
 */
@Composable
fun WaiversTab(
    service: RecommendationService,
    leagueId: Long,
    rosterId: Long,
    week: Int,
    modifier: Modifier = Modifier,
) {
    val vm = remember(service) { WaiversViewModel(service) }
    val state by vm.state.collectAsState()
    val sizes = rememberAdvisorSizes()
    var selectedFilter by remember { mutableStateOf("All") }

    LaunchedEffect(leagueId, rosterId, week) {
        vm.load(leagueId, rosterId, week)
    }

    Column(
        modifier = modifier.fillMaxWidth().padding(sizes.cardPadding),
        verticalArrangement = Arrangement.spacedBy(sizes.spacingMd),
    ) {
        FilterChipRow(
            selected = selectedFilter,
            onSelected = { selectedFilter = it },
            sizes = sizes,
        )

        when (val s = state) {
            is ScreenState.Loading -> LoadingBlock()
            is ScreenState.Empty -> EmptyBlock(message = "No waiver targets this week.")
            is ScreenState.Failed -> FailedBlock(message = s.message, onRetry = s.retry)
            is ScreenState.Content -> WaiverList(
                targets = s.value,
                filter = selectedFilter,
                sizes = sizes,
            )
        }
    }
}

@Composable
private fun FilterChipRow(
    selected: String,
    onSelected: (String) -> Unit,
    sizes: AdvisorSizes,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(sizes.spacingSm)) {
        items(POSITION_FILTERS) { filter ->
            val isActive = filter == selected
            val border: Color = if (isActive) Theme[colors][primary] else Theme[colors][outline]
            val text: Color = if (isActive) Theme[colors][onPrimary] else Theme[colors][onSurface]
            val bg: Color = if (isActive) Theme[colors][primary] else Theme[colors][surface]
            Text(
                text = filter,
                color = text,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(sizes.chipRadius))
                    .background(bg)
                    .border(1.dp, border, RoundedCornerShape(sizes.chipRadius))
                    .clickable { onSelected(filter) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun WaiverList(
    targets: List<WaiverTarget>,
    filter: String,
    sizes: AdvisorSizes,
) {
    // Position data is not on WaiverTarget; we resolve via SleeperCache per-card.
    // For "All" we skip the filter entirely to avoid a lookup round-trip on first render.
    LazyColumn(verticalArrangement = Arrangement.spacedBy(sizes.spacingMd)) {
        items(targets) { target ->
            WaiverCard(target = target, filter = filter, sizes = sizes)
        }
    }
}

@Composable
private fun WaiverCard(
    target: WaiverTarget,
    filter: String,
    sizes: AdvisorSizes,
) {
    var expanded by remember { mutableStateOf(false) }
    val playerPosition by produceState(initialValue = null as String?, target.playerId) {
        value = try {
            SleeperCache.getPlayer(target.playerId)?.position
        } catch (e: Exception) {
            null
        }
    }
    // Hide cards that don't match the position filter, once the position is resolved.
    if (filter != "All" && playerPosition != null && playerPosition != filter) return

    val name = rememberPlayerName(target.playerId)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(sizes.cardRadius))
            .background(Theme[colors][surface])
            .border(1.dp, Theme[colors][outline], RoundedCornerShape(sizes.cardRadius))
            .clickable { expanded = !expanded }
            .padding(sizes.cardPadding),
        verticalArrangement = Arrangement.spacedBy(sizes.spacingSm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    color = Theme[colors][onSurface],
                    fontSize = sizes.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = playerPosition ?: "—",
                    color = Theme[colors][onSurfaceMuted],
                    fontSize = sizes.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Priority ${target.priorityScore}",
                    color = Theme[colors][primary],
                    fontSize = sizes.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "FAAB ${target.suggestedFaabPct}%",
                    color = Theme[colors][onSurface],
                    fontSize = sizes.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Text(
            text = target.rationale.summary,
            color = Theme[colors][onSurface],
            fontSize = sizes.bodyLarge,
        )
        FactorChipRow(factors = target.rationale.positive)
        ConfidenceBadge(confidence = target.confidence)

        if (expanded && target.dropCandidates.isNotEmpty()) {
            Text(
                text = "Drop candidates",
                color = Theme[colors][onSurfaceMuted],
                fontSize = sizes.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = sizes.spacingSm),
            )
            target.dropCandidates.forEachIndexed { index, id ->
                val dropName = rememberPlayerName(id)
                Text(
                    text = if (index == 0) "$dropName  (preferred drop)" else dropName,
                    color = Theme[colors][onSurface],
                    fontSize = sizes.bodyMedium,
                )
            }
        }
    }
}

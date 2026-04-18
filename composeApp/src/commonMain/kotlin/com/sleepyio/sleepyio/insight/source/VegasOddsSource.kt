package com.sleepyio.sleepyio.insight.source

import kotlinx.serialization.Serializable

/**
 * Data source for Vegas betting lines that feed the "game script" factor.
 *
 * The map returned is keyed by team tricode (home AND away — both teams show
 * up as separate entries) so per-player lookup is a single hash probe. Fail
 * soft with `emptyMap()` on error.
 */
interface VegasOddsSource {
    suspend fun fetchOdds(week: Int): Map<String, TeamOdds>
}

/**
 * Per-team betting context. [impliedTeamTotal] is the expected points the team
 * will score (total / 2 adjusted by spread); [spread] is the team's spread
 * with the standard sign convention (negative = favorite).
 */
@Serializable
data class TeamOdds(
    val impliedTeamTotal: Double,
    val spread: Double,
)

package com.sleepyio.sleepyio.client

// NOTE: Phase 1 refactor — introduces a SleeperRepository interface so
// analyzers can be unit-tested without the real Sleeper network client.
// See docs/architecture/REFACTOR_PLAN.md §4 for the "why." Scope is
// intentionally narrow: only the three methods the domain layer actually
// calls today. Add more as new analyzers need them — do not let this
// interface grow preemptively.

import com.sleepyio.sleepyio.client.model.league.SleeperRoster
import com.sleepyio.sleepyio.client.model.player.SleeperPlayer
import com.sleepyio.sleepyio.client.model.player.TrendingPlayer

/**
 * Contract for reading Sleeper's public API. [SleeperClient] is the production
 * implementation; tests substitute a fake.
 *
 * All methods are fail-soft at the source — a network or parse error
 * yields an empty collection (or `null` for single-entity gets), never an
 * exception. That contract must be honored by every implementation.
 */
interface SleeperRepository {
    suspend fun getRostersInLeague(leagueId: Long): List<SleeperRoster>
    suspend fun getAllPlayers(sport: String = "nfl"): Map<String, SleeperPlayer>
    suspend fun getTrendingPlayers(
        sport: String,
        type: String,
        lookbackHours: Int = 24,
        limit: Int = 25,
    ): List<TrendingPlayer>
}

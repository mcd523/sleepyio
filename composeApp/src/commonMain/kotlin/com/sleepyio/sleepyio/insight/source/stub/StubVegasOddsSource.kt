package com.sleepyio.sleepyio.insight.source.stub

import com.sleepyio.sleepyio.insight.source.TeamOdds
import com.sleepyio.sleepyio.insight.source.VegasOddsSource

/**
 * Stub [VegasOddsSource] with a fixed plausible map of team totals / spreads.
 *
 * TODO: wire real odds source when API key available (The Odds API, Pinnacle,
 * DraftKings scrape, etc.). Keeping this deterministic means the "game
 * environment" factor reliably contributes a mild signal during development
 * without masquerading as real market data.
 */
class StubVegasOddsSource : VegasOddsSource {
    override suspend fun fetchOdds(week: Int): Map<String, TeamOdds> = TEAMS.associateWith { team ->
        // Deterministic plausible distribution: high-tempo teams near 26,
        // average near 22, low-scoring near 18. Spread alternates around 0.
        val base = 22.0 + ((team.hashCode() and 0x7) - 4).toDouble()
        val spread = ((team.hashCode() shr 3) and 0x7).toDouble() - 3.5
        TeamOdds(impliedTeamTotal = base, spread = spread)
    }

    companion object {
        private val TEAMS = listOf(
            "ARI", "ATL", "BAL", "BUF", "CAR", "CHI", "CIN", "CLE",
            "DAL", "DEN", "DET", "GB", "HOU", "IND", "JAX", "KC",
            "LAC", "LAR", "LV", "MIA", "MIN", "NE", "NO", "NYG",
            "NYJ", "PHI", "PIT", "SEA", "SF", "TB", "TEN", "WAS",
        )
    }
}

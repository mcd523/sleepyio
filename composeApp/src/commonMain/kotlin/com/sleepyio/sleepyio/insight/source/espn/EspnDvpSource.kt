package com.sleepyio.sleepyio.insight.source.espn

import com.sleepyio.sleepyio.insight.model.DefenseVsPosition
import com.sleepyio.sleepyio.insight.source.DefenseVsPositionSource
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * STUB implementation of [DefenseVsPositionSource].
 *
 * **TODO: derive DvP rankings from real defensive stats (ESPN's team stats
 * endpoint, FantasyPros, or DVOA).** Until wired up this returns a
 * deterministic fixed ranking — every team ranked mid-pack vs every position
 * so the matchup factor consistently maps to NEUTRAL. This keeps analyzers
 * compiling and lineup math functional without poisoning recommendations with
 * fake "ELITE" matchups.
 */
class EspnDvpSource : DefenseVsPositionSource {
    private val logger = KotlinLogging.logger {}

    override suspend fun fetchDvp(week: Int): List<DefenseVsPosition> {
        return try {
            TEAMS.flatMap { team ->
                POSITIONS.map { pos ->
                    DefenseVsPosition(
                        team = team,
                        position = pos,
                        rankVsPos = 16,
                        fantasyPointsAllowedPerGame = 18.0,
                    )
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "EspnDvpSource stub failed for week=$week; degrading to empty list" }
            emptyList()
        }
    }

    companion object {
        private val TEAMS = listOf(
            "ARI", "ATL", "BAL", "BUF", "CAR", "CHI", "CIN", "CLE",
            "DAL", "DEN", "DET", "GB", "HOU", "IND", "JAX", "KC",
            "LAC", "LAR", "LV", "MIA", "MIN", "NE", "NO", "NYG",
            "NYJ", "PHI", "PIT", "SEA", "SF", "TB", "TEN", "WAS",
        )
        private val POSITIONS = listOf("QB", "RB", "WR", "TE", "K", "DEF")
    }
}

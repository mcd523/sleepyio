package com.sleepyio.sleepyio.insight.source.espn

import com.sleepyio.sleepyio.insight.model.ProjectionRange
import com.sleepyio.sleepyio.insight.source.ProjectionsSource
import com.sleepyio.sleepyio.insight.source.UsageSource
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.math.max

/**
 * STUB implementation of [ProjectionsSource].
 *
 * **TODO: wire a real projections feed when available.** ESPN's
 * `fantasy.espn.com/apis/v3/games/ffl/seasons/*/segments/0/leagues/*` endpoint
 * is fantasy-league-scoped and requires cookies, and there is no reliable free
 * public projections endpoint. Until we can wire a paid feed, this derives a
 * deterministic projection from recent usage so analyzers remain testable and
 * the feature is still functional end-to-end.
 *
 * Heuristic (half-PPR, rough):
 *     median = 12 * snap% + 20 * targetShare + 16 * carryShare + 3 * rzOpp/3
 *     floor  = max(0, median - 4)
 *     ceiling = median + 6
 *
 * Missing usage inputs contribute 0 to the sum.
 */
class EspnProjectionsSource(
    private val usageSource: UsageSource,
) : ProjectionsSource {
    private val logger = KotlinLogging.logger {}

    override suspend fun fetchProjections(week: Int): Map<String, ProjectionRange> {
        return try {
            val usage = usageSource.fetchUsage(week)
            usage.mapValues { (_, trend) ->
                val snap = trend.snapPctLast3 ?: 0.0
                val targets = trend.targetShareLast3 ?: 0.0
                val carries = trend.carryShareLast3 ?: 0.0
                val rz = (trend.redZoneOppLast3 ?: 0).toDouble()
                val median = 12.0 * snap + 20.0 * targets + 16.0 * carries + rz
                ProjectionRange(
                    floor = max(0.0, median - 4.0),
                    median = median,
                    ceiling = median + 6.0,
                )
            }
        } catch (e: Exception) {
            logger.error(e) { "EspnProjectionsSource stub failed for week=$week; degrading to empty map" }
            emptyMap()
        }
    }
}

package com.sleepyio.sleepyio.insight

import com.sleepyio.sleepyio.client.SleeperClient
import com.sleepyio.sleepyio.client.model.player.SleeperPlayer
import com.sleepyio.sleepyio.insight.model.DefenseVsPosition
import com.sleepyio.sleepyio.insight.model.FantasyImpact
import com.sleepyio.sleepyio.insight.model.GameEnvironment
import com.sleepyio.sleepyio.insight.model.InjuryStatus
import com.sleepyio.sleepyio.insight.model.MatchupGrade
import com.sleepyio.sleepyio.insight.model.MatchupRating
import com.sleepyio.sleepyio.insight.model.NewsBlurb
import com.sleepyio.sleepyio.insight.model.PlayerInsight
import com.sleepyio.sleepyio.insight.model.ProjectionRange
import com.sleepyio.sleepyio.insight.model.UsageTrend
import com.sleepyio.sleepyio.insight.source.DefenseVsPositionSource
import com.sleepyio.sleepyio.insight.source.InjuryReport
import com.sleepyio.sleepyio.insight.source.InjurySource
import com.sleepyio.sleepyio.insight.source.NewsSource
import com.sleepyio.sleepyio.insight.source.ProjectionsSource
import com.sleepyio.sleepyio.insight.source.TeamOdds
import com.sleepyio.sleepyio.insight.source.UsageSource
import com.sleepyio.sleepyio.insight.source.VegasOddsSource
import com.sleepyio.sleepyio.insight.source.WeatherForecast
import com.sleepyio.sleepyio.insight.source.WeatherSource
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Fuses every [com.sleepyio.sleepyio.insight.source] dependency into a single
 * [PlayerInsight] the recommendation service can reason over.
 *
 * The aggregator is the only class that knows about the *set* of data sources
 * — analyzers see one fused insight per player and don't care whether a given
 * factor came from ESPN, Open-Meteo, or a stub. Every call is wrapped in
 * try/catch so a single misbehaving source can only cost us one factor; the
 * aggregator never throws through to the UI.
 */
open class InsightAggregator(
    private val sleeper: SleeperClient = SleeperClient,
    private val injury: InjurySource,
    private val news: NewsSource,
    private val weather: WeatherSource,
    private val odds: VegasOddsSource,
    private val projections: ProjectionsSource,
    private val usage: UsageSource,
    private val dvp: DefenseVsPositionSource,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Fuse a single player's insight. Convenience wrapper that delegates to
     * the batch variant so the "fetch week-scoped inputs once" discipline is
     * preserved even for single-player calls.
     */
    open suspend fun insightFor(playerId: String, week: Int): PlayerInsight {
        return insightFor(listOf(playerId), week)[playerId]
            ?: fallbackInsight(playerId, week, player = null)
    }

    /**
     * Fuse insights for a collection of player ids. Fetches week-scoped inputs
     * (injuries, projections, usage, DvP, odds) once, then fans out per-player
     * for weather/news using [coroutineScope] + async. Fail-soft across the
     * board — any source error is swallowed (with a log line) and the affected
     * factor simply arrives as null/empty.
     */
    open suspend fun insightFor(playerIds: Collection<String>, week: Int): Map<String, PlayerInsight> {
        if (playerIds.isEmpty()) return emptyMap()

        val allPlayers: Map<String, SleeperPlayer> = runCatchingSource("sleeper.getAllPlayers") {
            sleeper.getAllPlayers("nfl")
        } ?: emptyMap()

        val injuries: Map<String, InjuryReport> = runCatchingSource("injury.fetchInjuries") {
            injury.fetchInjuries(week).associateBy { it.playerId }
        } ?: emptyMap()

        val projectionsByPlayer: Map<String, ProjectionRange> = runCatchingSource("projections.fetchProjections") {
            projections.fetchProjections(week)
        } ?: emptyMap()

        val usageByPlayer: Map<String, UsageTrend> = runCatchingSource("usage.fetchUsage") {
            usage.fetchUsage(week)
        } ?: emptyMap()

        val dvpByTeamPos: Map<Pair<String, String>, DefenseVsPosition> = runCatchingSource("dvp.fetchDvp") {
            dvp.fetchDvp(week).associateBy { it.team.uppercase() to it.position.uppercase() }
        } ?: emptyMap()

        val oddsByTeam: Map<String, TeamOdds> = runCatchingSource("odds.fetchOdds") {
            odds.fetchOdds(week).mapKeys { it.key.uppercase() }
        } ?: emptyMap()

        return coroutineScope {
            playerIds.map { playerId ->
                async {
                    val player = allPlayers[playerId]
                    playerId to buildInsight(
                        playerId = playerId,
                        player = player,
                        week = week,
                        injuries = injuries,
                        projectionsByPlayer = projectionsByPlayer,
                        usageByPlayer = usageByPlayer,
                        dvpByTeamPos = dvpByTeamPos,
                        oddsByTeam = oddsByTeam,
                    )
                }
            }.awaitAll().toMap()
        }
    }

    private suspend fun buildInsight(
        playerId: String,
        player: SleeperPlayer?,
        week: Int,
        injuries: Map<String, InjuryReport>,
        projectionsByPlayer: Map<String, ProjectionRange>,
        usageByPlayer: Map<String, UsageTrend>,
        dvpByTeamPos: Map<Pair<String, String>, DefenseVsPosition>,
        oddsByTeam: Map<String, TeamOdds>,
    ): PlayerInsight {
        val position = (player?.position ?: "FLEX").uppercase()
        val team = player?.team?.uppercase()

        // Weather + news are per-player network calls, parallelised above us.
        val forecast: WeatherForecast? = team?.let {
            runCatchingSource("weather.fetchForecast") {
                weather.fetchForecast(it, kickoffEpochMs = 0L)
            }
        }

        val newsItems = runCatchingSource("news.fetchNewsForPlayer") {
            val espnId = player?.espnId ?: playerId
            news.fetchNewsForPlayer(espnId, sinceEpochMs = 0L)
        } ?: emptyList()

        val teamOdds = team?.let { oddsByTeam[it] }
        val matchup = team?.let { buildMatchup(it, position, dvpByTeamPos) }
            ?: MatchupRating(MatchupGrade.NEUTRAL, null, null)

        val injuryReport = injuries[playerId]
        val injuryStatus = InjuryStatus(
            designation = injuryReport?.designation ?: player?.injuryStatus,
            bodyPart = injuryReport?.bodyPart,
            practiceStatus = injuryReport?.practiceStatus ?: player?.practiceParticipation,
            lastUpdatedEpochMs = injuryReport?.lastUpdatedEpochMs,
        )

        val usageTrend = usageByPlayer[playerId]
            ?: UsageTrend(null, null, null, null)

        val projection = projectionsByPlayer[playerId]
            ?: ProjectionRange(floor = 0.0, median = 0.0, ceiling = 0.0)

        val gameEnv = GameEnvironment(
            impliedTeamTotal = teamOdds?.impliedTeamTotal,
            spread = teamOdds?.spread,
            windMph = forecast?.windMph,
            precipitationPct = forecast?.precipitationPct,
            temperatureF = forecast?.temperatureF,
            dome = forecast?.dome ?: false,
        )

        val fullName = listOfNotNull(player?.firstName, player?.lastName)
            .joinToString(" ")
            .ifBlank { playerId }

        return PlayerInsight(
            playerId = playerId,
            fullName = fullName,
            position = position,
            team = team,
            opponent = null,
            week = week,
            projection = projection,
            injury = injuryStatus,
            usage = usageTrend,
            matchup = matchup,
            gameEnvironment = gameEnv,
            recentNews = newsItems.map {
                NewsBlurb(
                    headline = it.headline,
                    body = it.body,
                    source = it.source,
                    publishedEpochMs = it.publishedEpochMs,
                    fantasyImpact = it.fantasyImpactHint ?: FantasyImpact.NEUTRAL,
                )
            },
        )
    }

    private fun buildMatchup(
        team: String,
        position: String,
        dvpByTeamPos: Map<Pair<String, String>, DefenseVsPosition>,
    ): MatchupRating {
        val dvp = dvpByTeamPos[team to position]
        val rank = dvp?.rankVsPos
        val grade = matchupGradeFromRank(rank)
        val notes = dvp?.let {
            "Opponent ranks ${it.rankVsPos} vs $position (${it.fantasyPointsAllowedPerGame} FPA/G)"
        }
        return MatchupRating(grade = grade, opponentRankVsPos = rank, notes = notes)
    }

    /**
     * Fallback insight when we can't fuse anything meaningful — every factor
     * empty, everything null-safe. The analyzers can still hand this to the
     * UI; the rationale will just note the data gap.
     */
    private fun fallbackInsight(playerId: String, week: Int, player: SleeperPlayer?): PlayerInsight =
        PlayerInsight(
            playerId = playerId,
            fullName = playerId,
            position = (player?.position ?: "FLEX").uppercase(),
            team = player?.team?.uppercase(),
            opponent = null,
            week = week,
            projection = ProjectionRange(0.0, 0.0, 0.0),
            injury = InjuryStatus(null, null, null, null),
            usage = UsageTrend(null, null, null, null),
            matchup = MatchupRating(MatchupGrade.NEUTRAL, null, null),
            gameEnvironment = GameEnvironment(null, null, null, null, null, false),
            recentNews = emptyList(),
        )

    private inline fun <T> runCatchingSource(label: String, block: () -> T): T? = try {
        block()
    } catch (e: Exception) {
        logger.error(e) { "InsightAggregator source call failed: $label" }
        null
    }

    companion object {
        /**
         * Map a DvP rank (1 = toughest, 32 = easiest) to a [MatchupGrade] per
         * the framework doc. Null rank → NEUTRAL.
         */
        fun matchupGradeFromRank(rank: Int?): MatchupGrade = when (rank) {
            null -> MatchupGrade.NEUTRAL
            in 1..6 -> MatchupGrade.NIGHTMARE
            in 7..12 -> MatchupGrade.TOUGH
            in 13..20 -> MatchupGrade.NEUTRAL
            in 21..26 -> MatchupGrade.GOOD
            else -> MatchupGrade.ELITE
        }
    }
}

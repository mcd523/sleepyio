package com.sleepyio.sleepyio.recommendation

import com.sleepyio.sleepyio.client.SleeperRepository
import com.sleepyio.sleepyio.client.model.league.SleeperRoster
import com.sleepyio.sleepyio.client.model.player.SleeperPlayer
import com.sleepyio.sleepyio.client.model.player.TrendingPlayer
import com.sleepyio.sleepyio.insight.InsightAggregator
import com.sleepyio.sleepyio.insight.model.PlayerInsight
import com.sleepyio.sleepyio.insight.source.DefenseVsPositionSource
import com.sleepyio.sleepyio.insight.source.InjurySource
import com.sleepyio.sleepyio.insight.source.NewsSource
import com.sleepyio.sleepyio.insight.source.ProjectionsSource
import com.sleepyio.sleepyio.insight.source.UsageSource
import com.sleepyio.sleepyio.insight.source.VegasOddsSource
import com.sleepyio.sleepyio.insight.source.WeatherSource
import com.sleepyio.sleepyio.playerInsight
import com.sleepyio.sleepyio.runSuspending
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Exercises the analyzer behavior that landed with the Phase-1
 * [SleeperRepository] extraction.
 *
 * The pure [WaiverWireAnalyzer.suggestDropCandidates] helper can be tested
 * without the aggregator at all. Full [WaiverWireAnalyzer.findTargets] is
 * now reachable via a fake repository + a subclass of [InsightAggregator]
 * that returns stubbed insights (the same seam QA already uses for
 * StartSit).
 */
class WaiverWireAnalyzerTest {

    // --- suggestDropCandidates (pure helper) -------------------------------

    /** Same-position adds prefer the weakest same-position bench player first. */
    @Test
    fun dropsPrioritiseSamePositionThenWeakestProjection() {
        val analyzer = WaiverWireAnalyzer(aggregator = stubAggregator(), sleeper = EmptyRepo)
        val bench = mapOf(
            "rb_weak" to playerInsight("rb_weak", position = "RB", medianProjection = 3.0),
            "rb_strong" to playerInsight("rb_strong", position = "RB", medianProjection = 15.0),
            "wr_weak" to playerInsight("wr_weak", position = "WR", medianProjection = 2.0),
            "te_mid" to playerInsight("te_mid", position = "TE", medianProjection = 5.0),
        )
        val drops = analyzer.suggestDropCandidates(addPosition = "WR", benchInsights = bench)
        assertEquals("wr_weak", drops.first(), "same-position drop must lead")
        val secondaryGroup = drops.drop(1).take(2).toSet()
        assertTrue(
            "te_mid" in secondaryGroup && "rb_weak" in secondaryGroup,
            "FLEX-compatible bench pieces should rank above non-FLEX — got $drops",
        )
    }

    /** Non-FLEX positions still order by projection ascending. */
    @Test
    fun dropsForDefFallsThroughToWeakestOverall() {
        val analyzer = WaiverWireAnalyzer(aggregator = stubAggregator(), sleeper = EmptyRepo)
        val bench = mapOf(
            "rb" to playerInsight("rb", position = "RB", medianProjection = 12.0),
            "qb" to playerInsight("qb", position = "QB", medianProjection = 20.0),
        )
        val drops = analyzer.suggestDropCandidates(addPosition = "DEF", benchInsights = bench)
        assertEquals("rb", drops.first(), "lowest-projection player drops first when no position match")
    }

    /** Empty bench returns empty. */
    @Test
    fun dropsForEmptyBenchReturnsEmptyList() {
        val analyzer = WaiverWireAnalyzer(aggregator = stubAggregator(), sleeper = EmptyRepo)
        assertEquals(emptyList(), analyzer.suggestDropCandidates("WR", emptyMap()))
    }

    // --- findTargets position filter --------------------------------------

    /**
     * Streaming use case: passing `positionFilter = "DEF"` filters trending
     * candidates to defenses only. We seed two candidates, one DEF + one RB,
     * and assert only the DEF lands in the result.
     */
    @Test
    fun findTargetsPositionFilterKeepsOnlyMatchingPosition() {
        val insights = mapOf(
            "def1" to playerInsight(
                "def1", position = "DEF",
                snapPct = 0.60, targetShare = 0.20, medianProjection = 11.0,
            ),
            "rb1" to playerInsight(
                "rb1", position = "RB",
                snapPct = 0.60, targetShare = 0.20, medianProjection = 11.0,
            ),
        )
        val analyzer = WaiverWireAnalyzer(
            aggregator = stubAggregator(insights),
            sleeper = FakeSleeper(
                rosters = listOf(
                    SleeperRoster(
                        starters = emptyList(),
                        rosterId = 99,
                        players = emptyList(),
                        ownerId = 42L,
                        leagueId = 1L,
                    ),
                ),
                trending = listOf(
                    TrendingPlayer(playerId = "def1", count = 10L),
                    TrendingPlayer(playerId = "rb1", count = 9L),
                ),
            ),
        )
        val results = runSuspendingCapture {
            analyzer.findTargets(leagueId = 1L, rosterId = 99L, week = 7, positionFilter = "DEF")
        }
        assertEquals(1, results.size, "position filter must drop non-DEF candidates")
        assertEquals("def1", results.first().playerId)
    }

    // --- helpers ----------------------------------------------------------

    private fun <T> runSuspendingCapture(block: suspend () -> T): T {
        var captured: T? = null
        runSuspending { captured = block() }
        return captured ?: error("block did not complete synchronously")
    }

    private object EmptyRepo : SleeperRepository {
        override suspend fun getRostersInLeague(leagueId: Long): List<SleeperRoster> = emptyList()
        override suspend fun getAllPlayers(sport: String): Map<String, SleeperPlayer> = emptyMap()
        override suspend fun getTrendingPlayers(
            sport: String, type: String, lookbackHours: Int, limit: Int,
        ): List<TrendingPlayer> = emptyList()
    }

    private class FakeSleeper(
        private val rosters: List<SleeperRoster>,
        private val trending: List<TrendingPlayer>,
    ) : SleeperRepository {
        override suspend fun getRostersInLeague(leagueId: Long): List<SleeperRoster> = rosters
        override suspend fun getAllPlayers(sport: String): Map<String, SleeperPlayer> = emptyMap()
        override suspend fun getTrendingPlayers(
            sport: String, type: String, lookbackHours: Int, limit: Int,
        ): List<TrendingPlayer> = trending
    }

    /** Aggregator that returns canned insights without hitting the network. */
    private fun stubAggregator(insights: Map<String, PlayerInsight> = emptyMap()): InsightAggregator =
        object : InsightAggregator(
            sleeper = EmptyRepo,
            injury = object : InjurySource {
                override suspend fun fetchInjuries(week: Int) = emptyList<com.sleepyio.sleepyio.insight.source.InjuryReport>()
            },
            news = object : NewsSource {
                override suspend fun fetchNewsForPlayer(playerId: String, sinceEpochMs: Long) =
                    emptyList<com.sleepyio.sleepyio.insight.source.NewsItem>()
            },
            weather = object : WeatherSource {
                override suspend fun fetchForecast(gameVenueTeam: String, kickoffEpochMs: Long) = null
            },
            odds = object : VegasOddsSource {
                override suspend fun fetchOdds(week: Int) = emptyMap<String, com.sleepyio.sleepyio.insight.source.TeamOdds>()
            },
            projections = object : ProjectionsSource {
                override suspend fun fetchProjections(week: Int) =
                    emptyMap<String, com.sleepyio.sleepyio.insight.model.ProjectionRange>()
            },
            usage = object : UsageSource {
                override suspend fun fetchUsage(week: Int) =
                    emptyMap<String, com.sleepyio.sleepyio.insight.model.UsageTrend>()
            },
            dvp = object : DefenseVsPositionSource {
                override suspend fun fetchDvp(week: Int) =
                    emptyList<com.sleepyio.sleepyio.insight.model.DefenseVsPosition>()
            },
        ) {
            override suspend fun insightFor(playerId: String, week: Int): PlayerInsight =
                insights[playerId] ?: playerInsight(playerId)
            override suspend fun insightFor(playerIds: Collection<String>, week: Int) =
                playerIds.associateWith { insights[it] ?: playerInsight(it) }
        }

    @Test
    fun analyzerClassName() {
        // compile-time canary preserved from the original test
        assertNotNull(WaiverWireAnalyzer::class.simpleName)
    }
}

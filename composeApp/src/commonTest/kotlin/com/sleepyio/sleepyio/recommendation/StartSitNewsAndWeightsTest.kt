package com.sleepyio.sleepyio.recommendation

import com.sleepyio.sleepyio.client.SleeperRepository
import com.sleepyio.sleepyio.client.model.league.SleeperRoster
import com.sleepyio.sleepyio.client.model.player.SleeperPlayer
import com.sleepyio.sleepyio.client.model.player.TrendingPlayer
import com.sleepyio.sleepyio.insight.InsightAggregator
import com.sleepyio.sleepyio.insight.model.FantasyImpact
import com.sleepyio.sleepyio.insight.model.MatchupGrade
import com.sleepyio.sleepyio.insight.model.PlayerInsight
import com.sleepyio.sleepyio.insight.source.DefenseVsPositionSource
import com.sleepyio.sleepyio.insight.source.InjurySource
import com.sleepyio.sleepyio.insight.source.NewsSource
import com.sleepyio.sleepyio.insight.source.ProjectionsSource
import com.sleepyio.sleepyio.insight.source.UsageSource
import com.sleepyio.sleepyio.insight.source.VegasOddsSource
import com.sleepyio.sleepyio.insight.source.WeatherSource
import com.sleepyio.sleepyio.newsBlurb
import com.sleepyio.sleepyio.playerInsight
import com.sleepyio.sleepyio.runSuspending
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Covers the two StartSitAnalyzer enhancements that landed with the
 * implementation pass: news→factor wiring and position-aware matchup weight.
 */
class StartSitNewsAndWeightsTest {

    /** Negative breaking news inside the 48h window becomes a Recent-news factor. */
    @Test
    fun negativeRecentNewsEmitsFactor() {
        // "Now" is 1000 ms; window is 48h; news published at 500 ms is inside.
        val now = 48L * 60L * 60L * 1000L + 500L
        val analyzer = StartSitAnalyzer(
            aggregator = stubAggregator(),
            nowEpochMs = { now },
        )
        val a = playerInsight("a", position = "WR", medianProjection = 12.0)
        val b = playerInsight(
            "b", position = "WR", medianProjection = 12.0,
            recentNews = listOf(newsBlurb(impact = FantasyImpact.NEGATIVE, publishedEpochMs = now - 1000L)),
        )
        val aggregator = stubAggregator(mapOf("a" to a, "b" to b))
        val scoped = StartSitAnalyzer(aggregator = aggregator, nowEpochMs = { now })
        val recommendation = runSuspendingCapture { scoped.analyze("a", "b", week = 7) }
        assertEquals("a", recommendation.startPlayerId, "negative news on B should flip pick to A")
        val newsFactor = recommendation.rationale.factors.firstOrNull { it.label == "Recent news" }
        assertNotNull(newsFactor, "recent-news factor must be emitted when news impact differs")
        assertEquals(StartSitAnalyzer.WEIGHT_NEWS, newsFactor.weight)
    }

    /** News older than the recency window is ignored. */
    @Test
    fun oldNewsIsFilteredOut() {
        val now = 48L * 60L * 60L * 1000L * 2L
        val cutoff = now - StartSitAnalyzer.NEWS_RECENCY_WINDOW_MS
        val staleBlurb = newsBlurb(impact = FantasyImpact.NEGATIVE, publishedEpochMs = cutoff - 1)
        val aggregator = stubAggregator(
            mapOf(
                "a" to playerInsight("a", position = "WR"),
                "b" to playerInsight("b", position = "WR", recentNews = listOf(staleBlurb)),
            ),
        )
        val analyzer = StartSitAnalyzer(aggregator = aggregator, nowEpochMs = { now })
        val recommendation = runSuspendingCapture { analyzer.analyze("a", "b", week = 7) }
        val hasNewsFactor = recommendation.rationale.factors.any { it.label == "Recent news" }
        assertTrue(!hasNewsFactor, "stale news must not produce a factor")
    }

    /** WR/TE comparisons bump matchup weight 20% above the base constant. */
    @Test
    fun wrComparisonUsesElevatedMatchupWeight() {
        val a = playerInsight("a", position = "WR", matchup = MatchupGrade.ELITE)
        val b = playerInsight("b", position = "WR", matchup = MatchupGrade.NIGHTMARE)
        val analyzer = StartSitAnalyzer(stubAggregator(mapOf("a" to a, "b" to b)))
        val rec = runSuspendingCapture { analyzer.analyze("a", "b", week = 7) }
        val matchup = rec.rationale.factors.first { it.label == "Matchup" }
        assertEquals(StartSitAnalyzer.WEIGHT_MATCHUP * 1.2, matchup.weight)
    }

    /** QB comparisons reduce matchup weight 20% below the base. */
    @Test
    fun qbComparisonUsesReducedMatchupWeight() {
        val a = playerInsight("a", position = "QB", matchup = MatchupGrade.ELITE)
        val b = playerInsight("b", position = "QB", matchup = MatchupGrade.TOUGH)
        val analyzer = StartSitAnalyzer(stubAggregator(mapOf("a" to a, "b" to b)))
        val rec = runSuspendingCapture { analyzer.analyze("a", "b", week = 7) }
        val matchup = rec.rationale.factors.first { it.label == "Matchup" }
        assertEquals(StartSitAnalyzer.WEIGHT_MATCHUP * 0.8, matchup.weight)
    }

    /** RB comparisons leave matchup weight at the base constant. */
    @Test
    fun rbComparisonKeepsBaseMatchupWeight() {
        val a = playerInsight("a", position = "RB", matchup = MatchupGrade.ELITE)
        val b = playerInsight("b", position = "RB", matchup = MatchupGrade.NEUTRAL)
        val analyzer = StartSitAnalyzer(stubAggregator(mapOf("a" to a, "b" to b)))
        val rec = runSuspendingCapture { analyzer.analyze("a", "b", week = 7) }
        val matchup = rec.rationale.factors.first { it.label == "Matchup" }
        assertEquals(StartSitAnalyzer.WEIGHT_MATCHUP, matchup.weight)
    }

    // --- plumbing -------------------------------------------------------

    private fun <T> runSuspendingCapture(block: suspend () -> T): T {
        var captured: T? = null
        runSuspending { captured = block() }
        return captured ?: error("block did not complete synchronously")
    }

    private object EmptyRepo : SleeperRepository {
        override suspend fun getRostersInLeague(leagueId: Long) = emptyList<SleeperRoster>()
        override suspend fun getAllPlayers(sport: String) = emptyMap<String, SleeperPlayer>()
        override suspend fun getTrendingPlayers(
            sport: String, type: String, lookbackHours: Int, limit: Int,
        ) = emptyList<TrendingPlayer>()
    }

    private fun stubAggregator(
        insights: Map<String, PlayerInsight> = emptyMap(),
    ): InsightAggregator = object : InsightAggregator(
        sleeper = EmptyRepo,
        injury = object : InjurySource {
            override suspend fun fetchInjuries(week: Int) =
                emptyList<com.sleepyio.sleepyio.insight.source.InjuryReport>()
        },
        news = object : NewsSource {
            override suspend fun fetchNewsForPlayer(playerId: String, sinceEpochMs: Long) =
                emptyList<com.sleepyio.sleepyio.insight.source.NewsItem>()
        },
        weather = object : WeatherSource {
            override suspend fun fetchForecast(gameVenueTeam: String, kickoffEpochMs: Long) = null
        },
        odds = object : VegasOddsSource {
            override suspend fun fetchOdds(week: Int) =
                emptyMap<String, com.sleepyio.sleepyio.insight.source.TeamOdds>()
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
}

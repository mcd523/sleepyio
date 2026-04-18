package com.sleepyio.sleepyio.recommendation

import com.sleepyio.sleepyio.insight.InsightAggregator
import com.sleepyio.sleepyio.insight.model.MatchupGrade
import com.sleepyio.sleepyio.insight.model.PlayerInsight
import com.sleepyio.sleepyio.insight.source.DefenseVsPositionSource
import com.sleepyio.sleepyio.insight.source.InjuryReport
import com.sleepyio.sleepyio.insight.source.InjurySource
import com.sleepyio.sleepyio.insight.source.NewsItem
import com.sleepyio.sleepyio.insight.source.NewsSource
import com.sleepyio.sleepyio.insight.source.ProjectionsSource
import com.sleepyio.sleepyio.insight.source.TeamOdds
import com.sleepyio.sleepyio.insight.source.UsageSource
import com.sleepyio.sleepyio.insight.source.VegasOddsSource
import com.sleepyio.sleepyio.insight.source.WeatherForecast
import com.sleepyio.sleepyio.insight.source.WeatherSource
import com.sleepyio.sleepyio.insight.model.DefenseVsPosition
import com.sleepyio.sleepyio.insight.model.ProjectionRange
import com.sleepyio.sleepyio.insight.model.UsageTrend
import com.sleepyio.sleepyio.playerInsight
import com.sleepyio.sleepyio.recommendation.model.Confidence
import com.sleepyio.sleepyio.runSuspending
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Minimal [InsightAggregator] test double: overrides the public `insightFor`
 * entrypoints so no data-source wiring or network I/O runs. Constructor wires
 * empty source stubs because the base class requires non-null interfaces.
 */
private class FakeAggregator(
    private val insights: Map<String, PlayerInsight>,
) : InsightAggregator(
    injury = EmptyInjurySource,
    news = EmptyNewsSource,
    weather = NullWeatherSource,
    odds = EmptyOddsSource,
    projections = EmptyProjectionsSource,
    usage = EmptyUsageSource,
    dvp = EmptyDvpSource,
) {
    override suspend fun insightFor(playerIds: Collection<String>, week: Int): Map<String, PlayerInsight> =
        playerIds.mapNotNull { id -> insights[id]?.let { id to it } }.toMap()

    override suspend fun insightFor(playerId: String, week: Int): PlayerInsight =
        insights[playerId] ?: error("Test fixture missing insight for $playerId")
}

private object EmptyInjurySource : InjurySource {
    override suspend fun fetchInjuries(week: Int): List<InjuryReport> = emptyList()
}

private object EmptyNewsSource : NewsSource {
    override suspend fun fetchNewsForPlayer(playerId: String, sinceEpochMs: Long): List<NewsItem> = emptyList()
}

private object NullWeatherSource : WeatherSource {
    override suspend fun fetchForecast(gameVenueTeam: String, kickoffEpochMs: Long): WeatherForecast? = null
}

private object EmptyOddsSource : VegasOddsSource {
    override suspend fun fetchOdds(week: Int): Map<String, TeamOdds> = emptyMap()
}

private object EmptyProjectionsSource : ProjectionsSource {
    override suspend fun fetchProjections(week: Int): Map<String, ProjectionRange> = emptyMap()
}

private object EmptyUsageSource : UsageSource {
    override suspend fun fetchUsage(week: Int): Map<String, UsageTrend> = emptyMap()
}

private object EmptyDvpSource : DefenseVsPositionSource {
    override suspend fun fetchDvp(week: Int): List<DefenseVsPosition> = emptyList()
}

class StartSitAnalyzerTest {

    /**
     * Framework section 1 + 4.1: when A's projection, matchup, and health all
     * favor A, the winner is A and the confidence score should land in HIGH.
     */
    @Test
    fun projectionMatchupAndHealthFavorAYieldsHighConfidenceStartA() = runSuspending {
        val a = playerInsight(
            playerId = "A",
            medianProjection = 22.0,
            matchup = MatchupGrade.ELITE,
            designation = null,
            impliedTeamTotal = 28.0,
        )
        val b = playerInsight(
            playerId = "B",
            medianProjection = 8.0,
            matchup = MatchupGrade.NIGHTMARE,
            designation = "Q",
            impliedTeamTotal = 17.0,
        )
        val analyzer = StartSitAnalyzer(FakeAggregator(mapOf("A" to a, "B" to b)))

        val rec = analyzer.analyze("A", "B", week = 7)

        assertEquals("A", rec.startPlayerId)
        assertEquals(Confidence.HIGH, rec.confidence)
        assertTrue(rec.score >= 75, "expected HIGH score (>=75), got ${rec.score}")
        assertTrue(rec.rationale.factors.size >= 2, "rationale must have >=2 factors")
    }

    /**
     * Framework section 4.2: OUT designation short-circuits the score. When A
     * is OUT the analyzer must pick B decisively (rawScore=5 -> start B).
     */
    @Test
    fun outDesignationOnAForcesStartB() = runSuspending {
        val a = playerInsight(
            playerId = "A",
            medianProjection = 30.0, // A "looks" great but is OUT — must be benched.
            matchup = MatchupGrade.ELITE,
            designation = "OUT",
        )
        val b = playerInsight(
            playerId = "B",
            medianProjection = 5.0,
            matchup = MatchupGrade.NIGHTMARE,
            designation = null,
        )
        val analyzer = StartSitAnalyzer(FakeAggregator(mapOf("A" to a, "B" to b)))

        val rec = analyzer.analyze("A", "B", week = 7)

        assertEquals("B", rec.startPlayerId)
        // rawScore = 5 (start B), confidenceScore = 50 + |5-50| = 95.
        assertEquals(95, rec.score)
        assertTrue(rec.rationale.factors.size >= 2)
    }

    /**
     * Rationale invariant (framework section 4.4): the factors list always
     * contains at least Projection + Matchup, and the score lands within 1 of
     * 50 when insights are identical. Ties deterministically resolve to A
     * because the weightedScore is exactly 50 and `rawScore >= 50` picks A.
     */
    @Test
    fun equalInsightsProduceNeutralScoreAndPreserveRationaleInvariant() = runSuspending {
        val identical = playerInsight(
            playerId = "A",
            medianProjection = 12.0,
            matchup = MatchupGrade.NEUTRAL,
            designation = null,
        )
        val twin = identical.copy(playerId = "B", fullName = "B")
        val analyzer = StartSitAnalyzer(FakeAggregator(mapOf("A" to identical, "B" to twin)))

        val rec = analyzer.analyze("A", "B", week = 7)

        assertTrue(rec.rationale.factors.size >= 2, "rationale must have >=2 factors")
        val labels = rec.rationale.factors.map { it.label }
        assertTrue("Projection" in labels, "Projection factor must be present")
        assertTrue("Matchup" in labels, "Matchup factor must be present")
        // rawScore should be 50 because every factor direction is NEUTRAL.
        // confidenceScore = 50 + |50-50| = 50.
        assertTrue(abs(rec.score - 50) <= 1, "expected score within 1 of 50, got ${rec.score}")
        assertEquals("A", rec.startPlayerId, "tie must deterministically resolve to A")
    }

    /**
     * Weight constants are load-bearing: a silent re-weight would change every
     * recommendation's confidence distribution. This test locks them to the
     * values documented in `ANALYSIS_FRAMEWORK.md` section 1.
     */
    @Test
    fun weightConstantsMatchFrameworkDoc() {
        assertEquals(25.0, StartSitAnalyzer.WEIGHT_PROJECTION)
        assertEquals(15.0, StartSitAnalyzer.WEIGHT_MATCHUP)
        assertEquals(12.0, StartSitAnalyzer.WEIGHT_HEALTH)
    }
}

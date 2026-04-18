package com.sleepyio.sleepyio.recommendation

import com.sleepyio.sleepyio.client.SleeperClient
import com.sleepyio.sleepyio.insight.InsightAggregator
import com.sleepyio.sleepyio.insight.model.PlayerInsight
import com.sleepyio.sleepyio.insight.source.espn.EspnDvpSource
import com.sleepyio.sleepyio.insight.source.espn.EspnInjurySource
import com.sleepyio.sleepyio.insight.source.espn.EspnNewsSource
import com.sleepyio.sleepyio.insight.source.espn.EspnProjectionsSource
import com.sleepyio.sleepyio.insight.source.openmeteo.OpenMeteoWeatherSource
import com.sleepyio.sleepyio.insight.source.stub.StubUsageSource
import com.sleepyio.sleepyio.insight.source.stub.StubVegasOddsSource
import com.sleepyio.sleepyio.recommendation.model.LineupRecommendation
import com.sleepyio.sleepyio.recommendation.model.StartSitRecommendation
import com.sleepyio.sleepyio.recommendation.model.WaiverTarget
import com.sleepyio.sleepyio.recommendation.model.WeeklyReport

/**
 * Top-level recommendation facade matching the [docs/ARCHITECTURE.md] contract.
 *
 * Every UI-facing recommendation surface goes through this interface. An
 * implementation is free to choose its data sources; the shipping default
 * wires ESPN + Open-Meteo + stubs via [DefaultRecommendationService.default].
 */
interface RecommendationService {
    suspend fun weeklyLineup(leagueId: Long, rosterId: Long, week: Int): LineupRecommendation
    suspend fun startSit(playerA: String, playerB: String, week: Int): StartSitRecommendation
    suspend fun waiverTargets(leagueId: Long, rosterId: Long, week: Int): List<WaiverTarget>
    suspend fun playerInsight(playerId: String, week: Int): PlayerInsight
    suspend fun weeklyReport(leagueId: Long, rosterId: Long, week: Int): WeeklyReport

    companion object {
        /**
         * Convenience factory that wires the MVP default stack: ESPN for
         * injuries/news/projections/DvP, Open-Meteo for weather, stub odds +
         * usage until paid feeds are plumbed in.
         */
        fun default(): RecommendationService = DefaultRecommendationService.default()
    }
}

/**
 * Default [RecommendationService] implementation. Each public method
 * delegates to the corresponding analyzer; the service itself owns no logic
 * beyond composition so analyzers stay independently testable.
 */
class DefaultRecommendationService(
    private val aggregator: InsightAggregator,
    private val startSitAnalyzer: StartSitAnalyzer,
    private val waiverAnalyzer: WaiverWireAnalyzer,
    private val insightComposer: PlayerInsightComposer,
    private val reportBuilder: WeeklyReportBuilder,
) : RecommendationService {

    override suspend fun weeklyLineup(leagueId: Long, rosterId: Long, week: Int): LineupRecommendation =
        reportBuilder.build(leagueId, rosterId, week).lineup

    override suspend fun startSit(playerA: String, playerB: String, week: Int): StartSitRecommendation =
        startSitAnalyzer.analyze(playerA, playerB, week)

    override suspend fun waiverTargets(leagueId: Long, rosterId: Long, week: Int): List<WaiverTarget> =
        waiverAnalyzer.findTargets(leagueId, rosterId, week)

    override suspend fun playerInsight(playerId: String, week: Int): PlayerInsight =
        insightComposer.compose(playerId, week)

    override suspend fun weeklyReport(leagueId: Long, rosterId: Long, week: Int): WeeklyReport =
        reportBuilder.build(leagueId, rosterId, week)

    companion object {
        /**
         * Build the MVP default service. Stubs live behind the same interfaces
         * as their real siblings, so swapping in real implementations later is
         * a one-line change in this factory.
         */
        fun default(): RecommendationService {
            val sleeper = SleeperClient
            val usage = StubUsageSource()
            val injury = EspnInjurySource()
            val news = EspnNewsSource()
            val weather = OpenMeteoWeatherSource()
            val odds = StubVegasOddsSource()
            val projections = EspnProjectionsSource(usage)
            val dvp = EspnDvpSource()

            val aggregator = InsightAggregator(
                sleeper = sleeper,
                injury = injury,
                news = news,
                weather = weather,
                odds = odds,
                projections = projections,
                usage = usage,
                dvp = dvp,
            )
            val startSit = StartSitAnalyzer(aggregator)
            val waivers = WaiverWireAnalyzer(aggregator, sleeper)
            val composer = PlayerInsightComposer(aggregator)
            val reports = WeeklyReportBuilder(sleeper, aggregator, startSit, waivers)

            return DefaultRecommendationService(
                aggregator = aggregator,
                startSitAnalyzer = startSit,
                waiverAnalyzer = waivers,
                insightComposer = composer,
                reportBuilder = reports,
            )
        }
    }
}

package com.sleepyio.sleepyio.di

// NOTE: Phase 1 refactor — declarative Koin module that mirrors what the
// existing RecommendationService.default() factory wires manually. The UI
// does NOT yet consume this module — that adoption is Phase 2. Declaring
// it now lets tests opt in via Koin without disturbing existing call
// sites. See docs/architecture/REFACTOR_PLAN.md §4 + §11.

import com.sleepyio.sleepyio.client.SleeperClient
import com.sleepyio.sleepyio.client.SleeperRepository
import com.sleepyio.sleepyio.insight.InsightAggregator
import com.sleepyio.sleepyio.insight.source.DefenseVsPositionSource
import com.sleepyio.sleepyio.insight.source.InjurySource
import com.sleepyio.sleepyio.insight.source.NewsSource
import com.sleepyio.sleepyio.insight.source.ProjectionsSource
import com.sleepyio.sleepyio.insight.source.UsageSource
import com.sleepyio.sleepyio.insight.source.VegasOddsSource
import com.sleepyio.sleepyio.insight.source.WeatherSource
import com.sleepyio.sleepyio.insight.source.espn.EspnDvpSource
import com.sleepyio.sleepyio.insight.source.espn.EspnInjurySource
import com.sleepyio.sleepyio.insight.source.espn.EspnNewsSource
import com.sleepyio.sleepyio.insight.source.espn.EspnProjectionsSource
import com.sleepyio.sleepyio.insight.source.openmeteo.OpenMeteoWeatherSource
import com.sleepyio.sleepyio.insight.source.stub.StubUsageSource
import com.sleepyio.sleepyio.insight.source.stub.StubVegasOddsSource
import com.sleepyio.sleepyio.recommendation.DefaultRecommendationService
import com.sleepyio.sleepyio.recommendation.PlayerInsightComposer
import com.sleepyio.sleepyio.recommendation.RecommendationService
import com.sleepyio.sleepyio.recommendation.StartSitAnalyzer
import com.sleepyio.sleepyio.recommendation.WaiverWireAnalyzer
import com.sleepyio.sleepyio.recommendation.WeeklyReportBuilder
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The single Koin module for the app. Every binding that was previously
 * hand-constructed in `RecommendationService.default()` lives here, one
 * line per dependency.
 *
 * Why a single module for now: we have ~15 bindings, module granularity
 * would be pre-optimization. Phase 2/3 will split this into `dataModule`,
 * `domainModule`, and per-feature modules when we gain more bindings.
 *
 * To consume in production: start Koin once at app launch (phase 2 wires
 * this into `App.kt` / platform entry points).
 *
 * To consume in tests:
 * ```
 * val koin = koinApplication { modules(appModule) }.koin
 * val service = koin.get<RecommendationService>()
 * ```
 */
val appModule: Module = module {
    // --- data layer ----------------------------------------------------
    single<SleeperRepository> { SleeperClient }

    single<UsageSource> { StubUsageSource() }
    single<InjurySource> { EspnInjurySource() }
    single<NewsSource> { EspnNewsSource() }
    single<WeatherSource> { OpenMeteoWeatherSource() }
    single<VegasOddsSource> { StubVegasOddsSource() }
    single<ProjectionsSource> { EspnProjectionsSource(get()) }
    single<DefenseVsPositionSource> { EspnDvpSource() }

    single {
        InsightAggregator(
            sleeper = get(),
            injury = get(),
            news = get(),
            weather = get(),
            odds = get(),
            projections = get(),
            usage = get(),
            dvp = get(),
        )
    }

    // --- domain layer --------------------------------------------------
    single { StartSitAnalyzer(get()) }
    single { WaiverWireAnalyzer(get(), get()) }
    single { PlayerInsightComposer(get()) }
    single { WeeklyReportBuilder(get(), get(), get(), get()) }

    single<RecommendationService> {
        DefaultRecommendationService(
            aggregator = get(),
            startSitAnalyzer = get(),
            waiverAnalyzer = get(),
            insightComposer = get(),
            reportBuilder = get(),
        )
    }
}

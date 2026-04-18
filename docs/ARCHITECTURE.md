# sleepy.io Weekly Recommendation Platform — Architecture

## Vision

Give the user the single best weekly lineup, waiver-wire targets, and trade
advice by fusing Sleeper league/roster data with up-to-the-minute insider
signals (news, injuries, beat reporters, snap counts, target share, weather,
Vegas lines, DVOA matchup data) and surfacing an opinionated recommendation.

## Target shape

The platform is an on-device Kotlin Multiplatform app today (Android, iOS,
Desktop, Web). Heavy or rate-limited data work runs through a recommendation
service abstraction so it can later be hoisted into a Ktor backend without
rewriting the UI.

```
┌────────────────────────────────────────────────────────────────┐
│                   Compose Multiplatform UI                     │
│   Start/Sit • Waiver Wire • Player Insight • Weekly Report     │
└──────────────────────────────▲─────────────────────────────────┘
                               │  ViewModels (common)
┌──────────────────────────────┴─────────────────────────────────┐
│               recommendation.RecommendationService              │
│   - StartSitAnalyzer                                            │
│   - WaiverWireAnalyzer                                          │
│   - PlayerInsightComposer                                       │
│   - WeeklyReportBuilder                                         │
└──────────────────────────────▲─────────────────────────────────┘
                               │
┌──────────────────────────────┴─────────────────────────────────┐
│                   insight.* (data sources)                      │
│   Sleeper  •  InjuryReports  •  BeatWriterNews  •  Weather      │
│   VegasOdds  •  Projections  •  SnapCounts/Usage                │
└─────────────────────────────────────────────────────────────────┘
```

## Package layout (commonMain)

```
com.sleepyio.sleepyio
├── client/                     # existing Sleeper client
├── cache/                      # existing Redis cache
├── insight/
│   ├── model/                  # shared domain types (PlayerInsight, Matchup…)
│   ├── source/                 # data-source interfaces
│   │   ├── InjurySource.kt
│   │   ├── NewsSource.kt
│   │   ├── WeatherSource.kt
│   │   ├── VegasOddsSource.kt
│   │   └── ProjectionsSource.kt
│   ├── source/espn/            # concrete ESPN-backed implementations
│   └── InsightAggregator.kt
├── recommendation/
│   ├── model/                  # Recommendation, Confidence, Rationale
│   ├── StartSitAnalyzer.kt
│   ├── WaiverWireAnalyzer.kt
│   ├── PlayerInsightComposer.kt
│   ├── WeeklyReportBuilder.kt
│   └── RecommendationService.kt
└── ui/
    ├── recommendation/         # Start/Sit, Waiver, Player Insight screens
    └── theme/                  # design system tokens
```

## Cross-cutting rules

1. **Common first.** All business logic goes in `commonMain`. Platform-specific
   code is limited to HTTP engines and presentation entry points.
2. **No network in Composables.** Screens observe `StateFlow` from
   ViewModels; ViewModels call `RecommendationService`.
3. **Data sources are interfaces.** Every external dependency sits behind
   an interface in `insight.source` so it can be stubbed in tests and swapped
   per-platform (e.g. Web CORS, Android background fetch).
4. **Reasoning is first-class.** Every recommendation carries a
   `Rationale` with weighted factors so the UI can explain *why*.
5. **Confidence over certainty.** Recommendations carry a
   `Confidence { HIGH, MEDIUM, LOW }` plus numeric score; UI renders accordingly.
6. **Fail soft.** Any data source that errors must degrade gracefully —
   the recommendation just loses that factor rather than the whole response
   failing.

## Contract: `RecommendationService`

```kotlin
interface RecommendationService {
    suspend fun weeklyLineup(leagueId: Long, rosterId: Long, week: Int): LineupRecommendation
    suspend fun startSit(playerA: String, playerB: String, week: Int): StartSitRecommendation
    suspend fun waiverTargets(leagueId: Long, rosterId: Long, week: Int): List<WaiverTarget>
    suspend fun playerInsight(playerId: String, week: Int): PlayerInsight
    suspend fun weeklyReport(leagueId: Long, rosterId: Long, week: Int): WeeklyReport
}
```

## Key domain types (target signatures — defined in code by the FF expert)

- `Recommendation` — polymorphic sealed type with `Rationale` + `Confidence`.
- `PlayerInsight` — projection range, recent news, injury, usage trend, matchup.
- `Rationale` — list of `Factor(label, weight, evidence)`.
- `WaiverTarget` — player + priority score + suggested FAAB bid.
- `WeeklyReport` — top lineup decisions, risky starts, waiver moves, sit-downs.

## External data sources (MVP)

| Source                | Used for                   | Notes                                     |
|-----------------------|----------------------------|-------------------------------------------|
| Sleeper (existing)    | rosters, matchups, players | already in `client/`                      |
| ESPN hidden API       | injuries, news, projections| public JSON endpoints, no auth            |
| Open-Meteo            | stadium weather            | free, no key                              |
| The Odds API          | Vegas spreads/totals       | optional, behind interface                |
| RotoWire / FantasyPros | expert consensus           | optional, behind interface (requires key) |

The MVP ships with ESPN + Open-Meteo + stubbed Vegas source so the feature is
fully functional without paid keys.

## Navigation additions

`App.kt` currently has USER_LOGIN → LEAGUE_LIST → LEAGUE_STATE. The
frontend engineer adds a fourth destination, **LEAGUE_ADVISOR**, with four
tabs: `Weekly Report`, `Start/Sit`, `Waivers`, `Player Insight`.

## What this doc fixes

This is the contract every agent reads before they start. UX writes against
the screen list. Backend writes against `RecommendationService` and the
`insight.source` interfaces. Frontend consumes ViewModels that call the
service. QA targets the pure-logic analyzers.

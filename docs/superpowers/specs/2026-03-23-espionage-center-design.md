# Espionage Center Design Spec

## Context

sleepy.io currently shows a consolidated cross-league activity feed and per-league intel with 6 insight modules (H2H, Vulnerability, TransactionActivity, DraftTendency, ScoringTrend, RosterComposition). The app needs to evolve into a competitive intelligence center that monitors opponents across ALL their leagues — including ones the user isn't in — to provide predictive, tactical, strategic, and competitive intel.

The user plays in 2-4 leagues per season with significant opponent overlap (same friend group), making deep behavioral profiling of a small set of recurring rivals the highest-value use case.

## Architecture: Hybrid Federated Intel + Tiered Caching

### Core Approach

Create `OpponentRepository` that extends `LeaguemateRepository`, inheriting all existing data-fetching methods and adding shadow league discovery and cross-league aggregation. Existing `InsightModule<T>` implementations continue to work unchanged — their `analyze()` method receives `LeaguemateRepository` (the supertype), and the `leagueHistory` map is widened to include shadow leagues. New cross-league-aware modules use an extended `SurveillanceInsightModule` interface that takes `OpponentRepository` directly for shadow-league-specific queries.

Bolt on a 3-tier caching strategy for rate management.

### Data Model Extension

```kotlin
data class OpponentLeague(
    val league: SleeperLeague,
    val isShared: Boolean,
    val season: String
)

enum class IntelCategory { PREDICTIVE, TACTICAL, STRATEGIC, COMPETITIVE }
enum class DataTier { DISCOVERY, METADATA, DEEP }

interface SurveillanceInsightModule<T> : InsightModule<T> {
    val category: IntelCategory
    val requiredTier: DataTier
    suspend fun analyzeCrossLeague(
        targetUserId: String,
        myUserId: String,
        allLeagues: Map<Long, List<OpponentLeague>>,
        sharedLeagues: Set<Long>,
        repository: OpponentRepository
    ): T?
}
```

### 3-Tier Data Pipeline

**Tier 1 — Discovery** (~10-20 API calls, cached 6h)
- `GET /user/{userId}/leagues/nfl/{season}` for each opponent × current + 2 prior seasons
- Produces: `Map<OpponentId, Map<Season, List<OpponentLeague>>>`
- Fires on login

**Tier 2 — Metadata** (~30-60 API calls, cached 2h)
- For each shadow league: `GET /league/{id}/rosters`, `GET /league/{id}/users`, `GET /league/{id}`
- Produces: roster snapshots, league settings, FAAB budgets
- Fires on login alongside Tier 1

**Tier 3 — Deep Intel** (~150-400 API calls, cached 1h)
- Transactions × 18 weeks, matchups × 18 weeks, draft picks per shadow league
- Fires on-demand when opening a dossier
- **Exception:** auto-scans this week's matchup opponent in background

**Rate limiting:** Shared rate limiter across all tiers — max 10 concurrent requests, batches of 10 requests with 100ms delay between batches. Redis-backed caching prevents re-fetching within TTL windows.

### Creative Data Sourcing

1. **Trending Players Correlation** — Cross-reference `GET /players/nfl/trending/add` with opponent roster holes to predict waiver targets
2. **Projection Delta Tracking** — Fetch weekly projections for opponent roster players; flag sharp drops as potential streaming/drop signals
3. **News Reaction Speed** — Compare GraphQL player news timestamps against opponent transaction timestamps to profile reaction latency
4. **GraphQL Introspection** (experimental) — Probe `sleeper.com/graphql` for undocumented queries (league chat activity, last-active timestamps, matchup legs). This depends on undocumented API surface and may not yield results.

## New Intelligence Modules (8 total)

All implement `SurveillanceInsightModule<T>` and register in `InsightRegistry`.

### Tier 1: Core Cross-League

| Module | Category | Data Tier | Purpose |
|--------|----------|-----------|---------|
| ConvergentInterestModule | COMPETITIVE | METADATA | Players rostered across multiple leagues — reveals high-conviction targets |
| WaiverPatternModule | PREDICTIVE | DEEP | Cross-league add/drop timing patterns — predicts waiver targets from shadow league moves |
| FAABIntelModule | TACTICAL | METADATA | FAAB spending psychology across leagues — predicts bid behavior |
| ShadowRosterModule | STRATEGIC | METADATA | Composite "dream team" from all leagues — reveals player preferences and roster construction patterns |

### Tier 2: Advanced Analysis

| Module | Category | Data Tier | Purpose |
|--------|----------|-----------|---------|
| TradeNetworkModule | STRATEGIC | DEEP | Maps trade partners, deal structures, negotiation style across leagues |
| AnomalyDetectorModule | COMPETITIVE | DEEP | Flags unusual activity spikes — sudden roster churn, pattern breaks, late-night transactions |
| ThreatLevelModule | TACTICAL | DEEP | Composite threat score aggregating all other module outputs (runs after all other modules complete) |
| CounterIntelModule | COMPETITIVE | DEEP | Detects when opponents may be targeting you — counter-drafting, picking up your drops |

## Screens

### War Room Dashboard (replaces HomeShellScreen as default)

**Layout (desktop ≥1200px):** Two-column with alert banner.
- **Top:** Alert banner — high-priority intel (convergent interests, anomalies, counter-intel signals)
- **Left column:** Opponent cards ranked by threat level. This-week's opponent highlighted with red border. Each card shows: avatar, name, shared/shadow league counts, threat score, intel chip tags (injuries, FAAB %, convergent players, activity level)
- **Right column:** Shadow Activity Feed — cross-league transactions annotated with espionage context ("Also in your league", "Selling high?", shadow league name). Color-coded by transaction type with purple for anomalies.

**Responsive:**
- Tablet (800-1199px): Stacked layout
- Mobile (<800px): Tabbed (Opponents / Feed / Alerts)

### Opponent Dossier (click from War Room)

**Header:** Avatar, name, shared/shadow league counts, threat level score, quick stats row (H2H record, avg pts/wk, moves this season, convergent player count).

**Module Grid (2-column on desktop):**
1. New cross-league modules at top (ConvergentInterest, WaiverPattern, ShadowRoster, ThreatBreakdown, FAAB, TradeNetwork, Anomaly, CounterIntel)
2. Existing 6 modules below — now powered by shadow league data
3. Each module loads independently with loading/error/empty states via existing `InsightCard` component

### Navigation Changes

- `HomeShellScreen` becomes `WarRoomScreen`
- `LeaguemateProfileScreen` becomes `OpponentDossierScreen` with shadow league awareness
- Per-league intel tab still exists for league-scoped analysis
- Navigation: `LOGIN → WAR_ROOM → (OPPONENT_DOSSIER | LEAGUE_DETAIL)`

## Key Files to Modify

| File | Change |
|------|--------|
| `intel/LeaguemateRepository.kt` | Extend to `OpponentRepository` with shadow league discovery + tiered caching |
| `intel/InsightModule.kt` | Add `SurveillanceInsightModule` extended interface |
| `intel/InsightRegistry.kt` | Support category/tier metadata on module registration |
| `client/SleeperClient.kt` | Add convenience methods for shadow league crawling, rate limiter |
| `ui/home/HomeShellScreen.kt` | Replace with `WarRoomScreen` — absorbs `CrossLeagueFeedScreen` into the Shadow Activity Feed panel; `FeedDataLoader` is refactored to use `OpponentRepository` for shadow league awareness |
| `ui/leaguemate/LeaguemateProfileScreen.kt` | Evolve to `OpponentDossierScreen` |
| `cache/SleeperCache.kt` | Extend with tiered TTL support for surveillance data |
| `App.kt` | Update navigation to include WAR_ROOM as default post-login |

## New Files

| File | Purpose |
|------|---------|
| `intel/OpponentRepository.kt` | Cross-league data discovery and aggregation |
| `intel/SurveillanceCache.kt` | Tiered caching with configurable TTLs |
| `intel/RateLimiter.kt` | Shared rate limiter (10 concurrent, 100ms batching) |
| `intel/modules/ConvergentInterestModule.kt` | New module |
| `intel/modules/WaiverPatternModule.kt` | New module |
| `intel/modules/FAABIntelModule.kt` | New module |
| `intel/modules/ShadowRosterModule.kt` | New module |
| `intel/modules/TradeNetworkModule.kt` | New module |
| `intel/modules/AnomalyDetectorModule.kt` | New module |
| `intel/modules/ThreatLevelModule.kt` | New module |
| `intel/modules/CounterIntelModule.kt` | New module |
| `ui/warroom/WarRoomScreen.kt` | War Room dashboard |
| `ui/warroom/OpponentCard.kt` | Opponent summary card component |
| `ui/warroom/AlertBanner.kt` | Priority alert banner |
| `ui/warroom/ShadowActivityFeed.kt` | Cross-league activity feed |
| `ui/dossier/OpponentDossierScreen.kt` | Enhanced opponent profile |

## Implementation Phases

### Phase 1: Data Foundation
- `OpponentRepository` with Tier 1+2 discovery and metadata fetching
- `RateLimiter` and `SurveillanceCache`
- `SurveillanceInsightModule` interface
- Existing modules receive shadow league data

### Phase 2: Core Espionage Modules
- ConvergentInterestModule
- ShadowRosterModule
- FAABIntelModule
- WaiverPatternModule

### Phase 3: War Room Dashboard
- WarRoomScreen with opponent grid + shadow activity feed
- AlertBanner with priority intel
- Navigation changes (WAR_ROOM as default)

### Phase 4: Advanced Modules + Tier 3
- TradeNetworkModule
- AnomalyDetectorModule
- ThreatLevelModule (aggregates all other modules)
- CounterIntelModule
- Tier 3 on-demand deep data fetching

### Phase 5: Opponent Dossier Upgrade
- OpponentDossierScreen with all 14 modules (6 existing + 8 new)
- Progressive loading as tiers complete
- Auto-scan this-week's opponent

### Phase 6: Creative Intelligence
- Trending player correlation engine
- Projection delta tracking
- News reaction speed profiling
- GraphQL introspection for undocumented data

## Verification

1. **Unit tests:** Each new module gets test coverage for its analysis logic
2. **Visual verification:** Run JS target (`./gradlew :composeApp:jsBrowserDevelopmentRun`), use Playwright to screenshot War Room and dossier screens
3. **Data pipeline test:** Log in with real Sleeper credentials, verify shadow league discovery returns correct leagues for known opponents
4. **Rate limiting test:** Monitor API call volume during login to confirm <100 calls in first 10s
5. **Progressive loading test:** Open dossier, verify Tier 1+2 data renders immediately, Tier 3 data appears progressively
6. **Cross-platform:** Verify on Desktop (JVM) and Web (JS) targets

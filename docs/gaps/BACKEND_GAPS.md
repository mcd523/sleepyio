# Backend Implementation Gaps

Audit of the recommendation/data layer for stubs, fragility, and cheap wins.
Focus: what the code *says* it does vs. what it actually does today.

## 1. Stub + fallback inventory

| File | Stub | Replacement |
|---|---|---|
| `insight/source/espn/EspnProjectionsSource.kt` | projection derived from usage × matchup grade, not a real ESPN feed | nflverse player_stats + derive; or SportsDataIO paid |
| `insight/source/espn/EspnDvpSource.kt` | every team NEUTRAL rank 16 vs every position | nflverse PBP → rolling 6-week FPA |
| `insight/source/stub/StubVegasOddsSource.kt` | fixed `impliedTeamTotal=22.0, spread=0.0` | The Odds API free tier |
| `insight/source/stub/StubUsageSource.kt` | fixed snap=0.55, target=0.18, carry=0.20, RZ=2 | nflverse snap_counts + player_stats |
| `InsightAggregator.insightFor(...)` | `opponent = null` always | ESPN `/scoreboard` endpoint → team vs team |
| `WaiverWireAnalyzer.findTargets(...)` line 64 | `dropCandidates = emptyList()` | Rank user's bench by projection, lowest at matching pos |
| `WeeklyReportBuilder.selectLineup` | hardcoded `QB, RB, RB, WR, WR, TE, FLEX, DEF, K` | Read `SleeperLeague.rosterPositions` |
| `EspnProjectionsSource` ctor accepts `usage: UsageSource` | circular: projections derived from usage | Replace source; remove the dependency |

## 2. Correctness gaps vs. the framework

`ANALYSIS_FRAMEWORK.md §1` weight table:

| Factor | Framework weight | Implementation |
|---|---|---|
| Opportunity | 25 | StartSit folds into "Projection" (proxy); waiver uses snap+target |
| Matchup | 15 | ✓ wired (`StartSitAnalyzer.matchupScore`), but DvP source is stubbed |
| Health | 12 | ✓ wired with short-circuit for OUT/IR |
| Game script | 10 | Partial — `impliedTeamTotal` only read in `StartSitAnalyzer.environmentScore`, ignored in waiver/weekly |
| Teammate availability | 10 | **Not computed.** Framework wants WR2 bump when WR1 out; no code path. |
| Recent form | 8 | Partial — usage trend captures last-3 only, not 6-week delta |
| Weather | 5 | Wired but two different thresholds (15 vs 20 mph); inconsistent |
| O-line health | 7 | **Not computed.** Requires filtering ESPN injuries to OL positions. |
| ECR | 5 | **Not computed.** No ECR source. |
| Ownership | 3 | **Not computed.** Trending is fetched but not surfaced in scoring. |

**Headline advice** (`WeeklyReportBuilder.buildHeadline`) uses the top *projection* starter, not the strongest positive factor the spec requires. Divergence from `ANALYSIS_FRAMEWORK §4.3`.

**FAAB tiers** (`WaiverWireAnalyzer.faabPctFor`) use 4 buckets (50/24/7/1). Framework `§3.2` specifies tier 1 (bid 25–40% of budget), tier 2 (12–24%), tier 3 (3–10%), tier 4 (0–2%). The top tier's 50% is too aggressive.

**`findClosestCalls`** in `WeeklyReportBuilder` returns top 3 by projection delta. A closer call at the WR slot can swamp a marginal QB call — fine, but it can also hide a FLEX decision entirely. Consider "top 1 per slot" as an alternative.

## 3. Reliability / resilience gaps

- **HttpClient leaks**: `SleeperClient.client` and `EspnHttp.client` are created in module initializers and never closed. For a long-lived mobile session this is OK; for Desktop/JVM it's a socket leak at shutdown.
- **No request timeouts** configured on Ktor `HttpClient`. Add `install(HttpTimeout) { requestTimeoutMillis = 8_000; connectTimeoutMillis = 3_000 }` once in the shared client.
- **No retry** anywhere in source code. Transient 5xx = permanent failure for that week's insight.
- **No circuit breaker**. If ESPN injury endpoint is down for 10 minutes, every `weeklyReport` call eats the 3-second timeout per player.
- **`SleeperCache` is JVM-only**. On iOS/Android/Web/WasmJs, no caching occurs — every screen navigation re-fetches. This is a big performance issue for mobile.
- **Fail-soft inconsistent**: `source/*` swallows to `emptyList()` with a log; `WeeklyReportBuilder` returns with null roster silently; `InsightAggregator` returns a default `PlayerInsight` on complete source failure? Verify — code path isn't obvious.

## 4. Extensibility gaps

- `DefaultRecommendationService.default()` constructs all sources inline. Adding `NflverseUsageSource` means editing the factory. A cleaner shape:

```kotlin
class InsightAggregator(
    private val sleeper: SleeperClient,
    private val contributors: List<InsightContributor>,  // plug-in sources
)
```

Each contributor implements `suspend fun contribute(context, player): PlayerInsight.Updater`. The aggregator composes them left-to-right. New source = register in Koin module, no constructor change.

- `RecommendationService.default()` fanout means every integration test has to build the full graph. Phase-2 refactor introduces DI (see `architecture/REFACTOR_PLAN.md` when it lands).

## 5. Caching strategy gaps

Nothing cached today except the Sleeper players list (JVM only).

Minimum viable cross-platform cache:

```kotlin
interface InsightCache {
    suspend fun <T : Any> getOrPut(key: String, ttl: Duration, compute: suspend () -> T): T
}
```

Implementation candidates:

1. **multiplatform-settings + kotlinx.serialization** — 30 LOC, good for small values, great for phase 2.
2. **SQLDelight** — proper schema, supports blob/text, phase 3 when accuracy tracking needs history.

Reuse the TTLs from `docs/fantasy/DATA_SOURCES.md`.

## 6. Testability gaps

(See `docs/qa/KNOWN_ISSUES.md` for the specific waiver-analyzer finding.)

Additional:

- `InsightAggregator` takes 7 source interfaces *plus* `SleeperClient` as a concrete `data object`. Not testable without the network. Extract `SleeperRepository` interface (Phase-1 refactor).
- `EspnHttp` creates its own `HttpClient`. Share one client via Koin so tests can inject a `MockEngine`.
- `runSuspending` helper in `commonTest/Fixtures.kt` is a workaround for `kotlinx-coroutines-test` not being on the classpath. Add the dep; delete the helper.

## 7. Performance gaps

- `InsightAggregator.insightFor(Collection)` — verify it batches. Each source has a *week-scoped* `fetch*(week)` that should be called once; per-player calls are for weather (stadium-keyed) and news (player-keyed). Code may or may not do this correctly.
- `WeeklyReportBuilder.build` makes ~5 suspend calls (roster, all-players, insight batch, waiver analyze, start-sit per close-call). The `getAllPlayers("nfl")` fetches ~2 MB of JSON every report build — **cache once per 24h** (the SleeperCache was supposed to do this but is JVM-only).
- `WaiverWireAnalyzer` fetches rosters + trending sequentially. Parallelize with `coroutineScope { async { ... } }`.

## 8. Ranked fix list (top 10)

| Rank | Fix | Effort (hrs) | Risk | Impact |
|---|---|---|---|---|
| 1 | Extract `SleeperRepository` interface | 1 | low | Unblocks QA gap + all future repo-pattern work |
| 2 | Drop-candidate heuristic in `WaiverWireAnalyzer` | 2 | low | Finishes an always-empty field |
| 3 | Parallelize `WaiverWireAnalyzer` roster+trending fetch | 1 | low | Sub-second waiver tab load |
| 4 | Add `HttpTimeout` + Ktor `HttpRequestRetry` plugin once | 2 | low | Eliminates 10-min hangs on dead sources |
| 5 | Wire `getAllPlayers` into a cross-platform cache | 4 | med | 10× faster report load on mobile |
| 6 | Fix `buildHeadline` to use strongest positive factor | 1 | low | Matches framework spec |
| 7 | Read `SleeperLeague.rosterPositions` in `selectLineup` | 2 | med | Unlocks SuperFlex / 2QB / TE-premium leagues |
| 8 | FAAB tier bands match framework | 0.5 | low | More conservative bids |
| 9 | Pluggable `InsightContributor` (list-based) | 4 | med | Unblocks nflverse phase |
| 10 | Introduce `Outcome<T>` at service boundary | 3 | med | Phase-1 unified errors |

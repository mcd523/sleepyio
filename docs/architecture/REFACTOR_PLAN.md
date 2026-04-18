# Refactor Plan — From "Works" to "Maintainable"

> **Audience:** the owner of sleepy.io, who is new to mobile development.
> **Goal:** explain *why* we're changing the architecture before we change it,
> so future you can repeat the decisions when you hit the next feature.

This plan turns a single-module Kotlin Multiplatform project into a layered,
testable, plug-in-friendly system **without** a rewrite. Every change is
justified. Every change is reversible. We pick the smallest possible version
of each idea so you don't drown in abstractions.

---

## 1. Guiding principles

These five rules are the "why" for every decision below. Learn these once,
they apply for years.

1. **Separation of concerns.** Code that *fetches* data, code that *analyses*
   data, and code that *renders* data should live in different places. When
   you mix them, you can't change one without breaking another. In the
   current codebase, `LeagueStateScreen.kt` fetches, analyses, *and* renders
   matchup data in the same Composable. That's why it's ~600 lines.
2. **Explicit dependencies.** A class should say what it needs in its
   constructor. Today, `WaiverWireAnalyzer` depends on the global
   `SleeperClient` singleton — that's an invisible dependency, and it's the
   exact reason QA couldn't test it (see `docs/qa/KNOWN_ISSUES.md`).
3. **Single source of truth.** For colors, we currently have two systems:
   `MyTheme` (compose-unstyled) and `MaterialTheme.colorScheme`. For data
   loading, we have `LaunchedEffect + rememberCoroutineScope` in Composables
   *and* `viewModelScope` in ViewModels. Pick one per concern.
4. **Fail soft at the edge, fail loud at the core.** Network calls at the
   very edge (a `Source` talking to ESPN) should swallow errors and return
   empty. Pure logic at the core (an analyzer) should throw if it gets
   impossible input. Mixing these leads to silent bugs.
5. **Pure core, effectful shell.** Analyzers should be pure functions: same
   input → same output, no I/O. Network, persistence, time — those live at
   the shell. A pure core is trivially testable and trivially reasoned
   about.

---

## 2. Target architecture

We're moving from a flat `com.sleepyio.sleepyio.*` layout to a **layered**
one. Pictured:

```
┌── UI Layer (Compose screens) ──────────────────────────────┐
│   ui/recommendation/*Tab.kt, ui/league/*, App.kt           │
│   Rule: no suspend calls, no service wiring.               │
├── Presentation Layer (ViewModels + UiState) ───────────────┤
│   ui/recommendation/*ViewModel.kt                          │
│   Rule: expose StateFlow<ScreenState<T>>; no domain leaks. │
├── Domain Layer (Use Cases + pure analyzers) ───────────────┤
│   recommendation/*Analyzer.kt                              │
│   Rule: pure functions. No HTTP, no cache, no time.        │
├── Data Layer (Repositories + Sources) ─────────────────────┤
│   insight/InsightAggregator.kt, insight/source/*           │
│   Rule: fail soft. Cache here, not in domain.              │
├── Platform Layer (Capabilities + DI) ──────────────────────┤
│   platform/*.kt, di/AppModule.kt (new)                     │
│   Rule: expect/actual lives here. No business logic.       │
└─────────────────────────────────────────────────────────────┘
```

**What to notice.** Today, `WeeklyReportBuilder` ("domain") reads from
`SleeperClient` ("data") *and* from `InsightAggregator` ("data"). That's two
data sources talking to a domain class. The refactor introduces a single
`InsightRepository` the domain layer can reach, and the repository handles
multi-source coordination. The analyzer becomes pure.

---

## 3. Module boundaries (Phase 3 — design now, execute later)

Gradle modules give you: faster incremental builds, compile-time layer
enforcement (a lower module can't import an upper one because it isn't on
the classpath), and the ability to swap an entire module's implementation
without touching the others.

Proposed split, in dependency order:

```
:core-model         // pure data classes, serialization only
:core-util          // coroutine helpers, logging
:core-platform      // expect/actual capability surface
:data-sleeper       // SleeperClient + SleeperRepository
:data-insight       // InsightAggregator + sources (all of them)
:data-cache         // CrossPlatformCache abstraction
:domain-recommendation  // analyzers + use cases
:feature-advisor    // Advisor screens + ViewModels
:feature-league     // League screens + ViewModels
:design-system      // Theme + shared Composables (ConfidenceBadge, etc.)
:app                // wires everything, contains App.kt + NavigationScreen
```

### Why split like this

- **`:core-model` cannot import anything.** It's the lingua franca —
  everyone returns its types. This is why `PlayerInsight` must live there,
  and why it must not be `@Serializable` in the domain layer (wire types
  stay in data modules). The current domain types are already pure — good.
- **`:data-*` depends on `:core-*`.** Swapping out ESPN for SportsDataIO
  becomes an `:data-sportsdataio` module that implements the same interfaces
  as `:data-insight`, no other module knows.
- **`:domain-recommendation` depends on `:data-*` via interfaces, not
  implementations.** This is exactly the `SleeperRepository` extraction
  discussed below.
- **`:feature-*` depend on `:design-system`, `:domain-*`, and `:core-*`.**
  Never on each other. Features can ship and remove independently.
- **`:app` is the only place that knows how to wire everything.** Koin
  modules live there (or in each feature module).

### Why **not** split in Phase 1

A module split is mechanically easy but semantically risky. You need every
interface to be in the right place, or you get circular dependencies that
are painful to untangle. Phase 1 instead puts the right interfaces in the
right packages; Phase 3 physically separates them.

---

## 4. Dependency injection

**Problem.** `RecommendationService.default()` hand-constructs every
source, aggregator, and analyzer in a 30-line factory. Adding a new source
means editing the factory. Testing a single analyzer means building the
full graph.

**Solution: Koin.** A KMP-idiomatic service locator where modules are
declarative Kotlin:

```kotlin
val dataModule = module {
    single<SleeperRepository> { SleeperClientImpl(httpClient = get()) }
    single<InjurySource> { EspnInjurySource(get()) }
    // ... one line per binding
}

val domainModule = module {
    single { StartSitAnalyzer(aggregator = get()) }
    single { WaiverWireAnalyzer(aggregator = get(), sleeper = get()) }
    single<RecommendationService> { DefaultRecommendationService(...) }
}
```

**Why Koin over alternatives?**

- **vs. kotlin-inject / Dagger.** Those are compile-time, require code-gen,
  and trip up new mobile devs. Koin is runtime, pure Kotlin, and its errors
  are easy to read.
- **vs. manual service locator.** A hand-rolled `AppContainer` works for 20
  dependencies but becomes a god object at 80. Koin's `module { ... }` DSL
  reads the same way and scales.
- **vs. CompositionLocal DI.** We already use `LocalRecommendationService`
  and `LocalPlatformCapabilities`. That's fine for a *handful* of things
  and a great UI-layer pattern, but it doesn't scale to analyzers/sources
  because non-Composable code can't read `LocalXxx`.

**Phase 1 scope for Koin.** Add the dependency. Declare one `appModule`
wiring everything the existing `RecommendationService.default()` factory
already builds. Do NOT make the UI read from Koin yet — that's Phase 2.
The goal of Phase 1 is to make the existing factory a one-liner.

---

## 5. Repository pattern + caching

**Problem.** Analyzers call `aggregator.insightFor(...)` directly. The
aggregator calls sources directly. There's no layer to attach caching,
retry, or offline behaviour. When we add a `Connectivity` flow (already
built; see `docs/gaps/MOBILE_EDGE_GAPS.md`) we'd need to modify every
source. That's wrong.

**Solution: an `InsightRepository` layer.**

```kotlin
interface InsightRepository {
    suspend fun insightFor(playerId: String, week: Int): PlayerInsight
    suspend fun insightFor(playerIds: Collection<String>, week: Int): Map<String, PlayerInsight>
    // Flow variant for screens that want live updates
    fun observeInsight(playerId: String, week: Int): Flow<Outcome<PlayerInsight>>
}

class DefaultInsightRepository(
    private val aggregator: InsightAggregator,
    private val cache: CrossPlatformCache,
) : InsightRepository { /* cache-aside with per-key TTL */ }
```

TTLs come from `docs/fantasy/DATA_SOURCES.md` §"Rate-limit + caching
strategy". The repository is where caching lives, not the sources. This
means one place to change when Redis-on-JVM needs to become
multiplatform-settings everywhere — **which is exactly what Phase 2 will
do**.

**Cache decision.** For phase 2, use `com.russhwolf:multiplatform-settings
-coroutines` + `kotlinx-serialization-json` to persist JSON blobs with
TTL. 50 LOC. For phase 3, when historical accuracy needs relational
queries, add **SQLDelight**. Don't do both now.

---

## 6. Unified result type

**Problem.** `Source` methods swallow to empty; `RecommendationService`
throws; ViewModels wrap the call in `try/catch { ... it.message }`. Three
different error models, three times the boilerplate.

**Solution: `Outcome<T>`**, a sealed result type:

```kotlin
sealed interface Outcome<out T> {
    data class Ok<T>(val value: T) : Outcome<T>
    data class Fail(val reason: Reason, val cause: Throwable? = null) : Outcome<Nothing>
    data object Loading : Outcome<Nothing>
}

enum class Reason { NETWORK, PARSE, NOT_FOUND, UNAUTHORIZED, CANCELLED, UNKNOWN }
```

Where it belongs:
- **Source interfaces**: still return `List<T>`/`T?` and swallow — they're
  low-level.
- **Repository interfaces**: return `Outcome<T>` or `Flow<Outcome<T>>`.
- **ViewModels**: consume `Outcome` into `ScreenState`.
- **Composables**: never see `Outcome` — they only see `ScreenState`.

**Why not `kotlin.Result`?** It's sealed in stdlib, has weaker exhaustive
`when`, and doesn't support a `Loading` state. Our UI always wants
`Loading` as a first-class value.

**Phase 1 scope.** Just add the file + KDoc with examples. Existing code
continues to throw. Phase 2 migrates boundaries one at a time.

---

## 7. Navigation (Phase 3)

Right now `App.kt` is `when (currentScreen) { USER_LOGIN -> ...; LEAGUE_LIST -> ...; ... }`. It works for 6 screens. It doesn't survive configuration changes, there's no back stack (hardware-back on Android drops you out), deep-links (already parsed by `DeepLinkParser`!) have nowhere to dispatch.

Options:

- **(a) Keep `when`.** Works for ~10 screens. Free.
- **(b) Compose Navigation (`androidx.navigation.compose`).** Android-first;
  KMP support is nascent (2026-era) but still rough on Desktop/Web.
- **(c) Decompose (by Arkivanov).** KMP-native, has a back stack, survives
  config changes, deep-link routing is built-in. Steeper learning curve.

**Recommendation: Decompose in Phase 3.** Too much change for Phase 1/2;
wait until the app has ~10 screens so the payoff is worth the ceremony.

---

## 8. ViewModel lifecycle

**Problem.** Every tab composable has `val vm = remember { WeeklyReportViewModel(service) }`. `remember` is scoped to the Composable's key — if the parent recomposes with a new key, we get a new VM and lose state. It also means the VM doesn't participate in Android's process-death survival.

**Solution.** Replace with `viewModel { ... }` (the androidx `lifecycle-viewmodel-compose` API — already on the classpath). Koin has `koinViewModel()` that wires both.

Phase 2. Low risk; requires per-tab edit.

---

## 9. Theming consolidation

Drop the `com.composeunstyled.theme` usage. Use `MaterialTheme` with a
`SleepyIoColorScheme` built from the tokens in `DESIGN_SYSTEM.md`. One
source of truth. Material3 gives screen-reader-safe defaults.

**Phase 3.** Requires touching every screen that reads `Theme[colors][...]`.
Not a functional change — a cleanup. Do it last.

---

## 10. Test infrastructure

**Problem.** `kotlinx-coroutines-test` isn't on the classpath, so `Fixtures.kt` has a hand-rolled `runSuspending` that uses `runBlocking` on JVM and ad-hoc on other targets. You can't use `TestScope.advanceTimeBy(...)` to unit-test a cache TTL.

**Solution.** Add `org.jetbrains.kotlinx:kotlinx-coroutines-test` to
`commonTest.dependencies`. Keep `runSuspending` as `typealias` of `runTest`
for one release so existing tests don't break in git blame.

**Phase 1.** Two-line build-file change. Low risk. Unlocks real async
testing.

---

## 11. Phasing

### Phase 1 — 1–2 days, minimum viable, all backwards-compatible

1. Extract `SleeperRepository` interface from `SleeperClient`. `SleeperClient` keeps its old surface (delegates). Analyzers depend on the interface. This closes the QA gap.
2. Introduce `Outcome<T>`. Don't migrate anything yet — just declare.
3. Add Koin + a single `appModule` that rebuilds the graph the old factory built. UI doesn't read from Koin yet.
4. Add `kotlinx-coroutines-test` to commonTest.
5. Verification: every existing test passes (they target the surface, not the wiring).

### Phase 2 — 1 week, real mobile wins

1. `InsightRepository` layer with caching.
2. Cross-platform cache via `multiplatform-settings`.
3. Pluggable `List<InsightContributor>` in the aggregator.
4. Swap UI `remember { VM(...) }` → `koinViewModel { }`.
5. Wire `SecureStorage` for session persistence (addresses `docs/gaps/UX_AND_FRONTEND_GAPS.md` #1).
6. Wire `DeepLinkParser` into navigation (addresses `docs/gaps/MOBILE_EDGE_GAPS.md` #1).
7. Split `LeagueStateScreen.kt` and `WeeklyReportTab.kt` into ≤ 200-line files.

### Phase 3 — 2+ weeks, structural

1. Gradle multi-module split per §3.
2. Replace `when (currentScreen)` with Decompose.
3. Theming consolidation (drop compose-unstyled).
4. SQLDelight for historical accuracy tracking (addresses `gaps/ANALYSIS_AND_DATA_GAPS.md` A11).

### Phase 4 — optional, high-investment

- Push notification backend (Cloudflare Worker + FCM/APNs).
- Home-screen widget target on both platforms.
- Monte Carlo lineup simulator replacing greedy `selectLineup`.

---

## 12. Anti-goals (explicitly not doing)

These exist so future you doesn't add them under the mistaken belief they're
"best practice":

- **Full DDD aggregate roots / repositories-everywhere.** Overkill for a
  fantasy app.
- **MVI / Redux.** The `ScreenState` sealed type is enough state management.
- **Rx-style reactive everything.** Flows where they help (live matchup),
  suspend fun where they don't (one-shot request).
- **Premature interface extraction.** Every interface adds a file and a
  seam. Only extract when you have (a) a test that needs a fake or (b) a
  second implementation in flight. `SleeperRepository` qualifies on both.
- **Custom annotation processors, reflection, code-gen.** Costs clarity,
  buys nothing at our scale.
- **Hexagonal architecture diagrams in the app.** The layers in §2 are
  enough.

---

## 13. How to use this document

When you add a new feature, walk through:

1. **Which layer does it belong in?** Data source? Analyzer? Screen?
2. **What's its input type?** Should be a `:core-model` class.
3. **What are its dependencies?** Put them in the constructor; register
   them in the Koin module.
4. **How do you test it?** If you can't fake its dependencies, you have a
   design bug — fix that first.
5. **Does it need caching?** If yes, it lives in the repository layer.
6. **Does it need persistence across sessions?** `SecureStorage` (small) or
   SQLDelight (relational, Phase 3).

If the answer to any of the above requires editing three modules, your
feature is probably in the wrong layer.

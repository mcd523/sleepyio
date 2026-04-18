# Patterns — How to Read and Extend sleepy.io

> Companion to `REFACTOR_PLAN.md`. Pattern-library + cheat-sheet for a new
> mobile developer. Reach for this doc when you're about to add code.

---

## 1. Reading the code — the Start/Sit flow end to end

This is the canonical flow. Everything else is a variation.

```
┌────────────────────────────────────────────────────────────────────┐
│  User types two player IDs in StartSitTab (ui/recommendation/)     │
│    ↓ click "Compare"                                               │
│  StartSitViewModel.compare(a, b, week)       [Presentation]        │
│    ↓ viewModelScope.launch                                         │
│  service.startSit(a, b, week)                [Service facade]      │
│    ↓ one call                                                      │
│  StartSitAnalyzer.analyze(a, b, week)        [Domain — pure-ish]   │
│    ↓ needs data                                                    │
│  InsightAggregator.insightFor([a, b], week)  [Data layer]          │
│    ↓ fan-out                                                       │
│  EspnInjurySource + EspnNewsSource + …       [Data sources]        │
│    ↓ HTTP                                                          │
│  Ktor client → network                                             │
└────────────────────────────────────────────────────────────────────┘
```

**Reading direction.** Top = user; bottom = bytes on the wire. A function
at layer N is only allowed to call layer N or below. You never call
"upward."

**Data shape.** As results flow back up, they're re-typed at each boundary:

- Source → **wire DTO** (e.g. `InjuryReport` in `source/InjurySource.kt`).
- Aggregator → **domain type** (`PlayerInsight` in `insight/model/`).
- Analyzer → **recommendation** (`StartSitRecommendation` in `recommendation/model/`).
- ViewModel → **UI state** (`ScreenState.Content(...)` in `ui/recommendation/`).

Never let a wire DTO leak into a Composable. Never let a Composable see a
`SleeperRoster`. If you catch yourself importing `insight.source.espn.*`
in a ViewModel, you've crossed a layer line.

---

## 2. Adding a new data source

Pattern: four files + one Koin line.

**Step 1 — Write (or reuse) an interface in `insight/source/`.**
```kotlin
interface StadiumCapacitySource {
    suspend fun capacityOf(team: String): Int?
}
```

**Step 2 — Implement it.**
```kotlin
class NflStadiumSource(private val http: HttpClient) : StadiumCapacitySource {
    override suspend fun capacityOf(team: String): Int? = try {
        http.get("...").body<CapacityDto>().value
    } catch (e: Exception) {
        logger.error(e) { "stadium fetch failed" }
        null   // fail soft
    }
}
```

**Step 3 — Fold it into `InsightAggregator`** (Phase 2: via
`List<InsightContributor>`). Until Phase 2, add the constructor param.

**Step 4 — Register in Koin `appModule`.**
```kotlin
single<StadiumCapacitySource> { NflStadiumSource(http = get()) }
```

**Step 5 — Write a test.** Fake the source (not the Ktor engine — the
interface exists for this). Put it in `commonTest/insight/`.

That's the whole pattern. A new source is ~20 LOC.

---

## 3. Adding a new analyzer

**Example: a `TradeAnalyzer` that grades "A+B for C+D."**

1. Define the output in `recommendation/model/`:
   ```kotlin
   data class TradeRecommendation(
       val verdict: Verdict, /* ACCEPT / DECLINE / COUNTER */
       override val score: Int,
       override val confidence: Confidence,
       override val rationale: Rationale,
   ) : Recommendation
   ```
2. Create `recommendation/TradeAnalyzer.kt`. Constructor takes whatever
   pure-domain inputs it needs:
   ```kotlin
   class TradeAnalyzer(private val aggregator: InsightAggregator) {
       suspend fun analyze(
           out: List<String>, in_: List<String>, restOfSeason: Boolean,
       ): TradeRecommendation { /* pure logic on insights */ }
   }
   ```
3. Add a method to `RecommendationService`:
   ```kotlin
   suspend fun trade(out: List<String>, in_: List<String>): TradeRecommendation
   ```
4. Wire in Koin.
5. Test it. You fake `InsightAggregator` (or better, inject an
   `InsightRepository` once Phase 2 lands), not the network.

**Rule of thumb.** If your analyzer needs more than three constructor
params, it's doing too much. Split it.

---

## 4. Adding a new screen

**Example: a "Trade Analyzer" screen.**

1. **UiState**: in `ui/recommendation/`:
   ```kotlin
   data class TradeUiState(
       val outgoing: List<String> = emptyList(),
       val incoming: List<String> = emptyList(),
       val result: ScreenState<TradeRecommendation> = ScreenState.Empty,
   )
   ```
2. **ViewModel**: `TradeViewModel.kt`:
   ```kotlin
   class TradeViewModel(private val service: RecommendationService) : ViewModel() {
       private val _state = MutableStateFlow(TradeUiState())
       val state: StateFlow<TradeUiState> = _state
       fun analyze() { viewModelScope.launch { /* set to Loading, call service, set to Content */ } }
   }
   ```
3. **Screen**: `TradeTab.kt`:
   ```kotlin
   @Composable
   fun TradeTab(vm: TradeViewModel = koinViewModel(), modifier: Modifier = Modifier) {
       val state by vm.state.collectAsStateWithLifecycle()
       // render from state; never call the service directly
   }
   ```
4. **Koin module**: `factory { TradeViewModel(service = get()) }`.
5. **Navigation**: add the destination to whatever nav you're on
   (Phase 1: `NavigationScreen` enum + branch in `App.kt`; Phase 3:
   Decompose `Component`).

**Never**, ever, put `service.trade(...)` inside a Composable body.
ViewModels exist to keep suspend calls out of recomposition.

---

## 5. Handling errors — `Outcome<T>`

Use the sealed type in repositories and upward. Don't use it in sources —
those swallow.

```kotlin
// In a repository
suspend fun weeklyReport(leagueId: Long, rosterId: Long, week: Int): Outcome<WeeklyReport> =
    runCatching { service.weeklyReport(leagueId, rosterId, week) }
        .fold(
            onSuccess = { Outcome.Ok(it) },
            onFailure = { Outcome.Fail(classify(it), it) },
        )

private fun classify(t: Throwable): Outcome.Reason = when (t) {
    is io.ktor.client.plugins.HttpRequestTimeoutException -> Outcome.Reason.NETWORK
    is io.ktor.serialization.JsonConvertException -> Outcome.Reason.PARSE
    else -> Outcome.Reason.UNKNOWN
}
```

```kotlin
// In a ViewModel
viewModelScope.launch {
    _state.value = _state.value.copy(result = ScreenState.Loading)
    _state.value = _state.value.copy(
        result = when (val outcome = repo.weeklyReport(...)) {
            is Outcome.Ok -> ScreenState.Content(outcome.value)
            is Outcome.Fail -> ScreenState.Failed(outcome.reason.label, retry = { analyze() })
            Outcome.Loading -> ScreenState.Loading // shouldn't happen for one-shot
        }
    )
}
```

**Do** surface errors with `Reason` — user-actionable ("No internet" vs.
"Something went wrong"). **Don't** surface raw exception messages to the
UI — they're usually Kotlin type names.

---

## 6. Testing cheat-sheet

What's worth testing vs. not.

| Thing | Worth testing? | How |
|---|---|---|
| Analyzers (`StartSitAnalyzer`, `WaiverWireAnalyzer`, `WeeklyReportBuilder.selectLineup`) | **Yes** — pure logic | `commonTest` + fake aggregator |
| `Confidence.fromScore` and other math | **Yes** | unit test, no deps |
| `Rationale.positive/.negative` filters | **Yes** | unit test |
| `DeepLinkParser` | **Yes** | unit test |
| `InsightAggregator` (network fan-out) | **Maybe** — only with a `HttpClient(MockEngine)` injected; low ROI | integration-lite |
| Compose UI | **Only for components with branching logic** (matchup bar filling based on grade) | Compose test in androidInstrumentedTest (Phase 3) |
| Sources with real HTTP | **No** — mock the endpoint or don't test |
| Simple data classes | **No** |

**Use `runTest` from `kotlinx-coroutines-test`**. Time-travel is free:
`advanceTimeBy(31.minutes)` to test that your cache TTL expires.

**Fakes over mocks.** In KMP, mock frameworks (MockK/Mockito) don't work
on all targets. Hand-roll a fake that implements the interface. It's 10
lines and it works everywhere.

Example fake:
```kotlin
class FakeInjurySource(private val reports: List<InjuryReport>) : InjurySource {
    override suspend fun fetchInjuries(week: Int) = reports
}
```

---

## 7. Common mistakes new mobile devs make

Each of these is already in the codebase or has come close. Call-outs:

### 7.1 `LaunchedEffect(key1 = Unit)` — runs once, never again
```kotlin
// BAD: never refreshes when userId changes
LaunchedEffect(Unit) { vm.load(userId) }

// GOOD
LaunchedEffect(userId) { vm.load(userId) }
```

### 7.2 `by remember { mutableStateOf(...) }` across navigation
When the user navigates away and back, you lose state. Use a ViewModel for
anything that has to survive nav. `remember` is for ephemeral UI state
(expanded? yes/no).

### 7.3 `GlobalScope.launch`
Never. Always a ViewModel scope or `coroutineScope { }`. `GlobalScope` is
leaked coroutines.

### 7.4 Blocking dispatcher choice
On JVM, `withContext(Dispatchers.IO) { }` for network. On other targets,
`Dispatchers.Default` is what you've got. Ktor handles its own dispatcher
— don't wrap `client.get(...)` in `withContext`. Read the Ktor docs
*once*, trust them.

### 7.5 Singleton `data object` for stateful services
The current `SleeperClient` is a `data object`. It holds an
`HttpClient`. If you call `SleeperClient` before its module-initializer
runs, Kotlin runs it for you — fine. But you can't substitute it in tests
without the QA gap we already hit. Phase 1 extracts the interface.

### 7.6 Mixing `MaterialTheme.colorScheme` and `Theme[colors][...]`
Pick one. We're on Material3 Phase 3. Until then, if a screen reads from
the custom theme, keep it there; don't add `MaterialTheme.colorScheme.X`
calls in the same file.

### 7.7 Swallowing an exception in a ViewModel
```kotlin
// BAD
try { service.load(...) } catch (e: Exception) { /* nothing */ }

// GOOD
try { service.load(...) } catch (e: Exception) {
    _state.value = _state.value.copy(result = ScreenState.Failed(e.userMessage()))
}
```
A UI that silently fails is worse than one that errors.

### 7.8 Non-thread-safe `MutableStateFlow` updates
```kotlin
// BAD: race if called from multiple coroutines
_state.value = _state.value.copy(loading = true)

// GOOD
_state.update { it.copy(loading = true) }
```

### 7.9 Huge screens
If a `*Screen.kt` or `*Tab.kt` passes 300 lines, split it. Common splits:
header, content, footer. Each is `@Composable internal fun` in the same
file or a `-Content.kt` companion.

### 7.10 Inventing abstractions before you have two callers
The YAGNI corollary. `TradeService` shouldn't be an interface until a
second `TradeAnalyzer` implementation is actually in flight. Phase-1's
`SleeperRepository` is the **exception**: its second implementation is
the test fake, and that's a good reason.

---

## 8. When you're stuck

- If you can't test something, the *dependency shape is wrong*. Extract an
  interface, inject it, fake it.
- If a file is scary to edit, it's doing too much. Split by **concern**
  (fetch vs. analyse vs. render), not by line count.
- If a decision feels like it could go either way, pick the simpler one
  and move on. The layer you commit to will tell you when it's wrong
  — it'll rub against reality.
- Read `REFACTOR_PLAN.md` §1. Every answer is in the five principles.

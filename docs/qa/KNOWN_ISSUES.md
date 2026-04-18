# QA Known Issues

Running list of testability gaps discovered while writing unit tests for the
recommendation platform. Every entry is "what we couldn't cleanly test" +
"the smallest refactor that would unblock it".

---

## 1. `WaiverWireAnalyzer` is coupled to `SleeperClient` (a `data object`)

**File:** `composeApp/src/commonMain/kotlin/com/sleepyio/sleepyio/recommendation/WaiverWireAnalyzer.kt`

**Symptom:** The analyzer's only public entry point (`findTargets`) hits
`SleeperClient.getRostersInLeague` and `SleeperClient.getTrendingPlayers`
unconditionally. Because `SleeperClient` is a `data object` (not an
interface, not even an `open class`), the constructor default
`sleeper: SleeperClient = SleeperClient` has no seam a test double can slot
into — there is no subtype relationship we can satisfy.

**Downstream impact:** The FAAB-tier logic (framework 3.2) lives in
`private fun faabPctFor(priority: Int, week: Int): Int` and cannot be tested
without constructing the analyzer AND making it reach Sleeper's live HTTP
API. QA therefore ships only a smoke test + a TODO comment in
`WaiverWireAnalyzerTest.kt` enumerating the tier assertions we want.

**Recommended refactor (smallest viable change):** extract a narrow
interface, e.g.

```kotlin
interface SleeperRostersProvider {
    suspend fun getRostersInLeague(leagueId: Long): List<SleeperRoster>
    suspend fun getTrendingPlayers(sport: String, type: String, lookbackHours: Int, limit: Int): List<TrendingPlayer>
}
```

and have `SleeperClient` implement it as a `data object`. The analyzer's
constructor becomes `sleeper: SleeperRostersProvider = SleeperClient` and a
test double drops in painlessly.

---

## 2. `InsightAggregator` made `open` to permit a test subclass

**File:** `composeApp/src/commonMain/kotlin/com/sleepyio/sleepyio/insight/InsightAggregator.kt`

**Change applied:** added `open` to the class declaration and to both
`insightFor` overloads. No behaviour change; no new dependency; no new
public surface.

**Why it was needed:** `StartSitAnalyzer` takes a concrete `InsightAggregator`
parameter, not an interface. The real aggregator transitively calls
`SleeperClient.getAllPlayers("nfl")`, which hits the network. With the
class `final`, QA could not produce an aggregator test double without
either refactoring the analyzer contract or hitting the live API.

**Longer-term fix (preferred):** promote `InsightAggregator` to an
interface with the current class renamed to `DefaultInsightAggregator`.
That also lets the service layer swap aggregator strategies
(e.g. a caching aggregator) without surgery.

---

## 3. `WeeklyReportBuilder` end-to-end is untestable in common code

**File:** `composeApp/src/commonMain/kotlin/com/sleepyio/sleepyio/recommendation/WeeklyReportBuilder.kt`

**Status:** the pure `selectLineup(...)` helper IS testable (covered by
`WeeklyReportBuilderTest.kt`). The full `build(...)` suspend function depends
on `SleeperClient` and cannot be unit-tested until issue #1 is resolved.

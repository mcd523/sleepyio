# Social Intelligence Platform Design

## Problem

sleepy.io currently provides a linear, read-only view of fantasy football leagues: login → pick league → view matchups. Users have no way to study their leaguemates' behavior, identify patterns in their moves, or prepare intelligence for upcoming matchups. The app stops at showing what's happening now, when the real competitive advantage comes from understanding what your opponents tend to do.

## Solution

Transform sleepy.io into a league intelligence platform where each leaguemate gets a profile built from their Sleeper activity data. Users can study transaction patterns, draft tendencies, scoring trends, and roster composition to gain matchup advantages. A plugin architecture makes it trivial to add new insight modules over time.

No custom backend is needed — all intelligence is derived from Sleeper's existing public API, with analysis computed client-side.

## Architecture Overview

### Navigation: Linear → Hub

The current 3-state flow (`USER_LOGIN → LEAGUE_LIST → LEAGUE_STATE`) becomes:

```
USER_LOGIN → LEAGUE_LIST → LEAGUE_HUB
                              ├── Matchups tab     (existing LeagueStateScreen)
                              ├── Leaguemates tab  (new)
                              ├── Activity tab     (new)
                              └── Intel tab        (new)
                                    └── Detail screens (pushed on back-stack)
                                        ├── LeaguemateProfile
                                        └── PlayerProfile (existing)
```

**Navigation pattern**: Material3 `NavigationBar` at bottom for screens < 800px, `NavigationRail` on the left for screens ≥ 800px. Uses existing responsive breakpoints from `getResponsiveSizes()` in `LeagueStateScreen.kt`.

**State management**: Existing Compose `mutableStateOf` + `LaunchedEffect` pattern. No ViewModel library needed. A `LeagueShellScreen` composable manages the active tab and a detail back-stack (`List<DetailScreen>`).

### Insight Module Plugin System

Each intelligence module implements a typed interface:

```kotlin
interface InsightModule<T> {
    val id: String
    val displayName: String

    suspend fun analyze(
        targetUserId: String,
        myUserId: String,
        sharedLeagueIds: List<Long>
    ): T?

    @Composable
    fun Render(data: T, modifier: Modifier)
}
```

Modules are registered in a central `InsightRegistry` object. Adding a new module = one new file + one `register()` call.

The `LeaguemateProfileScreen` iterates over all registered modules, calling `analyze()` independently per module (each shows its own loading state), then rendering each module's card in a `LazyColumn`.

### Data Layer: LeaguemateRepository

A session-scoped class that sits between `SleeperClient` and the UI, aggregating and caching cross-league data:

```kotlin
class LeaguemateRepository(
    private val myUserId: String
) {
    suspend fun getLeaguemates(leagueId: Long): List<SleeperUser>
    suspend fun getSharedLeagues(targetUserId: String): List<SleeperLeague>
    suspend fun getLeaguemateTransactions(leagueId: Long, userId: String): List<SleeperTransaction>
    suspend fun getLeaguemateDraftPicks(leagueId: Long, userId: String): List<SleeperPick>
    suspend fun getLeaguemateRoster(leagueId: Long, userId: String): SleeperRoster?
    suspend fun getCurrentMatchupOpponent(leagueId: Long): SleeperUser?
    suspend fun getHeadToHeadHistory(leagueId: Long, opponentUserId: String): List<MatchupResult>
}
```

- Uses `SleeperClient` methods that already exist (no new API endpoints)
- Shared-leagues computation cached in-memory per session (expensive: requires `getLeaguesForUser` for both users)
- Transaction-to-user mapping done via `getRostersInLeague` → `ownerId`
- Instantiated via `remember(leagueId, myUserId)` in `LeagueShellScreen`

## Screens

### 1. LeagueShellScreen (hub)

The container for all league content. Manages:
- `NavigationBar`/`NavigationRail` with 4 tabs: Matchups, Leaguemates, Activity, Intel
- Active tab state
- Detail back-stack for profile drill-downs
- `LeaguemateRepository` instance scoped to current league + user

**File**: `ui/shell/LeagueShellScreen.kt`

### 2. LeaguemateListScreen

Shows all users in the current league as cards with:
- Avatar, display name
- Win-loss record (from `getLeagueStandings`)
- Number of shared leagues
- Badge if they're your current-week opponent

Tapping a row pushes `LeaguemateProfileScreen` onto the detail back-stack.

**File**: `ui/leaguemate/LeaguemateListScreen.kt`

### 3. LeaguemateProfileScreen

The intelligence dossier. Structure:
- **Header**: Avatar, name, shared league count, aggregate record, total points, head-to-head vs you
- **Body**: `LazyColumn` of insight module cards, each loading independently

Each card renders via the module's `Render()` function. Loading/error states handled by a generic `InsightCard` wrapper composable.

**File**: `ui/leaguemate/LeaguemateProfileScreen.kt`

### 4. ActivityFeedScreen

Chronological feed of all league transactions:
- Color-coded by type: trades (orange), waivers (teal), free agent (green), drops (red)
- Grouped by week with divider labels
- Filter chips at top: All, Trades, Waivers, Free Agent, Drops
- Trade events show players exchanged in a split received/sent layout
- Data source: `getAllTransactionsInSeason()` enriched with user/player names

**File**: `ui/activity/ActivityFeedScreen.kt`

### 5. MatchupIntelScreen

Focused briefing for the current week's opponent:
- Auto-detects opponent from matchup data + roster ownership
- **Header**: Opponent identity, head-to-head record
- **Scoring comparison**: Your avg vs their avg points/week
- **Vulnerability alerts**: Injured starters, bye week gaps, recent drops
- **Recent moves**: Last N transactions by this user
- **Scoring trend**: Last 5 weeks mini bar chart with trend direction

**File**: `ui/matchup/MatchupIntelScreen.kt`

## Initial Insight Modules (4)

### TransactionActivityModule
- **Analyzes**: Trades, waivers, FA pickups, drops across shared leagues
- **Shows**: Add/drop/trade counts, most active day of week, last move timestamp
- **API**: `getTransactions()`, `getAllTransactionsInSeason()`
- **File**: `intel/modules/TransactionActivityModule.kt`

### DraftTendencyModule
- **Analyzes**: Draft picks across shared leagues' drafts
- **Shows**: Position distribution bar, early-round bias label (e.g., "RB-heavy"), repeatedly targeted players
- **API**: `getDraftsForLeague()`, `getDraftPicks()`
- **File**: `intel/modules/DraftTendencyModule.kt`

### ScoringTrendModule
- **Analyzes**: Weekly matchup points across the season
- **Shows**: Bar chart of weekly scores, average points/week, consistency rating, win/loss streak
- **API**: `getMatchupsInLeague()` (iterated per week), `getLeagueStandings()`
- **File**: `intel/modules/ScoringTrendModule.kt`

### RosterCompositionModule
- **Analyzes**: Current roster player positions, injury statuses, bye weeks
- **Shows**: Position allocation chips (QB x2, RB x5, etc.), bye week alerts, injury exposure count
- **API**: `getRostersInLeague()`, `getAllPlayers()` (cached)
- **File**: `intel/modules/RosterCompositionModule.kt`

## Package Structure

```
com.sleepyio.sleepyio/
  App.kt                          # Modified: updated nav to use LeagueShellScreen
  Theme.kt                        # Unchanged

  client/                          # Unchanged
    SleeperClient.kt
    model/...

  cache/                           # Unchanged
    SleeperCache.kt

  intel/                           # NEW
    InsightModule.kt               # Plugin interface
    InsightRegistry.kt             # Central registry
    LeaguemateRepository.kt        # Data aggregation layer
    model/
      LeaguemateProfile.kt         # Aggregated profile data
      ActivityEvent.kt             # Unified transaction event
      MatchupResult.kt             # Head-to-head result
    modules/
      TransactionActivityModule.kt
      DraftTendencyModule.kt
      ScoringTrendModule.kt
      RosterCompositionModule.kt

  ui/
    shell/
      LeagueShellScreen.kt        # NEW: hub with bottom nav/rail
    league/
      LeagueStateScreen.kt        # Existing, embedded as Matchups tab
    leaguemate/
      LeaguemateListScreen.kt     # NEW
      LeaguemateProfileScreen.kt  # NEW
    activity/
      ActivityFeedScreen.kt       # NEW
    matchup/
      MatchupIntelScreen.kt       # NEW
    components/
      InsightCard.kt              # NEW: generic loading/error wrapper
    player/
      PlayerProfileScreen.kt      # Existing, unchanged
```

## Critical Files to Modify

| File | Change |
|------|--------|
| `App.kt` | Replace `LEAGUE_STATE` screen with `LeagueShellScreen`. Add `LEAGUE_SHELL` to nav enum. |
| `LeagueStateScreen.kt` | No structural changes — embedded as the Matchups tab content. May need to accept league/user as parameters instead of managing its own state. |
| `Theme.kt` | No changes needed — existing tokens cover the new screens. |
| `SleeperClient.kt` | No changes — all needed endpoints already exist. |

## Existing Code to Reuse

- **`SleeperClient.getAllTransactionsInSeason()`** — fetches all weeks' transactions, used by ActivityFeedScreen
- **`SleeperClient.getLeagueStandings()`** — GraphQL standings query, used for win/loss records
- **`SleeperClient.getDraftPicks()`** — draft pick data for DraftTendencyModule
- **`SleeperClient.getMatchupsInLeague()`** — per-week matchup data for ScoringTrendModule
- **`SleeperClient.getUsersInLeague()`** — leaguemate list data
- **`SleeperClient.getRostersInLeague()`** — roster-to-user mapping
- **`SleeperClient.getLeaguesForUser()`** — shared league computation
- **`SleeperCache.getPlayer()`** — cached player lookups for enrichment
- **`getResponsiveSizes()`** in `LeagueStateScreen.kt` — responsive breakpoint logic (extract to shared utility)
- **`SleeperTheme`**, **`SleeperSpacing`**, **`SleeperType`** — all design tokens from `Theme.kt`

## Phased Implementation

### Phase 1: Navigation Shell
- Create `LeagueShellScreen` with `NavigationBar`/`NavigationRail`
- Move `LeagueStateScreen` into Matchups tab
- Update `App.kt` nav state machine
- Placeholder composables for 3 new tabs

### Phase 2: Data Layer + Leaguemate List
- Create `LeaguemateRepository`
- Create `LeaguemateListScreen` with user cards, records, shared league counts
- Wire into Leaguemates tab

### Phase 3: Activity Feed
- Create `ActivityEvent` model
- Create `ActivityFeedScreen` with transaction rendering and filters
- Wire into Activity tab

### Phase 4: Plugin System + First Modules
- Create `InsightModule<T>` interface and `InsightRegistry`
- Create `InsightCard` wrapper composable
- Implement `RosterCompositionModule` and `TransactionActivityModule`

### Phase 5: Leaguemate Profile
- Create `LeaguemateProfileScreen` consuming all registered modules
- Wire navigation from LeaguemateListScreen
- Add detail back-stack to shell

### Phase 6: Remaining Modules
- Implement `DraftTendencyModule` and `ScoringTrendModule`

### Phase 7: Matchup Intel
- Create `MatchupIntelScreen`
- Auto-detect current opponent
- Compose vulnerability alerts, recent moves, scoring trend
- Wire into Intel tab

## Verification

1. **Build**: `./gradlew :composeApp:run` — desktop app launches with new navigation
2. **Navigation**: Login → select league → hub appears with 4 tabs, bottom nav bar visible
3. **Matchups tab**: Existing matchup view renders identically to current behavior
4. **Leaguemates tab**: Shows all league members with records and shared league counts
5. **Activity tab**: Displays transactions grouped by week, filters work
6. **Intel tab**: Detects current opponent, shows vulnerability alerts and recent moves
7. **Profile drill-down**: Tap a leaguemate → profile opens with all 4 insight cards loading and rendering
8. **Responsive**: Resize window past 800px → bottom nav converts to side rail
9. **Tests**: `./gradlew :composeApp:allTests` — existing tests still pass

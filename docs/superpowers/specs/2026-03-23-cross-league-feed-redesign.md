# Cross-League Feed & Navigation Redesign

## Problem

The current flow requires selecting a league before seeing any content: `Login → League List → League Shell`. This forces users to pick a single league context before viewing activity, losing the cross-league social experience that makes fantasy football engaging across multiple leagues.

## Solution

Replace the league-first flow with a social-first home screen. After login, users land on a cross-league activity feed that aggregates events from all their leagues. Leagues become a drill-down destination, not a prerequisite.

## Navigation State Machine

### Current
```
USER_LOGIN → LEAGUE_LIST → LEAGUE_STATE
```

### New
```
USER_LOGIN → HOME_SHELL (2 tabs)
               ├─ Feed (default) — cross-league activity stream
               └─ My Leagues — league list (reuses LeagueTable)
             → LEAGUE_DETAIL (push navigation, full-screen replace with back arrow)
               ├─ Matchups
               ├─ Leaguemates
               ├─ Activity
               └─ Intel
```

- `App.kt` enum: `USER_LOGIN`, `HOME_SHELL`, `LEAGUE_DETAIL`
- Entering a league replaces the home shell entirely; back arrow returns to home
- Feed tab is the default landing after login
- Responsive: `NavigationBar` (mobile <800px), `NavigationRail` (desktop >=800px)

## Cross-League Feed

### Content
Aggregates all activity across all user leagues into a single chronological feed:
- **Transactions**: trades, waiver claims, free agent adds/drops (reuses existing `ActivityEvent` parsing)
- **Matchup results**: W/L outcomes, scores, close-game highlights
- **Draft events**: picks as they happen during drafts
- **Commissioner actions**: rule changes, deadline extensions, manual adjustments

### UI
- Filter chips: All | Trades | Waivers | Matchups | Drafts | Commissioner
- Events grouped by NFL week, most recent first
- Each event tagged with source league name (tappable to enter that league)
- Color-coded by type: orange=trades, teal=waivers, indigo=matchups, green=free agent, purple=commissioner
- Trade cards show RECEIVED/SENT split layout (same as existing ActivityFeedScreen)
- Matchup cards show W/L, your score vs opponent score, close-game badge

### Data Model
```kotlin
sealed class FeedEvent {
    abstract val leagueId: String
    abstract val leagueName: String
    abstract val timestamp: Long
    abstract val week: Int

    data class Transaction(/* wraps existing ActivityEvent + league context */)
    data class MatchupResult(/* your score, opponent, W/L, margin */)
    data class DraftPick(/* player, round, pick, drafter */)
    data class CommissionerAction(/* description, type */)
}
```

### Loading Strategy: Progressive
1. On login, fetch all leagues for user (already done)
2. Fire parallel coroutines per league to fetch transactions + matchups
3. As each league completes, merge results into shared feed state
4. UI shows skeleton → partial results → full feed
5. No caching beyond existing player cache — fresh data each session

## File Changes

| File | Change |
|------|--------|
| `App.kt` | New 3-state enum (`USER_LOGIN`, `HOME_SHELL`, `LEAGUE_DETAIL`). After login, fetch all leagues then navigate to `HOME_SHELL`. Store `allLeagues` in state. |
| **NEW** `ui/home/HomeShellScreen.kt` | Two-tab shell (Feed/Leagues). Same responsive NavigationBar/Rail pattern as `LeagueShellScreen`. Accepts `onLeagueSelected` and `onBack` callbacks. |
| **NEW** `ui/home/CrossLeagueFeedScreen.kt` | Aggregated feed composable. Parallel league fetches, event merging, filter chips, week grouping. Reuses rendering patterns from `ActivityFeedScreen`. |
| **NEW** `ui/home/FeedEvent.kt` | Sealed class for unified feed events (Transaction, MatchupResult, DraftPick, CommissionerAction). |
| `ui/shell/LeagueShellScreen.kt` | Add `onBack` callback parameter, render back arrow in top bar. |
| `LeagueTable.kt` | Ensure it accepts `onLeagueSelected` callback (may already). |
| `client/SleeperClient.kt` | Add/expose methods for fetching matchup results and transactions at the granularity needed for the feed. |

## What Doesn't Change

All existing league-scoped screens remain unchanged:
- `LeagueStateScreen` (matchups)
- `ActivityFeedScreen` (league-scoped activity)
- `LeaguemateListScreen`, `LeaguemateProfileScreen`
- `IntelOverviewScreen`
- `PlayerProfileScreen`
- `LeagueBfsView`, `TeamsView`
- All intel modules, cache layer, SleeperClient core

## Verification

1. **Login flow**: Enter username → lands on Feed tab with cross-league activity loading progressively
2. **Feed filtering**: Tap filter chips, verify events filter correctly
3. **League drill-down**: Tap league badge on feed item → enters that league's shell with back arrow
4. **Leagues tab**: Tap My Leagues tab → see league list → tap a league → enters league shell
5. **Back navigation**: Back arrow from league shell → returns to Home Shell on the tab you were on
6. **Responsive**: Verify NavigationBar (mobile) and NavigationRail (desktop) both work
7. **Empty states**: User with no leagues, league with no transactions
8. **Progressive loading**: Verify skeleton → partial → full feed rendering with multiple leagues

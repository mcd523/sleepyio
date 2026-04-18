# UX & Frontend Gaps — Decision Surface Audit

Focus: **what does an active Sunday-morning manager need that the app
doesn't yet deliver?** Cited against the actual composables.

## 1. Critical user journeys

| Journey | State | Missing |
|---|---|---|
| "Sunday AM — who do I start?" | PRESENT | 3 taps (Login → League → Advisor). Could be 1 tap on re-open; `SecureStorage` is built but *not used* to remember last league/roster. |
| "Player X just got hurt pre-game — is my backup in?" | MISSING | No live refresh, no push notifications, no "last-opened diff." `NotificationCenter` is built, not integrated. |
| "Wednesday waivers" | PRESENT | `WaiversTab` covers this. But no FAAB-budget awareness — we suggest % but don't know the user's remaining budget. |
| "Opponent benched their RB1 — does that change my strategy?" | MISSING | No opponent-scouting surface. `WeeklyReportBuilder` could run for both rosters — not surfaced. |
| "Should I trade A+B for C+D?" | MISSING | No trade analyzer screen at all. |
| "Bye-week gauntlet this Sunday" | PARTIAL | Start/Sit handles one pair. No multi-position or roster-wide view. |
| "Who do I drop to make room for this waiver add?" | MISSING | `WaiverTarget.dropCandidates` is always empty (see `BACKEND_GAPS.md`); even if populated, the tab doesn't render a drop picker. |
| "Explain why you picked X over Y" | PARTIAL | `Rationale.factors` is rendered as chips but no "learn more" tooltip that teaches the framework. |

## 2. Screen-level audit

### `WeeklyReportTab.kt`
- **Great**: Sections map cleanly to the design-spec wireframe. Risky starters chip row is a strong signal.
- **Missing**:
  - Projection *range* (floor-ceil) for the lineup total — the design spec called for it; today we render `lineup.score` + `rationale.summary` only. (`LineupRecommendation` has no aggregate `ProjectionRange` — this is a domain-model gap too.)
  - No "your opponent projects N" line — opponent scouting is the single biggest missing contextual signal.
  - Start/Sit decisions embedded here show verdict but not projection deltas.
- **Friction**: No way to jump from a risky starter to that player's full `PlayerInsight` — dead end.

### `StartSitTab.kt`
- **Great**: Self-contained, deterministic.
- **Missing**: Player IDs entered via `TextField` (TODO comment exists). No searchable picker reading `SleeperCache`. No recent-comparison history.
- **Content**: Side-by-side projection stats (floor/ceil) are promised in the design doc but not rendered because `StartSitRecommendation` doesn't carry per-player projections.
- **Friction**: Raw player IDs are unusable for a casual user — this tab is effectively broken until a picker lands.

### `WaiversTab.kt`
- **Great**: Filter chip row for positions is there.
- **Missing**: FAAB budget context (we suggest 24% — of what?). No drop picker (see journey #7). No "projected owned %" signal for urgency.
- **Content**: Rationale chips render well.

### `PlayerInsightTab.kt`
- **Great**: Projection pill + matchup bar + usage/injury/news sections match the design doc.
- **Missing**:
  - Trend arrows — "snap % 74% (↑5pp vs season avg)" adds context vs. just "74%."
  - Opponent field is always blank — `InsightAggregator` doesn't populate `opponent` (see `BACKEND_GAPS.md`).
  - News items have `FantasyImpact` but no visual affordance beyond color — design doc requires color + glyph.
- **Friction**: Tab takes a raw player ID like Start/Sit.

## 3. Navigation + shell gaps

- **No persistence.** Opening the app tomorrow starts from login. The Sleeper username is in a `remember` inside `UserLoginScreen` with a hardcoded default `"thehippokid"`. `SecureStorage` (KMP capability) exists but is unused.
- **No back-stack.** `when (currentScreen) { ... }` in `App.kt`. Hardware back on Android falls off the app from the Advisor. Deep links land on Advisor but have no way to reach Player Insight directly.
- **Deep links parsed, not routed.** `MainActivity.pendingDeepLink` holds a parsed `DeepLink` but nothing in `App.kt` reads it. `sleepyio://players/<id>?week=N` is a dead feature.
- **Advisor is entered from League State.** Makes it a second-class citizen. Most apps lead with "today's advice" — add an "Advisor" card on the LeagueList for the first league, or a dedicated tab.
- **Week selector** is on `LeagueAdvisorScreen` but not on Player Insight or Start/Sit — every tab should share the selector.

## 4. Content & density gaps

- `rationale.summary` is a compact one-liner — it's only rendered in some places. Add it to every card that shows a `Recommendation`.
- Confidence rendered as `HIGH/MED/LOW` pill is good. Also showing the raw `score` 0–100 alongside is information overload; pick one per surface.
- **No explain-it tooltip.** Tap "Matchup: GOOD" and nothing happens. Should show: "Based on opponent's rank 24 vs WR, trailing 6 weeks."
- **Accuracy scoreboard missing.** "We went 22–11 on start/sit last year" builds trust. Requires phase-2 persistence.

## 5. Accessibility audit

- `FactorChipRow`, `ConfidenceBadge`, `InjuryTag`, `MatchupGradeBar` — check for `Modifier.semantics { contentDescription = ... }` on non-text content. Likely missing on the matchup bar (segments alone don't speak to a screen reader).
- `"58 pts"` as plain text reads badly on TalkBack / VoiceOver. Consider `Modifier.semantics { text = AnnotatedString("58 fantasy points") }`.
- Color-only: confidence badges currently use background color + text; glyph is not mandatory. Design spec said every color signal must pair with a glyph. Add a ▲ / • / ▼ per tier.
- Minimum tap targets: verify every `Button` and `IconButton` is ≥ 48.dp. Most Material3 defaults meet this; custom cards (e.g. clickable matchup cards) may not.

## 6. Edge-giving UX features that don't exist

| Feature | What it is | E | F | E/F | Needs |
|---|---|---|---|---|---|
| **"Since you last opened" diff feed** | "3 roster players have news since Wed 4pm (2 neg, 1 pos)" | 4 | 2 | 2.0 | Persistence, news timestamps (already have) |
| **Opponent preview card** | Your Week N opponent's projected lineup total | 4 | 2 | 2.0 | Second `WeeklyReportBuilder` call |
| **Shareable decision card** | One-tap "Here's why I'm starting Robinson" for league chat | 3 | 2 | 1.5 | Platform share intent + a PNG composable export |
| **Deadline bar** | "Thu-night lock in 3h 42m · FAAB closes 2h after SNF" | 4 | 1 | 4.0 | Sleeper `SleeperState.week` + schedule lookup |
| **What-if simulator** | Swap A for B, projected total re-rolls | 3 | 2 | 1.5 | Re-run `selectLineup` with tweaked starter set |
| **League-relative context** | "WR18 overall · borderline flex in your PPR league" | 4 | 2 | 2.0 | A2 in `ANALYSIS_AND_DATA_GAPS.md` |
| **"Teach the framework" tooltips** | Tap any factor chip → mini explainer | 3 | 1 | 3.0 | Static copy + existing `Factor.evidence` |
| **Drop picker flow** | Waiver tab: pick an add → pre-fills the best drop | 4 | 1 | 4.0 | Backend gap A1 first |
| **Player search in Start/Sit + Player Insight** | Replace raw-ID `TextField` | 5 | 2 | 2.5 | Read `SleeperCache` across platforms first |
| **Persisted session** | Remember username + last league/roster | 4 | 1 | 4.0 | Wire `SecureStorage` into `App.kt` |

## 7. Reusable components to extract

Scanning `ui/recommendation/*Tab.kt` for duplication:

- `PlayerRow` — each tab renders player name + position + team + injury differently. Extract `PlayerIdentityCard(playerId, insight?, modifier)`.
- `ScoreRationaleCard` — Weekly/Start-Sit/Waiver all render Confidence + Score + Rationale. One component with optional side slot.
- `NewsBlurbList` — only Player Insight renders it; when it shows up on Weekly Report ("since you last opened"), both will share one.

## 8. Ranked punch list — top 10

| Rank | Gap | E | F | E/F | Notes |
|---|---|---|---|---|---|
| 1 | Wire `SecureStorage` for last-opened league/roster | 4 | 1 | 4.0 | Saves 2 taps every session |
| 2 | Drop picker on Waivers tab | 4 | 1 | 4.0 | Gated on backend A1 |
| 3 | Deadline bar | 4 | 1 | 4.0 | Already-fetched state |
| 4 | Searchable player picker | 5 | 2 | 2.5 | `SleeperCache` cross-platform blocker |
| 5 | Opponent preview card | 4 | 2 | 2.0 | Second report build |
| 6 | Since-you-last-opened diff | 4 | 2 | 2.0 | Requires persistence |
| 7 | Wire `DeepLinkParser` into navigation | 3 | 1 | 3.0 | Already built, unused |
| 8 | Glyph + color for every signal | 3 | 1 | 3.0 | Accessibility win |
| 9 | Tooltips teaching the framework | 3 | 1 | 3.0 | Engagement + trust |
| 10 | Week selector on every advisor screen | 2 | 1 | 2.0 | Consistency |

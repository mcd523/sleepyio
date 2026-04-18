# Analysis & Data Gaps — Edge Opportunities

Audit of what the platform actually computes today vs. what a serious fantasy
manager needs to win a league. Every gap is scored on **E** (edge 0–5) and
**F** (effort 0–5). Sort by **E/F** for what to build next.

## 1. Current feature inventory (honest)

- **Start/Sit** (`recommendation/StartSitAnalyzer.kt`) — head-to-head comparison on projection, matchup, health, usage, environment. Score formula follows `ANALYSIS_FRAMEWORK §4.1`. OUT/IR short-circuits work. **Feels right; its output quality is bottlenecked on the data quality, not the logic.**
- **Waiver Wire** (`recommendation/WaiverWireAnalyzer.kt`) — pulls Sleeper trending adds, excludes league-owned, scores on snap share / target share / red zone / matchup / projection. FAAB tiers in 4 coarse buckets. `dropCandidates` is **always `emptyList()`** (line 64) — big unfinished hole.
- **Weekly Report** (`recommendation/WeeklyReportBuilder.kt`) — greedy slot fill (QB, RB, RB, WR, WR, TE, FLEX, DEF, K). Picks three closest start/sit calls per position. Risky-starter detector fires on designation, NIGHTMARE matchup, or wind ≥ 20 mph.
- **Player Insight** (`recommendation/PlayerInsightComposer.kt`) — thin pass-through over `InsightAggregator`. The fused `PlayerInsight` contains everything the UI renders.

### Stubs, nulls, and always-empties visible in code

| Where | Current value | Consequence |
|---|---|---|
| `EspnProjectionsSource` | deterministic projection = `(usage.snapPct × 10) + matchupGrade.bonus` | Projection deltas ≈ 0–5 pts between players of similar usage. Start/Sit factor weight 25 effectively flattens. |
| `EspnDvpSource` | every team ranked `16` (NEUTRAL) for every position | `MatchupGrade` lands on NEUTRAL almost always → matchup factor contributes ~0 to score. |
| `StubVegasOddsSource` | fixed `impliedTeamTotal = 22.0`, `spread = 0.0` | Game script signal is dead. `environmentScore` returns 22 − 0 for everyone. |
| `StubUsageSource` | fixed snap 0.55, target 0.18, carry 0.20, RZ 2 | Waiver breakout thresholds (0.55 snap, 0.18 target, 3 RZ) are *barely* crossed — everyone looks medium-interesting. |
| `PlayerInsight.opponent` | `null` in `InsightAggregator` | UI "vs. OPP" label is blank; opponent-lineup scouting impossible. |
| `WaiverTarget.dropCandidates` | `emptyList()` | User has to pick a drop manually every time. |
| Fixed-roster only | `selectLineup` hardcodes `QB, RB, RB, WR, WR, TE, FLEX, DEF, K` | 2-QB / SuperFlex / TE-premium leagues get broken lineups. |
| `news` not scored | recent news fetched into `PlayerInsight.recentNews` | Rendered in UI but never feeds `Factor` weights → breaking negative news doesn't move the score. |

## 2. Data gaps — replace the stubs

| # | Stub today | Real replacement | E | F | Notes |
|---|---|---|---|---|---|
| D1 | DvP (everyone NEUTRAL) | `nflverse-data` PBP aggregation — compute FPA/pos over trailing 6 weeks | 5 | 2 | Single CSV download, group-by in Kotlin. Unlocks framework's 15% matchup weight. |
| D2 | Usage (fixed fixture) | `nflverse-data` snap_counts + player_stats CSV | 5 | 2 | Unlocks framework's 25% opportunity weight + waiver's 55-pt snap tier. |
| D3 | Projections (stub) | (a) Derive from real DvP × real usage × O-line (free, good); (b) SportsDataIO ~$19/mo | 4 | 2 | Free path gets 80% of paid-feed quality. |
| D4 | Opponent (null) | ESPN scoreboard endpoint → team vs team for week | 3 | 1 | Trivial. Lights up UI labels + enables opponent scouting. |
| D5 | Vegas (stub) | The Odds API free tier, 100 req/hr | 3 | 1 | Key needed. Restores 10% game-script weight. |
| D6 | O-line health (not captured) | ESPN team injuries + position filter (OL) | 3 | 2 | Framework says 7% weight; we compute 0% today. |
| D7 | Practice-report trend (missing) | nflverse `injuries.csv` includes `practice_status` | 3 | 1 | The real injury signal — "DNP-DNP-LP" means "sit them." |
| D8 | Ownership context (not captured) | Sleeper trending + league rostered union (already have `ownedIds`) | 2 | 1 | "Owned in 98% of leagues" beside player names. |

## 3. Analysis gaps — missing features that matter

| # | Feature | What it does | E | F | E/F | Notes |
|---|---|---|---|---|---|---|
| A1 | **Drop-candidate heuristic** | Fill `WaiverTarget.dropCandidates` with the user's weakest bench at the matching position | 4 | 1 | 4.0 | Biggest *unfinished* one-day fix in the codebase. |
| A2 | **League-aware scoring format** | Read `SleeperLeague.scoringSettings`; re-weight TE (TE-premium), route pass-heavy (PPR vs standard) | 5 | 2 | 2.5 | In a PPR league today, a 6-target WR and a 20-carry RB look identical. |
| A3 | **Rest-of-season playoff schedule grader** | Score each roster spot's Weeks 14–17 matchups | 4 | 2 | 2.0 | Dictates hold/trade/drop decisions. |
| A4 | **Opponent lineup scouting** | "Your Week N opponent projects 127 — you need 130+" | 4 | 2 | 2.0 | Leverages already-built `WeeklyReportBuilder` across 2 rosters. |
| A5 | **News → factor wiring** | Any `NewsBlurb(fantasyImpact = NEGATIVE)` in last 48h → negative Factor w=8 | 3 | 1 | 3.0 | Data is already in `PlayerInsight`; wiring is 30 lines. |
| A6 | **Monte Carlo lineup simulator** | 10k sims from `ProjectionRange` floor/ceil → P(win) vs opponent | 4 | 3 | 1.3 | Replaces greedy. Shows win-prob on Weekly Report. |
| A7 | **Position-aware factor weights** | Framework §1 table has per-position overrides; analyzer uses position-agnostic weights | 3 | 1 | 3.0 | One switch on `position` — fast win. |
| A8 | **Trade analyzer** | "Send A+B for C+D" → rest-of-season Δ projection + win-prob delta | 4 | 3 | 1.3 | Probably the #1 feature casual users ask for. |
| A9 | **Streaming tools (DEF + K)** | Weekly matchup rankings filtered to FA defenses / kickers | 3 | 1 | 3.0 | Pure filter over existing waiver analyzer. |
| A10 | **Bye-week planner** | Mark bye weeks across the season; flag roster spots that go bare | 3 | 1 | 3.0 | Read `SleeperPlayer` bye week; compose a grid. |
| A11 | **Historical accuracy scoreboard** | Track our picks week-over-week; show "we've gone 18–7 this season" | 4 | 3 | 1.3 | Trust-builder. Needs persistent storage (SQLDelight). |
| A12 | **Personalized coaching** | "You started Collins over Kirk 3 weeks; Collins is underperforming. Consider bench." | 4 | 4 | 1.0 | Needs longitudinal data. Phase 2. |
| A13 | **Auction + draft tools** | Live draft assistant with value-over-replacement | 3 | 4 | 0.8 | Huge ask; only relevant 2 months a year. |
| A14 | **Dynasty / keeper valuation** | KeepTradeCut-style values for dynasty leagues | 3 | 4 | 0.8 | Needed for users in dynasty, niche otherwise. |
| A15 | **Insider news push** | Schefter-tier breaking → push within 90s to affected rostered-player owners | 5 | 4 | 1.3 | Requires tiny backend. See `MOBILE_EDGE_GAPS.md`. |

## 4. Underused data (cheapest wins)

These are "data we already fetch, score we already don't update":

1. **`gameEnvironment.impliedTeamTotal`** — only read in `StartSitAnalyzer.environmentScore`. `WaiverWireAnalyzer` and `WeeklyReportBuilder` ignore it. Wiring it into waiver scoring (+10 pts when impliedTotal > 26) is 15 lines. **E=2, F=0.5**
2. **`gameEnvironment.windMph`** — only used in `hasNegativeHighWeightFactor` (threshold 20 mph) and StartSit `environmentScore` (15 mph). Different thresholds, same signal. Unify to framework §1.5 (wind ≥ 15 penalty for QB/K). **E=2, F=0.5**
3. **`usage.redZoneOppLast3`** — used only in waiver scoring. `StartSitAnalyzer.buildFactors` ignores it. RZ opportunity is the strongest TD-dependence signal we have. **E=3, F=0.5**
4. **`recentNews`** — fetched, rendered, not scored. See gap A5. **E=3, F=1**
5. **`matchup.opponentRankVsPos`** — we compute a `MatchupGrade` tier from it but discard the numeric rank. Showing "vs. #4 CB" in UI beats "TOUGH matchup" for expert users. **E=1, F=0.5**

## 5. Free-edge quick wins (< 1 engineer-day each)

1. **Drop-candidate heuristic** (A1) — Scan user's bench, pick lowest `projection.median` at candidate's position. 30 lines.
2. **News → factor wiring** (A5) — In `InsightAggregator`, append negative news as a Factor when `fantasyImpact == NEGATIVE` and `publishedEpochMs > now - 48h`.
3. **Position-aware weights** (A7) — When analyzing a QB, weight pass-yardage signals higher than rush attempts. One branch.
4. **Use `impliedTeamTotal` in waiver scoring** — +8 priority points if candidate plays in a game total ≥ 48. One line.
5. **Streaming DEF + K tool** (A9) — Add a query-flag to `findTargets(positionFilter = "DEF")`. 10 lines.

Combined edge of these 5 probably moves our weekly pick accuracy by 5–10 percentage points on current data, before any data upgrades.

## 6. Paid data worth considering

| Source | $/mo | Unlocks | Phase-2? | nflverse substitute? |
|---|---|---|---|---|
| SportsDataIO NFL | $19+ | Real projections + practice reports | Yes | 80% via own model + `injuries.csv` |
| Sportradar | ~$100 | Official injury + real-time stats | Later | Same + live scoring via Sleeper |
| FantasyPros PRO | $40 | Licensed ECR API | Yes | ~70% via ensemble of 3 free sources |
| FTN Fantasy (DVOA) | $15 | Per-game DVOA | Maybe | Own PBP aggregation (A3/D1 work). |
| The Odds API Paid | $30 | 5k req/hr lines | Yes (when we fetch per-user) | Free tier fine for MVP. |

Recommendation: **skip all paid feeds until gaps D1/D2 (nflverse DvP + usage) land**. nflverse is free and gets you to ~80% of paid-feed quality.

## 7. E/F ranked punch list (top 20)

| Rank | Gap | E | F | E/F | Data needed | Code needed |
|---|---|---|---|---|---|---|
| 1 | A1 Drop candidates | 4 | 1 | 4.0 | Existing `SleeperRoster.players` | `WaiverWireAnalyzer.kt` — +30 lines |
| 2 | A5 News → factor | 3 | 1 | 3.0 | Already fetched | `InsightAggregator.kt` |
| 3 | A7 Position-aware weights | 3 | 1 | 3.0 | None | `StartSitAnalyzer.kt` |
| 4 | A9 Streaming DEF/K | 3 | 1 | 3.0 | None | `WaiverWireAnalyzer.kt` |
| 5 | A10 Bye-week planner | 3 | 1 | 3.0 | Existing `SleeperPlayer.byeWeek` | new composable |
| 6 | D1 Real DvP | 5 | 2 | 2.5 | nflverse PBP CSV | new `NflverseDvpSource` |
| 7 | D2 Real usage | 5 | 2 | 2.5 | nflverse snap_counts | new `NflverseUsageSource` |
| 8 | A2 League-aware scoring | 5 | 2 | 2.5 | `SleeperLeague.scoringSettings` (existing) | analyzer branch |
| 9 | D5 Real odds | 3 | 1 | 3.0 | The Odds API key | new `TheOddsApiVegasSource` |
| 10 | D4 Opponent field | 3 | 1 | 3.0 | ESPN scoreboard | `InsightAggregator` wire |
| 11 | D7 Practice trend | 3 | 1 | 3.0 | nflverse injuries.csv | `InjurySource` fold-in |
| 12 | D3 Real projections | 4 | 2 | 2.0 | D1+D2 first | derive-from-usage tweak |
| 13 | A3 Playoff schedule | 4 | 2 | 2.0 | NFL schedule feed | new `PlayoffScheduleScorer` |
| 14 | A4 Opponent scouting | 4 | 2 | 2.0 | existing | second `WeeklyReportBuilder` call |
| 15 | A6 Monte Carlo | 4 | 3 | 1.3 | none | new `MonteCarloLineupOptimizer` |
| 16 | A8 Trade analyzer | 4 | 3 | 1.3 | A3 for rest-of-season | new `TradeAnalyzer` |
| 17 | A11 Accuracy board | 4 | 3 | 1.3 | persistence | SQLDelight + new screen |
| 18 | A15 Insider push | 5 | 4 | 1.3 | tiny cloud backend | mobile + server |
| 19 | D8 Ownership % | 2 | 1 | 2.0 | already have | UI only |
| 20 | A12 Personalized coach | 4 | 4 | 1.0 | A11 persistence first | new screen |

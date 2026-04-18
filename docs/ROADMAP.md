# sleepy.io Roadmap — Ranked Edge-per-Effort

Synthesis of every gap doc (`docs/gaps/*`) sorted by **E/F** — edge gained
(0–5) divided by effort (0–5). This is the single queue to pick next work
from. Pick top of list; revisit weekly.

## Top 10 — do these first

| # | Gap | Source | E | F | E/F | Lands when |
|---|---|---|---|---|---|---|
| 1 | Drop-candidate heuristic — fill `WaiverTarget.dropCandidates` | `ANALYSIS` A1, `BACKEND` #2, `UX` #2 | 4 | 1 | 4.0 | ≤ 2 hrs |
| 2 | Wire `SecureStorage` to remember last league/roster across sessions | `UX` #1, `MOBILE` #3 | 4 | 1 | 4.0 | ≤ 4 hrs |
| 3 | Wire `DeepLinkParser` into navigation so push notifications can land | `MOBILE` #1, `UX` #7 | 4 | 1 | 4.0 | ≤ 4 hrs |
| 4 | Cross-platform KV cache — fix "JVM-only" caching on mobile | `BACKEND` #5, `MOBILE` #2 | 4 | 1 | 4.0 | ≤ 1 day |
| 5 | Deadline bar — "Thu lock in 3h 42m" header | `UX` §6 | 4 | 1 | 4.0 | ≤ 4 hrs |
| 6 | News → Factor wiring: NEGATIVE news in last 48h lowers score | `ANALYSIS` A5 | 3 | 1 | 3.0 | ≤ 2 hrs |
| 7 | Position-aware factor weights per framework §1 | `ANALYSIS` A7 | 3 | 1 | 3.0 | ≤ 2 hrs |
| 8 | Streaming DEF + K tool (position filter on waiver) | `ANALYSIS` A9 | 3 | 1 | 3.0 | ≤ 2 hrs |
| 9 | Bye-week planner | `ANALYSIS` A10 | 3 | 1 | 3.0 | ≤ 4 hrs |
| 10 | Tooltips teaching the framework on tap-factor | `UX` #9 | 3 | 1 | 3.0 | ≤ 4 hrs |

**Sum of edge for top 10**: ~37 edge-points for ~3 engineer-days.

## Next 10 — after the quick wins

| # | Gap | Source | E | F | E/F | Notes |
|---|---|---|---|---|---|---|
| 11 | Real DvP via nflverse PBP | `ANALYSIS` D1, `BACKEND` | 5 | 2 | 2.5 | Kills NEUTRAL-for-everyone bug |
| 12 | Real usage via nflverse snap_counts | `ANALYSIS` D2, `BACKEND` | 5 | 2 | 2.5 | Lights up 25% opportunity weight |
| 13 | League-aware scoring format (PPR / TE-premium / SF) | `ANALYSIS` A2, `BACKEND` #7 | 5 | 2 | 2.5 | Reads existing `SleeperLeague.scoringSettings` |
| 14 | Real Vegas lines via The Odds API (free tier) | `ANALYSIS` D5 | 3 | 1 | 3.0 | Key needed |
| 15 | Populate `PlayerInsight.opponent` | `ANALYSIS` D4, `BACKEND` | 3 | 1 | 3.0 | ESPN scoreboard endpoint |
| 16 | Practice-report trend (DNP→LP→FP) | `ANALYSIS` D7 | 3 | 1 | 3.0 | nflverse injuries.csv already planned |
| 17 | `HttpTimeout` + retry on all sources | `BACKEND` #4 | 3 | 1 | 3.0 | Eliminates 10-min hangs |
| 18 | Opponent preview card ("opp projects 127") | `UX` §6, `ANALYSIS` A4 | 4 | 2 | 2.0 | Second report build |
| 19 | Since-you-last-opened diff feed | `UX` §6 | 4 | 2 | 2.0 | Needs persistence |
| 20 | Rest-of-season playoff schedule grader | `ANALYSIS` A3 | 4 | 2 | 2.0 | Dictates hold/trade/drop |

## Longer bets — after the top 20 ship

| Theme | Gaps | Horizon |
|---|---|---|
| **Push pipeline (Cloudflare Worker + FCM/APNs)** | `MOBILE` #10 | 1 week + tiny backend |
| **Pluggable source contributors** | `BACKEND` #9 | Lands with Phase-2 refactor |
| **Unified `Outcome<T>` at boundaries** | `BACKEND` #10 | Phase-2 refactor |
| **Monte Carlo lineup simulator** | `ANALYSIS` A6 | Replaces greedy |
| **Trade analyzer** | `ANALYSIS` A8 | Depends on A3 rest-of-season |
| **Historical accuracy scoreboard** | `ANALYSIS` A11 | SQLDelight (Phase 3) |
| **Home-screen widget** | `MOBILE` §3 | WidgetKit + appwidget targets |
| **Compose Multiplatform navigation (Decompose)** | `REFACTOR_PLAN` §7 | Phase 3 |
| **Theming consolidation on Material3** | `REFACTOR_PLAN` §9 | Phase 3 |
| **Personalized coaching loop** | `ANALYSIS` A12 | Needs A11 first |

## Intentionally deprioritized

| Feature | Reason |
|---|---|
| **Wear OS / watchOS** | Small audience, high cost. Revisit if push lands and users ask. |
| **Auction + dynasty draft tools** | Relevant 2 months/year. Build the weekly-edge loop first. |
| **iOS Live Activity** | Resource-hungry, iOS-only, one-shot UX. Static widget beats it for v1. |
| **Full DDD / MVI rewrite** | YAGNI. `ScreenState` + sealed `Recommendation` are enough. |
| **Custom analytics SDK** | Wait until there are users. |

## How this list evolves

Each time a feature ships: (a) check it off here, (b) re-score the remaining
list — priorities shift when dependencies land. Keep this doc as the single
source of truth for what's next.

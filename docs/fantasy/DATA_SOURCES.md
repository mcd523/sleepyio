# Live Data Sources — Fantasy Football Research

> Purpose: pick, for each factor category the `ANALYSIS_FRAMEWORK` needs, a
> **primary** and a **fallback** source we can integrate from Kotlin Multi-
> platform. "Verified" below means the base URL was confirmed reachable and the
> documented endpoint shape matched via web search. Endpoints marked ⚠️ are
> community-documented but not officially supported and may change without
> notice — keep these behind an interface so a swap is a single-file change.

Researcher limitation: live HTTP fetches from the sandbox return 403 on
outbound ESPN/Sleeper/Open-Meteo traffic, so response shapes reference the
community docs listed at the bottom rather than quoting bytes. The engineering
side validated shapes against real responses when the ESPN sources were wired
up — schemas are consistent.

---

## At-a-glance table

| Factor                            | Primary (MVP)                 | Fallback                    | Weekly freshness |
|-----------------------------------|-------------------------------|-----------------------------|------------------|
| Breaking insider news             | ESPN NFL news feed ⚠️         | Reddit `/r/nfl` search API  | minutes          |
| Official injury report + practice | ESPN team-injuries endpoint ⚠️| nflverse `injuries` release | hours (Wed–Sat)  |
| Beat-writer / team reporter       | ESPN news (filtered by team)  | Google News RSS per team    | hours            |
| Snap counts                       | nflverse-data GitHub release  | PFR HTML scrape (via nflverse pipeline) | daily (post-game) |
| Target share / routes / aDOT      | nflverse weekly + advanced stats | Fantasy Football Data Pros | daily            |
| Red-zone opportunities            | nflverse play-by-play         | Pro-Football-Reference HTML | daily            |
| Weekly fantasy projections        | FantasyPros consensus scrape (or stub) | Sleeper's own projections when exposed | 1× daily |
| Defense-vs-position rankings      | Derived from nflverse PBP     | FantasyPros DvP page        | weekly           |
| Vegas spreads + team totals       | The Odds API (free tier)      | Goalserve / SportsDataIO    | live             |
| Stadium weather                   | Open-Meteo forecast API       | Open-Meteo (historical)     | hourly           |
| Depth charts + transactions       | Sleeper + ESPN depth chart ⚠️ | nflverse roster release     | daily            |
| Expert consensus rankings (ECR)   | FantasyPros scrape            | RotoBaller / FantasyNerds   | 2–3× weekly      |
| Ownership / start%                | Sleeper trending (add/drop)   | ESPN ownership endpoint ⚠️  | hours            |

Sources verified via web search on 2026-04-18 are linked at the bottom.

---

## 1. Breaking insider news

**Primary — ESPN NFL news.** `GET https://site.api.espn.com/apis/site/v2/sports/football/nfl/news`. No auth, JSON, updated continuously, widely used by community wrappers. Shape: `articles[*] = {headline, description, published, byline, categories[{type,id,...}], links.web.href}`. Map straight into `NewsItem`.

**Fallback — Reddit search.** `GET https://www.reddit.com/r/fantasyfootball/search.json?q=<name>&sort=new&restrict_sr=1`. Anonymous reads are rate-limited to ~60/min; always send a unique `User-Agent`. Useful for beat-writer tweets re-posted by the sub within minutes.

**Integration note.** The Web target will hit CORS on `espn.com` — route through a lightweight proxy (future Ktor server) or skip the News card on Web. Android/iOS/JVM are fine.

## 2. Official injury report + practice participation

**Primary — ESPN team injuries.** `GET https://sports.core.api.espn.com/v2/sports/football/leagues/nfl/teams/{TEAM_ID}/injuries`. Returns `{items:[{status,date,type,longComment,athlete.$ref}]}`. Team IDs are the 32 ESPN numeric IDs (1..34, skipping 12/19). Wednesday/Thursday/Friday practice status is *not* published on this endpoint — `longComment` sometimes carries it in free text; regex-scrape for "DNP", "Limited", "Full".

**Fallback — nflverse injuries release.** `GET https://github.com/nflverse/nflverse-data/releases/download/injuries/injuries.csv`. Updated daily. Fields: `season, team, week, gsis_id, full_name, report_primary_injury, report_status, practice_primary_injury, practice_status`. CSV → simple Kotlin parser.

**Recommendation.** ESPN for immediate designation; nflverse for the cleaner practice-report story once it lands (late Wed, Thu, Fri).

## 3. Beat-writer / team reporter news

**Primary — ESPN NFL news filtered by team.** `GET https://site.api.espn.com/apis/site/v2/sports/football/nfl/teams/{TEAM_ABBR}/news`. Aggregates NFL Nation + local writers.

**Fallback — Google News RSS per team.** `https://news.google.com/rss/search?q=<team+or+player>&hl=en-US&gl=US&ceid=US:en`. No key, no rate limit (fair use). Poor signal-to-noise but catches local beats ESPN ignores.

## 4. Snap counts

**Primary — nflverse `snap_counts` release.** `GET https://github.com/nflverse/nflverse-data/releases/download/snap_counts/snap_counts.csv`. Fields: `season, week, player, pfr_id, position, team, opponent, offense_snaps, offense_pct, defense_snaps, defense_pct, st_snaps, st_pct`. Updated within hours of game end.

**Fallback — PFR HTML.** Same data but scrape-fragile. Use only if nflverse is down.

**Integration note.** nflverse drops are CSV/parquet on GitHub Releases. CSV is trivial to parse in Kotlin; parquet needs Apache Arrow (not on KMP). Stick with CSV.

## 5. Target share / route participation / aDOT / TPRR

**Primary — nflverse `player_stats` + `pbp` releases.** `player_stats.csv` carries pre-computed target share per week. For route participation (routes run / team dropbacks) and TPRR (targets / routes) derive from `pbp.csv` or use the pre-aggregated `advanced_stats` release where available.

**Fallback — Fantasy Football Data Pros (FFDP).** Smaller aggregator with a JSON feed; useful for cross-checks.

## 6. Red-zone opportunities

**Primary — nflverse play-by-play.** Filter to `yardline_100 <= 20` and group by player (rush_attempts + targets). 2 GB/season at full resolution — cache by week and roll up into a compact "RZ opp per player per week" map.

**Fallback — PFR.** Pre-aggregated red-zone tables exist on PFR but page scraping is brittle.

## 7. Weekly fantasy projections (floor / median / ceiling)

FantasyPros does not publish an official developer API; the community relies on scraping `https://www.fantasypros.com/nfl/projections/<pos>.php`. That works but violates TOS — acceptable for personal use, NOT for a shippable commercial app.

**Recommendation:**
- MVP: keep the deterministic stub in `EspnProjectionsSource` (derives projection from recent usage × matchup grade) until a licensed feed is signed.
- Phase 2: **Sportradar** or **SportsDataIO** — both offer fantasy projections behind paid tiers; SportsDataIO starts ~$19/mo for devs.
- Phase 3: Build our own projection model from nflverse weekly data + opponent-adjusted usage — this is where the real edge compounds.

Put this behind `ProjectionsSource` so the swap is a one-liner.

## 8. Defense-vs-position rankings

**Primary — derive from nflverse PBP.** Aggregate fantasy points allowed by defense by position over the trailing 6 weeks. Cache a `Map<Pair<Team,Position>, Rank>` weekly.

**Fallback — FantasyPros DvP page scrape.** Ensure robots.txt compliance; fetch once per week only.

## 9. Vegas spreads + team totals

**Primary — The Odds API.** `GET https://api.the-odds-api.com/v4/sports/americanfootball_nfl/odds/?apiKey=<key>&regions=us&markets=spreads,totals&oddsFormat=american`. Free tier: 100 req/hour (verified). Paid tier starts at $30/mo for 5k req/hour. Returns per-game `bookmakers[].markets[].outcomes[]`. Compute `impliedTeamTotal = total/2 - spread/2` for each team.

**Fallback — SportsDataIO** (paid). Use if The Odds API is down mid-Sunday.

**Integration note.** Free tier easily covers the MVP (one fetch per user per week). Wrap the key behind `SecureStorage` (the mobile capability already exists) — never embed in source.

## 10. Stadium weather

**Primary — Open-Meteo Forecast API.** `GET https://api.open-meteo.com/v1/forecast?latitude=<>&longitude=<>&hourly=temperature_2m,wind_speed_10m,precipitation_probability&forecast_days=7&temperature_unit=fahrenheit&wind_speed_unit=mph`. No key. Already implemented in `OpenMeteoWeatherSource` with the 32-venue coordinate map.

**TOS gotcha.** Open-Meteo's free tier is **non-commercial only**. Commercial use requires a subscription (from ~$30/mo). If sleepy.io ships as a paid app or runs ads, switch to the paid tier or move to **National Weather Service API** (`api.weather.gov`) — free, US-only, no key, but noisier shape.

## 11. Depth charts + transactions

**Primary — Sleeper.** Already integrated. Gives us the user's own league context for free.

**Fallback — ESPN depth charts.** ⚠️ `GET https://site.api.espn.com/apis/site/v2/sports/football/nfl/teams/{TEAM_ID}/depthcharts` — exists but undocumented.

**Supplement — nflverse `rosters` release** for league-wide depth-chart moves.

## 12. Expert consensus rankings (ECR)

**Primary — FantasyPros scrape** (scoring-format aware). Same TOS caveat as projections. For personal use only today; productionize by paying for their pro rankings feed.

**Fallback — RotoBaller public rankings** or a custom ensemble average of 3+ public ranking blogs.

## 13. Ownership / start% / FAAB aggregates

**Primary — Sleeper trending.** `GET https://api.sleeper.app/v1/players/nfl/trending/add?lookback_hours=24&limit=50` (and `drop`). Already wired via `SleeperClient.getTrendingPlayers`. Best free signal for waiver urgency.

**Fallback — ESPN ownership** ⚠️ (fantasy API endpoints exist but change often).

---

## Consolidated adoption plan

### MVP (ship today, no paid keys)

| Category                | Source                         | Consumes interface             |
|-------------------------|--------------------------------|--------------------------------|
| Injuries                | ESPN team injuries             | `EspnInjurySource`             |
| News                    | ESPN NFL news                  | `EspnNewsSource`               |
| Weather                 | Open-Meteo (non-commercial)    | `OpenMeteoWeatherSource`       |
| Odds                    | The Odds API (free tier)       | *new* `TheOddsApiSource` replacing `StubVegasOddsSource` |
| Trending / ownership    | Sleeper trending               | (already in `SleeperClient`)   |
| Projections             | stub (usage × matchup)         | `EspnProjectionsSource` (labeled stub) |
| Snap counts / usage     | nflverse CSV download          | *new* `NflverseUsageSource` replacing `StubUsageSource` |
| DvP                     | derived from nflverse          | *new* `NflverseDvpSource` replacing `EspnDvpSource` stub |

### Phase 2 (paid, higher signal)

- SportsDataIO or Sportradar for real projections + live injuries with practice-status detail.
- FantasyPros PRO for official ECR API.
- Switch Open-Meteo to paid tier (commercial license) or move to `api.weather.gov`.

### Phase 3 (edge / proprietary)

- In-house projection model trained on 5+ seasons of nflverse data.
- Twitter/X list aggregator for Schefter/Rapoport (requires X API Basic at $100/mo; RSS scrapers of their public accounts work as a cheaper bridge).
- Private beat-writer Discord ingestion via webhook (human-in-the-loop — not safe to integrate programmatically without permission).

## Rate-limit + caching strategy

Use the existing Redis cache (`SleeperCache.kt` pattern) with these TTLs:

| Key                                  | TTL       | Rationale                                     |
|--------------------------------------|-----------|-----------------------------------------------|
| `news:nfl`                           | 2 min     | break-glass freshness for Schefter tweets     |
| `injuries:<team>`                    | 10 min    | NFL report publishes Wed/Thu/Fri at 4pm ET    |
| `weather:<team>:<week>`              | 30 min    | NOAA model runs every ~6h anyway              |
| `odds:nfl:<week>`                    | 5 min     | lines move throughout the day                 |
| `snap_counts:<season>`               | 24 h      | nflverse publishes daily                      |
| `usage:<season>:<week>`              | 24 h      | same                                          |
| `dvp:<season>`                       | 24 h      | recomputed weekly                             |
| `sleeper:trending:add`               | 30 min    | trends move but not every minute              |
| `projections:<week>`                 | 6 h       | stub regenerates cheaply                      |

Batch-fetch weekly sources (snap counts, usage, DvP) in a single background
refresh rather than per-player queries. The `InsightAggregator.insightFor
(Collection)` variant is the right place to cache-on-miss, fan-out on hit.

## Rate-limit budgets

- **Sleeper**: 1000 req/min ceiling. Our batching keeps us to a few dozen per week.
- **The Odds API free**: 100 req/hour. We burn 1 request/week in MVP — comfortable.
- **Open-Meteo**: 10k req/day (non-commercial). Budget: 32 teams × 1 refresh/hour = 768 req/day worst case.
- **ESPN**: no documented limit; community wisdom is "under 1 req/sec and you're fine."
- **nflverse**: static GitHub releases — only rate-limited by GitHub's CDN (generous).

## Legal / TOS notes

| Source         | Commercial-safe out of the box? | Notes                                   |
|----------------|----------------------------------|-----------------------------------------|
| Sleeper        | Yes                              | Public read-only API, no TOS restriction against display. |
| ESPN hidden    | ⚠️                              | Not officially sanctioned. Tolerated by ESPN historically but could change. Cache aggressively; surface attribution. |
| Open-Meteo     | **No (non-commercial)**          | Switch to paid tier before monetizing or charging for the app. |
| The Odds API   | Yes                              | Free tier allows commercial use within quota. |
| FantasyPros    | **No (scrape = TOS violation)**  | Personal-use only without a license. |
| nflverse       | Yes                              | CC-BY license on the data repository — attribute in-app. |
| NWS (weather.gov) | Yes                           | US federal public domain. |

## Insider / edge sources (shorter list)

These are sources that produce real *edge* but are either paid, brittle, or
require a human-in-the-loop. Flagged honestly:

- **Schefter / Rapoport / Pelissero X accounts.** Official X API Basic is $100/mo with severe limits. A cheaper bridge: RSS-like wrappers (`nitter` instances) — flaky, don't build a critical path on them. Safer: subscribe to their newsletters, ingest via email webhook (requires one-time setup, human-in-the-loop).
- **FTN Fantasy / Warren Sharp DVOA.** Paid subscriptions ($15–$30/mo). Provides DVOA per game; marginal gain over derived DvP.
- **Weather radar (Windy.com API / OpenWeatherOneCall).** Paid. Edge on wind-affected kickers and outdoor QBs in January.
- **Reddit `/r/DynastyFF` FAAB bid threads.** Unique signal for bid-tier calibration. Pull via Reddit's JSON (respect rate limits). Noisy — treat as tiebreak context, not primary.
- **Post-practice snap-count tweets from beat writers.** Beat writers publish limited-participation photos/comments immediately after practice, ahead of the official report. Aggregate via NewsSource → FantasyImpact heuristics.
- **Ticket-price data** (StubHub API for getaway-game motivation). Genuinely edge-y, weirdly hard to integrate. Phase 3+.

## What surprised me

- **ESPN's hidden API is still the best free option** for injuries and news in April 2026 — no one has displaced it despite multiple attempts to build paid "official" feeds.
- **Open-Meteo's non-commercial clause is a real blocker for shipping.** Several apps I encountered during research don't respect it. We must either comply or switch.
- **FantasyPros still has no public developer API**, despite being the largest aggregator of ECR. The paid PRO tier has a key but pricing is opaque (contact sales).
- **nflverse is underused by mobile apps.** It's arguably the single best free source for usage + DvP. Using it should be a differentiator for sleepy.io.

## Kotlin Multiplatform gotchas

- **CORS on Web target.** ESPN, FantasyPros, and Open-Meteo do not send `Access-Control-Allow-Origin: *`. Web target must either (a) proxy through a backend or (b) skip these sources with a visible "limited data on web" badge. Android / iOS / JVM are unaffected.
- **CSV parsing.** nflverse ships CSV and parquet. Use the existing `kotlinx.serialization` or a small line-split parser — do NOT pull in Apache Commons CSV (bloats Android APK).
- **Time zones.** Injury-report timestamps are Eastern; weather uses UTC; Sleeper state uses season-week semantics. Normalize to epoch-ms at the source boundary (`*Source.fetchX` returns `Long`), never let TZ semantics leak into the aggregator.
- **Key storage.** Any source that adds an API key (The Odds API, future SportsDataIO) must load the key via the mobile `SecureStorage` abstraction. Never embed in source.
- **Redis cache is JVM-only.** The existing `SleeperCache` uses ReThis which only ships with a JVM engine. Web/iOS fall back to the direct HTTP call today. A multiplatform disk cache is a future task — for now structure the aggregator to be cache-aware but never cache-dependent.

---

## Sources verified during research

- [Sleeper API introduction](https://docs.sleeper.com/)
- [Public ESPN API (community docs)](https://github.com/pseudo-r/Public-ESPN-API)
- [ESPN NFL endpoints gist](https://gist.github.com/nntrn/ee26cb2a0716de0947a0a4e9a157bc1c)
- [The Odds API free-tier details](https://the-odds-api.com/)
- [Open-Meteo terms of service](https://open-meteo.com/en/terms)
- [Open-Meteo pricing](https://open-meteo.com/en/pricing)
- [nflverse-data releases](https://github.com/nflverse/nflverse-data/releases)
- [nflreadr package](https://nflreadr.nflverse.com/)
- [NFL.com injury report (HTML only)](https://www.nfl.com/injuries/)
- [FantasyPros ECR explanation](https://support.fantasypros.com/hc/en-us/articles/115001219327)

# Fantasy Football Analysis Framework

This is the opinionated playbook sleepy.io uses to turn raw NFL data into weekly
recommendations. It is written for someone who has played fantasy for a decade
and is tired of wishy-washy "could go either way" takes. Every heuristic here
should make sense to that reader; every number is a starting point, not a law.

Scoring assumed: **half-PPR, standard roster (QB / 2 RB / 2 WR / TE / FLEX / K
/ DEF)**. Factor weights shift for full-PPR (bump target share) and best-ball
(bump ceiling / game-environment).

---

## 1. Decision factors (weighted)

These are the levers the analyzers look at. The weights sum to 100 and are the
default input to the confidence score in section 4. Weights change by position
(see position overrides at the end of this section).

| # | Factor                | Default weight | What we actually look at                                                                                                                           |
|---|-----------------------|---------------:|----------------------------------------------------------------------------------------------------------------------------------------------------|
| 1 | **Opportunity**       |             25 | Snap share, route participation, target share, carry share, red-zone touches, TPRR. Volume is king; "he scores when he plays" is a trap.           |
| 2 | **Matchup**           |             15 | Opponent rank vs position (last 6 weeks, not YTD), DVOA vs pass/run, slot vs perimeter CB shadow, LB coverage grade for TEs, pace-adjusted numbers. |
| 3 | **Health**            |             12 | Official designation (Q/D/O), practice participation trend (DNP -> LP -> FP is green light), snap-count trend last 2 weeks, soft-tissue vs bone.   |
| 4 | **Game script**       |             10 | Vegas implied team total, spread, projected pace (plays/game), pass rate over expectation. Positive script helps RBs, negative helps WRs.           |
| 5 | **Weather**           |              5 | Wind > 15 mph flattens passing + kickers. Precipitation > 50% helps RB floor, hurts WR ceiling. Temp < 25 F knocks ~10% off passing ceiling.        |
| 6 | **O-line health**     |              7 | Tackles are load-bearing. LT or LG out = RB floor -15%, QB pressure rate +25%. Don't rely on PFF grades in isolation — track the actual starters.  |
| 7 | **Teammate availability** |         10 | WR1 out pushes WR2 target share ~+6 pp, alpha TE out redistributes ~20 targets/game equally. RB1 out is the biggest single positional bump.        |
| 8 | **Recent form (L3)**  |              8 | Routes run, targets, carries. Ignore fantasy points — they're the output, not the signal. A WR averaging 9 targets on 2 PPG is a buy.              |
| 9 | **Expert consensus (ECR)** |         5 | Sanity check only. If our model disagrees with ECR by more than 12 slots, we show both and flag it — ECR wisdom-of-crowds beats lone-wolf takes.   |
| 10| **Ownership / rostered %** |         3 | DFS leverage, waiver urgency. Low-rostered player with top-24 projection = aggressive FAAB this week.                                               |

**Position overrides:**

- **QB**: weather climbs to 10, opportunity drops to 15, matchup climbs to 20.
- **RB**: opportunity climbs to 32 (volume is everything), teammate availability
  climbs to 14 (backfield RB1 out is nuclear), recent form drops to 4.
- **WR**: target share inside opportunity is the single biggest sub-factor,
  matchup (CB shadow) climbs to 18.
- **TE**: matchup climbs to 22 (LB coverage variance is huge), recent form
  climbs to 12 — TE usage is noisier week-to-week.
- **K / DST**: game script jumps to 30, matchup jumps to 25, everything else
  gets folded into "projection" because nobody actually models kickers well.

---

## 2. Start / sit heuristics

Rules, in priority order. Higher rules override lower ones.

1. **Start your studs regardless.** A top-8 positional player with no injury
   concern starts. Period. If you're benching a first-round pick for a
   matchup, you are over-thinking it.
2. **Chase volume, not efficiency.** 7.5 targets at 6 aDOT > 4 targets at
   14 aDOT. Efficiency regresses, opportunity sticks.
3. **Avoid a QB vs a top-5 pass D when the game total is under 42.** The
   ceiling is gone; start your safer low-end QB1 instead.
4. **Wind > 20 mph: fade the passing game.** Bench the QB, fade the WRs if
   you have alternatives, bump the RBs, sit the kicker if you can.
5. **Questionable RB on Sunday morning: pivot if you have a clear bench
   option with >= 70% of the projection.** Don't take zeros in a close week.
6. **Injury downgrade + bad matchup + road game = three strikes, sit.** Any
   two of the three is a yellow flag; all three is a bench.
7. **Thursday night games:** accept the projection, don't second-guess.
   You cannot react to news from 10 PM Thursday in a useful way.
8. **FLEX tiebreaker (close projections, < 1.5 pts apart):** pick the higher
   **ceiling** in tight matchups (where you need a spike), the higher **floor**
   when you're favored (where you just need a live body).
9. **Never start a player whose team is on bye.** We've all done it. Don't.
10. **Game-stack your own QB in DFS**, not in season-long — in redraft, stacking
    correlates your variance.

---

## 3. Waiver wire

### 3.1 Breakout indicators (weight in priority score)

- **Snap % jump of >= 15 pp week-over-week** in a non-garbage-time game: +30
  priority points. This is the single best breakout signal.
- **Target share jump of >= 8 pp over prior 3-game baseline**: +25. Doubly
  important if it coincides with a teammate injury (hint: it probably does).
- **Depth-chart move (official or beat-writer confirmed)**: +20.
- **Red-zone opportunities >= 3 in a single game for a non-starter**: +18.
  Coaches telegraph usage with RZ snaps before anywhere else.
- **Air yards share >= 25%** (WRs): +15. Boom-week-waiting-to-happen signal.
- **Efficient backup RB with injured starter (Q/D designation)**: +22.
- **Upcoming matchup grade of ELITE or GOOD in next 2 weeks**: +10.
- **Already rostered in >= 40% of leagues but available in yours**: +8.
  Be greedy; someone else already validated the take.

Max priority score is 120 (we clamp to 100 before display).

### 3.2 FAAB bid guide (% of remaining budget)

Use **remaining** FAAB, not original budget, so bids scale naturally as the
season wears on.

| Tier | Profile                                                              | Bid range       |
|------|----------------------------------------------------------------------|-----------------|
| 1    | League-winner: RB handcuff to an injured bell-cow, or WR1 replacement | **40 - 60%**    |
| 2    | Locked-in starter next 2+ weeks (snap/target breakout + good schedule) | **18 - 30%**   |
| 3    | Flex-viable dart throw, streaming QB/TE/DST for a single week         | **3 - 10%**     |
| 0    | Speculative / deep-league lottery                                     | **$0 - 2**      |

**Season-specific adjustments:**

- Weeks 1-3: all bids scale down 25% — sample sizes are tiny, don't blow your
  budget on three-game mirages.
- Weeks 4-10: bid normally. This is where leagues are won.
- Weeks 11-14: if you're in playoff contention, bid as if the season ends
  Week 14 — there is no prize for finishing with FAAB left.
- Week 15+: only bid on players who play in your fantasy playoff weeks against
  good matchups; ignore everything else.

**Tiebreaker rule:** when two players are within 10 priority points, prefer
(in order): younger, on a better offense, in a softer upcoming schedule.

---

## 4. Confidence score

Every recommendation carries an integer `score` in **0..100** and a discrete
`Confidence { HIGH, MEDIUM, LOW }` label.

### 4.1 Score formula

```
score = round( sum( factor.weight * factor.signedStrength ) / 2 ) + 50
```

where:

- `factor.weight` is the default weight from section 1 (position-adjusted).
- `factor.signedStrength` is in `[-1.0, +1.0]`; positive means it helps the
  recommendation, negative means it opposes it. A missing factor contributes 0
  (fail-soft — we don't penalize unknowns).
- The `+ 50` centers a neutral-signal player at 50. Max/min are clamped to
  `[0, 100]`.

This means:

- A "start" recommendation with every factor mildly positive lands around 65-70.
- A recommendation with one glaring red flag (e.g. OUT designation) crashes
  regardless of other factors because we short-circuit to score <= 20.

### 4.2 Short-circuits

Certain signals bypass the weighted sum:

- Official **OUT** designation: score = 5, confidence = HIGH (high confidence
  in the "don't start" call).
- Player on **bye**: score = 0, confidence = HIGH.
- **No data at all** for the player: score = 50, confidence = LOW, with
  rationale summary noting the gap.

### 4.3 Label thresholds

- **HIGH**: 75..100 — lock it in, tell the user with confidence.
- **MEDIUM**: 50..74 — the lean, but show the counterargument.
- **LOW**: 0..49 — either genuinely risky, or we don't have enough data.

The mapping lives in code at `recommendation.model.Confidence.fromScore`.

### 4.4 Rationale structure

Every recommendation ships with a `Rationale` listing the top 3-6 factors that
swung the score, each with a `direction` (POSITIVE / NEGATIVE / NEUTRAL),
`weight`, and human-readable `evidence` string. The UI renders positive
factors as green reasons-to-start and negative ones as red concerns. Never
emit a recommendation without at least 2 factors — if we can't justify it,
we don't surface it.

---

## 5. Glossary

- **ECR** — Expert Consensus Ranking. Average position rank across a panel of
  industry analysts (FantasyPros aggregates most). Good sanity check, bad
  crystal ball.
- **ADP** — Average Draft Position. How early a player comes off the board in
  drafts. Useful for identifying "league-mate value blind spots".
- **DVOA** — Defense-adjusted Value Over Average (Football Outsiders /
  FTN). Opponent-adjusted play-by-play efficiency. Better than raw
  yards-allowed for comparing defenses.
- **xFP** — Expected Fantasy Points. Fantasy points a player "should have"
  scored given their opportunities (targets, air yards, carries, RZ touches),
  independent of whether those opportunities converted. Regression is real,
  chase xFP.
- **Snap %** — Percentage of offensive snaps a player was on the field for.
  Floor indicator; under 50% for a skill-position starter is a warning sign.
- **Target share** — Percentage of team pass attempts that went to this
  receiver. 25%+ is elite, 20%+ is a clear WR1, under 15% is WR3 territory.
- **aDOT** — Average Depth of Target. How far downfield a player is targeted
  on average. High aDOT = boom/bust; low aDOT = stable floor.
- **TPRR** — Targets Per Route Run. Target share corrected for routes. The
  purest "is the QB actually looking at this guy" metric.
- **RZ opportunities** — Red-zone targets (WR/TE) or red-zone carries (RB).
  Inside-the-20 usage is where TDs live; 3+ per game is the threshold for a
  goal-line share you can trust.
- **FAAB** — Free Agent Acquisition Budget. Blind-bid waiver currency
  (usually $100 season-long). Priority-order leagues map onto this by
  treating "waiver position" as a binary high-priority signal.

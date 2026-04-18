# sleepy.io Design System

Product posture: confident, data-dense, decision-first. Think *The Athletic*
meets *Robinhood*, not ESPN marketing.

Every screen is built around one question: *what should I do this week?* The
UI should answer in under two seconds of scanning.

---

## Color tokens

Dark theme is the default — fantasy decisions happen on couches and at bars
late Sunday morning. A light theme is specified for future use but not yet
wired in.

### Dark (default)

| Token             | Hex        | Use                                        | Contrast vs pairing |
|-------------------|------------|--------------------------------------------|---------------------|
| background        | #0B0F14    | App background                             | —                   |
| onBackground      | #E6EAF0    | Primary text on background                 | 14.9:1 (AAA)        |
| surface           | #111822    | Cards, list rows                           | —                   |
| surfaceElevated   | #1A2330    | Modals, elevated cards                     | —                   |
| onSurface         | #E6EAF0    | Primary text on surface                    | 13.3:1 (AAA)        |
| onSurfaceMuted    | #98A2B3    | Secondary text, labels                     | 5.9:1 (AA)          |
| outline           | #2A3442    | Dividers, chip borders                     | —                   |
| primary           | #F5A524    | Brand accent, primary CTA                  | —                   |
| onPrimary         | #0B0F14    | Text/icon on primary                       | 11.2:1 (AAA)        |
| secondary         | #5B8DEF    | Secondary actions, links                   | 5.2:1 vs surface    |
| positive          | #16A34A    | Start recommendation, green-light signals  | 4.8:1 vs surface    |
| negative          | #DC2626    | Sit recommendation, risk, drop             | 4.9:1 vs surface    |
| warning           | #EAB308    | Questionable / caution designations        | 8.2:1 vs surface    |
| info              | #3B82F6    | Informational accents                      | 4.5:1 vs surface    |
| error             | #EF4444    | Error states, destructive                  | 4.9:1 vs surface    |

**Signaling rule**: never encode meaning in color alone. Pair every color
signal with a glyph or label (e.g. a positive factor is *green + up arrow +
"+"*, a questionable tag is *amber + "Q" glyph*).

### Light theme (future)

| Token            | Hex       |
|------------------|-----------|
| background       | #F7F8FA   |
| onBackground     | #0B0F14   |
| surface          | #FFFFFF   |
| surfaceElevated  | #F0F2F6   |
| onSurface        | #0B0F14   |
| onSurfaceMuted   | #50596B   |
| outline          | #D5DAE2   |
| primary          | #B9761A   |
| onPrimary        | #FFFFFF   |

---

## Typography

Compose Multiplatform `MaterialTheme.typography` with adjusted sizes:

| Role          | Size | Weight  | Use                                  |
|---------------|------|---------|--------------------------------------|
| displayMedium | 32sp | Bold    | Hero numbers (projected total)       |
| headlineLarge | 24sp | Bold    | Screen titles                        |
| headlineSmall | 20sp | SemiBold| Section titles                       |
| titleMedium   | 16sp | SemiBold| Card headers, player names in lists  |
| bodyLarge     | 15sp | Normal  | Reading copy (news, rationale)       |
| bodyMedium    | 14sp | Normal  | Secondary body                       |
| labelLarge    | 13sp | SemiBold| Buttons, chips, tags                 |
| labelSmall    | 11sp | Medium  | Meta (timestamps, positions)         |

Responsive rules in `LeagueStateScreen.getResponsiveSizes` remain — extend the
pattern to new screens rather than hardcoding `sp` values.

---

## Spacing & radii

- Spacing: `4, 8, 12, 16, 24, 32, 48`. Default gutter 16.
- Corner radii: `4, 8, 12, 16`. Cards use 12 by default, elevated modals 16.
- Minimum tap target: **48dp**.

---

## Core components

### Confidence badge
Three states — `HIGH`, `MED`, `LOW`. Filled pill, 12dp radius, 4dp vertical /
10dp horizontal padding, `labelLarge` type.

- HIGH  → bg `positive`, fg `#0B0F14`
- MED   → bg `warning`,  fg `#0B0F14`
- LOW   → bg `outline`,  fg `onSurfaceMuted`

### Factor chip
Inline evidence for a recommendation. Outline chip, 8dp radius.

- Positive → border `positive`, leading `▲` glyph, text `onSurface`
- Negative → border `negative`, leading `▼` glyph, text `onSurface`
- Neutral  → border `outline`,  leading `•` glyph, text `onSurfaceMuted`

### Projection range pill
Single pill rendering `floor – ceiling` with bold median underneath.
Background `surfaceElevated`, outline `outline`, width grows with content.

### Injury tag
Small uppercase tag next to player name:
- HEALTHY → hidden
- Q       → bg `warning`
- D       → bg `negative` with 70% opacity
- O/IR    → bg `negative`, fg `onPrimary`

### Matchup grade bar
Horizontal 5-segment bar. Fills left-to-right based on `MatchupGrade`:
ELITE (5/5 positive), GOOD (4/5 positive), NEUTRAL (3 segments outline),
TOUGH (2/5 negative), NIGHTMARE (5/5 negative).

---

## Information architecture

Four new screens live under `ui/recommendation/`, reached from a new bottom
nav inside the league view. All screens are observed from a single
`RecommendationViewModel` per league+week.

### 1. Weekly Report (default tab)

```
┌─────────────────────────────────────────────────┐
│ ← Back    Week N · My Team          ⟳ Refresh   │  AppBar
│ "Start your studs, fade Jameson vs CB1"         │  headline
├─────────────────────────────────────────────────┤
│ ┌─ Projected total ─────────────────┐           │
│ │  58.2 median   ·   45 – 72 range  │           │
│ │  Confidence: HIGH                 │           │
│ └───────────────────────────────────┘           │
│                                                 │
│ SECTION  Optimal lineup                         │
│ ┌── QB  Josh Allen         proj 22.4  ▲ matchup│
│ ├── RB  Bijan Robinson     proj 17.8  ▲ volume │
│ ├── RB  De'Von Achane      proj 13.1  ▲ health │
│ ├── WR  CeeDee Lamb        proj 16.2            │
│ ├── WR  Nico Collins       proj 12.8  ⚠ Q      │
│ ├── TE  Trey McBride       proj  9.5            │
│ ├── FLX Jaylen Warren      proj 11.0            │
│ ├── DST Steelers           proj  8.2            │
│ └── K   Younghoe Koo       proj  8.0            │
│                                                 │
│ SECTION  Start/Sit decisions (3)                │
│ [card] Start: Flowers  over  Tucker            │
│ [card] Start: Mingo    over  Reed              │
│                                                 │
│ SECTION  Risky starters                         │
│ • Collins (Q, limited Wed/Thu)                  │
│                                                 │
│ SECTION  Waiver targets (3)                     │
│ [card] Sampson  FAAB 18%                        │
└─────────────────────────────────────────────────┘
```

States: `loading` → skeleton list of 6 cards. `empty` → "Pick a week to see
your report." `error` → inline banner with Retry. Partial failure (e.g.
weather feed down) → show report, add subtle "Some data unavailable" footer.

### 2. Start/Sit

```
┌─────────────────────────────────────────────────┐
│ ← Back   Start/Sit                              │
├─────────────────────────────────────────────────┤
│ [Player A selector]   vs   [Player B selector]  │
│                                                 │
│ BIG VERDICT                                     │
│ ┌── START: Player A ───────────────────────┐   │
│ │ Confidence: HIGH        Score: 82        │   │
│ └──────────────────────────────────────────┘   │
│                                                 │
│ Why                                             │
│ ▲ Top-5 matchup vs position                     │
│ ▲ 28% target share last 3 weeks                 │
│ ▼ Questionable designation (monitor)            │
│                                                 │
│ Side-by-side                                    │
│ ┌ Player A │ Player B ┐                         │
│ │ Proj 15.2│ Proj 12.8│                         │
│ │ Floor 9  │ Floor 7  │                         │
│ │ Ceil 24  │ Ceil 18  │                         │
│ └──────────┴──────────┘                         │
└─────────────────────────────────────────────────┘
```

Keyboard focus order: A selector → B selector → verdict. Both selectors
searchable.

### 3. Waivers

```
┌─────────────────────────────────────────────────┐
│ ← Back   Waiver Wire · Week N                   │
├─────────────────────────────────────────────────┤
│ Filters:  [All] [QB] [RB] [WR] [TE] [DST] [K]   │
│                                                 │
│ 1. Ty Chandler         RB · MIN      FAAB 24%   │
│    Mattison IR · 68% snap share last week       │
│    [View insight]   [Drop: …]                   │
│                                                 │
│ 2. Romeo Doubs         WR · GB       FAAB 12%   │
│    Target share 26% since Watson return         │
│    [View insight]   [Drop: …]                   │
└─────────────────────────────────────────────────┘
```

### 4. Player Insight

```
┌─────────────────────────────────────────────────┐
│ ← Back   Bijan Robinson · RB · ATL              │
├─────────────────────────────────────────────────┤
│ [Projection pill]  17.8 median · 12 – 27 range  │
│ Matchup grade [■■■■□]  GOOD                    │
│                                                 │
│ Usage (last 3 wks)                              │
│  Snap %     74   Target share   12   RZ opp 5   │
│                                                 │
│ Injury                                          │
│  Questionable · knee · Full practice Fri        │
│                                                 │
│ Game environment                                │
│  Total 48.5   Spread -3    Dome   65°F          │
│                                                 │
│ Recent news                                     │
│  ▲ 2h — "Robinson expected to play"             │
│  ▼ 3d — "Wear down concerns after 24 carries"   │
└─────────────────────────────────────────────────┘
```

---

## Accessibility checklist

- Body text contrast ≥ 4.5:1, large text ≥ 3:1.
- Every color signal has a secondary glyph or label.
- Tap targets ≥ 48dp.
- All Composable `Modifier.semantics` on non-text interactive elements.
- Numeric projections are screen-reader-friendly: "median 17 point 8, range
  12 to 27", not raw digits.

---

## Anti-patterns to avoid

- No neon-on-black (fixed — the old palette used primary #00FF00 and
  surface #0066FF; do not reintroduce).
- No emoji-as-UI for anything load-bearing. Use typed glyphs or `Icon`.
- No pie charts. Use bars or rails — they scan faster on mobile.
- Never hide confidence. If a recommendation has LOW confidence, say so
  in the card header, not buried in an expandable.

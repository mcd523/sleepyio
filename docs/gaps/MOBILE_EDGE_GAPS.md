# Mobile Platform-Edge Gaps

The platform-capability surface is **built but almost entirely unwired**. The
single biggest mobile edge available — breaking-news push — is not yet
shipped. This doc ranks the gaps.

## 1. Capability surface audit

Each capability: built? wired into the UI? edge value 0–5 once wired.

| Capability | Common | Android | iOS | Wired? | Edge |
|---|---|---|---|---|---|
| `Haptics` | ✓ | ✓ | ✓ | **No** — no `lightTap/success/warning/error` call-site exists in any `ui/` file | 2 |
| `SecureStorage` | ✓ | ✓ (EncryptedSharedPreferences) | ⚠️ stub (NSUserDefaults, *not* Keychain) | **No** — neither `App.kt` nor any ViewModel reads it | 3 |
| `Notifications` (local) | ✓ | ✓ | ✓ | **No** — `LocalNotification` never scheduled | 3 |
| `Notifications` (push / remote) | — | — | — | **Missing entirely** — no FCM/APNs | **5** |
| `Connectivity` | ✓ | ✓ (NetworkCallback) | ✓ (NWPathMonitor) | **No** — `isOnline` flow is never collected | 3 |
| `AppLifecycle` | ✓ | ✓ (ProcessLifecycleOwner) | ✓ (UIApplication notifs) | **No** — `isForeground` flow unused | 2 |
| `DeviceInfo` | ✓ | ✓ | ✓ | **No** — `FormFactor` never read | 1 |
| `BiometricAuth` | ✓ | ✓ (BiometricPrompt) | ✓ (LAContext) | **No** — no action gated | 2 |
| `DeepLink` parser | ✓ | intent parsed | — | **Parsed but not routed** — `MainActivity.pendingDeepLink` set, `App.kt` ignores | 4 |

**Finding.** Seven of eight capabilities are fully built on Android and iOS,
but the app's UI layer does not yet reach them. The first phase of mobile
work should be **wiring**, not new capabilities.

## 2. Breaking-news push pipeline — the #1 mobile edge

**Why it matters.** A real league edge is *"I knew Barkley was OUT 4 minutes
before my opponent and moved Warren into FLEX."* Twitter reactions, ESPN
push alerts, and RotoWorld subs are how winning managers get this today.
sleepy.io's `NewsSource` already ingests ESPN news — we just don't push.

### Architecture gap

| Piece | Built? |
|---|---|
| Source of truth (news feed) | ✓ `EspnNewsSource` |
| Impact classifier | ✓ `FantasyImpact.POSITIVE/NEUTRAL/NEGATIVE` inferred from headline |
| Local notification API | ✓ `NotificationCenter.schedule(LocalNotification)` |
| Subscription model (per-player opt-in) | **Missing** |
| Remote push (APNs / FCM) | **Missing** — no server |
| Targeting (which user has which player rostered) | Derivable from Sleeper rosters |

### Minimum viable backend

Avoid standing up a full server. Options:

1. **Supabase Edge Functions + Postgres triggers** — free tier covers low traffic. Cron job pulls ESPN news every 3 min, writes rows, trigger fans out via FCM/APNs to subscribed device tokens.
2. **Cloudflare Workers + KV** — even cheaper, no cold starts, write a 200-line worker.
3. **Ktor server on Fly.io / Railway** — $5/mo. Share the Kotlin code with `commonMain`.

Recommend **Cloudflare Workers** for MVP. Move to a Ktor server once cross-league features need database writes.

### Client-side data model (sketch — belongs in `commonMain/recommendation/` or `platform/`)

```kotlin
data class NewsSubscription(
    val playerId: String,
    val channels: Set<Channel>,     // INJURY, TRANSACTION, PRACTICE_REPORT
    val minImpact: FantasyImpact,   // opt in to NEGATIVE only
)

interface NewsSubscriptionRegistry {
    suspend fun subscribeAllRostered(rosterId: Long)
    suspend fun unsubscribe(playerId: String)
    fun current(): Flow<List<NewsSubscription>>
}
```

### End-user UX

When push arrives with deep-link `sleepyio://players/<id>?week=N`:
1. OS renders push; user taps.
2. `MainActivity.onNewIntent` (Android) or `SceneDelegate` (iOS) passes URI to `DeepLinkParser`.
3. Parsed `DeepLink.PlayerInsight` dispatched to nav.
4. App lands on Player Insight for that player with the latest news at top.

Steps 2–4 require the deep-link wiring gap in `UX_AND_FRONTEND_GAPS.md`.

**E=5, F=4. E/F=1.25.** Highest-edge mobile feature by far.

## 3. Widgets + Live Activities — the Sunday edge

**Android home-screen widget / iOS WidgetKit widget** to scan live matchup
score without opening the app. Medium edge, medium effort.

| Variant | Platform | Effort | Edge |
|---|---|---|---|
| Matchup score delta (you vs opp, live points) | Both | 3d | 3 |
| Next 3 starters yet to play | Both | 2d | 2 |
| Active injury alerts (red dot on widget) | Both | 2d | 3 |
| iOS Live Activity during user's game | iOS only | 3d | 2 |

**Data.** Widgets read `WeeklyReport.lineup` + live Sleeper matchup points.
The data model is ready; only the platform targets are missing.

**Skip for MVP.** Deprioritize Live Activity — it's an iOS-only resource
hog for one-off "live matchup" UX. Ship the static widget first.

## 4. Wear OS + watchOS

Skip v1. Engineering cost high, fantasy audience on watches is small.
Revisit only if the mobile app finds product-market-fit and watch
notifications become a clear ask.

## 5. Offline + background refresh

- `Connectivity.isOnline` exists. **Nothing consumes it.** Offline today means
  the app silently fails every network request and shows Failed screens.
- `SleeperCache` is JVM-only (Redis). Mobile has **no cache**. Every
  screen navigation re-fetches.
- `WorkManager` (Android) + `BGAppRefreshTask` (iOS) — not implemented.
  The mobile doc flags these as phase 2.

### Minimum viable offline

1. **Cross-platform cache.** Use `multiplatform-settings` + kotlinx-serialization for a simple KV cache. 50 LOC. Applies to: `getAllPlayers`, weekly report, last-seen news timestamps.
2. **"Stale" badge.** When `Connectivity.isOnline == false`, every tab shows a banner: "Offline — showing cached data from 14 min ago."
3. **Background news poll.** Android: `PeriodicWorkRequest` every 30 min. iOS: `BGAppRefreshTask` registered in `AppDelegate`. Poll `NewsSource.fetchNewsForPlayer` for subscribed players; schedule `LocalNotification` on NEGATIVE impact. This is the **server-less** version of the push pipeline — works but has battery cost and 15–30 min latency.

Ship (1) and (2) in phase 1. (3) is a fallback before the server side of push lands.

## 6. Deep-link integration

- `DeepLinkParser` **is built and unit-tested**. Cite: `commonMain/platform/DeepLink.kt`, `commonTest/platform/DeepLinkParserTest.kt`.
- `MainActivity.onCreate` parses `intent.data` → `pendingDeepLink`. **Nothing reads `pendingDeepLink`.**
- `App.kt` composable has no `LaunchedEffect` observing an incoming deep link.
- iOS `SceneDelegate` handling is documented in the mobile doc but not present in `MainViewController.kt`.
- **Universal links / App Links**: `AndroidManifest.xml` intent-filter does NOT have `android:autoVerify="true"`, so `sleepyio://` is a custom scheme only. To support `https://sleepy.io/players/<id>`, add `autoVerify` + an `assetlinks.json` on your domain. iOS: add an Associated Domain entitlement + `apple-app-site-association`.

**E=4, F=1.** Wiring is ~50 LOC. **Do this before shipping push.**

## 7. Biometric + secure storage

- `BiometricAuth` is built on both platforms. **No action gates on it.** When waiver submissions via Sleeper writes land (future feature), gate those.
- iOS `SecureStorage` uses `NSUserDefaults` instead of Keychain — **a real security issue** if the user ever stores a Sleeper session cookie or The Odds API key. Fix: replace with `SecKeychain` wrapper (mobile doc flagged this as a stub).

## 8. Haptics — micro-interactions

Sometimes overrated, sometimes a real "feel" multiplier. Three places it'd matter:

1. `.success()` after submitting a waiver bid.
2. `.warning()` when tapping a HIGH-confidence "SIT" button (heavy action).
3. `.lightTap()` on any list row to confirm input reception on Android (iOS native gives this for free on Button).

**E=2, F=0.5 each.** Do as part of phase-1 wiring pass.

## 9. Form factor

`WindowSizeClass` is declared in `commonMain/platform/ui/WindowSizeClass.kt`. **No screen reads it.** `AdvisorSizes` mirrors `LeagueStateScreen`'s responsive pattern using raw width thresholds — migrate both to `WindowSizeClass` so tablet / foldable / expanded layouts are consistent across the app.

## 10. Ranked punch list — top 10

| Rank | Gap | Edge | Effort | E/F | Platform |
|---|---|---|---|---|---|
| 1 | Wire `DeepLinkParser` into navigation | 4 | 1 | 4.0 | Shared |
| 2 | Cross-platform KV cache (multiplatform-settings) | 4 | 1 | 4.0 | Shared |
| 3 | Persist last league/roster via `SecureStorage` | 4 | 1 | 4.0 | Shared |
| 4 | Offline banner driven by `Connectivity.isOnline` | 3 | 1 | 3.0 | Shared |
| 5 | Haptics wiring on button actions | 2 | 1 | 2.0 | Shared |
| 6 | Keychain replacement for iOS `SecureStorage` | 3 | 2 | 1.5 | iOS |
| 7 | Universal links + `apple-app-site-association` | 3 | 2 | 1.5 | Both |
| 8 | Static home-screen widget (matchup score) | 3 | 3 | 1.0 | Both |
| 9 | Background news poll + local notification | 3 | 3 | 1.0 | Both |
| 10 | Full push pipeline (FCM/APNs + tiny server) | 5 | 4 | 1.25 | Backend + both clients |

**Highest-edge single feature we're leaving on the table**: the push
pipeline (#10). Highest E/F immediate wins: #1–4, all shared, all < 1
engineer-day each.

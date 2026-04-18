# Mobile architecture — sleepy.io

This document covers how the Compose Multiplatform app reaches into
mobile-native features (haptics, secure storage, notifications, biometric
auth, …) without forking `commonMain` on `getPlatform()`, and the
conventions every future mobile workstream should follow.

It complements `docs/ARCHITECTURE.md`, which covers the data / domain /
recommendation layers. Anything here is strictly about the UI and the
capabilities below the compose tree.

## Design principles

1. **One expect per capability, never a god object.** Each capability
   (e.g. `Haptics`, `BiometricAuth`) is its own `expect class` in
   `commonMain/.../platform/`. That keeps the unit-of-test tiny and
   makes adding a capability a purely additive operation.
2. **No branching on `getPlatform()` at call sites.** Common code calls
   `capabilities.haptics.success()`. It never asks "am I on iOS?". If
   a platform doesn't support a capability it returns a sentinel
   (`AuthResult.UNAVAILABLE`) or silently no-ops — never throws.
3. **Aggregated behind one `PlatformCapabilities` handle.** All
   capabilities are reached through a single root object, which is
   provided to Composables via `LocalPlatformCapabilities`
   (`staticCompositionLocalOf`). No prop-drilling, no passing
   individual `Haptics` instances down.
4. **No `Context` / `NSObject` in `commonMain`.** Platform types stay
   on their side of the `expect`/`actual` boundary. The common API
   exposes Kotlin primitives, enums, data classes, and `StateFlow`.
5. **Fail soft.** An `actual` that can't do its job returns a stable
   sentinel rather than throwing. This is a hard rule — see
   anti-patterns below.

## Capability surface (commonMain)

| Capability          | Common type                       | Purpose                                                            |
| ------------------- | --------------------------------- | ------------------------------------------------------------------ |
| `Haptics`           | `expect class`                    | tap / success / warning / error feedback                           |
| `SecureStorage`     | `expect class` (suspend)          | small encrypted key/value                                          |
| `NotificationCenter`| `expect class` (+ data classes)   | local notifications + permission                                   |
| `Connectivity`      | `expect class` (`StateFlow<Bool>`)| reactive online/offline                                            |
| `AppLifecycle`      | `expect class` (`StateFlow<Bool>`)| foreground/background                                              |
| `DeviceInfo`        | `expect class`                    | model / os / form factor                                           |
| `BiometricAuth`     | `expect class` (suspend)          | Face/Touch ID gate                                                 |

All live in `com.sleepyio.sleepyio.platform`. The aggregate is
`PlatformCapabilities`, exposed via
`val LocalPlatformCapabilities = staticCompositionLocalOf<PlatformCapabilities>`.

## Platform notes

### Android

| Capability       | Impl                                                          | Permission / caveats                                    |
| ---------------- | ------------------------------------------------------------- | ------------------------------------------------------- |
| Haptics          | `VibratorManager` (API 31+) / `Vibrator`                      | `VIBRATE`                                               |
| SecureStorage    | `EncryptedSharedPreferences` + `MasterKey` (AES256 GCM)       | AndroidKeyStore-backed                                  |
| Notifications    | `NotificationManagerCompat`, channel `sleepyio_advisor`       | `POST_NOTIFICATIONS` on API 33+; AlarmManager phase-2   |
| Connectivity     | `ConnectivityManager.NetworkCallback` + `NET_CAPABILITY_VALIDATED` | `ACCESS_NETWORK_STATE`                              |
| AppLifecycle     | `ProcessLifecycleOwner`                                       | process-scoped, not Activity-scoped                     |
| DeviceInfo       | `Build.*` + `Configuration.smallestScreenWidthDp`             | foldable detection deferred (`androidx.window`)         |
| BiometricAuth    | `BiometricPrompt` from `androidx.biometric`                   | API 28+; `BIOMETRIC_STRONG \| DEVICE_CREDENTIAL` only   |

Dependencies added to `composeApp/build.gradle.kts` under `androidMain`:
`androidx.biometric:biometric`, `androidx.security:security-crypto`,
`androidx.fragment:fragment-ktx`.

`MainActivity` now extends `FragmentActivity` (required by `BiometricPrompt`).

### iOS

| Capability       | Impl                                                          | Caveats                                                  |
| ---------------- | ------------------------------------------------------------- | -------------------------------------------------------- |
| Haptics          | `UIImpactFeedbackGenerator`, `UINotificationFeedbackGenerator`| main-thread only                                         |
| SecureStorage    | **STUB — NSUserDefaults**, see below                          | flip to Keychain before App Store                        |
| Notifications    | `UNUserNotificationCenter` + `UNTimeIntervalNotificationTrigger` | payload `userInfo["deepLink"]` for deep-link tap    |
| Connectivity     | `NWPathMonitor` on default-priority GCD queue                 | reports `satisfied` status only                          |
| AppLifecycle     | `UIApplicationDidBecomeActive` / `WillResignActive` notifs    | "active" not merely "foreground"                         |
| DeviceInfo       | `UIDevice.currentDevice`, `userInterfaceIdiom`                |                                                          |
| BiometricAuth    | `LAContext` + `LAPolicyDeviceOwnerAuthentication`             | Info.plist `NSFaceIDUsageDescription` required           |

`Info.plist` updates (already applied in `iosApp/iosApp/Info.plist`):

```xml
<key>NSFaceIDUsageDescription</key>
<string>sleepy.io uses Face ID to protect access to your league credentials.</string>
<key>CFBundleURLTypes</key>
<array>
  <dict>
    <key>CFBundleURLName</key><string>com.sleepyio.sleepyio.deeplink</string>
    <key>CFBundleURLSchemes</key><array><string>sleepyio</string></array>
  </dict>
</array>
```

#### Stubs

- **`SecureStorage.ios.kt`** is a documented STUB backed by
  `NSUserDefaults`. A production impl must route through `SecItemAdd` /
  `SecItemCopyMatching` / `SecItemDelete` with
  `kSecAttrAccessibleWhenUnlockedThisDeviceOnly` and an app-group
  `kSecAttrAccessGroup` so a future share-extension can read the
  same bucket. The common API (`suspend put/get/remove`) does not
  change.

### Desktop (JVM) / Web (JS / wasmJs)

All capabilities have sensible fallbacks:

- `Haptics` → no-op.
- `SecureStorage` → JVM uses `java.util.prefs.Preferences` with a
  dev-time "not encrypted" warning log. Web uses `localStorage` with
  an identical caveat documented in code.
- `NotificationCenter` → logs; `requestPermission` returns GRANTED on
  desktop, NOT_DETERMINED on web. Real Web `Notification` API / desktop
  `SystemTray` wiring is phase-2.
- `Connectivity` / `AppLifecycle` → constant `true` flows (comment in
  each file documents the follow-up signal to subscribe to).
- `DeviceInfo` → `os.name` / `navigator.userAgent`, form factor
  `DESKTOP` or `WEB`.
- `BiometricAuth` → UNAVAILABLE.

## Deep-link scheme

Base scheme: `sleepyio://`.

| Path                                        | `DeepLink` variant       |
| ------------------------------------------- | ------------------------ |
| `sleepyio://advisor/<leagueId>/<rosterId>`  | `DeepLink.Advisor`       |
| `sleepyio://players/<playerId>[?week=N]`    | `DeepLink.PlayerInsight` |
| `sleepyio://waivers/<leagueId>/<rosterId>/<week>` | `DeepLink.Waivers` |

Parser is pure: `com.sleepyio.sleepyio.platform.DeepLinkParser.parse(uri)`.
Unit-test it from `commonTest`; do not copy the parsing into platform
code.

### Android

Manifest intent-filter added to `MainActivity`:

```xml
<intent-filter android:autoVerify="false">
    <action android:name="android.intent.action.VIEW"/>
    <category android:name="android.intent.category.DEFAULT"/>
    <category android:name="android.intent.category.BROWSABLE"/>
    <data android:scheme="sleepyio"/>
</intent-filter>
```

`MainActivity.onCreate` / `onNewIntent` run `DeepLinkParser.parse` and
stash the result in `MainActivity.pendingDeepLink` until the future
`LocalPlatformCapabilities`-aware navigator consumes it.

### iOS

`CFBundleURLSchemes` is set (see Info.plist snippet above). When the
shared-code wiring lands, the host Swift `iOSApp.swift` will forward
`onOpenURL { url in Deeplinks.shared.receive(url.absoluteString) }` into
a `NativeEntryPoint` helper that calls the same `DeepLinkParser.parse`
and pushes onto the shared state machine. For now, the iOS plist
advertises the scheme so testing via `xcrun simctl openurl` works.

## Adaptive layout

New in `platform.ui.WindowSizeClass`:

- `WindowWidthClass` — COMPACT (<600dp) / MEDIUM (600..840) / EXPANDED (>840).
- `WindowHeightClass` — COMPACT (<480dp) / MEDIUM (480..900) / EXPANDED (>900).
- `rememberWindowSize()` — recomposes on resize; uses `LocalWindowInfo`
  so it works on every target (follows the pattern already in
  `LeagueStateScreen`).

### Advisor tab guidance

- **Compact width.** Single-pane. Start/Sit, Waivers, Player Insight,
  Weekly Report are siblings in a tab row; tapping a player pushes a
  full-screen detail.
- **Medium width.** Single-pane, but lists get two-column grids where
  they fit (waiver target cards).
- **Expanded width.** Two-pane list/detail. Left pane keeps the
  roster/target list, right pane owns the detail — no push navigation.
  Header lives above both panes.

Concretely: `when (rememberWindowSize().widthClass) { COMPACT -> … ; else -> … }`.
The advisor screens don't yet consume `WindowSize`; that migration is a
follow-up and should be done one screen at a time.

## Offline strategy

| Layer                          | Concern                                                              |
| ------------------------------ | -------------------------------------------------------------------- |
| Redis cache (existing)         | Server/side-loaded aggregations, already in place                    |
| `Connectivity.isOnline`        | UI gating: disable "submit waiver", show "stale" badge on reports    |
| `SecureStorage`                | auth tokens, per-league preferences — small, sensitive               |
| File cache (future)            | large, non-sensitive blobs (news articles, images). NOT `SecureStorage`; add a `Blobs` capability when needed |

Rule of thumb: if a value is secret, put it in `SecureStorage`. If it's
big or re-fetchable, it belongs in a future file-cache capability, not
in secure storage.

## Background refresh (phase-2)

Capability surface is introduced now, background execution is phase-2.

- **Android**: `WorkManager` with a periodic `CoroutineWorker` for
  player-news refresh + a one-shot `OneTimeWorkRequest` for scheduled
  lineup alerts. Hooked via a new `BackgroundWork` capability —
  NOT by importing WorkManager into `commonMain`.
- **iOS**: `BGAppRefreshTask` registered in `AppDelegate`;
  `BGTaskScheduler.submit(...)` from Kotlin via a similar
  `BackgroundWork` capability.

Both targets feed the same common `ScheduledRefresh` interface; desktop
falls through to an in-process `Timer`.

## Extensibility checklist

To add a new capability (e.g. `Clipboard`, `Share`, `Camera`, `Sensors`):

1. One file in `commonMain/platform/Xxx.kt` — `expect class Xxx` with a
   KDoc block (what it's for, WHY it's an abstraction, safe-to-call
   semantics).
2. Add the property to `PlatformCapabilities` (common + every actual).
3. One `actual` per target:
   - `androidMain/platform/Xxx.android.kt`
   - `iosMain/platform/Xxx.ios.kt`
   - `jvmMain/platform/Xxx.jvm.kt`
   - `jsMain/platform/Xxx.js.kt`
   - `wasmJsMain/platform/Xxx.wasmJs.kt`
4. Any new dependency goes in the target-specific `dependencies` block
   only. Never pull Android/iOS APIs into `commonMain`.
5. If a capability can't ship on a target today, return a sentinel
   (`UNAVAILABLE`, `NOT_DETERMINED`) or no-op. **Never** throw from
   an `actual`.
6. Unit-test any pure logic (e.g. URI parsing, size-class math) from
   `commonTest` — platform impls stay thin by design.

## Anti-patterns (enforced)

- **No `android.content.Context`, `View`, `Activity`, `NSObject`, or
  `UIView` in `commonMain`.** If you feel the urge, the capability is
  mis-sized — break it further.
- **No `actual` that throws.** Graceful degradation is part of the
  contract.
- **No `getPlatform().name == "Android"` branching at call sites.**
  That's the exact smell this abstraction exists to remove.
- **No CompositionLocal for individual capabilities.** Everything
  reaches through `LocalPlatformCapabilities` — one source of truth.
- **No DI container (Hilt/Koin) for this layer.** We assemble one
  aggregate in the platform entry point and publish via
  CompositionLocal. That is the DI strategy.

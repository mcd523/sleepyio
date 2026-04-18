Welcome to sleepy.io, a fun project to combine my love of fantasy football and my curiosity towards new technologies. This is a Kotlin Multiplatform project that uses Compose Multiplatform for the UI and targets Android, iOS, Desktop (JVM), and Web (Wasm and JS).

* [/composeApp](./composeApp/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
    - [commonMain](./composeApp/src/commonMain/kotlin) is for code that’s common for all targets.
    - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
      For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
      the [iosMain](./composeApp/src/iosMain/kotlin) folder would be the right place for such calls.
      Similarly, if you want to edit the Desktop (JVM) specific part, the [jvmMain](./composeApp/src/jvmMain/kotlin)
      folder is the appropriate location.

* [/iosApp](./iosApp/iosApp) contains iOS applications. Even if you’re sharing your UI with Compose Multiplatform,
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.

### Build and Run Android Application

To build and run the development version of the Android app, use the run configuration from the run widget
in your IDE’s toolbar or build it directly from the terminal:

- on macOS/Linux
  ```shell
  ./gradlew :composeApp:assembleDebug
  ```
- on Windows
  ```shell
  .\gradlew.bat :composeApp:assembleDebug
  ```

### Build and Run Desktop (JVM) Application

To build and run the development version of the desktop app, use the run configuration from the run widget
in your IDE’s toolbar or run it directly from the terminal:

- on macOS/Linux
  ```shell
  ./gradlew :composeApp:run
  ```
- on Windows
  ```shell
  .\gradlew.bat :composeApp:run
  ```

### Build and Run Web Application

To build and run the development version of the web app, use the run configuration from the run widget
in your IDE's toolbar or run it directly from the terminal:

- for the Wasm target (faster, modern browsers):
    - on macOS/Linux
      ```shell
      ./gradlew :composeApp:wasmJsBrowserDevelopmentRun
      ```
    - on Windows
      ```shell
      .\gradlew.bat :composeApp:wasmJsBrowserDevelopmentRun
      ```
- for the JS target (slower, supports older browsers):
    - on macOS/Linux
      ```shell
      ./gradlew :composeApp:jsBrowserDevelopmentRun
      ```
    - on Windows
      ```shell
      .\gradlew.bat :composeApp:jsBrowserDevelopmentRun
      ```

### Build and Run iOS Application

To build and run the development version of the iOS app, use the run configuration from the run widget
in your IDE’s toolbar or open the [/iosApp](./iosApp) directory in Xcode and run it from there.

---

## Mobile

The app targets Android and iOS as first-class platforms alongside Desktop
and Web. Mobile-native features are surfaced to `commonMain` through a
capability abstraction layer in `com.sleepyio.sleepyio.platform`, so
screens call e.g. `capabilities.haptics.success()` instead of branching
on `getPlatform()`.

Capabilities available today:

- `Haptics` — tap / success / warning / error feedback
- `SecureStorage` — encrypted key/value (Android Keystore, iOS Keychain*)
- `NotificationCenter` — local notifications + permission
- `Connectivity` — reactive online/offline `StateFlow<Boolean>`
- `AppLifecycle` — foreground/background `StateFlow<Boolean>`
- `DeviceInfo` — model / OS / form factor
- `BiometricAuth` — Face/Touch ID / BiometricPrompt

*iOS SecureStorage is a documented `NSUserDefaults`-backed stub in this
revision; see `docs/mobile/MOBILE_ARCHITECTURE.md` for the Keychain
migration plan.

Adaptive layout helpers live at
`com.sleepyio.sleepyio.platform.ui.rememberWindowSize()` and are the
canonical way to branch on window-size-class across every target.

### Deep links

The app registers the `sleepyio://` scheme on both Android and iOS.
Parsing is shared via `com.sleepyio.sleepyio.platform.DeepLinkParser`.

| URL                                              | Opens                         |
| ------------------------------------------------ | ----------------------------- |
| `sleepyio://advisor/<leagueId>/<rosterId>`       | Advisor for that roster       |
| `sleepyio://players/<playerId>?week=N`           | Player Insight screen         |
| `sleepyio://waivers/<leagueId>/<rosterId>/<week>`| Waiver-wire targets           |

Test on Android: `adb shell am start -a android.intent.action.VIEW -d "sleepyio://advisor/1/2"`.
Test on iOS: `xcrun simctl openurl booted "sleepyio://advisor/1/2"`.

Full mobile architecture, per-platform API choices, permission model,
offline strategy, and the extensibility checklist for adding new
capabilities are documented in
[`docs/mobile/MOBILE_ARCHITECTURE.md`](./docs/mobile/MOBILE_ARCHITECTURE.md).

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html),
[Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform/#compose-multiplatform),
[Kotlin/Wasm](https://kotl.in/wasm/)…

We would appreciate your feedback on Compose/Web and Kotlin/Wasm in the public Slack
channel [#compose-web](https://slack-chats.kotlinlang.org/c/compose-web).
If you face any issues, please report them on [YouTrack](https://youtrack.jetbrains.com/newIssue?project=CMP).
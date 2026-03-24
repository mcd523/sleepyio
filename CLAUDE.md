# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

sleepy.io is a Kotlin Multiplatform Compose application for viewing fantasy football leagues on the Sleeper platform. It targets Android, iOS, Desktop (JVM), and Web (Wasm/JS) from a single shared codebase.

## Build & Run Commands

```bash
# Desktop (JVM) - primary dev target
./gradlew :composeApp:run

# Android
./gradlew :composeApp:assembleDebug

# Web (Wasm - modern browsers)
./gradlew :composeApp:wasmJsBrowserDevelopmentRun

# Web (JS - broader compatibility)
./gradlew :composeApp:jsBrowserDevelopmentRun

# Run tests
./gradlew :composeApp:allTests

# iOS - open iosApp/ in Xcode
```

## Testing UI Changes

Use the web (JS) target to visually verify UI changes, as Playwright can interact with and screenshot the running app:

```bash
# Start the web dev server
./gradlew :composeApp:jsBrowserDevelopmentRun
# App runs at http://localhost:8080
```

Use Playwright MCP tools (`browser_navigate`, `browser_snapshot`, `browser_take_screenshot`) to evaluate UI state after changes. This is the preferred way to validate layout, styling, and interactive behavior.

## Architecture

### Module Structure
Single module project: `composeApp` contains all shared code in `commonMain`, with platform-specific entry points in `androidMain`, `iosMain`, `jvmMain`, and `webMain`.

### Key Packages (`composeApp/src/commonMain/kotlin/com/sleepyio/sleepyio/`)

- **`client/`** — `SleeperClient` is a `data object` singleton HTTP client using Ktor. Talks to Sleeper's REST API (`api.sleeper.app/v1/`), stats API (`api.sleeper.com/`), and GraphQL endpoint (`sleeper.com/graphql`). Models are in `client/model/` organized by domain (user, league, draft, player, stats, graphql).
- **`ui/`** — Compose screens. `LeagueStateScreen` is the main league view with responsive layout (mobile/tablet/desktop breakpoints at 800/1200px). `PlayerProfileScreen` shows player details.
- **`cache/`** — `SleeperCache` is a Redis-backed singleton cache for player data with 24h TTL. Requires Redis running on localhost:6379 (see `docker-compose.yml`).
- **`App.kt`** — Root composable with navigation state machine: `USER_LOGIN → LEAGUE_LIST → LEAGUE_STATE`.
- **`Theme.kt`** — Sleeper-inspired dark theme using pure Material3. Contains `SleeperTheme` composable, `SleeperSpacing` (dp tokens: xxs/xs/sm/md/lg/xl/xxl), `SleeperType` (sp tokens: displayLarge/displayMedium/headline/titleLarge/titleMedium/body/caption/statValue), and `BfsDepthColors` for BFS depth visualization.

### Platform Specifics
- `Platform.kt` declares `expect` interface for platform-specific HTTP engine selection
- Desktop entry: `jvmMain/.../main.kt` — Compose Window app
- Android entry: `androidMain/.../MainActivity.kt`
- iOS entry: `iosApp/iosApp/iOSApp.swift` wrapping ComposeApp framework
- Web entry: `webMain/.../main.kt` — ComposeViewport

### Dependencies (managed in `gradle/libs.versions.toml`)
- Kotlin 2.2.20, Compose Multiplatform 1.9.0, JVM target 21
- Ktor 3.3.1 (CIO engine, content negotiation, JSON)
- Coil 3 for image loading
- kotlinx.serialization for JSON
- Rethis 0.3.5 for Redis caching

### Infrastructure
- Redis for player caching: `docker compose up -d` to start
- Sleeper CDN at `sleepercdn.com` for avatars, headshots, team logos

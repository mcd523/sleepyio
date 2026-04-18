package com.sleepyio.sleepyio.platform

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Aggregate of every mobile-native capability the app exposes to
 * `commonMain`. One instance per process; assembled by each platform
 * entry point (Android `MainActivity`, iOS `MainViewController`, …) and
 * published to Composables via [LocalPlatformCapabilities].
 *
 * Keeping this as an `expect class` (instead of a common `class` that
 * takes seven constructor args) lets each target lazily initialise
 * capabilities using its own idiomatic scope — Android gets an
 * `ApplicationContext` + `lifecycleScope`, iOS gets the main run loop,
 * and the desktop/web fallbacks are free to be stateless singletons.
 */
expect class PlatformCapabilities {
    val haptics: Haptics
    val secureStorage: SecureStorage
    val notifications: NotificationCenter
    val connectivity: Connectivity
    val lifecycle: AppLifecycle
    val device: DeviceInfo
    val biometric: BiometricAuth
}

/**
 * CompositionLocal for reaching [PlatformCapabilities] from any Composable
 * without prop-drilling. The platform entry point is responsible for
 * wrapping `App()` in a `CompositionLocalProvider(LocalPlatformCapabilities provides …)`.
 *
 * `staticCompositionLocalOf` is chosen because capabilities are effectively
 * immutable for the lifetime of the process — swapping them would mean
 * changing OS-provided handles, which never happens at runtime.
 */
val LocalPlatformCapabilities = staticCompositionLocalOf<PlatformCapabilities> {
    error(
        "PlatformCapabilities not provided — wrap App() in a " +
            "CompositionLocalProvider(LocalPlatformCapabilities provides …) " +
            "from your platform entry point (MainActivity / MainViewController / main.kt).",
    )
}

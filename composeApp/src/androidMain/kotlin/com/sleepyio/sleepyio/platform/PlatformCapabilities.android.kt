package com.sleepyio.sleepyio.platform

/**
 * Android [PlatformCapabilities] aggregate. Construct once after
 * [AndroidContextProvider.install] has been called from the host
 * Activity (typically `MainActivity.onCreate`) and reuse for the process
 * lifetime — internal singletons (network callbacks, lifecycle observers)
 * are not safe to recreate per-screen.
 *
 * All capabilities have no-arg constructors to match the common
 * `expect class` declarations; their Android resources come from the
 * installed [AndroidContextProvider].
 */
actual class PlatformCapabilities {
    actual val haptics: Haptics = Haptics()
    actual val secureStorage: SecureStorage = SecureStorage()
    actual val notifications: NotificationCenter = NotificationCenter()
    actual val connectivity: Connectivity = Connectivity()
    actual val lifecycle: AppLifecycle = AppLifecycle()
    actual val device: DeviceInfo = DeviceInfo()
    actual val biometric: BiometricAuth = BiometricAuth()
}

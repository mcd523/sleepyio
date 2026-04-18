package com.sleepyio.sleepyio.platform

/**
 * iOS [PlatformCapabilities] aggregate. All capabilities are zero-arg
 * constructors because the UIKit-side handles (`UIDevice`, notification
 * centers, `LAContext`, …) are already process-global singletons.
 *
 * Constructed from `MainViewController.kt` once, reused for the process
 * lifetime.
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

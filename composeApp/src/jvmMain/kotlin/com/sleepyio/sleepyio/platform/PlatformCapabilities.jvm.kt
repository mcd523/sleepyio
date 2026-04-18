package com.sleepyio.sleepyio.platform

actual class PlatformCapabilities {
    actual val haptics: Haptics = Haptics()
    actual val secureStorage: SecureStorage = SecureStorage()
    actual val notifications: NotificationCenter = NotificationCenter()
    actual val connectivity: Connectivity = Connectivity()
    actual val lifecycle: AppLifecycle = AppLifecycle()
    actual val device: DeviceInfo = DeviceInfo()
    actual val biometric: BiometricAuth = BiometricAuth()
}

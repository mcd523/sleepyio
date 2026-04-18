package com.sleepyio.sleepyio.platform

import android.content.Context
import androidx.fragment.app.FragmentActivity

/**
 * Android [PlatformCapabilities] aggregate. Construct once from the host
 * Activity (typically `MainActivity.onCreate`) and reuse for the process
 * lifetime — internal singletons (network callbacks, lifecycle observers)
 * are not safe to recreate per-screen.
 *
 * [activityProvider] is a `() -> FragmentActivity?` because the only
 * capability that needs a live Activity is [BiometricAuth], and wiring
 * that through a WeakReference-style lookup lets the rest of the
 * capabilities remain activity-independent.
 */
actual class PlatformCapabilities(
    context: Context,
    activityProvider: () -> FragmentActivity?,
) {
    private val appContext = context.applicationContext

    actual val haptics: Haptics = Haptics(appContext)
    actual val secureStorage: SecureStorage = SecureStorage(appContext)
    actual val notifications: NotificationCenter = NotificationCenter(appContext)
    actual val connectivity: Connectivity = Connectivity(appContext)
    actual val lifecycle: AppLifecycle = AppLifecycle()
    actual val device: DeviceInfo = DeviceInfo(appContext)
    actual val biometric: BiometricAuth = BiometricAuth(appContext, activityProvider)
}

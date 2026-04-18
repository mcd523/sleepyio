package com.sleepyio.sleepyio.platform

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Android haptics backed by the system [Vibrator]. We deliberately use the
 * Vibrator rather than `View.performHapticFeedback` so the capability is
 * available without a Compose `View` reference — Composables can still
 * call this from effect blocks that don't have `LocalView`.
 *
 * Primary constructor is no-arg to match the common `expect class Haptics`
 * (which has an implicit no-arg constructor). The Android [Context] is
 * fetched lazily from [AndroidContextProvider], which the platform
 * entry point installs before any capability is touched.
 */
actual class Haptics {

    private val vibrator: Vibrator? by lazy {
        val ctx = AndroidContextProvider.requireContext()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val mgr = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            mgr?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    actual fun lightTap() = oneShot(TICK_MS, amplitudeLow = true)
    actual fun success() = pattern(longArrayOf(0, 20, 40, 30))
    actual fun warning() = pattern(longArrayOf(0, 40, 60, 40))
    actual fun error() = pattern(longArrayOf(0, 60, 80, 60, 80, 60))

    private fun oneShot(durationMs: Long, amplitudeLow: Boolean) {
        val v = vibrator?.takeIf { it.hasVibrator() } ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val amplitude = if (amplitudeLow) LOW_AMPLITUDE else VibrationEffect.DEFAULT_AMPLITUDE
            v.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(durationMs)
        }
    }

    private fun pattern(pattern: LongArray) {
        val v = vibrator?.takeIf { it.hasVibrator() } ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(pattern, -1)
        }
    }

    private companion object {
        const val TICK_MS = 10L
        const val LOW_AMPLITUDE = 64
    }
}

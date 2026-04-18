package com.sleepyio.sleepyio.platform

/**
 * Haptic feedback capability.
 *
 * Why this is an `expect class` and not a `getPlatform()` branch:
 * callers in `commonMain` (ViewModels, Compose effects) want to express
 * intent (`lightTap()`, `success()`) without knowing whether they are on
 * Android's `HapticFeedbackConstants`, iOS's `UIFeedbackGenerator`, or a
 * desktop target where haptics are simply a no-op. Adding a new target
 * (watchOS, wearable) means writing one `actual` — no call-site churn.
 *
 * All methods are safe to call at any time; implementations must never
 * throw. If the device lacks a haptic engine the call silently drops.
 */
expect class Haptics {
    /** Short, low-impact tick used for selection / toggle / slider detents. */
    fun lightTap()

    /** Positive confirmation — lineup locked, waiver claim submitted. */
    fun success()

    /** Soft warning — destructive confirmation, unsaved changes. */
    fun warning()

    /** Error pattern — failed submit, network loss while live-updating. */
    fun error()
}

package com.sleepyio.sleepyio.platform

/**
 * Browser no-op haptics. The Vibration API exists on some mobile
 * browsers (`navigator.vibrate`) but is inconsistent; left out of
 * this phase.
 */
actual class Haptics {
    actual fun lightTap() = Unit
    actual fun success() = Unit
    actual fun warning() = Unit
    actual fun error() = Unit
}

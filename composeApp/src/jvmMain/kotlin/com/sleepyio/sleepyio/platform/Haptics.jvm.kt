package com.sleepyio.sleepyio.platform

/**
 * Desktop no-op haptics. Desktops have no haptic actuator; calls silently
 * do nothing so `commonMain` callers don't need to branch.
 */
actual class Haptics {
    actual fun lightTap() = Unit
    actual fun success() = Unit
    actual fun warning() = Unit
    actual fun error() = Unit
}

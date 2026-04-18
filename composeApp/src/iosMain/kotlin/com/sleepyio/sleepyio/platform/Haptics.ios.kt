package com.sleepyio.sleepyio.platform

import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType

/**
 * iOS haptics backed by [UIImpactFeedbackGenerator] (taps) and
 * [UINotificationFeedbackGenerator] (success/warning/error).
 *
 * Generators must be allocated and then `prepare()`d shortly before use
 * for the lowest-latency haptic; we re-allocate per-call for simplicity
 * (the alloc cost is trivial compared to a frame).
 *
 * UIKit haptics are safe to call on the main thread only; Kotlin/Native
 * coroutines on iOS default to a main-thread dispatcher so callers from
 * Compose effect blocks are already compliant.
 */
actual class Haptics {

    actual fun lightTap() {
        val gen = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleLight)
        gen.prepare()
        gen.impactOccurred()
    }

    actual fun success() = notify(UINotificationFeedbackType.UINotificationFeedbackTypeSuccess)
    actual fun warning() = notify(UINotificationFeedbackType.UINotificationFeedbackTypeWarning)
    actual fun error() = notify(UINotificationFeedbackType.UINotificationFeedbackTypeError)

    private fun notify(type: UINotificationFeedbackType) {
        val gen = UINotificationFeedbackGenerator()
        gen.prepare()
        gen.notificationOccurred(type)
    }
}

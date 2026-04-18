package com.sleepyio.sleepyio.platform

import kotlinx.coroutines.flow.StateFlow

/**
 * Foreground/background state of the host app.
 *
 * Used to gate expensive refresh loops (e.g. snap-count polling) so they
 * pause when the user task-switches and resume on return — critical for
 * battery on mobile, and a correctness concern for iOS where long-running
 * background network calls are limited.
 *
 * Why expect/actual:
 * - Android: `ProcessLifecycleOwner.get().lifecycle` observer.
 * - iOS: `UIApplication.willResignActiveNotification` / `didBecomeActiveNotification`.
 * - Desktop/Web: always `true` (the process model doesn't map cleanly; for
 *   web we could later wire `document.visibilitychange`).
 */
expect class AppLifecycle {
    val isForeground: StateFlow<Boolean>
}

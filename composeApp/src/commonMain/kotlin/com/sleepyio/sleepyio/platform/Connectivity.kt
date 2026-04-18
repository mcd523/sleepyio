package com.sleepyio.sleepyio.platform

import kotlinx.coroutines.flow.StateFlow

/**
 * Reactive network-reachability signal.
 *
 * The advisor UI uses this to:
 * - disable "submit waiver" / "lock lineup" CTAs when offline,
 * - stamp cached reports with a "stale — reconnect to refresh" badge,
 * - pause live-update polling (snap counts, in-game news) off-network.
 *
 * Why expect/actual:
 * - Android: `ConnectivityManager.NetworkCallback` with NET_CAPABILITY_VALIDATED.
 * - iOS: `NWPathMonitor` on a background queue.
 * - JVM/JS/Wasm: we don't have a reliable common signal; emit a constant
 *   `true` so UI doesn't get false negatives in dev/web. A future JS impl
 *   can subscribe to `window.online/offline`.
 *
 * The `StateFlow` always has an initial value so collectors never block.
 */
expect class Connectivity {
    val isOnline: StateFlow<Boolean>
}

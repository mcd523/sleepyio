package com.sleepyio.sleepyio.platform

/**
 * Process-scoped singleton holder for [PlatformCapabilities] on Android.
 *
 * Rationale: `commonMain` composables reach capabilities via
 * [LocalPlatformCapabilities], but the wiring (`CompositionLocalProvider(...)`
 * around `App()`) is a separate, follow-up task — per the workstream scope
 * we do NOT modify `App.kt`. This holder lets the capability surface be
 * constructed from `MainActivity.onCreate` today, ready to be injected
 * into a future `CompositionLocalProvider` call.
 *
 * The holder is intentionally NOT a Dagger / Hilt / Koin container —
 * the capability count is bounded, the lifetime is the process, and we
 * avoid a new dependency.
 */
object PlatformCapabilitiesHolder {
    @Volatile
    private var instance: PlatformCapabilities? = null

    fun install(capabilities: PlatformCapabilities) {
        instance = capabilities
    }

    fun get(): PlatformCapabilities =
        instance
            ?: error("PlatformCapabilitiesHolder.install(...) was not called before first use.")
}

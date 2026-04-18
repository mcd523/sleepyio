package com.sleepyio.sleepyio

import androidx.compose.ui.window.ComposeUIViewController
import com.sleepyio.sleepyio.platform.PlatformCapabilities

/**
 * iOS entry point. Constructs the process-scoped [PlatformCapabilities]
 * once and hands off to the shared `App()` composable.
 *
 * The `CompositionLocalProvider(LocalPlatformCapabilities provides …)`
 * wiring around `App()` is intentionally deferred to a follow-up — this
 * workstream delivers the plumbing, not the wiring (see
 * MOBILE_ARCHITECTURE.md).
 */
@Suppress("unused") // Called from Swift via the generated Kotlin/Native framework.
val platformCapabilities: PlatformCapabilities by lazy { PlatformCapabilities() }

fun MainViewController() = ComposeUIViewController {
    // Touch the lazy so the capability surface is initialised eagerly
    // on first view-controller build.
    platformCapabilities
    App()
}

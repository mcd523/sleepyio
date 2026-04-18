package com.sleepyio.sleepyio

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.fragment.app.FragmentActivity
import com.sleepyio.sleepyio.platform.DeepLink
import com.sleepyio.sleepyio.platform.DeepLinkParser
import com.sleepyio.sleepyio.platform.PlatformCapabilities
import com.sleepyio.sleepyio.platform.PlatformCapabilitiesHolder

/**
 * Host activity for the compose UI.
 *
 * Extends [FragmentActivity] (superclass of the old ComponentActivity
 * parent on recent AndroidX) so `androidx.biometric:BiometricPrompt`
 * can attach. This is the minimal platform-entry-point edit permitted
 * by the mobile-capabilities workstream — `setContent { App() }` itself
 * is untouched.
 */
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Install the process-scoped capability surface. Composables reach it
        // via com.sleepyio.sleepyio.platform.LocalPlatformCapabilities — the
        // CompositionLocalProvider wiring is intentionally left for a
        // follow-up (we don't modify App.kt in this workstream).
        PlatformCapabilitiesHolder.install(
            PlatformCapabilities(
                context = applicationContext,
                activityProvider = { this },
            ),
        )

        pendingDeepLink = extractDeepLink(intent)

        setContent {
            App()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingDeepLink = extractDeepLink(intent)
    }

    private fun extractDeepLink(intent: Intent?): DeepLink? {
        val data = intent?.data?.toString() ?: return null
        return DeepLinkParser.parse(data)
    }

    companion object {
        /**
         * Last-seen inbound deep link, observable from shared code once the
         * `LocalPlatformCapabilities` wiring lands. Kept as a simple
         * `@Volatile` var to avoid pulling a DI container in before we need one.
         */
        @Volatile
        var pendingDeepLink: DeepLink? = null
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}

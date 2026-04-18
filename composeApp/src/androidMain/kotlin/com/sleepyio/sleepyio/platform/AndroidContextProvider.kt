package com.sleepyio.sleepyio.platform

import android.content.Context
import androidx.fragment.app.FragmentActivity

/**
 * Process-scoped holder for the Android [Context] and current
 * [FragmentActivity]. Used by the `actual class` implementations of each
 * capability so their primary constructors can stay no-arg and match the
 * common `expect class`es (which declare an implicit no-arg constructor).
 *
 * Wired from `MainActivity.onCreate` via [install]. Never read from
 * `commonMain` — the whole point is to keep Context behind the
 * platform boundary.
 */
object AndroidContextProvider {

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var activityRef: FragmentActivity? = null

    fun install(context: Context, activityProvider: () -> FragmentActivity?) {
        appContext = context.applicationContext
        activityRef = activityProvider()
        currentActivity = activityProvider
    }

    @Volatile
    private var currentActivity: () -> FragmentActivity? = { null }

    fun requireContext(): Context =
        appContext ?: error(
            "AndroidContextProvider.install(...) must be called before any " +
                "com.sleepyio.sleepyio.platform.* capability is used.",
        )

    fun currentActivityOrNull(): FragmentActivity? = currentActivity()
}

package com.sleepyio.sleepyio.platform

import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.prefs.Preferences

/**
 * Desktop [SecureStorage] fallback.
 *
 * Backed by `java.util.prefs.Preferences` — NOT encrypted at rest.
 * Suitable for local dev and staging; production desktop must migrate
 * to an OS-native secret store (macOS Keychain via `security`,
 * Windows DPAPI, libsecret on Linux). We log a single warning on
 * construction so the downgrade is visible in logs.
 */
actual class SecureStorage {
    private val prefs: Preferences = Preferences.userRoot().node("com/sleepyio/sleepyio/secure")

    init {
        logger.warn { "SecureStorage on JVM uses java.util.prefs — values are NOT encrypted at rest." }
    }

    actual suspend fun put(key: String, value: String) {
        prefs.put(key, value)
    }

    actual suspend fun get(key: String): String? = prefs.get(key, null)

    actual suspend fun remove(key: String) {
        prefs.remove(key)
    }

    private companion object {
        val logger = KotlinLogging.logger {}
    }
}

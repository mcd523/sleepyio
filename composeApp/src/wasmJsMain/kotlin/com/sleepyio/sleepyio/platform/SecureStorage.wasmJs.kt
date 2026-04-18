package com.sleepyio.sleepyio.platform

import kotlinx.browser.localStorage

/**
 * Wasm browser [SecureStorage] — `localStorage`. Same caveat as jsMain:
 * NOT encrypted.
 */
actual class SecureStorage {
    actual suspend fun put(key: String, value: String) {
        localStorage.setItem(key, value)
    }

    actual suspend fun get(key: String): String? = localStorage.getItem(key)

    actual suspend fun remove(key: String) {
        localStorage.removeItem(key)
    }
}

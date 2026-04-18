package com.sleepyio.sleepyio.platform

import kotlinx.browser.localStorage

/**
 * Browser [SecureStorage] backed by `localStorage`. DEPRECATED from day one:
 * `localStorage` is plain-text and readable by any script on the origin.
 * Use only for non-sensitive session hints (e.g. "last selected league")
 * until a WebAuthn-gated storage scheme is added.
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

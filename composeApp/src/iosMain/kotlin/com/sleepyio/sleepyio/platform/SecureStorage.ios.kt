package com.sleepyio.sleepyio.platform

import platform.Foundation.NSUserDefaults

/**
 * iOS [SecureStorage].
 *
 * Production impl should target the Keychain via `Security.framework`
 * (`SecItemAdd` / `SecItemCopyMatching` / `SecItemDelete`) with
 * `kSecAttrAccessibleWhenUnlockedThisDeviceOnly` and an app-group
 * `kSecAttrAccessGroup` so a future share-extension can read the same
 * bucket. See MOBILE_ARCHITECTURE.md for the target API surface.
 *
 * This is a documented STUB: it uses `NSUserDefaults` so the capability
 * is exercisable end-to-end in dev builds without us hand-rolling cinterop
 * bindings for `Security.framework` inside the sandbox. The KDoc above
 * is the contract a production follow-up must satisfy; the call-site
 * surface (`suspend put/get/remove`) does not change.
 *
 * NOTE: NSUserDefaults is NOT encrypted. Do not ship this to the App
 * Store — flip to Keychain before release.
 */
actual class SecureStorage {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual suspend fun put(key: String, value: String) {
        defaults.setObject(value, forKey = key)
    }

    actual suspend fun get(key: String): String? =
        defaults.stringForKey(key)

    actual suspend fun remove(key: String) {
        defaults.removeObjectForKey(key)
    }
}

package com.sleepyio.sleepyio.platform

/**
 * Encrypted key/value store for small secrets: Sleeper username/cookies,
 * league-scoped auth tokens, biometric-gated feature flags.
 *
 * Why expect/actual instead of a common implementation:
 * - Android wants EncryptedSharedPreferences backed by AndroidKeyStore.
 * - iOS wants Keychain with access groups (for a future share-extension).
 * - Desktop has no OS-standard secret store; we fall back to java.util.prefs
 *   and log a dev-only warning so production builds never silently leak.
 * - Browsers only have `localStorage`, which is plain-text — we surface that
 *   limitation by documenting it, not by throwing.
 *
 * API is `suspend` so native implementations can do IPC / keystore work
 * off the main thread without each call-site re-wrapping in `withContext`.
 */
expect class SecureStorage {
    /** Store (or overwrite) a value. Empty string is a valid value; null is not. */
    suspend fun put(key: String, value: String)

    /** Read a value. Returns null if the key is absent or decryption fails. */
    suspend fun get(key: String): String?

    /** Remove a key. No-op if absent. */
    suspend fun remove(key: String)
}

package com.sleepyio.sleepyio.platform

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android [SecureStorage] backed by EncryptedSharedPreferences
 * + AndroidKeyStore-managed master key.
 *
 * Notes:
 * - The underlying prefs file name is `sleepyio_secure`; don't rename it
 *   without a migration — existing installs would silently lose secrets.
 * - Keys and values are both encrypted (AES-256 GCM), so a rooted device
 *   still has to break the hardware-backed keystore to exfiltrate.
 * - All disk I/O is routed to `Dispatchers.IO`; callers on the main
 *   thread are safe.
 */
actual class SecureStorage(context: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context.applicationContext,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    actual suspend fun put(key: String, value: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(key, value).apply()
    }

    actual suspend fun get(key: String): String? = withContext(Dispatchers.IO) {
        prefs.getString(key, null)
    }

    actual suspend fun remove(key: String) = withContext(Dispatchers.IO) {
        prefs.edit().remove(key).apply()
    }

    private companion object {
        const val PREFS_NAME = "sleepyio_secure"
    }
}

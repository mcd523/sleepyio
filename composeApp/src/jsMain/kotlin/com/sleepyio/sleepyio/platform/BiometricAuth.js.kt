package com.sleepyio.sleepyio.platform

/**
 * Browser biometric fallback. WebAuthn could satisfy this but needs a
 * server-side verifier; deferred. Today: UNAVAILABLE.
 */
actual class BiometricAuth {
    actual suspend fun authenticate(reason: String): AuthResult = AuthResult.UNAVAILABLE
}

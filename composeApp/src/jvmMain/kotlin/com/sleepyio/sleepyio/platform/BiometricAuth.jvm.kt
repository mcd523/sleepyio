package com.sleepyio.sleepyio.platform

/**
 * Desktop biometric fallback. Touch-ID on macOS is reachable via
 * `LocalAuthentication` through JNI but that's out of scope; for now we
 * return UNAVAILABLE so the UI can fall through to a password prompt.
 */
actual class BiometricAuth {
    actual suspend fun authenticate(reason: String): AuthResult = AuthResult.UNAVAILABLE
}

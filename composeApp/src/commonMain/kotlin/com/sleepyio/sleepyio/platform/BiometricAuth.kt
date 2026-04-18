package com.sleepyio.sleepyio.platform

/**
 * Face-ID / Touch-ID / fingerprint gate for sensitive actions
 * (e.g. revealing league auth tokens, confirming a trade proposal).
 *
 * Why expect/actual:
 * - Android: `BiometricPrompt` via `androidx.biometric` (API 28+; older
 *   devices resolve to UNAVAILABLE).
 * - iOS: `LAContext` + `LocalAuthentication.framework`.
 * - Desktop/Web: no standard biometric surface; immediately return
 *   `UNAVAILABLE` so the UI can fall through to a password/PIN.
 *
 * The API intentionally exposes no BiometryType — the UI should ask "can
 * you verify it's you?" without caring which modality the user enrolled.
 */
expect class BiometricAuth {
    /**
     * Present the biometric prompt. [reason] is shown to the user (Apple's
     * HIG and Google's guidelines both require a justification string).
     * Always returns — never throws.
     */
    suspend fun authenticate(reason: String): AuthResult
}

enum class AuthResult {
    SUCCESS,
    USER_CANCELLED,
    UNAVAILABLE,
    FAILED,
}

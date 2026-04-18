package com.sleepyio.sleepyio.platform

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Android [BiometricAuth] backed by `androidx.biometric:BiometricPrompt`.
 *
 * Constraints:
 * - Needs a [FragmentActivity] host. The [activityProvider] is called
 *   lazily at prompt time so the capability can be constructed before
 *   any activity exists.
 * - API 28 required; older devices return UNAVAILABLE synchronously
 *   (BiometricManager tells us via `canAuthenticate`).
 *
 * Caveats:
 * - We only ask for `BIOMETRIC_STRONG | DEVICE_CREDENTIAL` (Class 3 +
 *   PIN/pattern fallback). Weak biometrics are not accepted because
 *   we're gating revealable tokens.
 */
actual class BiometricAuth(
    private val context: Context,
    private val activityProvider: () -> FragmentActivity?,
) {

    actual suspend fun authenticate(reason: String): AuthResult {
        val manager = BiometricManager.from(context)
        val allowed = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        when (manager.canAuthenticate(allowed)) {
            BiometricManager.BIOMETRIC_SUCCESS -> Unit
            else -> return AuthResult.UNAVAILABLE
        }
        val activity = activityProvider() ?: return AuthResult.UNAVAILABLE

        return suspendCancellableCoroutine { cont ->
            val executor = ContextCompat.getMainExecutor(context)
            val prompt = BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(
                        result: BiometricPrompt.AuthenticationResult,
                    ) {
                        if (cont.isActive) cont.resume(AuthResult.SUCCESS)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        val mapped = when (errorCode) {
                            BiometricPrompt.ERROR_USER_CANCELED,
                            BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                            BiometricPrompt.ERROR_CANCELED -> AuthResult.USER_CANCELLED
                            BiometricPrompt.ERROR_HW_NOT_PRESENT,
                            BiometricPrompt.ERROR_HW_UNAVAILABLE,
                            BiometricPrompt.ERROR_NO_BIOMETRICS -> AuthResult.UNAVAILABLE
                            else -> AuthResult.FAILED
                        }
                        if (cont.isActive) cont.resume(mapped)
                    }

                    override fun onAuthenticationFailed() {
                        // Single failed attempt — don't resume; user may retry.
                    }
                },
            )

            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Verify it's you")
                .setSubtitle(reason)
                .setAllowedAuthenticators(allowed)
                .build()

            prompt.authenticate(info)
        }
    }
}

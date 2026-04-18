package com.sleepyio.sleepyio.platform

import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAErrorUserCancel
import platform.LocalAuthentication.LAErrorUserFallback
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthentication

/**
 * iOS [BiometricAuth] backed by `LAContext`.
 *
 * Policy is `LAPolicyDeviceOwnerAuthentication` (Face/Touch ID with
 * passcode fallback) — mirrors the Android "BIOMETRIC_STRONG |
 * DEVICE_CREDENTIAL" choice so the two platforms gate the same
 * trust level.
 *
 * `NSFaceIDUsageDescription` MUST be set in the host app's Info.plist
 * or iOS will kill the app when we invoke biometrics. See
 * MOBILE_ARCHITECTURE.md for the exact key.
 */
actual class BiometricAuth {

    actual suspend fun authenticate(reason: String): AuthResult {
        val context = LAContext()
        val canEvaluate = context.canEvaluatePolicy(LAPolicyDeviceOwnerAuthentication, null)
        if (!canEvaluate) return AuthResult.UNAVAILABLE

        return suspendCancellableCoroutine { cont ->
            context.evaluatePolicy(
                policy = LAPolicyDeviceOwnerAuthentication,
                localizedReason = reason,
            ) { success, error ->
                if (!cont.isActive) return@evaluatePolicy
                val result = when {
                    success -> AuthResult.SUCCESS
                    error?.code == LAErrorUserCancel -> AuthResult.USER_CANCELLED
                    error?.code == LAErrorUserFallback -> AuthResult.USER_CANCELLED
                    else -> AuthResult.FAILED
                }
                cont.resume(result)
            }
        }
    }
}

package com.sleepyio.sleepyio.platform

actual class BiometricAuth {
    actual suspend fun authenticate(reason: String): AuthResult = AuthResult.UNAVAILABLE
}

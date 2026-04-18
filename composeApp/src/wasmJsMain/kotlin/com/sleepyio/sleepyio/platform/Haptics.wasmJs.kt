package com.sleepyio.sleepyio.platform

/** Wasm browser no-op haptics. */
actual class Haptics {
    actual fun lightTap() = Unit
    actual fun success() = Unit
    actual fun warning() = Unit
    actual fun error() = Unit
}

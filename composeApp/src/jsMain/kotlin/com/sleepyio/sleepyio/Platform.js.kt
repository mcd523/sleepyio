package com.sleepyio.sleepyio

import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.js.Js

class JsPlatform : Platform {
    override val name: String = "Web with Kotlin/JS"
    override val clientEngine: HttpClientEngineFactory<HttpClientEngineConfig>
        get() = Js
}

actual fun getPlatform(): Platform = JsPlatform()
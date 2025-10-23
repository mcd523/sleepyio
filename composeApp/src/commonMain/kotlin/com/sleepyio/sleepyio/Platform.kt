package com.sleepyio.sleepyio

import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.cio.CIO

interface Platform {
    val name: String
    val clientEngine: HttpClientEngineFactory<HttpClientEngineConfig>
        get() = CIO
}

expect fun getPlatform(): Platform
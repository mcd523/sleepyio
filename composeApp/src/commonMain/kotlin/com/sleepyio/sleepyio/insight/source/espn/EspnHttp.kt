package com.sleepyio.sleepyio.insight.source.espn

import com.sleepyio.sleepyio.getPlatform
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Shared Ktor [HttpClient] for ESPN's hidden JSON APIs.
 *
 * Kept internal to the `espn` package so every ESPN-backed source goes through
 * the same engine/content-negotiation config (mirrors the pattern used by
 * [com.sleepyio.sleepyio.client.SleeperClient]).
 */
internal object EspnHttp {
    val json: Json = Json {
        prettyPrint = false
        isLenient = true
        ignoreUnknownKeys = true
    }

    val client: HttpClient = HttpClient(getPlatform().clientEngine) {
        install(ContentNegotiation) {
            json(json)
        }
    }
}

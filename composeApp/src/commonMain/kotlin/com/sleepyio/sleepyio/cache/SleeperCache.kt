package com.sleepyio.sleepyio.cache

import com.sleepyio.sleepyio.client.SleeperClient
import com.sleepyio.sleepyio.client.model.player.SleeperPlayer
import eu.vendeli.rethis.ReThis
import eu.vendeli.rethis.command.generic.del
import eu.vendeli.rethis.command.serde.set
import eu.vendeli.rethis.command.serde.get
import eu.vendeli.rethis.shared.types.DataProcessingException
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

object SleeperCache {
    private val logger = KotlinLogging.logger("SleeperCache")
    private val client = ReThis()
    private val refreshPeriod = 24.hours

    @OptIn(ExperimentalTime::class)
    suspend fun init() {
        val canary = "canary"
        val lastUpdate = try {
            client.get<Instant>(canary)
        } catch (e: DataProcessingException) {
            logger.warn(e) { "Unable to retrieve canary, removing current value" }
            client.del(canary)
            null
        }
        // if lastUpdate is less than 24 hours ago, skip initialization
        if (lastUpdate != null && Clock.System.now() - lastUpdate < refreshPeriod) {
            val nextRefresh = lastUpdate + refreshPeriod
            logger.info { "SleeperCache already initialized. Next refresh will occur at $nextRefresh" }
            return
        }

        logger.info { "Initializing SleeperCache..." }
        val players = SleeperClient.getAllPlayers()
        if (players.isEmpty()) {
            logger.warn { "No players fetched from SleeperClient. Cache initialization aborted." }
            return
        }
        players.forEach { (id, player) ->
            client.set(id, player)
        }
        logger.info { "Cached ${players.size} players." }
        client.set(canary, Clock.System.now())
    }

    suspend fun getPlayer(playerId: String): SleeperPlayer? {
        return client.get<SleeperPlayer>(playerId)
    }
}
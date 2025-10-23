package com.sleepyio.sleepyio

import com.sleepyio.sleepyio.client.SleeperClient

class Greeting(private val client: SleeperClient) {
    private val platform = getPlatform()

    suspend fun greet(): String {
        return "Hello, ${client.getUser().displayName}!"
    }
}
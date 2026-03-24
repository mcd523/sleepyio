package com.sleepyio.sleepyio.intel

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class RateLimiter(
    private val maxConcurrent: Int = 10,
    private val batchDelayMs: Long = 100
) {
    private val semaphore = Semaphore(maxConcurrent)

    suspend fun <T> withLimit(block: suspend () -> T): T {
        return semaphore.withPermit { block() }
    }

    suspend fun <T, R> executeBatch(items: List<T>, action: suspend (T) -> R): List<R> {
        val results = mutableListOf<R>()
        items.chunked(maxConcurrent).forEachIndexed { index, batch ->
            if (index > 0) {
                delay(batchDelayMs)
            }
            val batchResults = batch.map { item ->
                semaphore.withPermit { action(item) }
            }
            results.addAll(batchResults)
        }
        return results
    }
}

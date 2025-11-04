package com.sleepyio.sleepyio.util

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

suspend fun <T, R> Iterable<T>.pmap(
    transform: suspend (T) -> R
): List<R> = coroutineScope {
    map { item ->
        async {
            transform(item)
        }
    }.awaitAll()
}

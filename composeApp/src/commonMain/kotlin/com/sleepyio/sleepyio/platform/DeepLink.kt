package com.sleepyio.sleepyio.platform

/**
 * Parsed representation of an inbound `sleepyio://…` URL.
 *
 * Scheme grammar (see docs/mobile/MOBILE_ARCHITECTURE.md for the full table):
 *
 * ```
 * sleepyio://advisor/<leagueId>/<rosterId>
 * sleepyio://players/<playerId>[?week=<n>]
 * sleepyio://waivers/<leagueId>/<rosterId>/<week>
 * ```
 *
 * Parsing is platform-agnostic so the same result object can come from an
 * Android `Intent.data`, an iOS `openURL` callback, a web `window.location`,
 * or a push-notification payload.
 */
sealed interface DeepLink {
    data class PlayerInsight(val playerId: String, val week: Int?) : DeepLink
    data class Advisor(val leagueId: Long, val rosterId: Long) : DeepLink
    data class Waivers(val leagueId: Long, val rosterId: Long, val week: Int) : DeepLink
}

/**
 * Pure URI parser. No platform dependencies — unit-testable from
 * `commonTest`. Returns null for anything we don't recognise so callers
 * can fall through to a default route.
 */
object DeepLinkParser {
    private const val SCHEME = "sleepyio"

    fun parse(uri: String): DeepLink? {
        if (uri.isBlank()) return null
        val scheme = uri.substringBefore("://", missingDelimiterValue = "")
        if (scheme != SCHEME) return null

        val afterScheme = uri.substringAfter("://")
        val pathAndQuery = afterScheme.trimStart('/')
        val pathPart = pathAndQuery.substringBefore('?')
        val queryPart = if ('?' in pathAndQuery) pathAndQuery.substringAfter('?') else ""
        val segments = pathPart.split('/').filter { it.isNotEmpty() }
        if (segments.isEmpty()) return null

        val query = parseQuery(queryPart)

        return when (segments[0]) {
            "advisor" -> {
                if (segments.size < 3) return null
                val leagueId = segments[1].toLongOrNull() ?: return null
                val rosterId = segments[2].toLongOrNull() ?: return null
                DeepLink.Advisor(leagueId, rosterId)
            }
            "players" -> {
                if (segments.size < 2) return null
                val playerId = segments[1]
                val week = query["week"]?.toIntOrNull()
                DeepLink.PlayerInsight(playerId, week)
            }
            "waivers" -> {
                if (segments.size < 4) return null
                val leagueId = segments[1].toLongOrNull() ?: return null
                val rosterId = segments[2].toLongOrNull() ?: return null
                val week = segments[3].toIntOrNull() ?: return null
                DeepLink.Waivers(leagueId, rosterId, week)
            }
            else -> null
        }
    }

    private fun parseQuery(query: String): Map<String, String> {
        if (query.isEmpty()) return emptyMap()
        return query.split('&')
            .mapNotNull { pair ->
                val idx = pair.indexOf('=')
                if (idx < 0) return@mapNotNull null
                val k = pair.substring(0, idx)
                val v = pair.substring(idx + 1)
                if (k.isEmpty()) null else k to v
            }
            .toMap()
    }
}

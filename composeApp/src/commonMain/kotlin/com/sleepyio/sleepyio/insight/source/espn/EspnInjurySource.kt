package com.sleepyio.sleepyio.insight.source.espn

/*
 * ESPN hidden injury endpoint:
 *     https://site.api.espn.com/apis/site/v2/sports/football/nfl/injuries
 *
 * The JSON shape is roughly:
 *     { "injuries": [ { "team": {...}, "injuries": [
 *         { "athlete": { "id": "...", ... },
 *           "status": "Out",
 *           "details": { "type": "Hamstring", "returnDate": "..." },
 *           "date": "ISO-8601",
 *           "shortComment": "...",
 *           "longComment": "..." } ] } ] }
 *
 * We flatten that into per-player [InjuryReport] rows. Week is currently not a
 * filter ESPN exposes directly; we accept [week] to match the interface and
 * leave filtering to the aggregator if needed.
 */

import com.sleepyio.sleepyio.insight.source.InjurySource
import com.sleepyio.sleepyio.insight.source.InjuryReport
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * ESPN-backed [InjurySource]. Parses the public injuries endpoint into the
 * neutral [InjuryReport] wire shape. Fails soft — logs and returns an empty
 * list on any network / parse error so the aggregator just loses the injury
 * factor rather than the whole response.
 */
class EspnInjurySource : InjurySource {
    private val logger = KotlinLogging.logger {}

    override suspend fun fetchInjuries(week: Int): List<InjuryReport> {
        return try {
            val response: EspnInjuriesResponse = EspnHttp.client.get(URL).body()
            response.injuries
                .orEmpty()
                .flatMap { team -> team.injuries.orEmpty() }
                .mapNotNull { raw ->
                    val playerId = raw.athlete?.id ?: return@mapNotNull null
                    InjuryReport(
                        playerId = playerId,
                        designation = raw.status,
                        bodyPart = raw.details?.type,
                        practiceStatus = raw.details?.returnDate,
                        lastUpdatedEpochMs = null, // ESPN returns ISO strings; parsing intentionally skipped
                    )
                }
        } catch (e: Exception) {
            logger.error(e) { "EspnInjurySource failed for week=$week; degrading to empty list" }
            emptyList()
        }
    }

    companion object {
        private const val URL =
            "https://site.api.espn.com/apis/site/v2/sports/football/nfl/injuries"
    }

    @Serializable
    private data class EspnInjuriesResponse(
        val injuries: List<EspnTeamInjuries>? = null,
    )

    @Serializable
    private data class EspnTeamInjuries(
        val injuries: List<EspnInjury>? = null,
    )

    @Serializable
    private data class EspnInjury(
        val status: String? = null,
        val date: String? = null,
        @SerialName("shortComment") val shortComment: String? = null,
        @SerialName("longComment") val longComment: String? = null,
        val details: EspnInjuryDetails? = null,
        val athlete: EspnAthlete? = null,
    )

    @Serializable
    private data class EspnInjuryDetails(
        val type: String? = null,
        @SerialName("returnDate") val returnDate: String? = null,
    )

    @Serializable
    private data class EspnAthlete(
        val id: String? = null,
        val displayName: String? = null,
    )
}

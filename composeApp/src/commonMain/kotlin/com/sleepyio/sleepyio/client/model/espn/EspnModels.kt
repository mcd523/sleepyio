package com.sleepyio.sleepyio.client.model.espn

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class EspnLeagueResponse(
    val id: Long,
    val scoringPeriodId: Int = 0,
    val seasonId: Int = 0,
    val status: EspnLeagueStatus? = null,
    val settings: EspnLeagueSettings? = null,
    val teams: List<EspnTeam> = emptyList(),
    val schedule: List<EspnScheduleItem> = emptyList(),
    val members: List<EspnMember> = emptyList()
)

@Serializable
data class EspnLeagueStatus(
    val currentMatchupPeriod: Int = 0,
    val isActive: Boolean = false,
    val latestScoringPeriod: Int = 0
)

@Serializable
data class EspnLeagueSettings(
    val name: String = "",
    val size: Int = 0,
    val rosterSettings: EspnRosterSettings? = null,
    val scoringSettings: EspnScoringSettings? = null
)

@Serializable
data class EspnRosterSettings(
    val lineupSlotCounts: Map<String, Int> = emptyMap()
)

@Serializable
data class EspnScoringSettings(
    val scoringItems: List<EspnScoringItem> = emptyList()
)

@Serializable
data class EspnScoringItem(
    val statId: Int = 0,
    val pointsOverrides: Map<String, Double>? = null,
    val points: Double = 0.0
)

@Serializable
data class EspnTeam(
    val id: Int = 0,
    val name: String? = null,
    val abbrev: String? = null,
    val logo: String? = null,
    val record: EspnRecord? = null,
    val roster: EspnRoster? = null,
    val points: Double = 0.0,
    val primaryOwner: String? = null
)

@Serializable
data class EspnRecord(
    val overall: EspnRecordDetail? = null
)

@Serializable
data class EspnRecordDetail(
    val wins: Int = 0,
    val losses: Int = 0,
    val ties: Int = 0,
    val pointsFor: Double = 0.0,
    val pointsAgainst: Double = 0.0
)

@Serializable
data class EspnRoster(
    val entries: List<EspnRosterEntry> = emptyList()
)

@Serializable
data class EspnRosterEntry(
    val playerId: Int = 0,
    val lineupSlotId: Int = 0,
    val playerPoolEntry: EspnPlayerPoolEntry? = null
)

@Serializable
data class EspnPlayerPoolEntry(
    val player: EspnPlayer? = null,
    val ratings: Map<String, EspnPlayerRating>? = null
)

@Serializable
data class EspnPlayer(
    val id: Int = 0,
    val fullName: String = "",
    val firstName: String? = null,
    val lastName: String? = null,
    val proTeamId: Int = 0,
    val defaultPositionId: Int = 0,
    val injuryStatus: String? = null,
    val injured: Boolean = false,
    val stats: List<EspnPlayerStat> = emptyList()
)

@Serializable
data class EspnPlayerStat(
    val id: String? = null,
    val scoringPeriodId: Int = 0,
    val seasonId: Int = 0,
    val statSourceId: Int = 0, // 0 = actual, 1 = projected
    val appliedTotal: Double = 0.0,
    val stats: Map<String, Double> = emptyMap()
)

@Serializable
data class EspnPlayerRating(
    val positionalRanking: Int = 0,
    val totalRanking: Int = 0,
    val totalRating: Double = 0.0
)

@Serializable
data class EspnScheduleItem(
    val matchupPeriodId: Int = 0,
    val home: EspnScheduleTeam? = null,
    val away: EspnScheduleTeam? = null,
    val winner: String? = null
)

@Serializable
data class EspnScheduleTeam(
    val teamId: Int = 0,
    val totalPoints: Double = 0.0,
    val rosterForCurrentScoringPeriod: EspnRoster? = null
)

@Serializable
data class EspnMember(
    val id: String = "",
    val displayName: String = "",
    val firstName: String? = null,
    val lastName: String? = null
)

// ESPN position ID to name mapping
object EspnPositionMap {
    val positionNames = mapOf(
        1 to "QB", 2 to "RB", 3 to "WR", 4 to "TE",
        5 to "K", 16 to "D/ST"
    )

    val slotNames = mapOf(
        0 to "QB", 2 to "RB", 4 to "WR", 6 to "TE",
        17 to "K", 16 to "D/ST", 20 to "Bench", 21 to "IR",
        23 to "FLEX", 24 to "OP"
    )

    fun positionName(id: Int): String = positionNames[id] ?: "Unknown"
    fun slotName(id: Int): String = slotNames[id] ?: "Unknown"
    fun isStarter(slotId: Int): Boolean = slotId != 20 && slotId != 21
}

// ESPN NFL team ID to abbreviation mapping
object EspnTeamMap {
    val teamAbbrevs = mapOf(
        1 to "ATL", 2 to "BUF", 3 to "CHI", 4 to "CIN",
        5 to "CLE", 6 to "DAL", 7 to "DEN", 8 to "DET",
        9 to "GB", 10 to "TEN", 11 to "IND", 12 to "KC",
        13 to "LV", 14 to "LAR", 15 to "MIA", 16 to "MIN",
        17 to "NE", 18 to "NO", 19 to "NYG", 20 to "NYJ",
        21 to "PHI", 22 to "ARI", 23 to "PIT", 24 to "LAC",
        25 to "SF", 26 to "SEA", 27 to "TB", 28 to "WAS",
        29 to "CAR", 30 to "JAX", 33 to "BAL", 34 to "HOU"
    )

    fun teamAbbrev(id: Int): String = teamAbbrevs[id] ?: "FA"
}

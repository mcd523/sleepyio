package com.sleepyio.sleepyio.model

import com.sleepyio.sleepyio.client.model.espn.EspnLeagueResponse
import com.sleepyio.sleepyio.client.model.espn.EspnPositionMap
import com.sleepyio.sleepyio.client.model.espn.EspnTeamMap
import com.sleepyio.sleepyio.client.model.league.SleeperLeague
import com.sleepyio.sleepyio.client.model.league.SleeperMatchup
import com.sleepyio.sleepyio.client.model.league.SleeperRoster
import com.sleepyio.sleepyio.client.model.player.SleeperPlayer
import com.sleepyio.sleepyio.client.model.stats.PlayerStats
import com.sleepyio.sleepyio.client.model.user.SleeperUser

enum class Platform { SLEEPER, ESPN }

data class UnifiedLeague(
    val id: String,
    val name: String,
    val platform: Platform,
    val season: String,
    val size: Int,
    val teams: List<UnifiedTeam> = emptyList(),
    val currentWeek: Int = 1
)

data class UnifiedTeam(
    val id: String,
    val name: String,
    val ownerName: String,
    val wins: Int = 0,
    val losses: Int = 0,
    val ties: Int = 0,
    val pointsFor: Double = 0.0,
    val pointsAgainst: Double = 0.0,
    val roster: List<UnifiedPlayer> = emptyList(),
    val starters: List<UnifiedPlayer> = emptyList(),
    val bench: List<UnifiedPlayer> = emptyList()
)

data class UnifiedPlayer(
    val id: String,
    val name: String,
    val position: String,
    val team: String?, // NFL team abbreviation
    val isStarter: Boolean,
    val actualPoints: Double? = null,
    val projectedPoints: Double? = null,
    val injuryStatus: String? = null,
    val stats: PlayerStats? = null
)

data class UnifiedMatchup(
    val week: Int,
    val team1: UnifiedTeam,
    val team2: UnifiedTeam?,
    val team1Points: Double,
    val team2Points: Double
)

// Mappers from platform-specific to unified models
object SleeperMapper {
    suspend fun toUnifiedLeague(
        league: SleeperLeague,
        rosters: List<SleeperRoster>,
        users: List<SleeperUser>,
        playerCache: suspend (String) -> SleeperPlayer?,
        projections: Map<String, PlayerStats> = emptyMap(),
        stats: Map<String, PlayerStats> = emptyMap()
    ): UnifiedLeague {
        val userMap = users.associateBy { it.userId }

        val teams = rosters.map { roster ->
            val user = roster.ownerId?.let { userMap[it] }
            val ownerName = user?.displayName ?: user?.userName ?: "Team ${roster.rosterId}"

            val allPlayers = roster.players.map { playerId ->
                val player = playerCache(playerId)
                val isStarter = playerId in roster.starters
                UnifiedPlayer(
                    id = playerId,
                    name = if (player != null) "${player.firstName ?: ""} ${player.lastName ?: ""}".trim() else playerId,
                    position = player?.position ?: "N/A",
                    team = player?.team,
                    isStarter = isStarter,
                    actualPoints = stats[playerId]?.fantasyPoints,
                    projectedPoints = projections[playerId]?.fantasyPoints,
                    injuryStatus = player?.injuryStatus
                )
            }

            UnifiedTeam(
                id = roster.rosterId.toString(),
                name = ownerName,
                ownerName = ownerName,
                roster = allPlayers,
                starters = allPlayers.filter { it.isStarter },
                bench = allPlayers.filter { !it.isStarter }
            )
        }

        return UnifiedLeague(
            id = league.leagueId.toString(),
            name = league.leagueName ?: "Unnamed League",
            platform = Platform.SLEEPER,
            season = league.season,
            size = league.leagueSize.toIntOrNull() ?: 0,
            teams = teams
        )
    }

    suspend fun toUnifiedPlayer(
        playerId: String,
        player: SleeperPlayer,
        isStarter: Boolean,
        projections: Map<String, PlayerStats> = emptyMap(),
        stats: Map<String, PlayerStats> = emptyMap()
    ): UnifiedPlayer {
        return UnifiedPlayer(
            id = playerId,
            name = "${player.firstName ?: ""} ${player.lastName ?: ""}".trim(),
            position = player.position ?: "N/A",
            team = player.team,
            isStarter = isStarter,
            actualPoints = stats[playerId]?.fantasyPoints,
            projectedPoints = projections[playerId]?.fantasyPoints,
            injuryStatus = player.injuryStatus,
            stats = stats[playerId]
        )
    }
}

object EspnMapper {
    fun toUnifiedLeague(response: EspnLeagueResponse): UnifiedLeague {
        val memberMap = response.members.associateBy { it.id }

        val teams = response.teams.map { espnTeam ->
            val owner = espnTeam.primaryOwner?.let { memberMap[it] }
            val rosterEntries = espnTeam.roster?.entries ?: emptyList()

            val players = rosterEntries.map { entry ->
                val espnPlayer = entry.playerPoolEntry?.player
                val isStarter = EspnPositionMap.isStarter(entry.lineupSlotId)

                // Get projections (statSourceId=1) and actuals (statSourceId=0)
                val projectedStat = espnPlayer?.stats?.firstOrNull { it.statSourceId == 1 }
                val actualStat = espnPlayer?.stats?.firstOrNull { it.statSourceId == 0 }

                UnifiedPlayer(
                    id = entry.playerId.toString(),
                    name = espnPlayer?.fullName ?: "Unknown",
                    position = EspnPositionMap.positionName(espnPlayer?.defaultPositionId ?: 0),
                    team = EspnTeamMap.teamAbbrev(espnPlayer?.proTeamId ?: 0),
                    isStarter = isStarter,
                    actualPoints = actualStat?.appliedTotal,
                    projectedPoints = projectedStat?.appliedTotal,
                    injuryStatus = espnPlayer?.injuryStatus
                )
            }

            UnifiedTeam(
                id = espnTeam.id.toString(),
                name = espnTeam.name ?: "Team ${espnTeam.id}",
                ownerName = owner?.displayName ?: "Unknown",
                wins = espnTeam.record?.overall?.wins ?: 0,
                losses = espnTeam.record?.overall?.losses ?: 0,
                ties = espnTeam.record?.overall?.ties ?: 0,
                pointsFor = espnTeam.record?.overall?.pointsFor ?: 0.0,
                pointsAgainst = espnTeam.record?.overall?.pointsAgainst ?: 0.0,
                roster = players,
                starters = players.filter { it.isStarter },
                bench = players.filter { !it.isStarter }
            )
        }

        return UnifiedLeague(
            id = response.id.toString(),
            name = response.settings?.name ?: "ESPN League",
            platform = Platform.ESPN,
            season = response.seasonId.toString(),
            size = response.settings?.size ?: response.teams.size,
            teams = teams,
            currentWeek = response.status?.currentMatchupPeriod ?: 1
        )
    }
}

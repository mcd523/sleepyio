package com.sleepyio.sleepyio.client.model.odds

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OddsApiResponse(
    val id: String,
    @SerialName("sport_key")
    val sportKey: String = "",
    @SerialName("sport_title")
    val sportTitle: String = "",
    @SerialName("commence_time")
    val commenceTime: String = "",
    @SerialName("home_team")
    val homeTeam: String = "",
    @SerialName("away_team")
    val awayTeam: String = "",
    val bookmakers: List<Bookmaker> = emptyList()
)

@Serializable
data class Bookmaker(
    val key: String = "",
    val title: String = "",
    val markets: List<Market> = emptyList()
)

@Serializable
data class Market(
    val key: String = "", // "spreads", "totals", "h2h"
    val outcomes: List<Outcome> = emptyList()
)

@Serializable
data class Outcome(
    val name: String = "",
    val price: Int = 0,
    val point: Double? = null // spread or total value
)

// Processed game odds for analysis
data class GameOdds(
    val homeTeam: String,
    val awayTeam: String,
    val homeTeamAbbrev: String,
    val awayTeamAbbrev: String,
    val gameTime: String,
    val spread: Double, // positive = home favored
    val total: Double,
    val homeImpliedTotal: Double,
    val awayImpliedTotal: Double
) {
    val isHighScoring: Boolean get() = total >= 48.0
    val isShootout: Boolean get() = total >= 52.0

    fun impliedTotalForTeam(teamAbbrev: String): Double? = when (teamAbbrev) {
        homeTeamAbbrev -> homeImpliedTotal
        awayTeamAbbrev -> awayImpliedTotal
        else -> null
    }
}

// NFL team name to abbreviation mapping for odds matching
object NflTeamAbbreviations {
    private val nameToAbbrev = mapOf(
        "Arizona Cardinals" to "ARI",
        "Atlanta Falcons" to "ATL",
        "Baltimore Ravens" to "BAL",
        "Buffalo Bills" to "BUF",
        "Carolina Panthers" to "CAR",
        "Chicago Bears" to "CHI",
        "Cincinnati Bengals" to "CIN",
        "Cleveland Browns" to "CLE",
        "Dallas Cowboys" to "DAL",
        "Denver Broncos" to "DEN",
        "Detroit Lions" to "DET",
        "Green Bay Packers" to "GB",
        "Houston Texans" to "HOU",
        "Indianapolis Colts" to "IND",
        "Jacksonville Jaguars" to "JAX",
        "Kansas City Chiefs" to "KC",
        "Las Vegas Raiders" to "LV",
        "Los Angeles Chargers" to "LAC",
        "Los Angeles Rams" to "LAR",
        "Miami Dolphins" to "MIA",
        "Minnesota Vikings" to "MIN",
        "New England Patriots" to "NE",
        "New Orleans Saints" to "NO",
        "New York Giants" to "NYG",
        "New York Jets" to "NYJ",
        "Philadelphia Eagles" to "PHI",
        "Pittsburgh Steelers" to "PIT",
        "San Francisco 49ers" to "SF",
        "Seattle Seahawks" to "SEA",
        "Tampa Bay Buccaneers" to "TB",
        "Tennessee Titans" to "TEN",
        "Washington Commanders" to "WAS"
    )

    fun abbreviation(fullName: String): String =
        nameToAbbrev[fullName] ?: fullName.take(3).uppercase()
}

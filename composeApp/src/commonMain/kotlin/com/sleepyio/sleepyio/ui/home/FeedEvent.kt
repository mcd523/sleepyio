package com.sleepyio.sleepyio.ui.home

import com.sleepyio.sleepyio.client.model.draft.SleeperPick
import com.sleepyio.sleepyio.client.model.player.SleeperPlayer
import com.sleepyio.sleepyio.intel.model.ActivityEvent

sealed class FeedEvent : Comparable<FeedEvent> {
    abstract val leagueId: Long
    abstract val leagueName: String
    abstract val timestamp: Long
    abstract val week: Int

    override fun compareTo(other: FeedEvent): Int =
        other.timestamp.compareTo(this.timestamp) // descending

    data class Transaction(
        override val leagueId: Long,
        override val leagueName: String,
        override val timestamp: Long,
        override val week: Int,
        val event: ActivityEvent
    ) : FeedEvent()

    data class MatchupResult(
        override val leagueId: Long,
        override val leagueName: String,
        override val timestamp: Long,
        override val week: Int,
        val userScore: Float,
        val opponentScore: Float,
        val opponentName: String,
        val won: Boolean,
        val margin: Float
    ) : FeedEvent()

    data class DraftPickEvent(
        override val leagueId: Long,
        override val leagueName: String,
        override val timestamp: Long,
        override val week: Int,
        val player: SleeperPlayer?,
        val playerId: String,
        val round: Long,
        val pickNumber: Long,
        val pickedByName: String
    ) : FeedEvent()

    data class CommissionerAction(
        override val leagueId: Long,
        override val leagueName: String,
        override val timestamp: Long,
        override val week: Int,
        val description: String
    ) : FeedEvent()
}

enum class FeedFilter(val label: String) {
    ALL("All"),
    TRADES("Trades"),
    WAIVERS("Waivers"),
    MATCHUPS("Matchups"),
    DRAFTS("Drafts"),
    COMMISSIONER("Commissioner")
}

fun FeedEvent.matchesFilter(filter: FeedFilter): Boolean = when (filter) {
    FeedFilter.ALL -> true
    FeedFilter.TRADES -> this is FeedEvent.Transaction && this.event.type == com.sleepyio.sleepyio.intel.model.ActivityType.TRADE
    FeedFilter.WAIVERS -> this is FeedEvent.Transaction && this.event.type == com.sleepyio.sleepyio.intel.model.ActivityType.WAIVER_CLAIM
    FeedFilter.MATCHUPS -> this is FeedEvent.MatchupResult
    FeedFilter.DRAFTS -> this is FeedEvent.DraftPickEvent
    FeedFilter.COMMISSIONER -> this is FeedEvent.CommissionerAction
}

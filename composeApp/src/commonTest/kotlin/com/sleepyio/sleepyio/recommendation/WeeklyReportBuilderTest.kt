package com.sleepyio.sleepyio.recommendation

import com.sleepyio.sleepyio.client.model.league.SleeperRoster
import com.sleepyio.sleepyio.insight.model.PlayerInsight
import com.sleepyio.sleepyio.playerInsight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Pure-function tests for [selectLineup].
 *
 * This helper is the beating heart of the Weekly Report: it translates the
 * sea of rostered-player insights into the user's recommended starting
 * lineup. The invariants below come directly from the framework doc's
 * standard roster (QB / 2 RB / 2 WR / TE / FLEX / K / DEF) and from the
 * greedy-projection fill rule documented in the helper's KDoc.
 */
class WeeklyReportBuilderTest {

    private fun roster(players: List<String>): SleeperRoster = SleeperRoster(
        starters = emptyList(),
        rosterId = 1,
        players = players,
        ownerId = null,
        leagueId = 42L,
    )

    /**
     * Framework standard roster: QB -> RB -> RB -> WR -> WR -> TE -> FLEX ->
     * DEF -> K, in that exact order. Any reorder changes the FLEX pick and
     * breaks user expectations.
     */
    @Test
    fun fillsSlotsInFrameworkOrder() {
        val insights = mapOf(
            "qb1" to playerInsight("qb1", position = "QB", medianProjection = 21.0),
            "rb1" to playerInsight("rb1", position = "RB", medianProjection = 18.0),
            "rb2" to playerInsight("rb2", position = "RB", medianProjection = 14.0),
            "wr1" to playerInsight("wr1", position = "WR", medianProjection = 17.0),
            "wr2" to playerInsight("wr2", position = "WR", medianProjection = 12.0),
            "te1" to playerInsight("te1", position = "TE", medianProjection = 10.0),
            "flex_candidate" to playerInsight("flex_candidate", position = "RB", medianProjection = 13.0),
            "k1" to playerInsight("k1", position = "K", medianProjection = 8.0),
            "def1" to playerInsight("def1", position = "DEF", medianProjection = 9.0),
        )
        val lineup = selectLineup(roster(insights.keys.toList()), insights)

        val slots = lineup.map { it.slot }
        assertEquals(listOf("QB", "RB", "RB", "WR", "WR", "TE", "FLEX", "DEF", "K"), slots)
    }

    /**
     * Primary slots must pick the highest-projection eligible player before
     * FLEX — "greedy on projection" is the documented rule.
     */
    @Test
    fun primarySlotsPickHighestProjectionFirst() {
        val insights = mapOf(
            "qb_lo" to playerInsight("qb_lo", position = "QB", medianProjection = 15.0),
            "qb_hi" to playerInsight("qb_hi", position = "QB", medianProjection = 24.0),
            "rb_hi" to playerInsight("rb_hi", position = "RB", medianProjection = 20.0),
            "rb_mid" to playerInsight("rb_mid", position = "RB", medianProjection = 14.0),
            "rb_lo" to playerInsight("rb_lo", position = "RB", medianProjection = 6.0),
            "wr_hi" to playerInsight("wr_hi", position = "WR", medianProjection = 18.0),
            "wr_lo" to playerInsight("wr_lo", position = "WR", medianProjection = 8.0),
            "te1" to playerInsight("te1", position = "TE", medianProjection = 10.0),
            "k1" to playerInsight("k1", position = "K", medianProjection = 9.0),
            "def1" to playerInsight("def1", position = "DEF", medianProjection = 7.0),
        )

        val lineup = selectLineup(roster(insights.keys.toList()), insights)
        val byslot = lineup.associateBy({ it.slot }, { it.playerId })

        assertEquals("qb_hi", byslot["QB"])
        assertEquals("rb_hi", lineup.first { it.slot == "RB" }.playerId)
    }

    /**
     * FLEX must pick the best leftover RB/WR/TE after the primaries have been
     * consumed. Here rb3 is the highest-projection RB/WR/TE not already
     * starting — FLEX must be rb3, not the lower-projection wr3/te2.
     */
    @Test
    fun flexPicksBestRemainingAmongRbWrTe() {
        val insights = mapOf(
            "qb1" to playerInsight("qb1", position = "QB", medianProjection = 20.0),
            // Top 2 RBs fill the RB slots; rb3 is the best FLEX-eligible leftover.
            "rb1" to playerInsight("rb1", position = "RB", medianProjection = 22.0),
            "rb2" to playerInsight("rb2", position = "RB", medianProjection = 19.0),
            "rb3" to playerInsight("rb3", position = "RB", medianProjection = 16.0),
            // Top 2 WRs fill the WR slots; wr3 is lower-projection than rb3.
            "wr1" to playerInsight("wr1", position = "WR", medianProjection = 18.0),
            "wr2" to playerInsight("wr2", position = "WR", medianProjection = 15.0),
            "wr3" to playerInsight("wr3", position = "WR", medianProjection = 9.0),
            // TE starter + a weaker backup TE.
            "te1" to playerInsight("te1", position = "TE", medianProjection = 11.0),
            "te2" to playerInsight("te2", position = "TE", medianProjection = 5.0),
            "k1" to playerInsight("k1", position = "K", medianProjection = 8.0),
            "def1" to playerInsight("def1", position = "DEF", medianProjection = 6.0),
        )
        val lineup = selectLineup(roster(insights.keys.toList()), insights)
        val flex = lineup.firstOrNull { it.slot == "FLEX" }
        assertNotNull(flex, "FLEX slot must be filled when RB/WR/TE candidates remain")
        assertEquals("rb3", flex.playerId, "FLEX must pick highest-projection RB/WR/TE leftover")
    }

    /**
     * No eligible candidate for a slot -> slot is omitted (no crash, no
     * duplicate, no filler). Prevents silent lineup corruption when the
     * roster is short.
     */
    @Test
    fun missingSlotIsOmittedNotDuplicated() {
        val insights: Map<String, PlayerInsight> = mapOf(
            "qb1" to playerInsight("qb1", position = "QB", medianProjection = 20.0),
            "rb1" to playerInsight("rb1", position = "RB", medianProjection = 14.0),
            // No WR, TE, K, DEF in the roster.
        )
        val lineup = selectLineup(roster(insights.keys.toList()), insights)
        val slots = lineup.map { it.slot }

        assertTrue("QB" in slots, "QB must be filled")
        // Each player is started at most once.
        assertEquals(lineup.size, lineup.map { it.playerId }.distinct().size)
        assertTrue("WR" !in slots, "WR slot must not be filled if no WR eligible")
    }
}

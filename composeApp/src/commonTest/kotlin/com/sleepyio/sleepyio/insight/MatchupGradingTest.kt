package com.sleepyio.sleepyio.insight

import com.sleepyio.sleepyio.insight.model.MatchupGrade
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Enforces the DvP-rank -> [MatchupGrade] table from ANALYSIS_FRAMEWORK.md.
 * NFL convention: 1 = toughest defense vs position, 32 = easiest.
 *
 *   1..6   -> NIGHTMARE
 *   7..12  -> TOUGH
 *   13..20 -> NEUTRAL
 *   21..26 -> GOOD
 *   27..32 -> ELITE
 *   null   -> NEUTRAL (fail-soft: unknown opponent -> no signal)
 */
class MatchupGradingTest {

    /** Rank 1 is the toughest matchup in the league — must map to NIGHTMARE. */
    @Test
    fun rank1IsNightmare() {
        assertEquals(MatchupGrade.NIGHTMARE, InsightAggregator.matchupGradeFromRank(1))
    }

    /** Rank 6 is the inclusive upper bound of NIGHTMARE. */
    @Test
    fun rank6IsNightmare() {
        assertEquals(MatchupGrade.NIGHTMARE, InsightAggregator.matchupGradeFromRank(6))
    }

    /** Rank 7 is the inclusive lower bound of TOUGH. */
    @Test
    fun rank7IsTough() {
        assertEquals(MatchupGrade.TOUGH, InsightAggregator.matchupGradeFromRank(7))
    }

    /** Rank 12 is the inclusive upper bound of TOUGH. */
    @Test
    fun rank12IsTough() {
        assertEquals(MatchupGrade.TOUGH, InsightAggregator.matchupGradeFromRank(12))
    }

    /** Rank 13 is the inclusive lower bound of NEUTRAL. */
    @Test
    fun rank13IsNeutral() {
        assertEquals(MatchupGrade.NEUTRAL, InsightAggregator.matchupGradeFromRank(13))
    }

    /** Rank 20 is the inclusive upper bound of NEUTRAL. */
    @Test
    fun rank20IsNeutral() {
        assertEquals(MatchupGrade.NEUTRAL, InsightAggregator.matchupGradeFromRank(20))
    }

    /** Rank 21 is the inclusive lower bound of GOOD. */
    @Test
    fun rank21IsGood() {
        assertEquals(MatchupGrade.GOOD, InsightAggregator.matchupGradeFromRank(21))
    }

    /** Rank 26 is the inclusive upper bound of GOOD. */
    @Test
    fun rank26IsGood() {
        assertEquals(MatchupGrade.GOOD, InsightAggregator.matchupGradeFromRank(26))
    }

    /** Rank 27 is the inclusive lower bound of ELITE. */
    @Test
    fun rank27IsElite() {
        assertEquals(MatchupGrade.ELITE, InsightAggregator.matchupGradeFromRank(27))
    }

    /** Rank 32 is the softest matchup in the league — must map to ELITE. */
    @Test
    fun rank32IsElite() {
        assertEquals(MatchupGrade.ELITE, InsightAggregator.matchupGradeFromRank(32))
    }

    /** Null rank is the fail-soft default; the aggregator must not fabricate a grade. */
    @Test
    fun nullRankIsNeutralFailSoft() {
        assertEquals(MatchupGrade.NEUTRAL, InsightAggregator.matchupGradeFromRank(null))
    }
}

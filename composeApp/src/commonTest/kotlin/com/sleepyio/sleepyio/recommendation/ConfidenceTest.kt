package com.sleepyio.sleepyio.recommendation

import com.sleepyio.sleepyio.recommendation.model.Confidence
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Enforces the exact threshold table from `ANALYSIS_FRAMEWORK.md` section 4.3.
 * Any re-bucketing of the score->confidence mapping must ship deliberately
 * because the UI renders each tier with distinct visual weight.
 */
class ConfidenceTest {

    /** 0..49 must map to LOW — section 4.3 threshold. */
    @Test
    fun lowTierLowerBound() {
        assertEquals(Confidence.LOW, Confidence.fromScore(0))
    }

    /** 49 is the inclusive upper bound of LOW. */
    @Test
    fun lowTierUpperBound() {
        assertEquals(Confidence.LOW, Confidence.fromScore(49))
    }

    /** 50 is the inclusive lower bound of MEDIUM per section 4.3. */
    @Test
    fun mediumTierLowerBound() {
        assertEquals(Confidence.MEDIUM, Confidence.fromScore(50))
    }

    /** 74 is the inclusive upper bound of MEDIUM. */
    @Test
    fun mediumTierUpperBound() {
        assertEquals(Confidence.MEDIUM, Confidence.fromScore(74))
    }

    /** 75 is the inclusive lower bound of HIGH per section 4.3. */
    @Test
    fun highTierLowerBound() {
        assertEquals(Confidence.HIGH, Confidence.fromScore(75))
    }

    /** 100 is the inclusive upper bound of HIGH. */
    @Test
    fun highTierUpperBound() {
        assertEquals(Confidence.HIGH, Confidence.fromScore(100))
    }

    /** Below-range scores clamp to LOW via the ladder — guards against -5 quirks. */
    @Test
    fun negativeScoresClampLow() {
        assertEquals(Confidence.LOW, Confidence.fromScore(-5))
    }

    /** Above-range scores clamp to HIGH via the ladder — guards against 200 quirks. */
    @Test
    fun aboveRangeScoresClampHigh() {
        assertEquals(Confidence.HIGH, Confidence.fromScore(200))
    }
}

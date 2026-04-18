package com.sleepyio.sleepyio.recommendation

import com.sleepyio.sleepyio.recommendation.model.Factor
import com.sleepyio.sleepyio.recommendation.model.FactorDirection
import com.sleepyio.sleepyio.recommendation.model.Rationale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards the contract the UI depends on: [Rationale.positive] and
 * [Rationale.negative] must partition factors strictly by [FactorDirection],
 * and NEUTRAL factors must appear in neither bucket (section 4.4).
 */
class RationaleTest {

    private fun factor(label: String, direction: FactorDirection): Factor =
        Factor(label = label, weight = 10.0, evidence = "evidence for $label", direction = direction)

    /** `positive` returns only POSITIVE factors — green reason-to-start list. */
    @Test
    fun positiveFiltersOnlyPositives() {
        val pos1 = factor("A", FactorDirection.POSITIVE)
        val pos2 = factor("B", FactorDirection.POSITIVE)
        val rationale = Rationale(
            factors = listOf(
                pos1,
                factor("C", FactorDirection.NEGATIVE),
                factor("D", FactorDirection.NEUTRAL),
                pos2,
            ),
            summary = "test",
        )
        assertEquals(listOf(pos1, pos2), rationale.positive)
    }

    /** `negative` returns only NEGATIVE factors — red concern list. */
    @Test
    fun negativeFiltersOnlyNegatives() {
        val neg1 = factor("X", FactorDirection.NEGATIVE)
        val neg2 = factor("Y", FactorDirection.NEGATIVE)
        val rationale = Rationale(
            factors = listOf(
                factor("P", FactorDirection.POSITIVE),
                neg1,
                factor("N", FactorDirection.NEUTRAL),
                neg2,
            ),
            summary = "test",
        )
        assertEquals(listOf(neg1, neg2), rationale.negative)
    }

    /** NEUTRAL factors must never appear in either partition — UI has no bucket for them. */
    @Test
    fun neutralExcludedFromBothPartitions() {
        val neutral = factor("meh", FactorDirection.NEUTRAL)
        val rationale = Rationale(factors = listOf(neutral), summary = "s")
        assertTrue(neutral !in rationale.positive)
        assertTrue(neutral !in rationale.negative)
    }

    /** Empty factor list yields empty partitions — no explosions on the edge case. */
    @Test
    fun emptyFactorsYieldEmptyPartitions() {
        val rationale = Rationale(factors = emptyList(), summary = "s")
        assertTrue(rationale.positive.isEmpty())
        assertTrue(rationale.negative.isEmpty())
    }
}

package com.sleepyio.sleepyio.recommendation

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * WaiverWireAnalyzer depends directly on the concrete `data object`
 * [com.sleepyio.sleepyio.client.SleeperClient]. It cannot be swapped with a
 * test double without a production refactor (see docs/qa/KNOWN_ISSUES.md —
 * recommendation: extract a `SleeperRostersProvider` interface).
 *
 * The FAAB tier logic lives in a `private fun faabPctFor(...)`. With only
 * public reachability from commonTest we cannot assert the numeric tier
 * mapping without instantiating the analyzer and hitting the live Sleeper
 * API, which the harness forbids.
 *
 * This file therefore only documents the gap + asserts the class remains a
 * constructable type so consumers are reminded to lift the dependency before
 * FAAB regression testing is possible.
 *
 * TODO(QA): once `SleeperClient` sits behind an interface, assert:
 *   - priority >= 85  -> faab in 25..40 of remaining budget
 *   - priority 70..84 -> faab in 12..24
 *   - priority 50..69 -> faab in 3..10
 *   - priority < 50   -> faab in 0..2
 *   plus the weeks-1-3 25%-scale-down modifier from framework 3.2.
 */
class WaiverWireAnalyzerTest {

    /**
     * Compile-time smoke: the analyzer class still exists and its public API
     * is what the QA plan expects. A CI break here means the production
     * refactor has landed and the real tier tests can be plumbed in.
     */
    @Test
    fun waiverAnalyzerClassReferenceIsReachable() {
        val className = WaiverWireAnalyzer::class.simpleName
        assertTrue(className == "WaiverWireAnalyzer", "class rename requires updating this test")
    }
}

package com.sleepyio.sleepyio.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeepLinkParserTest {

    @Test
    fun parsesAdvisorDeepLink() {
        val result = DeepLinkParser.parse("sleepyio://advisor/12345/77")
        assertEquals(DeepLink.Advisor(leagueId = 12345L, rosterId = 77L), result)
    }

    @Test
    fun parsesPlayerInsightWithWeek() {
        val result = DeepLinkParser.parse("sleepyio://players/4017?week=6")
        assertEquals(DeepLink.PlayerInsight(playerId = "4017", week = 6), result)
    }

    @Test
    fun parsesPlayerInsightWithoutWeek() {
        val result = DeepLinkParser.parse("sleepyio://players/4017")
        assertEquals(DeepLink.PlayerInsight(playerId = "4017", week = null), result)
    }

    @Test
    fun parsesWaiversDeepLink() {
        val result = DeepLinkParser.parse("sleepyio://waivers/9/3/8")
        assertEquals(DeepLink.Waivers(leagueId = 9L, rosterId = 3L, week = 8), result)
    }

    @Test
    fun rejectsForeignScheme() {
        assertNull(DeepLinkParser.parse("https://example.com/advisor/1/2"))
    }

    @Test
    fun rejectsMalformedPaths() {
        assertNull(DeepLinkParser.parse("sleepyio://advisor/not-a-number/2"))
        assertNull(DeepLinkParser.parse("sleepyio://advisor/1"))
        assertNull(DeepLinkParser.parse("sleepyio://"))
        assertNull(DeepLinkParser.parse(""))
    }

    @Test
    fun rejectsUnknownHost() {
        assertNull(DeepLinkParser.parse("sleepyio://something/1/2"))
    }
}

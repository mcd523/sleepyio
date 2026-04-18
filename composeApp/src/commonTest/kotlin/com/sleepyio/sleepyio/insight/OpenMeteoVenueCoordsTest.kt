package com.sleepyio.sleepyio.insight

import com.sleepyio.sleepyio.insight.source.openmeteo.OpenMeteoWeatherSource
import com.sleepyio.sleepyio.runSuspending
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Enforces weather-source guarantees the analyzers rely on:
 *  1) Every NFL team has stadium coordinates wired up, so outdoor games never
 *     silently fall through to `null`.
 *  2) Dome teams short-circuit before any HTTP call, returning the controlled
 *     constant (windMph=0, dome=true) from [OpenMeteoWeatherSource.fetchForecast].
 */
class OpenMeteoVenueCoordsTest {

    /** 32 NFL teams => exactly 32 entries in VENUE_COORDS. */
    @Test
    fun venueCoordsHasAllThirtyTwoTeams() {
        assertEquals(32, OpenMeteoWeatherSource.VENUE_COORDS.size)
    }

    /** Every dome team must also appear in VENUE_COORDS so the map is consistent. */
    @Test
    fun everyDomeHasCoordsEntry() {
        OpenMeteoWeatherSource.DOMES.forEach { team ->
            assertTrue(
                team in OpenMeteoWeatherSource.VENUE_COORDS,
                "dome team $team missing from VENUE_COORDS",
            )
        }
    }

    /** Coordinates must be in plausible lat/lon ranges (CONUS). */
    @Test
    fun coordsAreWithinContiguousUnitedStates() {
        OpenMeteoWeatherSource.VENUE_COORDS.forEach { (team, coords) ->
            val (lat, lon) = coords
            assertTrue(lat in 24.0..49.5, "$team lat $lat out of CONUS range")
            assertTrue(lon in -125.0..-66.0, "$team lon $lon out of CONUS range")
        }
    }

    /**
     * Framework section 5 weather factor: dome teams skip the network entirely
     * and return `dome=true`. Hitting the real API would make this test flaky
     * on offline CI, so the short-circuit is load-bearing.
     */
    @Test
    fun domeTeamsShortCircuitBeforeNetworkCall() = runSuspending {
        val source = OpenMeteoWeatherSource()
        OpenMeteoWeatherSource.DOMES.forEach { team ->
            val forecast = source.fetchForecast(team, kickoffEpochMs = 0L)
            assertNotNull(forecast, "dome team $team must return non-null forecast synchronously")
            assertTrue(forecast.dome, "dome team $team forecast must have dome=true")
            assertEquals(0, forecast.windMph, "dome team $team must have wind=0")
        }
    }

    /** ATL, DAL, DET, HOU, IND, LV, LAR, MIN, NO, ARI are the documented domes. */
    @Test
    fun domeSetMatchesFrameworkList() {
        val expected = setOf("ATL", "DAL", "DET", "HOU", "IND", "LV", "LAR", "MIN", "NO", "ARI")
        assertEquals(expected, OpenMeteoWeatherSource.DOMES)
    }
}

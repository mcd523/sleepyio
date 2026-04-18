package com.sleepyio.sleepyio

import com.sleepyio.sleepyio.insight.model.FantasyImpact
import com.sleepyio.sleepyio.insight.model.GameEnvironment
import com.sleepyio.sleepyio.insight.model.InjuryStatus
import com.sleepyio.sleepyio.insight.model.MatchupGrade
import com.sleepyio.sleepyio.insight.model.MatchupRating
import com.sleepyio.sleepyio.insight.model.NewsBlurb
import com.sleepyio.sleepyio.insight.model.PlayerInsight
import com.sleepyio.sleepyio.insight.model.ProjectionRange
import com.sleepyio.sleepyio.insight.model.UsageTrend
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

/**
 * Multiplatform-safe suspend-test runner.
 *
 * `kotlinx-coroutines-test` is not on the classpath (see
 * composeApp/build.gradle.kts) and `kotlinx.coroutines.runBlocking` is not
 * exposed from commonMain for JS/wasmJs targets. Our suspend code paths
 * under test never truly suspend (no dispatcher hop, no delay) so running
 * the continuation synchronously via [startCoroutine] is sufficient. If a
 * future test needs real async scheduling we should add the
 * `kotlinx-coroutines-test` dependency rather than hacking around it.
 */
internal fun runSuspending(body: suspend () -> Unit) {
    var error: Throwable? = null
    var completed = false
    body.startCoroutine(object : Continuation<Unit> {
        override val context = EmptyCoroutineContext
        override fun resumeWith(result: Result<Unit>) {
            completed = true
            error = result.exceptionOrNull()
        }
    })
    check(completed) {
        "runSuspending body suspended; tests must not use real async APIs " +
            "(delay, coroutineScope with dispatcher, etc). Add kotlinx-coroutines-test " +
            "and switch to runTest if you need that."
    }
    error?.let { throw it }
}

/**
 * Shared, deterministic [PlayerInsight] fixtures used by every analyzer test.
 *
 * Every field is explicit so the test intent is readable at the call site —
 * copy + tweak the single field you care about for each scenario.
 */
internal fun playerInsight(
    playerId: String,
    fullName: String = playerId,
    position: String = "RB",
    team: String? = "KC",
    medianProjection: Double = 12.0,
    floor: Double = medianProjection - 4.0,
    ceiling: Double = medianProjection + 6.0,
    matchup: MatchupGrade = MatchupGrade.NEUTRAL,
    opponentRankVsPos: Int? = 16,
    designation: String? = null,
    snapPct: Double? = null,
    targetShare: Double? = null,
    carryShare: Double? = null,
    redZoneOpp: Int? = null,
    impliedTeamTotal: Double? = 22.0,
    windMph: Int? = 0,
    dome: Boolean = false,
    week: Int = 7,
    recentNews: List<NewsBlurb> = emptyList(),
): PlayerInsight = PlayerInsight(
    playerId = playerId,
    fullName = fullName,
    position = position,
    team = team,
    opponent = null,
    week = week,
    projection = ProjectionRange(floor = floor, median = medianProjection, ceiling = ceiling),
    injury = InjuryStatus(
        designation = designation,
        bodyPart = null,
        practiceStatus = null,
        lastUpdatedEpochMs = null,
    ),
    usage = UsageTrend(
        snapPctLast3 = snapPct,
        targetShareLast3 = targetShare,
        carryShareLast3 = carryShare,
        redZoneOppLast3 = redZoneOpp,
    ),
    matchup = MatchupRating(grade = matchup, opponentRankVsPos = opponentRankVsPos, notes = null),
    gameEnvironment = GameEnvironment(
        impliedTeamTotal = impliedTeamTotal,
        spread = null,
        windMph = windMph,
        precipitationPct = 0,
        temperatureF = 60,
        dome = dome,
    ),
    recentNews = recentNews,
)

/** Build a deterministic news blurb for tests. */
internal fun newsBlurb(
    headline: String = "headline",
    impact: FantasyImpact = FantasyImpact.NEGATIVE,
    publishedEpochMs: Long = 0L,
): NewsBlurb = NewsBlurb(
    headline = headline,
    body = headline,
    source = "test",
    publishedEpochMs = publishedEpochMs,
    fantasyImpact = impact,
)

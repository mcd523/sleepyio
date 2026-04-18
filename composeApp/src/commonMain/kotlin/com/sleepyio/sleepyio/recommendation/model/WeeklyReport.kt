package com.sleepyio.sleepyio.recommendation.model

/**
 * The top-of-feed weekly digest for a single roster in a single league.
 *
 * This is the aggregate that powers the "Weekly Report" tab: the user's
 * recommended lineup, every head-to-head start/sit call the analyzer thought
 * was close enough to surface, this week's waiver targets, the risky-starter
 * list (players who made the lineup but carry a negative factor the user
 * should know about), and a single human-readable [headlineAdvice] string
 * that summarizes the week in one sentence for notifications.
 */
data class WeeklyReport(
    val leagueId: Long,
    val rosterId: Long,
    val week: Int,
    val lineup: LineupRecommendation,
    val startSitDecisions: List<StartSitRecommendation>,
    val waiverTargets: List<WaiverTarget>,
    val riskyStarters: List<String>,
    val headlineAdvice: String,
)

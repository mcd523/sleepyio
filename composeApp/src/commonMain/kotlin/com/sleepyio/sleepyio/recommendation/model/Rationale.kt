package com.sleepyio.sleepyio.recommendation.model

/**
 * A single weighted reason that contributed to a recommendation's score.
 *
 * [weight] is the factor's contribution (typically 0..30 — see the
 * `ANALYSIS_FRAMEWORK.md` default weights table). [evidence] is a
 * human-readable sentence that the UI renders verbatim (e.g. "Target share up
 * 9 pp over the last 3 weeks after teammate's Week 5 injury").
 */
data class Factor(
    val label: String,
    val weight: Double,
    val evidence: String,
    val direction: FactorDirection,
)

/** Directional pull a [Factor] exerts on the final recommendation score. */
enum class FactorDirection { POSITIVE, NEGATIVE, NEUTRAL }

/**
 * The "why" behind a recommendation. Every recommendation the service emits
 * must ship with a rationale containing at least two factors — if an analyzer
 * cannot produce that many, it should not emit the recommendation at all.
 *
 * [summary] is a one-liner for compact UI surfaces (notifications, list
 * rows); [factors] drives the expanded detail view.
 */
data class Rationale(
    val factors: List<Factor>,
    val summary: String,
) {
    val positive: List<Factor>
        get() = factors.filter { it.direction == FactorDirection.POSITIVE }

    val negative: List<Factor>
        get() = factors.filter { it.direction == FactorDirection.NEGATIVE }
}

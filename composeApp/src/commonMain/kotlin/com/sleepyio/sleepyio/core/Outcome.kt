package com.sleepyio.sleepyio.core

// NOTE: Phase 1 refactor — introduces the unified result type the
// repository + presentation layers will adopt. Existing code continues to
// use exceptions + empty fallbacks; migration is Phase 2 and later. See
// docs/architecture/REFACTOR_PLAN.md §6.

/**
 * Sealed result type for any operation that can succeed, fail, or be in
 * progress.
 *
 * Intended usage:
 * - `Source` interfaces (lowest layer) keep returning `List<T>`/`T?` with
 *   fail-soft semantics — do NOT wrap sources in [Outcome].
 * - `Repository` and `*Service` boundaries return [Outcome] so every
 *   consumer gets a typed [Reason] instead of a raw exception.
 * - `ViewModel` collapses [Outcome] into `ScreenState` for the UI.
 * - Composables never see [Outcome]; they only see `ScreenState`.
 *
 * Example:
 * ```
 * val outcome = repo.weeklyReport(leagueId, rosterId, week)
 * _state.update { it.copy(
 *     report = when (outcome) {
 *         is Outcome.Ok -> ScreenState.Content(outcome.value)
 *         is Outcome.Fail -> ScreenState.Failed(outcome.reason.label)
 *         Outcome.Loading -> ScreenState.Loading
 *     }
 * ) }
 * ```
 *
 * Why not `kotlin.Result`? [Outcome] includes a first-class [Loading] and
 * a typed [Reason] enum, which is what every UI needs. `Result` has
 * neither.
 */
sealed interface Outcome<out T> {
    data class Ok<T>(val value: T) : Outcome<T>
    data class Fail(val reason: Reason, val cause: Throwable? = null) : Outcome<Nothing>
    data object Loading : Outcome<Nothing>
}

/** Coarse classification of why an operation failed — surfaced to users as a label. */
enum class Reason(val label: String) {
    NETWORK("You appear to be offline."),
    PARSE("We couldn't read the response from upstream."),
    NOT_FOUND("We couldn't find that."),
    UNAUTHORIZED("You need to sign in again."),
    RATE_LIMITED("We're being rate-limited — try again in a minute."),
    CANCELLED("Cancelled."),
    UNKNOWN("Something went wrong.");
}

/**
 * Convenience: run [block] and wrap its result. Exceptions are routed into
 * [Outcome.Fail] with a best-effort [Reason] classification. The caller can
 * override the classification by passing [classify].
 */
inline fun <T> outcomeOf(
    classify: (Throwable) -> Reason = { Reason.UNKNOWN },
    block: () -> T,
): Outcome<T> = try {
    Outcome.Ok(block())
} catch (t: Throwable) {
    Outcome.Fail(classify(t), t)
}

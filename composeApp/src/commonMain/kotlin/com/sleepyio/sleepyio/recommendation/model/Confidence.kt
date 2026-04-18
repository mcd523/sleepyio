package com.sleepyio.sleepyio.recommendation.model

/**
 * Discrete confidence tier attached to every [Recommendation]. The numeric
 * score lives on the recommendation itself; this enum is the UI-facing bucket.
 *
 * Thresholds (see [fromScore]) are intentionally asymmetric: LOW is the
 * widest bucket because "we don't know" and "we know this is bad" both land
 * there, and the UI renders them the same way (with a caveat).
 */
enum class Confidence {
    HIGH,
    MEDIUM,
    LOW;

    companion object {
        /**
         * Maps a 0..100 confidence score to the discrete tier.
         *
         *  - 0..49  -> [LOW]
         *  - 50..74 -> [MEDIUM]
         *  - 75..100 -> [HIGH]
         *
         * Scores outside 0..100 are clamped to the nearest end of the range.
         */
        fun fromScore(score: Int): Confidence = when {
            score >= 75 -> HIGH
            score >= 50 -> MEDIUM
            else -> LOW
        }
    }
}

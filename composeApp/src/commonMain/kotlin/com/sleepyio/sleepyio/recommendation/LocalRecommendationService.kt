package com.sleepyio.sleepyio.recommendation

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Composition local that provides the [RecommendationService] to the UI tree.
 *
 * A concrete value is installed once at the top of [com.sleepyio.sleepyio.App]
 * via `CompositionLocalProvider(LocalRecommendationService provides RecommendationService.default())`.
 * Screens resolve it with `LocalRecommendationService.current`.
 */
val LocalRecommendationService = staticCompositionLocalOf<RecommendationService> {
    error("RecommendationService not provided")
}

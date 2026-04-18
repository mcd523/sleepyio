package com.sleepyio.sleepyio.platform

/**
 * Static, cheap-to-read device descriptors.
 *
 * Used for:
 * - telemetry / crash reporting (model, OS),
 * - adaptive layout decisions that can't be answered by `WindowSize` alone
 *   (e.g. "is this a foldable that should show split-pane when unfolded"),
 * - A/B gating (newer iPad layouts).
 *
 * Why expect/actual instead of a runtime heuristic in common:
 * the heuristics for "is this a tablet" differ per OS — Android reads
 * `smallestScreenWidthDp`, iOS reads `UIDevice.userInterfaceIdiom`. Pushing
 * that into `commonMain` would duplicate code.
 */
expect class DeviceInfo {
    val model: String
    val osVersion: String
    val isTablet: Boolean
    val formFactor: FormFactor
}

enum class FormFactor {
    PHONE,
    TABLET,
    FOLDABLE,
    DESKTOP,
    WEB,
}

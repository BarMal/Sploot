package com.sploot.domain.model

/**
 * A complete sleep session with per-stage totals and a composite score.
 * May be derived from Whoop algorithms (source = ALGO) or imported from
 * Garmin (source = GARMIN).
 */
data class SleepSession(
    val id: Long,
    val startEpochSeconds: Long,
    val endEpochSeconds: Long,
    val source: SleepSource,
    val algorithmRevisionId: Long?,
    /** Composite sleep quality score 0-100, or null before scoring. */
    val totalScore: Int?,
    val deepMinutes: Int,
    val lightMinutes: Int,
    val remMinutes: Int,
    val awakeMinutes: Int,
    val latencyMinutes: Int,
    /** Sleep efficiency = (total_sleep / time_in_bed) x 100 */
    val efficiencyPercent: Float?,
)

package com.sploot.domain.model

/**
 * A 30-second epoch within a sleep session with stage label and signal features.
 * Both algo-derived and Garmin-imported epochs share this model.
 */
data class SleepEpoch(
    val epochStartSeconds: Long,
    val sessionId: Long,
    val stage: SleepStage,
    val source: SleepSource,
    // Signal features computed by :signal-proc (null before processing)
    val rmssd: Float?,
    val meanHr: Float?,
    val movementIntensity: Float?,
    val respRate: Float?,
)

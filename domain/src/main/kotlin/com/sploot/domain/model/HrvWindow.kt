package com.sploot.domain.model

/** A 5-minute HRV analysis window computed from the IBI series. */
data class HrvWindow(
    val windowStartSeconds: Long,
    val windowEndSeconds: Long,
    val sessionId: Long,
    val rmssd: Float,
    val sdnn: Float,
    val pnn50: Float,
    /** Lomb-Scargle periodogram low-frequency power (0.04–0.15 Hz), null if IBI series too short. */
    val lfPower: Float?,
    val hfPower: Float?,
    val lfHfRatio: Float?,
    val meanRrMs: Float,
)

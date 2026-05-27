package com.sploot.domain.scorer

import com.sploot.domain.model.SleepSession

/**
 * Composite sleep quality score 0–100.
 *
 * Weights (chosen to mirror published polysomnographic importance rankings):
 *   Duration    25%  — hours vs 8-hour target
 *   Deep sleep  30%  — % of total; target 20–25%
 *   REM sleep   25%  — % of total; target 20–25%
 *   Disturbances 20% — count of awake epochs during sleep window
 */
class SleepScorer {

    fun score(session: SleepSession): Int {
        val totalSleepMins = session.deepMinutes + session.lightMinutes + session.remMinutes
        if (totalSleepMins == 0) return 0

        // Duration: 8 hours = 100; score falls linearly below / stays flat above
        val durationScore = (totalSleepMins / 480f).coerceIn(0f, 1f)

        // Deep % target: 22.5% ±2.5% → full marks; falls linearly outside
        val deepPct = session.deepMinutes.toFloat() / totalSleepMins
        val deepScore = scorePercent(deepPct, target = 0.225f, tolerance = 0.025f)

        // REM % target same as deep
        val remPct = session.remMinutes.toFloat() / totalSleepMins
        val remScore = scorePercent(remPct, target = 0.225f, tolerance = 0.025f)

        // Disturbances: 0 awake epochs = full marks; loses 5 pts per 5-minute awake block
        val awakeBlocks = session.awakeMinutes / 5
        val disturbanceScore = (1f - awakeBlocks * 0.05f).coerceIn(0f, 1f)

        val composite = durationScore * 0.25f +
                deepScore     * 0.30f +
                remScore      * 0.25f +
                disturbanceScore * 0.20f

        return (composite * 100).toInt().coerceIn(0, 100)
    }

    /** Score a ratio value relative to a symmetric target ± tolerance, linearly decaying outside. */
    private fun scorePercent(actual: Float, target: Float, tolerance: Float): Float {
        val delta = kotlin.math.abs(actual - target)
        return if (delta <= tolerance) 1f
        else (1f - (delta - tolerance) / target).coerceIn(0f, 1f)
    }
}

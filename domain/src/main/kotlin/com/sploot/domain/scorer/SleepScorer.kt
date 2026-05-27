package com.sploot.domain.scorer

import com.sploot.domain.model.SleepScoreParameters
import com.sploot.domain.model.SleepSession
import kotlin.math.abs

/**
 * Composite sleep quality score 0-100.
 */
class SleepScorer(
    private val parameters: SleepScoreParameters = SleepScoreParameters(),
) {

    fun score(session: SleepSession): Int {
        val totalSleepMins = session.deepMinutes + session.lightMinutes + session.remMinutes
        if (totalSleepMins == 0) return 0

        val durationScore =
            (totalSleepMins / parameters.targetSleepMinutes.toFloat()).coerceIn(0f, 1f)

        val deepPct = session.deepMinutes.toFloat() / totalSleepMins
        val deepScore = scorePercent(
            actual = deepPct,
            target = parameters.targetStagePercent,
            tolerance = parameters.stageTolerancePercent,
        )

        val remPct = session.remMinutes.toFloat() / totalSleepMins
        val remScore = scorePercent(
            actual = remPct,
            target = parameters.targetStagePercent,
            tolerance = parameters.stageTolerancePercent,
        )

        val awakeBlocks = session.awakeMinutes / 5
        val disturbanceScore =
            (1f - awakeBlocks * parameters.disturbancePenaltyPerBlock).coerceIn(0f, 1f)

        val composite =
            durationScore * parameters.durationWeight +
                deepScore * parameters.deepWeight +
                remScore * parameters.remWeight +
                disturbanceScore * parameters.disturbanceWeight

        return (composite * 100).toInt().coerceIn(0, 100)
    }

    private fun scorePercent(actual: Float, target: Float, tolerance: Float): Float {
        val delta = abs(actual - target)
        return if (delta <= tolerance) 1f
        else (1f - (delta - tolerance) / target).coerceIn(0f, 1f)
    }
}

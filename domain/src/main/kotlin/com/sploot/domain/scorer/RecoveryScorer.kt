package com.sploot.domain.scorer

import com.sploot.domain.model.HrvWindow
import com.sploot.domain.model.PersonalBaseline
import com.sploot.domain.model.SleepSession
import kotlin.math.exp

/**
 * Overnight recovery score 0–100.
 *
 * Formula (inspired by published HRV recovery literature):
 *   hrv_score   = sigmoid( z_rmssd )             weight 50%
 *   rhr_score   = sigmoid( −z_resting_hr )        weight 20%  (lower HR = better)
 *   sleep_score = SleepScorer / 100               weight 30%
 *
 * where sigmoid(x) = 1 / (1 + e^{−x})  maps any z-score to (0, 1).
 * Final score = round( composite × 100 ).
 */
class RecoveryScorer(
    private val baseline: PersonalBaseline,
    private val sleepScorer: SleepScorer = SleepScorer(),
) {

    fun score(
        overnightHrv: HrvWindow,
        restingHrBpm: Float,
        sleepSession: SleepSession,
    ): Int {
        val zHrv = baseline.zScoreRmssd(overnightHrv.rmssd)
        val zHr  = baseline.zScoreHr(restingHrBpm)

        val hrvScore   = sigmoid(zHrv)
        val hrScore    = sigmoid(-zHr)           // inverted: lower HR → higher score
        val sleepScore = sleepScorer.score(sleepSession) / 100f

        val composite = hrvScore * 0.50f + hrScore * 0.20f + sleepScore * 0.30f
        return (composite * 100).toInt().coerceIn(0, 100)
    }

    private fun sigmoid(x: Float): Float = (1f / (1f + exp(-x.toDouble()))).toFloat()
}

package com.sploot.domain.scorer

import kotlin.math.exp

/**
 * Activity strain score on a 0–21 scale (Whoop-inspired).
 *
 * Uses exponential HR weighting (TRIMP method):
 *   load = Σ duration_minutes × exp(k × hr_fraction)
 * where:
 *   hr_fraction = (hr − rest_hr) / (max_hr − rest_hr)
 *   k ≈ 1.92 (Bannister's TRIMP constant)
 *
 * Raw load is then normalised to 0–21 using a device/person-specific ceiling.
 */
class StrainCalculator(
    private val maxHrBpm: Int,
    /** Assumed max raw load for a 100% strain effort (calibrated from data over time). */
    private val maxRawLoad: Float = 500f,
) {

    data class HrSample(val durationMinutes: Float, val hrBpm: Int)

    fun calculate(samples: List<HrSample>, restHrBpm: Int): Float {
        if (samples.isEmpty()) return 0f
        val hrRange = (maxHrBpm - restHrBpm).toFloat()
        if (hrRange <= 0f) return 0f

        val rawLoad = samples.sumOf { sample ->
            val fraction = ((sample.hrBpm - restHrBpm) / hrRange).coerceIn(0f, 1f)
            (sample.durationMinutes * exp(K * fraction.toDouble()))
        }.toFloat()

        return (rawLoad / maxRawLoad * 21f).coerceIn(0f, 21f)
    }

    companion object {
        private const val K = 1.92
    }
}

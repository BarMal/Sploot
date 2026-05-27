package com.sploot.signalproc.filter

/**
 * Second-order biquad Butterworth bandpass filter.
 *
 * Applied as a cascade of biquad sections for higher-order designs.
 * Coefficients are pre-computed for a given sample rate and cutoff pair.
 *
 * Phase 2 TODO: compute coefficients analytically from fc1/fc2/sampleRate.
 */
object ButterworthFilter {

    data class BiquadCoeffs(val b0: Double, val b1: Double, val b2: Double,
                            val a1: Double, val a2: Double)

    /**
     * Apply a biquad IIR filter to [input].
     * Direct Form II transposed implementation.
     */
    fun applyBiquad(input: FloatArray, coeffs: BiquadCoeffs): FloatArray {
        val out = FloatArray(input.size)
        var w1 = 0.0; var w2 = 0.0
        for (i in input.indices) {
            val x   = input[i].toDouble()
            val y   = coeffs.b0 * x + w1
            w1 = coeffs.b1 * x - coeffs.a1 * y + w2
            w2 = coeffs.b2 * x - coeffs.a2 * y
            out[i] = y.toFloat()
        }
        return out
    }

    // ── Pre-computed coefficient sets for common use cases ───────────────────

    /**
     * PPG bandpass 0.5–5 Hz at 100 Hz sample rate.
     * TODO Phase 2: replace with analytically-computed coefficients.
     */
    val PPG_BANDPASS_100HZ = BiquadCoeffs(
        b0 = 0.13673, b1 = 0.0, b2 = -0.13673,
        a1 = -1.72461, a2 = 0.72654,
    )

    /**
     * Respiration bandpass 0.15–0.5 Hz at 100 Hz sample rate.
     * TODO Phase 2: replace with analytically-computed coefficients.
     */
    val RESP_BANDPASS_100HZ = BiquadCoeffs(
        b0 = 0.00177, b1 = 0.0, b2 = -0.00177,
        a1 = -1.99559, a2 = 0.99646,
    )
}

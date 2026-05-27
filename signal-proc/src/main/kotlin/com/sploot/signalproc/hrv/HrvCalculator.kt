package com.sploot.signalproc.hrv

import kotlin.math.sqrt

/**
 * Time-domain HRV metrics from an IBI series (ms).
 *
 * Phase 2 TODO: Frequency domain via Lomb-Scargle periodogram.
 */
object HrvCalculator {

    data class FreqResult(val lfPower: Float, val hfPower: Float, val lfHfRatio: Float)

    /** Root mean square of successive differences. */
    fun rmssd(ibiMs: FloatArray): Float {
        if (ibiMs.size < 2) return 0f
        var sumSqDiff = 0f
        for (index in 1 until ibiMs.size) {
            val diff = ibiMs[index] - ibiMs[index - 1]
            sumSqDiff += diff * diff
        }
        return sqrt(sumSqDiff / (ibiMs.size - 1).toFloat())
    }

    /** Standard deviation of all NN intervals. */
    fun sdnn(ibiMs: FloatArray): Float {
        if (ibiMs.isEmpty()) return 0f
        val mean = ibiMs.average().toFloat()
        val variance = ibiMs.map { (it - mean) * (it - mean) }.average().toFloat()
        return sqrt(variance)
    }

    /** Percentage of successive differences > 50 ms. */
    fun pnn50(ibiMs: FloatArray): Float {
        if (ibiMs.size < 2) return 0f
        var count = 0
        for (index in 1 until ibiMs.size) {
            if (kotlin.math.abs(ibiMs[index] - ibiMs[index - 1]) > 50f) {
                count++
            }
        }
        return count.toFloat() / (ibiMs.size - 1) * 100f
    }

    fun meanRr(ibiMs: FloatArray): Float =
        if (ibiMs.isEmpty()) 0f else ibiMs.average().toFloat()

    /**
     * Frequency-domain analysis via Lomb-Scargle periodogram.
     * TODO Phase 2: implement non-uniform DFT for unevenly sampled IBI series.
     */
    fun frequencyDomain(ibiMs: FloatArray): FreqResult? {
        if (ibiMs.size < 30) return null
        // TODO Phase 2
        return null
    }
}

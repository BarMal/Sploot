package com.sploot.signalproc.ppg

import com.sploot.signalproc.filter.ButterworthFilter

/**
 * Detects systolic peaks in a PPG signal using a Pan-Tompkins-inspired approach:
 *
 *   1. Bandpass filter (0.5–5 Hz) to isolate cardiac component
 *   2. Differentiate to emphasise upstrokes
 *   3. Square to amplify large deflections
 *   4. Moving-window integrate (150 ms window at 100 Hz = 15 samples)
 *   5. Adaptive threshold: threshold = α × signal_max, recomputed every 1 s
 *   6. Refractory period: 300 ms minimum between peaks (< 200 bpm)
 *
 * Phase 2 TODO: adaptive threshold tuning; false-positive rejection using
 * search-back and T-wave exclusion logic from the original Pan-Tompkins paper.
 */
object PpgPeakDetector {

    private const val REFRACTORY_SAMPLES = 30  // 300 ms at 100 Hz
    private const val INTEGRATE_WINDOW   = 15  // 150 ms at 100 Hz
    private const val THRESHOLD_ALPHA    = 0.6f

    /**
     * Detect peaks in [signal] sampled at [sampleRateHz].
     * @return List of peak sample indices in the input signal.
     */
    fun detect(signal: FloatArray, sampleRateHz: Int = 100): List<Int> {
        if (signal.size < INTEGRATE_WINDOW * 2) return emptyList()

        // 1. Bandpass
        val filtered = ButterworthFilter.applyBiquad(signal, ButterworthFilter.PPG_BANDPASS_100HZ)

        // 2. Differentiate
        val diff = FloatArray(filtered.size) { i ->
            if (i == 0) 0f else filtered[i] - filtered[i - 1]
        }

        // 3. Square
        val squared = FloatArray(diff.size) { i -> diff[i] * diff[i] }

        // 4. Moving-window integration
        val integrated = FloatArray(squared.size)
        var windowSum = 0f
        for (i in squared.indices) {
            windowSum += squared[i]
            if (i >= INTEGRATE_WINDOW) windowSum -= squared[i - INTEGRATE_WINDOW]
            integrated[i] = windowSum / INTEGRATE_WINDOW
        }

        // 5. Adaptive threshold + 6. Refractory period
        val peaks = mutableListOf<Int>()
        var lastPeak = -REFRACTORY_SAMPLES

        // Recompute threshold each second
        val windowSamples = sampleRateHz
        for (i in INTEGRATE_WINDOW until integrated.size) {
            val windowStart = maxOf(0, i - windowSamples)
            val maxVal = integrated.slice(windowStart..i).max() ?: continue
            val threshold = THRESHOLD_ALPHA * maxVal

            if (integrated[i] > threshold && i - lastPeak > REFRACTORY_SAMPLES) {
                // Confirm it's a local max
                if (i > 0 && i < integrated.size - 1 &&
                    integrated[i] >= integrated[i - 1] &&
                    integrated[i] >= integrated[i + 1]
                ) {
                    peaks += i
                    lastPeak = i
                }
            }
        }
        return peaks
    }
}

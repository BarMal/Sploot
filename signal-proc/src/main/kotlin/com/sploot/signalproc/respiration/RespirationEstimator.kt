package com.sploot.signalproc.respiration

import com.sploot.signalproc.filter.ButterworthFilter
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Estimates respiration rate from the PPG envelope (RSA method).
 *
 * Respiratory sinus arrhythmia modulates both the amplitude and baseline of
 * the PPG signal at the breathing frequency (0.15–0.5 Hz = 9–30 breaths/min).
 *
 * Algorithm:
 *   1. Bandpass filter green channel at 0.15–0.5 Hz
 *   2. Find dominant frequency via DFT over the 0.15–0.5 Hz band
 *   3. Return dominant frequency × 60 = breaths/min
 */
object RespirationEstimator {

    /**
     * @param greenChannel  Green PPG channel A samples (concatenated from multiple R21 records)
     * @param sampleRateHz  Sample rate (default 100 Hz)
     * @return Estimated respiration rate in breaths/min, or null if insufficient data.
     */
    fun fromPpg(greenChannel: FloatArray, sampleRateHz: Int = 100): Float? {
        if (greenChannel.size < sampleRateHz * 10) return null  // need ≥10 s

        val filtered = ButterworthFilter.applyBiquad(greenChannel, ButterworthFilter.RESP_BANDPASS_100HZ)

        // DFT over respiration band
        val freqResHz = sampleRateHz.toDouble() / filtered.size
        val minBin = (0.15 / freqResHz).toInt()
        val maxBin = (0.50 / freqResHz).toInt()

        var peakMag  = 0.0
        var peakFreq = 0.15

        for (k in minBin..maxBin) {
            val freq = k * freqResHz
            var re = 0.0; var im = 0.0
            for (n in filtered.indices) {
                val angle = -2.0 * PI * k * n / filtered.size
                re += filtered[n] * cos(angle)
                im += filtered[n] * sin(angle)
            }
            val mag = sqrt(re * re + im * im)
            if (mag > peakMag) { peakMag = mag; peakFreq = freq }
        }

        return (peakFreq * 60).toFloat()
    }
}

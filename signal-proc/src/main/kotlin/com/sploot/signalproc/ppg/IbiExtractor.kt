package com.sploot.signalproc.ppg

/**
 * Converts a list of peak sample indices to inter-beat interval series (ms).
 *
 * IBI[i] = (peakIndices[i+1] - peakIndices[i]) / sampleRateHz * 1000
 *
 * Physiological range filter: 333–2000 ms (30–180 bpm) removes ectopic beats
 * and artefacts before HRV analysis.
 */
object IbiExtractor {

    private const val IBI_MIN_MS = 333f   // 180 bpm
    private const val IBI_MAX_MS = 2000f  // 30 bpm

    fun extract(peakIndices: List<Int>, sampleRateHz: Int = 100): FloatArray {
        if (peakIndices.size < 2) return FloatArray(0)
        val msPerSample = 1000f / sampleRateHz
        return peakIndices.zipWithNext { a, b ->
            (b - a) * msPerSample
        }.filter { it in IBI_MIN_MS..IBI_MAX_MS }.toFloatArray()
    }
}

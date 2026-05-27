package com.sploot.signalproc.spo2

import kotlin.math.sqrt

/**
 * Estimates SpO₂ using the Beer-Lambert ratio-of-ratios method.
 *
 * R = (AC_red / DC_red) / (AC_ir / DC_ir)
 * SpO₂ ≈ 110 − 25 × R   (empirical calibration — REQUIRES in-vivo tuning)
 *
 * Channel mapping from R21:
 *   IR  = channelC
 *   Red = channelF
 *
 * Phase 2 TODO: calibrate against a reference pulse oximeter for the specific
 * WHOOP LED wavelengths and optical geometry.
 */
object SpO2Estimator {

    /**
     * @param irSamples  100-sample window of infrared PPG (channelC)
     * @param redSamples 100-sample window of red PPG (channelF)
     * @return Estimated SpO₂ percentage, or null if confidence is too low.
     */
    fun estimate(irSamples: FloatArray, redSamples: FloatArray): Float? {
        if (irSamples.size < 20 || redSamples.size < 20) return null

        val acIr  = acComponent(irSamples)
        val dcIr  = dcComponent(irSamples)
        val acRed = acComponent(redSamples)
        val dcRed = dcComponent(redSamples)

        if (dcIr < 1f || dcRed < 1f) return null

        val r    = (acRed / dcRed) / (acIr / dcIr)
        val spo2 = (110f - 25f * r).coerceIn(85f, 100f)
        return spo2
    }

    private fun dcComponent(signal: FloatArray): Float = signal.average().toFloat()

    /** AC component = RMS of de-meaned signal. */
    private fun acComponent(signal: FloatArray): Float {
        val dc = dcComponent(signal)
        val variance = signal.map { (it - dc) * (it - dc) }.average().toFloat()
        return sqrt(variance)
    }
}

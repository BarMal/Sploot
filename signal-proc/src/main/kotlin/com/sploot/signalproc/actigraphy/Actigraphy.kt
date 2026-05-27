package com.sploot.signalproc.actigraphy

import kotlin.math.sqrt

/**
 * Classifies each 30-second epoch as SLEEP or WAKE using the Cole-Kripke algorithm.
 *
 * Cole-Kripke algorithm:
 *   P(wake) = W₋₄ × A[t−4] + W₋₃ × A[t−3] + … + W₄ × A[t+4]
 *   where A[t] is the activity count per epoch and W_k are empirical weights.
 *
 * If P(wake) ≥ 1.0 → WAKE, else → SLEEP.
 *
 * The 7 weights (indices −4..+4) from Cole et al. (1992):
 *   W = [0.001, 0.001, 0.002, 0.040, 0.340, 0.040, 0.002, 0.001, 0.001]
 */
object Actigraphy {

    enum class SleepWake { SLEEP, WAKE }

    // Cole-Kripke weights for a ±4 epoch window (9 values)
    private val WEIGHTS = doubleArrayOf(0.001, 0.001, 0.002, 0.040, 0.340, 0.040, 0.002, 0.001, 0.001)
    private const val THRESHOLD = 1.0
    private const val SCALE     = 0.00001  // activity count scaling factor from original paper

    /**
     * @param accelX/Y/Z  Interleaved 100 Hz accelerometer samples
     * @param epochSeconds Duration of each output epoch (default 30 s)
     * @return Sleep/wake classification per epoch
     */
    fun classify(
        accelX: FloatArray, accelY: FloatArray, accelZ: FloatArray,
        sampleRateHz: Int = 100,
        epochSeconds: Int = 30,
    ): List<SleepWake> {
        val samplesPerEpoch = sampleRateHz * epochSeconds
        val numEpochs = accelX.size / samplesPerEpoch

        // Compute per-epoch activity count = sum of vector magnitudes
        val activity = DoubleArray(numEpochs) { e ->
            val start = e * samplesPerEpoch
            val end   = start + samplesPerEpoch
            (start until end).sumOf { i ->
                sqrt(
                    (accelX[i].toDouble() * accelX[i]) +
                    (accelY[i].toDouble() * accelY[i]) +
                    (accelZ[i].toDouble() * accelZ[i])
                )
            }
        }

        // Apply Cole-Kripke weighted sum
        return List(numEpochs) { t ->
            val pWake = WEIGHTS.indices.sumOf { k ->
                val idx = t - 4 + k
                if (idx in activity.indices) WEIGHTS[k] * activity[idx] * SCALE else 0.0
            }
            if (pWake >= THRESHOLD) SleepWake.WAKE else SleepWake.SLEEP
        }
    }
}

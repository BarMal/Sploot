package com.sploot.signalproc.sleep

/**
 * Rule-based sleep stage classifier (Phase 1 baseline — no training data required).
 *
 * Classification rules per 30-second epoch, derived from polysomnography literature:
 *
 *   AWAKE  → movement high (intensity > awake threshold)
 *              OR (hr high AND rmssd high)
 *   DEEP   → rmssd low + hr low + resp rate slow and regular
 *   REM    → rmssd moderate-high + hr slightly elevated + movement near-zero (atonia)
 *   LIGHT  → everything else
 *
 * Phase 2 TODO: tune thresholds using paired Whoop + Garmin nights (≥7 nights).
 * Phase 4 TODO (optional): replace with TFLite decision tree trained on personal data.
 */
object SleepStageClassifier {

    enum class Stage { DEEP, LIGHT, REM, AWAKE }

    data class EpochFeatures(
        val rmssd:             Float,
        val meanHr:            Float,
        val movementIntensity: Float,  // normalised 0–1
        val respRate:          Float,  // breaths/min; 0 if unavailable
    )

    data class Thresholds(
        val awakeMovement:    Float = 0.20f,
        val awakeHrHigh:      Float = 80f,
        val awakeRmssdHigh:   Float = 60f,
        val deepRmssd:        Float = 30f,
        val deepHr:           Float = 55f,
        val deepResp:         Float = 14f,   // breaths/min (below = deep)
        val remMinRmssd:      Float = 40f,
        val remMaxMovement:   Float = 0.02f,
    )

    private val DEFAULT_THRESHOLDS = Thresholds()

    /** Rule-based classification using default (literature-derived) thresholds. */
    fun classifyRuleBased(features: List<EpochFeatures>): List<Stage> =
        classify(features, DEFAULT_THRESHOLDS)

    /** Threshold-tuned classification (call after fitting thresholds to personal data). */
    fun classifyTuned(features: List<EpochFeatures>, thresholds: Thresholds): List<Stage> =
        classify(features, thresholds)

    private fun classify(features: List<EpochFeatures>, t: Thresholds): List<Stage> =
        features.map { f ->
            when {
                // AWAKE: high movement OR (high HR + high HRV)
                f.movementIntensity > t.awakeMovement ||
                (f.meanHr > t.awakeHrHigh && f.rmssd > t.awakeRmssdHigh) -> Stage.AWAKE

                // DEEP: low HRV, low HR, slow breathing
                f.rmssd < t.deepRmssd && f.meanHr < t.deepHr &&
                (f.respRate == 0f || f.respRate < t.deepResp) -> Stage.DEEP

                // REM: moderate-to-high HRV, low movement (muscle atonia)
                f.rmssd > t.remMinRmssd && f.movementIntensity < t.remMaxMovement -> Stage.REM

                // Default: LIGHT
                else -> Stage.LIGHT
            }
        }
}

package com.sploot.domain.model

/**
 * 90-day rolling personal baseline used to z-score overnight HRV and resting HR.
 *
 * Updated after each session.  Stored/loaded via [com.sploot.data.repository.BaselineRepository].
 */
data class PersonalBaseline(
    val rmssdMean: Float,
    val rmssdStd: Float,
    val restingHrMean: Float,
    val restingHrStd: Float,
    /** Number of nights used to compute these statistics. */
    val sampleCount: Int,
) {
    fun zScoreRmssd(rmssd: Float): Float =
        if (rmssdStd > 0f) (rmssd - rmssdMean) / rmssdStd else 0f

    fun zScoreHr(hr: Float): Float =
        if (restingHrStd > 0f) (hr - restingHrMean) / restingHrStd else 0f

    companion object {
        /** Neutral baseline used before enough data has been collected. */
        val EMPTY = PersonalBaseline(
            rmssdMean = 50f, rmssdStd = 15f,
            restingHrMean = 60f, restingHrStd = 8f,
            sampleCount = 0,
        )
    }
}

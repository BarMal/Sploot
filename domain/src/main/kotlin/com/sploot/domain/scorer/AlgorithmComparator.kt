package com.sploot.domain.scorer

import com.sploot.domain.model.SleepEpoch
import com.sploot.domain.model.SleepSession
import com.sploot.domain.model.SleepSource
import com.sploot.domain.model.SleepStage

/**
 * Compares algo-derived sleep staging against Garmin ground truth for the same night.
 *
 * Produces:
 *   - Epoch-by-epoch accuracy
 *   - Cohen's κ (agreement corrected for chance)
 *   - 4×4 confusion matrix [true stage][predicted stage]
 *   - Score delta (our score − Garmin score)
 */
class AlgorithmComparator {

    data class ComparisonResult(
        val epochAccuracy: Float,
        val cohensKappa: Float,
        val confusionMatrix: Array<IntArray>,   // [4][4]: rows = GARMIN, cols = ALGO
        val scoreDelta: Int?,
        val matchedEpochCount: Int,
    ) {
        val stageNames = SleepStage.values().map { it.name }
    }

    /**
     * Compare two sleep sessions for the same night (identified by overlapping time windows).
     *
     * @param algoEpochs   Epochs with source = ALGO
     * @param garminEpochs Epochs with source = GARMIN
     * @param algoSession  Session-level scores from algo
     * @param garminSession Session-level scores from Garmin import
     */
    fun compare(
        algoEpochs:    List<SleepEpoch>,
        garminEpochs:  List<SleepEpoch>,
        algoSession:   SleepSession?,
        garminSession: SleepSession?,
    ): ComparisonResult {
        // Align epochs by start timestamp (±15 s tolerance for clock drift)
        val paired = alignEpochs(algoEpochs, garminEpochs)

        val stages = SleepStage.values()
        val n = stages.size
        val matrix = Array(n) { IntArray(n) }

        var correct = 0
        for ((garmin, algo) in paired) {
            val row = garmin.stage.ordinal
            val col = algo.stage.ordinal
            matrix[row][col]++
            if (row == col) correct++
        }

        val total  = paired.size
        val accuracy = if (total > 0) correct.toFloat() / total else 0f
        val kappa    = if (total > 0) computeKappa(matrix, total) else 0f

        val scoreDelta = if (algoSession?.totalScore != null && garminSession?.totalScore != null)
            algoSession.totalScore - garminSession.totalScore
        else null

        return ComparisonResult(
            epochAccuracy    = accuracy,
            cohensKappa      = kappa,
            confusionMatrix  = matrix,
            scoreDelta       = scoreDelta,
            matchedEpochCount = total,
        )
    }

    private fun alignEpochs(
        algo:   List<SleepEpoch>,
        garmin: List<SleepEpoch>,
    ): List<Pair<SleepEpoch, SleepEpoch>> {
        val algoMap = algo.associateBy { it.epochStartSeconds }
        return garmin.mapNotNull { ge ->
            // Accept ±15 s clock drift between Whoop and Garmin
            val match = algoMap[ge.epochStartSeconds]
                ?: algoMap[ge.epochStartSeconds - 15]
                ?: algoMap[ge.epochStartSeconds + 15]
            match?.let { ge to it }
        }
    }

    private fun computeKappa(matrix: Array<IntArray>, total: Int): Float {
        val n = matrix.size
        val po = matrix.indices.sumOf { matrix[it][it] }.toFloat() / total

        val rowSums = IntArray(n) { r -> matrix[r].sum() }
        val colSums = IntArray(n) { c -> matrix.sumOf { it[c] } }
        val pe = (0 until n).sumOf { i ->
            (rowSums[i].toLong() * colSums[i]) / total.toDouble()
        }.toFloat() / total

        return if (pe >= 1f) 1f else (po - pe) / (1f - pe)
    }
}

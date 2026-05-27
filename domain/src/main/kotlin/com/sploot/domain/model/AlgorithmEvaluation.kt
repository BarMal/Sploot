package com.sploot.domain.model

data class AlgorithmEvaluation(
    val id: Long,
    val family: MetricFamily,
    val algorithmRevisionId: Long,
    val algorithmSessionId: Long,
    val garminSessionId: Long,
    val garminDate: String,
    val epochAccuracy: Float,
    val cohensKappa: Float,
    val scoreDelta: Int?,
    val matchedEpochCount: Int,
    val createdAtMillis: Long,
)

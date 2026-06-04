package com.sploot.domain.model

data class ExternalHeartRateSample(
    val naturalKey: String,
    val source: String,
    val tsSeconds: Long,
    val hrBpm: Int,
)

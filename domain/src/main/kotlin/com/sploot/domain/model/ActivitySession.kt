package com.sploot.domain.model

data class ActivitySession(
    val naturalKey: String,
    val source: String,
    val externalId: String?,
    val activityType: String?,
    val title: String?,
    val startEpochSeconds: Long,
    val endEpochSeconds: Long,
    val avgHrBpm: Float?,
    val maxHrBpm: Float?,
    val caloriesKcal: Float?,
    val distanceMeters: Float?,
)

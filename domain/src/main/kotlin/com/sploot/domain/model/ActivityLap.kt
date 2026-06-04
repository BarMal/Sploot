package com.sploot.domain.model

data class ActivityLap(
    val naturalKey: String,
    val source: String,
    val activityNaturalKey: String?,
    val lapIndex: Int?,
    val activityType: String?,
    val startEpochSeconds: Long,
    val endEpochSeconds: Long,
    val distanceMeters: Float?,
    val caloriesKcal: Float?,
    val avgHrBpm: Float?,
    val maxHrBpm: Float?,
    val avgSpeedMetersPerSecond: Float?,
    val maxSpeedMetersPerSecond: Float?,
)

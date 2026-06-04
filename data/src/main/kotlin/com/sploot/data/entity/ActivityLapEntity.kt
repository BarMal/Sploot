package com.sploot.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "activity_laps")
data class ActivityLapEntity(
    @PrimaryKey val naturalKey: String,
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
    val sourceFileFingerprint: String,
    val importedAtMillis: Long = System.currentTimeMillis(),
)

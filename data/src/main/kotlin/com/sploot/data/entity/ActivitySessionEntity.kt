package com.sploot.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "activity_sessions")
data class ActivitySessionEntity(
    @PrimaryKey val naturalKey: String,
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
    val sourceFileFingerprint: String,
    val importedAtMillis: Long = System.currentTimeMillis(),
)

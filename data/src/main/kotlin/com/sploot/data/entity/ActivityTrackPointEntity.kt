package com.sploot.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "activity_track_points")
data class ActivityTrackPointEntity(
    @PrimaryKey val naturalKey: String,
    val source: String,
    val activityNaturalKey: String?,
    val tsSeconds: Long,
    val latitudeDegrees: Double,
    val longitudeDegrees: Double,
    val altitudeMeters: Float?,
    val distanceMeters: Float?,
    val speedMetersPerSecond: Float?,
    val sourceFileFingerprint: String,
    val importedAtMillis: Long = System.currentTimeMillis(),
)

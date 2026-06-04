package com.sploot.domain.model

data class ActivityTrackPoint(
    val naturalKey: String,
    val source: String,
    val activityNaturalKey: String?,
    val tsSeconds: Long,
    val latitudeDegrees: Double,
    val longitudeDegrees: Double,
    val altitudeMeters: Float?,
    val distanceMeters: Float?,
    val speedMetersPerSecond: Float?,
)

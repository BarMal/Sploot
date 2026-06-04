package com.sploot.domain.model

data class ActivityEvaluation(
    val id: Long,
    val algorithmRevisionId: Long,
    val algorithmActivityNaturalKey: String,
    val garminActivityNaturalKey: String,
    val garminDate: String,
    val overlapSeconds: Long,
    val durationDeltaSeconds: Int,
    val avgHrDelta: Float?,
    val maxHrDelta: Float?,
    val caloriesDelta: Float?,
    val distanceDeltaMeters: Float?,
    val createdAtMillis: Long,
)

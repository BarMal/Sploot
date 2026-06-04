package com.sploot.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "activity_evaluations")
data class ActivityEvaluationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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
    val createdAtMillis: Long = System.currentTimeMillis(),
)

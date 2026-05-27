package com.sploot.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "algorithm_revisions")
data class AlgorithmRevisionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val family: String,
    val version: Int,
    val status: String,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val notes: String?,
    val targetSleepMinutes: Int?,
    val durationWeight: Float?,
    val deepWeight: Float?,
    val remWeight: Float?,
    val disturbanceWeight: Float?,
    val targetStagePercent: Float?,
    val stageTolerancePercent: Float?,
    val disturbancePenaltyPerBlock: Float?,
    val awakeMovement: Float?,
    val awakeHrHigh: Float?,
    val awakeRmssdHigh: Float?,
    val deepRmssd: Float?,
    val deepHr: Float?,
    val deepResp: Float?,
    val remMinRmssd: Float?,
    val remMaxMovement: Float?,
)

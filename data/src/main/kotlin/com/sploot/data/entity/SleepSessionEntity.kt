package com.sploot.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sleep_sessions")
data class SleepSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startSeconds: Long,
    val endSeconds: Long,
    /** "ALGO" | "GARMIN" */
    val source: String,
    val algorithmRevisionId: Long?,
    val totalScore: Int?,
    val deepMinutes: Int,
    val lightMinutes: Int,
    val remMinutes: Int,
    val awakeMinutes: Int,
    val latencyMinutes: Int,
    val efficiencyPercent: Float?,
)

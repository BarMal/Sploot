package com.sploot.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 30-second sleep stage epoch.  source = "ALGO" or "GARMIN". */
@Entity(tableName = "sleep_epochs")
data class SleepEpochEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochStartSeconds: Long,
    val sessionId: Long,
    /** "DEEP" | "LIGHT" | "REM" | "AWAKE" */
    val stage: String,
    val source: String,
    val rmssd:             Float?,
    val meanHr:            Float?,
    val movementIntensity: Float?,
    val respRate:          Float?,
)

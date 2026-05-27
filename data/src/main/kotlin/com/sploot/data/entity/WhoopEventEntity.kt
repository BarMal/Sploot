package com.sploot.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Whoop device events (battery, temperature, wrist on/off). Kept permanently. */
@Entity(tableName = "whoop_events")
data class WhoopEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val tsSeconds: Long,
    /** "BATTERY" | "TEMP" | "WRIST_ON" | "WRIST_OFF" */
    val eventType: String,
    /** Battery percent, temperature °C — null for wrist events. */
    val valueFloat: Float?,
)

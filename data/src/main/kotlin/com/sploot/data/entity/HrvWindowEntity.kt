package com.sploot.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 5-minute HRV analysis window computed by :signal-proc from raw IBI series. */
@Entity(tableName = "hrv_windows")
data class HrvWindowEntity(
    @PrimaryKey val windowStartSeconds: Long,
    val windowEndSeconds: Long,
    val sessionId: Long,
    val rmssd:    Float,
    val sdnn:     Float,
    val pnn50:    Float,
    val lfPower:  Float?,
    val hfPower:  Float?,
    val lfHfRatio: Float?,
    val meanRrMs:  Float,
)

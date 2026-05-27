package com.sploot.data.entity

import androidx.room.Entity

/** 1 Hz heart-rate sample extracted from each R10 record. Kept permanently. */
@Entity(tableName = "hr_samples", primaryKeys = ["sessionId", "tsSeconds"])
data class HrSampleEntity(
    val sessionId: Long,
    val tsSeconds: Long,
    val hrBpm: Int,
)

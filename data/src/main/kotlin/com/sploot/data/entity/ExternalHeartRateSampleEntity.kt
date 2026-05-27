package com.sploot.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "external_heart_rate_samples")
data class ExternalHeartRateSampleEntity(
    @PrimaryKey val naturalKey: String,
    val source: String,
    val tsSeconds: Long,
    val hrBpm: Int,
    val sourceFileFingerprint: String,
    val importedAtMillis: Long = System.currentTimeMillis(),
)

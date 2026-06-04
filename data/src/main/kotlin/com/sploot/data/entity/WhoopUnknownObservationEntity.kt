package com.sploot.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "whoop_unknown_observations",
    indices = [
        Index(value = ["signature"], unique = true),
        Index(value = ["lastSeenSeconds"]),
    ],
)
data class WhoopUnknownObservationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val signature: String,
    val category: String,
    val packetType: Int?,
    val packetTypeName: String,
    val identifier: Int?,
    val identifierLabel: String,
    val frameSizeBytes: Int,
    val firstSeenSeconds: Long,
    val lastSeenSeconds: Long,
    val lastSessionId: Long?,
    val occurrenceCount: Int,
    val sampleHexPreview: String,
    val latestHexPreview: String,
    val note: String?,
    val userAnnotation: String?,
    val annotatedAtSeconds: Long?,
)

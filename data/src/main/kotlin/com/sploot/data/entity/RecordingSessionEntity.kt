package com.sploot.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recording_sessions")
data class RecordingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTimestampSeconds: Long,
    /** Null while recording is in progress. */
    val endTimestampSeconds: Long?,
    /** True once ProcessingWorker has run the signal-proc pipeline on this session. */
    val isProcessed: Boolean = false,
)

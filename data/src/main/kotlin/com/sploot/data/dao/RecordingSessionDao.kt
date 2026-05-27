package com.sploot.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sploot.data.entity.RecordingSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingSessionDao {
    @Insert
    suspend fun insert(session: RecordingSessionEntity): Long

    @Update
    suspend fun update(session: RecordingSessionEntity)

    @Query("SELECT * FROM recording_sessions WHERE id = :id")
    suspend fun getById(id: Long): RecordingSessionEntity?

    @Query("SELECT * FROM recording_sessions ORDER BY startTimestampSeconds DESC")
    fun getAllFlow(): Flow<List<RecordingSessionEntity>>

    @Query("SELECT * FROM recording_sessions WHERE endTimestampSeconds IS NULL LIMIT 1")
    suspend fun getActiveSession(): RecordingSessionEntity?

    @Query("UPDATE recording_sessions SET endTimestampSeconds = :endTs WHERE id = :id")
    suspend fun close(id: Long, endTs: Long)

    @Query("UPDATE recording_sessions SET isProcessed = 1 WHERE id = :id")
    suspend fun markProcessed(id: Long)
}

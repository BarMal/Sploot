package com.sploot.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sploot.data.entity.SleepSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SleepSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: SleepSessionEntity): Long

    @Update
    suspend fun update(session: SleepSessionEntity)

    @Query("SELECT * FROM sleep_sessions WHERE id = :id")
    suspend fun getById(id: Long): SleepSessionEntity?

    @Query("SELECT * FROM sleep_sessions ORDER BY startSeconds DESC")
    fun getAllFlow(): Flow<List<SleepSessionEntity>>

    @Query("SELECT * FROM sleep_sessions WHERE source = :source ORDER BY startSeconds DESC")
    suspend fun getBySource(source: String): List<SleepSessionEntity>

    /** Find all sessions (ALGO + GARMIN) overlapping a given date range. */
    @Query("""
        SELECT * FROM sleep_sessions
        WHERE startSeconds < :toSeconds AND endSeconds > :fromSeconds
        ORDER BY startSeconds ASC
    """)
    suspend fun getInRange(fromSeconds: Long, toSeconds: Long): List<SleepSessionEntity>

    @Query("DELETE FROM sleep_sessions WHERE id = :id")
    suspend fun deleteById(id: Long)
}

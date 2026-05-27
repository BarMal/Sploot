package com.sploot.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sploot.data.entity.SleepEpochEntity

@Dao
interface SleepEpochDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(epochs: List<SleepEpochEntity>)

    @Query("SELECT * FROM sleep_epochs WHERE sessionId = :sessionId AND source = :source ORDER BY epochStartSeconds ASC")
    suspend fun getBySessionAndSource(sessionId: Long, source: String): List<SleepEpochEntity>

    @Query("SELECT * FROM sleep_epochs WHERE sessionId = :sessionId ORDER BY epochStartSeconds ASC")
    suspend fun getBySession(sessionId: Long): List<SleepEpochEntity>

    @Query("DELETE FROM sleep_epochs WHERE sessionId = :sessionId")
    suspend fun deleteBySessionId(sessionId: Long)
}

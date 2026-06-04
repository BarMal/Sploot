package com.sploot.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sploot.data.entity.RawPpgEntity

@Dao
interface RawPpgDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(ppg: RawPpgEntity)

    @Query("SELECT * FROM raw_ppg WHERE sessionId = :sessionId ORDER BY tsSeconds ASC")
    suspend fun getBySession(sessionId: Long): List<RawPpgEntity>

    @Query("DELETE FROM raw_ppg WHERE tsSeconds < :cutoffSeconds")
    suspend fun deleteOlderThan(cutoffSeconds: Long): Int

    @Query("SELECT COUNT(*) FROM raw_ppg WHERE sessionId = :sessionId")
    suspend fun countForSession(sessionId: Long): Int

    @Query("SELECT MAX(tsSeconds) FROM raw_ppg")
    suspend fun getLatestTimestamp(): Long?
}

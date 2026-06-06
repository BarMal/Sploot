package com.sploot.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sploot.data.entity.RawImuEntity

@Dao
interface RawImuDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(imu: RawImuEntity)

    @Query("SELECT * FROM raw_imu WHERE sessionId = :sessionId ORDER BY tsSeconds ASC")
    suspend fun getBySession(sessionId: Long): List<RawImuEntity>

    /** Purge raw rows older than [cutoffSeconds] (7-day rolling retention). */
    @Query("DELETE FROM raw_imu WHERE tsSeconds < :cutoffSeconds")
    suspend fun deleteOlderThan(cutoffSeconds: Long): Int

    @Query("SELECT COUNT(*) FROM raw_imu WHERE sessionId = :sessionId")
    suspend fun countForSession(sessionId: Long): Int

    @Query("SELECT COUNT(*) FROM raw_imu WHERE tsSeconds = :tsSeconds")
    suspend fun countAtTimestamp(tsSeconds: Long): Int

    @Query("SELECT MAX(tsSeconds) FROM raw_imu")
    suspend fun getLatestTimestamp(): Long?
}

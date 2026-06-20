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

    @Query(
        """
        INSERT OR IGNORE INTO hr_samples(sessionId, tsSeconds, hrBpm)
        SELECT sessionId, tsSeconds, hrBpm
        FROM raw_imu
        WHERE hrBpm BETWEEN 30 AND 240
        """
    )
    suspend fun backfillMissingHrSamples()

    @Query(
        """
        SELECT COUNT(*)
        FROM raw_imu
        WHERE hrBpm BETWEEN 30 AND 240
            AND NOT EXISTS (
                SELECT 1
                FROM hr_samples
                WHERE hr_samples.sessionId = raw_imu.sessionId
                    AND hr_samples.tsSeconds = raw_imu.tsSeconds
            )
        """
    )
    suspend fun countMissingHrSamples(): Int
}

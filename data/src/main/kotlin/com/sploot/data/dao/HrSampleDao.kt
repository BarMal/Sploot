package com.sploot.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sploot.data.entity.HrSampleEntity

@Dao
interface HrSampleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sample: HrSampleEntity)

    @Query("SELECT * FROM hr_samples WHERE sessionId = :sessionId ORDER BY tsSeconds ASC")
    suspend fun getBySession(sessionId: Long): List<HrSampleEntity>

    @Query("SELECT AVG(hrBpm) FROM hr_samples WHERE sessionId = :sessionId AND tsSeconds BETWEEN :from AND :to")
    suspend fun averageHrInWindow(sessionId: Long, from: Long, to: Long): Float?

    @Query("SELECT * FROM hr_samples WHERE tsSeconds >= :fromSeconds ORDER BY tsSeconds ASC")
    suspend fun getSince(fromSeconds: Long): List<HrSampleEntity>

    @Query("SELECT COUNT(*) FROM hr_samples WHERE sessionId = :sessionId")
    suspend fun countForSession(sessionId: Long): Int

    @Query("SELECT COUNT(*) FROM hr_samples WHERE tsSeconds = :tsSeconds")
    suspend fun countAtTimestamp(tsSeconds: Long): Int

    @Query("SELECT MAX(tsSeconds) FROM hr_samples")
    suspend fun getLatestTimestamp(): Long?
}

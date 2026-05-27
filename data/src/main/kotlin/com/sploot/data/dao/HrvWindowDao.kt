package com.sploot.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sploot.data.entity.HrvWindowEntity

@Dao
interface HrvWindowDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(windows: List<HrvWindowEntity>)

    @Query("SELECT * FROM hrv_windows WHERE sessionId = :sessionId ORDER BY windowStartSeconds ASC")
    suspend fun getBySession(sessionId: Long): List<HrvWindowEntity>

    /** Returns the last-known overnight HRV window (used for recovery scoring). */
    @Query("SELECT * FROM hrv_windows ORDER BY windowStartSeconds DESC LIMIT 1")
    suspend fun getLatest(): HrvWindowEntity?

    /** Average RMSSD over the past [days] days for baseline computation. */
    @Query("""
        SELECT AVG(rmssd) FROM hrv_windows
        WHERE windowStartSeconds > :fromSeconds
    """)
    suspend fun averageRmssdSince(fromSeconds: Long): Float?

    /** All windows recorded after [fromSeconds] — used for 7-day sparkline. */
    @Query("SELECT * FROM hrv_windows WHERE windowStartSeconds > :fromSeconds ORDER BY windowStartSeconds ASC")
    suspend fun getWindowsSince(fromSeconds: Long): List<HrvWindowEntity>
}

package com.sploot.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sploot.data.entity.WhoopEventEntity

@Dao
interface WhoopEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: WhoopEventEntity)

    @Query("SELECT * FROM whoop_events WHERE sessionId = :sessionId ORDER BY tsSeconds ASC")
    suspend fun getBySession(sessionId: Long): List<WhoopEventEntity>

    @Query("SELECT * FROM whoop_events WHERE tsSeconds >= :fromSeconds AND eventType = :eventType ORDER BY tsSeconds ASC")
    suspend fun getByTypeSince(fromSeconds: Long, eventType: String): List<WhoopEventEntity>

    @Query("SELECT COUNT(*) FROM whoop_events WHERE sessionId = :sessionId")
    suspend fun countForSession(sessionId: Long): Int

    @Query("SELECT COUNT(*) FROM whoop_events WHERE tsSeconds = :tsSeconds AND eventType = :eventType")
    suspend fun countAtTimestamp(tsSeconds: Long, eventType: String): Int

    @Query("SELECT MAX(tsSeconds) FROM whoop_events")
    suspend fun getLatestTimestamp(): Long?
}

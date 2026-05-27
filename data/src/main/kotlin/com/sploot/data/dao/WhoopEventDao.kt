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
}

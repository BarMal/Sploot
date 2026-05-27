package com.sploot.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sploot.data.entity.ActivitySessionEntity

@Dao
interface ActivitySessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(sessions: List<ActivitySessionEntity>)

    @Query("SELECT naturalKey FROM activity_sessions WHERE naturalKey IN (:keys)")
    suspend fun getExistingKeys(keys: List<String>): List<String>
}

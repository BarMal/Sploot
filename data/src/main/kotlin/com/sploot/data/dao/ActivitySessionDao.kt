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

    @Query("SELECT * FROM activity_sessions ORDER BY startEpochSeconds DESC LIMIT :limit")
    suspend fun getLatest(limit: Int): List<ActivitySessionEntity>

    @Query("""
        SELECT * FROM activity_sessions
        WHERE startEpochSeconds < :toSeconds AND endEpochSeconds > :fromSeconds
        ORDER BY startEpochSeconds ASC
    """)
    suspend fun getInRange(fromSeconds: Long, toSeconds: Long): List<ActivitySessionEntity>

    @Query("SELECT * FROM activity_sessions WHERE naturalKey = :naturalKey LIMIT 1")
    suspend fun getByNaturalKey(naturalKey: String): ActivitySessionEntity?
}

package com.sploot.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sploot.data.entity.ActivityLapEntity

@Dao
interface ActivityLapDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(laps: List<ActivityLapEntity>)

    @Query("SELECT naturalKey FROM activity_laps WHERE naturalKey IN (:keys)")
    suspend fun getExistingKeys(keys: List<String>): List<String>

    @Query("""
        SELECT * FROM activity_laps
        WHERE activityNaturalKey = :activityNaturalKey
        ORDER BY startEpochSeconds ASC
    """)
    suspend fun getForActivity(activityNaturalKey: String): List<ActivityLapEntity>

    @Query("""
        SELECT * FROM activity_laps
        WHERE startEpochSeconds < :toSeconds AND endEpochSeconds > :fromSeconds
        ORDER BY startEpochSeconds ASC
    """)
    suspend fun getInRange(fromSeconds: Long, toSeconds: Long): List<ActivityLapEntity>
}

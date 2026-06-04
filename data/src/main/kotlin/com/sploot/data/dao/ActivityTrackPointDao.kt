package com.sploot.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sploot.data.entity.ActivityTrackPointEntity

@Dao
interface ActivityTrackPointDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(points: List<ActivityTrackPointEntity>)

    @Query("SELECT naturalKey FROM activity_track_points WHERE naturalKey IN (:keys)")
    suspend fun getExistingKeys(keys: List<String>): List<String>

    @Query("""
        SELECT * FROM activity_track_points
        WHERE activityNaturalKey = :activityNaturalKey
        ORDER BY tsSeconds ASC
    """)
    suspend fun getForActivity(activityNaturalKey: String): List<ActivityTrackPointEntity>

    @Query("""
        SELECT * FROM activity_track_points
        WHERE tsSeconds BETWEEN :fromSeconds AND :toSeconds
        ORDER BY tsSeconds ASC
    """)
    suspend fun getInRange(fromSeconds: Long, toSeconds: Long): List<ActivityTrackPointEntity>
}

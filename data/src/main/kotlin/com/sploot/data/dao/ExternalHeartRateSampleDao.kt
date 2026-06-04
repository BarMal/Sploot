package com.sploot.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sploot.data.entity.ExternalHeartRateSampleEntity

@Dao
interface ExternalHeartRateSampleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(samples: List<ExternalHeartRateSampleEntity>)

    @Query("SELECT naturalKey FROM external_heart_rate_samples WHERE naturalKey IN (:keys)")
    suspend fun getExistingKeys(keys: List<String>): List<String>

    @Query("""
        SELECT * FROM external_heart_rate_samples
        WHERE tsSeconds BETWEEN :fromSeconds AND :toSeconds
        ORDER BY tsSeconds ASC
    """)
    suspend fun getInRange(fromSeconds: Long, toSeconds: Long): List<ExternalHeartRateSampleEntity>
}

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
}

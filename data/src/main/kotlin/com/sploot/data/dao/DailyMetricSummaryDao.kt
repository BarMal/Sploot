package com.sploot.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sploot.data.entity.DailyMetricSummaryEntity

@Dao
interface DailyMetricSummaryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(metrics: List<DailyMetricSummaryEntity>)

    @Query("SELECT naturalKey FROM daily_metric_summaries WHERE naturalKey IN (:keys)")
    suspend fun getExistingKeys(keys: List<String>): List<String>
}

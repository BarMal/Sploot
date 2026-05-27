package com.sploot.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sploot.data.entity.GarminGroundTruthEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GarminGroundTruthDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: GarminGroundTruthEntity)

    @Query("SELECT * FROM garmin_ground_truth WHERE date = :date")
    suspend fun getByDate(date: String): GarminGroundTruthEntity?

    @Query("SELECT * FROM garmin_ground_truth ORDER BY date DESC")
    fun getAllFlow(): Flow<List<GarminGroundTruthEntity>>

    @Query("SELECT date FROM garmin_ground_truth ORDER BY date DESC")
    suspend fun getAllDates(): List<String>
}

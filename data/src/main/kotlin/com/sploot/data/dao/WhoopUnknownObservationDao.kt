package com.sploot.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sploot.data.entity.WhoopUnknownObservationEntity

@Dao
interface WhoopUnknownObservationDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: WhoopUnknownObservationEntity): Long

    @Update
    suspend fun update(entity: WhoopUnknownObservationEntity)

    @Query("SELECT * FROM whoop_unknown_observations WHERE signature = :signature LIMIT 1")
    suspend fun getBySignature(signature: String): WhoopUnknownObservationEntity?

    @Query("SELECT * FROM whoop_unknown_observations WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): WhoopUnknownObservationEntity?

    @Query("SELECT * FROM whoop_unknown_observations ORDER BY lastSeenSeconds DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 50): List<WhoopUnknownObservationEntity>
}

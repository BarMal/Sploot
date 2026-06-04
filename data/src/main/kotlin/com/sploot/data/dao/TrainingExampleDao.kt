package com.sploot.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sploot.data.entity.TrainingExampleEntity

@Dao
interface TrainingExampleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(example: TrainingExampleEntity): Long

    @Query(
        """
        SELECT * FROM training_examples
        WHERE family = :family
        ORDER BY exampleDate DESC, updatedAtMillis DESC
        """
    )
    suspend fun getByFamily(family: String): List<TrainingExampleEntity>

    @Query(
        """
        SELECT * FROM training_examples
        WHERE family = :family AND algorithmRevisionId = :algorithmRevisionId
        ORDER BY exampleDate DESC, updatedAtMillis DESC
        """
    )
    suspend fun getByFamilyAndRevision(
        family: String,
        algorithmRevisionId: Long,
    ): List<TrainingExampleEntity>

    @Query(
        """
        SELECT * FROM training_examples
        ORDER BY exampleDate DESC, updatedAtMillis DESC
        LIMIT :limit
        """
    )
    suspend fun getRecent(limit: Int): List<TrainingExampleEntity>

    @Query("SELECT COUNT(*) FROM training_examples WHERE family = :family")
    suspend fun countByFamily(family: String): Int
}

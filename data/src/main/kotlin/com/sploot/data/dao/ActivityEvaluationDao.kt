package com.sploot.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sploot.data.entity.ActivityEvaluationEntity

@Dao
interface ActivityEvaluationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(evaluation: ActivityEvaluationEntity): Long

    @Query("""
        DELETE FROM activity_evaluations
        WHERE algorithmRevisionId = :algorithmRevisionId
          AND algorithmActivityNaturalKey = :algorithmActivityNaturalKey
          AND garminActivityNaturalKey = :garminActivityNaturalKey
    """)
    suspend fun deleteForPair(
        algorithmRevisionId: Long,
        algorithmActivityNaturalKey: String,
        garminActivityNaturalKey: String,
    )

    @Query("""
        SELECT * FROM activity_evaluations
        WHERE algorithmRevisionId = :algorithmRevisionId
        ORDER BY createdAtMillis DESC
    """)
    suspend fun getByRevision(algorithmRevisionId: Long): List<ActivityEvaluationEntity>
}

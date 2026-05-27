package com.sploot.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sploot.data.entity.AlgorithmEvaluationEntity

@Dao
interface AlgorithmEvaluationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(evaluation: AlgorithmEvaluationEntity): Long

    @Query("DELETE FROM algorithm_evaluations WHERE family = :family AND algorithmSessionId = :algorithmSessionId AND garminSessionId = :garminSessionId")
    suspend fun deleteForPair(family: String, algorithmSessionId: Long, garminSessionId: Long)

    @Query("SELECT * FROM algorithm_evaluations WHERE family = :family AND algorithmRevisionId = :algorithmRevisionId ORDER BY createdAtMillis DESC")
    suspend fun getByRevision(family: String, algorithmRevisionId: Long): List<AlgorithmEvaluationEntity>
}

package com.sploot.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.sploot.data.entity.AlgorithmRevisionEntity

@Dao
interface AlgorithmRevisionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(revision: AlgorithmRevisionEntity): Long

    @Query("SELECT * FROM algorithm_revisions WHERE family = :family AND status = 'ACTIVE' ORDER BY version DESC LIMIT 1")
    suspend fun getActive(family: String): AlgorithmRevisionEntity?

    @Query("SELECT * FROM algorithm_revisions WHERE family = :family ORDER BY version DESC")
    suspend fun getAll(family: String): List<AlgorithmRevisionEntity>

    @Query("UPDATE algorithm_revisions SET status = 'RETIRED' WHERE family = :family AND status = 'ACTIVE'")
    suspend fun retireActive(family: String)

    @Query("UPDATE algorithm_revisions SET status = 'ACTIVE' WHERE id = :id")
    suspend fun activateById(id: Long)

    @Transaction
    suspend fun activate(family: String, id: Long) {
        retireActive(family)
        activateById(id)
    }
}

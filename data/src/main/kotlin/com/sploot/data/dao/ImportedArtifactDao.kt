package com.sploot.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sploot.data.entity.ImportedArtifactEntity

@Dao
interface ImportedArtifactDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(artifact: ImportedArtifactEntity)

    @Query("SELECT * FROM imported_artifacts WHERE fingerprint = :fingerprint")
    suspend fun getByFingerprint(fingerprint: String): ImportedArtifactEntity?
}

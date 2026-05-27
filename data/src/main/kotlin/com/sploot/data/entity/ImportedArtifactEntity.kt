package com.sploot.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "imported_artifacts")
data class ImportedArtifactEntity(
    @PrimaryKey val fingerprint: String,
    val source: String,
    val displayName: String,
    val mimeType: String?,
    val extension: String,
    val status: String,
    val notes: String?,
    val importedAtMillis: Long = System.currentTimeMillis(),
)

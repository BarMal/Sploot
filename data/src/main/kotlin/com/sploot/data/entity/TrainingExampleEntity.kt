package com.sploot.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "training_examples",
    indices = [
        Index(value = ["exampleKey"], unique = true),
        Index(value = ["family"]),
        Index(value = ["algorithmRevisionId"]),
        Index(value = ["exampleDate"]),
    ],
)
data class TrainingExampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exampleKey: String,
    val family: String,
    val algorithmRevisionId: Long?,
    val algorithmReference: String,
    val garminReference: String,
    val exampleDate: String,
    val featureJson: String,
    val labelJson: String,
    val evaluationJson: String?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

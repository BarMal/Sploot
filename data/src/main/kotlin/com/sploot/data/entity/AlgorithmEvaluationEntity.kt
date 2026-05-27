package com.sploot.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "algorithm_evaluations")
data class AlgorithmEvaluationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val family: String,
    val algorithmRevisionId: Long,
    val algorithmSessionId: Long,
    val garminSessionId: Long,
    val garminDate: String,
    val epochAccuracy: Float,
    val cohensKappa: Float,
    val scoreDelta: Int?,
    val matchedEpochCount: Int,
    val createdAtMillis: Long = System.currentTimeMillis(),
)

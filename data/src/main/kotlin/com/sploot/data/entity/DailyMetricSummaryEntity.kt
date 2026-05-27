package com.sploot.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_metric_summaries")
data class DailyMetricSummaryEntity(
    @PrimaryKey val naturalKey: String,
    val source: String,
    val date: String,
    val metricType: String,
    val numericValue: Float?,
    val textValue: String?,
    val unit: String?,
    val sourceFileFingerprint: String,
    val importedAtMillis: Long = System.currentTimeMillis(),
)

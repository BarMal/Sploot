package com.sploot.domain.model

data class DailyMetricSummary(
    val naturalKey: String,
    val source: String,
    val date: String,
    val metricType: String,
    val numericValue: Float?,
    val textValue: String?,
    val unit: String?,
)

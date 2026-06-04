package com.sploot.domain.model

data class TrainingExample(
    val id: Long,
    val exampleKey: String,
    val family: MetricFamily,
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

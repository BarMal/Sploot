package com.sploot.data.repository

import com.sploot.data.dao.TrainingExampleDao
import com.sploot.data.entity.TrainingExampleEntity
import com.sploot.domain.model.MetricFamily
import com.sploot.domain.model.TrainingExample
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrainingExampleRepository @Inject constructor(
    private val trainingExampleDao: TrainingExampleDao,
) {
    suspend fun save(example: TrainingExample): Long =
        trainingExampleDao.upsert(example.toEntity())

    suspend fun getExamples(
        family: MetricFamily,
        algorithmRevisionId: Long? = null,
    ): List<TrainingExample> =
        if (algorithmRevisionId == null) {
            trainingExampleDao.getByFamily(family.name)
        } else {
            trainingExampleDao.getByFamilyAndRevision(family.name, algorithmRevisionId)
        }.map { it.toDomain() }

    suspend fun countExamples(family: MetricFamily): Int =
        trainingExampleDao.countByFamily(family.name)

    suspend fun getRecentExamples(limit: Int): List<TrainingExample> =
        trainingExampleDao.getRecent(limit).map { it.toDomain() }

    private fun TrainingExample.toEntity() = TrainingExampleEntity(
        id = id,
        exampleKey = exampleKey,
        family = family.name,
        algorithmRevisionId = algorithmRevisionId,
        algorithmReference = algorithmReference,
        garminReference = garminReference,
        exampleDate = exampleDate,
        featureJson = featureJson,
        labelJson = labelJson,
        evaluationJson = evaluationJson,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
    )

    private fun TrainingExampleEntity.toDomain() = TrainingExample(
        id = id,
        exampleKey = exampleKey,
        family = MetricFamily.valueOf(family),
        algorithmRevisionId = algorithmRevisionId,
        algorithmReference = algorithmReference,
        garminReference = garminReference,
        exampleDate = exampleDate,
        featureJson = featureJson,
        labelJson = labelJson,
        evaluationJson = evaluationJson,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
    )
}

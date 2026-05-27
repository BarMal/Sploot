package com.sploot.data.repository

import com.sploot.data.dao.ActivitySessionDao
import com.sploot.data.dao.DailyMetricSummaryDao
import com.sploot.data.dao.ExternalHeartRateSampleDao
import com.sploot.data.dao.ImportedArtifactDao
import com.sploot.data.entity.ActivitySessionEntity
import com.sploot.data.entity.DailyMetricSummaryEntity
import com.sploot.data.entity.ExternalHeartRateSampleEntity
import com.sploot.data.entity.ImportedArtifactEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CanonicalImportRepository @Inject constructor(
    private val artifactDao: ImportedArtifactDao,
    private val activityDao: ActivitySessionDao,
    private val heartRateDao: ExternalHeartRateSampleDao,
    private val dailyMetricDao: DailyMetricSummaryDao,
) {

    data class UpsertResult(
        val inserted: Int,
        val updated: Int,
    )

    suspend fun getImportedArtifact(fingerprint: String): ImportedArtifactEntity? =
        artifactDao.getByFingerprint(fingerprint)

    suspend fun upsertImportedArtifact(artifact: ImportedArtifactEntity) {
        artifactDao.upsert(artifact)
    }

    suspend fun upsertActivitySessions(sessions: List<ActivitySessionEntity>): UpsertResult =
        upsertByNaturalKey(
            items = sessions,
            keyOf = { it.naturalKey },
            existingKeys = { activityDao.getExistingKeys(it) },
            persist = { activityDao.upsertAll(it) },
        )

    suspend fun upsertExternalHeartRateSamples(
        samples: List<ExternalHeartRateSampleEntity>,
    ): UpsertResult = upsertByNaturalKey(
        items = samples,
        keyOf = { it.naturalKey },
        existingKeys = { heartRateDao.getExistingKeys(it) },
        persist = { heartRateDao.upsertAll(it) },
    )

    suspend fun upsertDailyMetricSummaries(
        metrics: List<DailyMetricSummaryEntity>,
    ): UpsertResult = upsertByNaturalKey(
        items = metrics,
        keyOf = { it.naturalKey },
        existingKeys = { dailyMetricDao.getExistingKeys(it) },
        persist = { dailyMetricDao.upsertAll(it) },
    )

    private suspend fun <T> upsertByNaturalKey(
        items: List<T>,
        keyOf: (T) -> String,
        existingKeys: suspend (List<String>) -> List<String>,
        persist: suspend (List<T>) -> Unit,
    ): UpsertResult {
        if (items.isEmpty()) return UpsertResult(inserted = 0, updated = 0)
        val keys = items.map(keyOf)
        val existing = existingKeys(keys).toSet()
        persist(items)
        val inserted = keys.count { it !in existing }
        return UpsertResult(
            inserted = inserted,
            updated = items.size - inserted,
        )
    }
}

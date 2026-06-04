package com.sploot.data.repository

import com.sploot.data.dao.WhoopUnknownObservationDao
import com.sploot.data.entity.WhoopUnknownObservationEntity
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

data class UnknownObservationRecordResult(
    val entity: WhoopUnknownObservationEntity,
    val isNewSignature: Boolean,
)

@Singleton
class WhoopUnknownObservationRepository @Inject constructor(
    private val dao: WhoopUnknownObservationDao,
) {
    suspend fun recordObservation(
        sessionId: Long?,
        category: String,
        packetType: Int?,
        packetTypeName: String,
        identifier: Int?,
        identifierLabel: String,
        frameSizeBytes: Int,
        hexPreview: String,
        note: String?,
        observedAtSeconds: Long = Instant.now().epochSecond,
    ): UnknownObservationRecordResult {
        val signature = "$category:$packetTypeName:$identifierLabel:${frameSizeBytes}B"
        val existing = dao.getBySignature(signature)
        if (existing == null) {
            val inserted = WhoopUnknownObservationEntity(
                signature = signature,
                category = category,
                packetType = packetType,
                packetTypeName = packetTypeName,
                identifier = identifier,
                identifierLabel = identifierLabel,
                frameSizeBytes = frameSizeBytes,
                firstSeenSeconds = observedAtSeconds,
                lastSeenSeconds = observedAtSeconds,
                lastSessionId = sessionId,
                occurrenceCount = 1,
                sampleHexPreview = hexPreview,
                latestHexPreview = hexPreview,
                note = note,
                userAnnotation = null,
                annotatedAtSeconds = null,
            )
            val id = dao.insert(inserted)
            return UnknownObservationRecordResult(inserted.copy(id = id), isNewSignature = true)
        }

        val updated = existing.copy(
            lastSeenSeconds = observedAtSeconds,
            lastSessionId = sessionId,
            occurrenceCount = existing.occurrenceCount + 1,
            latestHexPreview = hexPreview,
            note = note ?: existing.note,
        )
        dao.update(updated)
        return UnknownObservationRecordResult(updated, isNewSignature = false)
    }

    suspend fun annotate(
        id: Long,
        annotation: String,
        annotatedAtSeconds: Long = Instant.now().epochSecond,
    ) {
        val current = dao.getById(id) ?: return
        dao.update(
            current.copy(
                userAnnotation = annotation.trim().take(MAX_ANNOTATION_CHARS),
                annotatedAtSeconds = annotatedAtSeconds,
            )
        )
    }

    companion object {
        private const val MAX_ANNOTATION_CHARS = 240
    }
}

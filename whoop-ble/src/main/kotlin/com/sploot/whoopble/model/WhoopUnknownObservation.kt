package com.sploot.whoopble.model

import java.time.Instant

data class WhoopUnknownObservation(
    val timestamp: Instant = Instant.now(),
    val category: UnknownObservationCategory,
    val packetType: Int?,
    val packetTypeName: String,
    val identifier: Int?,
    val identifierLabel: String,
    val frameSizeBytes: Int,
    val hexPreview: String,
    val note: String? = null,
)

enum class UnknownObservationCategory {
    EVENT,
    DATA_FRAME,
}

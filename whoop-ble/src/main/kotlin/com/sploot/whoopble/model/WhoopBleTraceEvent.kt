package com.sploot.whoopble.model

import java.time.Instant

data class WhoopBleTraceEvent(
    val timestamp: Instant = Instant.now(),
    val direction: TraceDirection,
    val channel: String,
    val summary: String,
    val sizeBytes: Int,
    val hexPreview: String,
)

enum class TraceDirection {
    OUTGOING,
    INCOMING,
    INTERNAL,
}

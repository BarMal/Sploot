package com.sploot.garminimport

import com.sploot.data.entity.ActivitySessionEntity
import com.sploot.data.entity.ActivityLapEntity
import com.sploot.data.entity.ActivityTrackPointEntity
import com.sploot.data.entity.ExternalHeartRateSampleEntity
import com.sploot.data.repository.CanonicalImportRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import timber.log.Timber

@Singleton
class GarminFitParser @Inject constructor(
    private val canonicalImportRepository: CanonicalImportRepository,
) {

    data class ImportResult(
        val inserted: Int,
        val updated: Int,
        val details: String,
    )

    suspend fun parseAndStore(
        bytes: ByteArray,
        sourceFileFingerprint: String,
        displayName: String,
    ): ImportResult {
        val parsed = parse(bytes, sourceFileFingerprint, displayName)

        val activityResult = canonicalImportRepository.upsertActivitySessions(parsed.activitySessions)
        val lapResult = canonicalImportRepository.upsertActivityLaps(parsed.laps)
        val trackPointResult = canonicalImportRepository.upsertActivityTrackPoints(parsed.trackPoints)
        val heartRateResult = canonicalImportRepository.upsertExternalHeartRateSamples(parsed.heartRateSamples)

        return ImportResult(
            inserted = activityResult.inserted + lapResult.inserted + trackPointResult.inserted + heartRateResult.inserted,
            updated = activityResult.updated + lapResult.updated + trackPointResult.updated + heartRateResult.updated,
            details =
                "Parsed ${parsed.activitySessions.size} activity sessions, " +
                    "${parsed.laps.size} laps, " +
                    "${parsed.trackPoints.size} track points, and " +
                    "${parsed.heartRateSamples.size} heart-rate samples",
        )
    }

    private fun parse(
        bytes: ByteArray,
        sourceFileFingerprint: String,
        displayName: String,
    ): ParsedFitFile {
        require(bytes.size >= 12) { "FIT file too small" }

        val headerSize = bytes[0].toInt() and 0xFF
        require(headerSize in 12..14) { "Unsupported FIT header size: $headerSize" }
        require(bytes.size >= headerSize) { "Truncated FIT header" }
        require(bytes.copyOfRange(8, 12).decodeToString() == ".FIT") { "Invalid FIT signature" }

        val dataSize = readUInt16Le(bytes, 4) or (readUInt16Le(bytes, 6) shl 16)
        val dataEnd = (headerSize + dataSize).coerceAtMost(bytes.size)

        val definitions = arrayOfNulls<MessageDefinition>(16)
        val sessions = mutableListOf<ActivitySessionEntity>()
        val laps = mutableListOf<ActivityLapEntity>()
        val recordSamples = mutableListOf<RecordSample>()
        var index = headerSize
        var lastFitTimestamp: Long? = null
        var syntheticSession: SessionAccumulator? = null
        var sessionCounter = 0
        var recordCounter = 0

        while (index < dataEnd) {
            val header = bytes[index].toInt() and 0xFF
            index++

            if ((header and 0x80) != 0) {
                val localMessageType = (header shr 5) and 0x03
                val timeOffset = header and 0x1F
                val definition = definitions[localMessageType]
                    ?: throw IllegalArgumentException("Compressed FIT record references undefined local message $localMessageType")
                val values = readDataValues(bytes, index, definition)
                index += definition.recordSize

                val expandedFitTimestamp = expandCompressedTimestamp(lastFitTimestamp, timeOffset)
                values[FIELD_TIMESTAMP] = expandedFitTimestamp
                lastFitTimestamp = expandedFitTimestamp

                handleDataMessage(
                    globalMessageNumber = definition.globalMessageNumber,
                    values = values,
                    sourceFileFingerprint = sourceFileFingerprint,
                    onSession = {
                        sessions += it.copy(
                            naturalKey = "GARMIN-FIT:${it.startEpochSeconds}:${it.endEpochSeconds}:${it.activityType.orEmpty()}:${sessionCounter++}"
                        )
                    },
                    onLap = { lap ->
                        laps += lap
                    },
                    onRecord = { record ->
                        recordSamples += record.copy(recordIndex = recordCounter++)
                        if (record.hrBpm != null) {
                            syntheticSession = (syntheticSession ?: SessionAccumulator()).update(record.tsSeconds, record.hrBpm)
                        }
                    },
                )
                continue
            }

            val isDefinitionMessage = (header and 0x40) != 0
            val hasDeveloperData = (header and 0x20) != 0
            val localMessageType = header and 0x0F

            if (isDefinitionMessage) {
                val reserved = bytes[index].toInt() and 0xFF
                val architecture = bytes[index + 1].toInt() and 0xFF
                val littleEndian = architecture == 0
                val globalMessageNumber = readUInt16(bytes, index + 2, littleEndian)
                val fieldCount = bytes[index + 4].toInt() and 0xFF
                index += 5

                val fields = buildList {
                    repeat(fieldCount) {
                        val fieldNumber = bytes[index].toInt() and 0xFF
                        val size = bytes[index + 1].toInt() and 0xFF
                        val baseType = bytes[index + 2].toInt() and 0xFF
                        add(FieldDefinition(fieldNumber, size, baseType))
                        index += 3
                    }
                }

                if (hasDeveloperData) {
                    val developerFieldCount = bytes[index].toInt() and 0xFF
                    index++
                    index += developerFieldCount * 3
                }

                definitions[localMessageType] = MessageDefinition(
                    globalMessageNumber = globalMessageNumber,
                    littleEndian = littleEndian,
                    fields = fields,
                )
            } else {
                val definition = definitions[localMessageType]
                    ?: throw IllegalArgumentException("FIT data record references undefined local message $localMessageType")

                val values = readDataValues(bytes, index, definition)
                index += definition.recordSize
                values[FIELD_TIMESTAMP]?.let { lastFitTimestamp = it }

                handleDataMessage(
                    globalMessageNumber = definition.globalMessageNumber,
                    values = values,
                    sourceFileFingerprint = sourceFileFingerprint,
                    onSession = {
                        sessions += it.copy(
                            naturalKey = "GARMIN-FIT:${it.startEpochSeconds}:${it.endEpochSeconds}:${it.activityType.orEmpty()}:${sessionCounter++}"
                        )
                    },
                    onLap = { lap ->
                        laps += lap
                    },
                    onRecord = { record ->
                        recordSamples += record.copy(recordIndex = recordCounter++)
                        if (record.hrBpm != null) {
                            syntheticSession = (syntheticSession ?: SessionAccumulator()).update(record.tsSeconds, record.hrBpm)
                        }
                    },
                )
            }
        }

        if (sessions.isEmpty()) {
            syntheticSession?.toActivitySession(sourceFileFingerprint, sessionCounter)?.let { sessions += it }
        }

        val heartRateSamples = recordSamples.mapNotNull { sample ->
            sample.hrBpm?.takeIf { it > 0 }?.let { hr ->
                ExternalHeartRateSampleEntity(
                    naturalKey = "GARMIN-FIT-HR:${sourceFileFingerprint}:${sample.recordIndex}",
                    source = "GARMIN",
                    tsSeconds = sample.tsSeconds,
                    hrBpm = hr,
                    sourceFileFingerprint = sourceFileFingerprint,
                )
            }
        }
        val trackPoints = recordSamples.mapNotNull { sample ->
            val latitudeDegrees = sample.latitudeDegrees ?: return@mapNotNull null
            val longitudeDegrees = sample.longitudeDegrees ?: return@mapNotNull null
            ActivityTrackPointEntity(
                naturalKey = "GARMIN-FIT-TRACK:${sourceFileFingerprint}:${sample.recordIndex}",
                source = "GARMIN",
                activityNaturalKey = findOwningActivityNaturalKey(sample.tsSeconds, sessions),
                tsSeconds = sample.tsSeconds,
                latitudeDegrees = latitudeDegrees,
                longitudeDegrees = longitudeDegrees,
                altitudeMeters = sample.altitudeMeters,
                distanceMeters = sample.distanceMeters,
                speedMetersPerSecond = sample.speedMetersPerSecond,
                sourceFileFingerprint = sourceFileFingerprint,
            )
        }

        Timber.d(
            "Parsed FIT file $displayName into ${sessions.size} sessions, ${laps.size} laps, ${trackPoints.size} track points and ${heartRateSamples.size} HR samples"
        )
        return ParsedFitFile(
            activitySessions = sessions,
            laps = laps.map { lap ->
                lap.copy(activityNaturalKey = lap.activityNaturalKey ?: findOwningActivityNaturalKey(lap.startEpochSeconds, sessions))
            },
            trackPoints = trackPoints,
            heartRateSamples = heartRateSamples,
        )
    }

    private fun handleDataMessage(
        globalMessageNumber: Int,
        values: MutableMap<Int, Long>,
        sourceFileFingerprint: String,
        onSession: (ActivitySessionEntity) -> Unit,
        onLap: (ActivityLapEntity) -> Unit,
        onRecord: (RecordSample) -> Unit,
    ) {
        when (globalMessageNumber) {
            GLOBAL_MESSAGE_SESSION -> {
                val session = buildActivitySession(values, sourceFileFingerprint)
                if (session != null) onSession(session)
            }
            GLOBAL_MESSAGE_LAP -> {
                val lap = buildLap(values, sourceFileFingerprint)
                if (lap != null) onLap(lap)
            }
            GLOBAL_MESSAGE_RECORD -> {
                buildRecordSample(values)?.let(onRecord)
            }
        }
    }

    private fun buildActivitySession(
        values: Map<Int, Long>,
        sourceFileFingerprint: String,
    ): ActivitySessionEntity? {
        val startEpochSeconds = values[FIELD_SESSION_START_TIME]?.fitToUnixEpoch()
        val endEpochSeconds = values[FIELD_TIMESTAMP]?.fitToUnixEpoch()
            ?: values[FIELD_SESSION_TOTAL_ELAPSED_TIME]?.let { elapsed ->
                startEpochSeconds?.plus((elapsed.toDouble() / 1000.0).roundToInt())
            }

        if (startEpochSeconds == null || endEpochSeconds == null || endEpochSeconds <= startEpochSeconds) return null

        val activityType = mapSport(values[FIELD_SESSION_SPORT]?.toInt())
        return ActivitySessionEntity(
            naturalKey = "GARMIN-FIT:$startEpochSeconds:$endEpochSeconds:${activityType.orEmpty()}",
            source = "GARMIN",
            externalId = null,
            activityType = activityType,
            title = activityType?.replaceFirstChar { it.uppercase() },
            startEpochSeconds = startEpochSeconds,
            endEpochSeconds = endEpochSeconds,
            avgHrBpm = values[FIELD_SESSION_AVG_HEART_RATE]?.toFloat(),
            maxHrBpm = values[FIELD_SESSION_MAX_HEART_RATE]?.toFloat(),
            caloriesKcal = values[FIELD_SESSION_TOTAL_CALORIES]?.toFloat(),
            distanceMeters = values[FIELD_SESSION_TOTAL_DISTANCE]?.let { it.toFloat() / 100f },
            sourceFileFingerprint = sourceFileFingerprint,
        )
    }

    private fun buildRecordSample(values: Map<Int, Long>): RecordSample? {
        val tsSeconds = values[FIELD_TIMESTAMP]?.fitToUnixEpoch() ?: return null
        return RecordSample(
            recordIndex = -1,
            tsSeconds = tsSeconds,
            hrBpm = values[FIELD_RECORD_HEART_RATE]?.toInt()?.takeIf { it > 0 },
            latitudeDegrees = values[FIELD_RECORD_POSITION_LAT]?.semicirclesToDegrees(),
            longitudeDegrees = values[FIELD_RECORD_POSITION_LONG]?.semicirclesToDegrees(),
            altitudeMeters = values[FIELD_RECORD_ALTITUDE]?.fitAltitudeMeters(),
            distanceMeters = values[FIELD_RECORD_DISTANCE]?.let { it.toFloat() / 100f },
            speedMetersPerSecond = values[FIELD_RECORD_SPEED]?.let { it.toFloat() / 1000f },
        )
    }

    private fun buildLap(
        values: Map<Int, Long>,
        sourceFileFingerprint: String,
    ): ActivityLapEntity? {
        val startEpochSeconds = values[FIELD_LAP_START_TIME]?.fitToUnixEpoch() ?: return null
        val endEpochSeconds = values[FIELD_TIMESTAMP]?.fitToUnixEpoch()
            ?: values[FIELD_LAP_TOTAL_ELAPSED_TIME]?.let { elapsed ->
                startEpochSeconds + (elapsed.toDouble() / 1000.0).roundToInt()
            }
            ?: return null
        if (endEpochSeconds <= startEpochSeconds) return null

        val activityType = mapSport(values[FIELD_LAP_SPORT]?.toInt())
        val lapIndex = values[FIELD_MESSAGE_INDEX]?.toInt()
        return ActivityLapEntity(
            naturalKey = "GARMIN-FIT-LAP:$startEpochSeconds:$endEpochSeconds:${lapIndex ?: -1}:$sourceFileFingerprint",
            source = "GARMIN",
            activityNaturalKey = null,
            lapIndex = lapIndex,
            activityType = activityType,
            startEpochSeconds = startEpochSeconds,
            endEpochSeconds = endEpochSeconds,
            distanceMeters = values[FIELD_LAP_TOTAL_DISTANCE]?.let { it.toFloat() / 100f },
            caloriesKcal = values[FIELD_LAP_TOTAL_CALORIES]?.toFloat(),
            avgHrBpm = values[FIELD_LAP_AVG_HEART_RATE]?.toFloat(),
            maxHrBpm = values[FIELD_LAP_MAX_HEART_RATE]?.toFloat(),
            avgSpeedMetersPerSecond = values[FIELD_LAP_AVG_SPEED]?.let { it.toFloat() / 1000f },
            maxSpeedMetersPerSecond = values[FIELD_LAP_MAX_SPEED]?.let { it.toFloat() / 1000f },
            sourceFileFingerprint = sourceFileFingerprint,
        )
    }

    private fun readDataValues(
        bytes: ByteArray,
        offset: Int,
        definition: MessageDefinition,
    ): MutableMap<Int, Long> {
        val values = mutableMapOf<Int, Long>()
        var cursor = offset
        definition.fields.forEach { field ->
            val value = readFieldValue(bytes, cursor, field, definition.littleEndian)
            if (value != null) values[field.fieldNumber] = value
            cursor += field.size
        }
        return values
    }

    private fun readFieldValue(
        bytes: ByteArray,
        offset: Int,
        field: FieldDefinition,
        littleEndian: Boolean,
    ): Long? {
        return when (field.baseType and 0x1F) {
            BASE_TYPE_ENUM,
            BASE_TYPE_UINT8,
            BASE_TYPE_UINT8Z,
            BASE_TYPE_BYTE -> {
                if (field.size != 1) null
                else {
                    val value = bytes[offset].toInt() and 0xFF
                    if (value == 0xFF) null else value.toLong()
                }
            }
            BASE_TYPE_SINT8 -> {
                if (field.size != 1) null
                else {
                    val value = bytes[offset].toInt()
                    if (value == 0x7F) null else value.toLong()
                }
            }
            BASE_TYPE_UINT16,
            BASE_TYPE_UINT16Z -> {
                if (field.size != 2) null
                else {
                    val value = readUInt16(bytes, offset, littleEndian)
                    if (value == 0xFFFF) null else value.toLong()
                }
            }
            BASE_TYPE_SINT16 -> {
                if (field.size != 2) null
                else {
                    val value = readInt16(bytes, offset, littleEndian)
                    if (value == 0x7FFF) null else value.toLong()
                }
            }
            BASE_TYPE_UINT32,
            BASE_TYPE_UINT32Z -> {
                if (field.size != 4) null
                else {
                    val value = readUInt32(bytes, offset, littleEndian)
                    if (value == 0xFFFF_FFFFL) null else value
                }
            }
            BASE_TYPE_SINT32 -> {
                if (field.size != 4) null
                else {
                    val value = readInt32(bytes, offset, littleEndian)
                    if (value == Int.MAX_VALUE) null else value.toLong()
                }
            }
            else -> null
        }
    }

    private fun readUInt16(bytes: ByteArray, offset: Int, littleEndian: Boolean): Int =
        if (littleEndian) readUInt16Le(bytes, offset)
        else ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)

    private fun readInt16(bytes: ByteArray, offset: Int, littleEndian: Boolean): Int =
        readUInt16(bytes, offset, littleEndian).toShort().toInt()

    private fun readUInt32(bytes: ByteArray, offset: Int, littleEndian: Boolean): Long {
        val b0: Long
        val b1: Long
        val b2: Long
        val b3: Long
        if (littleEndian) {
            b0 = (bytes[offset].toInt() and 0xFF).toLong()
            b1 = (bytes[offset + 1].toInt() and 0xFF).toLong()
            b2 = (bytes[offset + 2].toInt() and 0xFF).toLong()
            b3 = (bytes[offset + 3].toInt() and 0xFF).toLong()
            return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
        }
        b0 = (bytes[offset].toInt() and 0xFF).toLong()
        b1 = (bytes[offset + 1].toInt() and 0xFF).toLong()
        b2 = (bytes[offset + 2].toInt() and 0xFF).toLong()
        b3 = (bytes[offset + 3].toInt() and 0xFF).toLong()
        return (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
    }

    private fun readInt32(bytes: ByteArray, offset: Int, littleEndian: Boolean): Int =
        readUInt32(bytes, offset, littleEndian).toInt()

    private fun readUInt16Le(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    private fun expandCompressedTimestamp(lastFitTimestamp: Long?, timeOffset: Int): Long {
        val last = lastFitTimestamp ?: return timeOffset.toLong()
        var candidate = (last and COMPRESSED_TIMESTAMP_MASK.inv()) or timeOffset.toLong()
        if (candidate < last) candidate += COMPRESSED_TIMESTAMP_ROLLOVER
        return candidate
    }

    private fun Long.fitToUnixEpoch(): Long = this + FIT_EPOCH_OFFSET_SECONDS
    private fun Long.semicirclesToDegrees(): Double = this.toDouble() * (180.0 / 2147483648.0)
    private fun Long.fitAltitudeMeters(): Float = (this.toFloat() / 5f) - 500f

    private fun findOwningActivityNaturalKey(
        tsSeconds: Long,
        sessions: List<ActivitySessionEntity>,
    ): String? {
        sessions.firstOrNull { tsSeconds in it.startEpochSeconds..it.endEpochSeconds }?.let { return it.naturalKey }
        return sessions.minByOrNull { session ->
            when {
                tsSeconds < session.startEpochSeconds -> session.startEpochSeconds - tsSeconds
                tsSeconds > session.endEpochSeconds -> tsSeconds - session.endEpochSeconds
                else -> 0L
            }
        }?.takeIf {
            tsSeconds >= it.startEpochSeconds - TRACK_POINT_SESSION_GRACE_SECONDS &&
                tsSeconds <= it.endEpochSeconds + TRACK_POINT_SESSION_GRACE_SECONDS
        }?.naturalKey
    }

    private fun mapSport(sportCode: Int?): String? = when (sportCode) {
        0 -> "generic"
        1 -> "running"
        2 -> "cycling"
        3 -> "transition"
        4 -> "fitness_equipment"
        5 -> "swimming"
        6 -> "basketball"
        7 -> "soccer"
        8 -> "tennis"
        9 -> "american_football"
        10 -> "training"
        11 -> "walking"
        13 -> "hiking"
        14 -> "multisport"
        15 -> "paddling"
        16 -> "flying"
        17 -> "e_biking"
        18 -> "motorcycling"
        19 -> "boating"
        20 -> "driving"
        21 -> "golf"
        22 -> "hang_gliding"
        23 -> "horseback_riding"
        24 -> "hunting"
        25 -> "fishing"
        26 -> "inline_skating"
        27 -> "rock_climbing"
        28 -> "sailing"
        29 -> "ice_skating"
        30 -> "sky_diving"
        31 -> "snowboarding"
        32 -> "snowmobiling"
        33 -> "stand_up_paddleboarding"
        34 -> "surfing"
        35 -> "wakeboarding"
        36 -> "water_skiing"
        37 -> "kayaking"
        38 -> "rafting"
        39 -> "windsurfing"
        40 -> "kitesurfing"
        41 -> "tactical"
        42 -> "jumpmaster"
        43 -> "boxing"
        44 -> "floor_climbing"
        45 -> "baseball"
        46 -> "diving"
        47 -> "hiit"
        48 -> "racket"
        49 -> "wheelchair_push_walk"
        50 -> "wheelchair_push_run"
        51 -> "meditation"
        else -> null
    }

    private data class ParsedFitFile(
        val activitySessions: List<ActivitySessionEntity>,
        val laps: List<ActivityLapEntity>,
        val trackPoints: List<ActivityTrackPointEntity>,
        val heartRateSamples: List<ExternalHeartRateSampleEntity>,
    )

    private data class RecordSample(
        val recordIndex: Int,
        val tsSeconds: Long,
        val hrBpm: Int?,
        val latitudeDegrees: Double?,
        val longitudeDegrees: Double?,
        val altitudeMeters: Float?,
        val distanceMeters: Float?,
        val speedMetersPerSecond: Float?,
    )

    private data class MessageDefinition(
        val globalMessageNumber: Int,
        val littleEndian: Boolean,
        val fields: List<FieldDefinition>,
    ) {
        val recordSize: Int = fields.sumOf { it.size }
    }

    private data class FieldDefinition(
        val fieldNumber: Int,
        val size: Int,
        val baseType: Int,
    )

    private data class SessionAccumulator(
        val startEpochSeconds: Long? = null,
        val endEpochSeconds: Long? = null,
        val maxHrBpm: Int = 0,
        val hrSum: Long = 0L,
        val hrCount: Int = 0,
    ) {
        fun update(tsSeconds: Long, hrBpm: Int) = copy(
            startEpochSeconds = minOfNullable(startEpochSeconds, tsSeconds),
            endEpochSeconds = maxOfNullable(endEpochSeconds, tsSeconds),
            maxHrBpm = maxOf(maxHrBpm, hrBpm),
            hrSum = hrSum + hrBpm,
            hrCount = hrCount + 1,
        )

        fun toActivitySession(sourceFileFingerprint: String, sessionCounter: Int): ActivitySessionEntity? {
            val start = startEpochSeconds ?: return null
            val end = endEpochSeconds ?: return null
            if (end <= start) return null
            return ActivitySessionEntity(
                naturalKey = "GARMIN-FIT:$start:$end:unknown:$sessionCounter",
                source = "GARMIN",
                externalId = null,
                activityType = null,
                title = "Imported Activity",
                startEpochSeconds = start,
                endEpochSeconds = end,
                avgHrBpm = if (hrCount > 0) hrSum.toFloat() / hrCount else null,
                maxHrBpm = maxHrBpm.takeIf { it > 0 }?.toFloat(),
                caloriesKcal = null,
                distanceMeters = null,
                sourceFileFingerprint = sourceFileFingerprint,
            )
        }
    }

    companion object {
        private const val FIT_EPOCH_OFFSET_SECONDS = 631065600L
        private const val COMPRESSED_TIMESTAMP_MASK = 0x1FL
        private const val COMPRESSED_TIMESTAMP_ROLLOVER = 0x20L

        private const val GLOBAL_MESSAGE_SESSION = 18
        private const val GLOBAL_MESSAGE_LAP = 19
        private const val GLOBAL_MESSAGE_RECORD = 20

        private const val FIELD_MESSAGE_INDEX = 254
        private const val FIELD_TIMESTAMP = 253
        private const val FIELD_SESSION_START_TIME = 2
        private const val FIELD_SESSION_SPORT = 5
        private const val FIELD_SESSION_TOTAL_ELAPSED_TIME = 7
        private const val FIELD_SESSION_TOTAL_DISTANCE = 9
        private const val FIELD_SESSION_TOTAL_CALORIES = 11
        private const val FIELD_SESSION_AVG_HEART_RATE = 16
        private const val FIELD_SESSION_MAX_HEART_RATE = 17
        private const val FIELD_LAP_START_TIME = 2
        private const val FIELD_LAP_TOTAL_ELAPSED_TIME = 7
        private const val FIELD_LAP_TOTAL_DISTANCE = 9
        private const val FIELD_LAP_TOTAL_CALORIES = 11
        private const val FIELD_LAP_AVG_SPEED = 13
        private const val FIELD_LAP_MAX_SPEED = 14
        private const val FIELD_LAP_AVG_HEART_RATE = 15
        private const val FIELD_LAP_MAX_HEART_RATE = 16
        private const val FIELD_LAP_SPORT = 25
        private const val FIELD_RECORD_POSITION_LAT = 0
        private const val FIELD_RECORD_POSITION_LONG = 1
        private const val FIELD_RECORD_ALTITUDE = 2
        private const val FIELD_RECORD_HEART_RATE = 3
        private const val FIELD_RECORD_DISTANCE = 5
        private const val FIELD_RECORD_SPEED = 6

        private const val TRACK_POINT_SESSION_GRACE_SECONDS = 300L

        private const val BASE_TYPE_ENUM = 0x00
        private const val BASE_TYPE_SINT8 = 0x01
        private const val BASE_TYPE_UINT8 = 0x02
        private const val BASE_TYPE_SINT16 = 0x03
        private const val BASE_TYPE_UINT16 = 0x04
        private const val BASE_TYPE_SINT32 = 0x05
        private const val BASE_TYPE_UINT32 = 0x06
        private const val BASE_TYPE_UINT8Z = 0x0A
        private const val BASE_TYPE_UINT16Z = 0x0B
        private const val BASE_TYPE_UINT32Z = 0x0C
        private const val BASE_TYPE_BYTE = 0x0D

        private fun minOfNullable(existing: Long?, candidate: Long): Long = existing?.coerceAtMost(candidate) ?: candidate
        private fun maxOfNullable(existing: Long?, candidate: Long): Long = existing?.coerceAtLeast(candidate) ?: candidate
    }
}

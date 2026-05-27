package com.sploot.garminimport

import com.sploot.data.entity.ActivitySessionEntity
import com.sploot.data.entity.DailyMetricSummaryEntity
import com.sploot.data.entity.ExternalHeartRateSampleEntity
import com.sploot.data.repository.CanonicalImportRepository
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
class GarminCsvParser @Inject constructor(
    private val canonicalImportRepository: CanonicalImportRepository,
) {

    data class ImportResult(
        val fileType: String,
        val inserted: Int,
        val updated: Int,
        val details: String,
    )

    suspend fun parseAndStore(
        content: String,
        sourceFileFingerprint: String,
        displayName: String,
    ): ImportResult {
        val rows = parseCsv(content)
        if (rows.isEmpty()) {
            return ImportResult(
                fileType = "unknown",
                inserted = 0,
                updated = 0,
                details = "CSV was empty",
            )
        }

        val normalizedHeaders = rows.first().keys
        return when {
            looksLikeHeartRate(normalizedHeaders) -> importHeartRate(rows, sourceFileFingerprint)
            looksLikeActivity(normalizedHeaders) -> importActivities(rows, sourceFileFingerprint)
            looksLikeDailyMetrics(normalizedHeaders) -> importDailyMetrics(rows, sourceFileFingerprint)
            else -> {
                Timber.w("Unsupported Garmin CSV headers in $displayName: $normalizedHeaders")
                ImportResult(
                    fileType = "unknown",
                    inserted = 0,
                    updated = 0,
                    details = "Unsupported CSV shape",
                )
            }
        }
    }

    private suspend fun importHeartRate(
        rows: List<Map<String, String>>,
        sourceFileFingerprint: String,
    ): ImportResult {
        val samples = rows.mapNotNull { row ->
            val timestamp = parseTimestamp(row) ?: return@mapNotNull null
            val hr = row.firstValue(
                "heartrate",
                "heart_rate",
                "hr",
                "bpm",
                "averageheartrate",
            )?.toFloatOrNull()?.toInt() ?: return@mapNotNull null

            ExternalHeartRateSampleEntity(
                naturalKey = "GARMIN:$timestamp",
                source = "GARMIN",
                tsSeconds = timestamp,
                hrBpm = hr,
                sourceFileFingerprint = sourceFileFingerprint,
            )
        }

        val result = canonicalImportRepository.upsertExternalHeartRateSamples(samples)
        return ImportResult(
            fileType = "heart_rate",
            inserted = result.inserted,
            updated = result.updated,
            details = "Processed ${samples.size} heart-rate samples",
        )
    }

    private suspend fun importActivities(
        rows: List<Map<String, String>>,
        sourceFileFingerprint: String,
    ): ImportResult {
        val sessions = rows.mapNotNull { row ->
            val start = parseTimestamp(row) ?: return@mapNotNull null
            val end = parseEndTimestamp(row, start) ?: return@mapNotNull null
            val externalId = row.firstValue("activityid", "activity_id", "id", "sessionid")
            val activityType = row.firstValue("activitytype", "activity_type", "type", "sport", "activity")
            val title = row.firstValue("title", "name", "activityname", "activity_name")
            val avgHr = row.firstValue("averageheartrate", "avgheartrate", "avg_hr", "hraverage")?.toFloatOrNull()
            val maxHr = row.firstValue("maximumheartrate", "maxheartrate", "max_hr", "hrmax")?.toFloatOrNull()
            val calories = row.firstValue("calories", "activecalories", "energykcal")?.toFloatOrNull()
            val distance = row.firstValue("distance", "distancemeters", "distance_meters", "totaldistance")?.toFloatOrNull()
            val naturalKey = buildString {
                append("GARMIN:")
                append(externalId ?: "${activityType.orEmpty()}:$start:$end")
            }

            ActivitySessionEntity(
                naturalKey = naturalKey,
                source = "GARMIN",
                externalId = externalId,
                activityType = activityType,
                title = title,
                startEpochSeconds = start,
                endEpochSeconds = end,
                avgHrBpm = avgHr,
                maxHrBpm = maxHr,
                caloriesKcal = calories,
                distanceMeters = distance,
                sourceFileFingerprint = sourceFileFingerprint,
            )
        }

        val result = canonicalImportRepository.upsertActivitySessions(sessions)
        return ImportResult(
            fileType = "activity",
            inserted = result.inserted,
            updated = result.updated,
            details = "Processed ${sessions.size} activity sessions",
        )
    }

    private suspend fun importDailyMetrics(
        rows: List<Map<String, String>>,
        sourceFileFingerprint: String,
    ): ImportResult {
        val metrics = buildList {
            rows.forEach { row ->
                val date = parseDate(row) ?: return@forEach
                row.forEach { (header, value) ->
                    if (header in DAILY_METRIC_IGNORED_HEADERS || value.isBlank()) return@forEach
                    val normalizedMetric = header
                    val numericValue = value.toFloatOrNull()
                    add(
                        DailyMetricSummaryEntity(
                            naturalKey = "GARMIN:$date:$normalizedMetric",
                            source = "GARMIN",
                            date = date,
                            metricType = normalizedMetric,
                            numericValue = numericValue,
                            textValue = if (numericValue == null) value else null,
                            unit = guessUnit(normalizedMetric),
                            sourceFileFingerprint = sourceFileFingerprint,
                        )
                    )
                }
            }
        }

        val result = canonicalImportRepository.upsertDailyMetricSummaries(metrics)
        return ImportResult(
            fileType = "daily_metrics",
            inserted = result.inserted,
            updated = result.updated,
            details = "Processed ${metrics.size} daily metric values",
        )
    }

    private fun looksLikeHeartRate(headers: Set<String>): Boolean =
        headers.containsAny("heartrate", "heart_rate", "hr", "bpm") &&
            headers.containsAny("timestamp", "datetime", "time", "date")

    private fun looksLikeActivity(headers: Set<String>): Boolean =
        headers.containsAny("duration", "elapsedtime", "movingtime") &&
            headers.containsAny("starttime", "start", "begintime", "timestamp", "date")

    private fun looksLikeDailyMetrics(headers: Set<String>): Boolean =
        headers.containsAny("date", "calendardate") && headers.any { it !in DAILY_METRIC_IGNORED_HEADERS }

    private fun parseCsv(content: String): List<Map<String, String>> {
        val lines = content.lineSequence()
            .map { it.trimEnd('\r') }
            .filter { it.isNotBlank() }
            .toList()
        if (lines.isEmpty()) return emptyList()

        val headers = parseCsvLine(lines.first()).map { normalizeHeader(it) }
        return lines.drop(1).mapNotNull { line ->
            val cells = parseCsvLine(line)
            if (cells.isEmpty()) return@mapNotNull null
            headers.mapIndexed { index, header -> header to cells.getOrElse(index) { "" }.trim() }.toMap()
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val cells = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false

        line.forEachIndexed { index, ch ->
            when {
                ch == '"' && (index + 1 >= line.length || line[index + 1] != '"') -> inQuotes = !inQuotes
                ch == '"' && index + 1 < line.length && line[index + 1] == '"' -> current.append('"')
                ch == ',' && !inQuotes -> {
                    cells += current.toString()
                    current.setLength(0)
                }
                else -> current.append(ch)
            }
        }
        cells += current.toString()
        return cells
    }

    private fun parseTimestamp(row: Map<String, String>): Long? {
        val direct = row.firstValue("timestamp", "datetime", "time", "starttime", "start", "begintime")
        if (direct != null) return parseInstant(direct)

        val date = row.firstValue("date", "calendardate")
        val time = row.firstValue("localtime", "timeofday")
        return if (date != null && time != null) {
            runCatching {
                LocalDate.parse(date.trim()).atTime(LocalTime.parse(time.trim())).toEpochSecond(ZoneOffset.UTC)
            }.getOrNull()
        } else {
            date?.let { parseInstant(it) }
        }
    }

    private fun parseEndTimestamp(row: Map<String, String>, start: Long): Long? {
        row.firstValue("endtime", "end", "stoptime")?.let { endValue ->
            parseInstant(endValue)?.let { return it }
        }
        val durationSeconds = row.firstValue("duration", "elapsedtime", "movingtime", "durationseconds")
            ?.let { parseDurationSeconds(it) }
        return durationSeconds?.let { start + it }
    }

    private fun parseDate(row: Map<String, String>): String? {
        return row.firstValue("date", "calendardate")?.let { raw ->
            runCatching { LocalDate.parse(raw.trim()) }.getOrNull()?.toString()
                ?: parseInstant(raw)?.let { Instant.ofEpochSecond(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }
        }
    }

    private fun parseInstant(raw: String): Long? {
        val value = raw.trim()
        value.toLongOrNull()?.let { numeric ->
            return if (numeric > 99_999_999_999L) numeric / 1000L else numeric
        }

        val formatters = listOf(
            DateTimeFormatter.ISO_OFFSET_DATE_TIME,
            DateTimeFormatter.ISO_ZONED_DATE_TIME,
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss"),
            DateTimeFormatter.ISO_LOCAL_DATE,
        )

        formatters.forEach { formatter ->
            runCatching { ZonedDateTime.parse(value, formatter).toEpochSecond() }.getOrNull()?.let { return it }
            runCatching { LocalDateTime.parse(value, formatter).toEpochSecond(ZoneOffset.UTC) }.getOrNull()?.let { return it }
            runCatching { LocalDate.parse(value, formatter).atStartOfDay().toEpochSecond(ZoneOffset.UTC) }.getOrNull()?.let { return it }
        }
        return null
    }

    private fun parseDurationSeconds(raw: String): Long? {
        val value = raw.trim()
        value.toLongOrNull()?.let { return it }
        val parts = value.split(":")
        return when (parts.size) {
            2 -> parts[0].toLongOrNull()?.times(60)?.plus(parts[1].toLongOrNull() ?: return null)
            3 -> {
                val hours = parts[0].toLongOrNull() ?: return null
                val minutes = parts[1].toLongOrNull() ?: return null
                val seconds = parts[2].toLongOrNull() ?: return null
                hours * 3600 + minutes * 60 + seconds
            }
            else -> null
        }
    }

    private fun guessUnit(metricType: String): String? = when {
        metricType.contains("hr") || metricType.contains("heartrate") -> "bpm"
        metricType.contains("calor") -> "kcal"
        metricType.contains("distance") -> "m"
        metricType.contains("step") -> "count"
        metricType.contains("stress") -> "score"
        else -> null
    }

    private fun normalizeHeader(header: String): String =
        header.trim().lowercase().replace(Regex("[^a-z0-9]+"), "")

    private fun Set<String>.containsAny(vararg values: String): Boolean =
        values.any { contains(it) }

    private fun Map<String, String>.firstValue(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key -> this[key]?.takeIf { it.isNotBlank() } }

    companion object {
        private val DAILY_METRIC_IGNORED_HEADERS = setOf(
            "date",
            "calendardate",
            "timestamp",
            "datetime",
            "time",
            "source",
        )
    }
}

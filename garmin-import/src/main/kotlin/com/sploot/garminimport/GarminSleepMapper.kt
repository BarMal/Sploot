package com.sploot.garminimport

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.sploot.domain.model.SleepEpoch
import com.sploot.domain.model.SleepSession
import com.sploot.domain.model.SleepSource
import com.sploot.domain.model.SleepStage
import timber.log.Timber
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GarminSleepMapper @Inject constructor() {

    @JsonClass(generateAdapter = true)
    data class GarminSleepRoot(val dailySleepDTO: DailySleepDto?)

    @JsonClass(generateAdapter = true)
    data class DailySleepDto(
        val calendarDate: String?,
        val sleepLevels: List<SleepLevel>?,
    )

    @JsonClass(generateAdapter = true)
    data class SleepLevel(
        val activityLevel: String?,
        val startGMT: String?,
        val endGMT: String?,
    )

    data class ParsedGarminSleep(
        val session: SleepSession,
        val epochs: List<SleepEpoch>,
    )

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter(GarminSleepRoot::class.java)
    private val dtFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    fun parseSleep(json: String, date: String): ParsedGarminSleep? {
        val epochs = parseSleepEpochs(json, date)
        if (epochs.isEmpty()) return null

        val start = epochs.minOf { it.epochStartSeconds }
        val end = epochs.maxOf { it.epochStartSeconds } + 30L
        val deepMinutes = epochs.count { it.stage == SleepStage.DEEP } / 2
        val lightMinutes = epochs.count { it.stage == SleepStage.LIGHT } / 2
        val remMinutes = epochs.count { it.stage == SleepStage.REM } / 2
        val awakeMinutes = epochs.count { it.stage == SleepStage.AWAKE } / 2
        val totalSleepMinutes = deepMinutes + lightMinutes + remMinutes
        val timeInBedMinutes = ((end - start) / 60L).toInt().coerceAtLeast(1)
        val latencyMinutes = epochs.indexOfFirst { it.stage != SleepStage.AWAKE }
            .takeIf { it >= 0 }
            ?.let { it / 2 } ?: 0

        return ParsedGarminSleep(
            session = SleepSession(
                id = 0L,
                startEpochSeconds = start,
                endEpochSeconds = end,
                source = SleepSource.GARMIN,
                algorithmRevisionId = null,
                totalScore = null,
                deepMinutes = deepMinutes,
                lightMinutes = lightMinutes,
                remMinutes = remMinutes,
                awakeMinutes = awakeMinutes,
                latencyMinutes = latencyMinutes,
                efficiencyPercent = totalSleepMinutes.toFloat() / timeInBedMinutes * 100f,
            ),
            epochs = epochs,
        )
    }

    fun parseSleepEpochs(json: String, date: String, sessionId: Long = 0L): List<SleepEpoch> {
        return try {
            val root = adapter.fromJson(json) ?: return emptyList()
            val levels = root.dailySleepDTO?.sleepLevels ?: return emptyList()

            levels.flatMap { level ->
                val stage = mapStage(level.activityLevel) ?: return@flatMap emptyList()
                val startSecs = parseGmt(level.startGMT) ?: return@flatMap emptyList()
                val endSecs = parseGmt(level.endGMT) ?: return@flatMap emptyList()

                (startSecs until endSecs step 30).map { epochStart ->
                    SleepEpoch(
                        epochStartSeconds = epochStart,
                        sessionId = sessionId,
                        stage = stage,
                        source = SleepSource.GARMIN,
                        rmssd = null,
                        meanHr = null,
                        movementIntensity = null,
                        respRate = null,
                    )
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse Garmin sleep JSON for $date")
            emptyList()
        }
    }

    fun extractDate(filename: String): String? {
        val raw = filename.substringAfterLast("/")
            .replace("sleep_", "")
            .replace("hrv_status_", "")
            .substringBefore("T")
            .substringBefore(".")

        val normalized = if (raw.length == 8 && !raw.contains("-")) {
            "${raw.substring(0, 4)}-${raw.substring(4, 6)}-${raw.substring(6, 8)}"
        } else {
            raw
        }

        return runCatching { LocalDate.parse(normalized) }.getOrNull()?.toString()
    }

    private fun mapStage(level: String?): SleepStage? = when (level?.lowercase()) {
        "deep" -> SleepStage.DEEP
        "light" -> SleepStage.LIGHT
        "rem" -> SleepStage.REM
        "awake" -> SleepStage.AWAKE
        else -> null
    }

    private fun parseGmt(gmt: String?): Long? = runCatching {
        ZonedDateTime.parse("${gmt}Z", DateTimeFormatter.ISO_OFFSET_DATE_TIME).toEpochSecond()
    }.getOrElse {
        runCatching {
            LocalDateTime.parse(gmt, dtFormatter)
                .atZone(ZoneOffset.UTC)
                .toEpochSecond()
        }.getOrNull()
    }
}

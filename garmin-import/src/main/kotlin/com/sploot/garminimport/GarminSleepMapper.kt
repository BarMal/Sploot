package com.sploot.garminimport

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.sploot.domain.model.SleepEpoch
import com.sploot.domain.model.SleepSource
import com.sploot.domain.model.SleepStage
import timber.log.Timber
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Maps Garmin Connect sleep JSON export to domain [SleepEpoch] objects.
 *
 * Garmin sleep JSON schema (abbreviated):
 * {
 *   "dailySleepDTO": {
 *     "calendarDate": "2024-01-15",
 *     "sleepLevels": [
 *       { "activityLevel": "deep", "startGMT": "2024-01-15T22:30:00", "endGMT": "2024-01-15T23:00:00" },
 *       ...
 *     ]
 *   }
 * }
 *
 * Activity level mapping:
 *   "deep"   → DEEP
 *   "light"  → LIGHT
 *   "rem"    → REM
 *   "awake"  → AWAKE
 */
@Singleton
class GarminSleepMapper @Inject constructor() {

    // ── Moshi JSON models ─────────────────────────────────────────────────────

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

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter(GarminSleepRoot::class.java)
    private val dtFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    /**
     * Parse sleep epochs from a Garmin sleep JSON file.
     *
     * @param sessionId  The sleep session ID to associate epochs with (0 = unlinked)
     * @return List of [SleepEpoch] with 30-second resolution, or empty list on error.
     */
    fun parseSleepEpochs(json: String, date: String, sessionId: Long = 0L): List<SleepEpoch> {
        return try {
            val root   = adapter.fromJson(json) ?: return emptyList()
            val levels = root.dailySleepDTO?.sleepLevels ?: return emptyList()

            levels.flatMap { level ->
                val stage     = mapStage(level.activityLevel) ?: return@flatMap emptyList()
                val startSecs = parseGmt(level.startGMT)     ?: return@flatMap emptyList()
                val endSecs   = parseGmt(level.endGMT)       ?: return@flatMap emptyList()

                // Expand level into 30-second epochs
                (startSecs until endSecs step 30).map { epochStart ->
                    SleepEpoch(
                        epochStartSeconds  = epochStart,
                        sessionId          = sessionId,
                        stage              = stage,
                        source             = SleepSource.GARMIN,
                        rmssd              = null,
                        meanHr             = null,
                        movementIntensity  = null,
                        respRate           = null,
                    )
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse Garmin sleep JSON for $date")
            emptyList()
        }
    }

    /**
     * Extracts a "YYYY-MM-DD" date string from a Garmin export filename.
     * e.g. "sleep_20240115T120000.json" → "2024-01-15"
     */
    fun extractDate(filename: String): String? {
        val raw = filename.substringAfterLast("/")
            .replace("sleep_", "")
            .replace("hrv_status_", "")
            .substringBefore("T")
            .substringBefore(".")
        return runCatching { LocalDate.parse(raw.replace("", "-").run {
            // Handle YYYYMMDD without dashes
            if (length == 8 && !contains("-"))
                "${substring(0,4)}-${substring(4,6)}-${substring(6,8)}"
            else this
        }) }.getOrNull()?.toString()
    }

    private fun mapStage(level: String?): SleepStage? = when (level?.lowercase()) {
        "deep"  -> SleepStage.DEEP
        "light" -> SleepStage.LIGHT
        "rem"   -> SleepStage.REM
        "awake" -> SleepStage.AWAKE
        else    -> null
    }

    private fun parseGmt(gmt: String?): Long? = runCatching {
        ZonedDateTime.parse(gmt + "Z", DateTimeFormatter.ISO_OFFSET_DATE_TIME).toEpochSecond()
    }.getOrElse {
        runCatching {
            java.time.LocalDateTime.parse(gmt, dtFormatter)
                .atZone(java.time.ZoneOffset.UTC)
                .toEpochSecond()
        }.getOrNull()
    }
}

package com.sploot.garminimport

import android.content.ContentResolver
import android.net.Uri
import com.sploot.data.dao.GarminGroundTruthDao
import com.sploot.data.entity.GarminGroundTruthEntity
import timber.log.Timber
import java.util.zip.ZipInputStream
import javax.inject.Inject

/**
 * Streams a Garmin Connect health export ZIP (selected via SAF) and extracts
 * sleep and HRV JSON files into the local database.
 *
 * Garmin export archive layout (relevant files):
 *   DI_CONNECT/DI-Connect-Wellness/
 *     sleep_YYYYMMDDTHHMMSS.json    — sleep sessions with per-epoch stage labels
 *     hrv_status_YYYYMMDD.json      — weekly HRV status + last-night average
 *   DI_CONNECT/DI-Connect-Fitnes/
 *     ... (activity FIT files — not parsed here)
 *
 * Uses ZipInputStream to stream entries without loading the full archive
 * into memory (Garmin archives can be 200+ MB).
 */
class GarminZipParser @Inject constructor(
    private val contentResolver: ContentResolver,
    private val garminDao: GarminGroundTruthDao,
    private val sleepMapper: GarminSleepMapper,
) {

    data class ImportResult(
        val sleepDaysImported: Int,
        val hrvDaysImported: Int,
        val errors: List<String>,
    )

    suspend fun parseAndStore(zipUri: Uri): ImportResult {
        var sleepCount = 0
        var hrvCount   = 0
        val errors     = mutableListOf<String>()

        contentResolver.openInputStream(zipUri)?.use { inputStream ->
            ZipInputStream(inputStream).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name
                    try {
                        when {
                            name.contains("sleep_") && name.endsWith(".json") -> {
                                val json = zip.readBytes().decodeToString()
                                val date = sleepMapper.extractDate(name)
                                if (date != null) {
                                    val existing = garminDao.getByDate(date)
                                    garminDao.upsert((existing ?: GarminGroundTruthEntity(date = date))
                                        .copy(sleepJson = json))
                                    sleepMapper.parseSleepEpochs(json, date)  // Phase 3: persist epochs
                                    sleepCount++
                                }
                            }

                            name.contains("hrv_status") && name.endsWith(".json") -> {
                                val json = zip.readBytes().decodeToString()
                                val date = sleepMapper.extractDate(name)
                                if (date != null) {
                                    val existing = garminDao.getByDate(date)
                                    garminDao.upsert((existing ?: GarminGroundTruthEntity(date = date))
                                        .copy(hrvJson = json))
                                    hrvCount++
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error parsing $name")
                        errors += "$name: ${e.message}"
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }

        Timber.i("Garmin import complete: $sleepCount sleep days, $hrvCount HRV days, ${errors.size} errors")
        return ImportResult(sleepCount, hrvCount, errors)
    }

    // Extension to allow modifying a copy of an entity (since it's a data class)
    private fun GarminGroundTruthEntity.copy(
        sleepJson: String? = this.sleepJson,
        hrvJson:   String? = this.hrvJson,
    ) = GarminGroundTruthEntity(date, sleepJson, hrvJson, bodyBatteryJson, importedAtMillis)
}

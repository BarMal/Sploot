package com.sploot.garminimport

import android.content.ContentResolver
import android.net.Uri
import com.sploot.data.dao.GarminGroundTruthDao
import com.sploot.data.entity.GarminGroundTruthEntity
import com.sploot.data.repository.AlgorithmReviewRepository
import com.sploot.data.repository.SleepRepository
import timber.log.Timber
import java.util.zip.ZipInputStream
import javax.inject.Inject

class GarminZipParser @Inject constructor(
    private val contentResolver: ContentResolver,
    private val garminDao: GarminGroundTruthDao,
    private val sleepRepository: SleepRepository,
    private val reviewRepository: AlgorithmReviewRepository,
    private val sleepMapper: GarminSleepMapper,
) {

    data class ImportResult(
        val sleepDaysImported: Int,
        val hrvDaysImported: Int,
        val errors: List<String>,
    )

    suspend fun parseAndStore(zipUri: Uri): ImportResult {
        var sleepCount = 0
        var hrvCount = 0
        val errors = mutableListOf<String>()

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
                                    garminDao.upsert(
                                        (existing ?: GarminGroundTruthEntity(
                                            date = date,
                                            sleepJson = null,
                                            hrvJson = null,
                                            bodyBatteryJson = null,
                                        )).copy(sleepJson = json)
                                    )

                                    sleepMapper.parseSleep(json, date)?.let { parsed ->
                                        val garminSessionId = sleepRepository.replaceImportedGarminSession(
                                            session = parsed.session,
                                            epochs = parsed.epochs,
                                        )

                                        sleepRepository.getSessionsInRange(
                                            parsed.session.startEpochSeconds,
                                            parsed.session.endEpochSeconds,
                                        )
                                            .filter { it.source.name == "ALGO" }
                                            .forEach { algoSession ->
                                                reviewRepository.evaluateAgainstGarmin(algoSession.id)
                                            }

                                        Timber.d("Stored Garmin session $garminSessionId for $date")
                                    }
                                    sleepCount++
                                }
                            }

                            name.contains("hrv_status") && name.endsWith(".json") -> {
                                val json = zip.readBytes().decodeToString()
                                val date = sleepMapper.extractDate(name)
                                if (date != null) {
                                    val existing = garminDao.getByDate(date)
                                    garminDao.upsert(
                                        (existing ?: GarminGroundTruthEntity(
                                            date = date,
                                            sleepJson = null,
                                            hrvJson = null,
                                            bodyBatteryJson = null,
                                        )).copy(hrvJson = json)
                                    )
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
}

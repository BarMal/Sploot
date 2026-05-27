package com.sploot.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Raw Garmin health export for a single calendar day.
 *
 * JSON blobs are preserved verbatim so we can re-parse if the mapper changes.
 */
@Entity(tableName = "garmin_ground_truth")
data class GarminGroundTruthEntity(
    @PrimaryKey val date: String,   // "YYYY-MM-DD"
    val sleepJson:       String?,
    val hrvJson:         String?,
    val bodyBatteryJson: String?,
    val importedAtMillis: Long = System.currentTimeMillis(),
)

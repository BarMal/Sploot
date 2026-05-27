package com.sploot.garminimport

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.sploot.data.entity.ImportedArtifactEntity
import com.sploot.data.repository.CanonicalImportRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GarminFileImporter @Inject constructor(
    private val contentResolver: ContentResolver,
    private val canonicalImportRepository: CanonicalImportRepository,
    private val garminCsvParser: GarminCsvParser,
    private val garminZipParser: GarminZipParser,
) {

    data class ImportResult(
        val displayName: String,
        val fileType: String,
        val skippedAsDuplicate: Boolean,
        val inserted: Int,
        val updated: Int,
        val details: String,
    )

    suspend fun import(uri: Uri): ImportResult {
        val metadata = readMetadata(uri)
        val bytes = requireNotNull(contentResolver.openInputStream(uri)?.use { it.readBytes() }) {
            "Unable to read selected file"
        }
        val fingerprint = sha256(bytes)

        canonicalImportRepository.getImportedArtifact(fingerprint)?.let { existing ->
            return ImportResult(
                displayName = metadata.displayName,
                fileType = existing.extension,
                skippedAsDuplicate = true,
                inserted = 0,
                updated = 0,
                details = "Exact file already imported",
            )
        }

        val result = when (metadata.extension) {
            "zip" -> {
                val zipResult = garminZipParser.parseAndStore(uri)
                ImportResult(
                    displayName = metadata.displayName,
                    fileType = "zip",
                    skippedAsDuplicate = false,
                    inserted = zipResult.sleepDaysImported + zipResult.hrvDaysImported,
                    updated = 0,
                    details = "Imported ${zipResult.sleepDaysImported} sleep files and ${zipResult.hrvDaysImported} HRV files",
                )
            }
            "csv" -> {
                val csvResult = garminCsvParser.parseAndStore(
                    content = bytes.decodeToString(),
                    sourceFileFingerprint = fingerprint,
                    displayName = metadata.displayName,
                )
                ImportResult(
                    displayName = metadata.displayName,
                    fileType = csvResult.fileType,
                    skippedAsDuplicate = false,
                    inserted = csvResult.inserted,
                    updated = csvResult.updated,
                    details = csvResult.details,
                )
            }
            "fit" -> ImportResult(
                displayName = metadata.displayName,
                fileType = "fit",
                skippedAsDuplicate = false,
                inserted = 0,
                updated = 0,
                details = "FIT parsing is not implemented yet",
            )
            else -> ImportResult(
                displayName = metadata.displayName,
                fileType = metadata.extension.ifBlank { "unknown" },
                skippedAsDuplicate = false,
                inserted = 0,
                updated = 0,
                details = "Unsupported file type",
            )
        }

        canonicalImportRepository.upsertImportedArtifact(
            ImportedArtifactEntity(
                fingerprint = fingerprint,
                source = "GARMIN",
                displayName = metadata.displayName,
                mimeType = metadata.mimeType,
                extension = metadata.extension,
                status = when {
                    result.fileType == "fit" -> "PENDING"
                    result.inserted == 0 && result.updated == 0 -> "IGNORED"
                    else -> "IMPORTED"
                },
                notes = result.details,
            )
        )

        return result
    }

    private fun readMetadata(uri: Uri): FileMetadata {
        val mimeType = contentResolver.getType(uri)
        val displayName = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
            ?: uri.lastPathSegment
            ?: "garmin-upload"
        val extension = displayName.substringAfterLast('.', "").lowercase()
        return FileMetadata(displayName, extension, mimeType)
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private data class FileMetadata(
        val displayName: String,
        val extension: String,
        val mimeType: String?,
    )
}

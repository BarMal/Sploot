package com.sploot.app.ui.screen

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.health.connect.client.HealthConnectClient
import com.sploot.app.health.HealthConnectGarminImporter
import com.sploot.garminimport.GarminFileImporter
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GarminImportUiState(
    val isImporting: Boolean = false,
    val statusText: String = "No import yet.",
    val healthConnectAvailable: Boolean = false,
    val healthConnectNeedsUpdate: Boolean = false,
    val healthConnectPermissionsGranted: Boolean = false,
)

@HiltViewModel
class GarminImportViewModel @Inject constructor(
    private val garminFileImporter: GarminFileImporter,
    private val healthConnectGarminImporter: HealthConnectGarminImporter,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GarminImportUiState())
    val uiState: StateFlow<GarminImportUiState> = _uiState.asStateFlow()

    init {
        refreshHealthConnectState()
    }

    fun import(uri: Uri) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isImporting = true,
                    statusText = "Importing Garmin file...",
                )
            }
            runCatching { garminFileImporter.import(uri) }
                .onSuccess { result ->
                    _uiState.update {
                        it.copy(
                            isImporting = false,
                            statusText = if (result.skippedAsDuplicate) {
                                "Skipped duplicate ${result.displayName}: ${result.details}."
                            } else {
                                "${result.displayName}: ${result.details}. " +
                                    "Inserted ${result.inserted}, updated ${result.updated}."
                            },
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isImporting = false,
                            statusText = "Import failed: ${error.message}",
                        )
                    }
                }
        }
    }

    fun refreshHealthConnectState() {
        viewModelScope.launch {
            val availability = healthConnectGarminImporter.availabilityStatus()
            val permissionsGranted = if (availability == HealthConnectClient.SDK_AVAILABLE) {
                runCatching { healthConnectGarminImporter.hasAllPermissions() }.getOrDefault(false)
            } else {
                false
            }
            _uiState.update {
                it.copy(
                    healthConnectAvailable = availability == HealthConnectClient.SDK_AVAILABLE,
                    healthConnectNeedsUpdate = availability == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED,
                    healthConnectPermissionsGranted = permissionsGranted,
                )
            }
        }
    }

    fun syncHealthConnect() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isImporting = true,
                    statusText = "Syncing Garmin data from Health Connect...",
                )
            }
            runCatching { healthConnectGarminImporter.syncLast30Days() }
                .onSuccess { result ->
                    _uiState.update {
                        it.copy(
                            isImporting = false,
                            healthConnectPermissionsGranted = true,
                            statusText =
                                "Health Connect sync complete. " +
                                    "Sleep ${result.importedSleepSessions}, " +
                                    "activities ${result.importedActivities}, " +
                                    "laps ${result.importedLaps}, " +
                                    "HR samples ${result.importedHeartRateSamples}.",
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isImporting = false,
                            statusText = "Health Connect sync failed: ${error.message}",
                        )
                    }
                }
        }
    }

    fun requiredHealthConnectPermissions(): Set<String> =
        healthConnectGarminImporter.requiredPermissions
}

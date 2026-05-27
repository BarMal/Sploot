package com.sploot.app.ui.screen

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
)

@HiltViewModel
class GarminImportViewModel @Inject constructor(
    private val garminFileImporter: GarminFileImporter,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GarminImportUiState())
    val uiState: StateFlow<GarminImportUiState> = _uiState.asStateFlow()

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
}

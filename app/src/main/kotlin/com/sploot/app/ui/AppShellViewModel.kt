package com.sploot.app.ui

import androidx.lifecycle.ViewModel
import com.sploot.app.settings.AppSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class AppShellViewModel @Inject constructor(
    settingsRepository: AppSettingsRepository,
) : ViewModel() {
    val settings: StateFlow<com.sploot.app.settings.AppSettings> = settingsRepository.settings
}

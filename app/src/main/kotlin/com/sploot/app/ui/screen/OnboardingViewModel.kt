package com.sploot.app.ui.screen

import androidx.lifecycle.ViewModel
import com.sploot.app.settings.AppSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsRepository: AppSettingsRepository,
) : ViewModel() {

    fun completeOnboarding() {
        settingsRepository.update { it.copy(hasCompletedOnboarding = true) }
    }
}

package com.sploot.app.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sploot.data.repository.ActivityEvaluationRepository
import com.sploot.data.repository.ActivityRepository
import com.sploot.data.repository.AlgorithmRepository
import com.sploot.domain.model.ActivityEvaluation
import com.sploot.domain.model.ActivitySession
import com.sploot.domain.model.MetricFamily
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneOffset

data class ActivityReviewUiState(
    val isLoading: Boolean = true,
    val activeRevisionVersion: Int? = null,
    val garminActivities: List<ActivitySession> = emptyList(),
    val whoopDerivedActivities: List<ActivitySession> = emptyList(),
    val evaluations: List<ActivityEvaluation> = emptyList(),
)

@HiltViewModel
class ActivityReviewViewModel @Inject constructor(
    private val activityRepository: ActivityRepository,
    private val activityEvaluationRepository: ActivityEvaluationRepository,
    private val algorithmRepository: AlgorithmRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ActivityReviewUiState())
    val uiState: StateFlow<ActivityReviewUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val revision = algorithmRepository.getOrCreateActiveRevision(MetricFamily.ACTIVITY)
            val recentActivities = activityRepository.getLatestActivities(limit = 40)
            val evaluations = activityEvaluationRepository.getEvaluationsForRevision(revision.id)
                .sortedByDescending { it.createdAtMillis }
                .take(20)

            _uiState.update {
                it.copy(
                    isLoading = false,
                    activeRevisionVersion = revision.version,
                    garminActivities = recentActivities.filter { activity -> activity.source == GARMIN_SOURCE }.take(20),
                    whoopDerivedActivities = recentActivities.filter { activity -> activity.source == WHOOP_ALGO_SOURCE }.take(20),
                    evaluations = evaluations,
                )
            }
        }
    }

    fun formatWindow(activity: ActivitySession): String {
        val start = Instant.ofEpochSecond(activity.startEpochSeconds).atZone(ZoneOffset.UTC).toLocalDateTime()
        val end = Instant.ofEpochSecond(activity.endEpochSeconds).atZone(ZoneOffset.UTC).toLocalDateTime()
        return "${start.toLocalDate()} ${start.toLocalTime()} - ${end.toLocalTime()}"
    }

    companion object {
        private const val GARMIN_SOURCE = "GARMIN"
        private const val WHOOP_ALGO_SOURCE = "WHOOP_ALGO"
    }
}

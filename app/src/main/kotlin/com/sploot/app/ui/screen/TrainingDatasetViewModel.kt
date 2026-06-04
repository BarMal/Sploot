package com.sploot.app.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sploot.data.repository.TrainingExampleRepository
import com.sploot.domain.model.MetricFamily
import com.sploot.domain.model.TrainingExample
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

enum class TrainingDatasetFilter(
    val label: String,
    val family: MetricFamily?,
) {
    ALL("All", null),
    SLEEP("Sleep", MetricFamily.SLEEP),
    ACTIVITY("Activity", MetricFamily.ACTIVITY),
}

data class TrainingExampleSummary(
    val id: Long,
    val family: MetricFamily,
    val exampleDate: String,
    val algorithmRevisionId: Long?,
    val algorithmReference: String,
    val garminReference: String,
    val headline: String,
    val details: List<String>,
)

data class TrainingDatasetUiState(
    val isLoading: Boolean = true,
    val selectedFilter: TrainingDatasetFilter = TrainingDatasetFilter.ALL,
    val sleepExampleCount: Int = 0,
    val activityExampleCount: Int = 0,
    val recentExamples: List<TrainingExampleSummary> = emptyList(),
)

@HiltViewModel
class TrainingDatasetViewModel @Inject constructor(
    private val trainingExampleRepository: TrainingExampleRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrainingDatasetUiState())
    val uiState: StateFlow<TrainingDatasetUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun selectFilter(filter: TrainingDatasetFilter) {
        _uiState.update { it.copy(selectedFilter = filter) }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val selectedFilter = _uiState.value.selectedFilter
            val sleepCount = trainingExampleRepository.countExamples(MetricFamily.SLEEP)
            val activityCount = trainingExampleRepository.countExamples(MetricFamily.ACTIVITY)
            val examples = when (val family = selectedFilter.family) {
                null -> trainingExampleRepository.getRecentExamples(limit = 24)
                else -> trainingExampleRepository.getExamples(family).take(24)
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    sleepExampleCount = sleepCount,
                    activityExampleCount = activityCount,
                    recentExamples = examples.map(::toSummary),
                )
            }
        }
    }

    private fun toSummary(example: TrainingExample): TrainingExampleSummary {
        val details = mutableListOf<String>()
        when (example.family) {
            MetricFamily.SLEEP -> {
                val evaluation = example.evaluationJson?.let(::JSONObject)
                val labels = JSONObject(example.labelJson)
                val feature = JSONObject(example.featureJson)
                val accuracy = evaluation?.optDouble("epochAccuracy")?.takeIf { !it.isNaN() }
                val kappa = evaluation?.optDouble("cohensKappa")?.takeIf { !it.isNaN() }
                val scoreDelta = evaluation?.optDouble("scoreDelta")?.takeIf { !it.isNaN() }
                details += "Epochs: WHOOP ${feature.optInt("epochCount")} / Garmin ${labels.optInt("epochCount")}"
                accuracy?.let { details += "Epoch accuracy ${percent(it)}" }
                kappa?.let { details += "Cohen's kappa ${decimal(it)}" }
                scoreDelta?.let { details += "Sleep score delta ${signed(it)}" }
            }
            MetricFamily.ACTIVITY -> {
                val evaluation = example.evaluationJson?.let(::JSONObject)
                val labels = JSONObject(example.labelJson)
                val overlap = evaluation?.optInt("overlapSeconds")
                val durationDelta = evaluation?.optInt("durationDeltaSeconds")
                val avgHrDelta = evaluation?.optDouble("avgHrDelta")?.takeIf { !it.isNaN() }
                details += "Garmin laps ${labels.optJSONArray("laps")?.length() ?: 0} / track points ${labels.optInt("trackPointCount")}"
                overlap?.let { details += "Overlap ${it / 60} min" }
                durationDelta?.let { details += "Duration delta ${signed(it.toDouble())} sec" }
                avgHrDelta?.let { details += "Avg HR delta ${signed(it)} bpm" }
            }
            else -> {
                details += "Stored training example for ${example.family.name.lowercase()}."
            }
        }
        details += "Feature payload ${example.featureJson.length} chars / labels ${example.labelJson.length} chars"
        return TrainingExampleSummary(
            id = example.id,
            family = example.family,
            exampleDate = example.exampleDate,
            algorithmRevisionId = example.algorithmRevisionId,
            algorithmReference = example.algorithmReference,
            garminReference = example.garminReference,
            headline = "${example.family.name.lowercase().replaceFirstChar { it.uppercase() }} example on ${example.exampleDate}",
            details = details,
        )
    }

    private fun percent(value: Double): String = String.format("%.1f%%", value * 100.0)

    private fun decimal(value: Double): String = String.format("%.2f", value)

    private fun signed(value: Double): String = if (value > 0) "+${decimal(value)}" else decimal(value)
}

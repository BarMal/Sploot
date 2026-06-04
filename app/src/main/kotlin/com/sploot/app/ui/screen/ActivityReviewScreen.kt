package com.sploot.app.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.sploot.domain.model.ActivityEvaluation
import com.sploot.domain.model.ActivitySession

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityReviewScreen(
    nav: NavController,
    vm: ActivityReviewViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Activity Review") })
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Testing Snapshot",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = if (state.isLoading) {
                                "Loading activity calibration data..."
                            } else {
                                "Activity revision v${state.activeRevisionVersion ?: "?"} · " +
                                    "${state.garminActivities.size} Garmin activities · " +
                                    "${state.whoopDerivedActivities.size} WHOOP-derived activities · " +
                                    "${state.evaluations.size} comparisons"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(onClick = vm::refresh) {
                            Text("Refresh")
                        }
                    }
                }
            }

            item {
                Text("Recent WHOOP-derived activities", style = MaterialTheme.typography.titleMedium)
            }
            items(state.whoopDerivedActivities) { activity ->
                ActivityCard(
                    title = activity.title ?: activity.activityType ?: "WHOOP Activity",
                    subtitle = vm.formatWindow(activity),
                    details = listOf(
                        "Type: ${activity.activityType ?: "unknown"}",
                        "Avg HR: ${activity.avgHrBpm?.toInt()?.toString() ?: "-"}",
                        "Max HR: ${activity.maxHrBpm?.toInt()?.toString() ?: "-"}",
                        "Calories: ${activity.caloriesKcal?.toInt()?.toString() ?: "-"}",
                    ),
                )
            }

            item {
                Text("Recent Garmin activities", style = MaterialTheme.typography.titleMedium)
            }
            items(state.garminActivities) { activity ->
                ActivityCard(
                    title = activity.title ?: activity.activityType ?: "Garmin Activity",
                    subtitle = vm.formatWindow(activity),
                    details = listOf(
                        "Type: ${activity.activityType ?: "unknown"}",
                        "Avg HR: ${activity.avgHrBpm?.toInt()?.toString() ?: "-"}",
                        "Max HR: ${activity.maxHrBpm?.toInt()?.toString() ?: "-"}",
                        "Calories: ${activity.caloriesKcal?.toInt()?.toString() ?: "-"}",
                        "Distance m: ${activity.distanceMeters?.toInt()?.toString() ?: "-"}",
                    ),
                )
            }

            item {
                Text("Recent comparisons", style = MaterialTheme.typography.titleMedium)
            }
            items(state.evaluations) { evaluation ->
                EvaluationCard(evaluation)
            }
        }
    }
}

@Composable
private fun ActivityCard(
    title: String,
    subtitle: String,
    details: List<String>,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
            details.forEach { detail ->
                Text(detail, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun EvaluationCard(evaluation: ActivityEvaluation) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Garmin date ${evaluation.garminDate}", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Overlap: ${evaluation.overlapSeconds}s", style = MaterialTheme.typography.bodyMedium)
                Text("Duration Δ: ${evaluation.durationDeltaSeconds}s", style = MaterialTheme.typography.bodyMedium)
            }
            Text("Avg HR Δ: ${evaluation.avgHrDelta?.toInt()?.toString() ?: "-"}", style = MaterialTheme.typography.bodyMedium)
            Text("Max HR Δ: ${evaluation.maxHrDelta?.toInt()?.toString() ?: "-"}", style = MaterialTheme.typography.bodyMedium)
            Text("Calories Δ: ${evaluation.caloriesDelta?.toInt()?.toString() ?: "-"}", style = MaterialTheme.typography.bodyMedium)
            Text("Distance Δ: ${evaluation.distanceDeltaMeters?.toInt()?.toString() ?: "-"}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

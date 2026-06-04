package com.sploot.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.sploot.app.ui.Routes
import com.sploot.domain.model.ActivitySession
import com.sploot.domain.model.DailyMetricSummary
import com.sploot.domain.model.SleepSession
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private enum class GarminPage(
    val title: String,
    val subtitle: String,
) {
    OVERVIEW("Overview", "Latest Garmin snapshot and recent coverage"),
    SLEEP("Sleep", "Imported Garmin nights and stage totals"),
    ACTIVITY("Activity", "Workouts, HR, calories, and distances"),
    DAILY("Daily", "Daily summary metrics imported from Garmin"),
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GarminDashboardScreen(
    nav: NavController,
    vm: GarminDashboardViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val pages = GarminPage.entries
    val listState = rememberLazyListState()
    val currentPage by remember {
        derivedStateOf { listState.firstVisibleItemIndex.coerceIn(0, pages.lastIndex) }
    }
    val coroutineScope = rememberCoroutineScope()
    var horizontalDragAccumulation by remember { mutableFloatStateOf(0f) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Garmin View") },
                actions = {
                    OutlinedButton(onClick = { nav.navigate(Routes.GARMIN_IMPORT) }) {
                        Text("Import & Sync")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.background,
                        ),
                    )
                )
                .padding(padding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                GarminHeroCard(
                    state = state,
                    onManageData = { nav.navigate(Routes.GARMIN_IMPORT) },
                    formatTimestamp = vm::formatTimestamp,
                )
                GarminPageTabs(
                    pages = pages,
                    currentPage = currentPage,
                    onSelectPage = { pageIndex ->
                        coroutineScope.launch {
                            listState.animateScrollToItem(pageIndex)
                        }
                    },
                )
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(520.dp)
                        .pointerInput(currentPage, pages.size) {
                            detectHorizontalDragGestures(
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    horizontalDragAccumulation += dragAmount
                                },
                                onDragEnd = {
                                    val targetPage = when {
                                        horizontalDragAccumulation < -80f && currentPage < pages.lastIndex -> currentPage + 1
                                        horizontalDragAccumulation > 80f && currentPage > 0 -> currentPage - 1
                                        else -> currentPage
                                    }
                                    horizontalDragAccumulation = 0f
                                    coroutineScope.launch {
                                        listState.animateScrollToItem(targetPage)
                                    }
                                },
                                onDragCancel = {
                                    horizontalDragAccumulation = 0f
                                },
                            )
                        },
                ) {
                    val pageWidth = maxWidth
                    LazyRow(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        flingBehavior = rememberSnapFlingBehavior(lazyListState = listState),
                    ) {
                        items(pages.size) { pageIndex ->
                            Box(modifier = Modifier.width(pageWidth)) {
                                when (pages[pageIndex]) {
                                    GarminPage.OVERVIEW -> GarminOverviewPage(state = state, vm = vm)
                                    GarminPage.SLEEP -> GarminSleepPage(state = state, vm = vm)
                                    GarminPage.ACTIVITY -> GarminActivityPage(state = state, vm = vm)
                                    GarminPage.DAILY -> GarminDailyPage(state = state, vm = vm)
                                }
                            }
                        }
                    }
                }
                PagerDots(
                    currentIndex = currentPage,
                    count = pages.size,
                    labels = pages.map { it.title },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GarminPageTabs(
    pages: List<GarminPage>,
    currentPage: Int,
    onSelectPage: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Garmin Pages",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Swipe or tap",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.64f),
            )
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            pages.forEachIndexed { index, page ->
                FilterChip(
                    selected = index == currentPage,
                    onClick = { onSelectPage(index) },
                    label = { Text(page.title) },
                )
            }
        }
    }
}

@Composable
private fun GarminHeroCard(
    state: GarminDashboardUiState,
    onManageData: () -> Unit,
    formatTimestamp: (Long) -> String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Garmin Companion",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
            )
            Text(
                text = when {
                    state.isLoading -> "Loading your latest Garmin-backed metrics."
                    state.latestGarminTimestampSeconds != null ->
                        "Latest Garmin-backed data: ${formatTimestamp(state.latestGarminTimestampSeconds)}"
                    else -> "No Garmin-backed data has been imported yet."
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "This view reflects the latest Garmin data Sploot has imported or synced. It is a synced snapshot, not a second-by-second live watch feed.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SnapshotPill(
                    label = "${state.garminSleepCount} nights / 30D",
                    icon = Icons.Outlined.Bedtime,
                    accent = Color(0xFF57E6B1),
                )
                SnapshotPill(
                    label = "${state.garminActivityCount} activities / 30D",
                    icon = Icons.Outlined.Route,
                    accent = Color(0xFFFFA24C),
                )
                SnapshotPill(
                    label = "${state.garminHeartRateSampleCount} HR samples / 7D",
                    icon = Icons.Outlined.Favorite,
                    accent = Color(0xFFF86D74),
                )
            }
            OutlinedButton(onClick = onManageData) {
                Icon(Icons.Outlined.Sync, contentDescription = null)
                Text("Manage Garmin Data", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun GarminOverviewPage(
    state: GarminDashboardUiState,
    vm: GarminDashboardViewModel,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PageHeader(
                title = GarminPage.OVERVIEW.title,
                subtitle = GarminPage.OVERVIEW.subtitle,
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GarminMetricCard(
                    modifier = Modifier.weight(1f),
                    title = "Latest HR",
                    value = state.latestHeartRateSample?.let { "${it.hrBpm} bpm" } ?: "--",
                    subtitle = state.latestHeartRateSample?.let { vm.formatTimestamp(it.tsSeconds) } ?: "No recent Garmin HR sample",
                    icon = Icons.Outlined.Favorite,
                    accent = Color(0xFFF86D74),
                )
                GarminMetricCard(
                    modifier = Modifier.weight(1f),
                    title = "Latest Sleep",
                    value = state.latestSleep?.let { vm.formatDuration(it.startEpochSeconds, it.endEpochSeconds) } ?: "--",
                    subtitle = state.latestSleep?.let { vm.formatTimestamp(it.startEpochSeconds) } ?: "No Garmin sleep yet",
                    icon = Icons.Outlined.Bedtime,
                    accent = Color(0xFF57E6B1),
                )
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GarminMetricCard(
                    modifier = Modifier.weight(1f),
                    title = "Latest Activity",
                    value = state.latestActivity?.activityType?.replaceFirstChar { it.uppercase() } ?: "--",
                    subtitle = state.latestActivity?.let { vm.formatDuration(it.startEpochSeconds, it.endEpochSeconds) } ?: "No Garmin activity yet",
                    icon = Icons.Outlined.Route,
                    accent = Color(0xFFFFA24C),
                )
                GarminMetricCard(
                    modifier = Modifier.weight(1f),
                    title = "Daily Metrics",
                    value = state.recentDailyMetrics.size.toString(),
                    subtitle = "Unique metric types in the last 30 days",
                    icon = Icons.AutoMirrored.Outlined.ShowChart,
                    accent = Color(0xFF7AC7FF),
                )
            }
        }
        item {
            GarminSectionCard(
                title = "Latest Garmin Snapshot",
                body = buildList {
                    state.latestSleep?.let {
                        add("Sleep: ${vm.formatDuration(it.startEpochSeconds, it.endEpochSeconds)} with ${it.deepMinutes}m deep, ${it.remMinutes}m REM, and ${it.efficiencyPercent?.roundToInt() ?: 0}% efficiency.")
                    }
                    state.latestActivity?.let {
                        add("Activity: ${it.title ?: it.activityType ?: "Recent session"} with avg HR ${it.avgHrBpm?.roundToInt()?.toString() ?: "--"} and calories ${it.caloriesKcal?.roundToInt()?.toString() ?: "--"}.")
                    }
                    state.latestHeartRateSample?.let {
                        add("Heart rate: ${it.hrBpm} bpm at ${vm.formatTimestamp(it.tsSeconds)}.")
                    }
                    if (state.latestSleep == null && state.latestActivity == null && state.latestHeartRateSample == null) {
                        add("Import Garmin files or run a Health Connect sync to populate this view.")
                    }
                },
            )
        }
    }
}

@Composable
private fun GarminSleepPage(
    state: GarminDashboardUiState,
    vm: GarminDashboardViewModel,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PageHeader(
                title = GarminPage.SLEEP.title,
                subtitle = GarminPage.SLEEP.subtitle,
            )
        }
        item {
            state.latestSleep?.let { session ->
                GarminSectionCard(
                    title = "Latest Garmin Night",
                    body = listOf(
                        "Window: ${vm.formatTimestamp(session.startEpochSeconds)} to ${vm.formatTimestamp(session.endEpochSeconds)}",
                        "Duration: ${vm.formatDuration(session.startEpochSeconds, session.endEpochSeconds)}",
                        "Deep ${session.deepMinutes}m · Light ${session.lightMinutes}m · REM ${session.remMinutes}m · Awake ${session.awakeMinutes}m",
                        "Efficiency: ${session.efficiencyPercent?.roundToInt()?.toString() ?: "--"}%",
                    ),
                )
            } ?: GarminSectionCard(
                title = "Latest Garmin Night",
                body = listOf("No Garmin sleep sessions are stored yet."),
            )
        }
        items(state.recentSleeps) { session ->
            GarminListCard(
                title = vm.formatTimestamp(session.startEpochSeconds),
                subtitle = vm.formatDuration(session.startEpochSeconds, session.endEpochSeconds),
                lines = listOf(
                    "Deep ${session.deepMinutes}m · Light ${session.lightMinutes}m · REM ${session.remMinutes}m",
                    "Awake ${session.awakeMinutes}m · Efficiency ${session.efficiencyPercent?.roundToInt()?.toString() ?: "--"}%",
                ),
            )
        }
    }
}

@Composable
private fun GarminActivityPage(
    state: GarminDashboardUiState,
    vm: GarminDashboardViewModel,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PageHeader(
                title = GarminPage.ACTIVITY.title,
                subtitle = GarminPage.ACTIVITY.subtitle,
            )
        }
        item {
            state.latestActivity?.let { activity ->
                GarminSectionCard(
                    title = "Latest Garmin Activity",
                    body = listOf(
                        "${activity.title ?: activity.activityType ?: "Activity"} · ${vm.formatDuration(activity.startEpochSeconds, activity.endEpochSeconds)}",
                        "Avg HR ${activity.avgHrBpm?.roundToInt()?.toString() ?: "--"} · Max HR ${activity.maxHrBpm?.roundToInt()?.toString() ?: "--"}",
                        "Calories ${activity.caloriesKcal?.roundToInt()?.toString() ?: "--"} · Distance ${activity.distanceMeters?.roundToInt()?.toString() ?: "--"} m",
                    ),
                )
            } ?: GarminSectionCard(
                title = "Latest Garmin Activity",
                body = listOf("No Garmin activities are stored yet."),
            )
        }
        items(state.recentActivities) { activity ->
            GarminListCard(
                title = activity.title ?: activity.activityType?.replaceFirstChar { it.uppercase() } ?: "Garmin Activity",
                subtitle = "${vm.formatTimestamp(activity.startEpochSeconds)} · ${vm.formatDuration(activity.startEpochSeconds, activity.endEpochSeconds)}",
                lines = listOf(
                    "Avg HR ${activity.avgHrBpm?.roundToInt()?.toString() ?: "--"} · Max HR ${activity.maxHrBpm?.roundToInt()?.toString() ?: "--"}",
                    "Calories ${activity.caloriesKcal?.roundToInt()?.toString() ?: "--"} · Distance ${activity.distanceMeters?.roundToInt()?.toString() ?: "--"} m",
                ),
            )
        }
    }
}

@Composable
private fun GarminDailyPage(
    state: GarminDashboardUiState,
    vm: GarminDashboardViewModel,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PageHeader(
                title = GarminPage.DAILY.title,
                subtitle = GarminPage.DAILY.subtitle,
            )
        }
        if (state.recentDailyMetrics.isEmpty()) {
            item {
                GarminSectionCard(
                    title = "Daily Summaries",
                    body = listOf(
                        "No Garmin daily summary metrics are available yet.",
                        "Health Connect covers sleep, exercise, laps, and heart rate. CSV imports are the current path for richer Garmin daily summaries.",
                    ),
                )
            }
        } else {
            items(state.recentDailyMetrics) { metric ->
                GarminListCard(
                    title = vm.metricLabel(metric),
                    subtitle = metric.date,
                    lines = listOf(vm.metricDisplay(metric)),
                )
            }
        }
    }
}

@Composable
private fun PageHeader(
    title: String,
    subtitle: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleLarge)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
        )
    }
}

@Composable
private fun GarminMetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(accent.copy(alpha = 0.18f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                }
            }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.64f),
            )
        }
    }
}

@Composable
private fun GarminSectionCard(
    title: String,
    body: List<String>,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            body.forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                )
            }
        }
    }
}

@Composable
private fun GarminListCard(
    title: String,
    subtitle: String,
    lines: List<String>,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.64f),
            )
            lines.forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                )
            }
        }
    }
}

@Composable
private fun SnapshotPill(
    label: String,
    icon: ImageVector,
    accent: Color,
) {
    Row(
        modifier = Modifier
            .background(accent.copy(alpha = 0.14f), RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
        Text(text = label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun PagerDots(
    currentIndex: Int,
    count: Int,
    labels: List<String>,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val selected = index == currentIndex
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .width(if (selected) 22.dp else 8.dp)
                    .height(8.dp)
                    .background(
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(999.dp),
                    ),
            )
        }
        Text(
            text = labels.getOrElse(currentIndex) { "" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.64f),
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

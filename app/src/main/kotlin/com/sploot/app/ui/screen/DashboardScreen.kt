package com.sploot.app.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.BluetoothConnected
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.sploot.app.ui.Routes
import com.sploot.app.ui.theme.recoveryColor
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class, ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    nav: NavController,
    vm: DashboardViewModel = hiltViewModel(),
    garminVm: GarminDashboardViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val garminState by garminVm.uiState.collectAsStateWithLifecycle()
    val pullRefreshState = rememberPullRefreshState(
        refreshing = state.isWhoopSyncing,
        onRefresh = vm::refreshWhoopHistory,
    )
    val refreshHoldOffset by animateDpAsState(
        targetValue = if (state.isWhoopSyncing) 72.dp else 0.dp,
        animationSpec = spring(stiffness = 420f, dampingRatio = 0.88f),
        label = "dashboardRefreshHoldOffset",
    )

    LifecycleResumeEffect(Unit) {
        garminVm.refresh()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sploot") },
                actions = {
                    OutlinedButton(onClick = { nav.navigate(Routes.DEBUG) }) {
                        Text("Live Feed")
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
                .padding(padding)
                .pullRefresh(pullRefreshState),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(y = refreshHoldOffset)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item { Spacer(Modifier.height(8.dp)) }

                item { HeroPanel(state = state) }

                item {
                    DeviceDeckSection(
                        state = state,
                        garminState = garminState,
                        garminVm = garminVm,
                        onStartSpotSpo2 = vm::startSpotSpo2Reading,
                        onOpenGarmin = { nav.navigate(Routes.GARMIN_DASHBOARD) },
                        onManageGarmin = { nav.navigate(Routes.GARMIN_IMPORT) },
                    )
                }

                item {
                    RangeSelector(
                        selectedRange = state.selectedRange,
                        onRangeSelected = vm::selectRange,
                    )
                }

                item {
                    TodaySignalsSection(state = state)
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        MetricCard(
                            modifier = Modifier.weight(1f),
                            title = "Sleep Score",
                            value = state.averageSleepScore?.roundToInt()?.toString() ?: "--",
                            subtitle = "${state.selectedRange.label} average",
                            accent = Color(0xFF57E6B1),
                            icon = Icons.Outlined.Bedtime,
                        )
                        MetricCard(
                            modifier = Modifier.weight(1f),
                            title = "Avg Sleep",
                            value = state.averageSleepHours?.let { String.format("%.1fh", it) } ?: "--",
                            subtitle = "Per night",
                            accent = Color(0xFF7AC7FF),
                            icon = Icons.AutoMirrored.Outlined.TrendingUp,
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        MetricCard(
                            modifier = Modifier.weight(1f),
                            title = "RMSSD",
                            value = state.latestRmssd?.let { String.format("%.0f ms", it) } ?: "--",
                            subtitle = "Latest HRV window",
                            accent = Color(0xFFFFA24C),
                            icon = Icons.AutoMirrored.Outlined.ShowChart,
                        )
                        MetricCard(
                            modifier = Modifier.weight(1f),
                            title = "Activity",
                            value = state.totalActivityHours.let { String.format("%.1fh", it) },
                            subtitle = "${state.activityCount} sessions",
                            accent = Color(0xFFF86D74),
                            icon = Icons.Outlined.Bolt,
                        )
                    }
                }

                item {
                    QuickActionsRow(nav = nav)
                }

                item {
                    TrendCard(
                        title = "Heart Rate Trend",
                        subtitle = "Average WHOOP heart rate by day",
                        accent = Color(0xFFF86D74),
                        points = state.heartRateTrend,
                        yAxis = TrendYAxis(
                            min = 40f,
                            max = 200f,
                            ticks = listOf(200f, 160f, 120f, 80f, 40f),
                            suffix = "",
                        ),
                    )
                }

                item {
                    TrendCard(
                        title = "Sleep Score Trend",
                        subtitle = "Algorithm-derived sleep score by day",
                        accent = Color(0xFF57E6B1),
                        points = state.sleepScoreTrend,
                    )
                }

                item {
                    TrendCard(
                        title = "HRV Trend",
                        subtitle = "RMSSD across the selected range",
                        accent = Color(0xFF7AC7FF),
                        points = state.hrvTrend,
                    )
                }

                item {
                    TrendCard(
                        title = "Activity Minutes",
                        subtitle = "Total captured activity minutes per day",
                        accent = Color(0xFFFFA24C),
                        points = state.activityTrend,
                    )
                }

                item { Spacer(Modifier.height(28.dp)) }
            }

            PullRefreshIndicator(
                refreshing = state.isWhoopSyncing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}

private enum class GarminDashboardPage(
    val title: String,
) {
    OVERVIEW("Overview"),
    SLEEP("Sleep"),
    ACTIVITY("Activity"),
    DAILY("Daily"),
}

private enum class DashboardDevicePage(
    val title: String,
) {
    WHOOP("WHOOP"),
    GARMIN("Garmin"),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroPanel(
    state: DashboardUiState,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Your wearable lab",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.5.sp,
                    )
                    Text(
                        text = state.preferredWhoopDeviceName ?: "Choose a WHOOP device to unlock full sync",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = when {
                            state.isWhoopSyncing ->
                                "Syncing the strap now and keeping only samples newer than your saved WHOOP history."
                            state.latestWhoopDataTimestampSeconds == null ->
                                "No WHOOP history is stored yet. The next sync will backfill everything currently available on the strap."
                            else ->
                                "Latest WHOOP sample: ${formatTimestamp(state.latestWhoopDataTimestampSeconds)}"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    )
                    Spacer(Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        StatusPill(
                            label = when {
                                state.isWhoopSyncing -> "Syncing"
                                state.connectionState == com.sploot.whoopble.gatt.ConnectionState.READY ||
                                    state.connectionState == com.sploot.whoopble.gatt.ConnectionState.CONNECTED -> "Connected"
                                state.connectionState == com.sploot.whoopble.gatt.ConnectionState.CONNECTING -> "Connecting"
                                else -> "Disconnected"
                            },
                            accent = when {
                                state.isWhoopSyncing -> Color(0xFF7AC7FF)
                                state.connectionState == com.sploot.whoopble.gatt.ConnectionState.READY ||
                                    state.connectionState == com.sploot.whoopble.gatt.ConnectionState.CONNECTED -> Color(0xFF57E6B1)
                                state.connectionState == com.sploot.whoopble.gatt.ConnectionState.CONNECTING -> Color(0xFFFFA24C)
                                else -> Color(0xFFB0B7C3)
                            },
                            icon = Icons.Outlined.BluetoothConnected,
                        )
                        StatusPill(
                            label = state.liveBatteryPercent?.let { "${it.roundToInt()}% battery" } ?: "Battery --",
                            accent = Color(0xFFFFA24C),
                            icon = Icons.Outlined.Devices,
                        )
                    }
                }
                CircularGauge(
                    score = state.latestRecoveryScore ?: 0,
                    label = "Recovery",
                    modifier = Modifier.size(132.dp),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusPill(
                    label = state.sleepEfficiencyAverage?.let { "Avg efficiency ${it.roundToInt()}%" } ?: "No efficiency yet",
                    accent = Color(0xFF7AC7FF),
                    icon = Icons.Outlined.Bedtime,
                )
            }

            Text(
                text = when {
                    !state.hasPreferredWhoopDevice ->
                        "Pick a WHOOP device first, then pull down anywhere on this screen to catch up."
                    state.isWhoopSyncing ->
                        "Keep holding while Sploot catches up with the newest WHOOP history."
                    else ->
                        "Pull down anywhere on the dashboard to sync the latest WHOOP changes."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeviceDeckSection(
    state: DashboardUiState,
    garminState: GarminDashboardUiState,
    garminVm: GarminDashboardViewModel,
    onStartSpotSpo2: () -> Unit,
    onOpenGarmin: () -> Unit,
    onManageGarmin: () -> Unit,
) {
    val pages = DashboardDevicePage.entries
    val listState = rememberLazyListState()
    val currentPage by remember {
        derivedStateOf { listState.firstVisibleItemIndex.coerceIn(0, pages.lastIndex) }
    }
    val coroutineScope = rememberCoroutineScope()
    var horizontalDragAccumulation by remember { mutableFloatStateOf(0f) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Devices",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "Swipe between WHOOP and Garmin",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.64f),
            )
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            pages.forEachIndexed { index, page ->
                FilterChip(
                    selected = currentPage == index,
                    onClick = {
                        coroutineScope.launch {
                            listState.animateScrollToItem(index)
                        }
                    },
                    label = { Text(page.title) },
                )
            }
        }

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
                            DashboardDevicePage.WHOOP -> WhoopDevicePageCard(
                                state = state,
                                onStartSpotSpo2 = onStartSpotSpo2,
                            )
                            DashboardDevicePage.GARMIN -> GarminDevicePageCard(
                                state = garminState,
                                vm = garminVm,
                                onOpenGarmin = onOpenGarmin,
                                onManageGarmin = onManageGarmin,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WhoopDevicePageCard(
    state: DashboardUiState,
    onStartSpotSpo2: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "WHOOP",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "Live strap status and today’s processed WHOOP summary.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f),
            )
            FlowRow(
                maxItemsInEachRow = 2,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LiveStatTile(
                    title = "Heart Rate",
                    value = state.liveHrBpm?.let { "$it bpm" } ?: "--",
                    subtitle = "Live",
                    accent = Color(0xFFF86D74),
                    icon = Icons.Outlined.Favorite,
                )
                LiveStatTile(
                    title = "Battery",
                    value = state.liveBatteryPercent?.let { "${it.roundToInt()}%" } ?: "--",
                    subtitle = "Strap level",
                    accent = Color(0xFFFFA24C),
                    icon = Icons.Outlined.Devices,
                )
                LiveStatTile(
                    title = "Skin Temp",
                    value = state.liveTempCelsius?.let { String.format("%.1f°C", it) } ?: "--",
                    subtitle = "On-change event",
                    accent = Color(0xFF7AC7FF),
                    icon = Icons.Outlined.Thermostat,
                )
                LiveStatTile(
                    title = "SpO2",
                    value = state.spotSpo2Percent?.let { "${it.roundToInt()}%" } ?: "--",
                    subtitle = when {
                        state.isSpotSpo2Reading -> "${state.spotSpo2SampleCount} samples"
                        state.spotSpo2Percent == null -> "Tap for spot read"
                        else -> state.spotSpo2Status
                    },
                    accent = Color(0xFF57E6B1),
                    icon = Icons.Outlined.Favorite,
                    onClick = onStartSpotSpo2,
                    enabled = !state.isSpotSpo2Reading && !state.isWhoopSyncing,
                )
                LiveStatTile(
                    title = "Status",
                    value = when {
                        state.isSpotSpo2Reading -> "Reading SpO2"
                        state.isWhoopSyncing -> "Syncing"
                        state.connectionState == com.sploot.whoopble.gatt.ConnectionState.READY ||
                            state.connectionState == com.sploot.whoopble.gatt.ConnectionState.CONNECTED -> "Connected"
                        state.connectionState == com.sploot.whoopble.gatt.ConnectionState.CONNECTING -> "Connecting"
                        else -> "Disconnected"
                    },
                    subtitle = if (state.isSpotSpo2Reading) {
                        state.spotSpo2Status
                    } else {
                        state.latestWhoopDataTimestampSeconds?.let(::formatTimestamp) ?: "No stored sample yet"
                    },
                    accent = if (state.isSpotSpo2Reading) Color(0xFFFFA24C) else Color(0xFF57E6B1),
                    icon = Icons.Outlined.BluetoothConnected,
                )
            }
            if (state.isSpotSpo2Reading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFFFFA24C),
                    )
                    Text(
                        text = state.spotSpo2Status,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                    )
                }
            }
            Text(
                text = "Tap the SpO2 tile for an experimental red/infrared spot estimate. Use Garmin as the comparison point.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
            )
            Text(
                text = "Today",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            FlowRow(
                maxItemsInEachRow = 2,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LiveStatTile(
                    title = "Sleep",
                    value = state.todaySleep?.let {
                        String.format("%.1fh", ((it.endEpochSeconds - it.startEpochSeconds).coerceAtLeast(0L) / 3600f))
                    } ?: "--",
                    subtitle = state.todaySleep?.totalScore?.let { "Score $it" } ?: "No processed sleep yet",
                    accent = Color(0xFF57E6B1),
                    icon = Icons.Outlined.Bedtime,
                )
                LiveStatTile(
                    title = "HRV",
                    value = state.todayRmssd?.let { String.format("%.0f ms", it) } ?: "--",
                    subtitle = "Today RMSSD",
                    accent = Color(0xFF7AC7FF),
                    icon = Icons.AutoMirrored.Outlined.ShowChart,
                )
                LiveStatTile(
                    title = "Activity",
                    value = String.format("%.1fh", state.todayActivityHours),
                    subtitle = "${state.todayActivityCount} sessions today",
                    accent = Color(0xFFF86D74),
                    icon = Icons.Outlined.Bolt,
                )
                LiveStatTile(
                    title = "Calories",
                    value = if (state.todayCalories > 0f) state.todayCalories.roundToInt().toString() else "--",
                    subtitle = "Tracked activity kcal today",
                    accent = Color(0xFFFFA24C),
                    icon = Icons.Outlined.Favorite,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GarminDevicePageCard(
    state: GarminDashboardUiState,
    vm: GarminDashboardViewModel,
    onOpenGarmin: () -> Unit,
    onManageGarmin: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Garmin",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = when {
                            state.isLoading -> "Loading Garmin-backed metrics."
                            state.latestGarminTimestampSeconds != null ->
                                "Latest Garmin data: ${vm.formatTimestamp(state.latestGarminTimestampSeconds)}"
                            else -> "No Garmin-backed data imported yet."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f),
                    )
                }
                OutlinedButton(onClick = onOpenGarmin) {
                    Text("Open")
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatusPill(
                    label = "${state.garminSleepCount} nights / 30D",
                    accent = Color(0xFF57E6B1),
                    icon = Icons.Outlined.Bedtime,
                )
                StatusPill(
                    label = "${state.garminActivityCount} activities / 30D",
                    accent = Color(0xFFFFA24C),
                    icon = Icons.Outlined.Route,
                )
                StatusPill(
                    label = "${state.garminHeartRateSampleCount} HR samples / 7D",
                    accent = Color(0xFFF86D74),
                    icon = Icons.Outlined.Favorite,
                )
            }
            FlowRow(
                maxItemsInEachRow = 2,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LiveStatTile(
                    title = "Latest HR",
                    value = state.latestHeartRateSample?.let { "${it.hrBpm} bpm" } ?: "--",
                    subtitle = state.latestHeartRateSample?.let { vm.formatTimestamp(it.tsSeconds) } ?: "No recent sample",
                    accent = Color(0xFFF86D74),
                    icon = Icons.Outlined.Favorite,
                )
                LiveStatTile(
                    title = "Latest Sleep",
                    value = state.latestSleep?.let { vm.formatDuration(it.startEpochSeconds, it.endEpochSeconds) } ?: "--",
                    subtitle = state.latestSleep?.let { vm.formatTimestamp(it.startEpochSeconds) } ?: "No Garmin sleep yet",
                    accent = Color(0xFF57E6B1),
                    icon = Icons.Outlined.Bedtime,
                )
                LiveStatTile(
                    title = "Latest Activity",
                    value = state.latestActivity?.activityType?.replaceFirstChar { it.uppercase() } ?: "--",
                    subtitle = state.latestActivity?.let { vm.formatDuration(it.startEpochSeconds, it.endEpochSeconds) } ?: "No Garmin activity yet",
                    accent = Color(0xFFFFA24C),
                    icon = Icons.Outlined.Route,
                )
                LiveStatTile(
                    title = "Daily Metrics",
                    value = state.recentDailyMetrics.size.toString(),
                    subtitle = "Recent unique metric types",
                    accent = Color(0xFF7AC7FF),
                    icon = Icons.AutoMirrored.Outlined.ShowChart,
                )
            }
            OutlinedButton(onClick = onManageGarmin) {
                Icon(Icons.Outlined.Sync, contentDescription = null)
                Text("Manage Garmin Data", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LiveStrapStatsCard(
    state: DashboardUiState,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Live Strap Snapshot",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "Current values coming directly from the WHOOP connection.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f),
            )
            FlowRow(
                maxItemsInEachRow = 2,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LiveStatTile(
                    title = "Heart Rate",
                    value = state.liveHrBpm?.let { "$it bpm" } ?: "--",
                    subtitle = "Live",
                    accent = Color(0xFFF86D74),
                    icon = Icons.Outlined.Favorite,
                )
                LiveStatTile(
                    title = "Battery",
                    value = state.liveBatteryPercent?.let { "${it.roundToInt()}%" } ?: "--",
                    subtitle = "Strap level",
                    accent = Color(0xFFFFA24C),
                    icon = Icons.Outlined.Devices,
                )
                LiveStatTile(
                    title = "Skin Temp",
                    value = state.liveTempCelsius?.let { String.format("%.1f°C", it) } ?: "--",
                    subtitle = "On-change event",
                    accent = Color(0xFF7AC7FF),
                    icon = Icons.Outlined.Thermostat,
                )
                LiveStatTile(
                    title = "Status",
                    value = when {
                        state.isWhoopSyncing -> "Syncing"
                        state.connectionState == com.sploot.whoopble.gatt.ConnectionState.READY ||
                            state.connectionState == com.sploot.whoopble.gatt.ConnectionState.CONNECTED -> "Connected"
                        state.connectionState == com.sploot.whoopble.gatt.ConnectionState.CONNECTING -> "Connecting"
                        else -> "Disconnected"
                    },
                    subtitle = state.latestWhoopDataTimestampSeconds?.let(::formatTimestamp) ?: "No stored sample yet",
                    accent = Color(0xFF57E6B1),
                    icon = Icons.Outlined.BluetoothConnected,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TodayOverviewCard(
    state: DashboardUiState,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "WHOOP Today",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "A same-day snapshot from the WHOOP data Sploot has captured and processed so far.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f),
            )
            FlowRow(
                maxItemsInEachRow = 2,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LiveStatTile(
                    title = "Sleep",
                    value = state.todaySleep?.let {
                        String.format("%.1fh", ((it.endEpochSeconds - it.startEpochSeconds).coerceAtLeast(0L) / 3600f))
                    } ?: "--",
                    subtitle = state.todaySleep?.totalScore?.let { "Score $it" } ?: "No processed sleep yet",
                    accent = Color(0xFF57E6B1),
                    icon = Icons.Outlined.Bedtime,
                )
                LiveStatTile(
                    title = "HRV",
                    value = state.todayRmssd?.let { String.format("%.0f ms", it) } ?: "--",
                    subtitle = "Today RMSSD",
                    accent = Color(0xFF7AC7FF),
                    icon = Icons.AutoMirrored.Outlined.ShowChart,
                )
                LiveStatTile(
                    title = "Activity",
                    value = String.format("%.1fh", state.todayActivityHours),
                    subtitle = "${state.todayActivityCount} sessions today",
                    accent = Color(0xFFF86D74),
                    icon = Icons.Outlined.Bolt,
                )
                LiveStatTile(
                    title = "Calories",
                    value = if (state.todayCalories > 0f) state.todayCalories.roundToInt().toString() else "--",
                    subtitle = "Tracked activity kcal today",
                    accent = Color(0xFFFFA24C),
                    icon = Icons.Outlined.Favorite,
                )
            }
        }
    }
}

@Composable
private fun RangeSelector(
    selectedRange: DashboardRange,
    onRangeSelected: (DashboardRange) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        DashboardRange.entries.forEach { range ->
            FilterChip(
                selected = selectedRange == range,
                onClick = { onRangeSelected(range) },
                label = { Text(range.label) },
            )
        }
    }
}

@Composable
private fun TodaySignalsSection(
    state: DashboardUiState,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Today Signals",
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = "Intraday WHOOP signals captured and derived so far today. Respiratory rate appears where Sploot has a derived value, which is usually overnight first.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f),
        )
        TrendCard(
            title = "Heart Rate Today",
            subtitle = "5-minute average heart rate",
            accent = Color(0xFFF86D74),
            points = state.todayHeartRateTrend,
            yAxis = TrendYAxis(
                min = 40f,
                max = 200f,
                ticks = listOf(200f, 160f, 120f, 80f, 40f),
                suffix = "",
            ),
        )
        TrendCard(
            title = "Breaths Per Minute Today",
            subtitle = "Derived respiratory rate where available",
            accent = Color(0xFF7AC7FF),
            points = state.todayRespRateTrend,
            yAxis = TrendYAxis(
                min = 8f,
                max = 30f,
                ticks = listOf(30f, 24f, 18f, 12f, 8f),
                suffix = "",
            ),
        )
        TrendCard(
            title = "Skin Temperature Today",
            subtitle = "Average of recorded temperature events",
            accent = Color(0xFFFFA24C),
            points = state.todayTempTrend,
            yAxis = TrendYAxis(
                min = 25f,
                max = 40f,
                ticks = listOf(40f, 35f, 30f, 25f),
                suffix = "C",
            ),
        )
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    accent: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                )
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(accent.copy(alpha = 0.18f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accent,
                    )
                }
            }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
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
private fun LiveStatTile(
    title: String,
    value: String,
    subtitle: String,
    accent: Color,
    icon: ImageVector,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth(0.48f)
            .then(
                if (onClick != null) {
                    Modifier.clickable(enabled = enabled, onClick = onClick)
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)),
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
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                )
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(accent.copy(alpha = 0.18f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(18.dp),
                    )
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
private fun TrendCard(
    title: String,
    subtitle: String,
    accent: Color,
    points: List<DashboardTrendPoint>,
    yAxis: TrendYAxis? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f),
            )
            LineTrendChart(
                points = points,
                accent = accent,
                yAxis = yAxis,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
            )
        }
    }
}

@Composable
private fun QuickActionsRow(nav: NavController) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Quick Actions",
            style = MaterialTheme.typography.titleLarge,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ActionCard(
                modifier = Modifier.weight(1f),
                title = "Devices",
                body = "Pair and manage WHOOP straps",
                icon = Icons.Outlined.Devices,
                accent = Color(0xFF57E6B1),
                onClick = { nav.navigate(Routes.WHOOP_DEVICES) },
            )
            ActionCard(
                modifier = Modifier.weight(1f),
                title = "Garmin",
                body = "Open Garmin snapshot pages and manage imports",
                icon = Icons.AutoMirrored.Outlined.ShowChart,
                accent = Color(0xFFFFA24C),
                onClick = { nav.navigate(Routes.GARMIN_DASHBOARD) },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ActionCard(
                modifier = Modifier.weight(1f),
                title = "Review",
                body = "Compare WHOOP-derived activities with Garmin",
                icon = Icons.AutoMirrored.Outlined.TrendingUp,
                accent = Color(0xFFF86D74),
                onClick = { nav.navigate(Routes.ACTIVITY_REVIEW) },
            )
            ActionCard(
                modifier = Modifier.weight(1f),
                title = "Training",
                body = "Inspect reusable Garmin-labelled WHOOP examples",
                icon = Icons.AutoMirrored.Outlined.ShowChart,
                accent = Color(0xFF7AC7FF),
                onClick = { nav.navigate(Routes.TRAINING_DATASET) },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ActionCard(
                modifier = Modifier.fillMaxWidth(),
                title = "Settings",
                body = "Adjust capture, sync, and battery behavior",
                icon = Icons.Outlined.Settings,
                accent = Color(0xFF87A9FF),
                onClick = { nav.navigate(Routes.SETTINGS) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GarminDashboardSection(
    state: GarminDashboardUiState,
    vm: GarminDashboardViewModel,
    onManageData: () -> Unit,
) {
    val pages = GarminDashboardPage.entries
    val listState = rememberLazyListState()
    val currentPage by remember {
        derivedStateOf { listState.firstVisibleItemIndex.coerceIn(0, pages.lastIndex) }
    }
    val coroutineScope = rememberCoroutineScope()
    var horizontalDragAccumulation by remember { mutableFloatStateOf(0f) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Garmin Companion",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = when {
                            state.isLoading -> "Loading Garmin-backed metrics."
                            state.latestGarminTimestampSeconds != null ->
                                "Latest Garmin data: ${vm.formatTimestamp(state.latestGarminTimestampSeconds)}"
                            else -> "No Garmin-backed data imported yet."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f),
                    )
                    state.syncStatus?.let { syncStatus ->
                        Text(
                            text = syncStatus,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.56f),
                        )
                    }
                }
                OutlinedButton(onClick = onManageData) {
                    Icon(Icons.Outlined.Sync, contentDescription = null)
                    Text("Manage", modifier = Modifier.padding(start = 8.dp))
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatusPill(
                    label = "${state.garminSleepCount} nights / 30D",
                    accent = Color(0xFF57E6B1),
                    icon = Icons.Outlined.Bedtime,
                )
                StatusPill(
                    label = "${state.garminActivityCount} activities / 30D",
                    accent = Color(0xFFFFA24C),
                    icon = Icons.Outlined.Route,
                )
                StatusPill(
                    label = "${state.garminHeartRateSampleCount} HR samples / 7D",
                    accent = Color(0xFFF86D74),
                    icon = Icons.Outlined.Favorite,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Swipe or tap through Garmin pages",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.64f),
                )
                Text(
                    text = pages[currentPage].title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                pages.forEachIndexed { index, page ->
                    FilterChip(
                        selected = currentPage == index,
                        onClick = {
                            coroutineScope.launch {
                                listState.animateScrollToItem(index)
                            }
                        },
                        label = { Text(page.title) },
                    )
                }
            }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
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
                    GarminDashboardPage.OVERVIEW -> GarminDashboardPageCard(
                        title = "Overview",
                        lines = buildList {
                            add(state.latestHeartRateSample?.let { "Latest HR ${it.hrBpm} bpm at ${vm.formatTimestamp(it.tsSeconds)}" }
                                ?: "No recent Garmin heart-rate sample")
                            add(state.latestSleep?.let {
                                "Latest sleep ${vm.formatDuration(it.startEpochSeconds, it.endEpochSeconds)} from ${vm.formatTimestamp(it.startEpochSeconds)}"
                            } ?: "No Garmin sleep session imported yet")
                            add(state.latestActivity?.let {
                                "Latest activity ${it.title ?: it.activityType ?: "Recent session"} for ${vm.formatDuration(it.startEpochSeconds, it.endEpochSeconds)}"
                            } ?: "No Garmin activity imported yet")
                        },
                    )
                    GarminDashboardPage.SLEEP -> GarminDashboardPageCard(
                        title = "Sleep",
                        lines = if (state.latestSleep != null) {
                            val sleep = state.latestSleep
                            listOf(
                                "Window ${vm.formatTimestamp(sleep.startEpochSeconds)} to ${vm.formatTimestamp(sleep.endEpochSeconds)}",
                                "Deep ${sleep.deepMinutes}m · Light ${sleep.lightMinutes}m · REM ${sleep.remMinutes}m · Awake ${sleep.awakeMinutes}m",
                                "Efficiency ${sleep.efficiencyPercent?.roundToInt()?.toString() ?: "--"}%",
                            )
                        } else {
                            listOf("No Garmin sleep data is available yet.")
                        },
                    )
                    GarminDashboardPage.ACTIVITY -> GarminDashboardPageCard(
                        title = "Activity",
                        lines = if (state.latestActivity != null) {
                            val activity = state.latestActivity
                            listOf(
                                "${activity.title ?: activity.activityType ?: "Activity"} · ${vm.formatDuration(activity.startEpochSeconds, activity.endEpochSeconds)}",
                                "Avg HR ${activity.avgHrBpm?.roundToInt()?.toString() ?: "--"} · Max HR ${activity.maxHrBpm?.roundToInt()?.toString() ?: "--"}",
                                "Calories ${activity.caloriesKcal?.roundToInt()?.toString() ?: "--"} · Distance ${activity.distanceMeters?.roundToInt()?.toString() ?: "--"} m",
                            )
                        } else {
                            listOf("No Garmin activity data is available yet.")
                        },
                    )
                                GarminDashboardPage.DAILY -> GarminDashboardPageCard(
                                    title = "Daily",
                                    lines = if (state.recentDailyMetrics.isNotEmpty()) {
                                        state.recentDailyMetrics.take(4).map { metric ->
                                            "${vm.metricLabel(metric)} · ${vm.metricDisplay(metric)} · ${metric.date}"
                                        }
                                    } else {
                                        listOf("No Garmin daily summary metrics are available yet.")
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GarminDashboardPageCard(
    title: String,
    lines: List<String>,
) {
    Card(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        ),
    ) {
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .verticalScroll(scrollState)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
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
private fun ActionCard(
    title: String,
    body: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(accent.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = accent)
            }
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
            )
        }
    }
}

@Composable
private fun StatusPill(
    label: String,
    accent: Color,
    icon: ImageVector,
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
private fun CircularGauge(
    score: Int,
    label: String,
    modifier: Modifier = Modifier,
) {
    val clamped = score.coerceIn(0, 100)
    val accent = recoveryColor(clamped)
    val gaugeTrack = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 18.dp.toPx()
            drawArc(
                color = gaugeTrack,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                size = Size(size.width, size.height),
            )
            drawArc(
                color = accent,
                startAngle = 135f,
                sweepAngle = 270f * (clamped / 100f),
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                size = Size(size.width, size.height),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = clamped.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            )
        }
    }
}

@Composable
private fun LineTrendChart(
    points: List<DashboardTrendPoint>,
    accent: Color,
    yAxis: TrendYAxis? = null,
    modifier: Modifier = Modifier,
) {
    val validPoints = points.mapIndexedNotNull { index, point ->
        point.value?.let { index to it }
    }
    val axisConfig = remember(points, yAxis) {
        yAxis ?: validPoints
            .takeIf { it.isNotEmpty() }
            ?.let { values ->
                val min = values.minOf { it.second }
                val max = values.maxOf { it.second }
                val range = (max - min).takeIf { it > 0f } ?: 1f
                TrendYAxis(
                    min = min,
                    max = max + if (max == min) range else 0f,
                    ticks = listOf(max, min + range / 2f, min),
                    suffix = "",
                )
            }
    }
    val axisLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            if (axisConfig != null) {
                Column(
                    modifier = Modifier
                        .width(42.dp)
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    axisConfig.ticks.forEach { tick ->
                        Text(
                            text = axisConfig.format(tick),
                            style = MaterialTheme.typography.labelSmall,
                            color = axisLabelColor,
                            textAlign = TextAlign.End,
                            modifier = Modifier
                                .fillMaxWidth(),
                        )
                    }
                }
            }

            Canvas(
                modifier = Modifier
                    .fillMaxSize(),
            ) {
                val width = size.width
                val height = size.height
                val padding = 18.dp.toPx()
                val usableWidth = (width - padding * 2).coerceAtLeast(1f)
                val usableHeight = (height - padding * 2).coerceAtLeast(1f)
                val axisMin = axisConfig?.min ?: validPoints.minOfOrNull { it.second } ?: 0f
                val axisMax = axisConfig?.max ?: validPoints.maxOfOrNull { it.second } ?: 1f
                val range = (axisMax - axisMin).takeIf { it > 0f } ?: 1f

                val tickValues = axisConfig?.ticks ?: listOf(axisMax, axisMin + range / 2f, axisMin)
                tickValues.forEach { tick ->
                    val y = height - padding - ((tick - axisMin) / range).coerceIn(0f, 1f) * usableHeight
                    drawLine(
                        color = gridColor,
                        start = Offset(padding, y),
                        end = Offset(width - padding, y),
                        strokeWidth = 1.dp.toPx(),
                    )
                }

                if (validPoints.size >= 2) {
                    val path = Path()
                    validPoints.forEachIndexed { idx, (index, value) ->
                        val x = padding + usableWidth * (index / (points.lastIndex.coerceAtLeast(1)).toFloat())
                        val y = height - padding - ((value - axisMin) / range).coerceIn(0f, 1f) * usableHeight
                        if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }

                    drawPath(
                        path = path,
                        color = accent,
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
                    )

                    validPoints.forEach { (index, value) ->
                        val x = padding + usableWidth * (index / (points.lastIndex.coerceAtLeast(1)).toFloat())
                        val y = height - padding - ((value - axisMin) / range).coerceIn(0f, 1f) * usableHeight
                        drawCircle(color = accent, radius = 4.dp.toPx(), center = Offset(x, y))
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            visibleAxisLabels(points).forEach { point ->
                Text(
                    text = point.label.ifBlank { " " },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private data class TrendYAxis(
    val min: Float,
    val max: Float,
    val ticks: List<Float>,
    val suffix: String,
) {
    fun format(value: Float): String =
        if (suffix.isBlank()) {
            value.toInt().toString()
        } else {
            "${value.toInt()}$suffix"
        }
}

private fun visibleAxisLabels(points: List<DashboardTrendPoint>, maxLabels: Int = 6): List<DashboardTrendPoint> {
    if (points.isEmpty()) return emptyList()
    if (points.size <= maxLabels) return points
    val step = ((points.size - 1).toFloat() / (maxLabels - 1).coerceAtLeast(1)).toDouble()
    return (0 until maxLabels).map { index ->
        points[(index * step).toInt().coerceIn(0, points.lastIndex)]
    }
}

private fun formatTimestamp(timestampSeconds: Long): String =
    DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochSecond(timestampSeconds))

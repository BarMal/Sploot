package com.sploot.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.BluetoothSearching
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.sploot.app.ui.Routes

private data class OnboardingPage(
    val eyebrow: String,
    val title: String,
    val body: String,
    val icon: ImageVector,
    val accent: Color,
    val bullets: List<String>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    nav: NavController,
    vm: OnboardingViewModel = hiltViewModel(),
) {
    val pages = remember {
        listOf(
            OnboardingPage(
                eyebrow = "WELCOME",
                title = "Build your own wearable lab",
                body = "Sploot records raw WHOOP data, lines it up with Garmin ground truth, and turns the result into an experiment loop you can actually iterate on.",
                icon = Icons.Outlined.Insights,
                accent = Color(0xFF57E6B1),
                bullets = listOf(
                    "Capture WHOOP history and live sensor streams",
                    "Import Garmin exports or sync Garmin through Health Connect",
                    "Track the accuracy of each metric algorithm revision",
                ),
            ),
            OnboardingPage(
                eyebrow = "SETUP",
                title = "Connect data sources first",
                body = "Pick your WHOOP strap, decide how aggressively you want to capture raw streams, and choose which Garmin sources you want available for validation.",
                icon = Icons.AutoMirrored.Outlined.BluetoothSearching,
                accent = Color(0xFFFFA24C),
                bullets = listOf(
                    "Choose a preferred WHOOP device",
                    "Enable Garmin Health Connect or import FIT, CSV, and ZIP files",
                    "Use battery-aware capture settings when you need them",
                ),
            ),
            OnboardingPage(
                eyebrow = "DASHBOARD",
                title = "See the metrics, not just the plumbing",
                body = "The dashboard is built for trends: gauges for your latest state, charts over selectable ranges, and quick links into sync, activity review, and deeper debugging when needed.",
                icon = Icons.AutoMirrored.Outlined.ShowChart,
                accent = Color(0xFF7AC7FF),
                bullets = listOf(
                    "Review recovery, sleep, HRV, and activity trends",
                    "Pull down to sync the newest WHOOP history",
                    "Keep the debug feed around when you want protocol-level detail",
                ),
            ),
        )
    }
    var pageIndex by rememberSaveable { mutableIntStateOf(0) }
    val page = pages[pageIndex]
    val isFinalPage = pageIndex == pages.lastIndex

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Get Started") },
                actions = {
                    if (!isFinalPage) {
                        Text(
                            text = "${pageIndex + 1}/${pages.size}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.padding(end = 20.dp),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surface,
                        ),
                    )
                )
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(pages.size) { index ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .background(
                                    color = if (index <= pageIndex) page.accent else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(999.dp),
                                )
                        )
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(28.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .background(page.accent.copy(alpha = 0.18f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = page.icon,
                                contentDescription = null,
                                tint = page.accent,
                                modifier = Modifier.size(36.dp),
                            )
                        }

                        Text(
                            text = page.eyebrow,
                            style = MaterialTheme.typography.labelMedium,
                            color = page.accent,
                            letterSpacing = 2.sp,
                        )
                        Text(
                            text = page.title,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = page.body,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        )

                        page.bullets.forEach { bullet ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 7.dp)
                                        .size(8.dp)
                                        .background(page.accent, CircleShape),
                                )
                                Text(
                                    text = bullet,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                                )
                            }
                        }
                    }
                }

                if (isFinalPage) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                        shape = RoundedCornerShape(24.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(18.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Bolt,
                                contentDescription = null,
                                tint = page.accent,
                            )
                            Text(
                                text = "You can adjust capture intensity, battery-saver behavior, and sync cadence later from Settings.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                            )
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        if (isFinalPage) {
                            vm.completeOnboarding()
                            nav.navigate(Routes.DASHBOARD) {
                                popUpTo(Routes.ONBOARDING) { inclusive = true }
                            }
                        } else {
                            pageIndex += 1
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (isFinalPage) "Open Dashboard" else "Continue")
                }

                when (pageIndex) {
                    0 -> {
                        OutlinedButton(
                            onClick = { nav.navigate(Routes.WHOOP_DEVICES) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Choose WHOOP Device")
                        }
                    }
                    1 -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(
                                onClick = { nav.navigate(Routes.WHOOP_DEVICES) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("WHOOP Devices")
                            }
                            OutlinedButton(
                                onClick = { nav.navigate(Routes.GARMIN_IMPORT) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Garmin Sync")
                            }
                        }
                    }
                    else -> {
                        OutlinedButton(
                            onClick = { nav.navigate(Routes.SETTINGS) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Review Capture Settings")
                        }
                    }
                }

                if (pageIndex > 0) {
                    TextButton(
                        onClick = { pageIndex -= 1 },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text("Back")
                    }
                } else {
                    Spacer(Modifier.height(48.dp))
                }
            }
        }
    }
}

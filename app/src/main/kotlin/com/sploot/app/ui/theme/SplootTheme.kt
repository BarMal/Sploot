package com.sploot.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val RecoveryGreen  = Color(0xFF4CAF50)
private val RecoveryYellow = Color(0xFFFFC107)
private val RecoveryRed    = Color(0xFFF44336)

private val SplootDarkColors = darkColorScheme(
    primary         = Color(0xFF1DE9B6),   // Teal accent
    onPrimary       = Color(0xFF003731),
    secondary       = RecoveryGreen,
    background      = Color(0xFF121212),
    surface         = Color(0xFF1E1E1E),
    onBackground    = Color(0xFFE0E0E0),
    onSurface       = Color(0xFFE0E0E0),
)

@Composable
fun SplootTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SplootDarkColors,
        content     = content,
    )
}

/** Recovery colour: green ≥ 67, yellow 34–66, red < 34. */
fun recoveryColor(score: Int): Color = when {
    score >= 67 -> RecoveryGreen
    score >= 34 -> RecoveryYellow
    else        -> RecoveryRed
}

package com.sploot.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val RecoveryGreen = Color(0xFF57E6B1)
private val RecoveryYellow = Color(0xFFFFC768)
private val RecoveryRed = Color(0xFFF86D74)

private val SplootColors = darkColorScheme(
    primary = Color(0xFF57E6B1),
    onPrimary = Color(0xFF0A2B23),
    secondary = Color(0xFF7AC7FF),
    tertiary = Color(0xFFFFA24C),
    background = Color(0xFF07131A),
    surface = Color(0xFF10212B),
    surfaceVariant = Color(0xFF17313F),
    onBackground = Color(0xFFE7F4F7),
    onSurface = Color(0xFFE7F4F7),
)

private val SplootLightColors = lightColorScheme(
    primary = Color(0xFF007A5A),
    onPrimary = Color(0xFFF6FFFC),
    secondary = Color(0xFF006E9B),
    onSecondary = Color(0xFFF7FCFF),
    tertiary = Color(0xFFB35B00),
    onTertiary = Color(0xFFFFFAF5),
    background = Color(0xFFF3F8FA),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFDDE9EE),
    onBackground = Color(0xFF102028),
    onSurface = Color(0xFF102028),
)

private val SplootTypography = Typography(
    headlineSmall = TextStyle(
        fontSize = 28.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.3).sp,
    ),
    headlineMedium = TextStyle(
        fontSize = 34.sp,
        lineHeight = 38.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.6).sp,
    ),
    titleLarge = TextStyle(
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleMedium = TextStyle(
        fontSize = 17.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Medium,
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 21.sp,
        fontWeight = FontWeight.Normal,
    ),
    labelLarge = TextStyle(
        fontSize = 13.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.3.sp,
    ),
)

@Composable
fun SplootTheme(content: @Composable () -> Unit) {
    val colorScheme = if (isSystemInDarkTheme()) SplootColors else SplootLightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = SplootTypography,
        content = content,
    )
}

fun recoveryColor(score: Int): Color = when {
    score >= 67 -> RecoveryGreen
    score >= 34 -> RecoveryYellow
    else -> RecoveryRed
}

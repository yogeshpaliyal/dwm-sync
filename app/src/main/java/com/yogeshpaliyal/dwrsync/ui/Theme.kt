package com.yogeshpaliyal.dwrsync.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Devs Who Run brand green, plus a small supporting palette. */
private object Palette {
    val Forest = Color(0xFF234D3C)
    val ForestDeep = Color(0xFF0E2A1F)
    val Moss = Color(0xFF4F8A6B)
    val Lichen = Color(0xFFD6E6DC)
    val Paper = Color(0xFFF7F9F7)
    val Ink = Color(0xFF1A2420)
    val Slate = Color(0xFF55655D)
}

private val LightColors = lightColorScheme(
    primary = Palette.Forest,
    onPrimary = Color.White,
    primaryContainer = Palette.Lichen,
    onPrimaryContainer = Palette.ForestDeep,
    secondary = Palette.Moss,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2EDE6),
    onSecondaryContainer = Color(0xFF173528),
    tertiary = Color(0xFF6B5E2E),
    onTertiary = Color.White,
    background = Palette.Paper,
    onBackground = Palette.Ink,
    surface = Palette.Paper,
    onSurface = Palette.Ink,
    surfaceVariant = Color(0xFFE2E8E4),
    onSurfaceVariant = Palette.Slate,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF0F4F1),
    surfaceContainer = Color(0xFFEAEFEC),
    surfaceContainerHigh = Color(0xFFE4EAE6),
    surfaceContainerHighest = Color(0xFFDDE4E0),
    outline = Color(0xFF85958D),
    outlineVariant = Color(0xFFC9D3CD),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9FD3B6),
    onPrimary = Color(0xFF0B3523),
    primaryContainer = Palette.Forest,
    onPrimaryContainer = Palette.Lichen,
    secondary = Color(0xFF86BFA0),
    onSecondary = Color(0xFF0D2F20),
    secondaryContainer = Color(0xFF2A4136),
    onSecondaryContainer = Color(0xFFCFE6D8),
    tertiary = Color(0xFFD8C78E),
    onTertiary = Color(0xFF3A3005),
    background = Color(0xFF111915),
    onBackground = Color(0xFFDFE6E1),
    surface = Color(0xFF111915),
    onSurface = Color(0xFFDFE6E1),
    surfaceVariant = Color(0xFF3B4842),
    onSurfaceVariant = Color(0xFFB8C6BE),
    surfaceContainerLowest = Color(0xFF0C130F),
    surfaceContainerLow = Color(0xFF17211C),
    surfaceContainer = Color(0xFF1B2620),
    surfaceContainerHigh = Color(0xFF25302A),
    surfaceContainerHighest = Color(0xFF303B35),
    outline = Color(0xFF83938B),
    outlineVariant = Color(0xFF3B4842),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
)

private val base = Typography()

private val AppTypography = Typography(
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.4).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(lineHeight = 24.sp),
    bodyMedium = base.bodyMedium.copy(lineHeight = 21.sp),
    bodySmall = base.bodySmall,
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium,
    labelSmall = base.labelSmall,
)

/** Tabular figures so times, durations and distances line up. */
val Numbers = TextStyle(fontFeatureSettings = "tnum")

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun DwrTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}

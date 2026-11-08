package com.novatrade.demo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Colours sampled from the broker app's screens. */
object AppColors {
    val Background = Color(0xFF07080B)
    val Surface = Color(0xFF121A21)
    val SurfaceHigh = Color(0xFF222B33)
    val Divider = Color(0xFF2A3139)
    val TextPrimary = Color(0xFFE9EEF4)
    val TextSecondary = Color(0xFF92989F)
    val Accent = Color(0xFFFFDD00)
    val OnAccent = Color(0xFF141414)
    val Buy = Color(0xFF128AF9)
    val Sell = Color(0xFFEA453C)
    val Profit = Color(0xFF2FB57C)
    val Loss = Color(0xFFEA453C)
    val CandleUp = Color(0xFF167CE3)
    val CandleDown = Color(0xFFD44149)
    val Grid = Color(0xFF1A222B)
    val Toast = Color(0xFF212A33)
    val Toggle = Color(0xFF6A8094)

    /** Demo accounts use green chips, real accounts neutral ones. */
    val DemoChipText = Color(0xFF5BB585)
    val DemoChipBg = Color(0xFF183630)
    val RealChipText = Color(0xFFE9EEF4)
    val RealChipBg = Color(0xFF242C35)
    val SheetBg = Color(0xFF121A21)
    val SheetCard = Color(0xFF222B33)

    fun pnl(v: Double): Color = if (v >= 0) Profit else Loss
}

private val colors = darkColorScheme(
    primary = AppColors.Accent,
    onPrimary = AppColors.OnAccent,
    secondary = AppColors.Buy,
    background = AppColors.Background,
    onBackground = AppColors.TextPrimary,
    surface = AppColors.Surface,
    onSurface = AppColors.TextPrimary,
    surfaceVariant = AppColors.SurfaceHigh,
    onSurfaceVariant = AppColors.TextSecondary,
    surfaceContainer = AppColors.Surface,
    surfaceContainerLow = AppColors.Surface,
    surfaceContainerHigh = AppColors.SurfaceHigh,
    surfaceContainerHighest = AppColors.SurfaceHigh,
    outline = AppColors.Divider,
    outlineVariant = AppColors.Divider,
    error = AppColors.Sell,
)

private val typography = Typography(
    titleLarge = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun NovaTradeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}

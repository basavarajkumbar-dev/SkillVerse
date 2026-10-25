package com.skillverse.app.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skillverse.app.data.settings.ThemeMode

/** Raw palette. Screens use [MaterialTheme.colorScheme] and [SvTheme.colors], never these directly. */
object Palette {
    val Navy950 = Color(0xFF070B17)
    val Navy900 = Color(0xFF0B1020)
    val Navy850 = Color(0xFF10172C)
    val Navy800 = Color(0xFF151D36)
    val Navy700 = Color(0xFF1F2A4B)
    val Navy600 = Color(0xFF2B3866)
    val Charcoal = Color(0xFF1A1D27)
    val Violet = Color(0xFF7C5CFF)
    val VioletDeep = Color(0xFF5B3DF5)
    val VioletSoft = Color(0xFFB5A3FF)
    val Cyan = Color(0xFF3DD6E8)
    val CyanDeep = Color(0xFF0B8FA0)
    val Green = Color(0xFF3DDC97)
    val GreenDeep = Color(0xFF14895A)
    val Amber = Color(0xFFFFB547)
    val AmberDeep = Color(0xFFB86E00)
    val Rose = Color(0xFFFF6B81)
    val RoseDeep = Color(0xFFC62842)
    val Ink = Color(0xFFE9ECFF)
    val InkMuted = Color(0xFFA6AFD6)
    val InkFaint = Color(0xFF6F79A3)
    val Paper = Color(0xFFF4F5FB)
    val PaperCard = Color(0xFFFFFFFF)
    val SlateInk = Color(0xFF141834)
    val SlateMuted = Color(0xFF545C80)
}

/** Semantic colours that Material's scheme does not cover. */
@Immutable
data class SvColors(
    val success: Color,
    val onSuccessContainer: Color,
    val successContainer: Color,
    val warning: Color,
    val danger: Color,
    val dangerContainer: Color,
    val xp: Color,
    val streak: Color,
    val locked: Color,
    val cardBorder: Color,
    val cardElevated: Color,
    val codeBackground: Color,
    val codeText: Color,
    val heroBrush: Brush,
    val xpBrush: Brush,
    val isDark: Boolean,
)

private val DarkScheme: ColorScheme = darkColorScheme(
    primary = Palette.Violet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2C2163),
    onPrimaryContainer = Palette.VioletSoft,
    secondary = Palette.Cyan,
    onSecondary = Palette.Navy950,
    secondaryContainer = Color(0xFF0E3A44),
    onSecondaryContainer = Color(0xFFB6F3FA),
    tertiary = Palette.Green,
    onTertiary = Palette.Navy950,
    background = Palette.Navy900,
    onBackground = Palette.Ink,
    surface = Palette.Navy850,
    onSurface = Palette.Ink,
    surfaceVariant = Palette.Navy800,
    onSurfaceVariant = Palette.InkMuted,
    surfaceContainer = Palette.Navy850,
    surfaceContainerHigh = Palette.Navy800,
    surfaceContainerHighest = Palette.Navy700,
    surfaceContainerLow = Palette.Navy900,
    surfaceContainerLowest = Palette.Navy950,
    outline = Palette.Navy600,
    outlineVariant = Palette.Navy700,
    error = Palette.Rose,
    onError = Palette.Navy950,
)

private val LightScheme: ColorScheme = lightColorScheme(
    primary = Palette.VioletDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE7E1FF),
    onPrimaryContainer = Color(0xFF26137A),
    secondary = Palette.CyanDeep,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD4F5FA),
    onSecondaryContainer = Color(0xFF00363D),
    tertiary = Palette.GreenDeep,
    onTertiary = Color.White,
    background = Palette.Paper,
    onBackground = Palette.SlateInk,
    surface = Palette.PaperCard,
    onSurface = Palette.SlateInk,
    surfaceVariant = Color(0xFFECEEF7),
    onSurfaceVariant = Palette.SlateMuted,
    surfaceContainer = Palette.PaperCard,
    surfaceContainerHigh = Color(0xFFF0F1F8),
    surfaceContainerHighest = Color(0xFFE6E8F3),
    surfaceContainerLow = Palette.Paper,
    surfaceContainerLowest = Color.White,
    outline = Color(0xFFC9CDE2),
    outlineVariant = Color(0xFFDDE0EE),
    error = Palette.RoseDeep,
    onError = Color.White,
)

private val DarkSv = SvColors(
    success = Palette.Green,
    onSuccessContainer = Color(0xFFC7F7E2),
    successContainer = Color(0xFF0F3B2C),
    warning = Palette.Amber,
    danger = Palette.Rose,
    dangerContainer = Color(0xFF45161F),
    xp = Palette.Cyan,
    streak = Palette.Amber,
    locked = Palette.InkFaint,
    cardBorder = Color(0x1FFFFFFF),
    cardElevated = Palette.Navy800,
    codeBackground = Palette.Navy950,
    codeText = Color(0xFFD7DEFF),
    heroBrush = Brush.linearGradient(listOf(Color(0xFF2A1F6B), Color(0xFF14204A), Color(0xFF0D2B3A))),
    xpBrush = Brush.horizontalGradient(listOf(Palette.Violet, Palette.Cyan)),
    isDark = true,
)

private val LightSv = SvColors(
    success = Palette.GreenDeep,
    onSuccessContainer = Color(0xFF00391F),
    successContainer = Color(0xFFD6F5E7),
    warning = Palette.AmberDeep,
    danger = Palette.RoseDeep,
    dangerContainer = Color(0xFFFFE0E5),
    xp = Palette.CyanDeep,
    streak = Palette.AmberDeep,
    locked = Color(0xFF8B92B3),
    cardBorder = Color(0xFFE1E4F0),
    cardElevated = Color.White,
    codeBackground = Color(0xFF151A33),
    codeText = Color(0xFFE3E8FF),
    heroBrush = Brush.linearGradient(listOf(Color(0xFFEDE8FF), Color(0xFFE3F0FF), Color(0xFFDDF7F8))),
    xpBrush = Brush.horizontalGradient(listOf(Palette.VioletDeep, Palette.CyanDeep)),
    isDark = false,
)

val SvTypography = Typography(
    displaySmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.5).sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.4).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 16.sp, letterSpacing = 0.3.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.6.sp),
)

val CodeTextStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, lineHeight = 21.sp)

val SvShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Spacing scale used across all screens. */
object Spacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val screen = 20.dp
}

val LocalSvColors = staticCompositionLocalOf { DarkSv }

/** True when the user asked for reduced motion; components skip non-essential animation. */
val LocalReducedMotion = staticCompositionLocalOf { false }

object SvTheme {
    val colors: SvColors
        @Composable get() = LocalSvColors.current
    val reducedMotion: Boolean
        @Composable get() = LocalReducedMotion.current
}

@Composable
fun SkillVerseTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    reducedMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    CompositionLocalProvider(
        LocalSvColors provides if (dark) DarkSv else LightSv,
        LocalReducedMotion provides reducedMotion,
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = SvTypography,
            shapes = SvShapes,
            content = content,
        )
    }
}

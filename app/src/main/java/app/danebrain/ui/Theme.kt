@file:OptIn(ExperimentalTextApi::class)

package app.danebrain.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import app.danebrain.data.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.danebrain.R

/** A warm paper palette: cream and ink, a family of browns, and one teal accent. */
object Palette {
    val Cream = Color(0xFFFAF8F3)
    val WarmWhite = Color(0xFFFFFEF9)
    val Ink = Color(0xFF1A1A1A)
    val DarkBrown = Color(0xFF3D3027)
    val MidBrown = Color(0xFF6B5D52)
    val LightBrown = Color(0xFF7A6F60)
    val Sand = Color(0xFFA89984)
    /** Brand teal. White text on it is 4.9:1, so it is the fill for primary actions in both themes. */
    val Teal = Color(0xFF207D77)
    val TealHover = Color(0xFF1A6B65)
    val WarmTan = Color(0xFFD4A574)
    val Error = Color(0xFFC13A3A)
    val ErrorBg = Color(0xFFFCEDE7)
}

private val Light = lightColorScheme(
    primary = Palette.Teal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9ECE9),
    onPrimaryContainer = Color(0xFF0E4440),
    secondary = Palette.DarkBrown,
    onSecondary = Palette.Cream,
    secondaryContainer = Color(0xFFEFE8DA),
    onSecondaryContainer = Palette.DarkBrown,
    tertiary = Color(0xFF8A5A22),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF6E6D2),
    onTertiaryContainer = Color(0xFF5A3A12),
    background = Palette.Cream,
    onBackground = Palette.Ink,
    surface = Palette.Cream,
    onSurface = Palette.Ink,
    surfaceVariant = Color(0xFFEFE8DA),
    onSurfaceVariant = Palette.MidBrown,
    outline = Palette.LightBrown,
    outlineVariant = Color(0xFFE2D9C8),
    error = Palette.Error,
    onError = Color.White,
    errorContainer = Palette.ErrorBg,
    onErrorContainer = Color(0xFFA02E2E),
    surfaceContainerLowest = Palette.WarmWhite,
    surfaceContainerLow = Color(0xFFF6F2EA),
    surfaceContainer = Color(0xFFF2EDE3),
    surfaceContainerHigh = Color(0xFFEDE7DB),
    surfaceContainerHighest = Color(0xFFE8E1D3),
    surfaceBright = Palette.WarmWhite,
    surfaceDim = Color(0xFFE8E1D3),
    inverseSurface = Palette.Ink,
    inverseOnSurface = Palette.Cream,
    inversePrimary = Color(0xFF5BBDB5),
)

// The dark scheme reuses the ink, browns and sand, with the teal
// lightened where it is used as text so it stays readable on ink.
private val Dark = darkColorScheme(
    primary = Color(0xFF5BBDB5),
    onPrimary = Color(0xFF06302D),
    primaryContainer = Palette.TealHover,
    onPrimaryContainer = Color(0xFFE2F4F2),
    secondary = Palette.Sand,
    onSecondary = Palette.Ink,
    secondaryContainer = Palette.DarkBrown,
    onSecondaryContainer = Palette.Cream,
    tertiary = Palette.WarmTan,
    onTertiary = Color(0xFF3A2508),
    tertiaryContainer = Color(0xFF4A3520),
    onTertiaryContainer = Color(0xFFF3DDBF),
    background = Palette.Ink,
    onBackground = Palette.Cream,
    surface = Palette.Ink,
    onSurface = Palette.Cream,
    surfaceVariant = Color(0xFF2E2823),
    onSurfaceVariant = Palette.Sand,
    outline = Palette.LightBrown,
    outlineVariant = Palette.DarkBrown,
    error = Color(0xFFE8837A),
    onError = Color(0xFF4A0F0F),
    errorContainer = Color(0xFF5C1F1F),
    onErrorContainer = Palette.ErrorBg,
    surfaceContainerLowest = Color(0xFF141414),
    surfaceContainerLow = Color(0xFF1F1D1B),
    surfaceContainer = Color(0xFF242120),
    surfaceContainerHigh = Color(0xFF2B2724),
    surfaceContainerHighest = Color(0xFF342F2A),
    surfaceBright = Color(0xFF342F2A),
    surfaceDim = Palette.Ink,
    inverseSurface = Palette.Cream,
    inverseOnSurface = Palette.Ink,
    inversePrimary = Palette.Teal,
)

private fun fraunces(weight: Int) = Font(
    R.font.fraunces,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Serif for titles and for the user's own words. */
val Serif = FontFamily(fraunces(400), fraunces(500), fraunces(600))

private val AppTypography = Typography().let { t ->
    t.copy(
        displaySmall = t.displaySmall.copy(fontFamily = Serif),
        headlineLarge = t.headlineLarge.copy(fontFamily = Serif, lineHeight = 38.sp),
        headlineMedium = t.headlineMedium.copy(fontFamily = Serif),
        headlineSmall = t.headlineSmall.copy(fontFamily = Serif),
        titleLarge = t.titleLarge.copy(fontFamily = Serif, fontWeight = FontWeight.Medium),
        titleMedium = t.titleMedium.copy(fontFamily = Serif, fontWeight = FontWeight.Medium, fontSize = 18.sp),
    )
}

@Composable
fun DaneBrainTheme(mode: String, content: @Composable () -> Unit) {
    val dark = when (mode) {
        Settings.THEME_LIGHT -> false
        Settings.THEME_DARK -> true
        else -> isSystemInDarkTheme()
    }
    // Status and navigation bar icons follow the app's theme, which can differ from the phone's.
    val view = LocalView.current
    SideEffect {
        (view.context as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        typography = AppTypography,
        content = content,
    )
}

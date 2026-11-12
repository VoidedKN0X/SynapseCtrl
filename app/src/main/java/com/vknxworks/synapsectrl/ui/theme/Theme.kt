package com.vknxworks.synapsectrl.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.vknxworks.synapsectrl.R

/** One Ctrl / Signal palette: [CtrlColors.Dark] ("Signal") or [CtrlColors.Light] ("Signal Light"). */
@Immutable
data class CtrlPalette(
    val isDark: Boolean,
    val background: Color,
    /** Cards, sheets, dialogs. */
    val surface: Color,
    val raised: Color,
    /** Card borders. */
    val outline: Color,
    /** Borders of controls (avatar, buttons, text fields, the tab bar). */
    val controlOutline: Color,
    val divider: Color,
    val text: Color,
    val muted: Color,
    /** Disabled and stopped states. */
    val disabled: Color,
    /** The accent as text or an icon on this background (the fill is always [CtrlColors.Accent]). */
    val accentText: Color,
    val error: Color,
    val onError: Color,
    val warn: Color,
    /** Progress bar track and fill. */
    val track: Color,
    val trackFill: Color,
    val switchOffTrack: Color,
    val switchOffKnob: Color,
    val pinEmpty: Color,
    /** Bluish highlight for hosts-style rules and rewrites. */
    val info: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
)

/** The palette of the current theme (dark or light, following the device). */
val LocalCtrlColors = staticCompositionLocalOf { CtrlColors.Dark }

/**
 * Ctrl / Signal: the shared look of the Ctrl series, dark or light following the device. The accent is
 * defined once here: [Accent] is the fill (hero card, main buttons, the selected tab, switches, PIN cells)
 * with [OnAccent] on top, in both themes; [AccentText] is the accent as text or icon, darker in light mode.
 * Everything else reads the current palette from [LocalCtrlColors].
 */
object CtrlColors {
    /** The one accent colour. */
    val Accent = Color(0xFFB48CFF)
    /** Text and icons on top of the accent, in both themes. */
    val OnAccent = Color(0xFF08080A)
    /** The accent as text or icons on light backgrounds (#B48CFF is too pale on white). */
    private val AccentInk = Color(0xFF6A3BDB)

    val Dark = CtrlPalette(
        isDark = true,
        background = Color(0xFF08080A),
        surface = Color(0xFF141417),
        raised = Color(0xFF1C1C21),
        outline = Color(0xFF232328),
        controlOutline = Color(0xFF2A2A2F),
        divider = Color(0xFF1E1E23),
        text = Color(0xFFF4F4F2),
        muted = Color(0xFFA3A3AB),
        disabled = Color(0xFF8C8C94),
        accentText = Accent,
        error = Color(0xFFFF6B6B),
        onError = Color(0xFF08080A),
        warn = Color(0xFFF2C94C),
        track = Color(0xFF26262C),
        trackFill = Color(0xFFF4F4F2),
        switchOffTrack = Color(0xFF2A2A30),
        switchOffKnob = Color(0xFF8C8C94),
        pinEmpty = Color(0xFF3A3A41),
        info = Color(0xFF8FC8FF),
        errorContainer = Color(0xFF2A1416),
        onErrorContainer = Color(0xFFFFC9C9),
    )

    val Light = CtrlPalette(
        isDark = false,
        background = Color(0xFFF5F5F1),
        surface = Color(0xFFFFFFFF),
        raised = Color(0xFFECECE7),
        outline = Color(0xFFDEDED7),
        controlOutline = Color(0xFFD2D2CA),
        divider = Color(0xFFE6E6E0),
        text = Color(0xFF0B0B0D),
        muted = Color(0xFF5C5C64),
        disabled = Color(0xFF74747C),
        accentText = AccentInk,
        error = Color(0xFFD93636),
        onError = Color(0xFFFFFFFF),
        warn = Color(0xFF9A6700),
        track = Color(0xFFE2E2DC),
        trackFill = Color(0xFF0B0B0D),
        switchOffTrack = Color(0xFFDCDCD5),
        switchOffKnob = Color(0xFF74747C),
        pinEmpty = Color(0xFFC9C9C1),
        info = Color(0xFF1F63B0),
        errorContainer = Color(0xFFFCE4E4),
        onErrorContainer = Color(0xFF6B1010),
    )

    val current: CtrlPalette @Composable @ReadOnlyComposable get() = LocalCtrlColors.current

    val AccentText: Color @Composable @ReadOnlyComposable get() = current.accentText
    val Background: Color @Composable @ReadOnlyComposable get() = current.background
    val Surface: Color @Composable @ReadOnlyComposable get() = current.surface
    val Raised: Color @Composable @ReadOnlyComposable get() = current.raised
    val Outline: Color @Composable @ReadOnlyComposable get() = current.outline
    val ControlOutline: Color @Composable @ReadOnlyComposable get() = current.controlOutline
    val Divider: Color @Composable @ReadOnlyComposable get() = current.divider
    val Text: Color @Composable @ReadOnlyComposable get() = current.text
    val Muted: Color @Composable @ReadOnlyComposable get() = current.muted
    val Disabled: Color @Composable @ReadOnlyComposable get() = current.disabled
    val Error: Color @Composable @ReadOnlyComposable get() = current.error
    val Warn: Color @Composable @ReadOnlyComposable get() = current.warn
    val Track: Color @Composable @ReadOnlyComposable get() = current.track
    val TrackFill: Color @Composable @ReadOnlyComposable get() = current.trackFill
    val SwitchOff: Color @Composable @ReadOnlyComposable get() = current.switchOffTrack
    val SwitchOffKnob: Color @Composable @ReadOnlyComposable get() = current.switchOffKnob
    val PinEmpty: Color @Composable @ReadOnlyComposable get() = current.pinEmpty
}

/** Corner radii of the series. */
object CtrlShapes {
    val Card = RoundedCornerShape(22.dp)
    val Hero = RoundedCornerShape(26.dp)
    val Control = RoundedCornerShape(16.dp)
    val Avatar = RoundedCornerShape(14.dp)
    val Badge = RoundedCornerShape(10.dp)
    val Dot = RoundedCornerShape(2.dp)
}

private fun scheme(p: CtrlPalette): ColorScheme {
    // Material's primary is used for text-like accents (text buttons, radio buttons, spinners, the time
    // picker), so it is the accent text colour; the Ctrl components fill with CtrlColors.Accent themselves.
    val primary = p.accentText
    val onPrimary = if (p.isDark) CtrlColors.OnAccent else Color.White
    return (if (p.isDark) darkColorScheme() else lightColorScheme()).copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = CtrlColors.Accent,
        onPrimaryContainer = CtrlColors.OnAccent,
        // Snackbar actions sit on the inverse surface: the dark theme's inverse is light, so it needs the deep accent.
        inversePrimary = if (p.isDark) CtrlColors.Light.accentText else CtrlColors.Accent,
        secondary = p.text,
        onSecondary = p.background,
        secondaryContainer = p.raised,
        onSecondaryContainer = p.text,
        tertiary = p.info,
        onTertiary = p.background,
        tertiaryContainer = p.raised,
        onTertiaryContainer = p.text,
        background = p.background,
        onBackground = p.text,
        surface = p.background,
        onSurface = p.text,
        surfaceVariant = p.raised,
        onSurfaceVariant = p.muted,
        surfaceTint = Color.Transparent,
        surfaceBright = p.surface,
        surfaceDim = p.background,
        surfaceContainerLowest = p.background,
        surfaceContainerLow = p.surface,
        surfaceContainer = p.surface,
        surfaceContainerHigh = p.surface,
        surfaceContainerHighest = p.raised,
        inverseSurface = p.text,
        inverseOnSurface = p.background,
        outline = p.controlOutline,
        outlineVariant = p.divider,
        error = p.error,
        onError = p.onError,
        errorContainer = p.errorContainer,
        onErrorContainer = p.onErrorContainer,
        scrim = Color.Black,
    )
}

private val DarkScheme = scheme(CtrlColors.Dark)
private val LightScheme = scheme(CtrlColors.Light)

private fun grotesk(weight: Int) = Font(
    R.font.space_grotesk,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Space Grotesk (variable, 300–700): all UI text and headings. */
val SpaceGrotesk = FontFamily(grotesk(400), grotesk(500), grotesk(600), grotesk(700))

/** JetBrains Mono: numbers, data readouts, section labels and the wordmark. */
val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_semibold, FontWeight.SemiBold),
)

private fun style(size: Int, line: Int, weight: Int, tracking: Double = 0.0) = TextStyle(
    fontFamily = SpaceGrotesk,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = FontWeight(weight),
    letterSpacing = tracking.em,
)

private val AppTypography = Typography(
    displayLarge = style(56, 54, 700, -0.04),
    displayMedium = style(48, 48, 700, -0.04),
    displaySmall = style(36, 40, 700, -0.035),
    headlineLarge = style(40, 42, 700, -0.035),
    headlineMedium = style(28, 32, 700, -0.025),
    headlineSmall = style(24, 28, 700, -0.02),
    titleLarge = style(20, 26, 600, -0.01),
    titleMedium = style(18, 24, 600),
    titleSmall = style(15, 20, 600),
    bodyLarge = style(16, 22, 400),
    bodyMedium = style(14, 20, 400),
    bodySmall = style(12, 17, 400),
    labelLarge = style(14, 20, 600),
    labelMedium = style(12, 16, 500),
    labelSmall = style(11, 14, 500, 0.02),
)

/** The mono styles that Material's type scale has no slot for. */
object CtrlType {
    /** Section headers: JetBrains Mono 11sp, upper case, wide tracking, muted. */
    val Label = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.16.em)
    /** The "AD/CTRL" wordmark. */
    val Wordmark = TextStyle(fontFamily = JetBrainsMono, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.14.em)
    /** Small data readouts: times, IDs, counts in lists. */
    val Data = TextStyle(fontFamily = JetBrainsMono, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal)
    /** Trailing status words in lists. */
    val Status = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.em)
    /** Big numbers in the hero card. */
    val Hero = TextStyle(fontFamily = SpaceGrotesk, fontSize = 64.sp, lineHeight = 60.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.05).em)
    /** Numbers in the metric cards. */
    val Metric = TextStyle(fontFamily = SpaceGrotesk, fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.03).em)
    /** PIN pad digits. */
    val Key = TextStyle(fontFamily = JetBrainsMono, fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.Medium)
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(9.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

/** Horizontal page margin used everywhere. */
val Gutter = 20.dp

/**
 * Ctrl / Signal follows the device: dark when the phone is in dark mode, light otherwise. There is no
 * theme choice in the app and no wallpaper (dynamic) colours.
 */
@Composable
fun SynapseCtrlTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val palette = if (dark) CtrlColors.Dark else CtrlColors.Light
    CompositionLocalProvider(LocalCtrlColors provides palette) {
        MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, typography = AppTypography, shapes = AppShapes, content = content)
    }
}

/** Health colours for status icons, dots and the attention list, for the current theme. */
object Health {
    val ok: Color @Composable @ReadOnlyComposable get() = CtrlColors.AccentText
    val warn: Color @Composable @ReadOnlyComposable get() = CtrlColors.Warn
    val critical: Color @Composable @ReadOnlyComposable get() = CtrlColors.Error
    val idle: Color @Composable @ReadOnlyComposable get() = CtrlColors.Disabled
}

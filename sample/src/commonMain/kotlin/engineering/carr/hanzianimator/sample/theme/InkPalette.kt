package engineering.carr.hanzianimator.sample.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The sample's ink-and-paper design tokens: warm paper neutrals, near-black
 * ink, and one seal-red accent. Adapted from the dictionary app's palette,
 * trimmed to what the sample uses. The web reference is light-only; the dark
 * variant is derived.
 */
@Immutable
data class InkPalette(
    val bg: Color,
    val bgSoft: Color,
    val panel: Color,
    val fg: Color,
    val muted: Color,
    val border: Color,
    val accent: Color,
    val accentSoft: Color,
    val onAccent: Color,
    val indigoGray: Color,
    val success: Color
)

internal val LightInkPalette = InkPalette(
    bg = Color(0xFFFAF7F1),
    bgSoft = Color(0xFFF2EEE5),
    panel = Color(0xFFFFFDF8),
    fg = Color(0xFF221F1A),
    muted = Color(0xFF6F695E),
    border = Color(0xFFDDD6C8),
    accent = Color(0xFFA53A2B),
    accentSoft = Color(0xFFF3E4DF),
    onAccent = Color.White,
    indigoGray = Color(0xFF5A6478),
    success = Color(0xFF33803F)
)

internal val DarkInkPalette = InkPalette(
    bg = Color(0xFF17140F),
    bgSoft = Color(0xFF242019),
    panel = Color(0xFF1E1A14),
    fg = Color(0xFFEAE4D8),
    muted = Color(0xFFA69E8F),
    border = Color(0xFF3C362B),
    accent = Color(0xFFD46A58),
    accentSoft = Color(0xFF43241D),
    onAccent = Color(0xFF1B0E0B),
    indigoGray = Color(0xFF97A0B4),
    success = Color(0xFF63AC70)
)

internal val LocalInkPalette = staticCompositionLocalOf { LightInkPalette }

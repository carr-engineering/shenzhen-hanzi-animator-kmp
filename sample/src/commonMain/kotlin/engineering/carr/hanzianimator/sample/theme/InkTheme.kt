package engineering.carr.hanzianimator.sample.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/** Light / Dark / follow-system theme selection for the sample. */
enum class ThemeMode { Light, Dark, System }

private fun schemeFrom(palette: InkPalette, dark: Boolean) = if (dark) {
    darkColorScheme(
        primary = palette.accent,
        onPrimary = palette.onAccent,
        primaryContainer = palette.accentSoft,
        onPrimaryContainer = palette.accent,
        secondary = palette.indigoGray,
        tertiary = palette.success,
        background = palette.bg,
        onBackground = palette.fg,
        surface = palette.panel,
        onSurface = palette.fg,
        surfaceVariant = palette.bgSoft,
        onSurfaceVariant = palette.muted,
        outline = palette.border,
        outlineVariant = palette.border,
        error = Color_Error_Dark
    )
} else {
    lightColorScheme(
        primary = palette.accent,
        onPrimary = palette.onAccent,
        primaryContainer = palette.accentSoft,
        onPrimaryContainer = palette.accent,
        secondary = palette.indigoGray,
        tertiary = palette.success,
        background = palette.bg,
        onBackground = palette.fg,
        surface = palette.panel,
        onSurface = palette.fg,
        surfaceVariant = palette.bgSoft,
        onSurfaceVariant = palette.muted,
        outline = palette.border,
        outlineVariant = palette.border,
        error = Color_Error_Light
    )
}

private val Color_Error_Light = androidx.compose.ui.graphics.Color(0xFFB3261E)
private val Color_Error_Dark = androidx.compose.ui.graphics.Color(0xFFE59289)

private val inkShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp)
)

private fun typographyWith(fontFamily: FontFamily): Typography {
    val base = Typography()
    fun TextStyle.withFamily() = copy(fontFamily = fontFamily)
    return Typography(
        displayLarge = base.displayLarge.withFamily(),
        displayMedium = base.displayMedium.withFamily(),
        displaySmall = base.displaySmall.withFamily(),
        headlineLarge = base.headlineLarge.withFamily(),
        headlineMedium = base.headlineMedium.withFamily(),
        headlineSmall = base.headlineSmall.withFamily(),
        titleLarge = base.titleLarge.withFamily(),
        titleMedium = base.titleMedium.withFamily(),
        titleSmall = base.titleSmall.withFamily(),
        bodyLarge = base.bodyLarge.withFamily(),
        bodyMedium = base.bodyMedium.withFamily(),
        bodySmall = base.bodySmall.withFamily(),
        labelLarge = base.labelLarge.withFamily(),
        labelMedium = base.labelMedium.withFamily(),
        labelSmall = base.labelSmall.withFamily()
    )
}

@Composable
fun InkTheme(
    themeMode: ThemeMode,
    fontFamily: FontFamily,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
        ThemeMode.System -> isSystemInDarkTheme()
    }
    val palette = if (dark) DarkInkPalette else LightInkPalette
    CompositionLocalProvider(LocalInkPalette provides palette) {
        MaterialTheme(
            colorScheme = schemeFrom(palette, dark),
            shapes = inkShapes,
            typography = typographyWith(fontFamily),
            content = content
        )
    }
}

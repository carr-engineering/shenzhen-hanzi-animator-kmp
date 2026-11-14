package engineering.carr.hanzianimator.compose

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Immutable visual styling for stroke-order rendering: colours, the background
 * grid, stroke widths (in glyph units), and transient-animation durations.
 *
 * Obtain a Material-independent instance from [StrokeOrderDefaults.style], overriding
 * only what you need. Widths are expressed in glyph units (the
 * [engineering.carr.hanzianimator.core.StrokeOrder.GLYPH_SIZE] square) so they
 * scale with the canvas.
 */
@Immutable
public data class StrokeOrderStyle(
    val outlineColor: Color,
    val completedStrokeColor: Color,
    val activeStrokeColor: Color,
    val radicalStrokeColor: Color,
    val hintColor: Color,
    val traceColor: Color,
    val errorColor: Color,
    val gridColor: Color,
    val showGrid: Boolean = true,
    val revealStrokeWidth: Float = DEFAULT_REVEAL_STROKE_WIDTH,
    val traceStrokeWidth: Float = DEFAULT_TRACE_STROKE_WIDTH,
    val medianStartExtension: Float = DEFAULT_MEDIAN_START_EXTENSION,
    val hintRevealDurationMillis: Int = DEFAULT_HINT_REVEAL_DURATION_MILLIS,
    val failureFlashDurationMillis: Int = DEFAULT_FAILURE_FLASH_DURATION_MILLIS,
    val completionPulseDurationMillis: Int = DEFAULT_COMPLETION_PULSE_DURATION_MILLIS,
    /**
     * Client-supplied colors in component-tree order. Six distinct colors cover
     * the bundled dataset. Unmapped strokes (and indexes beyond this list) use
     * [completedStrokeColor] / [activeStrokeColor]. Empty restores radical coloring.
     */
    val componentColors: List<Color> = StrokeOrderDefaults.LightComponentColors
) {
    init {
        require(revealStrokeWidth.isFinite() && revealStrokeWidth > 0f) { "revealStrokeWidth must be positive and finite" }
        require(traceStrokeWidth.isFinite() && traceStrokeWidth > 0f) { "traceStrokeWidth must be positive and finite" }
        require(medianStartExtension.isFinite() && medianStartExtension >= 0f) { "medianStartExtension must be non-negative and finite" }
        require(hintRevealDurationMillis >= 0 && failureFlashDurationMillis >= 0 && completionPulseDurationMillis >= 0) { "Animation durations must be non-negative" }
    }
    public companion object {
        /** Width of the wide brush that reveals a stroke through its outline. */
        public const val DEFAULT_REVEAL_STROKE_WIDTH: Float = 200f

        /** Width of the user's traced line during a quiz. */
        public const val DEFAULT_TRACE_STROKE_WIDTH: Float = 34f

        /** How far the reveal brush is extended before a stroke's first point. */
        public const val DEFAULT_MEDIAN_START_EXTENSION: Float = 100f

        public const val DEFAULT_HINT_REVEAL_DURATION_MILLIS: Int = 520
        public const val DEFAULT_FAILURE_FLASH_DURATION_MILLIS: Int = 220
        public const val DEFAULT_COMPLETION_PULSE_DURATION_MILLIS: Int = 420
    }
}

/** Material-free color defaults. Hosts can override every color or use the optional Material adapter. */
public object StrokeOrderDefaults {
    /** Defined Forest & bronze: pine ink, forest, bronze, olive, sage, sand; in component-tree order. */
    public val LightComponentColors: List<Color> = listOf(
        Color(0xFF283D32), Color(0xFF3F6D50), Color(0xFF9E7338),
        Color(0xFF625C3D), Color(0xFF718861), Color(0xFFAE8B57)
    )

    /** Defined Forest & bronze adapted for dark surfaces, in the same component order. */
    public val DarkComponentColors: List<Color> = listOf(
        Color(0xFFD0DBCD), Color(0xFF9FC39F), Color(0xFFD3AE6C),
        Color(0xFFACA77E), Color(0xFF7FAA84), Color(0xFFC2A078)
    )

    @Composable
    public fun style(
        dark: Boolean = isSystemInDarkTheme(),
        outlineColor: Color = (if (dark) Color(0xFFA69E8F) else Color(0xFF6F695E)).copy(alpha = 0.16f),
        completedStrokeColor: Color = (if (dark) Color(0xFFEAE4D8) else Color(0xFF221F1A)).copy(alpha = 0.92f),
        activeStrokeColor: Color = (if (dark) Color(0xFFEAE4D8) else Color(0xFF221F1A)).copy(alpha = 0.92f),
        radicalStrokeColor: Color = if (dark) Color(0xFFD46A58) else Color(0xFFA53A2B),
        hintColor: Color = (if (dark) Color(0xFFD46A58) else Color(0xFFA53A2B)).copy(alpha = 0.58f),
        traceColor: Color = if (dark) Color(0xFF63AC70) else Color(0xFF33803F),
        errorColor: Color = (if (dark) Color(0xFFE59289) else Color(0xFFB3261E)).copy(alpha = 0.74f),
        gridColor: Color = (if (dark) Color(0xFF3C362B) else Color(0xFFDDD6C8)).copy(alpha = 0.18f),
        showGrid: Boolean = true,
        componentColors: List<Color> = if (dark)
            DarkComponentColors else LightComponentColors
    ): StrokeOrderStyle = StrokeOrderStyle(
        outlineColor = outlineColor,
        completedStrokeColor = completedStrokeColor,
        activeStrokeColor = activeStrokeColor,
        radicalStrokeColor = radicalStrokeColor,
        hintColor = hintColor,
        traceColor = traceColor,
        errorColor = errorColor,
        gridColor = gridColor,
        showGrid = showGrid,
        componentColors = componentColors
    )
}

/** Component coloring takes precedence; unmapped strokes stay neutral. Legacy data uses radical coloring. */
internal fun strokeFinalColor(
    isRadical: Boolean,
    normal: Color,
    radical: Color,
    componentIndex: Int? = null,
    componentColors: List<Color> = emptyList(),
    hasComponentMapping: Boolean = false
): Color = when {
    hasComponentMapping && componentColors.isNotEmpty() ->
        componentIndex?.let { componentColors.getOrNull(it) } ?: normal
    isRadical -> radical
    else -> normal
}

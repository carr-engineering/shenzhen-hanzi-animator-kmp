package engineering.carr.hanzianimator.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Immutable visual styling for stroke-order rendering: colours, the background
 * grid, stroke widths (in glyph units), and transient-animation durations.
 *
 * Obtain a Material-aware instance from [StrokeOrderDefaults.style], overriding
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
    val completionPulseDurationMillis: Int = DEFAULT_COMPLETION_PULSE_DURATION_MILLIS
) {
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

/** Factory for a [StrokeOrderStyle] resolved from the current [MaterialTheme]. */
public object StrokeOrderDefaults {
    @Composable
    public fun style(
        outlineColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.16f),
        completedStrokeColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f),
        activeStrokeColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f),
        radicalStrokeColor: Color = MaterialTheme.colorScheme.primary,
        hintColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.58f),
        traceColor: Color = MaterialTheme.colorScheme.tertiary,
        errorColor: Color = MaterialTheme.colorScheme.error.copy(alpha = 0.74f),
        gridColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
        showGrid: Boolean = true
    ): StrokeOrderStyle = StrokeOrderStyle(
        outlineColor = outlineColor,
        completedStrokeColor = completedStrokeColor,
        activeStrokeColor = activeStrokeColor,
        radicalStrokeColor = radicalStrokeColor,
        hintColor = hintColor,
        traceColor = traceColor,
        errorColor = errorColor,
        gridColor = gridColor,
        showGrid = showGrid
    )
}

/** The final fill colour for a stroke once drawn: [radical] when it is part of the radical, else [normal]. */
internal fun strokeFinalColor(isRadical: Boolean, normal: Color, radical: Color): Color =
    if (isRadical) radical else normal

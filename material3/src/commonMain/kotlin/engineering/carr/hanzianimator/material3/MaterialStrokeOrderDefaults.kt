package engineering.carr.hanzianimator.material3
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import engineering.carr.hanzianimator.compose.StrokeOrderStyle
import engineering.carr.hanzianimator.compose.StrokeOrderDefaults
public object MaterialStrokeOrderDefaults {
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
        showGrid: Boolean = true,
        componentColors: List<Color> = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f)
            StrokeOrderDefaults.DarkComponentColors else StrokeOrderDefaults.LightComponentColors
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

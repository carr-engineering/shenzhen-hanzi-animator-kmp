package engineering.carr.hanzianimator.compose

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Fill
import engineering.carr.hanzianimator.core.StrokeOrder
import engineering.carr.hanzianimator.core.StrokeViewportTransform

/**
 * Statelessly renders a character's strokes, drawing the first [visibleStrokes]
 * strokes filled. Useful for static thumbnails, partial glyphs, or a still frame
 * of a character; for animation or quizzing use [StrokeOrderCanvas].
 */
@Composable
public fun HanziGlyph(
    strokeOrder: StrokeOrder,
    modifier: Modifier = Modifier,
    visibleStrokes: Int = Int.MAX_VALUE,
    style: StrokeOrderStyle = StrokeOrderDefaults.style()
) {
    val geometry = remember(strokeOrder, style.medianStartExtension) {
        StrokeGlyphGeometry.from(strokeOrder, style.medianStartExtension)
    }
    Canvas(modifier) {
        val viewport = StrokeViewportTransform(size.width, size.height)
        if (style.showGrid) drawGlyphGrid(viewport, style.gridColor)
        withGlyphTransform(viewport) {
            geometry.strokes.forEach { entry ->
                if (entry.index < visibleStrokes) {
                    entry.outlinePath?.let { path ->
                        drawPath(
                            path = path,
                            color = strokeFinalColor(
                                isRadical = entry.isRadical,
                                normal = style.completedStrokeColor,
                                radical = style.radicalStrokeColor
                            ),
                            style = Fill
                        )
                    }
                }
            }
        }
    }
}

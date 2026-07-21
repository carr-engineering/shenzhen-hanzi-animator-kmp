package engineering.carr.hanzianimator.compose

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp
import engineering.carr.hanzianimator.core.GlyphPoint
import engineering.carr.hanzianimator.core.StrokeOrder
import engineering.carr.hanzianimator.core.StrokeViewportTransform

/** Builds the glyph-space -> canvas matrix (uniform scale + centring translate). */
internal fun glyphCanvasMatrix(viewport: StrokeViewportTransform): Matrix =
    Matrix().apply {
        this[0, 0] = viewport.scale
        this[1, 1] = viewport.scale
        this[3, 0] = viewport.left
        this[3, 1] = viewport.top
    }

/** Runs [block] with the canvas concatenated by the glyph transform, then restores it. */
internal inline fun DrawScope.withGlyphTransform(viewport: StrokeViewportTransform, block: DrawScope.() -> Unit) {
    val matrix = glyphCanvasMatrix(viewport)
    drawContext.canvas.save()
    drawContext.canvas.concat(matrix)
    try {
        block()
    } finally {
        drawContext.canvas.restore()
    }
}

/** Draws the practice grid (border, mid-lines, diagonals) in canvas space. */
internal fun DrawScope.drawGlyphGrid(viewport: StrokeViewportTransform, color: Color) {
    if (viewport.scale <= 0f) return
    val side = StrokeOrder.GLYPH_SIZE * viewport.scale
    val left = viewport.left
    val top = viewport.top
    val right = left + side
    val bottom = top + side
    val centerX = (left + right) / 2f
    val centerY = (top + bottom) / 2f
    val width = 1.dp.toPx()
    drawRect(
        color = color.copy(alpha = 0.55f),
        topLeft = Offset(left, top),
        size = Size(side, side),
        style = Stroke(width = width)
    )
    drawLine(color, Offset(left, centerY), Offset(right, centerY), width)
    drawLine(color, Offset(centerX, top), Offset(centerX, bottom), width)
    drawLine(color.copy(alpha = color.alpha * 0.72f), Offset(left, top), Offset(right, bottom), width)
    drawLine(color.copy(alpha = color.alpha * 0.72f), Offset(right, top), Offset(left, bottom), width)
}

/**
 * Draws stroke [entry] revealed to [progress] in glyph space: a wide brush along
 * the median, clipped to the outline, filling completely once [progress] ~= 1.
 * [scratch] is a reusable [Path] for the partial-median segment.
 */
internal fun DrawScope.drawStrokeReveal(
    entry: StrokeGeometryEntry,
    progress: Float,
    color: Color,
    revealStrokeWidth: Float,
    scratch: Path
) {
    val outline = entry.outlinePath ?: return
    if (progress >= 0.999f) {
        drawPath(outline, color, style = Fill)
        return
    }
    val measure = entry.revealMeasure ?: return
    val stop = entry.revealLength * progress.coerceIn(0f, 1f)
    if (stop <= 0f) return
    scratch.reset()
    measure.getSegment(0f, stop, scratch, startWithMoveTo = true)
    clipPath(outline) {
        drawPath(scratch, color, style = Stroke(width = revealStrokeWidth, cap = StrokeCap.Round))
    }
}

/** Draws a polyline of glyph-space [points] (in glyph space, so under the glyph transform). */
internal fun DrawScope.drawGlyphPolyline(points: List<GlyphPoint>, color: Color, strokeWidth: Float) {
    if (points.size < 2) return
    for (index in 1 until points.size) {
        drawLine(
            color = color,
            start = Offset(points[index - 1].x, points[index - 1].y),
            end = Offset(points[index].x, points[index].y),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

package engineering.carr.hanzianimator.compose

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import engineering.carr.hanzianimator.core.GlyphPoint
import engineering.carr.hanzianimator.core.Stroke
import engineering.carr.hanzianimator.core.OutlineCommand
import engineering.carr.hanzianimator.core.StrokeOrder
import kotlin.math.sqrt

/**
 * Precomputed, size-independent render data for one [StrokeOrder], built once
 * (via `remember`) and reused across every frame.
 *
 * Each normalized command buffer is converted into a platform path once,
 * and its median is turned into a wide-brush reveal path with a ready
 * [PathMeasure], so per-frame drawing only samples a segment length rather than
 * re-parsing or re-deriving point lists.
 */
internal class StrokeGlyphGeometry private constructor(
    val strokes: List<StrokeGeometryEntry>
) {
    /** True when every stroke has both an outline path and a drawable median. */
    val canPractice: Boolean =
        strokes.isNotEmpty() && strokes.all { it.outlinePath != null && it.hasDrawableMedian }

    /** True when at least one stroke has a renderable outline. */
    val hasRenderableOutline: Boolean = strokes.any { it.outlinePath != null }

    companion object {
        fun from(order: StrokeOrder, medianStartExtension: Float): StrokeGlyphGeometry {
            val entries = order.strokes.map { stroke ->
                val outline = runCatching {
                    Path().apply {
                        var at = 0
                        fun coordinate(): Float = stroke.outline.coordinateAt(at++)
                        repeat(stroke.outline.commandCount) { index ->
                            when (stroke.outline.commandAt(index)) {
                                OutlineCommand.MOVE -> moveTo(coordinate(), coordinate())
                                OutlineCommand.LINE -> lineTo(coordinate(), coordinate())
                                OutlineCommand.QUAD -> quadraticTo(coordinate(), coordinate(), coordinate(), coordinate())
                                OutlineCommand.CUBIC -> cubicTo(coordinate(), coordinate(), coordinate(), coordinate(), coordinate(), coordinate())
                                OutlineCommand.CLOSE -> close()
                            }
                        }
                    }
                }.getOrNull()
                val revealPath = buildRevealPath(stroke, medianStartExtension)
                val measure = revealPath?.let { PathMeasure().apply { setPath(it, false) } }
                StrokeGeometryEntry(
                    index = stroke.index,
                    isRadical = stroke.isRadical,
                    componentIndex = stroke.componentIndex,
                    hasDrawableMedian = stroke.hasDrawableMedian,
                    outlinePath = outline,
                    revealPath = revealPath,
                    revealMeasure = measure,
                    revealLength = measure?.length ?: 0f
                )
            }
            return StrokeGlyphGeometry(entries)
        }

        private fun buildRevealPath(stroke: Stroke, startExtension: Float): Path? {
            if (!stroke.hasDrawableMedian) return null
            val points = extendMedianStart(stroke.median, startExtension)
            if (points.size < 2) return null
            return Path().apply {
                moveTo(points.first().x, points.first().y)
                for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
            }
        }

        private fun extendMedianStart(points: List<GlyphPoint>, distance: Float): List<GlyphPoint> {
            if (points.size < 2) return points
            val first = points.first()
            val second = points.firstOrNull { distanceBetween(first, it) > 0.001f } ?: return points
            val dx = first.x - second.x
            val dy = first.y - second.y
            val length = sqrt(dx * dx + dy * dy)
            if (length <= 0f) return points
            val extended = GlyphPoint(
                x = first.x + dx / length * distance,
                y = first.y + dy / length * distance
            )
            return listOf(extended) + points.drop(1)
        }

        private fun distanceBetween(a: GlyphPoint, b: GlyphPoint): Float {
            val dx = a.x - b.x
            val dy = a.y - b.y
            return sqrt(dx * dx + dy * dy)
        }
    }
}

/** Render data for a single stroke; see [StrokeGlyphGeometry]. */
internal class StrokeGeometryEntry(
    val index: Int,
    val isRadical: Boolean,
    val componentIndex: Int?,
    val hasDrawableMedian: Boolean,
    val outlinePath: Path?,
    val revealPath: Path?,
    val revealMeasure: PathMeasure?,
    val revealLength: Float
)

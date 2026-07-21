package engineering.carr.hanzianimator.core

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Maps between glyph coordinate space ([StrokeOrder.GLYPH_SIZE] square) and a
 * canvas of the given [width] and [height], centring the glyph in the largest
 * fitting square and insetting it by [paddingFraction] of that square.
 */
internal data class StrokeViewportTransform(
    val width: Float,
    val height: Float,
    val paddingFraction: Float = 0.06f
) {
    private val square = min(width, height).coerceAtLeast(0f)
    private val padding = square * paddingFraction.coerceIn(0f, 0.45f)

    val scale: Float = if (square > padding * 2f) {
        (square - padding * 2f) / StrokeOrder.GLYPH_SIZE
    } else {
        0f
    }
    val left: Float = (width - square) / 2f + padding
    val top: Float = (height - square) / 2f + padding

    fun glyphToCanvas(point: GlyphPoint): GlyphPoint =
        GlyphPoint(
            x = left + point.x * scale,
            y = top + point.y * scale
        )

    fun canvasToGlyph(point: GlyphPoint, clamp: Boolean = false): GlyphPoint {
        if (scale <= 0f) return GlyphPoint(0f, 0f)
        val result = GlyphPoint(
            x = (point.x - left) / scale,
            y = (point.y - top) / scale
        )
        return if (clamp) {
            GlyphPoint(
                x = result.x.coerceIn(0f, StrokeOrder.GLYPH_SIZE),
                y = result.y.coerceIn(0f, StrokeOrder.GLYPH_SIZE)
            )
        } else {
            result
        }
    }
}

internal fun distanceBetween(a: GlyphPoint, b: GlyphPoint): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return sqrt(dx * dx + dy * dy)
}

internal fun polylineLength(points: List<GlyphPoint>): Float {
    if (points.size < 2) return 0f
    var length = 0f
    for (index in 1 until points.size) {
        length += distanceBetween(points[index - 1], points[index])
    }
    return length
}

internal fun resamplePolyline(points: List<GlyphPoint>, sampleCount: Int): List<GlyphPoint> {
    if (points.isEmpty() || sampleCount <= 0) return emptyList()
    if (sampleCount == 1 || points.size == 1) return List(sampleCount) { points.first() }

    val cumulative = MutableList(points.size) { 0f }
    for (index in 1 until points.size) {
        cumulative[index] = cumulative[index - 1] + distanceBetween(points[index - 1], points[index])
    }
    val totalLength = cumulative.last()
    if (totalLength == 0f) return List(sampleCount) { points.first() }

    val step = totalLength / (sampleCount - 1)
    return List(sampleCount) { sampleIndex ->
        val target = if (sampleIndex == sampleCount - 1) totalLength else step * sampleIndex
        var segmentIndex = 1
        while (segmentIndex < cumulative.lastIndex && cumulative[segmentIndex] < target) {
            segmentIndex += 1
        }
        val segmentStartDistance = cumulative[segmentIndex - 1]
        val segmentLength = cumulative[segmentIndex] - segmentStartDistance
        if (segmentLength == 0f) {
            points[segmentIndex]
        } else {
            val t = ((target - segmentStartDistance) / segmentLength).coerceIn(0f, 1f)
            lerp(points[segmentIndex - 1], points[segmentIndex], t)
        }
    }
}

internal fun pathPrefix(points: List<GlyphPoint>, progress: Float): List<GlyphPoint> {
    if (points.isEmpty()) return emptyList()
    if (points.size == 1 || progress <= 0f) return listOf(points.first())
    if (progress >= 1f) return points

    val totalLength = polylineLength(points)
    if (totalLength == 0f) return listOf(points.first())

    val targetLength = totalLength * progress.coerceIn(0f, 1f)
    val prefix = mutableListOf(points.first())
    var travelled = 0f
    for (index in 1 until points.size) {
        val start = points[index - 1]
        val end = points[index]
        val segmentLength = distanceBetween(start, end)
        if (travelled + segmentLength < targetLength) {
            prefix += end
            travelled += segmentLength
        } else {
            val remaining = max(0f, targetLength - travelled)
            val t = if (segmentLength == 0f) 0f else min(1f, remaining / segmentLength)
            prefix += lerp(start, end, t)
            break
        }
    }
    return prefix
}

internal fun lerp(a: GlyphPoint, b: GlyphPoint, t: Float): GlyphPoint =
    GlyphPoint(
        x = a.x + (b.x - a.x) * t,
        y = a.y + (b.y - a.y) * t
    )

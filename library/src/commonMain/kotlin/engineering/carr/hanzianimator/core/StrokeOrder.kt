package engineering.carr.hanzianimator.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * A point in glyph coordinate space.
 *
 * The glyph coordinate system is a square of side [StrokeOrder.GLYPH_SIZE] with
 * the origin at the top-left and `y` increasing downward — the convention used
 * for on-screen rendering. Hanzi Writer data is authored y-up, so both the
 * outline paths and medians are flipped into this space when a [StrokeOrder] is
 * parsed (see [StrokeOrder.parse]).
 */
public data class GlyphPoint(
    val x: Float,
    val y: Float
)

/**
 * A single stroke of a character.
 *
 * @property index zero-based position of the stroke in canonical stroke order.
 * @property outlineSvgPath the stroke's filled outline as an SVG path string, in
 *   the raw Hanzi Writer (y-up) coordinate system. Rendering code applies the
 *   glyph y-flip once; see [StrokeOrder].
 * @property median the stroke's centre line, resampled points already flipped
 *   into [GlyphPoint] (y-down) space. May be empty when upstream data omits it.
 * @property isRadical whether this stroke belongs to the character's radical.
 */
public data class Stroke(
    val index: Int,
    val outlineSvgPath: String,
    val median: List<GlyphPoint>,
    val isRadical: Boolean
) {
    /** True when the median has enough points to draw or match against. */
    public val hasDrawableMedian: Boolean
        get() = median.size >= 2
}

/**
 * A parsed, render-ready character: its [character] text and ordered [strokes].
 *
 * Instances are produced by [parse] from Hanzi Writer stroke data, or built
 * directly for tests and bring-your-own-data scenarios. Coordinates are already
 * normalized into [GlyphPoint] space, so no further flipping is required.
 */
public data class StrokeOrder(
    val character: String,
    val strokes: List<Stroke>
) {
    /** Number of strokes in the character. */
    public val strokeCount: Int
        get() = strokes.size

    /** The raw (y-up) outline SVG path of each stroke, in stroke order. */
    public val outlineSvgPaths: List<String>
        get() = strokes.map { it.outlineSvgPath }

    /** Indexes of strokes that belong to the character's radical. */
    public val radicalStrokeIndexes: Set<Int>
        get() = strokes.filter { it.isRadical }.map { it.index }.toSet()

    public companion object {
        /** Side length of the square glyph coordinate space (Hanzi Writer's viewport). */
        public const val GLYPH_SIZE: Float = 1024f

        /**
         * The y coordinate the Hanzi Writer y-up space is flipped around, so a
         * raw point `(x, y)` maps to `(x, GLYPH_Y_BASE - y)`.
         */
        internal const val GLYPH_Y_BASE: Float = 900f

        /**
         * Parses Hanzi Writer stroke data for [character].
         *
         * The [hanziWriterJson] is a single character entry with `strokes`
         * (array of SVG path strings), optional `medians` (array of point
         * arrays), and optional `radStrokes` (radical stroke indexes). Medians
         * are flipped into [GlyphPoint] space here.
         *
         * @return the parsed [StrokeOrder], or `null` if the JSON is malformed
         *   or has no strokes.
         */
        public fun parse(character: String, hanziWriterJson: String): StrokeOrder? {
            val obj = runCatching {
                Json.parseToJsonElement(hanziWriterJson) as? JsonObject
            }.getOrNull() ?: return null
            return fromJsonObject(character, obj)
        }

        /**
         * Builds a [StrokeOrder] from an already-parsed Hanzi Writer entry.
         * Used by the bundled provider, which parses a whole shard once and
         * indexes into it, avoiding a re-serialize/re-parse per character.
         */
        internal fun fromJsonObject(character: String, obj: JsonObject): StrokeOrder? {
            val strokePathData = (obj["strokes"] as? JsonArray)
                ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                ?.takeIf { it.isNotEmpty() }
                ?: return null

            val radicalStrokeIndexes = (obj["radStrokes"] as? JsonArray)
                ?.mapNotNull { it.jsonPrimitive.intOrNull }
                ?.filter { it in strokePathData.indices }
                ?.toSet()
                .orEmpty()

            val medians = parseMedians(obj["medians"], strokePathData.size)

            val strokes = strokePathData.mapIndexed { index, pathData ->
                Stroke(
                    index = index,
                    outlineSvgPath = pathData,
                    median = medians[index].map(::flipY),
                    isRadical = index in radicalStrokeIndexes
                )
            }
            return StrokeOrder(character = character, strokes = strokes)
        }

        /** Flips a raw Hanzi Writer (y-up) point into [GlyphPoint] (y-down) space. */
        internal fun flipY(point: GlyphPoint): GlyphPoint =
            GlyphPoint(x = point.x, y = GLYPH_Y_BASE - point.y)

        private fun parseMedians(element: JsonElement?, strokeCount: Int): List<List<GlyphPoint>> {
            val medianArray = element as? JsonArray
            return List(strokeCount) { index ->
                parseMedian(medianArray?.getOrNull(index)) ?: emptyList()
            }
        }

        private fun parseMedian(element: JsonElement?): List<GlyphPoint>? {
            val pointArray = element as? JsonArray ?: return null
            val points = pointArray.mapNotNull { parseMedianPoint(it) }
            return if (points.size == pointArray.size && points.isNotEmpty()) points else null
        }

        private fun parseMedianPoint(element: JsonElement): GlyphPoint? {
            val pair = element as? JsonArray ?: return null
            val x = pair.getOrNull(0)?.jsonPrimitive?.contentOrNull?.toFloatOrNull() ?: return null
            val y = pair.getOrNull(1)?.jsonPrimitive?.contentOrNull?.toFloatOrNull() ?: return null
            return GlyphPoint(x = x, y = y)
        }
    }
}

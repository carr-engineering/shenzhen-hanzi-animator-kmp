package engineering.carr.hanzianimator.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StrokeOrderParseTest {
    @Test
    fun parsesHanziWriterStrokeDataWithMedians() {
        val order = StrokeOrder.parse(
            character = "X",
            hanziWriterJson = """
            {
              "strokes": ["M 1 2 Q 3 4 5 6", "M 7 8 L 9 10"],
              "medians": [[[1, 2], [3, 4]]],
              "radStrokes": [1]
            }
            """.trimIndent()
        )

        requireNotNull(order)
        assertEquals("X", order.character)
        assertEquals(listOf("M 1 2 Q 3 4 5 6", "M 7 8 L 9 10"), order.outlineSvgPaths)
        assertEquals(2, order.strokeCount)
        assertFalse(order.strokes[0].isRadical)
        assertTrue(order.strokes[1].isRadical)
        // Medians are flipped into y-down glyph space at parse: y' = 900 - y.
        assertEquals(listOf(GlyphPoint(1f, 898f), GlyphPoint(3f, 896f)), order.strokes[0].median)
        assertEquals(emptyList(), order.strokes[1].median)
        assertEquals(setOf(1), order.radicalStrokeIndexes)
    }

    @Test
    fun parsesMissingMediansAsEmptyPerStroke() {
        val order = StrokeOrder.parse(
            character = "X",
            hanziWriterJson = """
            {
              "strokes": ["M 1 2 L 3 4", "M 5 6 L 7 8"]
            }
            """.trimIndent()
        )

        requireNotNull(order)
        assertEquals(2, order.strokeCount)
        assertEquals(emptyList(), order.strokes[0].median)
        assertEquals(emptyList(), order.strokes[1].median)
    }

    @Test
    fun ignoresMalformedMediansWithoutDroppingStrokeOutlines() {
        val order = StrokeOrder.parse(
            character = "X",
            hanziWriterJson = """
            {
              "strokes": ["M 1 2 L 3 4", "M 5 6 L 7 8"],
              "medians": [[[1, "bad"], [3, 4]], "bad"]
            }
            """.trimIndent()
        )

        requireNotNull(order)
        assertEquals(2, order.strokeCount)
        assertEquals(emptyList(), order.strokes[0].median)
        assertEquals(emptyList(), order.strokes[1].median)
    }

    @Test
    fun keepsOnlyValidRadicalStrokeIndexes() {
        val order = StrokeOrder.parse(
            character = "X",
            hanziWriterJson = """
            {
              "strokes": ["M 1 2 L 3 4", "M 5 6 L 7 8"],
              "radStrokes": [1, 99, -1, 0]
            }
            """.trimIndent()
        )

        requireNotNull(order)
        assertEquals(setOf(0, 1), order.radicalStrokeIndexes)
        assertTrue(order.strokes[0].isRadical)
        assertTrue(order.strokes[1].isRadical)
    }

    @Test
    fun flipsHanziWriterYUpCoordinatesForRendering() {
        val flipped = StrokeOrder.flipY(GlyphPoint(120f, 250f))

        assertEquals(120f, flipped.x, 0.001f)
        assertEquals(650f, flipped.y, 0.001f)
        // The flip is its own inverse.
        val roundTrip = StrokeOrder.flipY(flipped)
        assertEquals(120f, roundTrip.x, 0.001f)
        assertEquals(250f, roundTrip.y, 0.001f)
    }

    @Test
    fun normalizesMedianPointsAtParse() {
        val order = StrokeOrder.parse(
            character = "X",
            hanziWriterJson = """
            {
              "strokes": ["M 10 100 L 20 200"],
              "medians": [[[10, 100], [20, 200]]]
            }
            """.trimIndent()
        )

        requireNotNull(order)
        assertEquals(listOf(GlyphPoint(10f, 800f), GlyphPoint(20f, 700f)), order.strokes[0].median)
        assertEquals(1024f, StrokeOrder.GLYPH_SIZE, 0.001f)
    }

    @Test
    fun rejectsMissingStrokePaths() {
        assertNull(StrokeOrder.parse("X", """{"radStrokes":[0]}"""))
        assertNull(StrokeOrder.parse("X", "not json"))
    }

    @Test
    fun viewportCentersGlyphAndRoundTripsTouchCoordinates() {
        val viewport = StrokeViewportTransform(
            width = 300f,
            height = 200f,
            paddingFraction = 0.10f
        )

        val center = viewport.glyphToCanvas(GlyphPoint(512f, 512f))
        assertEquals(150f, center.x, 0.001f)
        assertEquals(100f, center.y, 0.001f)

        val source = GlyphPoint(173f, 741f)
        val roundTrip = viewport.canvasToGlyph(viewport.glyphToCanvas(source))
        assertEquals(source.x, roundTrip.x, 0.001f)
        assertEquals(source.y, roundTrip.y, 0.001f)
    }
}

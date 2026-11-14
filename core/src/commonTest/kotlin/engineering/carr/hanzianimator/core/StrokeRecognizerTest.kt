package engineering.carr.hanzianimator.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StrokeRecognizerTest {
    @Test
    fun acceptsCloseForwardStroke() {
        val result = StrokeRecognizer.recognize(
            userPoints = listOf(
                GlyphPoint(4f, 5f),
                GlyphPoint(100f, -3f),
                GlyphPoint(198f, 4f)
            ),
            expectedMedian = horizontalMedian()
        )

        assertTrue(result.accepted)
        assertEquals(StrokeRecognitionStatus.Accepted, result.status)
    }

    @Test
    fun rejectsReversedStrokeAttempt() {
        val result = StrokeRecognizer.recognize(
            userPoints = listOf(
                GlyphPoint(200f, 0f),
                GlyphPoint(100f, 0f),
                GlyphPoint(0f, 0f)
            ),
            expectedMedian = horizontalMedian()
        )

        assertFalse(result.accepted)
        assertEquals(StrokeRecognitionStatus.Reversed, result.status)
    }

    @Test
    fun rejectsStrokeWithWrongStart() {
        val result = StrokeRecognizer.recognize(
            userPoints = listOf(
                GlyphPoint(300f, 300f),
                GlyphPoint(350f, 300f)
            ),
            expectedMedian = horizontalMedian()
        )

        assertEquals(StrokeRecognitionStatus.WrongStart, result.status)
    }

    @Test
    fun rejectsStrokeThatWandersFarFromMedian() {
        val result = StrokeRecognizer.recognize(
            userPoints = listOf(
                GlyphPoint(0f, 0f),
                GlyphPoint(100f, 360f),
                GlyphPoint(200f, 0f)
            ),
            expectedMedian = horizontalMedian(),
            config = StrokeRecognitionConfig(maxLengthRatio = 10f)
        )

        assertEquals(StrokeRecognitionStatus.OffPath, result.status)
    }

    @Test
    fun reportsMissingExpectedMedian() {
        val result = StrokeRecognizer.recognize(
            userPoints = listOf(GlyphPoint(0f, 0f), GlyphPoint(10f, 10f)),
            expectedMedian = emptyList()
        )

        assertEquals(StrokeRecognitionStatus.MissingExpectedMedian, result.status)
    }

    @Test
    fun rejectsTraceThatMatchesALaterStrokeMoreClosely() {
        val order = StrokeOrder(
            character = "X",
            strokes = listOf(
                strokeAt(0, 24f),
                strokeAt(1, 0f)
            )
        )

        val result = StrokeRecognizer.recognize(
            userPoints = listOf(
                GlyphPoint(0f, 0f),
                GlyphPoint(100f, 0f),
                GlyphPoint(200f, 0f)
            ),
            order = order,
            expectedStrokeIndex = 0,
            outlineVisible = true
        )

        assertFalse(result.accepted)
        assertEquals(StrokeRecognitionStatus.WrongStrokeOrder, result.status)
    }

    @Test
    fun acceptsForwardClosedStrokeEvenWhenEndpointsCoincide() {
        val loop = listOf(
            GlyphPoint(0f, 0f),
            GlyphPoint(100f, 0f),
            GlyphPoint(100f, 100f),
            GlyphPoint(0f, 100f),
            GlyphPoint(0f, 0f)
        )

        val result = StrokeRecognizer.recognize(loop, loop)

        assertTrue(result.accepted)
        assertEquals(StrokeRecognitionStatus.Accepted, result.status)
    }
}

private fun strokeAt(index: Int, y: Float): Stroke =
    Stroke(
        index = index,
        outline = StrokeOutline.EMPTY,
        median = listOf(
            GlyphPoint(0f, y),
            GlyphPoint(100f, y),
            GlyphPoint(200f, y)
        ),
        isRadical = false
    )

private fun horizontalMedian(): List<GlyphPoint> =
    listOf(
        GlyphPoint(0f, 0f),
        GlyphPoint(100f, 0f),
        GlyphPoint(200f, 0f)
    )

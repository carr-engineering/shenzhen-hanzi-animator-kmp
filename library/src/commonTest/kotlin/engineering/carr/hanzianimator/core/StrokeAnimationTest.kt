package engineering.carr.hanzianimator.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StrokeAnimationTest {
    @Test
    fun plansTimingBoundariesAndStrokeTransitions() {
        val planner = StrokeAnimationPlanner.fromStrokeOrder(
            orderWithMedians(
                listOf(GlyphPoint(0f, 0f), GlyphPoint(100f, 0f)),
                listOf(GlyphPoint(0f, 100f), GlyphPoint(100f, 100f))
            ),
            StrokeAnimationConfig(
                strokeDurationMillis = 100L,
                minStrokeDurationMillis = 1L,
                delayBetweenStrokesMillis = 20L
            )
        )

        assertEquals(220L, planner.totalDurationMillis)

        val start = planner.frameAt(0L)
        assertEquals(0, start.currentStrokeIndex)
        assertEquals(0f, start.progressByStroke[0], 0.001f)

        val middle = planner.frameAt(50L)
        assertEquals(0, middle.currentStrokeIndex)
        assertEquals(0.5f, middle.progressByStroke[0], 0.001f)

        val delay = planner.frameAt(100L)
        assertNull(delay.currentStrokeIndex)
        assertEquals(listOf(0), delay.completedStrokeIndexes)
        assertEquals(1f, delay.progressByStroke[0], 0.001f)

        val secondStart = planner.frameAt(120L)
        assertEquals(1, secondStart.currentStrokeIndex)
        assertEquals(listOf(0), secondStart.completedStrokeIndexes)
        assertEquals(0f, secondStart.progressByStroke[1], 0.001f)

        val finished = planner.frameAt(220L)
        assertNull(finished.currentStrokeIndex)
        assertEquals(listOf(0, 1), finished.completedStrokeIndexes)
        assertTrue(finished.isFinished)
    }

    @Test
    fun loopModeResetsForReplay() {
        val planner = StrokeAnimationPlanner.fromStrokeOrder(
            orderWithMedians(
                listOf(GlyphPoint(0f, 0f), GlyphPoint(100f, 0f)),
                listOf(GlyphPoint(0f, 100f), GlyphPoint(100f, 100f))
            ),
            StrokeAnimationConfig(
                strokeDurationMillis = 100L,
                minStrokeDurationMillis = 1L,
                delayBetweenStrokesMillis = 20L,
                loop = true
            )
        )

        val replayed = planner.frameAt(230L)

        assertEquals(0, replayed.currentStrokeIndex)
        assertEquals(0.1f, replayed.progressByStroke[0], 0.001f)
        assertEquals(emptyList(), replayed.completedStrokeIndexes)
    }

    @Test
    fun emptyOrderIsImmediatelyFinished() {
        val planner = StrokeAnimationPlanner.fromStrokeOrder(StrokeOrder("X", emptyList()))

        val frame = planner.frameAt(100L)

        assertEquals(0L, planner.totalDurationMillis)
        assertNull(frame.currentStrokeIndex)
        assertEquals(emptyList(), frame.progressByStroke)
        assertTrue(frame.isFinished)
    }

    @Test
    fun defaultTimingScalesWithMedianLength() {
        val short = StrokeAnimationPlanner.fromStrokeOrder(
            orderWithMedians(listOf(GlyphPoint(0f, 0f), GlyphPoint(100f, 0f)))
        )
        val long = StrokeAnimationPlanner.fromStrokeOrder(
            orderWithMedians(listOf(GlyphPoint(0f, 0f), GlyphPoint(700f, 0f)))
        )

        assertTrue(long.totalDurationMillis > short.totalDurationMillis)
        assertTrue(short.totalDurationMillis >= 280L)
        assertTrue(long.totalDurationMillis <= 850L)
    }
}

private fun orderWithMedians(vararg medians: List<GlyphPoint>): StrokeOrder =
    StrokeOrder(
        character = "X",
        strokes = medians.mapIndexed { index, points ->
            Stroke(
                index = index,
                outlineSvgPath = "M $index $index L ${index + 1} ${index + 1}",
                median = points,
                isRadical = false
            )
        }
    )

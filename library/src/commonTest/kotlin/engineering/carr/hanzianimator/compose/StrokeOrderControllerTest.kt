package engineering.carr.hanzianimator.compose

import androidx.compose.runtime.BroadcastFrameClock
import engineering.carr.hanzianimator.core.GlyphPoint
import engineering.carr.hanzianimator.core.Stroke
import engineering.carr.hanzianimator.core.StrokeAnimationConfig
import engineering.carr.hanzianimator.core.StrokeOrder
import engineering.carr.hanzianimator.core.StrokeRecognitionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StrokeOrderControllerTest {
    @Test
    fun playAdvancesFramesThenSettlesOnFullCharacter() = runTest {
        val clock = BroadcastFrameClock()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + clock)
        val controller = StrokeOrderController(
            strokeOrder = twoStrokeOrder(),
            animationConfig = StrokeAnimationConfig(
                strokeDurationMillis = 100L,
                minStrokeDurationMillis = 1L,
                delayBetweenStrokesMillis = 20L
            ),
            scope = scope
        )

        controller.play()
        assertEquals(StrokeOrderMode.Animating, controller.mode)
        testScheduler.runCurrent() // frame loop reaches the base withFrameMillis

        clock.sendFrame(0L) // base frame
        testScheduler.runCurrent()

        clock.sendFrame(50_000_000L) // 50 ms into stroke 0
        testScheduler.runCurrent()
        assertEquals(0, controller.animationFrame.currentStrokeIndex)
        assertEquals(0.5f, controller.animationFrame.progressByStroke[0], 0.01f)

        clock.sendFrame(120_000_000L) // 120 ms -> stroke 1 begins
        testScheduler.runCurrent()
        assertEquals(1, controller.animationFrame.currentStrokeIndex)

        clock.sendFrame(220_000_000L) // total duration -> finished
        testScheduler.runCurrent()
        testScheduler.runCurrent()
        assertEquals(StrokeOrderMode.Idle, controller.mode)
        assertTrue(controller.isStrokeCompleted(0))
        assertTrue(controller.isStrokeCompleted(1))
        assertFalse(controller.isAnimating)

        scope.cancel()
    }

    @Test
    fun quizAcceptsCorrectStrokesAndCompletes() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val controller = StrokeOrderController(twoStrokeOrder(), scope = scope)

        controller.startQuiz()
        assertEquals(StrokeOrderMode.Quiz, controller.mode)
        assertEquals(0, controller.currentStroke)

        val first = controller.submitStroke(listOf(GlyphPoint(0f, 0f), GlyphPoint(200f, 0f)))
        assertTrue(first.accepted)
        assertEquals(setOf(0), controller.acceptedStrokes)
        assertEquals(1, controller.currentStroke)
        assertFalse(controller.isQuizComplete)

        val second = controller.submitStroke(listOf(GlyphPoint(0f, 300f), GlyphPoint(200f, 300f)))
        assertTrue(second.accepted)
        assertTrue(controller.isQuizComplete)
        assertEquals(QuizSummary(totalMistakes = 0, mistakesPerStroke = listOf(0, 0)), controller.quizSummary())

        scope.cancel()
    }

    @Test
    fun quizShowsHintAfterConfiguredMistakes() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val controller = StrokeOrderController(twoStrokeOrder(), hintAfterMistakes = 2, scope = scope)
        controller.startQuiz()

        val miss = listOf(GlyphPoint(400f, 400f), GlyphPoint(450f, 450f))
        controller.submitStroke(miss)
        assertFalse(controller.hintVisible)
        assertEquals(1, controller.mistakesOnCurrentStroke)

        controller.submitStroke(miss)
        assertTrue(controller.hintVisible)
        assertEquals(2, controller.totalMistakes)

        // Accepting the stroke clears the hint and per-stroke mistakes.
        controller.submitStroke(listOf(GlyphPoint(0f, 0f), GlyphPoint(200f, 0f)))
        assertFalse(controller.hintVisible)
        assertEquals(0, controller.mistakesOnCurrentStroke)
        assertEquals(2, controller.totalMistakes)

        scope.cancel()
    }

    @Test
    fun strayTapDoesNotCountAsMistake() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val controller = StrokeOrderController(twoStrokeOrder(), hintAfterMistakes = 1, scope = scope)
        controller.startQuiz()

        // A tap yields (after deduplication) fewer than two distinct points.
        val tap = controller.submitStroke(listOf(GlyphPoint(100f, 100f), GlyphPoint(100.5f, 100f)))
        assertEquals(StrokeRecognitionStatus.EmptyInput, tap.status)
        assertEquals(0, controller.mistakesOnCurrentStroke)
        assertEquals(0, controller.totalMistakes)
        assertFalse(controller.hintVisible)
        assertEquals(StrokeRecognitionStatus.EmptyInput, controller.lastRecognitionStatus)

        scope.cancel()
    }

    @Test
    fun swappingStrokeOrderResetsState() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val controller = StrokeOrderController(twoStrokeOrder(), scope = scope)
        controller.startQuiz()
        controller.submitStroke(listOf(GlyphPoint(0f, 0f), GlyphPoint(200f, 0f)))
        assertEquals(setOf(0), controller.acceptedStrokes)

        controller.strokeOrder = oneStrokeOrder()
        assertEquals(StrokeOrderMode.Idle, controller.mode)
        assertEquals(emptySet(), controller.acceptedStrokes)
        assertEquals(0, controller.currentStroke)
        assertNull(controller.lastRecognitionStatus)

        scope.cancel()
    }

    @Test
    fun nextAndPreviousStepThroughStrokesStatically() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val controller = StrokeOrderController(twoStrokeOrder(), scope = scope)

        controller.nextStroke()
        assertEquals(StrokeOrderMode.Idle, controller.mode)
        assertTrue(controller.isStrokeCompleted(0))
        assertFalse(controller.isStrokeCompleted(1))

        controller.showFullCharacter()
        assertTrue(controller.isStrokeCompleted(1))

        controller.previousStroke()
        assertFalse(controller.isStrokeCompleted(1))

        scope.cancel()
    }
}

private fun twoStrokeOrder(): StrokeOrder =
    StrokeOrder(
        character = "X",
        strokes = listOf(
            Stroke(0, engineering.carr.hanzianimator.core.StrokeOutline.Builder().moveTo(0f, 0f).lineTo(200f, 0f).build(), listOf(GlyphPoint(0f, 0f), GlyphPoint(200f, 0f)), isRadical = false),
            Stroke(1, engineering.carr.hanzianimator.core.StrokeOutline.Builder().moveTo(0f, 300f).lineTo(200f, 300f).build(), listOf(GlyphPoint(0f, 300f), GlyphPoint(200f, 300f)), isRadical = false)
        )
    )

private fun oneStrokeOrder(): StrokeOrder =
    StrokeOrder(
        character = "Y",
        strokes = listOf(
            Stroke(0, engineering.carr.hanzianimator.core.StrokeOutline.Builder().moveTo(0f, 0f).lineTo(200f, 0f).build(), listOf(GlyphPoint(0f, 0f), GlyphPoint(200f, 0f)), isRadical = false)
        )
    )

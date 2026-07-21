package engineering.carr.hanzianimator.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuizLogicTest {
    @Test
    fun acceptsOnlyTheNextExpectedStroke() {
        val order = practiceOrder()
        var state = startStrokePractice()

        state = submitPracticeStroke(
            state = state,
            order = order,
            userPoints = listOf(GlyphPoint(0f, 300f), GlyphPoint(200f, 300f))
        )

        assertEquals(0, state.currentExpectedStroke)
        assertEquals(emptyList(), state.acceptedStrokeIndexes)
        assertEquals(1, state.mistakes)
        assertFalse(state.isComplete)

        state = submitPracticeStroke(
            state = state,
            order = order,
            userPoints = listOf(GlyphPoint(0f, 0f), GlyphPoint(200f, 0f))
        )

        assertEquals(listOf(0), state.acceptedStrokeIndexes)
        assertEquals(1, state.currentExpectedStroke)
        assertEquals(StrokeRecognitionStatus.Accepted, state.lastRecognitionStatus)
    }

    @Test
    fun completesAfterFinalAcceptedStroke() {
        val order = practiceOrder()
        var state = startStrokePractice()

        state = submitPracticeStroke(
            state,
            order,
            listOf(GlyphPoint(0f, 0f), GlyphPoint(200f, 0f))
        )
        state = submitPracticeStroke(
            state,
            order,
            listOf(GlyphPoint(0f, 300f), GlyphPoint(200f, 300f))
        )

        assertTrue(state.isComplete)
        assertEquals(listOf(0, 1), state.acceptedStrokeIndexes)
        assertEquals(2, state.currentExpectedStroke)
    }

    @Test
    fun showsHintAfterConfiguredMistakeThreshold() {
        val order = practiceOrder()
        var state = startStrokePractice()
        val config = StrokePracticeConfig(hintMistakeThreshold = 2)

        state = submitPracticeStroke(
            state,
            order,
            listOf(GlyphPoint(400f, 400f), GlyphPoint(450f, 450f)),
            config
        )
        assertFalse(state.hintVisible)

        state = submitPracticeStroke(
            state,
            order,
            listOf(GlyphPoint(400f, 400f), GlyphPoint(450f, 450f)),
            config
        )

        assertEquals(2, state.mistakes)
        assertTrue(state.hintVisible)
    }

    @Test
    fun resetPracticeClearsAcceptedStrokesAndMistakes() {
        val order = practiceOrder()
        val accepted = submitPracticeStroke(
            startStrokePractice(),
            order,
            listOf(GlyphPoint(0f, 0f), GlyphPoint(200f, 0f))
        )

        val reset = resetStrokePractice()

        assertEquals(listOf(0), accepted.acceptedStrokeIndexes)
        assertEquals(StrokePracticeMode.Practice, reset.mode)
        assertEquals(emptyList(), reset.acceptedStrokeIndexes)
        assertEquals(0, reset.mistakes)
        assertFalse(reset.hintVisible)
    }

    @Test
    fun hintThresholdAndMistakesResetPerStroke() {
        val order = practiceOrder()
        var state = startStrokePractice()
        val miss = listOf(GlyphPoint(400f, 400f), GlyphPoint(450f, 450f))

        repeat(2) {
            state = submitPracticeStroke(state, order, miss)
        }
        assertEquals(2, state.mistakesOnCurrentStroke)
        assertEquals(2, state.totalMistakes)
        assertTrue(state.hintVisible)

        state = submitPracticeStroke(
            state,
            order,
            listOf(GlyphPoint(0f, 0f), GlyphPoint(200f, 0f))
        )

        assertEquals(0, state.mistakesOnCurrentStroke)
        assertEquals(2, state.totalMistakes)
        assertFalse(state.hintVisible)
    }
}

private fun practiceOrder(): StrokeOrder =
    StrokeOrder(
        character = "X",
        strokes = listOf(
            Stroke(
                index = 0,
                outlineSvgPath = "M 0 0 L 200 0",
                median = listOf(GlyphPoint(0f, 0f), GlyphPoint(200f, 0f)),
                isRadical = false
            ),
            Stroke(
                index = 1,
                outlineSvgPath = "M 0 300 L 200 300",
                median = listOf(GlyphPoint(0f, 300f), GlyphPoint(200f, 300f)),
                isRadical = false
            )
        )
    )

package engineering.carr.hanzianimator.core

import kotlin.test.*

class StrokeSessionTest {
    private fun order(median: List<GlyphPoint> = listOf(GlyphPoint(0f,0f),GlyphPoint(200f,0f))) = StrokeOrder("X", listOf(Stroke(0, StrokeOutline.EMPTY, median, false)))
    @Test fun pauseResumeAndCompletionPreserveClockPosition() {
        val session = StrokeSession(order(), StrokeAnimationConfig(strokeDurationMillis = 1000))
        session.play(); session.advanceAnimation(400); session.pause(); session.advanceAnimation(900)
        assertEquals(400L, session.animationElapsedMillis)
        assertEquals(0.4f, session.animationFrame.progressByStroke.single())
        session.play(); assertEquals(400L, session.animationElapsedMillis)
        session.advanceAnimation(1000); assertFalse(session.isPlaying)
        assertEquals(StrokeSessionMode.Idle, session.mode); assertTrue(session.isStrokeCompleted(0))
        session.play(); assertEquals(0L, session.animationElapsedMillis)
    }
    @Test fun impossiblePracticeIsDisabledAndTapDoesNotCountAsMistake() {
        assertFalse(StrokeSession(order(emptyList())).startQuiz())
        assertFalse(StrokeSession(order(listOf(GlyphPoint(0f,0f),GlyphPoint(0f,0f)))).startQuiz())
        val session = StrokeSession(order()); assertTrue(session.startQuiz())
        assertEquals(StrokeRecognitionStatus.EmptyInput, session.submitStroke(listOf(GlyphPoint(0f,0f))).status)
        assertEquals(0, session.totalMistakes)
        assertTrue(session.submitStroke(session.strokeOrder.strokes[0].median).accepted)
        assertTrue(session.isQuizComplete); assertEquals(QuizSummary(0,listOf(0)),session.quizSummary())
        session.reset(); assertFalse(session.isQuizComplete); assertNull(session.quizSummary())
    }
    @Test fun replacementResetsAllPlaybackAndQuizState() {
        val session = StrokeSession(order()); session.play(); session.advanceAnimation(50)
        session.strokeOrder = order(emptyList())
        assertFalse(session.isPlaying); assertEquals(StrokeSessionMode.Idle, session.mode)
        assertEquals(0, session.idleRevealedCount); assertEquals(0L, session.animationElapsedMillis)
    }
    @Test fun staticControlsExitQuiz() {
        val session = StrokeSession(order()); session.startQuiz(); session.showHint(); session.nextStroke()
        assertEquals(StrokeSessionMode.Idle,session.mode); assertFalse(session.hintVisible)
        assertTrue(session.isStrokeCompleted(0)); session.previousStroke(); assertFalse(session.isStrokeCompleted(0))
    }
    @Test fun modelInputsAreCopiedAndValidated() {
        val coordinates = floatArrayOf(0f,0f); val commands = intArrayOf(0)
        val outline = StrokeOutline(commands, coordinates); coordinates[0] = 999f; commands[0] = 4
        assertEquals(0f, outline.coordinateAt(0)); assertEquals(OutlineCommand.MOVE, outline.commandAt(0))
        val median = mutableListOf(GlyphPoint(0f,0f)); val stroke = Stroke(0,outline,median,false)
        val strokes = mutableListOf(stroke); val components = mutableListOf("木")
        val order = StrokeOrder("木",strokes,components); median.clear(); strokes.clear(); components.clear()
        assertEquals(1,order.strokeCount); assertEquals(listOf("木"), order.components); assertEquals(1, stroke.median.size)
        assertFailsWith<IllegalArgumentException> { GlyphPoint(Float.NaN,0f) }
        assertFailsWith<IllegalArgumentException> { StrokeOrder("X",listOf(Stroke(1,outline,emptyList(),false))) }
        assertFailsWith<IllegalArgumentException> { StrokeOrder("X",listOf(Stroke(0,outline,emptyList(),false,0))) }
    }
    @Test fun timingAndRecognitionConfigsRejectInvalidRanges() {
        assertFailsWith<IllegalArgumentException> { StrokeAnimationConfig(strokeDurationMillis = 0) }
        assertFailsWith<IllegalArgumentException> { StrokeAnimationConfig(speedMultiplier = Float.NaN) }
        assertFailsWith<IllegalArgumentException> { StrokeRecognitionConfig(minCoverage = 2f) }
        assertFailsWith<IllegalArgumentException> { StrokeRecognitionConfig(sampleCount = 100000) }
    }
    @Test fun viewportCentersAndRoundTripsCoordinates() {
        val viewport = StrokeViewportTransform(300f,200f,0.1f)
        assertEquals(GlyphPoint(150f,100f),viewport.glyphToCanvas(GlyphPoint(512f,512f)))
        val source = GlyphPoint(173f,741f); val result=viewport.canvasToGlyph(viewport.glyphToCanvas(source))
        assertEquals(source.x,result.x,0.001f); assertEquals(source.y,result.y,0.001f)
    }
    @Test fun explicitDurationBypassesAutomaticClamp() {
        val planner=StrokeAnimationPlanner.fromStrokeOrder(order(),StrokeAnimationConfig(strokeDurationMillis=2000,minStrokeDurationMillis=280,maxStrokeDurationMillis=850))
        assertEquals(2000L, planner.totalDurationMillis); assertEquals(0.5f,planner.frameAt(1000).progressByStroke.single())
    }
    @Test fun quizCountsMistakesShowsHintAndReturnsPerStrokeSummary() {
        val session = StrokeSession(order(), hintAfterMistakes = 2)
        session.startQuiz()
        repeat(2) { assertEquals(StrokeRecognitionStatus.Reversed,session.submitStroke(session.strokeOrder.strokes[0].median.reversed()).status) }
        assertEquals(2,session.totalMistakes); assertEquals(2,session.mistakesOnCurrentStroke)
        assertTrue(session.hintVisible)
        session.submitStroke(session.strokeOrder.strokes[0].median)
        assertFalse(session.hintVisible); assertTrue(session.isQuizComplete)
        assertEquals(QuizSummary(2,listOf(2)),session.quizSummary())
        session.stop(); assertEquals(0,session.totalMistakes); assertNull(session.lastRecognitionStatus)
    }
    @Test fun loopingPlaybackNeverFinishesAndResetsProgress() {
        val session = StrokeSession(order(), StrokeAnimationConfig(strokeDurationMillis = 100,loop=true))
        session.play(); session.advanceAnimation(150)
        assertTrue(session.isPlaying); assertEquals(StrokeSessionMode.Animating,session.mode)
        assertEquals(0.5f,session.animationFrame.progressByStroke.single())
    }
    @Test fun timelineOverflowIsRejectedInsteadOfCompletingImmediately() {
        val two=StrokeOrder("X",listOf(Stroke(0,StrokeOutline.EMPTY,emptyList(),false),Stroke(1,StrokeOutline.EMPTY,emptyList(),false)))
        assertFailsWith<IllegalArgumentException> { StrokeAnimationPlanner.fromStrokeOrder(two,StrokeAnimationConfig(strokeDurationMillis=Long.MAX_VALUE)) }
    }

}

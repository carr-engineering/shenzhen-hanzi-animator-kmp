package engineering.carr.hanzianimator.core

public enum class StrokeSessionMode { Idle, Animating, Quiz }
public data class QuizSummary(val totalMistakes: Int, val mistakesPerStroke: List<Int>)
public data class StrokeReveal(val index: Int, val progress: Float)

/** Pure state machine. Hosts provide a clock and notify their own UI after mutations. */
public class StrokeSession(
    strokeOrder: StrokeOrder,
    private val animationConfig: StrokeAnimationConfig = StrokeAnimationConfig(),
    private val recognitionConfig: StrokeRecognitionConfig = StrokeRecognitionConfig(),
    private val hintAfterMistakes: Int = 2
) {
    init { require(hintAfterMistakes > 0) }
    public var strokeOrder: StrokeOrder = strokeOrder
        set(value) { field = value; planner = StrokeAnimationPlanner.fromStrokeOrder(value, animationConfig); reset() }
    private var planner = StrokeAnimationPlanner.fromStrokeOrder(strokeOrder, animationConfig)
    public var mode: StrokeSessionMode = StrokeSessionMode.Idle
        private set
    public var animationFrame: StrokeAnimationFrame = EMPTY_FRAME
        private set
    public var isPlaying: Boolean = false
        private set
    /** Clock position used to resume playback after a pause. */
    public var animationElapsedMillis: Long = 0
        private set
    public var idleRevealedCount: Int = 0
        private set
    public var acceptedStrokes: Set<Int> = emptySet()
        private set
    public var currentStroke: Int = 0
        private set
    public var mistakesOnCurrentStroke: Int = 0
        private set
    public var totalMistakes: Int = 0
        private set
    public var hintVisible: Boolean = false
        private set
    public var isQuizComplete: Boolean = false
        private set
    public var lastRecognitionStatus: StrokeRecognitionStatus? = null
        private set
    public var outlineVisible: Boolean = true
    private var mistakesPerStroke = IntArray(strokeOrder.strokeCount)

    public fun play() {
        if (strokeOrder.strokeCount == 0) return
        if (mode != StrokeSessionMode.Animating) animationElapsedMillis = 0
        clearQuiz()
        idleRevealedCount = 0
        mode = StrokeSessionMode.Animating
        isPlaying = true
        animationFrame = planner.frameAt(animationElapsedMillis)
    }
    public fun pause() { isPlaying = false }
    /** Advance to an absolute elapsed time since playback began, including pre-pause time. */
    public fun advanceAnimation(elapsedMillis: Long) {
        if (!isPlaying) return
        animationElapsedMillis = elapsedMillis.coerceAtLeast(0)
        animationFrame = planner.frameAt(animationElapsedMillis)
        if (animationFrame.isFinished) {
            isPlaying = false
            mode = StrokeSessionMode.Idle
            idleRevealedCount = strokeOrder.strokeCount
            animationElapsedMillis = 0
            animationFrame = EMPTY_FRAME
        }
    }
    public fun stop() { staticReveal(0) }
    public fun reset() { staticReveal(0) }
    public fun showFullCharacter() { staticReveal(strokeOrder.strokeCount) }
    public fun nextStroke() { staticReveal((idleRevealedCount + 1).coerceAtMost(strokeOrder.strokeCount)) }
    public fun previousStroke() { staticReveal((idleRevealedCount - 1).coerceAtLeast(0)) }
    private fun staticReveal(count: Int) {
        isPlaying = false; animationElapsedMillis = 0; animationFrame = EMPTY_FRAME
        clearQuiz(); idleRevealedCount = count; mode = StrokeSessionMode.Idle
    }
    /** Returns false when any expected stroke lacks a usable median. */
    public fun startQuiz(): Boolean {
        reset()
        if (!strokeOrder.supportsPractice) return false
        mode = StrokeSessionMode.Quiz
        return true
    }
    public fun stopQuiz() { reset() }
    public fun showHint() { if (mode == StrokeSessionMode.Quiz && !isQuizComplete) hintVisible = true }
    public fun submitStroke(points: List<GlyphPoint>): StrokeRecognitionResult {
        if (mode != StrokeSessionMode.Quiz || isQuizComplete) return StrokeRecognitionResult(StrokeRecognitionStatus.EmptyInput)
        val result = StrokeRecognizer.recognize(points, strokeOrder, currentStroke, outlineVisible, recognitionConfig)
        lastRecognitionStatus = result.status
        if (result.status == StrokeRecognitionStatus.EmptyInput || result.status == StrokeRecognitionStatus.MissingExpectedMedian) return result
        if (result.accepted) {
            acceptedStrokes = acceptedStrokes + currentStroke
            mistakesOnCurrentStroke = 0; hintVisible = false
            if (acceptedStrokes.size == strokeOrder.strokeCount) isQuizComplete = true else currentStroke++
        } else {
            mistakesOnCurrentStroke++; totalMistakes++; mistakesPerStroke[currentStroke]++
            if (mistakesOnCurrentStroke >= hintAfterMistakes) hintVisible = true
        }
        return result
    }
    public fun quizSummary(): QuizSummary? = if (isQuizComplete) QuizSummary(totalMistakes, mistakesPerStroke.toList()) else null
    public fun isStrokeCompleted(index: Int): Boolean = when (mode) {
        StrokeSessionMode.Idle -> index in 0 until idleRevealedCount
        StrokeSessionMode.Animating -> index in animationFrame.completedStrokeIndexes
        StrokeSessionMode.Quiz -> index in acceptedStrokes
    }
    public fun activeReveal(): StrokeReveal? = if (mode == StrokeSessionMode.Animating) animationFrame.currentStrokeIndex?.let { StrokeReveal(it, animationFrame.progressByStroke[it]) } else null
    private fun clearQuiz() {
        acceptedStrokes = emptySet(); currentStroke = 0; mistakesOnCurrentStroke = 0; totalMistakes = 0
        hintVisible = false; isQuizComplete = false; lastRecognitionStatus = null
        mistakesPerStroke = IntArray(strokeOrder.strokeCount)
    }
    public companion object { public val EMPTY_FRAME: StrokeAnimationFrame = StrokeAnimationFrame(null, emptyList(), emptyList(), true) }
}

package engineering.carr.hanzianimator.compose

import androidx.compose.runtime.*
import engineering.carr.hanzianimator.core.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

public typealias StrokeOrderMode = StrokeSessionMode
public typealias QuizSummary = engineering.carr.hanzianimator.core.QuizSummary
internal typealias StrokeReveal = engineering.carr.hanzianimator.core.StrokeReveal

/** Compose state and frame-clock adapter for the shared, headless session engine. */
@Stable
public class StrokeOrderController(
    strokeOrder: StrokeOrder,
    animationConfig: StrokeAnimationConfig = StrokeAnimationConfig(),
    recognitionConfig: StrokeRecognitionConfig = StrokeRecognitionConfig(),
    hintAfterMistakes: Int = 2,
    private val scope: CoroutineScope
) {
    private val session = StrokeSession(strokeOrder, animationConfig, recognitionConfig, hintAfterMistakes)
    private var revision by mutableIntStateOf(0)
    private var frameJob: Job? = null
    public var animationFrame: StrokeAnimationFrame by mutableStateOf(session.animationFrame)
        private set
    private fun <T> observed(read: () -> T): T { revision; return read() }
    private fun <T> change(action: () -> T): T {
        val result = action(); animationFrame = session.animationFrame; revision++; return result
    }
    public var strokeOrder: StrokeOrder
        get() = observed { session.strokeOrder }
        set(value) { frameJob?.cancel(); frameJob = null; change { session.strokeOrder = value } }
    public val mode: StrokeOrderMode get() = observed { session.mode }
    public val isAnimating: Boolean get() = observed { session.isPlaying }
    internal val idleRevealedCount: Int get() = observed { session.idleRevealedCount }
    public val acceptedStrokes: Set<Int> get() = observed { session.acceptedStrokes }
    public val currentStroke: Int get() = observed { session.currentStroke }
    public val mistakesOnCurrentStroke: Int get() = observed { session.mistakesOnCurrentStroke }
    public val totalMistakes: Int get() = observed { session.totalMistakes }
    public val hintVisible: Boolean get() = observed { session.hintVisible }
    public val isQuizComplete: Boolean get() = observed { session.isQuizComplete }
    public val lastRecognitionStatus: StrokeRecognitionStatus? get() = observed { session.lastRecognitionStatus }
    public var outlineVisible: Boolean
        get() = observed { session.outlineVisible }
        set(value) { change { session.outlineVisible = value } }
    public fun play() {
        frameJob?.cancel()
        change { session.play() }
        if (!session.isPlaying) return
        frameJob = scope.launch {
            val base = withFrameMillis { it } - session.animationElapsedMillis
            while (isActive && session.isPlaying) {
                session.advanceAnimation(withFrameMillis { it } - base)
                animationFrame = session.animationFrame
            }
            if (isActive) { revision++; frameJob = null }
        }
    }
    public fun pause() { frameJob?.cancel(); frameJob = null; change { session.pause() } }
    public fun stop() { frameJob?.cancel(); frameJob = null; change { session.stop() } }
    public fun reset() { frameJob?.cancel(); frameJob = null; change { session.reset() } }
    public fun nextStroke() { frameJob?.cancel(); frameJob = null; change { session.nextStroke() } }
    public fun previousStroke() { frameJob?.cancel(); frameJob = null; change { session.previousStroke() } }
    public fun showFullCharacter() { frameJob?.cancel(); frameJob = null; change { session.showFullCharacter() } }
    public fun startQuiz(): Boolean { frameJob?.cancel(); frameJob = null; return change { session.startQuiz() } }
    public fun stopQuiz() { change { session.stopQuiz() } }
    public fun showHint() { change { session.showHint() } }
    public fun submitStroke(points: List<GlyphPoint>): StrokeRecognitionResult = change { session.submitStroke(points) }
    public fun quizSummary(): QuizSummary? = observed { session.quizSummary() }
    internal fun isStrokeCompleted(index: Int): Boolean = when (mode) {
        StrokeOrderMode.Animating -> index in animationFrame.completedStrokeIndexes
        else -> session.isStrokeCompleted(index)
    }
    internal fun activeReveal(): StrokeReveal? {
        if (mode != StrokeOrderMode.Animating) return null
        val frame = animationFrame
        val index = frame.currentStrokeIndex ?: return null
        return StrokeReveal(index, frame.progressByStroke.getOrNull(index) ?: 0f)
    }
}

@Composable
public fun rememberStrokeOrderController(
    strokeOrder: StrokeOrder,
    animationConfig: StrokeAnimationConfig = StrokeAnimationConfig(),
    recognitionConfig: StrokeRecognitionConfig = StrokeRecognitionConfig(),
    hintAfterMistakes: Int = 2
): StrokeOrderController {
    val scope = rememberCoroutineScope()
    val controller = remember(strokeOrder, scope, animationConfig, recognitionConfig, hintAfterMistakes) {
        StrokeOrderController(strokeOrder, animationConfig, recognitionConfig, hintAfterMistakes, scope)
    }
    DisposableEffect(controller) { onDispose { controller.stop() } }
    return controller
}

package engineering.carr.hanzianimator.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import engineering.carr.hanzianimator.core.GlyphPoint
import engineering.carr.hanzianimator.core.StrokeAnimationConfig
import engineering.carr.hanzianimator.core.StrokeAnimationFrame
import engineering.carr.hanzianimator.core.StrokeAnimationPlanner
import engineering.carr.hanzianimator.core.StrokeOrder
import engineering.carr.hanzianimator.core.StrokeRecognitionConfig
import engineering.carr.hanzianimator.core.StrokeRecognitionResult
import engineering.carr.hanzianimator.core.StrokeRecognitionStatus
import engineering.carr.hanzianimator.core.StrokeRecognizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** The high-level state a [StrokeOrderController] is in. */
public enum class StrokeOrderMode {
    /** Not animating; strokes are shown statically (none, stepped, or full). */
    Idle,

    /** Playing the stroke-order animation. */
    Animating,

    /** Guided quiz: the user traces each stroke in order. */
    Quiz
}

/** A completed quiz's mistake tally. */
public data class QuizSummary(
    val totalMistakes: Int,
    val mistakesPerStroke: List<Int>
)

/**
 * Drives stroke-order animation and quizzing for a single character, backed by
 * Compose snapshot state so that per-frame updates invalidate only the drawing
 * phase of [StrokeOrderCanvas], not the surrounding composition.
 *
 * Create one with [rememberStrokeOrderController]; the frame loop runs on the
 * supplied [scope], which must carry a `MonotonicFrameClock` (composition scopes
 * and test scopes with a frame clock both do).
 */
@Stable
public class StrokeOrderController(
    strokeOrder: StrokeOrder,
    private val animationConfig: StrokeAnimationConfig = StrokeAnimationConfig(),
    private val recognitionConfig: StrokeRecognitionConfig = StrokeRecognitionConfig(),
    private val hintAfterMistakes: Int = 2,
    private val scope: CoroutineScope
) {
    private var _strokeOrder by mutableStateOf(strokeOrder)
    private var planner by mutableStateOf(StrokeAnimationPlanner.fromStrokeOrder(strokeOrder, animationConfig))

    /**
     * The character being shown. Assigning a new value stops any animation or
     * quiz and resets to an empty idle canvas.
     */
    public var strokeOrder: StrokeOrder
        get() = _strokeOrder
        set(value) {
            if (value === _strokeOrder) return
            frameJob?.cancel()
            _strokeOrder = value
            planner = StrokeAnimationPlanner.fromStrokeOrder(value, animationConfig)
            resetStateForNewOrder()
        }

    /** The current high-level mode. */
    public var mode: StrokeOrderMode by mutableStateOf(StrokeOrderMode.Idle)
        private set

    /** The most recent animation frame; read from the draw phase during [StrokeOrderMode.Animating]. */
    public var animationFrame: StrokeAnimationFrame by mutableStateOf(EMPTY_FRAME)
        private set

    /** True while the animation frame loop is actively advancing (false when paused/idle). */
    public val isAnimating: Boolean
        get() = frameJob?.isActive == true

    // Strokes shown statically in Idle mode: indexes [0, idleRevealedCount).
    internal var idleRevealedCount: Int by mutableStateOf(0)
        private set

    private var animationElapsedMillis: Long = 0L
    private var frameJob: Job? = null

    // ---- Quiz state -------------------------------------------------------

    /** Strokes accepted so far in the current quiz. */
    public var acceptedStrokes: Set<Int> by mutableStateOf(emptySet())
        private set

    /** The stroke the quiz currently expects (also the step index in Idle). */
    public var currentStroke: Int by mutableStateOf(0)
        private set

    /** Mistakes made on the current expected stroke. */
    public var mistakesOnCurrentStroke: Int by mutableStateOf(0)
        private set

    /** Total mistakes made across the current quiz. */
    public var totalMistakes: Int by mutableStateOf(0)
        private set

    /** Whether the next-stroke hint should be shown. */
    public var hintVisible: Boolean by mutableStateOf(false)
        private set

    /** Whether the current quiz has been completed. */
    public var isQuizComplete: Boolean by mutableStateOf(false)
        private set

    /** The status of the most recently submitted stroke, or `null` before any submission. */
    public var lastRecognitionStatus: StrokeRecognitionStatus? by mutableStateOf(null)
        private set

    private var mistakesPerStroke: IntArray = IntArray(strokeOrder.strokeCount)

    // ---- Outline toggle (affects rendering and recognition leniency) ------

    /** Whether the character outline is shown as a tracing guide. */
    public var outlineVisible: Boolean by mutableStateOf(true)

    // ---- Playback controls ------------------------------------------------

    /** Starts or resumes the stroke-order animation from the current position. */
    public fun play() {
        if (planner.strokeCount == 0) return
        frameJob?.cancel()
        exitQuizState()
        idleRevealedCount = 0
        mode = StrokeOrderMode.Animating
        frameJob = scope.launch {
            val base = withFrameMillis { it } - animationElapsedMillis
            while (isActive) {
                animationElapsedMillis = withFrameMillis { it } - base
                animationFrame = planner.frameAt(animationElapsedMillis)
                if (animationFrame.isFinished) break
            }
            if (isActive && !animationConfig.loop) {
                mode = StrokeOrderMode.Idle
                idleRevealedCount = _strokeOrder.strokeCount
                animationElapsedMillis = 0L
                animationFrame = EMPTY_FRAME
            }
        }
    }

    /** Pauses the animation, freezing the current frame. */
    public fun pause() {
        frameJob?.cancel()
        frameJob = null
    }

    /** Stops the animation and returns to an empty idle canvas. */
    public fun stop() {
        frameJob?.cancel()
        frameJob = null
        animationElapsedMillis = 0L
        animationFrame = EMPTY_FRAME
        idleRevealedCount = 0
        mode = StrokeOrderMode.Idle
    }

    /** Reveals the whole character statically. */
    public fun showFullCharacter() {
        stopAnimationInto(_strokeOrder.strokeCount)
    }

    /** Reveals one more stroke statically. */
    public fun nextStroke() {
        stopAnimationInto((idleRevealedCount + 1).coerceAtMost(_strokeOrder.strokeCount))
    }

    /** Hides the most recently revealed static stroke. */
    public fun previousStroke() {
        stopAnimationInto((idleRevealedCount - 1).coerceAtLeast(0))
    }

    /** Stops animation and quiz, returning to an empty idle canvas. */
    public fun reset() {
        frameJob?.cancel()
        frameJob = null
        animationElapsedMillis = 0L
        animationFrame = EMPTY_FRAME
        resetStateForNewOrder()
    }

    private fun stopAnimationInto(revealed: Int) {
        frameJob?.cancel()
        frameJob = null
        exitQuizState()
        animationElapsedMillis = 0L
        animationFrame = EMPTY_FRAME
        idleRevealedCount = revealed
        mode = StrokeOrderMode.Idle
    }

    // ---- Quiz -------------------------------------------------------------

    /** Starts a guided quiz from the first stroke. */
    public fun startQuiz() {
        frameJob?.cancel()
        frameJob = null
        animationElapsedMillis = 0L
        animationFrame = EMPTY_FRAME
        idleRevealedCount = 0
        acceptedStrokes = emptySet()
        currentStroke = 0
        mistakesOnCurrentStroke = 0
        totalMistakes = 0
        hintVisible = false
        isQuizComplete = false
        lastRecognitionStatus = null
        mistakesPerStroke = IntArray(_strokeOrder.strokeCount)
        mode = StrokeOrderMode.Quiz
    }

    /** Ends the quiz and returns to an empty idle canvas. */
    public fun stopQuiz() {
        exitQuizState()
        mode = StrokeOrderMode.Idle
    }

    /** Forces the next-stroke hint to be shown. */
    public fun showHint() {
        if (mode == StrokeOrderMode.Quiz && !isQuizComplete) hintVisible = true
    }

    /**
     * Grades a traced stroke (in glyph coordinates) against the currently
     * expected stroke, updating quiz state. Returns the recognition result;
     * a no-op result is returned when not quizzing.
     */
    public fun submitStroke(points: List<GlyphPoint>): StrokeRecognitionResult {
        if (mode != StrokeOrderMode.Quiz || isQuizComplete) {
            return StrokeRecognitionResult(StrokeRecognitionStatus.EmptyInput)
        }
        val result = StrokeRecognizer.recognize(
            userPoints = points,
            order = _strokeOrder,
            expectedStrokeIndex = currentStroke,
            outlineVisible = outlineVisible,
            config = recognitionConfig
        )
        lastRecognitionStatus = result.status
        if (result.accepted) {
            acceptedStrokes = acceptedStrokes + currentStroke
            mistakesOnCurrentStroke = 0
            hintVisible = false
            if (acceptedStrokes.size >= _strokeOrder.strokeCount) {
                isQuizComplete = true
            } else {
                currentStroke += 1
            }
        } else {
            mistakesOnCurrentStroke += 1
            totalMistakes += 1
            mistakesPerStroke.getOrNull(currentStroke)?.let {
                mistakesPerStroke[currentStroke] = it + 1
            }
            if (mistakesOnCurrentStroke >= hintAfterMistakes) hintVisible = true
        }
        return result
    }

    /** The summary of the just-completed quiz, or `null` if the quiz is not complete. */
    public fun quizSummary(): QuizSummary? =
        if (isQuizComplete) QuizSummary(totalMistakes, mistakesPerStroke.toList()) else null

    // ---- Render helpers (read from the draw phase) ------------------------

    /** Whether stroke [index] should be drawn fully, given the current mode. */
    internal fun isStrokeCompleted(index: Int): Boolean = when (mode) {
        StrokeOrderMode.Animating -> index in animationFrame.completedStrokeIndexes
        StrokeOrderMode.Quiz -> index in acceptedStrokes
        StrokeOrderMode.Idle -> index < idleRevealedCount
    }

    /** The stroke currently being revealed during animation, and its progress. */
    internal fun activeReveal(): StrokeReveal? {
        if (mode != StrokeOrderMode.Animating) return null
        val index = animationFrame.currentStrokeIndex ?: return null
        val progress = animationFrame.progressByStroke.getOrNull(index) ?: 0f
        return StrokeReveal(index, progress)
    }

    private fun resetStateForNewOrder() {
        animationElapsedMillis = 0L
        idleRevealedCount = 0
        acceptedStrokes = emptySet()
        currentStroke = 0
        mistakesOnCurrentStroke = 0
        totalMistakes = 0
        hintVisible = false
        isQuizComplete = false
        lastRecognitionStatus = null
        mistakesPerStroke = IntArray(_strokeOrder.strokeCount)
        mode = StrokeOrderMode.Idle
    }

    private fun exitQuizState() {
        acceptedStrokes = emptySet()
        currentStroke = 0
        mistakesOnCurrentStroke = 0
        totalMistakes = 0
        hintVisible = false
        isQuizComplete = false
        lastRecognitionStatus = null
    }

    internal companion object {
        internal val EMPTY_FRAME = StrokeAnimationFrame(
            currentStrokeIndex = null,
            progressByStroke = emptyList(),
            completedStrokeIndexes = emptyList(),
            isFinished = true
        )
    }
}

/** A stroke index and its reveal progress in `0f..1f`. */
internal data class StrokeReveal(val index: Int, val progress: Float)

/**
 * Remembers a [StrokeOrderController] for [strokeOrder], driven by a
 * composition-scoped frame clock. A new controller is created when
 * [strokeOrder] changes; to swap characters without losing the instance, assign
 * [StrokeOrderController.strokeOrder] instead.
 */
@Composable
public fun rememberStrokeOrderController(
    strokeOrder: StrokeOrder,
    animationConfig: StrokeAnimationConfig = StrokeAnimationConfig(),
    recognitionConfig: StrokeRecognitionConfig = StrokeRecognitionConfig(),
    hintAfterMistakes: Int = 2
): StrokeOrderController {
    val scope = rememberCoroutineScope()
    return remember(strokeOrder, scope) {
        StrokeOrderController(
            strokeOrder = strokeOrder,
            animationConfig = animationConfig,
            recognitionConfig = recognitionConfig,
            hintAfterMistakes = hintAfterMistakes,
            scope = scope
        )
    }
}

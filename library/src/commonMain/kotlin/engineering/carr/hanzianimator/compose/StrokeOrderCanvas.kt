package engineering.carr.hanzianimator.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import engineering.carr.hanzianimator.core.GlyphPoint
import engineering.carr.hanzianimator.core.StrokeRecognitionResult
import engineering.carr.hanzianimator.core.StrokeRecognitionStatus
import engineering.carr.hanzianimator.core.StrokeViewportTransform
import kotlinx.coroutines.delay

/**
 * The building-block canvas: renders [controller]'s current state (idle,
 * animating, or quiz) and, during a quiz, captures the user's traced strokes.
 *
 * Frame-varying state is read only in the draw phase, so an in-progress
 * animation repaints without recomposing this composable or its siblings.
 *
 * @param onStrokeResult invoked with the grading result after each traced stroke.
 * @param onQuizComplete invoked with the summary when the quiz is completed.
 */
@Composable
public fun StrokeOrderCanvas(
    controller: StrokeOrderController,
    modifier: Modifier = Modifier,
    style: StrokeOrderStyle = StrokeOrderDefaults.style(),
    onStrokeResult: (StrokeRecognitionResult) -> Unit = {},
    onQuizComplete: (QuizSummary) -> Unit = {}
) {
    val currentOnStrokeResult by rememberUpdatedState(onStrokeResult)
    val currentOnQuizComplete by rememberUpdatedState(onQuizComplete)
    val order = controller.strokeOrder
    val geometry = remember(order, style.medianStartExtension) {
        StrokeGlyphGeometry.from(order, style.medianStartExtension)
    }

    // Reusable scratch paths (active reveal, hint reveal) and the live user trace.
    val activeScratch = remember(controller, order) { Path() }
    val hintScratch = remember(controller, order) { Path() }
    val tracePath = remember(controller, order) { Path() }
    val tracePoints = remember(controller, order) { mutableListOf<GlyphPoint>() }
    var traceRevision by remember(controller, order) { mutableIntStateOf(0) }

    var failedTrace by remember(controller, order) { mutableStateOf<List<GlyphPoint>>(emptyList()) }
    var failureKey by remember(controller, order) { mutableIntStateOf(0) }
    val hintProgress = remember(controller, order) { Animatable(0f) }
    val completionPulse = remember(controller, order) { Animatable(0f) }

    LaunchedEffect(controller, order, controller.mode, controller.hintVisible, controller.currentStroke, style.hintRevealDurationMillis) {
        if (controller.mode == StrokeOrderMode.Quiz && controller.hintVisible && !controller.isQuizComplete) {
            hintProgress.snapTo(0f)
            hintProgress.animateTo(1f, tween(style.hintRevealDurationMillis, easing = FastOutSlowInEasing))
        } else {
            hintProgress.snapTo(0f)
        }
    }
    LaunchedEffect(controller, order, failureKey, style.failureFlashDurationMillis) {
        if (failureKey > 0) {
            delay(style.failureFlashDurationMillis.toLong())
            failedTrace = emptyList()
        }
    }
    LaunchedEffect(controller, order, controller.isQuizComplete, style.completionPulseDurationMillis) {
        if (controller.isQuizComplete) {
            completionPulse.snapTo(1f)
            completionPulse.animateTo(0f, tween(style.completionPulseDurationMillis, easing = FastOutSlowInEasing))
        } else {
            completionPulse.snapTo(0f)
        }
    }

    val quizActive = controller.mode == StrokeOrderMode.Quiz &&
        !controller.isQuizComplete &&
        geometry.canPractice

    val inputModifier = if (quizActive) {
        Modifier.pointerInput(controller, order, controller.currentStroke) {
            awaitEachGesture {
                try {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val viewport = StrokeViewportTransform(size.width.toFloat(), size.height.toFloat())
                    tracePoints.clear()
                    val start = viewport.canvasToGlyph(GlyphPoint(down.position.x, down.position.y), clamp = true)
                    tracePoints.add(start)
                    tracePath.reset()
                    tracePath.moveTo(start.x, start.y)
                    traceRevision++
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        val point = viewport.canvasToGlyph(GlyphPoint(change.position.x, change.position.y), clamp = true)
                        tracePoints.add(point)
                        tracePath.lineTo(point.x, point.y)
                        traceRevision++
                        change.consume()
                        if (!change.pressed) {
                            val points = tracePoints.toList()
                            val wasComplete = controller.isQuizComplete
                            val result = controller.submitStroke(points)
                            // Host callbacks may reset or replace the session. Preserve this result's summary.
                            val completedSummary = if (!wasComplete && controller.isQuizComplete) controller.quizSummary() else null
                            currentOnStrokeResult(result)
                            if (!result.accepted && result.status != StrokeRecognitionStatus.EmptyInput) {
                                failedTrace = points
                                failureKey++
                            }
                            completedSummary?.let(currentOnQuizComplete)
                            break
                        }
                    }
                } finally {
                    // Runs on normal completion and when the gesture is cancelled
                    // mid-trace, so no partial trace lingers on the canvas.
                    if (tracePoints.isNotEmpty()) {
                        tracePoints.clear()
                        tracePath.reset()
                        traceRevision++
                    }
                }
            }
        }
    } else {
        Modifier
    }

    Canvas(modifier.then(inputModifier)) {
        val viewport = StrokeViewportTransform(size.width, size.height)
        if (viewport.scale <= 0f) return@Canvas
        if (style.showGrid) drawGlyphGrid(viewport, style.gridColor)

        // Draw-phase snapshot reads: repaint without recomposition.
        val pulse = completionPulse.value
        val hint = hintProgress.value
        val trigger = traceRevision // registers the trace as a draw dependency
        val activeReveal = controller.activeReveal()
        val outlineVisible = controller.outlineVisible
        val mode = controller.mode
        val hintVisible = controller.hintVisible
        val quizComplete = controller.isQuizComplete
        val currentStroke = controller.currentStroke

        withGlyphTransform(viewport) {
            if (outlineVisible) {
                geometry.strokes.forEach { entry ->
                    entry.outlinePath?.let { drawPath(it, style.outlineColor, style = Fill) }
                }
            }
            geometry.strokes.forEach { entry ->
                if (controller.isStrokeCompleted(entry.index)) {
                    entry.outlinePath?.let {
                        drawPath(
                            it,
                            lerp(
                                strokeFinalColor(
                                    entry.isRadical, style.completedStrokeColor, style.radicalStrokeColor,
                                    entry.componentIndex, style.componentColors, order.components != null
                                ),
                                style.traceColor,
                                pulse
                            ),
                            style = Fill
                        )
                    }
                }
            }
            activeReveal?.let { reveal ->
                geometry.strokes.getOrNull(reveal.index)?.let { entry ->
                    drawStrokeReveal(
                        entry = entry,
                        progress = reveal.progress,
                        color = strokeFinalColor(
                            entry.isRadical, style.activeStrokeColor, style.radicalStrokeColor,
                            entry.componentIndex, style.componentColors, order.components != null
                        ),
                        revealStrokeWidth = style.revealStrokeWidth,
                        scratch = activeScratch
                    )
                }
            }
            if (mode == StrokeOrderMode.Quiz && hintVisible && !quizComplete) {
                geometry.strokes.getOrNull(currentStroke)?.let { entry ->
                    drawStrokeReveal(
                        entry = entry,
                        progress = hint,
                        color = style.hintColor,
                        revealStrokeWidth = style.revealStrokeWidth,
                        scratch = hintScratch
                    )
                }
            }
            if (trigger >= 0) {
                drawPath(
                    tracePath,
                    style.traceColor,
                    style = Stroke(width = style.traceStrokeWidth, cap = StrokeCap.Round)
                )
            }
            if (failedTrace.isNotEmpty()) {
                drawGlyphPolyline(failedTrace, style.errorColor, style.traceStrokeWidth)
            }
        }
    }
}

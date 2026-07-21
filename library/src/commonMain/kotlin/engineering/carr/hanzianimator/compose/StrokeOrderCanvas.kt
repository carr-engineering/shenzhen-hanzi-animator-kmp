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
    val order = controller.strokeOrder
    val geometry = remember(order, style.medianStartExtension) {
        StrokeGlyphGeometry.from(order, style.medianStartExtension)
    }

    // Reusable scratch paths (active reveal, hint reveal) and the live user trace.
    val activeScratch = remember(order) { Path() }
    val hintScratch = remember(order) { Path() }
    val tracePath = remember(order) { Path() }
    val tracePoints = remember(order) { mutableListOf<GlyphPoint>() }
    var traceRevision by remember(order) { mutableIntStateOf(0) }

    var failedTrace by remember(order) { mutableStateOf<List<GlyphPoint>>(emptyList()) }
    var failureKey by remember(order) { mutableIntStateOf(0) }
    val hintProgress = remember(order) { Animatable(0f) }
    val completionPulse = remember(order) { Animatable(0f) }

    LaunchedEffect(order, controller.hintVisible, controller.currentStroke) {
        if (controller.mode == StrokeOrderMode.Quiz && controller.hintVisible && !controller.isQuizComplete) {
            hintProgress.snapTo(0f)
            hintProgress.animateTo(1f, tween(style.hintRevealDurationMillis, easing = FastOutSlowInEasing))
        } else {
            hintProgress.snapTo(0f)
        }
    }
    LaunchedEffect(order, failureKey) {
        if (failureKey > 0) {
            delay(style.failureFlashDurationMillis.toLong())
            failedTrace = emptyList()
        }
    }
    LaunchedEffect(order, controller.isQuizComplete) {
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
        Modifier.pointerInput(order, controller.currentStroke) {
            awaitEachGesture {
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
                        onStrokeResult(result)
                        if (!result.accepted) {
                            failedTrace = points
                            failureKey++
                        }
                        if (!wasComplete && controller.isQuizComplete) {
                            controller.quizSummary()?.let(onQuizComplete)
                        }
                        tracePoints.clear()
                        tracePath.reset()
                        traceRevision++
                        break
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
        val completedColor = lerp(style.completedStrokeColor, style.traceColor, pulse)

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
                            strokeFinalColor(entry.isRadical, completedColor, style.radicalStrokeColor),
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
                        color = strokeFinalColor(entry.isRadical, style.activeStrokeColor, style.radicalStrokeColor),
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

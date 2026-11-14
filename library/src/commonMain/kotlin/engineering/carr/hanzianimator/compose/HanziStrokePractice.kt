package engineering.carr.hanzianimator.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import engineering.carr.hanzianimator.core.StrokeAnimationConfig
import engineering.carr.hanzianimator.core.StrokeRecognitionConfig
import engineering.carr.hanzianimator.core.StrokeRecognitionResult
import engineering.carr.hanzianimator.data.StrokeOrderProvider

/**
 * Drop-in guided practice for a [character]: loads its data from [provider] (the
 * caller's chosen data source), then shows the tracing canvas above a wrapping
 * control bar, with quizzing wired up and sensible defaults.
 *
 * Report progress via [onStrokeResult] / [onQuizComplete]; feedback text is left
 * to the caller (map [StrokeRecognitionResult.status] to your own localized copy).
 */
@Composable
public fun HanziStrokePractice(
    character: String,
    modifier: Modifier = Modifier,
    provider: StrokeOrderProvider,
    style: StrokeOrderStyle = StrokeOrderDefaults.style(),
    animationConfig: StrokeAnimationConfig = StrokeAnimationConfig(),
    recognitionConfig: StrokeRecognitionConfig = StrokeRecognitionConfig(),
    hintAfterMistakes: Int = 2,
    layout: StrokeOrderLayoutConfig = StrokeOrderLayoutConfig(),
    controls: @Composable (StrokeOrderController) -> Unit = { StrokeOrderControls(it, layout = layout, style = style) },
    onStrokeResult: (StrokeRecognitionResult) -> Unit = {},
    onQuizComplete: (QuizSummary) -> Unit = {},
    loading: @Composable () -> Unit = {},
    missing: @Composable () -> Unit = {},
    error: @Composable (Throwable, retry: () -> Unit) -> Unit = { _, _ -> }
) {
    var retryKey by remember(character, provider) { mutableIntStateOf(0) }
    val state by rememberStrokeOrderLoadState(character, provider, retryKey)
    when (val current = state) {
        StrokeOrderLoadState.Loading -> loading()
        StrokeOrderLoadState.Missing -> missing()
        is StrokeOrderLoadState.Error -> error(current.cause) { retryKey++ }
        is StrokeOrderLoadState.Loaded -> {
            val controller = rememberStrokeOrderController(
                strokeOrder = current.order,
                animationConfig = animationConfig,
                recognitionConfig = recognitionConfig,
                hintAfterMistakes = hintAfterMistakes
            )
            Column(
                modifier = modifier,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(layout.canvasControlsGap)
            ) {
                StrokeOrderCanvas(
                    controller = controller,
                    modifier = Modifier.widthIn(max = layout.maxCanvasSize).fillMaxWidth().aspectRatio(1f),
                    style = style,
                    onStrokeResult = onStrokeResult,
                    onQuizComplete = onQuizComplete
                )
                controls(controller)
            }
        }
    }
}

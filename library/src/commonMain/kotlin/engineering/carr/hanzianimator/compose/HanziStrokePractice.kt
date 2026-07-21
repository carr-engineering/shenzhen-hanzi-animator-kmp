package engineering.carr.hanzianimator.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import engineering.carr.hanzianimator.core.StrokeAnimationConfig
import engineering.carr.hanzianimator.core.StrokeRecognitionConfig
import engineering.carr.hanzianimator.core.StrokeRecognitionResult
import engineering.carr.hanzianimator.data.BundledStrokeOrderProvider
import engineering.carr.hanzianimator.data.StrokeOrderProvider

/**
 * Drop-in guided practice for a [character]: loads its data from [provider] (the
 * bundled dataset by default), then shows the tracing canvas above a Material 3
 * control bar, with quizzing wired up and sensible defaults.
 *
 * Report progress via [onStrokeResult] / [onQuizComplete]; feedback text is left
 * to the caller (map [StrokeRecognitionResult.status] to your own localized copy).
 */
@Composable
public fun HanziStrokePractice(
    character: String,
    modifier: Modifier = Modifier,
    provider: StrokeOrderProvider = BundledStrokeOrderProvider.Default,
    style: StrokeOrderStyle = StrokeOrderDefaults.style(),
    animationConfig: StrokeAnimationConfig = StrokeAnimationConfig(),
    recognitionConfig: StrokeRecognitionConfig = StrokeRecognitionConfig(),
    hintAfterMistakes: Int = 2,
    onStrokeResult: (StrokeRecognitionResult) -> Unit = {},
    onQuizComplete: (QuizSummary) -> Unit = {},
    loading: @Composable () -> Unit = {},
    missing: @Composable () -> Unit = {}
) {
    val state by produceState<StrokeOrderLoadState>(StrokeOrderLoadState.Loading, character, provider) {
        value = provider.strokeOrder(character)
            ?.let { StrokeOrderLoadState.Loaded(it) }
            ?: StrokeOrderLoadState.Missing
    }
    when (val current = state) {
        StrokeOrderLoadState.Loading -> loading()
        StrokeOrderLoadState.Missing -> missing()
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
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StrokeOrderCanvas(
                    controller = controller,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    style = style,
                    onStrokeResult = onStrokeResult,
                    onQuizComplete = onQuizComplete
                )
                StrokeOrderControls(controller = controller)
            }
        }
    }
}

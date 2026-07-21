package engineering.carr.hanzianimator.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import engineering.carr.hanzianimator.core.StrokeAnimationConfig
import engineering.carr.hanzianimator.data.BundledStrokeOrderProvider
import engineering.carr.hanzianimator.data.StrokeOrderProvider

/**
 * Drop-in stroke-order animation for a [character]: loads the character's data
 * from [provider] (the bundled dataset by default) and autoplays a looping
 * animation. The zero-wiring entry point — supply a character and go.
 *
 * @param loading shown while the character's data is being loaded.
 * @param missing shown when the character is not available from [provider].
 */
@Composable
public fun HanziStrokeAnimation(
    character: String,
    modifier: Modifier = Modifier,
    provider: StrokeOrderProvider = BundledStrokeOrderProvider.Default,
    animationConfig: StrokeAnimationConfig = StrokeAnimationConfig(loop = true),
    style: StrokeOrderStyle = StrokeOrderDefaults.style(),
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
                animationConfig = animationConfig
            )
            LaunchedEffect(controller) { controller.play() }
            StrokeOrderCanvas(controller = controller, modifier = modifier, style = style)
        }
    }
}

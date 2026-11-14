package engineering.carr.hanzianimator.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import engineering.carr.hanzianimator.core.StrokeAnimationConfig
import engineering.carr.hanzianimator.data.StrokeOrderProvider

/**
 * Drop-in stroke-order animation for a [character]: loads the character's data
 * from the caller's [provider] and autoplays a looping animation.
 *
 * @param loading shown while the character's data is being loaded.
 * @param missing shown when the character is not available from [provider].
 */
@Composable
public fun HanziStrokeAnimation(
    character: String,
    modifier: Modifier = Modifier,
    provider: StrokeOrderProvider,
    animationConfig: StrokeAnimationConfig = StrokeAnimationConfig(loop = true),
    style: StrokeOrderStyle = StrokeOrderDefaults.style(),
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
                animationConfig = animationConfig
            )
            LaunchedEffect(controller) { controller.play() }
            StrokeOrderCanvas(controller = controller, modifier = modifier, style = style)
        }
    }
}

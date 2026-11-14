package engineering.carr.hanzianimator.compose
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
@Immutable
public data class StrokeOrderLayoutConfig(
    val maxCanvasSize: Dp = 360.dp,
    val canvasControlsGap: Dp = 8.dp,
    val minimumControlSize: Dp = 48.dp,
    val controlHorizontalGap: Dp = 2.dp,
    val controlRowGap: Dp = 4.dp
) {
    init {
        require(maxCanvasSize.value.isFinite() && maxCanvasSize > 0.dp)
        require(minimumControlSize.value.isFinite() && minimumControlSize > 0.dp)
        require(listOf(canvasControlsGap, controlHorizontalGap, controlRowGap).all { it.value.isFinite() && it >= 0.dp })
    }
}
@Immutable
public data class StrokeOrderControlsLabels(
    val play: String = "Play stroke order", val pause: String = "Pause",
    val previous: String = "Previous stroke", val next: String = "Next stroke",
    val reveal: String = "Show full character", val reset: String = "Reset",
    val quiz: String = "Start quiz", val hint: String = "Show next stroke hint",
    val restart: String = "Restart quiz", val exit: String = "Exit quiz",
    val hideOutline: String = "Hide outline", val showOutline: String = "Show outline"
)

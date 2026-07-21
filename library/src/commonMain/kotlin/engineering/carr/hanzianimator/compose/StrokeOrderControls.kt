package engineering.carr.hanzianimator.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A ready-made Material 3 control bar for a [StrokeOrderController]: play/pause,
 * step, reveal-all, reset, quiz toggle (with hint), and an outline toggle. The
 * buttons shown adapt to the controller's mode.
 *
 * This is a convenience; build your own bar from the controller's methods for
 * full control over layout, iconography, and localized accessibility labels.
 * (The `contentDescription`s here are English defaults.)
 */
@Composable
public fun StrokeOrderControls(
    controller: StrokeOrderController,
    modifier: Modifier = Modifier
) {
    val hasStrokes = controller.strokeOrder.strokeCount > 0
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (controller.mode == StrokeOrderMode.Quiz) {
            IconButton(
                enabled = !controller.isQuizComplete,
                onClick = { controller.showHint() }
            ) {
                Icon(HanziIcons.Lightbulb, contentDescription = "Show next stroke hint")
            }
            IconButton(onClick = { controller.startQuiz() }) {
                Icon(HanziIcons.Replay, contentDescription = "Restart quiz")
            }
            IconButton(onClick = { controller.stopQuiz() }) {
                Icon(HanziIcons.Close, contentDescription = "Exit quiz")
            }
        } else {
            IconButton(
                enabled = hasStrokes,
                onClick = { if (controller.isAnimating) controller.pause() else controller.play() }
            ) {
                if (controller.isAnimating) {
                    Icon(HanziIcons.Pause, contentDescription = "Pause")
                } else {
                    Icon(HanziIcons.Play, contentDescription = "Play stroke order")
                }
            }
            IconButton(enabled = hasStrokes, onClick = { controller.previousStroke() }) {
                Icon(HanziIcons.SkipPrevious, contentDescription = "Previous stroke")
            }
            IconButton(enabled = hasStrokes, onClick = { controller.nextStroke() }) {
                Icon(HanziIcons.SkipNext, contentDescription = "Next stroke")
            }
            IconButton(enabled = hasStrokes, onClick = { controller.showFullCharacter() }) {
                Icon(HanziIcons.Done, contentDescription = "Show full character")
            }
            IconButton(enabled = hasStrokes, onClick = { controller.reset() }) {
                Icon(HanziIcons.Replay, contentDescription = "Reset")
            }
            IconButton(enabled = hasStrokes, onClick = { controller.startQuiz() }) {
                Icon(HanziIcons.Edit, contentDescription = "Start quiz")
            }
        }
        IconButton(onClick = { controller.outlineVisible = !controller.outlineVisible }) {
            if (controller.outlineVisible) {
                Icon(HanziIcons.VisibilityOff, contentDescription = "Hide outline")
            } else {
                Icon(HanziIcons.Visibility, contentDescription = "Show outline")
            }
        }
    }
}

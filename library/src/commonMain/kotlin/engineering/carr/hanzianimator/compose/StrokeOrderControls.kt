package engineering.carr.hanzianimator.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role

/**
 * A Foundation control bar for a [StrokeOrderController]: play/pause,
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
    modifier: Modifier = Modifier,
    layout: StrokeOrderLayoutConfig = StrokeOrderLayoutConfig(),
    labels: StrokeOrderControlsLabels = StrokeOrderControlsLabels(),
    style: StrokeOrderStyle = StrokeOrderDefaults.style()
) {
    val hasStrokes = controller.strokeOrder.strokeCount > 0
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(layout.controlHorizontalGap),
        verticalArrangement = Arrangement.spacedBy(layout.controlRowGap)
    ) {
        if (controller.mode == StrokeOrderMode.Quiz) {
            ControlButton(layout.minimumControlSize,
                enabled = !controller.isQuizComplete,
                onClick = { controller.showHint() }
            ) {
                ControlIcon(style.completedStrokeColor, HanziIcons.Lightbulb, contentDescription = labels.hint)
            }
            ControlButton(layout.minimumControlSize, onClick = { controller.startQuiz() }) {
                ControlIcon(style.completedStrokeColor, HanziIcons.Replay, contentDescription = labels.restart)
            }
            ControlButton(layout.minimumControlSize, onClick = { controller.stopQuiz() }) {
                ControlIcon(style.completedStrokeColor, HanziIcons.Close, contentDescription = labels.exit)
            }
        } else {
            ControlButton(layout.minimumControlSize,
                enabled = hasStrokes,
                onClick = { if (controller.isAnimating) controller.pause() else controller.play() }
            ) {
                if (controller.isAnimating) {
                    ControlIcon(style.completedStrokeColor, HanziIcons.Pause, contentDescription = labels.pause)
                } else {
                    ControlIcon(style.completedStrokeColor, HanziIcons.Play, contentDescription = labels.play)
                }
            }
            ControlButton(layout.minimumControlSize, enabled = hasStrokes, onClick = { controller.previousStroke() }) {
                ControlIcon(style.completedStrokeColor, HanziIcons.SkipPrevious, contentDescription = labels.previous)
            }
            ControlButton(layout.minimumControlSize, enabled = hasStrokes, onClick = { controller.nextStroke() }) {
                ControlIcon(style.completedStrokeColor, HanziIcons.SkipNext, contentDescription = labels.next)
            }
            ControlButton(layout.minimumControlSize, enabled = hasStrokes, onClick = { controller.showFullCharacter() }) {
                ControlIcon(style.completedStrokeColor, HanziIcons.Done, contentDescription = labels.reveal)
            }
            ControlButton(layout.minimumControlSize, enabled = hasStrokes, onClick = { controller.reset() }) {
                ControlIcon(style.completedStrokeColor, HanziIcons.Replay, contentDescription = labels.reset)
            }
            ControlButton(layout.minimumControlSize, enabled = controller.strokeOrder.supportsPractice, onClick = { controller.startQuiz() }) {
                ControlIcon(style.completedStrokeColor, HanziIcons.Edit, contentDescription = labels.quiz)
            }
        }
        ControlButton(layout.minimumControlSize, onClick = { controller.outlineVisible = !controller.outlineVisible }) {
            if (controller.outlineVisible) {
                ControlIcon(style.completedStrokeColor, HanziIcons.VisibilityOff, contentDescription = labels.hideOutline)
            } else {
                ControlIcon(style.completedStrokeColor, HanziIcons.Visibility, contentDescription = labels.showOutline)
            }
        }
    }
}

@Composable
private fun ControlButton(size: androidx.compose.ui.unit.Dp, enabled: Boolean = true, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.size(size).alpha(if (enabled) 1f else 0.38f).clickable(enabled = enabled, role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) { content() }
}
@Composable
private fun ControlIcon(color: androidx.compose.ui.graphics.Color, image: ImageVector, contentDescription: String) {
    Image(rememberVectorPainter(image), contentDescription, Modifier.size(24.dp), colorFilter = ColorFilter.tint(color))
}

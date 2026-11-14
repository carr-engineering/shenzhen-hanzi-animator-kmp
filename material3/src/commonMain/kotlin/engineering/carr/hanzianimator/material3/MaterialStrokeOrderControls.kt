package engineering.carr.hanzianimator.material3

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import engineering.carr.hanzianimator.compose.StrokeOrderController
import engineering.carr.hanzianimator.compose.StrokeOrderControls
import engineering.carr.hanzianimator.compose.StrokeOrderControlsLabels
import engineering.carr.hanzianimator.compose.StrokeOrderLayoutConfig

/** Wrapping accessible controls using the application's current Material palette. */
@Composable
public fun MaterialStrokeOrderControls(
    controller: StrokeOrderController,
    modifier: Modifier = Modifier,
    layout: StrokeOrderLayoutConfig = StrokeOrderLayoutConfig(),
    labels: StrokeOrderControlsLabels = StrokeOrderControlsLabels()
): Unit = StrokeOrderControls(controller, modifier, layout, labels, MaterialStrokeOrderDefaults.style())

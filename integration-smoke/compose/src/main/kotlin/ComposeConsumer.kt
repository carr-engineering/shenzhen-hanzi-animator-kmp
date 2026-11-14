import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import engineering.carr.hanzianimator.compose.*
import engineering.carr.hanzianimator.core.StrokeOrder
import engineering.carr.hanzianimator.data.StrokeOrderProvider

/** Compile the public API as an external Foundation-only application would. */
@Composable
fun PublishedPractice(provider: StrokeOrderProvider) {
    HanziStrokePractice(
        character = "永", provider = provider, modifier = Modifier,
        layout = StrokeOrderLayoutConfig(),
        controls = { controller -> StrokeOrderControls(controller, labels = StrokeOrderControlsLabels(quiz = "练习")) },
        onQuizComplete = { println(it.totalMistakes) },
    )
    HanziStrokeAnimation(character = "永", provider = provider)
}

@Composable
fun PublishedGlyph(order: StrokeOrder) {
    val controller = rememberStrokeOrderController(order)
    HanziGlyph(order)
    StrokeOrderCanvas(controller)
    StrokeOrderControls(controller)
}

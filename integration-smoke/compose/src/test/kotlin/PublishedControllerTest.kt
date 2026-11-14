import engineering.carr.hanzianimator.compose.StrokeOrderController
import engineering.carr.hanzianimator.core.*
import kotlinx.coroutines.*
import kotlin.test.*

class PublishedControllerTest {
    @Test fun staticAndQuizActionsNeedNoDatasetOrMaterial() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val stroke = Stroke(0, StrokeOutline.Builder().moveTo(100f, 100f).lineTo(800f, 800f).build(),
                listOf(GlyphPoint(100f, 100f), GlyphPoint(800f, 800f)), false)
            val controller = StrokeOrderController(StrokeOrder("一", listOf(stroke)), scope = scope)
            controller.showFullCharacter()
            assertEquals(StrokeSessionMode.Idle, controller.mode)
            assertTrue(controller.startQuiz())
            assertTrue(controller.submitStroke(stroke.median).accepted)
            assertEquals(0, assertNotNull(controller.quizSummary()).totalMistakes)
            controller.reset()
            assertFalse(controller.isQuizComplete)
        } finally { scope.cancel() }
    }
}

@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package engineering.carr.hanzianimator.compose

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import engineering.carr.hanzianimator.core.GlyphPoint
import engineering.carr.hanzianimator.core.Stroke
import engineering.carr.hanzianimator.core.StrokeOrder
import engineering.carr.hanzianimator.core.StrokeOutline
import engineering.carr.hanzianimator.data.StrokeOrderProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ComposeLifecycleTest {
    private fun order(character: String) = StrokeOrder(character, listOf(Stroke(
        0, StrokeOutline.Builder().moveTo(100f, 90f).lineTo(700f, 90f)
            .lineTo(700f, 110f).lineTo(100f, 110f).close().build(),
        listOf(GlyphPoint(100f, 100f), GlyphPoint(700f, 100f)), false
    )))

    @Test
    fun completedGestureKeepsItsSummaryWhenResultCallbackRestartsQuiz() = runTest {
        val controller = StrokeOrderController(order("A"), scope = backgroundScope).apply { startQuiz() }
        val summaries = mutableListOf<QuizSummary>()
        val scene = ImageComposeScene(1024, 1024, coroutineContext = backgroundScope.coroutineContext)
        try {
            scene.setContent {
                StrokeOrderCanvas(controller, Modifier.fillMaxSize(),
                    onStrokeResult = { controller.startQuiz() }, onQuizComplete = { summaries.add(it) })
            }
            scene.render(); runCurrent()
            scene.sendPointerEvent(PointerEventType.Press, Offset(100f, 100f))
            scene.sendPointerEvent(PointerEventType.Move, Offset(400f, 100f))
            scene.sendPointerEvent(PointerEventType.Release, Offset(700f, 100f))
            assertEquals(1, summaries.size)
            assertEquals(0, summaries.single().totalMistakes)
            assertEquals(0, controller.currentStroke)
        } finally { scene.close() }
    }

    @Test
    fun characterChangeImmediatelyResetsLoadingAndIgnoresLateResult() = runTest {
        val old = CompletableDeferred<StrokeOrder?>()
        val next = CompletableDeferred<StrokeOrder?>()
        val character = mutableStateOf("A")
        val provider = StrokeOrderProvider { key ->
            withContext(NonCancellable) { if (key == "A") old.await() else next.await() }
        }
        var displayed: StrokeOrderLoadState = StrokeOrderLoadState.Missing
        val scene = ImageComposeScene(256, 256, coroutineContext = backgroundScope.coroutineContext)
        try {
            scene.setContent { val state by rememberStrokeOrderLoadState(character.value, provider); displayed = state }
            scene.render(); runCurrent()
            character.value = "B"
            scene.render(); runCurrent()
            assertIs<StrokeOrderLoadState.Loading>(displayed)
            old.complete(order("A")); runCurrent(); scene.render()
            assertIs<StrokeOrderLoadState.Loading>(displayed)
            next.complete(order("B")); runCurrent(); scene.render()
            assertEquals("B", assertIs<StrokeOrderLoadState.Loaded>(displayed).order.character)
        } finally { old.complete(null); next.complete(null); scene.close() }
    }

    @Test
    fun failuresAreVisibleAndRetryLoadsAgain() = runTest {
        var calls = 0
        val retry = mutableStateOf(0)
        val provider = StrokeOrderProvider { if (++calls == 1) error("network unavailable") else order("A") }
        var displayed: StrokeOrderLoadState = StrokeOrderLoadState.Loading
        val scene = ImageComposeScene(256, 256, coroutineContext = backgroundScope.coroutineContext)
        try {
            scene.setContent { val state by rememberStrokeOrderLoadState("A", provider, retry.value); displayed = state }
            scene.render(); runCurrent(); scene.render()
            assertEquals("network unavailable", assertIs<StrokeOrderLoadState.Error>(displayed).cause.message)
            retry.value++
            repeat(3) { scene.render(); runCurrent() }
            assertIs<StrokeOrderLoadState.Loaded>(displayed)
            assertEquals(2, calls)
        } finally { scene.close() }
    }

    @Test
    fun gestureUsesUpdatedCallbacksAndControllerReplacementCancelsTrace() = runTest {
        val glyph = order("A")
        val first = StrokeOrderController(glyph, scope = backgroundScope).apply { startQuiz() }
        val second = StrokeOrderController(glyph, scope = backgroundScope).apply { startQuiz() }
        val controller = mutableStateOf(first)
        val callbackVersion = mutableStateOf(1)
        val results = mutableListOf<Int>()
        val scene = ImageComposeScene(1024, 1024, coroutineContext = backgroundScope.coroutineContext)
        try {
            scene.setContent {
                val version = callbackVersion.value
                StrokeOrderCanvas(controller.value, Modifier.fillMaxSize(), onStrokeResult = { results.add(version) })
            }
            scene.render(); runCurrent()
            scene.sendPointerEvent(PointerEventType.Press, Offset(100f, 100f))
            callbackVersion.value = 2
            scene.render(); runCurrent()
            scene.sendPointerEvent(PointerEventType.Move, Offset(400f, 100f))
            scene.sendPointerEvent(PointerEventType.Release, Offset(700f, 100f))
            runCurrent(); scene.render()
            assertEquals(listOf(2), results)
            first.startQuiz(); scene.render(); runCurrent()
            scene.sendPointerEvent(PointerEventType.Press, Offset(100f, 100f))
            controller.value = second
            scene.render(); runCurrent()
            scene.sendPointerEvent(PointerEventType.Release, Offset(700f, 100f))
            assertEquals(listOf(2), results)
            assertEquals(0, first.currentStroke)
            assertEquals(0, second.currentStroke)
            scene.sendPointerEvent(PointerEventType.Press, Offset(100f, 100f))
            scene.sendPointerEvent(PointerEventType.Move, Offset(400f, 100f))
            scene.sendPointerEvent(PointerEventType.Release, Offset(700f, 100f))
            assertEquals(listOf(2, 2), results)
        } finally { scene.close() }
    }
}

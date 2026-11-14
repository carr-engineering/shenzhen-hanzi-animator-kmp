package engineering.carr.hanzianimator.sample

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController
import engineering.carr.hanzianimator.data.BundledStrokeOrderProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** The iOS host embeds the generated strokes directory in its application bundle. */
fun MainViewController(): UIViewController = ComposeUIViewController { SampleApp() }

/** Installed-app integration check; its short-lived scope is owned and closed by this sample. */
fun runBundledDataSmokeTest(onResult: (String) -> Unit) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    scope.launch {
        try {
            val provider = BundledStrokeOrderProvider(scope)
            check(provider.availableCharacters().size == 9574)
            val eternal = checkNotNull(provider.strokeOrder("永"))
            val forest = checkNotNull(provider.strokeOrder("森"))
            check(eternal.strokeCount == 5 && eternal.supportsPractice)
            check(forest.components == listOf("木", "木", "木"))
            check(forest.strokes.all { it.componentIndex != null })
            onResult("PASS: 9574 characters; 永 practice; 森 repeated component membership; native Brotli bundle")
        } catch (failure: Exception) {
            onResult("FAIL: ${failure.message}")
        } finally {
            scope.cancel()
        }
    }
}

package engineering.carr.hanzianimator.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import engineering.carr.hanzianimator.core.StrokeOrder
import engineering.carr.hanzianimator.data.BundledStrokeOrderProvider
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ComponentColorRenderTest {
    private fun order(character: String): StrokeOrder = runBlocking {
        requireNotNull(BundledStrokeOrderProvider(this).strokeOrder(character))
    }

    @Test
    fun rendersAllSixColorsOnLightAndDarkSurfaces() {
        val order = order("聽")
        for (dark in listOf(false, true)) {
            val palette = if (dark) StrokeOrderDefaults.DarkComponentColors else StrokeOrderDefaults.LightComponentColors
            val counts = render(dark, "listen-${if (dark) "dark" else "light"}") {
                val style = StrokeOrderDefaults.style(dark = dark, showGrid = false)
                assertEquals(palette, style.componentColors)
                HanziGlyph(order, Modifier.fillMaxSize(), style = style)
            }
            palette.forEach { color ->
                assertTrue((counts[color.toArgb()] ?: 0) > 100, "missing visible component color $color")
            }
        }
    }

    @Test
    fun clientPaletteOverridesDefaultsAcrossThemesAndRenderers() = runBlocking {
        val custom = listOf(
            Color(0xFF283D32), Color(0xFF3F6D50), Color(0xFF718861),
            Color(0xFF9E7338), Color(0xFF625C3D), Color(0xFFAE8B57)
        )
        val ink = Color(0xFF221F1A)
        for (dark in listOf(false, true)) {
            for (character in listOf("聽", "赢")) {
                val order = order(character)
                val controller = StrokeOrderController(order, scope = this).apply { showFullCharacter() }
                for (canvas in listOf(false, true)) {
                    val counts = render(dark) {
                        val style = StrokeOrderDefaults.style(
                            showGrid = false, componentColors = custom, completedStrokeColor = ink
                        )
                        assertEquals(custom, style.componentColors)
                        if (canvas) StrokeOrderCanvas(controller, Modifier.fillMaxSize(), style)
                        else HanziGlyph(order, Modifier.fillMaxSize(), style = style)
                    }
                    val expected = custom.take(order.componentCount) +
                        if (order.strokes.any { it.componentIndex == null }) listOf(ink) else emptyList()
                    expected.forEach {
                        assertTrue((counts[it.toArgb()] ?: 0) > 100, "client color not honored: $it")
                    }
                }
            }
        }
    }

    @Test
    fun largestPartiallyMappedCharacterShowsSixColorsPlusNeutralInk() {
        val order = order("斷")
        assertEquals(6, order.componentCount)
        assertEquals(1, order.strokes.count { it.componentIndex == null })
        for (dark in listOf(false, true)) {
            val palette = if (dark) StrokeOrderDefaults.DarkComponentColors else StrokeOrderDefaults.LightComponentColors
            val ink = if (dark) Color(0xFFEAE4D8) else Color(0xFF221F1A)
            val counts = render(dark, "sever-${if (dark) "dark" else "light"}") {
                HanziGlyph(order, Modifier.fillMaxSize(), style = StrokeOrderDefaults.style(
                    dark = dark, showGrid = false, completedStrokeColor = ink
                ))
            }
            (palette + ink).forEach {
                assertTrue((counts[it.toArgb()] ?: 0) > 100, "missing component or neutral color $it")
            }
        }
    }

    @Test
    fun repeatedPartsHaveThreeVisibleColorsAndUnmappedStrokesKeepInk() {
        val forest = render(false, "forest-light") {
            HanziGlyph(order("森"), Modifier.fillMaxSize(), style = StrokeOrderDefaults.style(showGrid = false))
        }
        StrokeOrderDefaults.LightComponentColors.take(3).forEach {
            assertTrue((forest[it.toArgb()] ?: 0) > 100)
        }
        val ink = Color(0xFF221F1A)
        val win = render(false, "win-light") {
            HanziGlyph(order("赢"), Modifier.fillMaxSize(), style = StrokeOrderDefaults.style(
                showGrid = false, completedStrokeColor = ink
            ))
        }
        (StrokeOrderDefaults.LightComponentColors.take(3) + ink).forEach {
            assertTrue((win[it.toArgb()] ?: 0) > 100, "missing mapped color or unmapped ink $it")
        }
    }

    @Test
    fun animationAndAcceptedPracticeStrokesUseTheSameComponentColors() = runBlocking {
        val order = order("聽")
        val animation = StrokeOrderController(order, scope = this).apply { showFullCharacter() }
        val practice = StrokeOrderController(order, scope = this).apply {
            startQuiz()
            // Leave the final stroke unsubmitted to avoid the transient completion pulse.
            order.strokes.dropLast(1).forEach { assertTrue(submitStroke(it.median).accepted) }
        }
        for (controller in listOf(animation, practice)) {
            val counts = render(false) {
                StrokeOrderCanvas(controller, Modifier.fillMaxSize(), style = StrokeOrderDefaults.style(showGrid = false))
            }
            StrokeOrderDefaults.LightComponentColors.forEach {
                assertTrue((counts[it.toArgb()] ?: 0) > 100, "canvas missing component color $it")
            }
        }
    }

    /** Optionally exports the actual renderer's pixels for visual review. */
    private fun render(
        dark: Boolean,
        name: String? = null,
        content: @Composable () -> Unit
    ): Map<Int, Int> {
        val background = if (dark) Color(0xFF1E1A14) else Color(0xFFFFFDF8)
        val scheme = if (dark) darkColorScheme(surface = background) else lightColorScheme(surface = background)
        val scene = ImageComposeScene(width = 512, height = 512)
        return try {
            scene.setContent {
                MaterialTheme(colorScheme = scheme) {
                    Box(Modifier.fillMaxSize().background(background)) { content() }
                }
            }
            val image = scene.render()
            System.getenv("HANZI_COMPONENT_PREVIEW_DIR")?.let { directory ->
                if (name != null) {
                    val file = File(directory, "$name.png")
                    file.parentFile.mkdirs()
                    file.writeBytes(requireNotNull(image.encodeToData(EncodedImageFormat.PNG)).bytes)
                }
            }
            val pixels = image.toComposeImageBitmap().toPixelMap()
            val counts = mutableMapOf<Int, Int>()
            for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                val color = pixels[x, y].toArgb()
                counts[color] = (counts[color] ?: 0) + 1
            }
            counts
        } finally {
            scene.close()
        }
    }
}

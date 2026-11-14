package engineering.carr.hanzianimator.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import engineering.carr.hanzianimator.data.BundledStrokeOrderProvider
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

class HanziGlyphRenderTest {
    @Test
    fun rendersZhongAsCenteredInk() {
        val order = runBlocking { BundledStrokeOrderProvider(this).strokeOrder("中") }
        requireNotNull(order) { "中 should be in the bundled dataset" }

        val size = 256
        val scene = ImageComposeScene(width = size, height = size)
        val pixels = try {
            scene.setContent {
                MaterialTheme {
                    Box(Modifier.fillMaxSize()) {
                        HanziGlyph(
                            strokeOrder = order,
                            modifier = Modifier.fillMaxSize(),
                            style = StrokeOrderDefaults.style(showGrid = false)
                        )
                    }
                }
            }
            scene.render().toComposeImageBitmap().toPixelMap()
        } finally {
            scene.close()
        }

        var inkCount = 0
        var sumX = 0L
        var sumY = 0L
        for (y in 0 until size) {
            for (x in 0 until size) {
                if (pixels[x, y].alpha > 0.3f) {
                    inkCount++
                    sumX += x
                    sumY += y
                }
            }
        }

        assertTrue(inkCount > size * size / 50, "expected a meaningful amount of ink, got $inkCount px")
        val centroidX = sumX.toDouble() / inkCount
        val centroidY = sumY.toDouble() / inkCount
        // 中 is roughly centred, so its ink centroid should sit near the middle.
        assertTrue(centroidX in size * 0.40..size * 0.60, "centroidX=$centroidX")
        assertTrue(centroidY in size * 0.30..size * 0.70, "centroidY=$centroidY")
    }
}

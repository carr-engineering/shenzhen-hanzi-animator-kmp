package engineering.carr.hanzianimator.compose

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class StrokeOrderColorTest {
    @Test
    fun everyComponentGetsItsOwnColorIncludingRepeatedParts() {
        for (palette in listOf(StrokeOrderDefaults.LightComponentColors, StrokeOrderDefaults.DarkComponentColors)) {
            val colors = palette.indices.map { index ->
                strokeFinalColor(true, Color.Black, Color.Red, index, palette, hasComponentMapping = true)
            }
            assertEquals(6, colors.toSet().size)
            assertEquals(palette, colors)
        }
    }

    @Test
    fun unmappedStrokesStayNeutralEvenWhenMarkedAsRadical() {
        assertEquals(Color.Black, strokeFinalColor(
            true, Color.Black, Color.Red, null, StrokeOrderDefaults.LightComponentColors,
            hasComponentMapping = true
        ))
    }

    @Test
    fun legacyDataAndExplicitlyDisabledPaletteKeepRadicalColoring() {
        assertEquals(Color.Red, strokeFinalColor(
            true, Color.Black, Color.Red, 0, StrokeOrderDefaults.LightComponentColors,
            hasComponentMapping = false
        ))
        assertEquals(Color.Red, strokeFinalColor(
            true, Color.Black, Color.Red, 0, emptyList(), hasComponentMapping = true
        ))
    }

    @Test
    fun shortCustomPaletteDoesNotWrapAndMisidentifyParts() {
        assertEquals(Color.Black, strokeFinalColor(
            false, Color.Black, Color.Red, 3, listOf(Color.Blue), hasComponentMapping = true
        ))
    }

    @Test
    fun strokeUsesRadicalColorOnlyWhenPartOfTheRadical() {
        val ink = Color(0xFF17120F)
        val radical = Color(0xFFB13A33)

        assertEquals(ink, strokeFinalColor(isRadical = false, normal = ink, radical = radical))
        assertEquals(radical, strokeFinalColor(isRadical = true, normal = ink, radical = radical))
    }
}

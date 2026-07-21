package engineering.carr.hanzianimator.compose

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class StrokeOrderColorTest {
    @Test
    fun strokeUsesRadicalColorOnlyWhenPartOfTheRadical() {
        val ink = Color(0xFF17120F)
        val radical = Color(0xFFB13A33)

        assertEquals(ink, strokeFinalColor(isRadical = false, normal = ink, radical = radical))
        assertEquals(radical, strokeFinalColor(isRadical = true, normal = ink, radical = radical))
    }
}

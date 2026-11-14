import engineering.carr.hanzianimator.json.StrokeOrderJson
import kotlin.test.*

class PublishedJsonTest {
    @Test fun importLegacyCoordinatesExactlyOnce() {
        val order = assertNotNull(StrokeOrderJson.parse("一", """{"strokes":["M 10 20 L 800 30 Z"],"medians":[[[10,20],[800,30]]]}"""))
        assertEquals(880f, order.strokes.single().median.first().y)
        assertEquals(880f, order.strokes.single().outline.coordinateAt(1))
        assertNull(StrokeOrderJson.parse("一", "not JSON"))
    }
}

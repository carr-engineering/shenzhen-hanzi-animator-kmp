package engineering.carr.hanzianimator.json

import engineering.carr.hanzianimator.core.*
import kotlin.test.*

class StrokeOrderJsonTest {
    @Test fun preservesRepeatedComponentsAndUnmappedMembership() {
        val order = requireNotNull(StrokeOrderJson.parse("森", """{"strokes":["M 0 0 L 1 1","M 2 2 L 3 3","M 4 4 L 5 5"],"components":["木","木"],"strokeComponents":[0,null,1]}"""))
        assertEquals(listOf("木", "木"), order.components)
        assertEquals(listOf(0, null, 1), order.strokes.map { it.componentIndex })
    }
    @Test fun flipsOutlinesAndMediansExactlyOnce() {
        val order = requireNotNull(StrokeOrderJson.parse("永", """{"strokes":["M 10 100 Q 20 200 30 300 Z"],"medians":[[[10,100],[20,200]]],"radStrokes":[0]}"""))
        assertEquals(listOf(GlyphPoint(10f,800f),GlyphPoint(20f,700f)), order.strokes.single().median)
        assertContentEquals(floatArrayOf(10f,800f,20f,700f,30f,600f), order.strokes.single().outline.copyCoordinates())
        assertEquals(OutlineCommand.CLOSE, order.strokes.single().outline.commandAt(2))
        assertTrue(order.supportsPractice)
    }
    @Test fun rejectsMalformedNestedShapesWithoutThrowingOrDroppingIndexes() {
        val invalid = listOf("not json", "[]", "{}", """{"strokes":["M 1 2",{}]}""", """{"strokes":["M 1 2"],"medians":[[{}]]}""", """{"strokes":["M 1 2"],"medians":[[[1,"bad"]]]}""", """{"strokes":["M 1 2"],"radStrokes":[99]}""", """{"strokes":["M 1 2"],"components":["木"],"strokeComponents":[9]}""", """{"strokes":["M 1 2"],"components":[{}]}""")
        invalid.forEach { assertNull(StrokeOrderJson.parse("X", it), it) }
    }
    @Test fun missingMediansRemainOptional() {
        val order = requireNotNull(StrokeOrderJson.parse("X", """{"strokes":["M 1 2 L 3 4"]}"""))
        assertTrue(order.strokes.single().median.isEmpty()); assertFalse(order.supportsPractice)
        assertNull(order.components)
    }
    @Test fun supportsAbsoluteAndImplicitCommandsButRejectsUnsupportedSyntax() {
        val outline = StrokeOrderJson.parseOutline("M1,2 3,4 C5 6 7 8 9 10 Z")
        assertEquals(listOf(OutlineCommand.MOVE,OutlineCommand.LINE,OutlineCommand.CUBIC,OutlineCommand.CLOSE), (0 until outline.commandCount).map(outline::commandAt))
        listOf("m 1 2", "M 1 2 H 3", "M 1", "M NaN 2", "M 1 2 Z 3 4", "L 1 2").forEach { assertFails { StrokeOrderJson.parseOutline(it) } }
    }
}

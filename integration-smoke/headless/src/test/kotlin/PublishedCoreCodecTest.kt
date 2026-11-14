import engineering.carr.hanzianimator.codec.BinaryStrokeArchive
import engineering.carr.hanzianimator.core.*
import kotlin.test.*

class PublishedCoreCodecTest {
    @Test fun decodeAndDriveSessionThroughPublicApi() {
        val archive = BinaryStrokeArchive(fixture())
        assertEquals(listOf("森"), archive.characters())
        val order = assertNotNull(archive.strokeOrder("森"))
        assertEquals(listOf("木", "木"), order.components)
        assertEquals(1, order.strokes.single().componentIndex)
        assertEquals(OutlineCommand.MOVE, order.strokes.single().outline.commandAt(0))
        assertEquals(898f, order.strokes.single().median.first().y)
        val session = StrokeSession(order, StrokeAnimationConfig(strokeDurationMillis = 100L))
        session.play()
        session.advanceAnimation(50L)
        assertEquals(0.5f, session.animationFrame.progressByStroke.single())
        session.pause()
        session.advanceAnimation(99L)
        assertEquals(50L, session.animationElapsedMillis)
        session.play()
        session.advanceAnimation(100L)
        assertEquals(StrokeSessionMode.Idle, session.mode)
        assertEquals(1, session.idleRevealedCount)
        assertTrue(session.startQuiz())
        assertTrue(session.submitStroke(order.strokes.single().median).accepted)
        assertEquals(0, assertNotNull(session.quizSummary()).totalMistakes)
    }

    @Test fun rejectDamagedArchiveAndProtectGeometry() {
        val bytes = fixture()
        bytes[20] = (bytes[20].toInt() xor 1).toByte()
        assertFailsWith<IllegalArgumentException> { BinaryStrokeArchive(bytes) }
        val coordinates = floatArrayOf(1f, 2f)
        val outline = StrokeOutline(intArrayOf(OutlineCommand.MOVE.ordinal), coordinates)
        coordinates[0] = 999f
        outline.copyCoordinates()[0] = 999f
        assertEquals(1f, outline.coordinateAt(0))
    }
}

// Public wire-format fixture: one glyph, all command kinds, fractional medians,
// radical flag and two repeated component occurrences. No project dependency.
private fun fixture(): ByteArray = byteArrayOf(
    72, 90, 66, 49, 1, 0, 0, 0, 12, 0, 0, 0, 59, 0, 0, 0, -93, 52, 75, -22, -18, 104, 0, 0, 0, 0, 0, 0, 59, 0, 0, 0, 1, 3, 3, -26, -100, -88, 3, -26, -100, -88, 5, 11, -120, 70, 4, 10, 6, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 3, 2, 4, -1, -1, -1, -1, 15, 0, 0, 0, 0, 0, 0, 10, 64, 4, 4, -1, -1, -1, -1, 15, 0, 0, 0, 0, 0, 0, 26, 64
)

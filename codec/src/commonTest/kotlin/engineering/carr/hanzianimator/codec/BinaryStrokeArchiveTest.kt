package engineering.carr.hanzianimator.codec

import engineering.carr.hanzianimator.core.OutlineCommand
import engineering.carr.hanzianimator.core.GlyphPoint
import kotlin.test.*

class BinaryStrokeArchiveTest {
    @Test fun indexesAndDecodesFixtureLosslessly() {
        val archive = BinaryStrokeArchive(TEST_ARCHIVE)
        assertEquals(TEST_ARCHIVE.size, archive.decodedByteSize)
        assertEquals(listOf("森"), archive.characters())
        val order = requireNotNull(archive.strokeOrder("森"))
        assertEquals(listOf("木", "木"), order.components)
        assertEquals(listOf(1), order.strokes.map { it.componentIndex })
        assertEquals(OutlineCommand.MOVE, order.strokes.first().outline.commandAt(0))
        assertContentEquals(floatArrayOf(1f,898f,3.5f,896f,5f,894f,7f,892f,9f,890f,11f,888f,13f,886f), order.strokes.single().outline.copyCoordinates())
        assertEquals(listOf(GlyphPoint(1f,898f),GlyphPoint(3.25f,896f),GlyphPoint(5f,893.5f)), order.strokes.single().median)
        assertTrue(order.strokes.single().isRadical)
        assertNull(archive.strokeOrder("不存在")); assertNull(archive.strokeOrder("A"))
        assertNull(archive.strokeOrder("\ud800"))
    }
    @Test fun inputAndReturnValuesCannotMutateStoredArchive() {
        val bytes = TEST_ARCHIVE.copyOf(); val archive = BinaryStrokeArchive(bytes)
        bytes.fill(0)
        assertNotNull(archive.strokeOrder("森"))
        val coordinates = requireNotNull(archive.strokeOrder("森")).strokes[0].outline.copyCoordinates()
        coordinates.fill(999f)
        assertFalse(requireNotNull(archive.strokeOrder("森")).strokes[0].outline.copyCoordinates().all { it == 999f })
    }
    @Test fun rejectsTruncationEverywhereAndPayloadCorruption() {
        for (length in TEST_ARCHIVE.indices) assertFailsWith<IllegalArgumentException>("length $length") { BinaryStrokeArchive(TEST_ARCHIVE.copyOf(length)) }
        val changed = TEST_ARCHIVE.copyOf(); changed[changed.lastIndex] = (changed.last().toInt() xor 1).toByte()
        assertFailsWith<IllegalArgumentException> { BinaryStrokeArchive(changed) }
        assertFailsWith<IllegalArgumentException> { BinaryStrokeArchive(TEST_ARCHIVE + byteArrayOf(0)) }
    }
    @Test fun rejectsUnknownVersionAndInvalidDirectoryEvenWithValidPayloadChecksum() {
        val version = TEST_ARCHIVE.copyOf(); version[3] = '2'.code.toByte()
        assertFailsWith<IllegalArgumentException> { BinaryStrokeArchive(version) }
        val remapped = TEST_ARCHIVE.copyOf(); remapped[20] = (remapped[20].toInt() xor 1).toByte()
        assertFailsWith<IllegalArgumentException> { BinaryStrokeArchive(remapped) }
        val offset = TEST_ARCHIVE.copyOf(); offset[24] = 1
        assertFailsWith<IllegalArgumentException> { BinaryStrokeArchive(offset) }
        val cp = TEST_ARCHIVE.copyOf(); cp[20] = 0; cp[21] = 0xd8.toByte(); cp[22] = 0; cp[23] = 0
        assertFailsWith<IllegalArgumentException> { BinaryStrokeArchive(cp) }
    }
    @Test fun rejectsInvalidRecordsEvenWhenChecksumWasRecomputed() {
        fun check(index: Int, value: Int) {
            val bytes = TEST_ARCHIVE.copyOf(); bytes[index] = value.toByte()
            var crc = -1
            for (offset in 20 until bytes.size) {
                crc = crc xor (bytes[offset].toInt() and 255)
                repeat(8) { crc = if (crc and 1 == 0) crc ushr 1 else (crc ushr 1) xor 0xedb88320.toInt() }
            }
            crc = crc.inv()
            repeat(4) { bytes[16 + it] = (crc ushr (8 * it)).toByte() }
            val archive = BinaryStrokeArchive(bytes)
            assertFailsWith<IllegalArgumentException>("byte $index") { archive.strokeOrder("森") }
        }
        check(32, 0) // Empty stroke list.
        check(42, 7) // Component index 2, but there are only two occurrences.
        check(44, 143) // Invalid command opcode 7.
        check(45, 198) // Nonzero command padding.
        check(43, 255) // Unterminated/oversized command-count varint.
    }

}

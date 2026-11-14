package engineering.carr.hanzianimator.codec

import engineering.carr.hanzianimator.core.GlyphPoint
import engineering.carr.hanzianimator.core.OutlineCommand
import engineering.carr.hanzianimator.core.Stroke
import engineering.carr.hanzianimator.core.StrokeOrder
import engineering.carr.hanzianimator.core.StrokeOutline

/** A checked, indexed HZB1 archive. It retains bytes and decodes one character on demand. */
public class BinaryStrokeArchive(bytes: ByteArray) {
    private val bytes: ByteArray = bytes.copyOf()
    public val decodedByteSize: Int get() = bytes.size
    private data class Entry(val codePoint: Int, val offset: Int, val length: Int)
    private val entries: List<Entry>
    private val payloadStart: Int
    init {
        require(bytes.size >= HEADER_SIZE && bytes.size <= MAX_ARCHIVE_SIZE) { "Invalid archive length" }
        val reader = Reader(this.bytes, 0, this.bytes.size)
        require(reader.u8() == 72 && reader.u8() == 90 && reader.u8() == 66 && reader.u8() == 49) { "Unsupported archive version" }
        val count = reader.u32().bounded(MAX_CHARACTERS, "Record count")
        val directorySize = reader.u32().bounded(MAX_CHARACTERS * 12, "Directory size")
        require(directorySize == count * 12) { "Invalid directory size" }
        val payloadSize = reader.u32().bounded(MAX_ARCHIVE_SIZE, "Payload size")
        val expectedCrc = reader.u32()
        payloadStart = HEADER_SIZE + directorySize
        require(payloadStart.toLong() + payloadSize == bytes.size.toLong()) { "Archive length mismatch" }
        require(crc32(this.bytes, HEADER_SIZE, directorySize + payloadSize) == expectedCrc) { "Archive checksum mismatch" }
        var previousCodePoint = -1
        var previousEnd = 0
        entries = List(count) {
            val cp = reader.u32().bounded(0x10ffff, "Code point")
            val offset = reader.u32().bounded(payloadSize, "Record offset")
            val length = reader.u32().bounded(MAX_RECORD_SIZE, "Record length")
            require(cp !in 0xd800..0xdfff && cp > previousCodePoint) { "Directory must have unique sorted Unicode scalars" }
            require(offset == previousEnd && length > 0 && offset.toLong() + length <= payloadSize) { "Invalid record bounds" }
            previousCodePoint = cp; previousEnd = offset + length
            Entry(cp, offset, length)
        }
        require(previousEnd == payloadSize) { "Unindexed payload bytes" }
    }
    public fun characters(): List<String> = entries.map { scalarString(it.codePoint) }
    public fun chars(): List<String> = characters()
    public fun strokeOrder(character: String): StrokeOrder? {
        val cp = scalarCodePoint(character) ?: return null
        var low = 0; var high = entries.lastIndex
        while (low <= high) {
            val mid = (low + high) ushr 1
            val entry = entries[mid]
            if (entry.codePoint < cp) low = mid + 1 else if (entry.codePoint > cp) high = mid - 1 else return decodeRecord(character, entry)
        }
        return null
    }
    private fun decodeRecord(character: String, entry: Entry): StrokeOrder {
        val reader = Reader(bytes, payloadStart + entry.offset, payloadStart + entry.offset + entry.length)
        val strokeCount = reader.uv().bounded(256, "Stroke count")
        require(strokeCount > 0) { "Empty character record" }
        val componentHeader = reader.uv().bounded(257, "Component count")
        val components = if (componentHeader == 0) null else List(componentHeader - 1) {
            val length = reader.uv().bounded(4096, "Label length")
            reader.take(length).decodeToString(throwOnInvalidSequence = true).also { require(it.isNotBlank()) }
        }
        val strokes = List(strokeCount) { index ->
            val flags = reader.uv().bounded(513, "Stroke flags")
            val membership = (flags ushr 1) - 1
            val componentIndex = if (membership < 0) null else membership.also { require(components != null && it in components.indices) { "Invalid component reference" } }
            val outlineHeader = reader.uv().bounded(131073, "Outline header")
            val count = outlineHeader ushr 1
            require(count > 0)
            val scale = if (outlineHeader and 1 == 0) 1f else 2f
            val packed = reader.take((count * 3 + 7) / 8)
            val commands = IntArray(count) { commandIndex ->
                val bit = commandIndex * 3
                val first = packed[bit / 8].toInt() and 255
                val second = if (bit % 8 > 5) packed[bit / 8 + 1].toInt() and 255 else 0
                val code = ((first or (second shl 8)) ushr (bit % 8)) and 7
                require(code in OutlineCommand.entries.indices) { "Unsupported outline command" }
                code
            }
            // Unused command padding must be zero, so corruption cannot hide in ignored bits.
            val used = count * 3 % 8
            if (used != 0) require((packed.last().toInt() and 255) ushr used == 0) { "Invalid command padding" }
            val pointCount = commands.sumOf { OutlineCommand.entries[it].coordinateCount / 2 }
            val xs = reader.deltas(pointCount)
            val ys = reader.deltas(pointCount)
            val coords = FloatArray(pointCount * 2) { position ->
                if (position % 2 == 0) xs[position / 2] / scale else StrokeOrder.GLYPH_Y_BASE - ys[position / 2] / scale
            }
            val outline = StrokeOutline(commands, coords)
            val medianCount = reader.uv().bounded(4096, "Median count")
            var previousX = 0L; var previousY = 0L
            val medians = List(medianCount) {
                val x = reader.medianCoordinate(previousX); previousX = x.second
                val y = reader.medianCoordinate(previousY); previousY = y.second
                GlyphPoint(x.first, StrokeOrder.GLYPH_Y_BASE - y.first)
            }
            Stroke(index, outline, medians, flags and 1 != 0, componentIndex)
        }
        require(reader.atEnd) { "Trailing record data" }
        return StrokeOrder(character, strokes, components)
    }
    public companion object {
        public const val FORMAT_VERSION: Int = 1
        private const val HEADER_SIZE = 20
        private const val MAX_ARCHIVE_SIZE = 64 * 1024 * 1024
        private const val MAX_CHARACTERS = 200000
        private const val MAX_RECORD_SIZE = 1024 * 1024
    }
}

private class Reader(private val bytes: ByteArray, private var position: Int, private val end: Int) {
    val atEnd: Boolean get() = position == end
    fun u8(): Int { require(position < end) { "Truncated archive" }; return bytes[position++].toInt() and 255 }
    fun u32(): Long = u8().toLong() or (u8().toLong() shl 8) or (u8().toLong() shl 16) or (u8().toLong() shl 24)
    fun uv(): Long {
        var value = 0L
        for (byteIndex in 0..4) {
            val byte = u8()
            require(byteIndex < 4 || byte and 240 == 0) { "Varint overflow" }
            value = value or ((byte and 127).toLong() shl (byteIndex * 7))
            if (byte and 128 == 0) {
                require(byteIndex == 0 || byte != 0) { "Noncanonical varint" }
                return value
            }
        }
        error("Varint overflow")
    }
    fun take(length: Int): ByteArray {
        require(length >= 0 && length <= end - position) { "Truncated archive" }
        val result = bytes.copyOfRange(position, position + length); position += length; return result
    }
    fun deltas(count: Int): FloatArray {
        var previous = 0L
        return FloatArray(count) {
            previous += zigzag(uv())
            require(previous in -1_000_000L..1_000_000L) { "Coordinate out of bounds" }
            previous.toFloat()
        }
    }
    fun medianCoordinate(previous: Long): Pair<Float, Long> {
        val value = uv()
        if (value == 0xffffffffL) {
            val low = u32(); val high = u32()
            val number = Double.fromBits(low or (high shl 32))
            require(number.isFinite() && number in -1_000_000.0..1_000_000.0) { "Invalid median coordinate" }
            return number.toFloat() to number.toLong()
        }
        val result = previous + zigzag(value)
        require(result in -1_000_000L..1_000_000L) { "Median coordinate out of bounds" }
        return result.toFloat() to result
    }
}
private fun Long.bounded(max: Int, label: String): Int { require(this in 0..max.toLong()) { "$label exceeds limit" }; return toInt() }
private fun zigzag(value: Long): Long = (value ushr 1) xor -(value and 1)
private fun scalarString(cp: Int): String = if (cp < 0x10000) cp.toChar().toString() else {
    val scalar = cp - 0x10000
    charArrayOf((0xd800 + (scalar ushr 10)).toChar(), (0xdc00 + (scalar and 1023)).toChar()).concatToString()
}
private fun scalarCodePoint(character: String): Int? = when {
    character.length == 1 && character[0].code !in 0xd800..0xdfff -> character[0].code
    character.length == 2 && character[0].code in 0xd800..0xdbff && character[1].code in 0xdc00..0xdfff -> 0x10000 + ((character[0].code - 0xd800) shl 10) + character[1].code - 0xdc00
    else -> null
}
private val crcTable: IntArray = IntArray(256) { value ->
    var crc = value
    repeat(8) { crc = if (crc and 1 != 0) (crc ushr 1) xor 0xedb88320.toInt() else crc ushr 1 }
    crc
}
private fun crc32(bytes: ByteArray, start: Int, length: Int): Long {
    var crc = -1
    for (position in start until start + length) crc = (crc ushr 8) xor crcTable[(crc xor bytes[position].toInt()) and 255]
    return (crc.inv().toLong() and 0xffffffffL)
}

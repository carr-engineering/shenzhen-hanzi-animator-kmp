package engineering.carr.hanzianimator.data

import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Loads packaged gzip HZB1 assets through a caller-selected class loader. */
public fun jvmBundledDataSource(
    classLoader: ClassLoader = BundledStrokeOrderProvider::class.java.classLoader,
    basePath: String = "strokes",
): StrokeDataSource = StrokeDataSource { key ->
    require(!key.contains("..") && !key.startsWith('/')) { "Invalid resource key" }
    val stream = classLoader.getResourceAsStream("${basePath.trimEnd('/')}/$key")
        ?: error("Bundled stroke resource missing: $key")
    stream.use { if (key.endsWith(".hzb")) decodeGzip(it) else readBounded(it) }
}

public actual fun platformBundledDataSource(): StrokeDataSource = jvmBundledDataSource()
internal actual fun platformCacheConfig(): StrokeDataCacheConfig = StrokeDataCacheConfig.Native
internal actual fun platformShardCount(): Int = 1

private fun readBounded(input: InputStream): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(32768)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        require(output.size() <= 64 * 1024 * 1024 - count) { "Decoded resource exceeds 64 MiB" }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

/** A single complete gzip member, including header/trailer integrity and absence of trailing input. */
private fun decodeGzip(input: InputStream): ByteArray {
    val stream = java.io.PushbackInputStream(input, 32768)
    val headerCrc = java.util.zip.CRC32()
    fun headerByte(): Int {
        val value = stream.read()
        if (value < 0) throw java.io.EOFException("Truncated gzip header")
        headerCrc.update(value)
        return value
    }
    if (headerByte() != 0x1f || headerByte() != 0x8b || headerByte() != 8) throw java.util.zip.ZipException("Invalid gzip header")
    val flags = headerByte()
    if (flags and 0xe0 != 0) throw java.util.zip.ZipException("Invalid gzip flags")
    repeat(6) { headerByte() }
    if (flags and 4 != 0) {
        val length = headerByte() or (headerByte() shl 8)
        repeat(length) { headerByte() }
    }
    if (flags and 8 != 0) while (headerByte() != 0) { }
    if (flags and 16 != 0) while (headerByte() != 0) { }
    if (flags and 2 != 0) {
        val low = stream.read(); val high = stream.read()
        if (low < 0 || high < 0) throw java.io.EOFException("Truncated gzip header checksum")
        if ((low or (high shl 8)).toLong() != (headerCrc.value and 0xffffL)) throw java.util.zip.ZipException("Invalid gzip header checksum")
    }
    val inflater = java.util.zip.Inflater(true)
    val crc = java.util.zip.CRC32()
    val compressed = ByteArray(32768)
    val decoded = ByteArray(32768)
    val output = ByteArrayOutputStream()
    var lastInput = 0
    try {
        while (!inflater.finished()) {
            if (inflater.needsInput()) {
                lastInput = stream.read(compressed)
                if (lastInput <= 0) throw java.io.EOFException("Truncated gzip payload")
                inflater.setInput(compressed, 0, lastInput)
            }
            val count = try { inflater.inflate(decoded) } catch (failure: java.util.zip.DataFormatException) {
                throw java.util.zip.ZipException("Invalid gzip payload: ${failure.message}")
            }
            if (count > 0) {
                require(output.size() <= 64 * 1024 * 1024 - count) { "Decoded resource exceeds 64 MiB" }
                crc.update(decoded, 0, count)
                output.write(decoded, 0, count)
            } else if (!inflater.finished() && !inflater.needsInput()) throw java.util.zip.ZipException("Invalid gzip stream state")
        }
        val remaining = inflater.remaining
        if (remaining > 0) stream.unread(compressed, lastInput - remaining, remaining)
    } finally { inflater.end() }
    fun trailerInteger(): Long {
        var value = 0L
        repeat(4) { shift ->
            val byte = stream.read()
            if (byte < 0) throw java.io.EOFException("Truncated gzip trailer")
            value = value or (byte.toLong() shl (8 * shift))
        }
        return value
    }
    if (trailerInteger() != crc.value) throw java.util.zip.ZipException("Invalid gzip payload checksum")
    if (trailerInteger() != output.size().toLong()) throw java.util.zip.ZipException("Invalid gzip decoded size")
    if (stream.read() != -1) throw java.util.zip.ZipException("Trailing input after gzip member")
    return output.toByteArray()
}

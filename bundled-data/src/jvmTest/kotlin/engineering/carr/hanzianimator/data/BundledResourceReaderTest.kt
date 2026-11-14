package engineering.carr.hanzianimator.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class BundledResourceReaderTest {
    private fun source(bytes: ByteArray): StrokeDataSource = jvmBundledDataSource(object : ClassLoader() {
        override fun getResourceAsStream(name: String) = ByteArrayInputStream(bytes)
    })

    private fun gzip(bytes: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { it.write(bytes) }
        return output.toByteArray()
    }

    @Test
    fun systemReaderReturnsDecodedBytesAndRejectsCorruptAndTruncatedGzip() = runTest {
        val decoded = "lossless binary bytes".encodeToByteArray()
        val compressed = gzip(decoded)
        assertContentEquals(decoded, source(compressed).read("all.hzb"))
        assertFailsWith<java.io.IOException> { source(compressed.copyOf(compressed.size - 1)).read("all.hzb") }
        assertFailsWith<java.io.IOException> { source(compressed + byteArrayOf(1, 2, 3)).read("all.hzb") }
        assertFailsWith<java.io.IOException> { source(compressed + compressed).read("all.hzb") }
        val corrupt = compressed.copyOf()
        corrupt[corrupt.size - 8] = (corrupt[corrupt.size - 8].toInt() xor 1).toByte()
        assertFailsWith<java.io.IOException> { source(corrupt).read("all.hzb") }
    }

    @Test
    fun readerRejectsTraversalKeys() = runTest {
        assertFailsWith<IllegalArgumentException> { source(gzip(byteArrayOf())).read("../elsewhere") }
    }
}

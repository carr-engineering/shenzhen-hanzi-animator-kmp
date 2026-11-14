package engineering.carr.hanzianimator.data

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class BrotliDecoderTest {
    private val compressed = byteArrayOf(11, 10, -128, 108, 111, 115, 115, 108, 101, 115, 115, 32, 98, 105, 110, 97, 114, 121, 32, 98, 121, 116, 101, 115, 3)

    @Test
    fun systemDecoderRequiresCompleteStream() {
        assertContentEquals("lossless binary bytes".encodeToByteArray(), decodeBrotli(compressed))
        assertFailsWith<IllegalArgumentException> { decodeBrotli(compressed.copyOf(compressed.size - 1)) }
        assertFailsWith<IllegalArgumentException> { decodeBrotli(compressed + byteArrayOf(0)) }
    }
}

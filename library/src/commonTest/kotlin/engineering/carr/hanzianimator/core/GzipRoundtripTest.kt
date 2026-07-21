package engineering.carr.hanzianimator.core

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GzipRoundtripTest {
    // `printf 'Hello, 你好! stroke order.' | gzip -9`
    private val gzipped = byteArrayOf(
        0x1f, 0x8b.toByte(), 0x08, 0x00, 0xac.toByte(), 0x38, 0x5f, 0x6a, 0x02, 0x03, 0xf3.toByte(), 0x48,
        0xcd.toByte(), 0xc9.toByte(), 0xc9.toByte(), 0xd7.toByte(), 0x51, 0x78, 0xb2.toByte(), 0x77, 0xc1.toByte(), 0xd3.toByte(), 0xa5.toByte(), 0x7b,
        0x15, 0x15, 0x8a.toByte(), 0x4b, 0x8a.toByte(), 0xf2.toByte(), 0xb3.toByte(), 0x53, 0x15, 0xf2.toByte(), 0x8b.toByte(), 0x52,
        0x52, 0x8b.toByte(), 0xf4.toByte(), 0x00, 0x29, 0x6e, 0x85.toByte(), 0x8b.toByte(), 0x1c, 0x00, 0x00, 0x00
    )

    @Test
    fun decompressesGzipOnEveryPlatform() = runTest {
        val decompressed = gunzip(gzipped).decodeToString()
        assertEquals("Hello, 你好! stroke order.", decompressed)
    }

    @Test
    fun decompressesEmptyInputToEmpty() = runTest {
        assertTrue(gunzip(ByteArray(0)).isEmpty())
    }
}

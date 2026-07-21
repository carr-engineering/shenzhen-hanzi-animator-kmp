package engineering.carr.hanzianimator.core

import java.util.zip.GZIPInputStream

internal actual suspend fun gunzip(bytes: ByteArray): ByteArray {
    if (bytes.isEmpty()) return ByteArray(0)
    return GZIPInputStream(bytes.inputStream()).use { it.readBytes() }
}

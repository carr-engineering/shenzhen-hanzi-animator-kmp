package engineering.carr.hanzianimator.core

import kotlinx.coroutines.await
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.get
import org.khronos.webgl.set
import kotlin.js.Promise

internal actual suspend fun gunzip(bytes: ByteArray): ByteArray {
    if (bytes.isEmpty()) return ByteArray(0)
    val input = Int8Array(bytes.size)
    for (i in bytes.indices) input[i] = bytes[i]
    val buffer = decompressGzip(input).await()
    val view = Int8Array(buffer)
    return ByteArray(view.length) { view[it] }
}

private fun decompressGzip(data: Int8Array): Promise<ArrayBuffer> = js(
    "new Response(new Blob([data]).stream().pipeThrough(new DecompressionStream('gzip'))).arrayBuffer()"
)

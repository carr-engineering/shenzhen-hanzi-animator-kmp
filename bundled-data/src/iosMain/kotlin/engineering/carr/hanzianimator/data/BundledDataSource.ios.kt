@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package engineering.carr.hanzianimator.data

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.darwin.COMPRESSION_BROTLI
import platform.darwin.COMPRESSION_LZMA
import platform.darwin.COMPRESSION_STATUS_END
import platform.darwin.COMPRESSION_STATUS_OK
import platform.darwin.COMPRESSION_STREAM_DECODE
import platform.darwin.COMPRESSION_STREAM_FINALIZE
import platform.darwin.compression_stream
import platform.darwin.compression_stream_destroy
import platform.darwin.compression_stream_init
import platform.darwin.compression_stream_process
import platform.Foundation.NSBundle
import platform.Foundation.NSFileManager
import platform.posix.memcpy

/** System codec used by an embedded iOS data profile. */
public enum class IosStrokeCompression { Brotli, Lzma }

/**
 * Reads Brotli HZB1 resources using Apple's system Compression decoder (iOS 15+).
 * Copy generated iosMain/resources/strokes into this bundle when embedding the framework.
 */
public fun iosBundledDataSource(
    bundle: NSBundle = NSBundle.mainBundle,
    basePath: String = "strokes",
    compression: IosStrokeCompression = IosStrokeCompression.Brotli,
): StrokeDataSource = StrokeDataSource { key ->
    val bytes = readBundleBytes(bundle, basePath, key)
    if (key.endsWith(".hzb")) decodeAppleStream(bytes, compression) else bytes
}

private fun readBundleBytes(bundle: NSBundle, basePath: String, key: String): ByteArray {
    require(!key.contains("..") && !key.startsWith('/')) { "Invalid bundle resource key" }
    val root = bundle.resourcePath ?: error("Bundle has no resource directory")
    val data = NSFileManager.defaultManager.contentsAtPath("$root/${basePath.trimEnd('/')}/$key")
        ?: error("Bundled stroke resource missing: $key")
    require(data.length <= Int.MAX_VALUE.toULong()) { "Resource exceeds maximum size" }
    val result = ByteArray(data.length.toInt())
    if (result.isNotEmpty()) result.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
    return result
}

/** Require stream completion; reject truncated data, trailing input and excessive expansion. */
internal fun decodeBrotli(input: ByteArray): ByteArray = decodeAppleStream(input, IosStrokeCompression.Brotli)

internal fun decodeAppleStream(input: ByteArray, compression: IosStrokeCompression): ByteArray {
    require(input.isNotEmpty()) { "Empty Brotli resource" }
    val chunks = ArrayList<ByteArray>()
    var size = 0
    memScoped {
        val stream = alloc<compression_stream>()
        stream.state = null
        val algorithm = if (compression == IosStrokeCompression.Brotli) COMPRESSION_BROTLI else COMPRESSION_LZMA
        check(compression_stream_init(stream.ptr, COMPRESSION_STREAM_DECODE, algorithm) == COMPRESSION_STATUS_OK) {
            "Cannot initialize system Brotli decoder"
        }
        try {
            val output = ByteArray(32768)
            input.usePinned { pinnedInput ->
                output.usePinned { pinnedOutput ->
                    stream.src_ptr = pinnedInput.addressOf(0).reinterpret()
                    stream.src_size = input.size.convert()
                    do {
                        stream.dst_ptr = pinnedOutput.addressOf(0).reinterpret()
                        stream.dst_size = output.size.convert()
                        val inputBefore = stream.src_size
                        val status = compression_stream_process(stream.ptr, COMPRESSION_STREAM_FINALIZE.toInt())
                        val count = output.size - stream.dst_size.toInt()
                        require(status == COMPRESSION_STATUS_OK || status == COMPRESSION_STATUS_END) { "Invalid Brotli resource" }
                        require(size <= 64 * 1024 * 1024 - count) { "Decoded resource exceeds 64 MiB" }
                        if (count > 0) { chunks += output.copyOf(count); size += count }
                        if (status == COMPRESSION_STATUS_END) {
                            require(stream.src_size == 0.toULong()) { "Trailing bytes after Brotli stream" }
                            break
                        }
                        require(count > 0 || stream.src_size != inputBefore) { "Truncated Brotli stream" }
                    } while (true)
                }
            }
        } finally {
            compression_stream_destroy(stream.ptr)
        }
    }
    val result = ByteArray(size)
    var offset = 0
    for (chunk in chunks) { chunk.copyInto(result, offset); offset += chunk.size }
    return result
}

public actual fun platformBundledDataSource(): StrokeDataSource = iosBundledDataSource()
internal actual fun platformCacheConfig(): StrokeDataCacheConfig = StrokeDataCacheConfig.Native
internal actual fun platformShardCount(): Int = 1

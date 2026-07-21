package engineering.carr.hanzianimator.core

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import platform.zlib.Z_BUF_ERROR
import platform.zlib.Z_NO_FLUSH
import platform.zlib.Z_OK
import platform.zlib.Z_STREAM_END
import platform.zlib.inflate
import platform.zlib.inflateEnd
import platform.zlib.inflateInit2_
import platform.zlib.z_stream
import platform.zlib.zlibVersion

@OptIn(ExperimentalForeignApi::class, UnsafeNumber::class)
internal actual suspend fun gunzip(bytes: ByteArray): ByteArray {
    if (bytes.isEmpty()) return ByteArray(0)
    val output = ArrayList<Byte>(bytes.size * 4)
    memScoped {
        val stream = alloc<z_stream>()
        // window bits 15 (max) + 32 -> auto-detect zlib/gzip header.
        val initResult = inflateInit2_(
            strm = stream.ptr,
            windowBits = 15 + 32,
            version = zlibVersion()?.toKString(),
            stream_size = sizeOf<z_stream>().toInt()
        )
        check(initResult == Z_OK) { "inflateInit2 failed: $initResult" }
        try {
            val bufferSize = 32768
            val outBuffer = ByteArray(bufferSize)
            bytes.usePinned { input ->
                outBuffer.usePinned { out ->
                    stream.next_in = input.addressOf(0).reinterpret()
                    stream.avail_in = bytes.size.convert()
                    var result: Int
                    do {
                        stream.next_out = out.addressOf(0).reinterpret()
                        stream.avail_out = bufferSize.convert()
                        result = inflate(stream.ptr, Z_NO_FLUSH)
                        check(result == Z_OK || result == Z_STREAM_END || result == Z_BUF_ERROR) {
                            "inflate failed: $result"
                        }
                        val produced = bufferSize - stream.avail_out.toInt()
                        for (i in 0 until produced) output.add(outBuffer[i])
                        if (result == Z_BUF_ERROR && produced == 0) break
                    } while (result != Z_STREAM_END)
                }
            }
        } finally {
            inflateEnd(stream.ptr)
        }
    }
    return output.toByteArray()
}

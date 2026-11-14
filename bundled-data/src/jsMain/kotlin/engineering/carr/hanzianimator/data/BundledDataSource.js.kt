package engineering.carr.hanzianimator.data

import kotlinx.coroutines.await
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.get
import kotlin.js.Promise

/** Browser fetch already decodes negotiated Content-Encoding; never decompress its result again. */
public fun browserBundledDataSource(baseUrl: String = "./strokes/"): StrokeDataSource = StrokeDataSource { key ->
    require(!key.contains("..") && !key.startsWith('/')) { "Invalid resource key" }
    val bytes = Int8Array(fetchBytes("${baseUrl.trimEnd('/')}/$key").await())
    ByteArray(bytes.length) { bytes[it] }
}

private fun fetchBytes(url: String): Promise<ArrayBuffer> = js(
    "fetch(url).then(function(r){if(!r.ok)throw new Error('Stroke resource HTTP '+r.status);return r.arrayBuffer();})"
)

public actual fun platformBundledDataSource(): StrokeDataSource = browserBundledDataSource()
internal actual fun platformCacheConfig(): StrokeDataCacheConfig = StrokeDataCacheConfig.Web
internal actual fun platformShardCount(): Int = 1024

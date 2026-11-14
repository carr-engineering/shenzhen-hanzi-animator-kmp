@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package engineering.carr.hanzianimator.data

import kotlinx.coroutines.await
import kotlin.js.JsAny
import kotlin.js.Promise

/** Browser fetch already decodes negotiated Content-Encoding; never decompress its result again. */
public fun browserBundledDataSource(baseUrl: String = "./strokes/"): StrokeDataSource = StrokeDataSource { key ->
    require(!key.contains("..") && !key.startsWith('/')) { "Invalid resource key" }
    val bytes = fetchBytes("${baseUrl.trimEnd('/')}/$key").await()
    ByteArray(bytes.length) { bytes.byteAt(it).toByte() }
}

private external interface BrowserByteView : JsAny {
    val length: Int
    fun byteAt(index: Int): Int
}

private fun fetchBytes(url: String): Promise<BrowserByteView> = js(
    "fetch(url).then(function(r){if(!r.ok)throw new Error('Stroke resource HTTP '+r.status);return r.arrayBuffer();}).then(function(b){var v=new Int8Array(b);return {length:v.length,byteAt:function(i){return v[i];}};})"
)

public actual fun platformBundledDataSource(): StrokeDataSource = browserBundledDataSource()
internal actual fun platformCacheConfig(): StrokeDataCacheConfig = StrokeDataCacheConfig.Web
internal actual fun platformShardCount(): Int = 1024

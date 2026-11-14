package engineering.carr.hanzianimator.data

import engineering.carr.hanzianimator.codec.BinaryStrokeArchive
import engineering.carr.hanzianimator.core.StrokeOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Reads decoded resource bytes. Archive keys return HZB1; index.txt returns UTF-8. HTTP Content-Encoding is already decoded. */
public fun interface StrokeDataSource {
    public suspend fun read(resourceKey: String): ByteArray
}

/** Bounded decoded block and parsed-character caches. Zero disables either cache. */
public data class StrokeDataCacheConfig(
    public val decodedBytes: Int = 12 * 1024 * 1024,
    public val parsedCharacters: Int = 64,
) {
    init {
        require(decodedBytes >= 0) { "decodedBytes must be non-negative" }
        require(parsedCharacters >= 0) { "parsedCharacters must be non-negative" }
    }

    public companion object {
        public val Native: StrokeDataCacheConfig = StrokeDataCacheConfig()
        public val Web: StrokeDataCacheConfig = StrokeDataCacheConfig(1024 * 1024, 64)
    }
}

/**
 * Loads lossless binary archives from an application's asset or network reader.
 *
 * [scope] belongs to the application: cancelling it cancels pending work. Concurrent
 * reads of the same block share one request, while unrelated blocks load independently.
 * [shardCount] must match the generated profile: 1 uses all.hzb, otherwise codePoint %
 * shardCount selects a numbered .hzb block. Reader keys are relative to the strokes folder.
 */
public class BinaryStrokeOrderProvider(
    private val source: StrokeDataSource,
    private val scope: CoroutineScope,
    private val cacheConfig: StrokeDataCacheConfig = StrokeDataCacheConfig.Native,
    public val shardCount: Int = 1,
) : StrokeOrderProvider {
    init { require(shardCount in 1..4096) { "shardCount must be in 1..4096" } }

    private val mutex = Mutex()
    private val archives = LinkedHashMap<String, BinaryStrokeArchive>()
    private val parsed = LinkedHashMap<String, StrokeOrder>()
    private val pending = mutableMapOf<String, Deferred<Result<BinaryStrokeArchive>>>()
    private var retainedBytes = 0

    override suspend fun strokeOrder(character: String): StrokeOrder? {
        val key = firstCharacterKey(character) ?: return null
        mutex.withLock { parsed.remove(key)?.let { parsed[key] = it; return it } }
        val resource = if (shardCount == 1) "all.hzb" else "${codePointOf(key) % shardCount}.hzb"
        val archive = loadArchive(resource)
        val order = withContext(Dispatchers.Default) { archive.strokeOrder(key) } ?: return null
        if (cacheConfig.parsedCharacters > 0) mutex.withLock {
            parsed[key] = order
            while (parsed.size > cacheConfig.parsedCharacters) parsed.remove(parsed.keys.first())
        }
        return order
    }

    /** Returns a snapshot of the index in an unsharded profile. */
    public suspend fun availableCharacters(): List<String> {
        require(shardCount == 1) { "For sharded data, read the profile's index.txt through your source." }
        return loadArchive("all.hzb").characters()
    }

    /** Clear retained blocks and parsed characters; pending loads are allowed to finish. */
    public suspend fun clearCache(): Unit = mutex.withLock {
        archives.clear()
        parsed.clear()
        retainedBytes = 0
    }

    private suspend fun loadArchive(resourceKey: String): BinaryStrokeArchive {
        val deferred = mutex.withLock {
            archives.remove(resourceKey)?.let { archives[resourceKey] = it; return it }
            pending[resourceKey] ?: scope.async {
                try {
                    val archive = withContext(Dispatchers.Default) { BinaryStrokeArchive(source.read(resourceKey)) }
                    mutex.withLock {
                        if (archive.decodedByteSize <= cacheConfig.decodedBytes && cacheConfig.decodedBytes > 0) {
                            archives.remove(resourceKey)?.let { retainedBytes -= it.decodedByteSize }
                            archives[resourceKey] = archive
                            retainedBytes += archive.decodedByteSize
                            while (retainedBytes > cacheConfig.decodedBytes) {
                                val eldest = archives.keys.first()
                                retainedBytes -= archives.remove(eldest)!!.decodedByteSize
                            }
                        }
                    }
                    Result.success(archive)
                } catch (failure: Throwable) {
                    // Source failures belong to this request; they must not cancel the caller-owned scope.
                    Result.failure(failure)
                } finally {
                    withContext(NonCancellable) { mutex.withLock { pending.remove(resourceKey) } }
                }
            }.also { pending[resourceKey] = it }
        }
        return deferred.await().getOrThrow()
    }
}

/** Returns the first valid Unicode scalar, or null for empty/malformed text. */
internal fun firstCharacterKey(text: String): String? {
    if (text.isEmpty()) return null
    val first = text[0]
    return when {
        first.isHighSurrogate() -> if (text.length >= 2 && text[1].isLowSurrogate()) text.substring(0, 2) else null
        first.isLowSurrogate() -> null
        else -> text.substring(0, 1)
    }
}

internal fun codePointOf(key: String): Int = if (key.length == 2) {
    0x10000 + ((key[0].code - 0xd800) shl 10) + key[1].code - 0xdc00
} else key[0].code

package engineering.carr.hanzianimator.data

import engineering.carr.hanzianimator.core.StrokeOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

/**
 * The full pinned dataset with target-specific lossless compression.
 *
 * The application supplies [scope] and owns pending-load lifetime. Android must
 * supply [androidBundledDataSource] with its AssetManager; iOS/JVM/browser use
 * the platform source by default. Custom sources return decoded HZB1 bytes.
 */
public class BundledStrokeOrderProvider(
    scope: CoroutineScope,
    source: StrokeDataSource = platformBundledDataSource(),
    cacheConfig: StrokeDataCacheConfig = platformCacheConfig(),
    shardCount: Int = platformShardCount(),
    private val indexSource: StrokeDataSource = source,
) : StrokeOrderProvider {
    private val provider = BinaryStrokeOrderProvider(source, scope, cacheConfig, shardCount)
    private val scope = scope
    private val unsharded = shardCount == 1
    private val indexMutex = Mutex()
    private var index: Deferred<Result<List<String>>>? = null

    override suspend fun strokeOrder(character: String): StrokeOrder? = provider.strokeOrder(character)

    /** A snapshot of all available Unicode characters in code-point order. */
    public suspend fun availableCharacters(): List<String> {
        val pending = indexMutex.withLock {
            index ?: scope.async {
                withContext(Dispatchers.Default) {
                    runCatching {
                        if (unsharded) provider.availableCharacters()
                        else {
                            val raw = indexSource.read("index.txt").decodeToString(throwOnInvalidSequence = true)
                            splitCharacters(raw)
                        }
                    }
                }
            }.also { index = it }
        }
        val result = pending.await()
        return try {
            result.getOrThrow().toList()
        } catch (failure: Throwable) {
            indexMutex.withLock { if (index === pending) index = null }
            throw failure
        }
    }

    public suspend fun randomCharacter(random: Random = Random.Default): String? {
        val available = availableCharacters()
        return if (available.isEmpty()) null else available[random.nextInt(available.size)]
    }

    public suspend fun clearCache() {
        provider.clearCache()
        indexMutex.withLock { index = null }
    }
}

/** The default resource reader. Android applications should supply an explicit AssetManager reader. */
public expect fun platformBundledDataSource(): StrokeDataSource

internal expect fun platformCacheConfig(): StrokeDataCacheConfig
internal expect fun platformShardCount(): Int

internal fun splitCharacters(text: String): List<String> {
    val characters = ArrayList<String>()
    var index = 0
    while (index < text.length) {
        val first = text[index]
        require(!first.isLowSurrogate()) { "Malformed character index" }
        if (first.isHighSurrogate()) {
            require(index + 1 < text.length && text[index + 1].isLowSurrogate()) { "Malformed character index" }
            characters += text.substring(index, index + 2)
            index += 2
        } else {
            characters += first.toString()
            index++
        }
    }
    require(characters.size == characters.toSet().size) { "Duplicate character index entries" }
    return characters
}

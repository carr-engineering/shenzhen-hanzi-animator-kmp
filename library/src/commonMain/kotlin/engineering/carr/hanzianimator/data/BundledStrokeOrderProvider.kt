package engineering.carr.hanzianimator.data

import engineering.carr.hanzianimator.core.StrokeOrder
import engineering.carr.hanzianimator.core.gunzip
import engineering.carr.hanzianimator.internal.resources.Res
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.random.Random
import org.jetbrains.compose.resources.ExperimentalResourceApi

/**
 * A [StrokeOrderProvider] backed by the stroke data bundled with the library
 * (the full Hanzi Writer dataset, ~9,574 characters).
 *
 * Data is stored as 256 gzip-compressed JSON shards keyed by
 * `codePoint % 256`. Shards are fetched and decoded lazily and kept in a small
 * LRU cache ([shardCacheSize] shards), so on web only the shards actually used
 * are downloaded. All access is safe to share across coroutines.
 */
@OptIn(ExperimentalResourceApi::class)
public class BundledStrokeOrderProvider(
    private val shardCacheSize: Int = DEFAULT_SHARD_CACHE_SIZE
) : StrokeOrderProvider {

    private val json = Json { ignoreUnknownKeys = true }
    private val shardMutex = Mutex()
    // Access-ordered LRU of shard id -> parsed shard object.
    private val shardCache = LinkedHashMap<Int, JsonObject>()

    private val indexMutex = Mutex()
    private var indexCache: List<String>? = null

    override suspend fun strokeOrder(character: String): StrokeOrder? {
        val key = firstCharacterKey(character) ?: return null
        val shard = loadShard(codePointOf(key) % SHARD_COUNT)
        val entry = shard[key] as? JsonObject ?: return null
        return StrokeOrder.fromJsonObject(key, entry)
    }

    /** All characters available from the bundled dataset, in dataset order. */
    public suspend fun availableCharacters(): List<String> = loadIndex()

    /** Returns a random available character, or `null` if the dataset is empty. */
    public suspend fun randomCharacter(random: Random = Random.Default): String? {
        val characters = loadIndex()
        return if (characters.isEmpty()) null else characters[random.nextInt(characters.size)]
    }

    private suspend fun loadShard(shardId: Int): JsonObject {
        shardMutex.withLock {
            shardCache.remove(shardId)?.let { cached ->
                shardCache[shardId] = cached // move to most-recently-used
                return cached
            }
        }
        val bytes = Res.readBytes("files/strokes/$shardId.json.gz")
        val text = gunzip(bytes).decodeToString()
        val parsed = json.parseToJsonElement(text) as JsonObject
        shardMutex.withLock {
            shardCache.remove(shardId)
            shardCache[shardId] = parsed
            while (shardCache.size > shardCacheSize) {
                val eldest = shardCache.keys.first()
                shardCache.remove(eldest)
            }
        }
        return parsed
    }

    private suspend fun loadIndex(): List<String> {
        indexCache?.let { return it }
        val bytes = Res.readBytes("files/strokes/index.txt.gz")
        val text = gunzip(bytes).decodeToString()
        val characters = splitCodePoints(text)
        indexMutex.withLock {
            indexCache?.let { return it }
            indexCache = characters
        }
        return characters
    }

    public companion object {
        /** Number of shards the dataset is split into. */
        internal const val SHARD_COUNT: Int = 256

        /** Default number of decoded shards kept in memory. */
        public const val DEFAULT_SHARD_CACHE_SIZE: Int = 8

        /**
         * A process-wide shared instance with a stable identity, suitable as a
         * default in composables (its cache is shared across all callers).
         */
        public val Default: BundledStrokeOrderProvider by lazy { BundledStrokeOrderProvider() }
    }
}

/** The single-character key for [text], or `null` when empty. */
internal fun firstCharacterKey(text: String): String? {
    if (text.isEmpty()) return null
    val c0 = text[0]
    return if (c0.isHighSurrogate() && text.length >= 2 && text[1].isLowSurrogate()) {
        text.substring(0, 2)
    } else {
        text.substring(0, 1)
    }
}

/** The Unicode code point of a single-character [key]. */
internal fun codePointOf(key: String): Int {
    val c0 = key[0]
    return if (c0.isHighSurrogate() && key.length >= 2 && key[1].isLowSurrogate()) {
        0x10000 + ((c0.code - 0xD800) shl 10) + (key[1].code - 0xDC00)
    } else {
        c0.code
    }
}

/** Splits [text] into a list of single-character strings, one per code point. */
internal fun splitCodePoints(text: String): List<String> {
    val result = ArrayList<String>()
    var i = 0
    while (i < text.length) {
        val c = text[i]
        if (c.isHighSurrogate() && i + 1 < text.length && text[i + 1].isLowSurrogate()) {
            result.add(text.substring(i, i + 2))
            i += 2
        } else {
            result.add(text.substring(i, i + 1))
            i += 1
        }
    }
    return result
}

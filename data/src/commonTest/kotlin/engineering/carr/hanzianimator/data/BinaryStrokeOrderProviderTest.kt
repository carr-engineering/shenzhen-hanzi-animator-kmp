package engineering.carr.hanzianimator.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BinaryStrokeOrderProviderTest {
    private val emptyArchive = byteArrayOf(72, 90, 66, 49, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)

    @Test
    fun sameBlockSharesOneReadAndDifferentBlocksProgressIndependently() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val other = CompletableDeferred<Unit>()
        val calls = kotlinx.coroutines.sync.Mutex()
        val keys = mutableListOf<String>()
        val source = StrokeDataSource { key ->
            calls.lock(); try { keys.add(key) } finally { calls.unlock() }
            if (key == "0.hzb") { entered.complete(Unit); release.await() }
            else { other.complete(Unit) }
            emptyArchive
        }
        val provider = BinaryStrokeOrderProvider(source, this, shardCount = 2)
        val first = async { provider.strokeOrder("木") }
        entered.await()
        val same = async { provider.strokeOrder("本") }
        val different = async { provider.strokeOrder("中") }
        other.await()
        assertNull(different.await())
        release.complete(Unit)
        assertNull(first.await())
        assertNull(same.await())
        assertEquals(1, keys.count { it == "0.hzb" })
        assertEquals(1, keys.count { it == "1.hzb" })
    }

    @Test
    fun emptyAndMalformedCharactersAvoidIO() = runTest {
        var reads = 0
        val provider = BinaryStrokeOrderProvider(StrokeDataSource { reads++; emptyArchive }, this)
        assertNull(provider.strokeOrder(""))
        assertNull(provider.strokeOrder("\ud800"))
        assertNull(provider.strokeOrder("\udc00"))
        assertEquals(0, reads)
    }

    @Test
    fun cacheBudgetEvictsAndClearCacheDropsRetainedArchives() = runTest {
        val calls = mutableListOf<String>()
        val provider = BinaryStrokeOrderProvider(StrokeDataSource { key -> calls.add(key); emptyArchive }, this,
            StrokeDataCacheConfig(decodedBytes = 20, parsedCharacters = 0), shardCount = 2)
        provider.strokeOrder("中")
        provider.strokeOrder("木")
        provider.strokeOrder("中")
        assertEquals(3, calls.size)
        provider.clearCache()
        provider.strokeOrder("中")
        assertEquals(4, calls.size)
    }

    @Test
    fun sourceFailureAllowsRetryWithoutCancellingOwnerScope() = runTest {
        var reads = 0
        val provider = BinaryStrokeOrderProvider(StrokeDataSource {
            reads++
            if (reads == 1) error("temporary load failure")
            emptyArchive
        }, this)
        assertFailsWith<IllegalStateException> { provider.strokeOrder("木") }
        assertTrue(isActive)
        assertNull(provider.strokeOrder("木"))
        assertEquals(2, reads)
    }

    @Test
    fun rejectsNegativeBudgetsAndInvalidShardCounts() {
        assertFailsWith<IllegalArgumentException> { StrokeDataCacheConfig(decodedBytes = -1) }
        assertFailsWith<IllegalArgumentException> { StrokeDataCacheConfig(parsedCharacters = -1) }
        assertTrue(StrokeDataCacheConfig.Web.decodedBytes < StrokeDataCacheConfig.Native.decodedBytes)
    }
}

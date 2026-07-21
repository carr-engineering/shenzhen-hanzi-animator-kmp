package engineering.carr.hanzianimator.data

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BundledDataIntegrityTest {
    @Test
    fun indexContainsTheFullDataset() = runTest {
        val provider = BundledStrokeOrderProvider()
        val characters = provider.availableCharacters()
        assertEquals(9574, characters.size)
        assertEquals(9574, characters.toSet().size, "characters should be unique")
    }

    @Test
    fun everyBundledCharacterParses() = runTest {
        // A large cache so each of the 256 shards is decoded at most once.
        val provider = BundledStrokeOrderProvider(shardCacheSize = 256)
        val characters = provider.availableCharacters()

        var parsed = 0
        for (character in characters) {
            val order = provider.strokeOrder(character)
            assertNotNull(order, "no stroke order for $character")
            assertTrue(order.strokeCount > 0, "empty stroke order for $character")
            parsed++
        }
        assertEquals(9574, parsed)
    }

    @Test
    fun parsesKnownCharacterWithExpectedStrokeCount() = runTest {
        val provider = BundledStrokeOrderProvider()
        // 中 is written with four strokes; its medians are present.
        val order = assertNotNull(provider.strokeOrder("中"))
        assertEquals("中", order.character)
        assertEquals(4, order.strokeCount)
        assertTrue(order.strokes.all { it.hasDrawableMedian })
    }

    @Test
    fun returnsNullForUnknownCharacter() = runTest {
        val provider = BundledStrokeOrderProvider()
        assertEquals(null, provider.strokeOrder("Z"))
    }

    @Test
    fun randomCharacterIsAlwaysResolvable() = runTest {
        val provider = BundledStrokeOrderProvider()
        repeat(20) {
            val character = assertNotNull(provider.randomCharacter())
            assertNotNull(provider.strokeOrder(character))
        }
    }
}

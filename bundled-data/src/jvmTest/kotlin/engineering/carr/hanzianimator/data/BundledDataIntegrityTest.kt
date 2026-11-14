package engineering.carr.hanzianimator.data

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BundledDataIntegrityTest {
    @Test
    fun indexContainsTheFullDataset() = runTest {
        val provider = BundledStrokeOrderProvider(scope = this)
        val characters = provider.availableCharacters()
        assertEquals(9574, characters.size)
        assertEquals(9574, characters.toSet().size, "characters should be unique")
    }

    @Test
    fun everyBundledCharacterParses() = runTest {
        // The decoded full archive is retained once; individual characters are decoded lazily.
        val provider = BundledStrokeOrderProvider(scope = this)
        val characters = provider.availableCharacters()

        var parsed = 0
        var maxComponents = 0
        var partiallyMapped = 0
        for (character in characters) {
            val order = provider.strokeOrder(character)
            assertNotNull(order, "no stroke order for $character")
            assertTrue(order.strokeCount > 0, "empty stroke order for $character")
            val components = assertNotNull(order.components, "missing component metadata for $character")
            assertTrue(order.strokes.all { it.componentIndex == null || it.componentIndex in components.indices })
            assertEquals(components.indices.toSet(), order.strokes.mapNotNull { it.componentIndex }.toSet())
            maxComponents = maxOf(maxComponents, order.componentCount)
            if (order.strokes.any { it.componentIndex == null }) partiallyMapped++
            parsed++
        }
        assertEquals(9574, parsed)
        assertEquals(6, maxComponents)
        assertEquals(483, partiallyMapped)
    }

    @Test
    fun knownCharactersHaveExpectedComponentOccurrences() = runTest {
        val provider = BundledStrokeOrderProvider(scope = this)
        val forest = assertNotNull(provider.strokeOrder("森"))
        assertEquals(listOf("木", "木", "木"), forest.components)
        assertEquals(List(4) { 0 } + List(4) { 1 } + List(4) { 2 }, forest.strokes.map { it.componentIndex })
        val listen = assertNotNull(provider.strokeOrder("聽"))
        assertEquals(listOf("耳", "王", "十", "罒", "一", "心"), listen.components)
        assertEquals(6, listen.componentCount)
        val win = assertNotNull(provider.strokeOrder("赢"))
        assertEquals(17, win.strokeCount)
        assertEquals(listOf("吂", "⺼", "贝"), win.components)
        assertTrue(win.strokes.takeLast(3).all { it.componentIndex == null })
    }

    @Test
    fun parsesKnownCharacterWithExpectedStrokeCount() = runTest {
        val provider = BundledStrokeOrderProvider(scope = this)
        // 中 is written with four strokes; its medians are present.
        val order = assertNotNull(provider.strokeOrder("中"))
        assertEquals("中", order.character)
        assertEquals(4, order.strokeCount)
        assertTrue(order.strokes.all { it.hasDrawableMedian })
    }

    @Test
    fun returnsNullForUnknownCharacter() = runTest {
        val provider = BundledStrokeOrderProvider(scope = this)
        assertEquals(null, provider.strokeOrder("Z"))
    }

    @Test
    fun randomCharacterIsAlwaysResolvable() = runTest {
        val provider = BundledStrokeOrderProvider(scope = this)
        repeat(20) {
            val character = assertNotNull(provider.randomCharacter())
            assertNotNull(provider.strokeOrder(character))
        }
    }
}

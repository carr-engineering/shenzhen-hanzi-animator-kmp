import engineering.carr.hanzianimator.data.BundledStrokeOrderProvider
import kotlinx.coroutines.*
import kotlin.test.*

class PublishedBundledDataTest {
    @Test fun readPublishedClasspathDataAndFullLicenses() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val provider = BundledStrokeOrderProvider(scope)
            assertEquals(9574, provider.availableCharacters().size)
            val first = assertNotNull(provider.strokeOrder("永"))
            assertTrue(first.supportsPractice)
            assertSame(first, provider.strokeOrder("永"))
            provider.clearCache()
            assertEquals(first, provider.strokeOrder("永"))
            for (name in listOf("Arphic-Public-License.txt", "makemeahanzi-COPYING.txt", "makemeahanzi-LGPL.txt", "makemeahanzi-GPL.txt")) {
                val text = assertNotNull(javaClass.classLoader.getResourceAsStream("licenses/$name"), name)
                    .bufferedReader().use { it.readText() }
                assertTrue(text.length > 100, "$name must contain its complete notice")
            }
        } finally { scope.cancel() }
    }
}

package engineering.carr.hanzianimator.codec

import engineering.carr.hanzianimator.json.StrokeOrderJson
import java.io.File
import java.util.zip.GZIPInputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.*

/** Runs against the committed source fixture and generated, packaged Android archive. */
class CorpusParityTest {
    @Test fun completeCorpusMatchesValidatedSourceIncludingComponents() {
        val root = File(requireNotNull(System.getProperty("hanzi.repositoryRoot")))
        val source = File(root,"tools/fixtures/source-data.json.gz")
        val packed = File(root,"bundled-data/src/androidMain/resources/strokes/all.hzb")
        val json = GZIPInputStream(source.inputStream()).bufferedReader().use { it.readText() }
        val objects = Json.parseToJsonElement(json) as JsonObject
        val archive = BinaryStrokeArchive(GZIPInputStream(packed.inputStream()).use { it.readBytes() })
        assertEquals(9574, objects.size)
        assertEquals(objects.keys, archive.characters().toSet())
        for ((character, obj) in objects) {
            val expected = StrokeOrderJson.fromJsonObject(character, obj as JsonObject)
            val actual = requireNotNull(archive.strokeOrder(character))
            assertEquals(expected, actual, "Lossless corpus mismatch for $character")
        }
    }
}

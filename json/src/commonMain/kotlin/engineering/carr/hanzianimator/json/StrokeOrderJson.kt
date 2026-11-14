package engineering.carr.hanzianimator.json

import engineering.carr.hanzianimator.core.GlyphPoint
import engineering.carr.hanzianimator.core.OutlineCommand
import engineering.carr.hanzianimator.core.Stroke
import engineering.carr.hanzianimator.core.StrokeOrder
import engineering.carr.hanzianimator.core.StrokeOutline
import kotlinx.serialization.json.*

/** Optional importer for validated Hanzi Writer JSON; malformed input returns null. */
public object StrokeOrderJson {
    public fun parse(character: String, hanziWriterJson: String): StrokeOrder? = try {
        fromJsonObject(character, Json.parseToJsonElement(hanziWriterJson) as? JsonObject ?: error("Expected object"))
    } catch (_: IllegalArgumentException) { null } catch (_: IllegalStateException) { null }

    public fun fromJsonObject(character: String, obj: JsonObject): StrokeOrder {
        val paths = array(obj["strokes"]).also { require(it.isNotEmpty() && it.size <= 256) }
        val outlines = paths.map { parseOutline(string(it)) }
        val components = obj["components"]?.let { array(it).map(::string).also { labels -> require(labels.all { it.isNotBlank() }) } }
        val memberships = obj["strokeComponents"]?.let { array(it).also { values -> require(values.size == paths.size); require(components != null) } }
        val radicals = obj["radStrokes"]?.let { array(it).map(::integer).also { values -> require(values.all { it in paths.indices }) }.toSet() }.orEmpty()
        val medians = obj["medians"]?.let { array(it).also { values -> require(values.size <= paths.size) } }
        val strokes = outlines.mapIndexed { index, outline ->
            val median = medians?.getOrNull(index)?.let { array(it).map { point ->
                val pair = array(point); require(pair.size == 2)
                GlyphPoint(number(pair[0]), StrokeOrder.GLYPH_Y_BASE - number(pair[1]))
            } }.orEmpty()
            val component = memberships?.get(index)?.let { if (it == JsonNull) null else integer(it).also { value -> require(components != null && value in components.indices) } }
            Stroke(index, outline, median, index in radicals, component)
        }
        return StrokeOrder(character, strokes, components)
    }
    private fun array(element: JsonElement?): JsonArray = element as? JsonArray ?: error("Expected array")
    private fun string(element: JsonElement): String = (element as? JsonPrimitive)?.takeIf { it.isString }?.content ?: error("Expected string")
    private fun integer(element: JsonElement): Int = (element as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull ?: error("Expected integer")
    private fun number(element: JsonElement): Float = (element as? JsonPrimitive)?.takeIf { !it.isString }?.floatOrNull?.takeIf { it.isFinite() } ?: error("Expected finite number")

    /** Accepts absolute M/L/Q/C/Z commands; implicit pairs after M become L. */
    public fun parseOutline(path: String): StrokeOutline {
        require(path.length <= 1_000_000)
        val tokenPattern = Regex("[MLQCZ]|[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?")
        val tokens = mutableListOf<String>()
        var end = 0
        for (match in tokenPattern.findAll(path)) {
            require(path.substring(end, match.range.first).all { it.isWhitespace() || it == ',' }) { "Unsupported SVG syntax" }
            tokens += match.value
            end = match.range.last + 1
        }
        require(path.substring(end).all { it.isWhitespace() || it == ',' })
        require(tokens.isNotEmpty())
        val builder = StrokeOutline.Builder()
        var cursor = 0
        var current: OutlineCommand? = null
        var commands = 0
        while (cursor < tokens.size) {
            val token = tokens[cursor]
            if (token.length == 1 && token[0] in "MLQCZ") {
                current = when (token) { "M" -> OutlineCommand.MOVE; "L" -> OutlineCommand.LINE; "Q" -> OutlineCommand.QUAD; "C" -> OutlineCommand.CUBIC; else -> OutlineCommand.CLOSE }
                cursor++
            }
            val command = current ?: error("Missing command")
            if (command == OutlineCommand.CLOSE) {
                builder.close(); current = null
            } else {
                require(cursor + command.coordinateCount <= tokens.size)
                val coords = FloatArray(command.coordinateCount) { offset ->
                    val value = tokens[cursor + offset].toFloatOrNull()?.takeIf { it.isFinite() } ?: error("Invalid coordinate")
                    if (offset % 2 == 1) StrokeOrder.GLYPH_Y_BASE - value else value
                }
                builder.command(command, *coords)
                cursor += command.coordinateCount
                if (command == OutlineCommand.MOVE) current = OutlineCommand.LINE
            }
            require(++commands <= 65536)
        }
        return builder.build()
    }
}

package engineering.carr.hanzianimator.core

/** A finite point in normalized glyph space: top-left origin and y increasing downward. */
public data class GlyphPoint(val x: Float, val y: Float) {
    init { require(x.isFinite() && y.isFinite()) { "Glyph coordinates must be finite" } }
}

/** Absolute path commands; coordinate pairs are in normalized, y-down glyph space. */
public enum class OutlineCommand(public val coordinateCount: Int) {
    MOVE(2), LINE(2), QUAD(4), CUBIC(6), CLOSE(0)
}

/** Immutable command buffer; inputs and returned arrays cannot mutate the outline. */
public class StrokeOutline(commands: IntArray, coordinates: FloatArray) {
    private val commands = commands.copyOf()
    private val coordinates = coordinates.copyOf()
    public val commandCount: Int get() = commands.size
    public val coordinateCount: Int get() = coordinates.size
    init {
        require(commands.isEmpty() || commands[0] == OutlineCommand.MOVE.ordinal) { "Outline must start with MOVE" }
        require(commands.all { it in OutlineCommand.entries.indices }) { "Unsupported outline command" }
        require(commands.sumOf { OutlineCommand.entries[it].coordinateCount } == coordinates.size) { "Coordinate count mismatch" }
        require(coordinates.all { it.isFinite() }) { "Outline coordinates must be finite" }
    }
    public fun commandAt(index: Int): OutlineCommand = OutlineCommand.entries[commands[index]]
    public fun coordinateAt(index: Int): Float = coordinates[index]
    public fun copyCommands(): IntArray = commands.copyOf()
    public fun copyCoordinates(): FloatArray = coordinates.copyOf()
    override fun equals(other: Any?): Boolean = other is StrokeOutline && commands.contentEquals(other.commands) && coordinates.contentEquals(other.coordinates)
    override fun hashCode(): Int = 31 * commands.contentHashCode() + coordinates.contentHashCode()
    public class Builder {
        private val commands = mutableListOf<Int>()
        private val coordinates = mutableListOf<Float>()
        public fun command(command: OutlineCommand, vararg coordinates: Float): Builder {
            require(coordinates.size == command.coordinateCount)
            commands += command.ordinal
            this.coordinates.addAll(coordinates.toList())
            return this
        }
        public fun moveTo(x: Float, y: Float): Builder = command(OutlineCommand.MOVE, x, y)
        public fun lineTo(x: Float, y: Float): Builder = command(OutlineCommand.LINE, x, y)
        public fun quadTo(cx: Float, cy: Float, x: Float, y: Float): Builder = command(OutlineCommand.QUAD, cx, cy, x, y)
        public fun cubicTo(cx1: Float, cy1: Float, cx2: Float, cy2: Float, x: Float, y: Float): Builder = command(OutlineCommand.CUBIC, cx1, cy1, cx2, cy2, x, y)
        public fun close(): Builder = command(OutlineCommand.CLOSE)
        public fun build(): StrokeOutline = StrokeOutline(commands.toIntArray(), coordinates.toFloatArray())
    }
    public companion object { public val EMPTY: StrokeOutline = StrokeOutline(intArrayOf(), floatArrayOf()) }
}

public class Stroke(
    public val index: Int,
    public val outline: StrokeOutline,
    median: List<GlyphPoint>,
    public val isRadical: Boolean,
    public val componentIndex: Int? = null
) {
    public val median: List<GlyphPoint> = FrozenList(median)
    init { require(index >= 0); require(componentIndex == null || componentIndex >= 0) }
    public val hasDrawableMedian: Boolean get() = median.size >= 2
    public val hasRecognizableMedian: Boolean get() = median.size >= 2 && median.any { distanceBetween(it, median.first()) > 1f }
    override fun equals(other: Any?): Boolean = other is Stroke && index == other.index && outline == other.outline && median == other.median && isRadical == other.isRadical && componentIndex == other.componentIndex
    override fun hashCode(): Int = (((index * 31 + outline.hashCode()) * 31 + median.hashCode()) * 31 + isRadical.hashCode()) * 31 + (componentIndex ?: -1)
}

/** Immutable render-ready character. Both outlines and medians are already y-down. */
public class StrokeOrder(public val character: String, strokes: List<Stroke>, components: List<String>? = null) {
    public val strokes: List<Stroke> = FrozenList(strokes)
    public val components: List<String>? = components?.let(::FrozenList)
    init {
        require(character.isNotBlank()) { "Character must not be blank" }
        require(strokes.withIndex().all { (index, stroke) -> stroke.index == index }) { "Stroke indexes must be canonical" }
        require(components == null || components.all { it.isNotBlank() }) { "Component labels must not be blank" }
        require(strokes.all { it.componentIndex == null || (components != null && it.componentIndex in components.indices) }) { "Invalid component index" }
    }
    public val strokeCount: Int get() = strokes.size
    public val componentCount: Int get() = components?.size ?: 0
    public val supportsPractice: Boolean get() = strokes.isNotEmpty() && strokes.all { it.hasRecognizableMedian }
    public val radicalStrokeIndexes: Set<Int> get() = strokes.filter { it.isRadical }.map { it.index }.toSet()
    override fun equals(other: Any?): Boolean = other is StrokeOrder && character == other.character && strokes == other.strokes && components == other.components
    override fun hashCode(): Int = (character.hashCode() * 31 + strokes.hashCode()) * 31 + (components?.hashCode() ?: 0)
    public companion object {
        public const val GLYPH_SIZE: Float = 1024f
        public const val GLYPH_Y_BASE: Float = 900f
    }
}

private class FrozenList<T>(source: List<T>) : AbstractList<T>() {
    private val values = source.toList()
    override val size: Int get() = values.size
    override fun get(index: Int): T = values[index]
}

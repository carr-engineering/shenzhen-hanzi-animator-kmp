@file:OptIn(ExperimentalJsExport::class)

import engineering.carr.hanzianimator.codec.BinaryStrokeArchive
import engineering.carr.hanzianimator.core.*
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/** Returns only JS primitives and arrays; the private model stays immutable in the shared engine. */
private fun orderView(order: StrokeOrder): dynamic {
    val value = js("({})")
    val descriptor = js("({})")
    descriptor.value = order
    js("Object").defineProperty(value, "__order", descriptor)
    value.character = order.character
    value.supportsPractice = order.supportsPractice
    value.components = order.components?.toTypedArray()
    value.strokes = order.strokes.map { stroke ->
        val view = js("({})")
        view.commands = stroke.outline.copyCommands().toTypedArray()
        view.coordinates = stroke.outline.copyCoordinates().toTypedArray()
        view.median = stroke.median.map { arrayOf(it.x, it.y) }.toTypedArray()
        view.isRadical = stroke.isRadical
        view.componentIndex = stroke.componentIndex
        view
    }.toTypedArray()
    return value
}

@JsExport
public fun decodeArchive(bytes: ByteArray): dynamic {
    val archive = BinaryStrokeArchive(bytes)
    val view = js("({})")
    view.characters = archive.characters().toTypedArray()
    view.byteLength = archive.decodedByteSize
    view.strokeOrder = { character: String -> archive.strokeOrder(character)?.let(::orderView) }
    return view
}

private fun number(options: dynamic, key: String, default: Double): Double =
    ((if (options == null) null else options[key]) as? Number)?.toDouble() ?: default
private fun optionalNumber(options: dynamic, key: String): Double? = ((if (options == null) null else options[key]) as? Number)?.toDouble()
private fun boolean(options: dynamic, key: String, default: Boolean): Boolean = ((if (options == null) null else options[key]) as? Boolean) ?: default

private fun integer(value: dynamic): Int {
    val number = (value as? Number)?.toDouble() ?: error("Integer value required")
    require(number.isFinite() && number % 1.0 == 0.0 && number >= Int.MIN_VALUE.toDouble() && number <= Int.MAX_VALUE.toDouble()) { "Integer value required" }
    return number.toInt()
}

private fun modelFromView(view: dynamic): StrokeOrder {
    val source = view.strokes
    val strokeCount = integer(source.length)
    require(strokeCount in 1..1024) { "Invalid stroke count" }
    val components: List<String>? = if (view.components == null) null else run {
        val labels = view.components
        val length = integer(labels.length)
        require(length in 0..1024)
        List(length) { labels[it] as String }
    }
    val strokes = List(strokeCount) { index ->
        val stroke = source[index]
        val commands = stroke.commands
        val coordinates = stroke.coordinates
        val median = stroke.median
        val commandCount = integer(commands.length)
        val coordinateCount = integer(coordinates.length)
        val medianCount = integer(median.length)
        require(commandCount in 0..65536 && coordinateCount in 0..393216 && medianCount in 0..65536) { "Invalid stroke buffers" }
        Stroke(index,
            StrokeOutline(IntArray(commandCount) { integer(commands[it]) }, FloatArray(coordinateCount) { (coordinates[it] as Number).toFloat() }),
            List(medianCount) { GlyphPoint((median[it][0] as Number).toFloat(), (median[it][1] as Number).toFloat()) },
            stroke.isRadical as Boolean,
            if (stroke.componentIndex == null) null else integer(stroke.componentIndex)
        )
    }
    return StrokeOrder(view.character as String, strokes, components)
}

@JsExport
public fun createStrokeSession(order: dynamic, options: dynamic): dynamic {
    val model = order.__order as? StrokeOrder ?: modelFromView(order)
    val animation = options.animation
    val recognition = options.recognition
    val session = StrokeSession(model, StrokeAnimationConfig(
        strokeDurationMillis = optionalNumber(animation, "strokeDurationMillis")?.toLong(),
        minStrokeDurationMillis = number(animation, "minStrokeDurationMillis", 280.0).toLong(),
        maxStrokeDurationMillis = number(animation, "maxStrokeDurationMillis", 850.0).toLong(),
        speedMultiplier = number(animation, "speedMultiplier", 1.0).toFloat(),
        strokeSpeedViewportUnitsPerSecond = optionalNumber(animation, "strokeSpeedViewportUnitsPerSecond")?.toFloat(),
        delayBetweenStrokesMillis = number(animation, "delayBetweenStrokesMillis", 120.0).toLong(),
        loop = boolean(animation, "loop", false)
    ), StrokeRecognitionConfig(
        leniency = number(recognition, "leniency", 1.0).toFloat(),
        startTolerance = number(recognition, "startTolerance", 250.0).toFloat(),
        endTolerance = number(recognition, "endTolerance", 250.0).toFloat(),
        averageDistanceTolerance = number(recognition, "averageDistanceTolerance", 350.0).toFloat(),
        outlineVisibleDistanceMultiplier = number(recognition, "outlineVisibleDistanceMultiplier", 0.5).toFloat(),
        maxDistanceTolerance = number(recognition, "maxDistanceTolerance", 260.0).toFloat(),
        coverageDistanceTolerance = number(recognition, "coverageDistanceTolerance", 150.0).toFloat(),
        minCoverage = number(recognition, "minCoverage", 0.6).toFloat(),
        minLengthRatio = number(recognition, "minLengthRatio", 0.35).toFloat(),
        maxLengthRatio = number(recognition, "maxLengthRatio", 2.35).toFloat(),
        frechetTolerance = number(recognition, "frechetTolerance", 0.4).toFloat(),
        sampleCount = number(recognition, "sampleCount", 30.0).toInt(),
        laterStrokeDistanceAdvantage = number(recognition, "laterStrokeDistanceAdvantage", 8.0).toFloat()
    ), number(options, "hintAfterMistakes", 2.0).toInt())
    val view = js("({})")
    view.snapshot = {
        val state = js("({})")
        state.mode = session.mode.name
        state.playing = session.isPlaying
        state.supportsPractice = model.supportsPractice
        state.elapsedMillis = session.animationElapsedMillis.toDouble()
        state.currentStroke = session.currentStroke
        state.progress = Array(model.strokeCount) { index ->
            if (session.mode == StrokeSessionMode.Animating) session.animationFrame.progressByStroke.getOrElse(index) { 0f }
            else if (session.isStrokeCompleted(index)) 1f else 0f
        }
        state.outlineVisible = session.outlineVisible
        state.hintVisible = session.hintVisible
        state.complete = session.isQuizComplete
        state.totalMistakes = session.totalMistakes
        state.mistakesPerStroke = session.quizSummary()?.mistakesPerStroke?.toTypedArray() ?: emptyArray<Int>()
        state
    }
    view.play = { session.play() }
    view.pause = { session.pause() }
    view.advance = { elapsedMillis: Double -> session.advanceAnimation(elapsedMillis.toLong()) }
    view.stepForward = { session.nextStroke() }
    view.stepBackward = { session.previousStroke() }
    view.revealAll = { session.showFullCharacter() }
    view.reset = { session.reset() }
    view.startQuiz = { session.startQuiz() }
    view.showHint = { session.showHint() }
    view.setOutline = { visible: Boolean -> session.outlineVisible = visible }
    view.submitStroke = { coordinates: Array<Double> ->
        require(coordinates.size % 2 == 0)
        val result = session.submitStroke(coordinates.asList().chunked(2).map { GlyphPoint(it[0].toFloat(), it[1].toFloat()) })
        val output = js("({})")
        output.accepted = result.accepted
        output.status = result.status.name
        output.complete = session.isQuizComplete
        output.hintVisible = session.hintVisible
        output.totalMistakes = session.totalMistakes
        output
    }
    return view
}

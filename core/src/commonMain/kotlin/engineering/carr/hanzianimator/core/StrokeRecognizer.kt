package engineering.carr.hanzianimator.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Tolerances that control how forgiving stroke recognition is.
 *
 * All distances are in glyph units ([StrokeOrder.GLYPH_SIZE] square). [leniency]
 * scales the whole grader: values above `1f` accept looser strokes. Defaults are
 * tuned for finger/mouse input against Hanzi Writer medians.
 */
public data class StrokeRecognitionConfig(
    val leniency: Float = 1f,
    val startTolerance: Float = 250f,
    val endTolerance: Float = 250f,
    val averageDistanceTolerance: Float = 350f,
    val outlineVisibleDistanceMultiplier: Float = 0.5f,
    val maxDistanceTolerance: Float = 260f,
    val coverageDistanceTolerance: Float = 150f,
    val minCoverage: Float = 0.60f,
    val minLengthRatio: Float = 0.35f,
    val maxLengthRatio: Float = 2.35f,
    val frechetTolerance: Float = 0.40f,
    val sampleCount: Int = 30,
    val laterStrokeDistanceAdvantage: Float = 8f
) {
    init {
        require(leniency.isFinite() && leniency > 0f)
        require(listOf(startTolerance, endTolerance, averageDistanceTolerance, maxDistanceTolerance, coverageDistanceTolerance, frechetTolerance, laterStrokeDistanceAdvantage).all { it.isFinite() && it >= 0f })
        require(outlineVisibleDistanceMultiplier.isFinite() && outlineVisibleDistanceMultiplier > 0f)
        require(minCoverage.isFinite() && minCoverage in 0f..1f)
        require(minLengthRatio.isFinite() && minLengthRatio > 0f && maxLengthRatio.isFinite() && maxLengthRatio >= minLengthRatio)
        require(sampleCount in 8..512)
    }
}

/** Outcome of matching a user's traced stroke against an expected median. */
public enum class StrokeRecognitionStatus {
    /** The trace matches the expected stroke. */
    Accepted,

    /** The trace had fewer than two distinct points. */
    EmptyInput,

    /** The expected stroke has no usable median to match against. */
    MissingExpectedMedian,

    /** The trace is too short relative to the expected stroke. */
    TooShort,

    /** The trace matches the expected stroke but was drawn in reverse. */
    Reversed,

    /** The trace starts too far from the expected start point. */
    WrongStart,

    /** The trace ends too far from the expected end point. */
    WrongEnd,

    /** The trace matches a later stroke more closely — drawn out of order. */
    WrongStrokeOrder,

    /** The trace is far too long relative to the expected stroke. */
    BadLength,

    /** The trace strays too far from the expected path or shape. */
    OffPath
}

/**
 * The result of [StrokeRecognizer.recognize], carrying the [status] plus the
 * geometric measurements that produced it (useful for debugging or tuning).
 */
public data class StrokeRecognitionResult(
    val status: StrokeRecognitionStatus,
    val startDistance: Float = Float.POSITIVE_INFINITY,
    val endDistance: Float = Float.POSITIVE_INFINITY,
    val averageDistance: Float = Float.POSITIVE_INFINITY,
    val maxDistance: Float = Float.POSITIVE_INFINITY,
    val lengthRatio: Float = 0f,
    val coverage: Float = 0f
) {
    /** Convenience for `status == StrokeRecognitionStatus.Accepted`. */
    public val accepted: Boolean
        get() = status == StrokeRecognitionStatus.Accepted
}

/** Grades a traced stroke against an expected median or a full [StrokeOrder]. */
public object StrokeRecognizer {
    /**
     * Matches [userPoints] against a single [expectedMedian] (in glyph space),
     * detecting reversed strokes. Use the [StrokeOrder] overload for guided
     * quizzing, which also flags strokes drawn out of order.
     */
    public fun recognize(
        userPoints: List<GlyphPoint>,
        expectedMedian: List<GlyphPoint>,
        config: StrokeRecognitionConfig = StrokeRecognitionConfig()
    ): StrokeRecognitionResult =
        matchStroke(
            userPoints = userPoints,
            expectedMedian = expectedMedian,
            outlineVisible = false,
            config = config,
            checkBackwards = true
        )

    /**
     * Matches [userPoints] against the stroke at [expectedStrokeIndex] in
     * [order]. When the trace also matches a later stroke appreciably better,
     * the result is downgraded to [StrokeRecognitionStatus.WrongStrokeOrder].
     *
     * @param outlineVisible relaxes the average-distance tolerance, for when the
     *   character outline is shown as a tracing guide.
     */
    public fun recognize(
        userPoints: List<GlyphPoint>,
        order: StrokeOrder,
        expectedStrokeIndex: Int,
        outlineVisible: Boolean,
        config: StrokeRecognitionConfig = StrokeRecognitionConfig()
    ): StrokeRecognitionResult {
        val expected = order.strokes.getOrNull(expectedStrokeIndex)
            ?: return StrokeRecognitionResult(StrokeRecognitionStatus.MissingExpectedMedian)
        val result = matchStroke(
            userPoints = userPoints,
            expectedMedian = expected.median,
            outlineVisible = outlineVisible,
            config = config,
            checkBackwards = true
        )
        if (!result.accepted) return result

        val laterBest = order.strokes.asSequence()
            .drop(expectedStrokeIndex + 1)
            .map { stroke ->
                matchStroke(
                    userPoints = userPoints,
                    expectedMedian = stroke.median,
                    outlineVisible = outlineVisible,
                    config = config,
                    checkBackwards = false
                )
            }
            .filter { it.accepted }
            .minByOrNull { it.averageDistance }
        return if (
            laterBest != null &&
            laterBest.averageDistance + config.laterStrokeDistanceAdvantage < result.averageDistance
        ) {
            result.copy(status = StrokeRecognitionStatus.WrongStrokeOrder)
        } else {
            result
        }
    }

    private fun matchStroke(
        userPoints: List<GlyphPoint>,
        expectedMedian: List<GlyphPoint>,
        outlineVisible: Boolean,
        config: StrokeRecognitionConfig,
        checkBackwards: Boolean
    ): StrokeRecognitionResult {
        val input = userPoints.deduplicateAdjacent()
        val expected = expectedMedian.deduplicateAdjacent()
        if (input.size < 2) return StrokeRecognitionResult(StrokeRecognitionStatus.EmptyInput)
        if (expected.size < 2) return StrokeRecognitionResult(StrokeRecognitionStatus.MissingExpectedMedian)
        if (checkBackwards) {
            val forward = matchStroke(
                userPoints = input,
                expectedMedian = expectedMedian,
                outlineVisible = outlineVisible,
                config = config,
                checkBackwards = false
            )
            if (forward.accepted) return forward
            val backwards = matchStroke(
                userPoints = input.asReversed(),
                expectedMedian = expectedMedian,
                outlineVisible = outlineVisible,
                config = config,
                checkBackwards = false
            )
            return if (backwards.accepted) {
                forward.copy(status = StrokeRecognitionStatus.Reversed)
            } else {
                forward
            }
        }

        val leniency = config.leniency
        val startDistance = distanceBetween(input.first(), expected.first())
        val endDistance = distanceBetween(input.last(), expected.last())
        val inputLength = polylineLength(input)
        val expectedLength = polylineLength(expected)
        val lengthRatio = if (expectedLength > 0f) inputLength / expectedLength else 0f
        if (inputLength <= 0f || expectedLength <= 0f) {
            return StrokeRecognitionResult(
                status = StrokeRecognitionStatus.TooShort,
                startDistance = startDistance,
                endDistance = endDistance,
                lengthRatio = lengthRatio
            )
        }

        if (startDistance > config.startTolerance * leniency) {
            return StrokeRecognitionResult(
                StrokeRecognitionStatus.WrongStart,
                startDistance,
                endDistance,
                lengthRatio = lengthRatio
            )
        }
        if (endDistance > config.endTolerance * leniency) {
            return StrokeRecognitionResult(
                StrokeRecognitionStatus.WrongEnd,
                startDistance,
                endDistance,
                lengthRatio = lengthRatio
            )
        }
        val paddedLengthRatio = (inputLength + 25f) / (expectedLength + 25f)
        if (paddedLengthRatio * leniency < config.minLengthRatio) {
            return StrokeRecognitionResult(
                StrokeRecognitionStatus.TooShort,
                startDistance,
                endDistance,
                lengthRatio = lengthRatio
            )
        }
        if (lengthRatio > config.maxLengthRatio * leniency) {
            return StrokeRecognitionResult(
                StrokeRecognitionStatus.BadLength,
                startDistance,
                endDistance,
                lengthRatio = lengthRatio
            )
        }

        val inputSamples = resamplePolyline(input, config.sampleCount.coerceAtLeast(8))
        val expectedSamples = resamplePolyline(expected, config.sampleCount.coerceAtLeast(8))
        val distances = inputSamples.map { point -> distanceToPolyline(point, expectedSamples) }
        val averageDistance = distances.average().toFloat()
        val maxDistance = distances.maxOrNull() ?: Float.POSITIVE_INFINITY
        val coverage = expectedSamples.count { expectedPoint ->
            distanceToPolyline(expectedPoint, inputSamples) <= config.coverageDistanceTolerance * leniency
        }.toFloat() / expectedSamples.size
        val distanceMultiplier = if (outlineVisible) config.outlineVisibleDistanceMultiplier else 1f
        val distanceMatches =
            averageDistance <= config.averageDistanceTolerance * distanceMultiplier * leniency &&
                maxDistance <= config.maxDistanceTolerance * leniency &&
                coverage >= config.minCoverage / leniency.coerceAtLeast(1f)
        val directionMatches = averageDirectionSimilarity(input, expected) > 0f
        val shapeMatches = shapeDistance(input, expected) <= config.frechetTolerance * leniency

        return StrokeRecognitionResult(
            status = if (distanceMatches && directionMatches && shapeMatches) {
                StrokeRecognitionStatus.Accepted
            } else {
                StrokeRecognitionStatus.OffPath
            },
            startDistance = startDistance,
            endDistance = endDistance,
            averageDistance = averageDistance,
            maxDistance = maxDistance,
            lengthRatio = lengthRatio,
            coverage = coverage
        )
    }
}

private fun averageDirectionSimilarity(
    input: List<GlyphPoint>,
    expected: List<GlyphPoint>
): Float {
    val expectedVectors = expected.zipWithNext(::subtractPoint).filter { vectorLength(it) > 0f }
    if (expectedVectors.isEmpty()) return -1f
    val similarities = input.zipWithNext(::subtractPoint)
        .filter { vectorLength(it) > 0f }
        .map { actual -> expectedVectors.maxOf { expectedVector -> cosineSimilarity(actual, expectedVector) } }
    return similarities.takeIf { it.isNotEmpty() }?.average()?.toFloat() ?: -1f
}

private fun shapeDistance(input: List<GlyphPoint>, expected: List<GlyphPoint>): Float {
    val normalizedInput = normalizeCurve(input)
    val normalizedExpected = normalizeCurve(expected)
    if (normalizedInput.size < 2 || normalizedExpected.size < 2) return Float.POSITIVE_INFINITY
    val rotations = listOf(PI / 16, PI / 32, 0.0, -PI / 32, -PI / 16)
    return rotations.minOf { angle ->
        discreteFrechet(normalizedInput, normalizedExpected.map { rotate(it, angle) })
    }
}

private fun normalizeCurve(points: List<GlyphPoint>): List<GlyphPoint> {
    val sampled = resamplePolyline(points, 30)
    if (sampled.isEmpty()) return emptyList()
    val meanX = sampled.map { it.x }.average().toFloat()
    val meanY = sampled.map { it.y }.average().toFloat()
    val translated = sampled.map { GlyphPoint(it.x - meanX, it.y - meanY) }
    val first = translated.first()
    val last = translated.last()
    val scale = sqrt(
        ((first.x * first.x + first.y * first.y) + (last.x * last.x + last.y * last.y)) / 2f
    )
    if (scale <= 0.0001f) return translated
    return translated.map { GlyphPoint(it.x / scale, it.y / scale) }
}

private fun discreteFrechet(a: List<GlyphPoint>, b: List<GlyphPoint>): Float {
    val values = Array(a.size) { FloatArray(b.size) }
    for (i in a.indices) {
        for (j in b.indices) {
            val distance = distanceBetween(a[i], b[j])
            values[i][j] = when {
                i == 0 && j == 0 -> distance
                i == 0 -> max(values[i][j - 1], distance)
                j == 0 -> max(values[i - 1][j], distance)
                else -> max(minOf(values[i - 1][j], values[i - 1][j - 1], values[i][j - 1]), distance)
            }
        }
    }
    return values.last().last()
}

private fun distanceToPolyline(point: GlyphPoint, points: List<GlyphPoint>): Float {
    if (points.isEmpty()) return Float.POSITIVE_INFINITY
    if (points.size == 1) return distanceBetween(point, points.first())
    return points.zipWithNext().minOf { (start, end) -> distanceToSegment(point, start, end) }
}

private fun distanceToSegment(point: GlyphPoint, start: GlyphPoint, end: GlyphPoint): Float {
    val dx = end.x - start.x
    val dy = end.y - start.y
    val lengthSquared = dx * dx + dy * dy
    if (lengthSquared <= 0f) return distanceBetween(point, start)
    val t = (((point.x - start.x) * dx + (point.y - start.y) * dy) / lengthSquared).coerceIn(0f, 1f)
    return distanceBetween(point, GlyphPoint(start.x + dx * t, start.y + dy * t))
}

private fun subtractPoint(end: GlyphPoint, start: GlyphPoint): GlyphPoint =
    GlyphPoint(end.x - start.x, end.y - start.y)

private fun vectorLength(point: GlyphPoint): Float = sqrt(point.x * point.x + point.y * point.y)

private fun cosineSimilarity(a: GlyphPoint, b: GlyphPoint): Float {
    val denominator = vectorLength(a) * vectorLength(b)
    return if (denominator <= 0f) -1f else (a.x * b.x + a.y * b.y) / denominator
}

private fun rotate(point: GlyphPoint, angle: Double): GlyphPoint =
    GlyphPoint(
        x = (cos(angle) * point.x - sin(angle) * point.y).toFloat(),
        y = (sin(angle) * point.x + cos(angle) * point.y).toFloat()
    )

private fun List<GlyphPoint>.deduplicateAdjacent(): List<GlyphPoint> {
    if (size < 2) return this
    val result = mutableListOf(first())
    for (index in 1 until size) {
        if (distanceBetween(result.last(), this[index]) > 1f) {
            result += this[index]
        }
    }
    return result
}

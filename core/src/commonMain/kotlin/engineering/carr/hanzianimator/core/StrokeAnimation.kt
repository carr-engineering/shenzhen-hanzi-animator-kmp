package engineering.carr.hanzianimator.core

import kotlin.math.roundToLong

/**
 * Timing configuration for stroke-order animation.
 *
 * By default each stroke's duration scales with the length of its median,
 * clamped to `[minStrokeDurationMillis, maxStrokeDurationMillis]`, with a fixed
 * [delayBetweenStrokesMillis] gap. Set [strokeDurationMillis] for a fixed
 * per-stroke duration, or [strokeSpeedViewportUnitsPerSecond] to drive duration
 * from a constant pen speed.
 */
public data class StrokeAnimationConfig(
    val strokeDurationMillis: Long? = null,
    val minStrokeDurationMillis: Long = 280L,
    val maxStrokeDurationMillis: Long = 850L,
    val speedMultiplier: Float = 1f,
    val strokeSpeedViewportUnitsPerSecond: Float? = null,
    val delayBetweenStrokesMillis: Long = 120L,
    val loop: Boolean = false
) {
    init {
        require(strokeDurationMillis == null || strokeDurationMillis > 0L)
        require(minStrokeDurationMillis > 0L && maxStrokeDurationMillis >= minStrokeDurationMillis)
        require(speedMultiplier.isFinite() && speedMultiplier > 0f)
        require(strokeSpeedViewportUnitsPerSecond == null || (strokeSpeedViewportUnitsPerSecond.isFinite() && strokeSpeedViewportUnitsPerSecond > 0f))
        require(delayBetweenStrokesMillis >= 0L)
    }
}

/**
 * A snapshot of an in-progress stroke-order animation at a point in time.
 *
 * @property currentStrokeIndex the stroke being drawn, or `null` during an
 *   inter-stroke gap or once finished.
 * @property progressByStroke draw progress in `0f..1f` for every stroke.
 * @property completedStrokeIndexes strokes already fully drawn.
 * @property isFinished true once the (non-looping) animation has ended.
 */
public data class StrokeAnimationFrame(
    val currentStrokeIndex: Int?,
    val progressByStroke: List<Float>,
    val completedStrokeIndexes: List<Int>,
    val isFinished: Boolean
)

/**
 * Pure time-to-[StrokeAnimationFrame] function for a character.
 *
 * A planner is created once for a [StrokeOrder] via [fromStrokeOrder] and then
 * sampled with [frameAt]; it holds no mutable state, so it is safe to read from
 * a draw phase or a background frame loop.
 */
public class StrokeAnimationPlanner private constructor(
    private val strokeDurationsMillis: List<Long>,
    private val delayBetweenStrokesMillis: Long,
    private val loop: Boolean
) {
    /** Number of strokes this planner animates. */
    public val strokeCount: Int
        get() = strokeDurationsMillis.size

    /** Total animation length, excluding the trailing gap; `0` when empty. */
    public val totalDurationMillis: Long = run {
        var total = 0L
        strokeDurationsMillis.forEachIndexed { index, duration ->
            val gap = if (index == 0) 0L else delayBetweenStrokesMillis
            require(total <= Long.MAX_VALUE - gap && total + gap <= Long.MAX_VALUE - duration) { "Animation timeline overflows" }
            total += gap + duration
        }
        total
    }

    /** Returns the animation frame at [elapsedMillis] since the animation started. */
    public fun frameAt(elapsedMillis: Long): StrokeAnimationFrame {
        if (strokeDurationsMillis.isEmpty()) {
            return StrokeAnimationFrame(
                currentStrokeIndex = null,
                progressByStroke = emptyList(),
                completedStrokeIndexes = emptyList(),
                isFinished = true
            )
        }

        val boundedElapsed = elapsedMillis.coerceAtLeast(0L)
        val timelineMillis = if (loop && totalDurationMillis > 0L) {
            boundedElapsed % totalDurationMillis
        } else {
            boundedElapsed
        }
        val progress = MutableList(strokeDurationsMillis.size) { 0f }
        val completed = mutableListOf<Int>()
        var current: Int? = null
        var cursor = 0L

        for (index in strokeDurationsMillis.indices) {
            val duration = strokeDurationsMillis[index]
            val strokeStart = cursor
            val strokeEnd = strokeStart + duration
            when {
                timelineMillis < strokeStart -> break
                timelineMillis < strokeEnd -> {
                    current = index
                    progress[index] = ((timelineMillis - strokeStart).toFloat() / duration).coerceIn(0f, 1f)
                    break
                }
                else -> {
                    progress[index] = 1f
                    completed += index
                }
            }
            cursor = strokeEnd + if (index < strokeDurationsMillis.lastIndex) delayBetweenStrokesMillis else 0L
        }

        return StrokeAnimationFrame(
            currentStrokeIndex = current,
            progressByStroke = progress,
            completedStrokeIndexes = completed,
            isFinished = !loop && boundedElapsed >= totalDurationMillis
        )
    }

    public companion object {
        /** Builds a planner for [order] using the timing in [config]. */
        public fun fromStrokeOrder(
            order: StrokeOrder,
            config: StrokeAnimationConfig = StrokeAnimationConfig()
        ): StrokeAnimationPlanner {
            val fixedDuration = config.strokeDurationMillis
            val minDuration = config.minStrokeDurationMillis
            val maxDuration = config.maxStrokeDurationMillis
            val speed = config.strokeSpeedViewportUnitsPerSecond?.takeIf { it > 0f }
            val durations = order.strokes.map { stroke ->
                val length = polylineLength(stroke.median)
                val plannedDuration = fixedDuration ?: speed?.let {
                    if (length > 0f) ((length / it) * 1000f).roundToLong() else minDuration
                } ?: (((length + 600f) / (3f * config.speedMultiplier)).roundToLong())
                if (fixedDuration != null) fixedDuration else plannedDuration.coerceIn(minDuration, maxDuration)
            }
            return StrokeAnimationPlanner(
                strokeDurationsMillis = durations,
                delayBetweenStrokesMillis = config.delayBetweenStrokesMillis.coerceAtLeast(0L),
                loop = config.loop
            )
        }
    }
}

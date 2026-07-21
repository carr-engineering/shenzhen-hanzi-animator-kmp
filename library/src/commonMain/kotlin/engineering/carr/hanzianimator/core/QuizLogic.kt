package engineering.carr.hanzianimator.core

/**
 * The reducers behind guided quizzing, kept as pure functions so they can be
 * unit-tested in isolation. `StrokeOrderController` drives these; consumers of
 * the library interact with the controller rather than this internal model.
 */

internal enum class StrokePracticeMode {
    Preview,
    Animation,
    Practice
}

internal data class StrokePracticeConfig(
    val recognizerConfig: StrokeRecognitionConfig = StrokeRecognitionConfig(),
    val hintMistakeThreshold: Int = 2
)

internal data class StrokePracticeState(
    val mode: StrokePracticeMode = StrokePracticeMode.Preview,
    val currentExpectedStroke: Int = 0,
    val acceptedStrokeIndexes: List<Int> = emptyList(),
    val mistakesOnCurrentStroke: Int = 0,
    val totalMistakes: Int = 0,
    val hintVisible: Boolean = false,
    val isComplete: Boolean = false,
    val lastRecognitionStatus: StrokeRecognitionStatus? = null
) {
    val mistakes: Int
        get() = totalMistakes
}

internal fun startStrokeAnimation(state: StrokePracticeState = StrokePracticeState()): StrokePracticeState =
    state.copy(
        mode = StrokePracticeMode.Animation,
        lastRecognitionStatus = null
    )

internal fun startStrokePractice(): StrokePracticeState =
    StrokePracticeState(mode = StrokePracticeMode.Practice)

internal fun showStrokeHint(state: StrokePracticeState): StrokePracticeState =
    state.copy(hintVisible = true)

internal fun resetStrokePractice(): StrokePracticeState = startStrokePractice()

internal fun submitPracticeStroke(
    state: StrokePracticeState,
    order: StrokeOrder,
    userPoints: List<GlyphPoint>,
    config: StrokePracticeConfig = StrokePracticeConfig()
): StrokePracticeState {
    if (state.mode != StrokePracticeMode.Practice || state.isComplete) return state
    val expectedIndex = state.currentExpectedStroke
    order.strokes.getOrNull(expectedIndex)
        ?: return state.copy(isComplete = order.strokeCount == 0)

    val recognition = StrokeRecognizer.recognize(
        userPoints = userPoints,
        order = order,
        expectedStrokeIndex = expectedIndex,
        outlineVisible = true,
        config = config.recognizerConfig
    )
    if (!recognition.accepted) {
        val mistakeCount = state.mistakesOnCurrentStroke + 1
        return state.copy(
            mistakesOnCurrentStroke = mistakeCount,
            totalMistakes = state.totalMistakes + 1,
            hintVisible = state.hintVisible || mistakeCount >= config.hintMistakeThreshold,
            lastRecognitionStatus = recognition.status
        )
    }

    val accepted = (state.acceptedStrokeIndexes + expectedIndex).distinct().sorted()
    val complete = accepted.size >= order.strokeCount
    return state.copy(
        currentExpectedStroke = if (complete) order.strokeCount else expectedIndex + 1,
        acceptedStrokeIndexes = accepted,
        mistakesOnCurrentStroke = 0,
        hintVisible = false,
        isComplete = complete,
        lastRecognitionStatus = StrokeRecognitionStatus.Accepted
    )
}

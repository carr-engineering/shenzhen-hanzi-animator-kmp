package engineering.carr.hanzianimator.sample

import engineering.carr.hanzianimator.core.StrokeRecognitionStatus

/**
 * Maps a [StrokeRecognitionStatus] to English feedback copy. The library
 * deliberately reports only the status; presentation text is the consumer's
 * responsibility, demonstrated here.
 */
fun strokeFeedback(status: StrokeRecognitionStatus): String = when (status) {
    StrokeRecognitionStatus.Accepted -> "Correct — next stroke"
    StrokeRecognitionStatus.Reversed -> "Draw in the other direction"
    StrokeRecognitionStatus.WrongStart -> "Start closer to the guide"
    StrokeRecognitionStatus.WrongEnd -> "Finish closer to the guide"
    StrokeRecognitionStatus.WrongStrokeOrder -> "Try the highlighted stroke first"
    StrokeRecognitionStatus.TooShort -> "Continue the stroke"
    StrokeRecognitionStatus.BadLength -> "Check the stroke length"
    StrokeRecognitionStatus.OffPath -> "Follow the stroke shape"
    StrokeRecognitionStatus.EmptyInput -> "Try again"
    StrokeRecognitionStatus.MissingExpectedMedian -> "Practice unavailable"
}

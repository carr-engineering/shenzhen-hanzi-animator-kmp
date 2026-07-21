package engineering.carr.hanzianimator.compose

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * A minimal set of 24dp control icons built from Material path data, so the
 * library's controls need no `material-icons` dependency. Tinting is left to
 * the calling `Icon`.
 */
internal object HanziIcons {
    val Play: ImageVector by lazy { icon("play", "M8 5v14l11-7z") }
    val Pause: ImageVector by lazy { icon("pause", "M6 5h4v14H6zm8 0h4v14h-4z") }
    val SkipPrevious: ImageVector by lazy { icon("skipPrevious", "M6 6h2v12H6zm3.5 6l8.5 6V6z") }
    val SkipNext: ImageVector by lazy { icon("skipNext", "M6 18l8.5-6L6 6v12zM16 6v12h2V6z") }
    val Done: ImageVector by lazy { icon("done", "M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z") }
    val Replay: ImageVector by lazy {
        icon(
            "replay",
            "M12 5V1L7 6l5 5V7c3.31 0 6 2.69 6 6s-2.69 6-6 6-6-2.69-6-6H4c0 4.42 3.58 8 8 8s8-3.58 " +
                "8-8-3.58-8-8-8z"
        )
    }
    val Edit: ImageVector by lazy {
        icon(
            "edit",
            "M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34a." +
                "9959.9959 0 00-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z"
        )
    }
    val Close: ImageVector by lazy {
        icon(
            "close",
            "M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 " +
                "17.59 13.41 12z"
        )
    }
    val Visibility: ImageVector by lazy {
        icon(
            "visibility",
            "M12 4.5C7 4.5 2.73 7.61 1 12c1.73 4.39 6 7.5 11 7.5s9.27-3.11 11-7.5c-1.73-4.39-6-7.5-11-7.5z" +
                "M12 17c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5zm0-8c-1.66 0-3 1.34-3 3s1.34 " +
                "3 3 3 3-1.34 3-3-1.34-3-3-3z"
        )
    }
    val VisibilityOff: ImageVector by lazy {
        icon(
            "visibilityOff",
            "M12 7c2.76 0 5 2.24 5 5 0 .65-.13 1.26-.36 1.83l2.92 2.92c1.51-1.26 2.7-2.89 3.43-4.75-1.73-" +
                "4.39-6-7.5-11-7.5-1.4 0-2.74.25-3.98.7l2.16 2.16C10.74 7.13 11.35 7 12 7zM2 4.27l2.28 " +
                "2.28.46.46C3.08 8.3 1.78 10.02 1 12c1.73 4.39 6 7.5 11 7.5 1.55 0 3.03-.3 4.38-.84l.42.42L" +
                "19.73 22 21 20.73 3.27 3 2 4.27zM7.53 9.8l1.55 1.55c-.05.21-.08.43-.08.65 0 1.66 1.34 3 " +
                "3 3 .22 0 .44-.03.65-.08l1.55 1.55c-.67.33-1.41.53-2.2.53-2.76 0-5-2.24-5-5 0-.79.2-1.53." +
                "53-2.2zm4.31-.78l3.15 3.15.02-.16c0-1.66-1.34-3-3-3l-.17.01z"
        )
    }
    val Lightbulb: ImageVector by lazy {
        icon(
            "lightbulb",
            "M9 21c0 .55.45 1 1 1h4c.55 0 1-.45 1-1v-1H9v1zm3-19C8.14 2 5 5.14 5 9c0 2.38 1.19 4.47 3 " +
                "5.74V17c0 .55.45 1 1 1h6c.55 0 1-.45 1-1v-2.26c1.81-1.27 3-3.36 3-5.74 0-3.86-3.14-7-7-7z"
        )
    }

    private fun icon(name: String, pathData: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).addPath(
            pathData = PathParser().parsePathString(pathData).toNodes(),
            fill = SolidColor(Color.Black)
        ).build()
}

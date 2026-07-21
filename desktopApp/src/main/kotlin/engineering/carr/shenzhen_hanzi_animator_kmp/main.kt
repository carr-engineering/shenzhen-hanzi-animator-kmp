package engineering.carr.shenzhen_hanzi_animator_kmp

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "ShenzhenHanziAnimatorKMP",
    ) {
        App()
    }
}
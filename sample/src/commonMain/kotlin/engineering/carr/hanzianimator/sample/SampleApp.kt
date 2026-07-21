package engineering.carr.hanzianimator.sample

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import engineering.carr.hanzianimator.HanziAnimator

/**
 * Root of the sample application. Placeholder for P1 — the responsive
 * animate/practice screens, ink theme, and character input land in P5.
 */
@Composable
fun SampleApp() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text("shenzhen-hanzi-animator sample ${HanziAnimator.VERSION}")
            }
        }
    }
}

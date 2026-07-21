package engineering.carr.hanzianimator.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import engineering.carr.hanzianimator.data.BundledStrokeOrderProvider
import engineering.carr.hanzianimator.sample.resources.Res
import engineering.carr.hanzianimator.sample.resources.noto_sans_sc_subset
import engineering.carr.hanzianimator.sample.theme.InkTheme
import engineering.carr.hanzianimator.sample.theme.ThemeMode
import org.jetbrains.compose.resources.Font

/** Root of the sample: ink theme, bundled CJK font, and the animate/practice screen. */
@Composable
fun SampleApp() {
    var themeMode by remember { mutableStateOf(ThemeMode.System) }
    val fontFamily = FontFamily(Font(Res.font.noto_sans_sc_subset))
    val provider = remember { BundledStrokeOrderProvider.Default }

    InkTheme(themeMode = themeMode, fontFamily = fontFamily) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopBar(themeMode = themeMode, onCycleTheme = { themeMode = themeMode.next() })
                SampleScreen(provider = provider, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun TopBar(themeMode: ThemeMode, onCycleTheme: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "shenzhen-hanzi-animator",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Stroke-order animation & practice",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(onClick = onCycleTheme) {
            Text("Theme: ${themeMode.label}")
        }
    }
}

private val ThemeMode.label: String
    get() = when (this) {
        ThemeMode.Light -> "Light"
        ThemeMode.Dark -> "Dark"
        ThemeMode.System -> "System"
    }

private fun ThemeMode.next(): ThemeMode = when (this) {
    ThemeMode.System -> ThemeMode.Light
    ThemeMode.Light -> ThemeMode.Dark
    ThemeMode.Dark -> ThemeMode.System
}

package engineering.carr.hanzianimator.sample
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import engineering.carr.hanzianimator.sample.resources.Res
import engineering.carr.hanzianimator.sample.resources.noto_sans_sc_subset
import org.jetbrains.compose.resources.Font
@Composable
actual fun sampleFontFamily(): FontFamily = FontFamily(Font(Res.font.noto_sans_sc_subset))

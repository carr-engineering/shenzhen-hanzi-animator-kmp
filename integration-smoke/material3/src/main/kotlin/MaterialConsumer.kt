import androidx.compose.runtime.Composable
import engineering.carr.hanzianimator.compose.*
import engineering.carr.hanzianimator.core.StrokeOrder
import engineering.carr.hanzianimator.material3.*

@Composable
fun PublishedMaterialGlyph(order: StrokeOrder) {
    val controller = rememberStrokeOrderController(order)
    val style = MaterialStrokeOrderDefaults.style()
    StrokeOrderCanvas(controller, style = style)
    MaterialStrokeOrderControls(controller)
}

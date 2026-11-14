package engineering.carr.hanzianimator.compose
import androidx.compose.runtime.*
import engineering.carr.hanzianimator.core.StrokeOrder
import engineering.carr.hanzianimator.data.StrokeOrderProvider
import kotlinx.coroutines.CancellationException
public sealed interface StrokeOrderLoadState {
    public data object Loading : StrokeOrderLoadState
    public data object Missing : StrokeOrderLoadState
    public data class Loaded(val order: StrokeOrder) : StrokeOrderLoadState
    public data class Error(val cause: Throwable) : StrokeOrderLoadState
}
@Composable
public fun rememberStrokeOrderLoadState(character: String, provider: StrokeOrderProvider, retryKey: Int = 0): State<StrokeOrderLoadState> {
    val state = remember(character, provider, retryKey) { mutableStateOf<StrokeOrderLoadState>(StrokeOrderLoadState.Loading) }
    LaunchedEffect(character, provider, retryKey) {
        state.value = try { provider.strokeOrder(character)?.let { StrokeOrderLoadState.Loaded(it) } ?: StrokeOrderLoadState.Missing }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { StrokeOrderLoadState.Error(error) }
    }
    return state
}

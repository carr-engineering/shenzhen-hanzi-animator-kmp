package engineering.carr.hanzianimator.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import engineering.carr.hanzianimator.data.BundledStrokeOrderProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * A single-character input: an [OutlinedTextField] plus "Show" and "Random"
 * buttons. Only a single character present in the bundled dataset is accepted;
 * anything else is rejected with an inline message.
 */
@Composable
fun CharacterInput(
    provider: BundledStrokeOrderProvider,
    onCharacterSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf("") }
    var error by remember(provider) { mutableStateOf<String?>(null) }
    var loading by remember(provider) { mutableStateOf(false) }
    var request by remember(provider) { mutableStateOf(0) }
    var job: Job? by remember(provider) { mutableStateOf(null) }
    val scope = rememberCoroutineScope()
    val selected by rememberUpdatedState(onCharacterSelected)
    DisposableEffect(provider) { onDispose { job?.cancel() } }

    fun invalidate() {
        request++
        job?.cancel()
        loading = false
        error = null
    }
    fun load(random: Boolean) {
        val candidate = text.trim()
        invalidate()
        if (!random && candidate.codePointCount() != 1) {
            error = "Enter exactly one character."
            return
        }
        val expected = request
        loading = true
        job = scope.launch {
            try {
                val character = if (random) provider.randomCharacter() else candidate
                val order = character?.let { provider.strokeOrder(it) }
                if (request == expected) {
                    if (order != null && character != null) {
                        text = character
                        selected(character)
                    } else error = "No stroke data for “${character ?: candidate}”."
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (request == expected) error = "Data could not be loaded. Please try again."
            } finally {
                if (request == expected) loading = false
            }
        }
    }

    @Composable
    fun InputField(fieldModifier: Modifier) {
        OutlinedTextField(
            value = text,
            onValueChange = { invalidate(); text = it },
            singleLine = true,
            isError = error != null,
            label = { Text("Hanzi") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { load(false) }),
            modifier = fieldModifier
        )
    }
    @Composable
    fun Actions() {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !loading, onClick = { load(false) }) { Text(if (loading) "Loading…" else "Show") }
            OutlinedButton(enabled = !loading, onClick = { load(true) }) { Text("Random") }
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth < 480.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    InputField(Modifier.fillMaxWidth())
                    Actions()
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    InputField(Modifier.weight(1f))
                    Actions()
                }
            }
        }
        error?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

/** Counts Unicode code points, so a surrogate pair counts as one character. */
internal fun String.codePointCount(): Int {
    var index = 0
    var count = 0
    while (index < length) {
        index += if (this[index].isHighSurrogate() && index + 1 < length && this[index + 1].isLowSurrogate()) 2 else 1
        count++
    }
    return count
}

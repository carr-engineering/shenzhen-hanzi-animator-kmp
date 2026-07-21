package engineering.carr.hanzianimator.sample

import androidx.compose.foundation.layout.Arrangement
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
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun submit() {
        val candidate = text.trim()
        when {
            candidate.codePointCount() != 1 -> error = "Enter exactly one character."
            else -> scope.launch {
                if (provider.strokeOrder(candidate) != null) {
                    error = null
                    onCharacterSelected(candidate)
                } else {
                    error = "No stroke data for “$candidate”."
                }
            }
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    error = null
                },
                singleLine = true,
                isError = error != null,
                label = { Text("Hanzi") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { submit() }),
                modifier = Modifier.weight(1f)
            )
            Button(onClick = { submit() }) { Text("Show") }
            OutlinedButton(onClick = {
                scope.launch {
                    provider.randomCharacter()?.let { random ->
                        text = random
                        error = null
                        onCharacterSelected(random)
                    }
                }
            }) { Text("Random") }
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

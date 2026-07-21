package engineering.carr.hanzianimator.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import engineering.carr.hanzianimator.compose.HanziStrokePractice
import engineering.carr.hanzianimator.compose.StrokeOrderCanvas
import engineering.carr.hanzianimator.compose.StrokeOrderControls
import engineering.carr.hanzianimator.compose.rememberStrokeOrderController
import engineering.carr.hanzianimator.core.StrokeOrder
import engineering.carr.hanzianimator.data.BundledStrokeOrderProvider

private const val WIDE_BREAKPOINT_DP = 720

@Composable
fun SampleScreen(provider: BundledStrokeOrderProvider, modifier: Modifier = Modifier) {
    var character by remember { mutableStateOf("你") }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CharacterInput(
            provider = provider,
            onCharacterSelected = { character = it },
            modifier = Modifier.fillMaxWidth()
        )
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            if (maxWidth >= WIDE_BREAKPOINT_DP.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    AnimatePane(character, provider, Modifier.weight(1f))
                    PracticePane(character, provider, Modifier.weight(1f))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    AnimatePane(character, provider, Modifier.fillMaxWidth())
                    PracticePane(character, provider, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun AnimatePane(character: String, provider: BundledStrokeOrderProvider, modifier: Modifier = Modifier) {
    PaneCard(title = "Animate", character = character, modifier = modifier) {
        val order by produceState<StrokeOrder?>(null, character, provider) {
            value = provider.strokeOrder(character)
        }
        val current = order
        if (current == null) {
            GlyphPlaceholder()
        } else {
            val controller = rememberStrokeOrderController(current)
            LaunchedEffect(controller) { controller.play() }
            StrokeOrderCanvas(
                controller = controller,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f)
            )
            StrokeOrderControls(controller = controller, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun PracticePane(character: String, provider: BundledStrokeOrderProvider, modifier: Modifier = Modifier) {
    var feedback by remember(character) { mutableStateOf<String?>(null) }
    PaneCard(title = "Practice", character = character, modifier = modifier) {
        HanziStrokePractice(
            character = character,
            provider = provider,
            modifier = Modifier.fillMaxWidth(),
            onStrokeResult = { feedback = strokeFeedback(it.status) },
            onQuizComplete = {
                feedback = if (it.totalMistakes == 0) {
                    "Complete — no mistakes!"
                } else {
                    "Complete — ${it.totalMistakes} mistake${if (it.totalMistakes == 1) "" else "s"}"
                }
            },
            loading = { GlyphPlaceholder() },
            missing = { GlyphPlaceholder(text = "No stroke data") }
        )
        Text(
            text = feedback ?: "Tap the pencil to start a quiz, then trace each stroke.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun PaneCard(
    title: String,
    character: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    OutlinedCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = character,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            content()
        }
    }
}

@Composable
private fun GlyphPlaceholder(text: String? = null) {
    Box(
        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        contentAlignment = Alignment.Center
    ) {
        if (text == null) {
            CircularProgressIndicator()
        } else {
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

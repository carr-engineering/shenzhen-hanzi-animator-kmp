package engineering.carr.hanzianimator.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import engineering.carr.hanzianimator.compose.HanziStrokePractice
import engineering.carr.hanzianimator.compose.rememberStrokeOrderLoadState
import engineering.carr.hanzianimator.compose.StrokeOrderLoadState
import engineering.carr.hanzianimator.compose.StrokeOrderCanvas
import engineering.carr.hanzianimator.material3.MaterialStrokeOrderDefaults
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
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CharacterInput(
            provider = provider,
            onCharacterSelected = { character = it },
            modifier = Modifier.widthIn(max = 960.dp).fillMaxWidth()
        )
        BoxWithConstraints(modifier = Modifier.widthIn(max = 960.dp).fillMaxWidth()) {
            // Keep both pane call sites stable when constraints change, preserving sessions.
            FlowRow(
                maxItemsInEachRow = if (maxWidth >= WIDE_BREAKPOINT_DP.dp) 2 else 1,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AnimatePane(character, provider, Modifier.weight(1f))
                PracticePane(character, provider, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AnimatePane(character: String, provider: BundledStrokeOrderProvider, modifier: Modifier = Modifier) {
    PaneCard(title = "Animate", character = character, modifier = modifier) {
        var retryKey by remember(character, provider) { mutableIntStateOf(0) }
        val state by rememberStrokeOrderLoadState(character, provider, retryKey)
        when (val current = state) {
            StrokeOrderLoadState.Loading -> GlyphPlaceholder()
            StrokeOrderLoadState.Missing -> GlyphPlaceholder("No stroke data")
            is StrokeOrderLoadState.Error -> Column {
                GlyphPlaceholder("Data could not be loaded")
                androidx.compose.material3.TextButton(onClick = { retryKey++ }) { Text("Retry") }
            }
            is StrokeOrderLoadState.Loaded -> {
                val controller = rememberStrokeOrderController(current.order)
                LaunchedEffect(controller) { controller.play() }
                val style = MaterialStrokeOrderDefaults.style()
                StrokeOrderCanvas(controller = controller, modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth().aspectRatio(1f), style = style)
                StrokeOrderControls(controller = controller, modifier = Modifier.padding(top = 8.dp), style = style)
            }
        }
    }
}

@Composable
private fun PracticePane(character: String, provider: BundledStrokeOrderProvider, modifier: Modifier = Modifier) {
    PaneCard(title = "Practice", character = character, modifier = modifier) {
        val style = MaterialStrokeOrderDefaults.style()
        HanziStrokePractice(
            character = character,
            provider = provider,
            style = style,
            modifier = Modifier.fillMaxWidth(),
            controls = { controller ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    StrokeOrderControls(controller, style = style)
                    val message = when {
                        controller.mode != engineering.carr.hanzianimator.compose.StrokeOrderMode.Quiz ->
                            "Tap the pencil to start a quiz, then trace each stroke."
                        controller.isQuizComplete -> if (controller.totalMistakes == 0) "Complete — no mistakes!"
                            else "Complete — ${controller.totalMistakes} mistake${if (controller.totalMistakes == 1) "" else "s"}"
                        controller.lastRecognitionStatus != null -> strokeFeedback(requireNotNull(controller.lastRecognitionStatus))
                        else -> "Trace stroke ${controller.currentStroke + 1} of ${controller.strokeOrder.strokeCount}."
                    }
                    Text(message, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                }
            },
            loading = { GlyphPlaceholder() },
            missing = { GlyphPlaceholder(text = "No stroke data") },
            error = { _, retry -> Column { GlyphPlaceholder("Data could not be loaded"); androidx.compose.material3.TextButton(onClick = retry) { Text("Retry") } } }
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
        modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth().aspectRatio(1f),
        contentAlignment = Alignment.Center
    ) {
        if (text == null) {
            CircularProgressIndicator()
        } else {
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

# shenzhen-hanzi-animator

A Kotlin Multiplatform library that animates hanzi (Chinese character) stroke
order and quizzes users on drawing them, for **Compose Multiplatform**. The full
[Hanzi Writer](https://hanziwriter.org/) dataset — **9,574 characters** — is
bundled, so there is nothing to download at runtime on native and desktop, and
only the characters actually used are fetched on web.

> Replace `OWNER` throughout this README (and in `library/build.gradle.kts`) with
> your GitHub org/user before publishing.

## Features

- 🖌️ **Stroke-order animation** — clipped reveal along each stroke's median, with
  configurable timing and looping.
- ✍️ **Guided quizzing** — trace strokes in order with rotation-robust
  recognition, mistake tracking, and hints.
- 📦 **9,574 characters bundled** — gzip-compressed JSON shards; lazy per-shard
  fetch on web, zero runtime downloads elsewhere.
- 🎨 **Compose-native** — a `@Stable` controller backed by snapshot state; frame
  updates repaint without recomposing.
- 🧩 **Layered API** — drop-in composables, or building blocks you wire yourself,
  or just the pure core (parser, animation, recognizer) with your own data.

### Supported targets

| Target | Artifact |
|--------|----------|
| Android | `shenzhen-hanzi-animator-android` |
| JVM (desktop) | `shenzhen-hanzi-animator-jvm` |
| iOS (`iosArm64`, `iosSimulatorArm64`) | `shenzhen-hanzi-animator-iosarm64`, … |
| JS (browser) | `shenzhen-hanzi-animator-js` |
| Wasm (browser) | `shenzhen-hanzi-animator-wasm-js` |

## Installation

```kotlin
// build.gradle.kts
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("engineering.carr:shenzhen-hanzi-animator:0.1.0")
        }
    }
}
```

## Quick start — animate a character

The zero-wiring entry point loads the bundled data and autoplays:

```kotlin
import engineering.carr.hanzianimator.compose.HanziStrokeAnimation

@Composable
fun Demo() {
    HanziStrokeAnimation("你", modifier = Modifier.size(240.dp))
}
```

## Full experience — animation + quizzing

Wire the building blocks yourself for complete control:

```kotlin
import engineering.carr.hanzianimator.compose.*
import engineering.carr.hanzianimator.core.StrokeOrder
import engineering.carr.hanzianimator.data.BundledStrokeOrderProvider

@Composable
fun Practice(character: String) {
    val provider = remember { BundledStrokeOrderProvider.Default }
    val order by produceState<StrokeOrder?>(null, character) {
        value = provider.strokeOrder(character)
    }
    order?.let { strokeOrder ->
        val controller = rememberStrokeOrderController(strokeOrder)
        Column {
            StrokeOrderCanvas(
                controller = controller,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                onStrokeResult = { result -> /* map result.status to your own copy */ },
                onQuizComplete = { summary -> /* summary.totalMistakes, mistakesPerStroke */ },
            )
            StrokeOrderControls(controller)
        }
    }
}
```

### Styling

`StrokeOrderStyle` is immutable; `StrokeOrderDefaults.style()` resolves colours
from the current `MaterialTheme`, and you override only what you want:

```kotlin
val style = StrokeOrderDefaults.style(
    radicalStrokeColor = Color(0xFFA53A2B),
    showGrid = false,
)
StrokeOrderCanvas(controller, style = style)
```

The library reports quiz outcomes as a `StrokeRecognitionStatus`; **feedback text
is your responsibility** (no user-facing strings are baked in), so you can
localize freely.

## Building blocks

| Piece | What it does |
|-------|--------------|
| `rememberStrokeOrderController(order)` | Creates the controller (frame loop, quiz state). |
| `controller.play()/pause()/stop()` | Animation playback. |
| `controller.nextStroke()/previousStroke()/showFullCharacter()` | Static stepping. |
| `controller.startQuiz()/submitStroke(points)/showHint()/stopQuiz()` | Quizzing. |
| `controller.quizSummary()` | Mistake tally once complete. |
| `StrokeOrderCanvas` | Renders the controller + captures quiz strokes. |
| `StrokeOrderControls` | Ready-made Material 3 control bar. |
| `HanziGlyph(order, visibleStrokes = n)` | Stateless static render. |
| `HanziStrokeAnimation(char)` / `HanziStrokePractice(char)` | Opinionated drop-ins. |

### Bring your own data

`StrokeOrder.parse(character, hanziWriterJson)` parses a single Hanzi Writer
entry, and `StrokeOrderProvider` is a one-method interface you can implement to
source data however you like:

```kotlin
val order = StrokeOrder.parse("中", myJson)
```

The pure core (`StrokeOrder`, `StrokeAnimationPlanner`, `StrokeRecognizer`) has no
Compose dependency and can be used headlessly.

## Data notes

The dataset lives in `library/src/commonMain/composeResources/files/strokes/` as
256 gzip shards keyed by `codePoint % 256`, plus a character index. On web,
`BundledStrokeOrderProvider` fetches only the shards you touch (via
`DecompressionStream`), so the ~12 MB costs web users nothing up front.

Regenerate from upstream (pinned commit) with:

```
python3 tools/generate_stroke_data.py
```

See [`tools/DATA_PROVENANCE.md`](tools/DATA_PROVENANCE.md) for the exact source
and commit.

## Running the sample

```
./gradlew :sample:run                          # desktop
./gradlew :sample:wasmJsBrowserDevelopmentRun  # web (Chrome / Safari 16.4+)
```

The sample shows single-character input with validation, a random button,
side-by-side animate/practice panes, an outline toggle, quizzing with feedback,
and a light/dark theme toggle — laid out responsively.

## Attribution & licenses

This library is MIT-licensed (see [`LICENSE`](LICENSE)). It bundles and builds on:

- **[Hanzi Writer](https://hanziwriter.org/)** — MIT. Data format and
  clipped-reveal / guided-quiz conventions.
- **[hanzi-writer-data](https://github.com/chanind/hanzi-writer-data)** — MIT;
  underlying glyph data from
  **[Make Me a Hanzi](https://github.com/skishore/makemeahanzi)** (Arphic Public
  License).
- **[stroke_order_animator](https://pub.dev/packages/stroke_order_animator)** —
  BSD-3-Clause; a conceptual reference for the controller API (no code copied).

The sample bundles a subset of **Noto Sans SC** (SIL Open Font License 1.1); see
its `FONT-NOTICE.md`. Full details are in [`NOTICE`](NOTICE).

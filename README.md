# shenzhen-hanzi-animator

Hanzi stroke animation and guided practice for Kotlin Multiplatform and browser
Canvas. The headless engine, Compose adapter, JSON importer and bundled dataset
are separate dependencies. The pinned dataset contains **9,574 characters**.

This repository is preparing version **0.1.0**. Coordinates below describe the
publication configuration; availability on Maven Central/npm is not implied.
See [validation and release steps](docs/RELEASING.md) for the checks required
before publishing. This refactor intentionally breaks the previous API; use the
[migration guide](docs/MIGRATION.md).

## Choose your dependencies

All Maven artifacts use `engineering.carr` and version `0.1.0`. Add dependencies
to `commonMain`; Gradle selects Android, JVM, iOS ARM64/simulator ARM64, JS or
Wasm variants.

| Artifact suffix (`shenzhen-hanzi-animator-…`) | Purpose and runtime dependencies |
| --- | --- |
| `core` | Immutable glyph geometry, animation, recognition and `StrokeSession`; Kotlin stdlib only |
| `codec` | Validated indexed HZB1 decoder; core and stdlib only |
| `json` | Optional Hanzi Writer JSON importer; core and kotlinx.serialization |
| `data` | Application-supplied decoded byte source, bounded caches and concurrent loading; core, codec and coroutines |
| `bundled-data` | Dataset and native/browser resource readers; data module |
| `compose` | Canvas, controller, Foundation controls and composables; core, Compose UI/runtime/Foundation and coroutines |
| `material3` | Optional Material color adapter and controls; Compose module and Material 3 |

The Compose artifact does not include a dataset, HTTP client, Material, generated
Compose resources or a font. Android requires API 24+. The default bundled iOS
Brotli reader requires iOS 15+ and Apple's system Compression framework.
The tested compiler is Kotlin 2.4.10. Android consumers need a compatible D8/R8;
see [the verified toolchain requirements](docs/r8-toolchain.md).

```kotlin
commonMain.dependencies {
    implementation("engineering.carr:shenzhen-hanzi-animator-compose:0.1.0")
    implementation("engineering.carr:shenzhen-hanzi-animator-bundled-data:0.1.0")
    // Optional: shenzhen-hanzi-animator-material3 or shenzhen-hanzi-animator-json
}
```

## Compose example

Create one provider using an application-owned `CoroutineScope`, then pass it to
your screen. Cancel that scope when its owning application/service shuts down.
Use a `SupervisorJob` when unrelated requests should fail independently.

```kotlin
val dataScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
val provider = BundledStrokeOrderProvider(dataScope) // JVM/iOS/browser
```

On Android, supply the application AssetManager explicitly:

```kotlin
val provider = BundledStrokeOrderProvider(
    scope = dataScope,
    source = androidBundledDataSource(applicationContext.assets),
)
```

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import engineering.carr.hanzianimator.compose.HanziStrokePractice
import engineering.carr.hanzianimator.data.StrokeOrderProvider

@Composable
fun Practice(provider: StrokeOrderProvider) {
    HanziStrokePractice(
        character = "永",
        provider = provider,
        modifier = Modifier,
        onQuizComplete = { summary -> println(summary.totalMistakes) },
        // Supply loading, missing and error slots for your application's UI.
    )
}
```

`HanziStrokeAnimation` uses the same explicit provider. For custom screens, load
an order and use `rememberStrokeOrderController`, `StrokeOrderCanvas`,
`StrokeOrderControls` or the stateless `HanziGlyph`. Load errors and unavailable
characters are distinct states; error slots receive a retry callback.

`StrokeOrderDefaults.style()` is Material independent. The optional
`MaterialStrokeOrderDefaults.style()` derives colors from `MaterialTheme`.
Component colors identify occurrences in decomposition-tree order, including
repeated labels. Unmapped strokes stay neutral; an empty palette restores radical
coloring. Six distinct colors cover the bundled component metadata. Feedback
messages and accessibility labels can be localized by the host.

## Headless and custom data

`StrokeOrder`, `StrokeOutline` and `GlyphPoint` contain render-ready geometry in
a 1,024-unit square with a top-left origin and downward Y. Input collections and
outline arrays are defensively copied. A `StrokeSession` owns playback and quiz
state without Compose; the host supplies elapsed milliseconds and redraws after
mutations. Import older JSON with the optional
`StrokeOrderJson.parse(character, json)`; do not flip imported coordinates again.

Use `BinaryStrokeOrderProvider(source, scope, cacheConfig, shardCount)` with your
application's assets or network client. `StrokeDataSource.read` must return
**decoded HZB1 bytes**, not gzip/Brotli bytes. Missing glyphs return `null`; source
failures throw. Details and cache/lifetime examples are in
[the migration guide](docs/MIGRATION.md).

## Browser Canvas

The standalone [`browser`](browser/README.md) adapter shares the Kotlin engine
and exports an ES module with TypeScript declarations. It has no Compose,
Skiko, Wasm, font or framework runtime. The dataset is separately deployed.

```ts
import {mountHanziPractice} from '@hanzi-animator/canvas';
const practice = mountHanziPractice(document.querySelector('#practice')!, {
  character: '永', data: {baseUrl: '/strokes/'},
  onComplete: summary => console.log(summary.totalMistakes)
});
// Call practice.dispose() when removing the component.
```

Fetch `.hzb` URLs and let the web server negotiate their `.gz`/`.br` sidecars
using `Content-Encoding` and `Vary: Accept-Encoding`. The browser exposes decoded
bytes. Raw `.hzb` files also work without server compression. Typical compressed
web blocks are about 7 KB; no full native archive is fetched by default.

## Samples and validation

The bundled stroke packs are generated, not committed. Prepare them once after cloning
(Python 3.14 and Node 22; see [data provenance](tools/DATA_PROVENANCE.md)):

```sh
tools/prepare_bundled_data.sh
```

```sh
./gradlew :sample:run                           # desktop
./gradlew :androidApp:assembleRelease           # Android sample APK
./gradlew :sample:wasmJsBrowserDevelopmentRun    # Compose web sample
./gradlew :browser-engine:jsBrowserProductionLibraryDistribution
cd browser && npm ci && npm run build && npm test && npm run demo
```

[`iosApp`](iosApp/README.md) embeds the sample framework and copies the iOS dataset
into the application bundle. Native resources are described in
[migration](docs/MIGRATION.md); they are not generated Compose resources.

See [measured sizes and layout](docs/MEASUREMENTS.md),
[data provenance](tools/DATA_PROVENANCE.md), [binary format](tools/BINARY_FORMAT.md),
and [isolated publication consumer](integration-smoke/README.md). `PLAN.md` and
`review-assets/` record earlier design work and are historical; current code and
these module documents describe the refactor.

## Attribution and distribution

The Kotlin/TypeScript code is [MIT licensed](LICENSE). **The bundled glyph data
is Arphic Public License data**, including the pinned hanzi-writer-data corpus;
the Hanzi Writer application being MIT does not relicense those glyphs. Component
metadata derives from Make Me a Hanzi under LGPL-3.0-or-later. Complete Arphic,
LGPL, GPL and upstream COPYING notices accompany the data and are available in
[docs/licenses](docs/licenses). The enriched source corpus, pinned revisions,
hashes and regeneration tools are retained in `tools/`.

The Compose web sample alone includes a subset of Noto Sans SC under SIL OFL 1.1;
it is not a dependency of the published library or Canvas adapter. See
[NOTICE](NOTICE) and the [third-party record](docs/THIRD_PARTY.md) for provenance,
modifications and license locations.

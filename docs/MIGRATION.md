# Migrating to the modular 0.1.0 API

This refactor makes clean API breaks. Replace the old monolithic
`engineering.carr:shenzhen-hanzi-animator` dependency with the modules listed in
[README](../README.md). The published Compose coordinate ends in `-compose`,
although its source project remains `:library`.

| Previous API/behavior | Replacement |
| --- | --- |
| `StrokeOrder.parse(character, json)` | Add `-json`; call `StrokeOrderJson.parse(character, json)` |
| SVG path string on a stroke | `StrokeOutline` command buffer; `commandAt`/`coordinateAt` or defensive copies |
| Upstream Y-up geometry | Both outlines and medians use normalized Y-down glyph space |
| Bundled default provider/global singleton | Construct `BundledStrokeOrderProvider(scope, source, cacheConfig)` and pass it explicitly |
| Zero-provider animation/practice composable | Supply `provider = …` |
| Core imports accidentally supplied by Compose | Add `-core` or `-codec` directly in headless consumers |
| Material-dependent defaults and controls | Foundation defaults remain in `compose`; add `-material3` for `MaterialStrokeOrderDefaults`/`MaterialStrokeOrderControls` |
| Generated `Res.readBytes` stroke resources | Platform asset/bundle/classpath/fetch reader or your own `StrokeDataSource` |
| Controller owns all behavior | `StrokeSession` owns the pure state machine; Compose controller is an adapter |

## Coordinates and immutable models

The viewport is `StrokeOrder.GLYPH_SIZE == 1024f`, with top-left origin and
positive downward Y. JSON import and binary decode apply `normalizedY = 900 -
upstreamY` once. Values can extend outside the square; normalization is a
coordinate transform, not clamping. Custom callers construct `GlyphPoint` and
`StrokeOutline` directly in normalized space. Remove previous render/input Y
flips. For a padded square canvas, map input pixels to glyph units by subtracting
its content origin and dividing by `contentSize / 1024f`; do not invert Y.

`StrokeOutline.Builder` supports absolute MOVE/LINE/QUAD/CUBIC/CLOSE commands.
`StrokeOrder` validates canonical zero-based stroke indexes and component
memberships. Geometry must be finite. Models defensively copy their inputs;
replace an order to change geometry instead of mutating arrays or casting lists.
Repeated component labels are distinct occurrences. A `null` membership remains
unmapped, and palette indexes beyond a custom list remain neutral.

## Timing and state

Use `StrokeSession(order, animationConfig, recognitionConfig, hintAfterMistakes)`
for non-Compose hosts. Call `play()`, then `advanceAnimation(elapsedMillis)` with
an **absolute elapsed clock**, not a per-frame delta. Pausing retains
`animationElapsedMillis`; on resume, subtract that elapsed amount from the new
clock origin. Updating the order resets playback and quiz state. The host must
invalidate its UI after mutations; the session is not observable or thread safe.
Confine a session to one owning execution context.

Fixed `strokeDurationMillis` takes precedence. Otherwise viewport speed, or the
length-based default with `speedMultiplier`, determines stroke durations clamped
to the minimum/maximum. Defaults are 280–850 ms, 120 ms between strokes and no
loop. `HanziStrokeAnimation` explicitly defaults to looping. Durations and speeds
must be positive and finite; gaps must be nonnegative. `StrokeAnimationPlanner`
excludes a trailing gap and rejects overflowing timelines.

`startQuiz()` returns false for orders lacking usable expected medians. Empty
input and missing expected medians do not count as drawing mistakes. Successful
completion exposes `QuizSummary`; hosts own feedback text/localization.

In Compose, use `rememberStrokeOrderController` inside composition. Configuration
changes recreate the controller; disposal stops it. Its frame loop uses the
Compose frame clock, so directly constructing a controller with a generic
background scope and calling `play()` requires a frame-clock context. Pure hosts
should use `StrokeSession`.

## Application-owned loading and caching

The dataset provider no longer creates an unmanaged global scope or HTTP client.
Create it in the application/service layer using a scope whose lifetime matches
shared work:

```kotlin
val job = SupervisorJob()
val scope = CoroutineScope(job + Dispatchers.Default)
val provider = BinaryStrokeOrderProvider(
    source = StrokeDataSource { key -> applicationByteLoader(key) },
    scope = scope,
    cacheConfig = StrokeDataCacheConfig.Web,
    shardCount = 1024,
)
// Pass provider to screens. When its owner shuts down: job.cancel().
```

`applicationByteLoader` is your implementation: it returns decoded HZB1 bytes
for archive keys and UTF-8 for `index.txt`. A missing glyph returns `null`;
transport, malformed data and resource errors throw. Propagate coroutine
cancellation. Concurrent reads of the same block share a request; cancelling one
waiting caller does not cancel work needed by other consumers. Cancelling the
provider's scope cancels that work. Failed requests can be retried.

Native defaults retain up to 12 MiB of decoded blocks and 64 parsed characters;
web defaults retain 1 MiB and 64 characters. The budgets measure decoded archive
bytes and entry counts, not total process memory. Set either budget to zero to
disable that cache. `clearCache()` drops retained entries but allows pending loads
to finish and repopulate them; cancel the owner scope to end pending work. The
bundled provider also caches the character index. `availableCharacters()` returns
a snapshot; the generic provider's method supports only unsharded profiles.

Remember a provider across character changes. Creating a provider per frame or
character discards useful caches. A screen-owned provider is also valid when
sharing is unnecessary; its scope must be cancelled on screen disposal.

## Native and browser resources

- **Android:** pass `androidBundledDataSource(applicationContext.assets)`.
  `bundled-data` publishes `strokes/all.hzb` in AAR assets. Keep `.hzb` in the
  application `androidResources.noCompress` list. The native reader uses system
  gzip; opaque names prevent Android's automatic `.gz` transformation.
- **JVM:** the default classpath reader finds `strokes/all.hzb` in the published
  artifact and decodes gzip with the JDK. Keep resource paths when making fat JARs.
- **iOS 15+:** copy `bundled-data/src/iosMain/resources/strokes/` into the app
  bundle's `strokes` directory and the data license directory into the app.
  Kotlin frameworks do not automatically embed these files. Published consumers
  can download `engineering.carr:shenzhen-hanzi-animator-bundled-data:0.1.0:ios-data@zip`
  and extract its `strokes/` and `licenses/` directories into the app bundle. Use
  `iosBundledDataSource(bundle, basePath)` for a custom resource bundle. The default
  reader decodes Brotli with Apple's system Compression framework. An explicitly
  generated Apple LZMA profile requires `IosStrokeCompression.Lzma`.
- **Web:** deploy `bundled-data/src/webMain/resources/strokes/`, including
  `index.txt`, manifests, license notices and HTTP sidecars. Published consumers
  can extract `engineering.carr:shenzhen-hanzi-animator-bundled-data:0.1.0:web-data@zip`,
  which includes both `strokes/` and `licenses/`. The profile has 1,024
  shards selected by Unicode scalar modulo 1,024; pass that shard count when using
  a custom reader. Fetch `.hzb`; negotiate `.hzb.br`/`.hzb.gz` on the server with
  correct `Content-Encoding` and `Vary: Accept-Encoding`. Never feed a compressed
  sidecar directly to `BinaryStrokeArchive`. Version asset URLs when updating data.

Use `loading`, `missing` and `error` composable slots to present application copy.
The error slot receives a retry action. Changing character/provider cancels the
old waiter so stale results cannot replace the current glyph.

## Layout and theming

`StrokeOrderLayoutConfig` sets maximum canvas 360 dp, canvas/control gap 8 dp,
default control targets 48 dp, horizontal gap 2 dp and row gap 4 dp. Controls use
`FlowRow` so narrow hosts wrap them. Let your page scroll vertically; avoid fixed
height clipping. `StrokeOrderControlsLabels` exposes every button's accessibility
label. Pass custom `controls` to `HanziStrokePractice` to own the layout.

`StrokeOrderDefaults.style(dark = …)` uses explicit colors independent of
MaterialTheme. Add `-material3` when you want current Material surface/ink colors.
Existing custom component palettes work; pass at least six distinct colors for
the complete bundled mapping or accept neutral fallback for uncovered indexes.

The Canvas package is a separate ES module entry point, documented in
[browser/README](../browser/README.md). It uses the same normalized engine and
localizable labels. Call `dispose()` on removal, and use `updateOptions()` rather
than remounting for styling/callback changes.

Control dimensions can be overridden with positive finite values; 48 dp is the touch-friendly default, not a forced client constraint. Completion callbacks receive the completed attempt’s summary snapshot even when a result callback restarts that same quiz.

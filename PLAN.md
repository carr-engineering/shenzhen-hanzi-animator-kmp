# Architecture & build plan

This library was extracted from stroke-order functionality that lived inline in
a private dictionary app, re-shaped into an idiomatic Compose Multiplatform API
and modelled conceptually (not transliterated) on the Flutter package
`stroke_order_animator`.

## Modules

- **`:library`** — the published artifact
  (`engineering.carr:shenzhen-hanzi-animator`). Targets: `android`, `jvm`,
  `iosArm64`, `iosSimulatorArm64`, `js(browser)`, `wasmJs(browser)`.
  `explicitApi()` is on. Published via the vanniktech maven-publish plugin.
- **`:sample`** — a Compose Multiplatform app (`wasmJs` + `jvm` desktop) that
  exercises the library. Not published.

## Package layout (`engineering.carr.hanzianimator`)

- `core/` — pure, Compose-free logic:
  - `StrokeOrder` — `GlyphPoint`, `Stroke`, `StrokeOrder.parse(...)`. Medians are
    flipped into y-down glyph space at parse time; outlines stay raw and are
    flipped once in the render geometry (the y-flip is defined in one place).
  - `StrokeGeometry` (internal) — viewport transform + polyline helpers.
  - `StrokeAnimation` — `StrokeAnimationPlanner`, a pure time → frame function.
  - `StrokeRecognizer` — the grader (length/coverage/direction + rotation-robust
    normalized discrete Fréchet, later-stroke disambiguation, reversed detection).
  - `QuizLogic` (internal) — pure practice-state reducers.
  - `Gzip` — `expect/actual gunzip` (JVM/Android `GZIPInputStream`, iOS
    `platform.zlib`, browser `DecompressionStream`).
- `data/` — `StrokeOrderProvider` interface and `BundledStrokeOrderProvider`
  (lazy per-shard fetch via `Res.readBytes`, LRU cache, `Mutex`, code-point-safe
  keying for beyond-BMP characters).
- `compose/` — `StrokeOrderController` (snapshot state + frame loop),
  `StrokeOrderStyle`/`StrokeOrderDefaults`, `StrokeGlyphGeometry` (internal),
  `StrokeOrderCanvas`, `HanziGlyph`, `StrokeOrderControls`, and the opinionated
  `HanziStrokeAnimation` / `HanziStrokePractice`.

## Data pipeline

`tools/generate_stroke_data.py` downloads the upstream `hanzi-writer-data`
combined dataset at a pinned commit, validates it (9,574 characters,
stroke/median parity), shards it by `codePoint % 256`, minifies and gzips each
shard (level 9, zeroed mtime), and writes the shards + a character index into
the library's Compose resources. Provenance is recorded in
`tools/DATA_PROVENANCE.md`. The shards are committed.

## Rendering performance

- Per `StrokeOrder`: parse + y-flip each outline once, precompute median reveal
  `Path` + `PathMeasure` (`StrokeGlyphGeometry`, built with `remember`).
- Per frame: only the draw phase reads the controller's `animationFrame` (a
  snapshot read), so animation repaints without recomposing the canvas or its
  siblings. Reveals use `PathMeasure.getSegment` into a reused scratch path;
  the user's trace is an incremental `Path` + a revision counter.

## Notable deviations from the original plan

- **`iosX64` dropped** — Compose Multiplatform 1.11 does not publish an
  `iosX64` variant, so a Compose-dependent library cannot target it. Apple-
  Silicon simulators use `iosSimulatorArm64`.
- **Generator + font tools are Python**, not a Kotlin `.main.kts` — no `kotlin`
  CLI was available, and the plan already used Python for font subsetting.
- **Responsive layout uses `BoxWithConstraints`**, not `material3-adaptive` —
  avoids a fragile version match for one breakpoint check.
- **Signing is conditional** on credentials so `publishToMavenLocal` works
  locally without a GPG key; CI/Central provides the key.

## Phase gates

Each phase ended on a green build + commit: P0 baseline/licenses, P1 module
restructure (`+ publishToMavenLocal`), P2 core + ported tests, P3 data pipeline,
P4 Compose layer + controller, P5 sample, P6 publishing/docs/CI, P7 verification.

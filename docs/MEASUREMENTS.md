# Compression, browser delivery and layout measurements

Measured on 2026-10-12 from the checked-in generated manifests and current Canvas
build. [measurement-report.json](measurement-report.json) records exact bytes,
source hash, generator versions and browser dimension observations. Byte counts
below are data/code payloads; they do not claim installed app size or App Store /
Play download size.

## Lossless data profiles

The canonical enriched input contains 9,574 characters. All profiles preserve
stroke geometry/order, every median point, radical flags, component occurrences,
label order and null membership. HZB1 has a 20-byte header and CRC32 covering the
**directory plus payload**, with checked record bounds and lengths. It is indexed
binary data; gzip/Brotli are delivery encodings around it.

| Profile | Files / codec | Payload bytes | Decoder |
| --- | --- | ---: | --- |
| Native decoded archive | One HZB1 archive | 8,672,382 | Stdlib-only codec |
| Android/JVM delivery | One gzip-9 archive, opaque `.hzb` name | 6,865,840 | Android/JDK system gzip |
| iOS delivery | One Brotli-11 archive | 5,825,784 | Apple system Compression, iOS 15+ |
| Web raw blocks | 1,024 HZB1 blocks | 8,692,842 total | Browser returns bytes unchanged |
| Web gzip sidecars | Per-block gzip | 7,432,757 total | HTTP Content-Encoding |
| Web Brotli sidecars | Per-block Brotli | 7,355,495 total | HTTP Content-Encoding |

Native profiles intentionally favor compression across the full corpus. A
native cold read expands the archive once; the default 12 MiB decoded cache can
retain it, and a 64-character parsed cache bounds retained glyph objects. These
are cache budgets, not peak memory measurements: compressed input, decode buffers,
defensive archive copies and parsed models can coexist during a cold load.

Web favors small independent requests. A raw block's median is 8,462 bytes
(range 2,679–16,370); negotiated gzip median is 7,250 bytes and Brotli median is
7,171.5 bytes. Only requested codepoint-modulo blocks are loaded, and the web
cache defaults to 1 MiB decoded bytes plus 64 parsed characters. Server negotiation
is required to achieve compressed transfers; the raw files work on ordinary
static hosts. Index and license/manifest overhead is additional.

The current enriched corpus before this binary refactor occupied 13,046,133
bytes in JSON/gzip shards plus index. The new native gzip profile is 47.37% smaller
and the iOS Brotli profile is 55.34% smaller than that current source baseline.
The earlier review recorded 12,850,374 compressed bytes and 30,882,334 decoded JSON
bytes before the component metadata update; those historical values describe a
different corpus revision and are not the refactor comparison baseline.
The application measurements below use newly built distributions of this refactor.

## Android and iOS application packaging

The local Android Release APK is **8,150,994 bytes**, signed with the sample's
debug key. Expanded ZIP entries total 9,224,884 bytes, including 2,025,996 bytes
of DEX. `assets/strokes/all.hzb` is present at exactly 6,865,840 bytes with ZIP
method STORE (0), preserving the native gzip payload and its opaque filename.
All four dataset license files are present, and no font file is included.

The prior shrinking experiment recorded 1,028,627 uncompressed DEX bytes; this
sample records 2,025,996. The example, module structure and actual shrinker now
differ, so this is not an isolated library-code comparison. The APK saving comes
primarily from compressed data and removal of the sample font from mobile.
Modular dependencies and app-supplied networking reduce what hosts must include,
but the full sample's code has not become smaller in every measure. Use a matched
minimal host and the app's own dependency closure when budgeting incremental code.

The AAB container is **10,069,669 bytes**. This is the bundle file itself, not a
device-specific Google Play download estimate. The earlier comparable shrunk
sample APK was 16,493,168 bytes; this local APK is 50.58% smaller. That comparison
includes application packaging, runtime and resource changes, so it does not
measure the incremental cost of embedding the library in another application.

The built and installed arm64 iOS Simulator **Debug** app totals 56,377,957 file
bytes, including the 5,825,784-byte native Brotli dataset. On iPhone 16e / iOS
26.0, the installed smoke check passed: all 9,574 characters indexed, 永 usable
for practice, 森 repeated component membership preserved, and the native system
Brotli reader loaded the data from the application bundle. All four notices were
present. This Debug simulator app contains development binaries and is not an
App Store delivery estimate or a Release-device measurement.

Raw evidence is in
[`platform-artifact-sizes.json`](../review-assets/current-implementation/platform-artifact-sizes.json)
and [`ios-installed-smoke.json`](../review-assets/current-implementation/ios-installed-smoke.json).

## Further lossless compression experiments

The reproducible [benchmark](../tools/benchmark_compression.py) compares canonical
JSON against indexed HZB1 with gzip, Brotli and XZ/LZMA, plus binary Zstandard.
All streams round-trip exactly; results are in [compression-experiment.json](compression-experiment.json).
The monolithic canonical JSON is 31,574,194 raw bytes, 12,511,329 with gzip9,
and 9,540,467 with Brotli11. The binary representation is 8,672,382 raw bytes,
6,865,840 with gzip9 and 5,825,784 with Brotli11. This isolates the format gain
from the old sharding overhead.

Zstandard19/22 measured 5,978,328/5,978,259 bytes: slightly larger than Brotli11,
while requiring a separate decoder on the intended mobile/browser paths. XZ/LZMA9
measured 5,631,744 bytes; Apple's supported LZMA framing measures 5,631,784 bytes,
only 194,000 bytes smaller than the selected Brotli profile. Host timings are
single-run observations, not mobile latency predictions; LZMA's slower decoding
was the tradeoff behind selecting Brotli. None of these experiments changes
shipped geometry or adds a runtime dependency.

The unsigned **physical arm64 iOS Release example** contains 39,942,577 file
bytes, including a 33,987,520-byte executable and the full 5,825,784-byte dataset.
It has iOS 15 minimum deployment metadata and all required data notices.
A symbol-stripping experiment on a build copy reduced the executable to
25,392,056 bytes, saving 8,595,464 bytes; the projected app file total is
31,347,113 bytes. The original app is unchanged. The stripped copy has not been
executed on a physical device. These are complete Compose example sizes, not
the library's incremental cost or an App Store download estimate. See
[iOS Release evidence](../review-assets/current-implementation/ios-release-size.json).
An app already using Compose shares its runtime; custom native renderers can use
only the headless core/data modules. Correct archive symbol stripping is an
additional production opportunity.

## Standalone Canvas JavaScript

`browser/dist/size-report.json` reports **301,198 bytes raw / 61,378 bytes gzip**
for the Canvas adapter plus its shared Kotlin engine. The ES module has TypeScript
declarations and no external Compose/Skiko/Wasm/font/frontend runtime. The build
sums gzip-9 per `.js`/`.mjs` file and enforces a 256,000-byte (250 KiB) budget.

These counts exclude the demo document/style, source maps, declarations and
requested data shards. They are compressed estimates, not a measured live-host
request total or startup latency. Loading two different initial characters can
request two shards; the dataset is separately deployed and licensed. Compare a
real network waterfall under the intended CDN policy before setting a startup
latency target.

The newly built Compose Wasm sample's document/style, application JS/Wasm,
Skiko Wasm and sample-only font total **15,921,079 raw bytes / 6,470,812 gzip
bytes**. This excludes requested data shards, source maps and notices. Skiko
accounts for 3,319,858 gzip bytes; the 3,297,592-byte font accounts for 2,044,361
gzip bytes. No old JSON data assets remain in that production distribution.
These per-file gzip estimates require HTTP compression to match transfer sizes.
This optional sample is a separate UI integration choice. Historical
`review-assets/size-measurements.json` describes a pre-refactor APK and Compose
web build and must not be reused as new release sizes.

## Responsive layout

The library exposes maximum canvas 360 dp, canvas/control gap 8 dp, minimum
control target 48 dp, horizontal gap 2 dp and wrapping row gap 4 dp. Samples use
vertical scrolling, content width at most 960 dp, and split panes when content
width reaches 720 dp. Host applications can override layout and labels.

The Canvas demo has 16-pixel side padding, at most 960 pixels of content and a
752-pixel viewport breakpoint for two columns (720 pixels of inner content).
Input actions stack below 480 viewport pixels. Checked widths were 280, 320, 360,
390, 479, 480, 719, 751, 752, 960 and 1,440 pixels. At every width, document
`scrollWidth` equaled viewport width and overflowing control count was zero.
Canvases ranged from 248 pixels at viewport 280 to the 360-pixel cap; at the
752-pixel two-pane transition each canvas measured 348 pixels.

These observations establish sizing and horizontal containment for the current
browser demo. They do not establish screen-reader behavior, all localized label
lengths, device touch behavior or native screenshot parity. Validate those on the
intended hosts before release.

## Compose sample verification

The actual production Wasm sample was checked at 280, 320, 480 and 1,440 pixels.
Glyph and font requests succeeded, with no JavaScript errors or horizontal
page overflow. Controls wrap, the page scrolls at short heights, and a real drawn
stroke was accepted. Quiz mode, accepted strokes and feedback survive resizing
across the two-pane breakpoint; restart clears progress and restores instructions.
Evidence: [Compose layout checks](../review-assets/current-implementation/compose-layout-checks.json).

The Android sample now uses the verified compatible R8 9.1.56 override; the
release mapping confirms that actual compiler and its build log has no metadata
warnings. See [toolchain diagnosis and consumer requirements](r8-toolchain.md).

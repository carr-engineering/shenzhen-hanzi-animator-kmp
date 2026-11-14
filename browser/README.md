# Browser Canvas adapter

This ES module draws stroke commands on Canvas and uses the same Kotlin core session, animation planner and recognition engine as the Compose library. It has no Compose, Skiko, WebAssembly, font, HTTP client or frontend framework runtime.

```ts
import {mountHanziPractice} from '@hanzi-animator/canvas';
const practice = mountHanziPractice(document.querySelector('#practice')!, {
  character: '永',
  data: {baseUrl: '/strokes/'},
  maxCanvasSize: 360,
  onComplete: summary => console.log(summary.totalMistakes)
});
// Existing clients can supply a provider or a decoded binary byte loader.
practice.updateOptions({labels: {play: '播放', quiz: '练习'}});
practice.dispose();
```

Use `createHanziAnimator(canvas, options)` for custom controls. Playback, stepping, reveal, reset, quiz, hints and outline visibility are methods on the returned instance. Call `setCharacter` to load, `updateOptions` to update styling/configuration/callbacks and `dispose` when removing it. Changing animation or recognition configuration restarts the current session. Stale and cancelled requests cannot replace the current character. The canvas owns touch gestures only during an active quiz; the rest of the page scrolls normally.

`provider.load(character, signal)` returns a normalized `StrokeOrder` command-buffer model or an order obtained from `createBinaryStrokeOrderProvider`. The shared engine validates and defensively copies application-supplied models. Binary-decoded orders serialize to ordinary JSON command-buffer models and can be restored by a custom provider; command codes are MOVE=0, LINE=1, QUAD=2, CUBIC=3 and CLOSE=4, with absolute y-down coordinates. A custom provider can delegate to that provider while integrating application caching. Its `loadBytes(resourceKey, signal)` adapter **must return decoded HZB1 bytes**. Embedded compressed files require system decompression first. On the web, fetch `.hzb` and let HTTP `Content-Encoding: br` or `gzip` decode the response. Never fetch `.br`/`.gz` sidecars directly as decoded bytes. The default source has 1,024 codepoint-modulo shards, a 1 MiB decoded-block cache, a 64-character parsed cache and per-block concurrent request deduplication. Each concurrent consumer can cancel independently.

Style overrides include the Forest/bronze component palette, stroke/outline/grid colors, background and glyph-space stroke widths. Empty `componentColors` restores radical coloring. Light/dark palettes and defaults are exported. `maxCanvasSize` defaults to 360 CSS pixels and `padding` to 12 pixels. Mounted controls wrap and default to 48-pixel minimum targets with 2/4-pixel horizontal/vertical gaps and an 8-pixel canvas gap. Override these with `controlMinSize`, `controlHorizontalGap`, `controlVerticalGap` and `canvasControlGap`; every visible control/status label can be localized through `labels`. Supply your own DOM via the canvas-only API for full control over layout and loading/error rendering.

## Build and demo

From the repository root, build `:browser-engine:jsBrowserProductionLibraryDistribution`, then run `npm install` and `npm run build` in this directory. The script copies the generated ES module and web data into `demo/strokes`, emits declarations and enforces the 250 KiB gzip JavaScript budget. `HANZI_ENGINE_DIR` overrides the engine artifact directory; `TSC_JS` can identify an existing TypeScript compiler.

Run `npm test` after building. Run `npm run demo` and open `http://127.0.0.1:8085`. The server negotiates existing Brotli/gzip sidecars. For production, deploy `demo/strokes` with Content-Encoding negotiation, `Vary: Accept-Encoding` and versioned immutable caching. The library package includes code only; dataset deployment and its required notices are separate. The local demo server is development tooling.

The demo uses two panes when content width reaches 720 pixels, stacked actions below 480 pixels, a 960-pixel content maximum and vertical scrolling. Browser support requires ES2022, Canvas 2D/Path2D, ResizeObserver, Pointer Events and AbortController.

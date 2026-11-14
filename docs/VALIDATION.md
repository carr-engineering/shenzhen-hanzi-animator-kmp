# Implementation validation — 2026-10-12

The approved refactor is implemented. These are local results for the current
working tree, not a claim that a public release has already been published.

| Check | Result |
| --- | --- |
| Core, codec, JSON, data, bundled data and Compose JVM tests | 67 passed |
| Core, codec, JSON, data and system Brotli iOS simulator tests | 38 passed |
| Browser adapter tests, including strict custom data and callback reentrancy | 12 passed |
| Generator tests | 5 passed |
| Isolated published-artifact consumer tests | 5 passed; Material example compiled |
| Enriched corpus parity | All 9,574 characters, every outline/median and component field |
| Android release | APK/AAB built; actual R8 9.1.56; metadata warnings resolved |
| SwiftUI host | Installed simulator resource smoke passed; unsigned physical arm64 Release built |
| Compose iOS test sources | Compiled; optional Material module has no test sources |
| Compose web | Production build and actual browser interaction passed |
| Configuration cache | Reused successfully on unchanged Android build |

The external consumer resolved 14 root/JVM publications from an isolated Maven
repository, checked dependency closures and both published resource ZIPs. Core
and codec require only Kotlin stdlib/annotations. Networking is supplied by the
host; JSON and Material adapters are optional. Compose's own transitive saved-state
dependency uses serialization-core; the JSON importer is not pulled in.

Rendered layout checks cover widths down to 280 pixels and short viewports.
Controls wrap, canvases stay capped, and the page scrolls. Actual tracing,
resizing with quiz progress, restart, loading and feedback were verified.
Completion summaries survive a host callback restarting the quiz.

See [measurements](MEASUREMENTS.md), [publication evidence](publication-smoke-report.json),
[migration](MIGRATION.md), and [release instructions](RELEASING.md).

Remaining release checks are physical Android/iOS device behavior, signed/archive
validation and actual store delivery sizes. No Android device/emulator was
connected for installation. The iOS stripped executable is a size experiment,
not a tested signed device build. Set the real repository metadata and signing
credentials before publishing; no remote publication was performed.

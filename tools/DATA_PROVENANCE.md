# Bundled stroke data provenance

The lossless HZB1 packs in `bundled-data/src/*Main/resources/strokes/` are generated
by `tools/generate_stroke_data.py`. The archived input is the current complete
corpus, including occurrence-based component metadata, previously stored in the
256 JSON/gzip shards. Generation never substitutes an older Git version.

| Field | Value |
| --- | --- |
| Glyph source | https://github.com/chanind/hanzi-writer-data |
| Glyph commit | `68d10a4b21150cae5e1ebbd223eed289cf32d90c` |
| Glyph source file | `data/all.json` |
| Component source | https://github.com/skishore/makemeahanzi |
| Component commit | `618dbab8a8ddefb958763c8b4afbaa741a4460de` |
| Component source fields | `dictionary.txt`: `decomposition`, `matches` |
| Characters | 9,574 |
| Canonical enriched input | `tools/fixtures/source-data.json.gz` |
| Decoded input SHA-256 | `d6db1e36007c41b2d68132975bfc132a64f50b9ceae4254730918ba8ea3d5ced` |
| Original resource hashes | `tools/fixtures/source-manifest.json` |
| HZB1 decoded native archive | 8,672,382 bytes |
| Android/JVM gzip 9 archive | 6,865,840 bytes |
| iOS Brotli 11 archive | 5,825,784 bytes |
| Web | 1,024 raw HZB1 blocks, gzip/Brotli HTTP sidecars |

The measured defaults were generated with Python 3.14.7, zlib 1.2.12, Node 22.19.0
and Node's built-in Brotli encoder. Profile manifests record generator tool
versions and decoded resource sizes/hashes. The source hash is mandatory for
custom inputs. Different compressor versions may alter encoded bytes while
preserving identical decoded data; use the recorded versions for byte-identical
release regeneration.

## Licensing and modifications

The pinned **hanzi-writer-data glyph dataset is under the Arphic Public License**,
not the MIT license used by Hanzi Writer's JavaScript application. Glyphs derive
from Make Me a Hanzi and the Arphic PL fonts. The complete pinned upstream
`ARPHICPL.TXT` is included as
`bundled-data/src/commonMain/resources/licenses/Arphic-Public-License.txt`.

Component metadata is derived from Make Me a Hanzi's LGPL-3.0-or-later
`dictionary.txt`. Its upstream COPYING, LGPL and GPL texts are included alongside
Arphic's license. The complete enriched source archive is retained for inspection
and modification; regeneration instructions are below.

Modifications are lossless binary encoding, occurrence-based component label
extraction from decomposition trees, per-stroke component memberships, target
compression profiles, and indexing. Stroke order, numeric geometry, radical
flags, repeated component occurrences, label order and unmapped (`null`)
memberships are preserved. No unknown component is inferred or merged by label.

## Regeneration and tests

Neither the packs nor the source corpus are committed. The corpus is pinned as the
release asset [`stroke-source-d6db1e36`](https://github.com/carr-engineering/shenzhen-hanzi-animator-kmp/releases/tag/stroke-source-d6db1e36)
(archive SHA-256 `04d7c735a9ebd3273915bbfd7798924643ad81c3f47991b9dd0fe3e0f64268f1`).
Run this before building:

```sh
tools/prepare_bundled_data.sh            # download + verify the corpus, generate the packs if needed
python3 -m unittest discover -s tools -p test_generate_stroke_data.py
```

It downloads and verifies the corpus when it is missing, and runs
`tools/generate_stroke_data.py` unless each profile's `manifest.json` already names this
corpus (`--force` regenerates anyway). Any build task that packages resources fails with
that instruction while the packs are missing. With the recorded tool versions, generation is
byte-identical to the packs that were previously committed.

Generation validates the pinned source, then numerically decodes and compares
every character in every generated profile before replacing the output resources.
Output directories are staged and individually replaced with rollback on a failed
rename. gzip timestamps and filename headers are fixed. Records and indexes are
ordered by Unicode code point.

Options: `--web-shards 1024`, `--native-shards 1|16`, `--characters '你好'`,
`--ios-codec brotli|apple-lzma`, `--source PATH --source-sha256 HASH`, and
`--output PATH`. Apple LZMA generation uses macOS's built-in Compression library;
select `IosStrokeCompression.Lzma` in the corresponding iOS reader. The default
packaging/readers expect one native archive and 1,024 web shards; supply matching
`shardCount` for custom profiles.

Web `.hzb.br` and `.hzb.gz` are server negotiation sidecars. Serve the `.hzb` URL
with `Content-Encoding: br` or `gzip` and `Vary: Accept-Encoding`; a normal browser
fetch then returns decoded HZB1 bytes. Do not explicitly request a sidecar and
pass its compressed contents to the codec. Native compressed data deliberately
uses opaque `.hzb` names to prevent Android asset processing from stripping a
`.gz` suffix and decompressing the asset.

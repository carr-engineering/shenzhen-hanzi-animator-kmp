# Third-party source and distribution record

The implementation is MIT licensed. Data and font licenses remain separate.
The paths below contain the actual source/provenance records and full texts used
by this repository, rather than a generic third-party license list.

| Material | Pinned source | License and packaged notice |
| --- | --- | --- |
| Glyph outlines, medians and stroke order | `chanind/hanzi-writer-data`, `68d10a4b21150cae5e1ebbd223eed289cf32d90c`, `data/all.json` | Arphic Public License; `Arphic-Public-License.txt` |
| Component occurrence/decomposition metadata | `skishore/makemeahanzi`, `618dbab8a8ddefb958763c8b4afbaa741a4460de`, `dictionary.txt` decomposition/matches | LGPL-3.0-or-later; upstream COPYING, LGPL and GPL, including Unicode/Unihan notice |
| Sample-only Noto Sans SC | `google/fonts`, `2894aab31764f10f29c421bdfd2340d3b382d384`, `ofl/notosanssc/NotoSansSC[wght].ttf` | SIL OFL 1.1; sample font directory `OFL.txt` |

The glyph dataset's Arphic license applies even though Hanzi Writer's JavaScript
application uses MIT. Upstream Make Me a Hanzi COPYING states that graphics.txt
derives from Arphic fonts, and dictionary.txt derives from Unihan/CJKlib. Its
preserved LGPL file includes the Unicode notice. All full dataset texts are in
[`bundled-data/src/commonMain/resources/licenses`](../bundled-data/src/commonMain/resources/licenses)
and copied in [`docs/licenses`](licenses).

## Dataset source and modifications

The complete enriched input is [`tools/fixtures/source-data.json.gz`](../tools/fixtures/source-data.json.gz).
Its decoded SHA-256 is
`d6db1e36007c41b2d68132975bfc132a64f50b9ceae4254730918ba8ea3d5ced`.
[`source-manifest.json`](../tools/fixtures/source-manifest.json) retains original
JSON resource hashes and upstream revisions.
[`DATA_PROVENANCE.md`](../tools/DATA_PROVENANCE.md) describes the source fields,
lossless transformation and reproduction commands.

Modifications are occurrence-based component extraction, per-stroke membership,
command/median binary encoding, indexing, sharding and target compression. No
stroke order, numeric geometry, radical flags, repeated component labels or
unmapped memberships are discarded. Every generated profile is decoded and
compared against all 9,574 enriched input records. Source and
[`generate_stroke_data.py`](../tools/generate_stroke_data.py) are retained to allow
inspection and modification of the distributed representation.

Published `bundled-data` includes the notices; its `ios-data` and `web-data` ZIP
classifiers include `strokes/` and `licenses/`. Native host applications and web
hosts must preserve both. The Canvas npm package contains code without dataset
files; separately deploying the web data carries its own license/source notices.

## Font source and modifications

The Compose Wasm sample alone packages
[`noto_sans_sc_subset.ttf`](../sample/src/wasmJsMain/composeResources/font/noto_sans_sc_subset.ttf).
The pinned upstream source SHA-256 is
`a3041811a78c361b1de50f953c805e0244951c21c5bd412f7232ef0d899af0da`;
the pinned OFL SHA-256 is
`1c05c68c34f9708415aada51f17e1b0092d2cea709bf4a94cd38114f9e73d7d9`.
The font's copyright is Adobe 2014–2021, Reserved Font Name `Source`; Noto is a
Google trademark. The upstream copyright and full license remain in
[`OFL.txt`](../sample/src/wasmJsMain/composeResources/font/OFL.txt).

The subset includes dataset characters, printable ASCII and sample punctuation
(9,677 code points), fixes weight to 400, and verifies cmap/static-weight output.
It has no fallback code points. The source, index, output hashes, tool version and
byte count are recorded in
[`FONT-PROVENANCE.json`](../sample/src/wasmJsMain/composeResources/font/FONT-PROVENANCE.json).
Reproduce using the hash-pinned requirements and generator:

```sh
python3 -m pip install --require-hashes -r tools/font-requirements.txt
python3 tools/subset_font.py
```

The generator verifies the exact source/font-license checksums and fontTools
version before replacing output; local verified `--source`/`--license` files can
avoid downloading. The font is absent from published library modules, native
samples and the standalone Canvas adapter.

## Conceptual references

[Hanzi Writer](https://github.com/chanind/hanzi-writer) (MIT application) provided
animation/quiz conventions. [stroke_order_animator](https://pub.dev/packages/stroke_order_animator)
(BSD-3-Clause) was a conceptual controller API reference; no code was copied.
These references do not change the licenses of the source data or font.

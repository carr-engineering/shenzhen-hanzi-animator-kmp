# Sample font provenance

`noto_sans_sc_subset.ttf` is derived from **Noto Sans SC**, under SIL Open Font
License 1.1. Copyright 2014–2021 Adobe, with Reserved Font Name “Source”.
The complete verified upstream license is included in `OFL.txt`.

- Source: https://raw.githubusercontent.com/google/fonts/2894aab31764f10f29c421bdfd2340d3b382d384/ofl/notosanssc/NotoSansSC%5Bwght%5D.ttf
- Pinned commit: `2894aab31764f10f29c421bdfd2340d3b382d384`
- Source SHA-256: `a3041811a78c361b1de50f953c805e0244951c21c5bd412f7232ef0d899af0da`
- License SHA-256: `1c05c68c34f9708415aada51f17e1b0092d2cea709bf4a94cd38114f9e73d7d9`
- Generator: `tools/subset_font.py`, fontTools `4.60.1`
- Output SHA-256: `0f97610fcc4bc7b94a4d06d8f5f5e9ce67111317ea4a18d1096b497037c7851e`
- Output size: 3,297,592 bytes; static Regular weight 400

Modifications: subset to the pinned 9,574-character dataset, printable ASCII and
sample punctuation; instantiate the variable weight axis at 400. Ordering and
font timestamps are deterministic. The complete requested character set is
verified against the generated font cmap. This font is packaged only
by the Wasm Compose sample, and is excluded from native samples, the standalone
Canvas browser adapter and all published library artifacts.

Regenerate with `python3 -m pip install --require-hashes -r tools/font-requirements.txt`
then `python3 tools/subset_font.py`. Verified local source/license files can be
supplied with `--source PATH --license PATH` for offline builds.

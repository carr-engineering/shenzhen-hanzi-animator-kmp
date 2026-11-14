#!/usr/bin/env python3
"""Build the sample-only Wasm font from a verified, pinned Noto Sans SC source.

Install the pinned requirements from tools/font-requirements.txt, then run:
    python3 tools/subset_font.py [--source PATH] [--license PATH]

Source/Unicode-set SHA-256 hashes and fontTools version are verified. Fonts are
staged, roundtrip inspected and replaced only after successful validation. The
font is never included in native samples or published library artifacts.
"""
from __future__ import annotations

import argparse
import hashlib
import io
import json
import os
import shutil
from pathlib import Path
import tempfile
import urllib.request

import fontTools
from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont

ROOT = Path(__file__).resolve().parent.parent
SOURCE_COMMIT = "2894aab31764f10f29c421bdfd2340d3b382d384"
SOURCE_URL = f"https://raw.githubusercontent.com/google/fonts/{SOURCE_COMMIT}/ofl/notosanssc/NotoSansSC%5Bwght%5D.ttf"
LICENSE_URL = f"https://raw.githubusercontent.com/google/fonts/{SOURCE_COMMIT}/ofl/notosanssc/OFL.txt"
SOURCE_SHA256 = "a3041811a78c361b1de50f953c805e0244951c21c5bd412f7232ef0d899af0da"
LICENSE_SHA256 = "1c05c68c34f9708415aada51f17e1b0092d2cea709bf4a94cd38114f9e73d7d9"
FONTTOOLS_VERSION = "4.60.1"
INDEX = ROOT / "bundled-data/src/webMain/resources/strokes/index.txt"
OUTPUT_DIR = ROOT / "sample/src/wasmJsMain/composeResources/font"
EXTRA_CHARS = "·×→←✓…—’"


def verified_bytes(local: Path | None, url: str, expected: str) -> bytes:
    raw = local.read_bytes() if local is not None else urllib.request.urlopen(url, timeout=60).read()
    if hashlib.sha256(raw).hexdigest() != expected:
        raise ValueError(f"source checksum mismatch: {local or url}")
    return raw


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path)
    parser.add_argument("--license", type=Path)
    parser.add_argument("--output", type=Path, default=OUTPUT_DIR)
    args = parser.parse_args()
    if fontTools.version != FONTTOOLS_VERSION:
        raise ValueError(f"Use fontTools {FONTTOOLS_VERSION}, found {fontTools.version}")
    index = INDEX.read_bytes()
    data_manifest = json.loads((INDEX.parent / "manifest.json").read_text())
    characters = set(index.decode("utf-8")) | {chr(n) for n in range(0x20, 0x7f)} | set(EXTRA_CHARS)
    source = verified_bytes(args.source, SOURCE_URL, SOURCE_SHA256)
    license_text = verified_bytes(args.license, LICENSE_URL, LICENSE_SHA256)
    font = TTFont(io.BytesIO(source), recalcTimestamp=False)
    requested = {ord(char) for char in characters}
    missing = requested - set(font.getBestCmap())
    if missing:
        raise ValueError(f"source font lacks required glyphs: {sorted(missing)}")
    included = requested - missing
    options = subset.Options()
    options.layout_features = ["*"]
    options.name_IDs = ["*"]
    options.recalc_bounds = True
    options.recalc_timestamp = False
    options.drop_tables = []
    subsetter = subset.Subsetter(options=options)
    subsetter.populate(unicodes=sorted(included))
    subsetter.subset(font)
    if "fvar" in font:
        instantiateVariableFont(font, {"wght": 400}, inplace=True)
    font.recalcTimestamp = False
    output = io.BytesIO()
    font.save(output, reorderTables=True)
    raw = output.getvalue()
    decoded = TTFont(io.BytesIO(raw), recalcTimestamp=False)
    if set(decoded.getBestCmap()) != included or "fvar" in decoded:
        raise ValueError("generated font failed cmap/static-weight validation")
    metadata = {
        "sourceCommit": SOURCE_COMMIT, "sourceUrl": SOURCE_URL, "sourceSha256": SOURCE_SHA256,
        "licenseUrl": LICENSE_URL, "licenseSha256": LICENSE_SHA256,
        "fontTools": FONTTOOLS_VERSION, "weight": 400, "glyphCodePoints": len(included),
        "indexSha256": hashlib.sha256(index).hexdigest(), "datasetSourceSha256": data_manifest["sourceSha256"],
        "outputSha256": hashlib.sha256(raw).hexdigest(), "outputBytes": len(raw),
        "fallbackCodePoints": sorted(missing),
    }
    notice = f"""# Sample font provenance

`noto_sans_sc_subset.ttf` is derived from **Noto Sans SC**, under SIL Open Font
License 1.1. Copyright 2014–2021 Adobe, with Reserved Font Name “Source”.
The complete verified upstream license is included in `OFL.txt`.

- Source: {SOURCE_URL}
- Pinned commit: `{SOURCE_COMMIT}`
- Source SHA-256: `{SOURCE_SHA256}`
- License SHA-256: `{LICENSE_SHA256}`
- Generator: `tools/subset_font.py`, fontTools `{FONTTOOLS_VERSION}`
- Output SHA-256: `{metadata['outputSha256']}`
- Output size: {len(raw):,} bytes; static Regular weight 400

Modifications: subset to the pinned 9,574-character dataset, printable ASCII and
sample punctuation; instantiate the variable weight axis at 400. Ordering and
font timestamps are deterministic. The complete requested character set is
verified against the generated font cmap. This font is packaged only
by the Wasm Compose sample, and is excluded from native samples, the standalone
Canvas browser adapter and all published library artifacts.

Regenerate with `python3 -m pip install --require-hashes -r tools/font-requirements.txt`
then `python3 tools/subset_font.py`. Verified local source/license files can be
supplied with `--source PATH --license PATH` for offline builds.
"""
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="hanzi-font-", dir=args.output.parent) as temporary:
        stage = Path(temporary) / "font"
        stage.mkdir()
        files = {"noto_sans_sc_subset.ttf": raw, "OFL.txt": license_text,
                 "FONT-NOTICE.md": notice.encode(), "FONT-PROVENANCE.json": (json.dumps(metadata, indent=2) + "\n").encode()}
        for name, contents in files.items():
            (stage / name).write_bytes(contents)
        backup = args.output.with_name(args.output.name + ".previous")
        if backup.exists():
            raise RuntimeError(f"recover/remove previous font generation first: {backup}")
        if args.output.exists():
            os.replace(args.output, backup)
        try:
            os.replace(stage, args.output)
        except BaseException:
            if backup.exists():
                os.replace(backup, args.output)
            raise
        if backup.exists():
            shutil.rmtree(backup)
    print(json.dumps(metadata, indent=2))


if __name__ == "__main__":
    main()

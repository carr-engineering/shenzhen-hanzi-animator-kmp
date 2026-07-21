#!/usr/bin/env python3
"""Subset Noto Sans SC to just the glyphs the sample app needs.

WebAssembly Compose cannot render CJK text without a bundled font, and the full
Noto Sans SC is ~17 MB. This subsets it to the bundled stroke dataset's
characters plus ASCII and a few UI punctuation marks, and pins the variable
weight axis to Regular, producing a small font for the `:sample` module.

Usage:
    python3 tools/subset_font.py [--source PATH]

If --source is omitted the upstream variable font is downloaded. The output is
committed to the sample's Compose resources.
"""
from __future__ import annotations

import argparse
import gzip
import os
import urllib.request

from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont

# Upstream Noto Sans SC variable font (SIL Open Font License 1.1).
SOURCE_URL = "https://cdn.jsdelivr.net/gh/google/fonts@main/ofl/notosanssc/NotoSansSC%5Bwght%5D.ttf"

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
INDEX_GZ = os.path.join(
    REPO_ROOT, "library", "src", "commonMain", "composeResources", "files", "strokes", "index.txt.gz"
)
OUTPUT_PATH = os.path.join(
    REPO_ROOT, "sample", "src", "commonMain", "composeResources", "font", "noto_sans_sc_subset.ttf"
)
NOTICE_PATH = os.path.join(
    REPO_ROOT, "sample", "src", "commonMain", "composeResources", "font", "FONT-NOTICE.md"
)

# UI punctuation used by the sample beyond printable ASCII.
EXTRA_CHARS = "·×→←✓…—’"


def dataset_characters() -> set[str]:
    return set(gzip.open(INDEX_GZ, "rb").read().decode("utf-8"))


def load_source(source: str | None) -> TTFont:
    if source and os.path.exists(source):
        print(f"Using source font {source}")
        return TTFont(source)
    print(f"Downloading source font from {SOURCE_URL}")
    data = urllib.request.urlopen(SOURCE_URL).read()
    tmp = os.path.join(os.path.dirname(OUTPUT_PATH), "_source.ttf")
    os.makedirs(os.path.dirname(tmp), exist_ok=True)
    with open(tmp, "wb") as f:
        f.write(data)
    font = TTFont(tmp)
    os.remove(tmp)
    return font


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", help="Path to a local Noto Sans SC variable font.")
    args = parser.parse_args()

    chars = dataset_characters()
    chars.update(chr(c) for c in range(0x20, 0x7F))  # printable ASCII
    chars.update(EXTRA_CHARS)
    print(f"Subsetting to {len(chars)} characters")

    font = load_source(args.source)

    options = subset.Options()
    options.layout_features = ["*"]
    options.name_IDs = ["*"]
    options.recalc_bounds = True
    options.drop_tables = []
    subsetter = subset.Subsetter(options=options)
    subsetter.populate(text="".join(chars))
    subsetter.subset(font)

    # Pin the variable weight axis to Regular so the result is a static font.
    if "fvar" in font:
        instantiateVariableFont(font, {"wght": 400}, inplace=True)

    os.makedirs(os.path.dirname(OUTPUT_PATH), exist_ok=True)
    font.save(OUTPUT_PATH)
    size_kb = os.path.getsize(OUTPUT_PATH) / 1024
    print(f"Wrote {OUTPUT_PATH} ({size_kb:.0f} KB)")

    write_notice()
    print(f"Wrote {NOTICE_PATH}")


def write_notice() -> None:
    content = """# Font notice

`noto_sans_sc_subset.ttf` is a subset of **Noto Sans SC**, licensed under the
SIL Open Font License, Version 1.1.

- Upstream: https://github.com/google/fonts/tree/main/ofl/notosanssc
- License: https://scripts.sil.org/OFL
- Copyright: Copyright 2014-2021 Adobe (http://www.adobe.com/), with Reserved
  Font Name 'Source'. Noto is a trademark of Google LLC.

This file contains only the glyphs used by the sample application (the bundled
stroke dataset's characters, ASCII, and a few UI punctuation marks), with the
variable weight axis pinned to Regular. Regenerate with
`python3 tools/subset_font.py`.

The font is used only by the `:sample` module and is not part of the published
`shenzhen-hanzi-animator` library artifact.
"""
    with open(NOTICE_PATH, "w", encoding="utf-8") as f:
        f.write(content)


if __name__ == "__main__":
    main()

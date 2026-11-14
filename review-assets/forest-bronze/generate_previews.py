"""Generate exact-vector palette comparisons from the bundled character shards.

Run from any directory with Python 3. PNGs can be rendered from the resulting
SVGs using sharp, a browser, or any SVG renderer; no approximation of strokes
or inference of unmapped components is involved.
"""
import gzip
import html
import json
from pathlib import Path

OUTPUT = Path(__file__).resolve().parent
REPO = OUTPUT.parent.parent
STROKES = REPO / "library/src/commonMain/composeResources/files/strokes"
PALETTES = json.loads((OUTPUT / "palettes.json").read_text())
CHARACTERS = [
    ("林", "lín", "grove"),
    ("森", "sēn", "forest"),
    ("器", "qì", "vessel"),
    ("聽", "tīng", "listen"),
    ("赢", "yíng", "win"),
    ("斷", "duàn", "sever"),
]
DATA = {
    c: json.loads(gzip.decompress((STROKES / f"{ord(c) % 256}.json.gz").read_bytes()))[c]
    for c, _, _ in CHARACTERS
}


def glyph(character, colors, ink, x, y, size, neutral_alpha=0.92):
    entry = DATA[character]
    paths = []
    for path, index in zip(entry["strokes"], entry["strokeComponents"]):
        color = ink if index is None else colors[index]
        opacity = neutral_alpha if index is None else 1
        paths.append(f'<path d="{path}" fill="{color}" fill-opacity="{opacity}"/>')
    return (
        f'<g transform="translate({x},{y}) scale({size / 1024})">'
        '<g transform="translate(0,900) scale(1,-1)">'
        + "".join(paths) + "</g></g>"
    )


def board(characters, theme, name):
    dark = theme == "dark"
    background, foreground, muted, border = (
        ("#1E1A14", "#EAE4D8", "#A69E8F", "#3C362B") if dark
        else ("#FFFDF8", "#221F1A", "#777167", "#DDD6C8")
    )
    width, header, row_height = 1210, 234, 270
    height = header + row_height * len(characters) + 70
    svg = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">',
        f'<rect width="{width}" height="{height}" fill="{background}"/>',
    ]

    def text(x, y, value, size=14, color=muted, serif=False):
        font = "Georgia" if serif else "Arial"
        svg.append(f'<text x="{x}" y="{y}" fill="{color}" font-family="{font}" font-size="{size}">{html.escape(value)}</text>')

    text(35, 39, f"FOREST & BRONZE / {theme.upper()} SURFACE", 15)
    text(35, 68, "Each row uses the same stroke paths and component order across all three variants.", 14)
    for col, palette in enumerate(PALETTES):
        x = 214 + col * 328
        text(x, 110, palette["name"], 25, foreground, True)
        text(x, 139, palette["description"], 13)
        for index, color in enumerate(palette[theme]):
            sx, sy = x + (index % 3) * 103, 164 + (index // 3) * 33
            svg.append(f'<rect x="{sx}" y="{sy}" width="16" height="16" rx="2" fill="{color}"/>')
            text(sx + 22, sy + 13, color, 12)
    svg.append(f'<path d="M35 {header - 5}H1175" stroke="{border}"/>')
    for row, (character, pinyin, meaning) in enumerate(characters):
        y = header + row * row_height
        entry = DATA[character]
        unmapped = [i + 1 for i, match in enumerate(entry["strokeComponents"]) if match is None]
        count = len(entry["components"])
        # A small monochrome glyph identifies each row independently of font fallback.
        svg.append(glyph(character, [foreground] * 6, foreground, 30, y + 24, 42, 1))
        text(84, y + 56, pinyin, 21, foreground, True)
        text(36, y + 87, meaning, 16, foreground)
        text(36, y + 119, f"{count} mapped parts", 14)
        text(36, y + 145, f"{len(unmapped)} unmapped " + ("stroke" if len(unmapped) == 1 else "strokes"), 14,
             foreground if unmapped else muted)
        if unmapped:
            text(36, y + 178, "Neutral ink:", 13)
            text(36, y + 201, "stroke " + ", ".join(map(str, unmapped)), 13)
        for col, palette in enumerate(PALETTES):
            svg.append(glyph(character, palette[theme], foreground, 247 + col * 328, y + 12, 250))
        if row + 1 < len(characters):
            svg.append(f'<path d="M35 {y + row_height}H1175" stroke="{border}"/>')
    text(35, height - 31, "Neutral ink marks strokes with no source component mapping; these strokes remain fully drawn.", 13)
    svg.append("</svg>")
    (OUTPUT / f"{name}-{theme}.svg").write_text("\n".join(svg))


def final_board(theme):
    palette = json.loads((OUTPUT / "final-palette.json").read_text())
    dark = theme == "dark"
    bg, ink, muted, border = (
        ("#1E1A14", "#EAE4D8", "#A69E8F", "#3C362B") if dark
        else ("#FFFDF8", "#221F1A", "#777167", "#DDD6C8")
    )
    svg = [
        '<svg xmlns="http://www.w3.org/2000/svg" width="1200" height="650" viewBox="0 0 1200 650">',
        f'<rect width="1200" height="650" fill="{bg}"/>',
        f'<text x="38" y="45" fill="{ink}" font-family="Georgia" font-size="27">{html.escape(palette["name"])} Forest &amp; bronze / applied palette</text>',
        f'<text x="38" y="77" fill="{muted}" font-family="Arial" font-size="14">{theme.capitalize()} surface / colors assigned in component-tree order</text>',
    ]
    for index, (name, color) in enumerate(zip(palette["componentOrder"], palette[theme])):
        x = 38 + index * 191
        svg.extend([
            f'<rect x="{x}" y="105" width="22" height="22" rx="3" fill="{color}"/>',
            f'<text x="{x + 30}" y="121" fill="{ink}" font-family="Arial" font-size="14">{index} / {name}</text>',
            f'<text x="{x + 30}" y="145" fill="{muted}" font-family="Arial" font-size="13">{color}</text>',
        ])
    for index, (character, label) in enumerate([
        ("森", "Forest / 3 mapped parts"),
        ("聽", "Listen / 6 mapped parts"),
        ("斷", "Sever / 6 mapped parts + neutral ink"),
    ]):
        x = index * 400
        svg.append(glyph(character, palette[theme], ink, x + 40, 198, 320))
        svg.append(f'<text x="{x + 38}" y="557" fill="{ink}" font-family="Arial" font-size="17">{label}</text>')
        if index:
            svg.append(f'<path d="M{x} 191V580" stroke="{border}"/>')
    svg.extend([
        f'<text x="38" y="621" fill="{muted}" font-family="Arial" font-size="14">Unmapped strokes stay neutral. Client palettes can override all six colors.</text>',
        '</svg>',
    ])
    (OUTPUT / f"final-{theme}.svg").write_text("\n".join(svg))


for theme in ["light", "dark"]:
    board(CHARACTERS[:4], theme, "mapped")
    board(CHARACTERS[4:], theme, "unmapped")
    board(CHARACTERS, theme, "all")
    final_board(theme)

(OUTPUT / "characters.json").write_text(json.dumps([
    {
        "character": character,
        "components": DATA[character]["components"],
        "unmappedStrokeNumbers": [i + 1 for i, x in enumerate(DATA[character]["strokeComponents"]) if x is None],
    }
    for character, _, _ in CHARACTERS
], ensure_ascii=False, indent=2) + "\n")
print("Generated eight boards: six comparisons and two applied-palette previews.")

#!/usr/bin/env python3
"""Renders the 1024x500 Play Store feature graphic.

Play crops this image on some surfaces and overlays UI on others, so the important
content is kept well inside the edges rather than filling the canvas.

The glyph is taken from the app's own adaptive icon foreground, so the graphic cannot
drift from the icon. Requires rsvg-convert (Debian/Ubuntu: librsvg2-bin).

    python3 scripts/render-play-feature-graphic.py
"""

from __future__ import annotations

import shutil
import subprocess
import sys
import xml.etree.ElementTree as ElementTree
from pathlib import Path

ANDROID = "{http://schemas.android.com/apk/res/android}"
RESOURCES = Path("app/src/main/res")
FOREGROUND = RESOURCES / "drawable/ic_launcher_foreground.xml"
COLORS = RESOURCES / "values/colors.xml"
OUTPUT = Path("play/feature-graphic-1024x500.png")

WIDTH, HEIGHT = 1024, 500
FONT = "DejaVu Sans"
TITLE = "Call Blocker"
TAGLINE = "Block unknown callers, or everyone"
FOOTNOTE = "Open source · No ads · No tracking · No internet"


def background_color() -> str:
    for element in ElementTree.parse(COLORS).getroot().findall("color"):
        if element.get("name") == "ic_launcher_background":
            return (element.text or "").strip()
    raise SystemExit("ic_launcher_background not found in colors.xml")


def to_css(argb: str) -> str:
    value = argb.lstrip("#")
    return "#" + (value[2:] if len(value) == 8 else value)


def glyph_paths() -> str:
    """The icon foreground, re-expressed in its own 24x24 authoring space."""
    root = ElementTree.parse(FOREGROUND).getroot()
    group = next(child for child in root if child.tag == "group")
    out = []
    for path in group:
        if path.tag != "path":
            continue
        attributes = [f'd="{path.get(ANDROID + "pathData")}"']
        fill = path.get(ANDROID + "fillColor")
        stroke = path.get(ANDROID + "strokeColor")
        attributes.append(f'fill="{to_css(fill)}"' if fill else 'fill="none"')
        if stroke:
            attributes.append(f'stroke="{to_css(stroke)}"')
            attributes.append(f'stroke-width="{path.get(ANDROID + "strokeWidth", "1")}"')
            cap = path.get(ANDROID + "strokeLineCap")
            if cap:
                attributes.append(f'stroke-linecap="{cap}"')
        out.append("<path " + " ".join(attributes) + "/>")
    return "\n      ".join(out)


def main() -> int:
    if not shutil.which("rsvg-convert"):
        print("rsvg-convert not found. Install librsvg2-bin.", file=sys.stderr)
        return 1

    background = to_css(background_color())
    # The icon's slash is separated from the handset by a stroke painted in the
    # background colour. That only disappears against a flat field of exactly that
    # colour, so this graphic uses no gradient and nothing behind the glyph — a
    # gradient or a tint circle turns the gap into a visible band.
    glyph_size = 200
    glyph_x, glyph_y = 80, (HEIGHT - glyph_size) / 2
    scale = glyph_size / 24
    text_x = glyph_x + glyph_size + 70

    svg = f"""<svg xmlns="http://www.w3.org/2000/svg" width="{WIDTH}" height="{HEIGHT}"
     viewBox="0 0 {WIDTH} {HEIGHT}">
  <rect width="{WIDTH}" height="{HEIGHT}" fill="{background}"/>
  <g transform="translate({glyph_x},{glyph_y:.0f}) scale({scale:.4f})">
      {glyph_paths()}
  </g>
  <text x="{text_x}" y="222" font-family="{FONT}" font-size="72" font-weight="bold"
        fill="#ffffff">{TITLE}</text>
  <text x="{text_x}" y="278" font-family="{FONT}" font-size="31"
        fill="#ffffff" fill-opacity="0.93">{TAGLINE}</text>
  <text x="{text_x}" y="330" font-family="{FONT}" font-size="22"
        fill="#ffffff" fill-opacity="0.70">{FOOTNOTE}</text>
</svg>
"""

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    scratch = OUTPUT.with_suffix(".svg")
    scratch.write_text(svg)
    subprocess.run(
        ["rsvg-convert", "-w", str(WIDTH), "-h", str(HEIGHT), str(scratch), "-o", str(OUTPUT)],
        check=True,
    )
    scratch.unlink()

    print(f"Wrote {OUTPUT} ({WIDTH}x{HEIGHT}, {OUTPUT.stat().st_size / 1024:.1f} KB)")
    return 0


if __name__ == "__main__":
    sys.exit(main())

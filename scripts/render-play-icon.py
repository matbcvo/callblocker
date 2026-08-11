#!/usr/bin/env python3
"""Renders the Play Store icon from the app's own vector drawables.

Google Play wants a 512x512 PNG. It applies its own corner mask and shadow, so the
image must be the full square with the background bleeding to every edge — a
pre-rounded or pre-masked icon gets clipped twice and looks wrong.

The artwork is taken from the adaptive icon layers so the store listing cannot drift
from what is on the device. Requires rsvg-convert (Debian/Ubuntu: librsvg2-bin).

    python3 scripts/render-play-icon.py
"""

from __future__ import annotations

import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ElementTree
from pathlib import Path

ANDROID = "{http://schemas.android.com/apk/res/android}"
RESOURCES = Path("app/src/main/res")
FOREGROUND = RESOURCES / "drawable/ic_launcher_foreground.xml"
COLORS = RESOURCES / "values/colors.xml"
OUTPUT = Path("play/icon-512.png")
SIZE = 512
CANVAS = 108.0  # the adaptive icon's viewport


def background_color() -> str:
    for element in ElementTree.parse(COLORS).getroot().findall("color"):
        if element.get("name") == "ic_launcher_background":
            return (element.text or "").strip()
    raise SystemExit("ic_launcher_background not found in colors.xml")


def to_css(argb: str) -> str:
    value = argb.lstrip("#")
    return "#" + (value[2:] if len(value) == 8 else value)


def foreground_svg() -> str:
    root = ElementTree.parse(FOREGROUND).getroot()
    group = next(child for child in root if child.tag == "group")
    scale = group.get(ANDROID + "scaleX", "1")
    x = group.get(ANDROID + "translateX", "0")
    y = group.get(ANDROID + "translateY", "0")

    paths = []
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
        paths.append("<path " + " ".join(attributes) + "/>")

    inner = "\n    ".join(paths)
    return f'<g transform="translate({x},{y}) scale({scale})">\n    {inner}\n  </g>'


def main() -> int:
    if not shutil.which("rsvg-convert"):
        print("rsvg-convert not found. Install librsvg2-bin.", file=sys.stderr)
        return 1

    svg = (
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{SIZE}" height="{SIZE}" '
        f'viewBox="0 0 {CANVAS:g} {CANVAS:g}">\n'
        f'  <rect width="{CANVAS:g}" height="{CANVAS:g}" fill="{to_css(background_color())}"/>\n'
        f"  {foreground_svg()}\n"
        f"</svg>\n"
    )

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    scratch = OUTPUT.with_suffix(".svg")
    scratch.write_text(svg)
    subprocess.run(
        ["rsvg-convert", "-w", str(SIZE), "-h", str(SIZE), str(scratch), "-o", str(OUTPUT)],
        check=True,
    )
    scratch.unlink()

    size_kb = OUTPUT.stat().st_size / 1024
    print(f"Wrote {OUTPUT} ({SIZE}x{SIZE}, {size_kb:.1f} KB)")
    return 0


if __name__ == "__main__":
    sys.exit(main())

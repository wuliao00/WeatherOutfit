"""Print representative colors for each scenery WebP so theme accents are sampled, not guessed.

Usage (from repo root):  py -3 tools/scenery_colors.py
"""
from __future__ import annotations

from pathlib import Path

from PIL import Image

REPO = Path(__file__).resolve().parent.parent
DIR = REPO / "app" / "src" / "main" / "res" / "drawable-nodpi"


def to_hex(rgb) -> str:
    return "#{:02X}{:02X}{:02X}".format(*[int(round(c)) for c in rgb])


def main() -> None:
    for webp in sorted(DIR.glob("bg_*.webp")):
        img = Image.open(webp).convert("RGB")
        w, h = img.size
        top = img.crop((0, 0, w, h // 3)).resize((1, 1), Image.BOX)
        mid = img.crop((0, h // 3, w, 2 * h // 3)).resize((1, 1), Image.BOX)
        bot = img.crop((0, 2 * h // 3, w, h)).resize((1, 1), Image.BOX)
        small = img.resize((32, 56), Image.BOX)
        # most saturated pixel = a usable accent
        best, best_sat = None, -1.0
        for x in range(32):
            for y in range(56):
                r, g, b = (c / 255 for c in small.getpixel((x, y)))
                sat = max(r, g, b) - min(r, g, b)
                if sat > best_sat:
                    best_sat, best = sat, (r * 255, g * 255, b * 255)
        print(
            f"{webp.stem:20s} sky={to_hex(top.getpixel((0,0)))} "
            f"mid={to_hex(mid.getpixel((0,0)))} ground={to_hex(bot.getpixel((0,0)))} "
            f"accent={to_hex(best)} (sat {best_sat:.2f})"
        )


if __name__ == "__main__":
    main()

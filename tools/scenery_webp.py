"""Turn generated scenery PNGs into WebP drawables for the JianYi app.

Usage (from repo root):
    py -3 tools/scenery_webp.py <dir-with-bg_*.png> [--quality 80]
                                    [--crop-bottom 0.06] [--preview-dir out/]

What it does
------------
1. Reframes each image by cropping a strip off the bottom (default 6%). The
   generator stamps an "AI generated" badge in the lower-right corner, so the
   crop is what keeps the shipped asset clean -- the provenance is documented
   in README.md instead of being hidden.
2. Encodes the result as lossy WebP into app/src/main/res/drawable-nodpi/
   (nodpi: a full-bleed background must never be density-scaled by aapt).
3. Optionally writes PNG previews of the bottom strip so the crop can be checked.

Keeps the source 9:16 canvas otherwise; SceneryBackground uses ContentScale.Crop.
"""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

from PIL import Image

REPO_DIR = Path(__file__).resolve().parent.parent
DST_DIR = REPO_DIR / "app" / "src" / "main" / "res" / "drawable-nodpi"


def resource_name(path: Path) -> str:
    """bg_dawn_ridge_1789969185211_b7326e9e.png -> bg_dawn_ridge"""
    stem = re.sub(r"(_\d{10,}[a-z0-9_]*)$", "", path.stem, flags=re.I)
    return re.sub(r"[^a-z0-9_]", "_", stem.lower())


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("src_dir", help="directory containing bg_*.png files")
    parser.add_argument("--quality", type=int, default=80)
    parser.add_argument("--crop-bottom", type=float, default=0.06,
                        help="fraction of the height to crop off the bottom")
    parser.add_argument("--preview-dir", default=None,
                        help="write bottom-strip PNG previews here for eyeballing")
    args = parser.parse_args()

    src_dir = Path(args.src_dir)
    DST_DIR.mkdir(parents=True, exist_ok=True)
    preview_dir = Path(args.preview_dir) if args.preview_dir else None
    if preview_dir:
        preview_dir.mkdir(parents=True, exist_ok=True)

    pngs = sorted(src_dir.glob("bg_*.png"))
    if not pngs:
        print(f"no bg_*.png found in {src_dir}", file=sys.stderr)
        return 1

    total_out = 0
    for png in pngs:
        name = resource_name(png)
        img = Image.open(png).convert("RGB")
        w, h = img.size
        cut = int(h * args.crop_bottom)
        img = img.crop((0, 0, w, h - cut))

        out = DST_DIR / f"{name}.webp"
        img.save(out, "WEBP", quality=args.quality, method=6, exact=True)
        total_out += out.stat().st_size
        print(f"{name}.webp  {img.size[0]}x{img.size[1]}  "
              f"{out.stat().st_size / 1024:8.1f} KB   <- {png.name}")

        if preview_dir:
            strip = img.crop((0, max(0, img.size[1] - 220), img.size[0], img.size[1]))
            strip.save(preview_dir / f"{name}_strip.png")

    print(f"\n{len(pngs)} files, {total_out / 1024 / 1024:.2f} MB total -> {DST_DIR}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

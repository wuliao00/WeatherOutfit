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

Pass --target <panelW>x<panelH> to ship a 1:1 bitmap instead. The generator only
outputs 1024x1792, so on a 1256x2760 / 120Hz panel the drawable is stretched ~1.6x
at draw time and reads as mush -- worst in the smooth sky gradients of the night
scene. --target does the same cover-crop the renderer would do, but with Lanczos
plus an unsharp mask, so the extra detail is actually there.

The source directory is shared with other projects, so always pass --only with the
eight scenery names; an unfiltered run drops unrelated drawables into the APK.
"""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

from PIL import Image, ImageFilter

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
    parser.add_argument("--target", default=None,
                        help="upscale to WxH with Lanczos + unsharp first, e.g. 1256x2760 "
                             "(the panel size of a 2K/120Hz phone). Without this the bitmap "
                             "is stretched ~1.6x at draw time and looks mushy.")
    parser.add_argument("--only", default=None,
                        help="comma-separated resource names to convert; the source dir is "
                             "shared with other projects so an unfiltered run would ship "
                             "unrelated drawables into the APK")
    parser.add_argument("--preview-dir", default=None,
                        help="write bottom-strip PNG previews here for eyeballing")
    args = parser.parse_args()

    src_dir = Path(args.src_dir)
    DST_DIR.mkdir(parents=True, exist_ok=True)
    preview_dir = Path(args.preview_dir) if args.preview_dir else None
    if preview_dir:
        preview_dir.mkdir(parents=True, exist_ok=True)

    target = None
    if args.target:
        tw, th = args.target.lower().split("x")
        target = (int(tw), int(th))

    keep = {n.strip() for n in args.only.split(",")} if args.only else None

    pngs = sorted(src_dir.glob("bg_*.png"))
    if keep is not None:
        pngs = [p for p in pngs if resource_name(p) in keep]
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

        if target:
            # 先按目标宽高做等比 cover 裁切，再放大：直接 resize 到不等比的目标尺寸
            # 会把画面拉扁，而 ContentScale.Crop 是在绘制端做同一件事——提前在这里做，
            # 才能拿到 1:1 的位图并顺手做一次锐化补偿。
            tw, th = target
            scale = max(tw / img.size[0], th / img.size[1])
            resized = img.resize(
                (int(round(img.size[0] * scale)), int(round(img.size[1] * scale))),
                Image.LANCZOS,
            )
            ox = (resized.size[0] - tw) // 2
            oy = (resized.size[1] - th) // 2
            img = resized.crop((ox, oy, ox + tw, oy + th))
            img = img.filter(ImageFilter.UnsharpMask(radius=1.6, percent=58, threshold=2))

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

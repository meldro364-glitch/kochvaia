#!/usr/bin/env python3
"""
Resize Kochvaia store assets to the exact sizes Amazon Appstore accepts:
- Icons: 512x512 (already present) + 114x114 PNGs.
- Screenshots: crop the OnePlus phone shots (1080x2376) to 1080x1920 by
  removing equal slices off the top (status bar) and bottom (gesture
  indicator). Amazon accepts 1920x1080 in either orientation.
"""
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).parent

def make_114_icon() -> None:
    src = Image.open(ROOT / "icon-512.png")
    small = src.resize((114, 114), Image.LANCZOS)
    small.save(ROOT / "icon-114.png", "PNG")
    print("wrote icon-114.png")

def crop_screenshot(name: str) -> None:
    src = Image.open(ROOT / "screenshots" / name)
    w, h = src.size
    target_h = w * 1920 // 1080  # 1920 when w=1080
    if h <= target_h:
        cropped = src
    else:
        slice_off = (h - target_h) // 2
        cropped = src.crop((0, slice_off, w, slice_off + target_h))
    out_name = name.rsplit(".", 1)[0] + ".png"
    cropped.save(ROOT / "screenshots-1080x1920" / out_name, "PNG")
    print(f"wrote screenshots-1080x1920/{out_name} ({cropped.size})")

def main() -> None:
    (ROOT / "screenshots-1080x1920").mkdir(exist_ok=True)
    make_114_icon()
    for shot in sorted((ROOT / "screenshots").glob("*.jpg")):
        crop_screenshot(shot.name)

if __name__ == "__main__":
    main()

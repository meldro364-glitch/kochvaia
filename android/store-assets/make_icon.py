#!/usr/bin/env python3
"""
Render the Kochvaia app icon at 512x512 for the Amazon Appstore listing.
Cream background, amber 5-pointed star with darker outline — same design
language as the launcher icon.
"""
import math
from pathlib import Path
from PIL import Image, ImageDraw

OUT = Path(__file__).parent / "icon-512.png"
SIZE = 512
BG = (255, 243, 214)         # #FFF3D6 — cream
FILL = (255, 179, 71)        # #FFB347 — amber
STROKE = (224, 138, 29)      # #E08A1D — darker amber

def star_points(cx: float, cy: float, r_outer: float, r_inner: float, count: int = 5):
    pts: list[tuple[float, float]] = []
    # Start at the top point (-90deg) and alternate outer/inner.
    for i in range(count * 2):
        angle = -math.pi / 2 + i * math.pi / count
        r = r_outer if i % 2 == 0 else r_inner
        pts.append((cx + r * math.cos(angle), cy + r * math.sin(angle)))
    return pts

def main() -> None:
    img = Image.new("RGB", (SIZE, SIZE), BG)
    draw = ImageDraw.Draw(img)
    pts = star_points(SIZE / 2, SIZE / 2 + 12, r_outer=200, r_inner=82)
    draw.polygon(pts, fill=FILL, outline=STROKE, width=6)
    img.save(OUT, "PNG")
    print(f"wrote {OUT} ({SIZE}x{SIZE})")

if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""
Generate the Homey App Store banner images for the Prayer Times app.

Dark-celestial theme (navy #0d1b2a + gold #e0b054): a night sky with a
crescent moon, stars and a mosque silhouette on a dawn-lit horizon.

Outputs the exact Homey App Store sizes:
  assets/images/small.png    250x175
  assets/images/large.png    500x350
  assets/images/xlarge.png   1000x700

Run from the homey/ directory:  python3 scripts/gen-assets.py
Requires: Pillow, numpy.
"""

import os
import math
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont, ImageChops

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# --- palette (matches the widget: deep navy + gold) ---
NAVY_TOP = (8, 16, 30)
NAVY_BOT = (19, 33, 55)
GOLD = (224, 176, 84)      # #e0b054
GOLD_HI = (243, 216, 156)
STAR = (240, 226, 190)
MOSQUE = (5, 11, 22)
WHITE = (240, 243, 248)
MUTED = (154, 173, 198)


def vgrad(w, h, top, bot):
    yy = np.linspace(0, 1, h)[:, None, None]
    arr = np.array(top) * (1 - yy) + np.array(bot) * yy
    return np.repeat(arr, w, axis=1).astype("uint8")


def load_font(size, bold=False):
    try:
        f = ImageFont.truetype("/System/Library/Fonts/SFNS.ttf", size)
        for n in (("Bold", "Heavy", "Semibold") if bold else ("Regular", "Medium")):
            try:
                f.set_variation_by_name(n)
                break
            except Exception:
                continue
        return f
    except Exception:
        p = ("/System/Library/Fonts/Supplemental/Arial Bold.ttf" if bold
             else "/System/Library/Fonts/Supplemental/Arial.ttf")
        return ImageFont.truetype(p, size)


def crescent(size, center, outer_r, dx, dy, inner_r, color, glow=False):
    """Crescent via (outer circle) minus (offset inner circle). Keep the inner
    circle fully inside the outer (|(dx,dy)| + inner_r <= outer_r) for a clean
    lune with no stray slivers."""
    ox, oy = center
    outer = Image.new("L", size, 0)
    ImageDraw.Draw(outer).ellipse([ox - outer_r, oy - outer_r, ox + outer_r, oy + outer_r], fill=255)
    inner = Image.new("L", size, 0)
    ImageDraw.Draw(inner).ellipse([ox + dx - inner_r, oy + dy - inner_r,
                                   ox + dx + inner_r, oy + dy + inner_r], fill=255)
    mask = ImageChops.subtract(outer, inner)

    layer = Image.new("RGBA", size, (0, 0, 0, 0))
    layer.paste(Image.new("RGBA", size, color + (255,)), (0, 0), mask)

    if glow:
        base = Image.new("RGBA", size, (0, 0, 0, 0))
        gmask = mask.filter(ImageFilter.GaussianBlur(16))
        base.paste(Image.new("RGBA", size, color + (110,)), (0, 0), gmask)
        base.alpha_composite(layer)
        return base
    return layer


def star_points(cx, cy, R, r):
    pts = []
    for i in range(10):
        ang = math.radians(-90 + i * 36)
        rad = R if i % 2 == 0 else r
        pts.append((cx + rad * math.cos(ang), cy + rad * math.sin(ang)))
    return pts


def draw_mosque(d, cx, base_y, s, color):
    # base platform
    d.rounded_rectangle([cx - 150 * s, base_y, cx + 150 * s, base_y + 24 * s],
                        radius=8 * s, fill=color)
    # minarets (+ dome cap + finial)
    for mx in (cx - 120 * s, cx + 120 * s):
        d.rectangle([mx - 11 * s, base_y - 150 * s, mx + 11 * s, base_y], fill=color)
        d.pieslice([mx - 17 * s, base_y - 178 * s, mx + 17 * s, base_y - 144 * s],
                   180, 360, fill=color)
        d.ellipse([mx - 6 * s, base_y - 200 * s, mx + 6 * s, base_y - 188 * s], fill=color)
    # central body
    d.rectangle([cx - 62 * s, base_y - 98 * s, cx + 62 * s, base_y], fill=color)
    # onion dome (ellipse + tip)
    d.ellipse([cx - 74 * s, base_y - 182 * s, cx + 74 * s, base_y - 66 * s], fill=color)
    d.polygon([(cx - 30 * s, base_y - 150 * s), (cx + 30 * s, base_y - 150 * s),
               (cx, base_y - 232 * s)], fill=color)


def build_banner():
    W, H = 1000, 700
    img = Image.fromarray(vgrad(W, H, NAVY_TOP, NAVY_BOT)).convert("RGBA")

    # dawn glow on the horizon behind the mosque
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(glow).ellipse([W * 0.44, H * 0.60, W * 1.06, H * 1.18], fill=GOLD + (60,))
    img.alpha_composite(glow.filter(ImageFilter.GaussianBlur(75)))

    d = ImageDraw.Draw(img)

    # stars
    stars = [(120, 96, 3), (208, 156, 2), (300, 74, 2.5), (402, 128, 2),
             (150, 232, 2), (500, 66, 3), (476, 168, 2), (600, 120, 2.5),
             (690, 70, 2), (860, 96, 3), (900, 190, 2.5), (812, 250, 2),
             (346, 196, 2), (250, 300, 1.8)]
    for sx, sy, sr in stars:
        d.ellipse([sx - sr, sy - sr, sx + sr, sy + sr], fill=STAR)

    # mosque silhouette on the horizon
    draw_mosque(d, 748, 566, 1.0, MOSQUE)

    # crescent moon (gold, softly glowing), upper-right
    img.alpha_composite(crescent((W, H), (792, 196), 120, 18, -16, 96, GOLD, glow=True))

    # title + subtitle (left)
    title = load_font(86, bold=True)
    d.text((70, 232), "Prayer", font=title, fill=WHITE)
    d.text((70, 330), "Times", font=title, fill=GOLD)

    d.rectangle([74, 452, 74 + 132, 458], fill=GOLD)  # accent underline
    sub = load_font(30, bold=False)
    d.text((72, 474), "UK salah times on your dashboard.", font=sub, fill=MUTED)

    img = img.convert("RGB")
    for rel, (w, h) in {
        "assets/images/xlarge.png": (1000, 700),
        "assets/images/large.png": (500, 350),
        "assets/images/small.png": (250, 175),
    }.items():
        out = img.resize((w, h), Image.LANCZOS)
        p = os.path.join(ROOT, rel)
        os.makedirs(os.path.dirname(p), exist_ok=True)
        out.save(p)
        print(f"wrote {rel} ({w}x{h})")


if __name__ == "__main__":
    build_banner()

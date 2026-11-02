"""Builds launcher icons from the supplied artwork.

Crops a square around the face, then writes legacy and adaptive icons at every
density. Run from the project root: python3 tools/make_icons.py
"""

import os
from PIL import Image, ImageDraw, ImageFilter

SRC = "/home/ubuntu/.hermes/cache/images/img_da910738cff3.jpeg"
RES = "app/src/main/res"
MASTER = "tools/icon_master.png"

# Face sits in the upper third, slightly left of centre.
CROP_SIDE = 2180
CROP_LEFT = 150
CROP_TOP = 40

DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}


def square_crop() -> Image.Image:
    image = Image.open(SRC).convert("RGB")
    side = min(CROP_SIDE, image.width, image.height)
    left = max(0, min(CROP_LEFT, image.width - side))
    top = max(0, min(CROP_TOP, image.height - side))
    return image.crop((left, top, left + side, top + side))


def rounded(image: Image.Image, radius_ratio: float) -> Image.Image:
    size = image.size[0]
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        (0, 0, size - 1, size - 1), radius=int(size * radius_ratio), fill=255)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(image, (0, 0), mask)
    return out


def circular(image: Image.Image) -> Image.Image:
    size = image.size[0]
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, size - 1, size - 1), fill=255)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(image, (0, 0), mask)
    return out


def adaptive_foreground(master: Image.Image, canvas_px: int) -> Image.Image:
    """Art occupies the inner safe zone so round masks never cut the face."""
    art_px = int(canvas_px * 0.70)
    art = master.resize((art_px, art_px), Image.LANCZOS)
    canvas = Image.new("RGBA", (canvas_px, canvas_px), (0, 0, 0, 0))
    offset = (canvas_px - art_px) // 2
    canvas.paste(art, (offset, offset))
    return canvas


def main() -> None:
    master = square_crop()
    os.makedirs("tools", exist_ok=True)
    master.save(MASTER)

    for density, scale in DENSITIES.items():
        folder = f"{RES}/mipmap-{density}"
        os.makedirs(folder, exist_ok=True)

        legacy = int(48 * scale)
        icon = master.resize((legacy, legacy), Image.LANCZOS)
        rounded(icon, 0.18).save(f"{folder}/ic_launcher.png")
        circular(icon).save(f"{folder}/ic_launcher_round.png")

        adaptive = int(108 * scale)
        adaptive_foreground(master, adaptive).save(f"{folder}/ic_launcher_foreground.png")

    # Adaptive icon definition (API 26+).
    anydpi = f"{RES}/mipmap-anydpi-v26"
    os.makedirs(anydpi, exist_ok=True)
    template = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background"/>
    <foreground android:drawable="@mipmap/ic_launcher_foreground"/>
</adaptive-icon>
"""
    for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
        with open(os.path.join(anydpi, name), "w", encoding="utf-8") as handle:
            handle.write(template)

    colors = f"{RES}/values/colors.xml"
    with open(colors, "w", encoding="utf-8") as handle:
        handle.write("""<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="ic_launcher_background">#171A22</color>
</resources>
""")
    print("icons written")


if __name__ == "__main__":
    main()

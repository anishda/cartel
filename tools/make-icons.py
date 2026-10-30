#!/usr/bin/env python3
"""Génère l'icône de l'app : une façade de musée crème sur fond bordeaux.

Écrit les PNG Android (mipmap-*) et l'icône iOS 1024 px. Nécessite Pillow : pip install pillow
Usage : python3 tools/make-icons.py
"""
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
BORDEAUX_TOP = (0x8A, 0x35, 0x45)
BORDEAUX_BOTTOM = (0x5C, 0x1F, 0x2B)
CREAM = (0xFB, 0xF8, 0xF3)


def building(draw: ImageDraw.ImageDraw, s: float, ox: float, oy: float, k: float) -> None:
    """Dessine la façade. Coordonnées sur une grille de 108 (comme l'icône adaptative Android)."""
    def r(x0, y0, x1, y1):
        draw.rectangle((ox + x0 * k, oy + y0 * k, ox + x1 * k, oy + y1 * k), fill=CREAM)

    draw.polygon([(ox + 54 * k, oy + 30 * k), (ox + 30 * k, oy + 44 * k), (ox + 78 * k, oy + 44 * k)], fill=CREAM)
    r(32, 46, 76, 50)
    for x in (34.5, 45.5, 56.5, 67.5):
        r(x, 52, x + 6, 72)
    r(30, 74, 78, 78)
    r(27, 79, 81, 83)


def gradient(size: int) -> Image.Image:
    img = Image.new("RGB", (size, size))
    px = img.load()
    for y in range(size):
        t = y / (size - 1)
        color = tuple(int(BORDEAUX_TOP[i] * (1 - t) + BORDEAUX_BOTTOM[i] * t) for i in range(3))
        for x in range(size):
            px[x, y] = color
    return img


def render(size: int, rounded: bool = False, round_shape: bool = False) -> Image.Image:
    img = gradient(size)
    draw = ImageDraw.Draw(img)
    # La façade occupe la zone sûre : on agrandit la grille de 108 pour remplir l'icône carrée.
    k = size / 108 * 1.25
    building(draw, size, size * (1 - 1.25) / 2, size * (1 - 1.25) / 2 - 2.5 * k, k)
    if rounded or round_shape:
        mask = Image.new("L", (size, size), 0)
        md = ImageDraw.Draw(mask)
        if round_shape:
            md.ellipse((0, 0, size - 1, size - 1), fill=255)
        else:
            md.rounded_rectangle((0, 0, size - 1, size - 1), radius=int(size * 0.22), fill=255)
        out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        out.paste(img, (0, 0), mask)
        return out
    return img


def main() -> None:
    ios = ROOT / "iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/app-icon-1024.png"
    render(1024).save(ios)
    sizes = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    for density, px in sizes.items():
        folder = ROOT / f"androidApp/src/main/res/mipmap-{density}"
        folder.mkdir(parents=True, exist_ok=True)
        render(px, rounded=True).save(folder / "ic_launcher.png")
        render(px, round_shape=True).save(folder / "ic_launcher_round.png")
    print("icônes écrites")


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""
Installs your own app icon.

    python tools/set_icon.py path/to/my_icon.png [--background "#FFFFFF"]

Use a square PNG, ideally 1024x1024. If the picture has a transparent background it is used as the
adaptive-icon foreground as it is; otherwise its corner colour is keyed out for the adaptive,
monochrome and splash layers. Keep the important part of the picture inside the middle ~66 %
(Android crops the rest on round / squircle masks).

What it writes:
  app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png, ic_launcher_round.png
  app/src/main/res/drawable-nodpi/ic_launcher_custom_foreground.png
  app/src/main/res/drawable-nodpi/ic_launcher_custom_monochrome.png
  app/src/main/res/drawable-nodpi/splash_logo.png
  app/src/main/res/drawable-night-nodpi/splash_logo.png
  app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml
  app/src/main/res/values/ic_launcher_background.xml
  app/src/main/ic_launcher-playstore.png (512x512 store icon)
  ic_launcher-playstore.png (1024x1024 README/source icon)
  fastlane/metadata/android/en-US/images/icon.png
  pwa/icons/{favicon-32,apple-touch-icon,icon-192,icon-512,icon-maskable-512}.png
Needs Pillow: pip install pillow
"""
import argparse, os, sys
from PIL import Image, ImageDraw

REPO = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
APP_MAIN = os.path.join(REPO, "app", "src", "main")
DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}


def fit_square(img):
    side = min(img.size)
    return img.crop(((img.width - side) // 2, (img.height - side) // 2,
                     (img.width + side) // 2, (img.height + side) // 2))


def flat(img, size, bg):
    base = Image.new("RGBA", (size, size), bg)
    base.alpha_composite(img.resize((size, size), Image.LANCZOS))
    return base


def cutout(img):
    """Make the flat corner colour transparent while keeping anti-aliased edges."""
    img = img.convert("RGBA")
    if img.getpixel((0, 0))[3] < 16:
        return img
    key = img.getpixel((0, 0))[:3]
    out = Image.new("RGBA", img.size, (0, 0, 0, 0))
    src = img.load()
    dst = out.load()
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = src[x, y]
            if a == 0:
                continue
            d = max(abs(r - key[0]), abs(g - key[1]), abs(b - key[2]))
            if d <= 10:
                continue
            alpha = a if d >= 60 else min(a, int((d - 10) * 255 / 50))
            dst[x, y] = (r, g, b, alpha)
    return out


def monochrome(img):
    out = Image.new("RGBA", img.size, (255, 255, 255, 0))
    out.putalpha(img.getchannel("A"))
    return out


def centered(img, size, scale):
    inner = round(size * scale)
    pic = img.resize((inner, inner), Image.LANCZOS)
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    canvas.alpha_composite(pic, ((size - inner) // 2, (size - inner) // 2))
    return canvas


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("icon")
    ap.add_argument("--background", default="#FFFFFF")
    a = ap.parse_args()
    src = fit_square(Image.open(a.icon).convert("RGBA"))
    bg = a.background if a.background.startswith("#") else "#" + a.background
    shape = cutout(src)
    mono = monochrome(shape)

    for name, scale in DENSITIES.items():
        d = os.path.join(APP_MAIN, "res", f"mipmap-{name}")
        os.makedirs(d, exist_ok=True)
        legacy = round(48 * scale)
        flat(src, legacy, bg).save(os.path.join(d, "ic_launcher.png"))
        rnd = flat(src, legacy, bg).convert("RGBA")
        mask = Image.new("L", (legacy, legacy), 0)
        ImageDraw.Draw(mask).ellipse((0, 0, legacy - 1, legacy - 1), fill=255)
        rnd.putalpha(mask)
        rnd.save(os.path.join(d, "ic_launcher_round.png"))

    nodpi = os.path.join(APP_MAIN, "res", "drawable-nodpi")
    night_nodpi = os.path.join(APP_MAIN, "res", "drawable-night-nodpi")
    os.makedirs(nodpi, exist_ok=True)
    os.makedirs(night_nodpi, exist_ok=True)
    # Adaptive foreground is a 108 dp canvas; keep the artwork inside the safe zone.
    centered(shape, 432, 0.85).save(os.path.join(nodpi, "ic_launcher_custom_foreground.png"))
    centered(mono, 432, 0.85).save(os.path.join(nodpi, "ic_launcher_custom_monochrome.png"))
    centered(shape, 512, 0.86).save(os.path.join(nodpi, "splash_logo.png"))
    centered(mono, 512, 0.86).save(os.path.join(night_nodpi, "splash_logo.png"))

    flat(src, 512, bg).save(os.path.join(APP_MAIN, "ic_launcher-playstore.png"))
    flat(src, 1024, bg).save(os.path.join(REPO, "ic_launcher-playstore.png"))
    os.makedirs(os.path.join(REPO, "fastlane", "metadata", "android", "en-US", "images"), exist_ok=True)
    flat(src, 512, bg).save(os.path.join(REPO, "fastlane", "metadata", "android", "en-US", "images", "icon.png"))

    pwa = os.path.join(REPO, "pwa", "icons")
    os.makedirs(pwa, exist_ok=True)
    flat(src, 32, bg).save(os.path.join(pwa, "favicon-32.png"))
    flat(src, 180, bg).save(os.path.join(pwa, "apple-touch-icon.png"))
    flat(src, 192, bg).save(os.path.join(pwa, "icon-192.png"))
    flat(src, 512, bg).save(os.path.join(pwa, "icon-512.png"))
    flat(src, 512, bg).save(os.path.join(pwa, "icon-maskable-512.png"))

    with open(os.path.join(APP_MAIN, "res", "mipmap-anydpi-v26", "ic_launcher.xml"), "w", encoding="utf-8") as f:
        f.write('<?xml version="1.0" encoding="utf-8"?>\n'
                '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
                '    <background android:drawable="@color/ic_launcher_background" />\n'
                '    <foreground android:drawable="@drawable/ic_launcher_custom_foreground" />\n'
                '    <monochrome android:drawable="@drawable/ic_launcher_custom_monochrome" />\n'
                '</adaptive-icon>\n')
    with open(os.path.join(APP_MAIN, "res", "values", "ic_launcher_background.xml"), "w", encoding="utf-8") as f:
        f.write('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
                f'    <color name="ic_launcher_background">{bg}</color>\n</resources>\n')
    for old in ("ic_launcher_foreground.xml", "ic_launcher_monochrome.xml"):
        for folder in ("drawable", "drawable-night", "drawable-v34", "drawable-night-v34"):
            path = os.path.join(APP_MAIN, "res", folder, old)
            if os.path.exists(path):
                os.remove(path)
    print("Done. Rebuild the app. PWA maskable icon uses the same safe-zone artwork.")


if __name__ == "__main__":
    sys.exit(main())

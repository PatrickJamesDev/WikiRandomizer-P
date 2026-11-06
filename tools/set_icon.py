#!/usr/bin/env python3
"""
Installs your own app icon.

    python tools/set_icon.py path/to/my_icon.png [--background "#FFFFFF"]

Use a square PNG, ideally 1024x1024. If the picture has a transparent background it is used as the
adaptive-icon foreground as it is; otherwise it is placed on the --background colour.
Keep the important part of the picture inside the middle ~66 % (Android crops the rest on round /
squircle masks).

What it writes (all under app/src/main):
  res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png, ic_launcher_round.png  (old phones)
  res/mipmap-{...}/ic_launcher_foreground.png                                        (adaptive icon)
  res/mipmap-anydpi-v26/ic_launcher.xml  -> uses the new foreground
  res/values/ic_launcher_background.xml  -> your background colour
  ic_launcher-playstore.png              (512x512 store icon)
It removes the old vector foreground. Needs Pillow:  pip install pillow
"""
import argparse, os, sys
from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main")
DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("icon")
    ap.add_argument("--background", default="#FFFFFF")
    a = ap.parse_args()
    src = Image.open(a.icon).convert("RGBA")
    side = min(src.size)
    src = src.crop(((src.width - side) // 2, (src.height - side) // 2,
                    (src.width + side) // 2, (src.height + side) // 2))
    bg = a.background if a.background.startswith("#") else "#" + a.background

    def flat(img, size):  # picture on the background colour
        base = Image.new("RGBA", (size, size), bg)
        base.alpha_composite(img.resize((size, size), Image.LANCZOS))
        return base

    for name, scale in DENSITIES.items():
        d = os.path.join(ROOT, "res", f"mipmap-{name}")
        os.makedirs(d, exist_ok=True)
        legacy = round(48 * scale)
        flat(src, legacy).convert("RGB").save(os.path.join(d, "ic_launcher.png"))
        rnd = flat(src, legacy)
        mask = Image.new("L", (legacy, legacy), 0)
        ImageDraw.Draw(mask).ellipse((0, 0, legacy - 1, legacy - 1), fill=255)
        rnd.putalpha(mask)
        rnd.save(os.path.join(d, "ic_launcher_round.png"))
        # Adaptive foreground: 108 dp canvas, picture scaled into the safe zone (66 %).
        canvas = round(108 * scale)
        fg = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
        inner = round(canvas * 0.66)
        pic = src.resize((inner, inner), Image.LANCZOS)
        fg.alpha_composite(pic, ((canvas - inner) // 2, (canvas - inner) // 2))
        fg.save(os.path.join(d, "ic_launcher_foreground.png"))

    flat(src, 512).convert("RGB").save(os.path.join(ROOT, "ic_launcher-playstore.png"))

    with open(os.path.join(ROOT, "res", "mipmap-anydpi-v26", "ic_launcher.xml"), "w", encoding="utf-8") as f:
        f.write('<?xml version="1.0" encoding="utf-8"?>\n'
                '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
                '    <background android:drawable="@color/ic_launcher_background"/>\n'
                '    <foreground android:drawable="@mipmap/ic_launcher_foreground"/>\n'
                '    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />\n'
                '</adaptive-icon>\n')
    with open(os.path.join(ROOT, "res", "values", "ic_launcher_background.xml"), "w", encoding="utf-8") as f:
        f.write('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
                f'    <color name="ic_launcher_background">{bg}</color>\n</resources>\n')
    old = os.path.join(ROOT, "res", "drawable", "ic_launcher_foreground.xml")
    if os.path.exists(old):
        os.remove(old)
    print("Done. Rebuild the app. (Themed/monochrome icon and splash logo still use the old shape.)")

if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env python3
"""
Derive ZR 4 adaptive-icon foregrounds from the brand master.

The legacy launcher tile (docs/brand/zr4_icon_1024.png) carries the full
"ZR 4" wordmark and is unchanged. Adaptive icons are masked by the launcher
(circle, squircle, teardrop...), and text placed low in the artwork clips in
round masks — so the adaptive foreground uses the observatory *symbol only*
(antenna + RF arcs), fitted inside the 66dp safe zone of the 108dp canvas.

The symbol is separated from the wordmark at the long transparent gap in the
master artwork; nothing is redrawn or re-proportioned.
Source master: docs/brand/zr4_mark_1024.png (from tools/make_brand_logos.py).
"""
from PIL import Image
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MASTER = os.path.join(ROOT, "docs", "brand", "zr4_mark_1024.png")
RES = os.path.join(ROOT, "app", "src", "main", "res")
FG = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}


def symbol_only(master: Image.Image) -> Image.Image:
    """Crop the artwork above the long empty gap that separates symbol and text."""
    alpha = master.getchannel("A")
    w, h = master.size
    # Search only the middle band: the transparent padding below the wordmark
    # is longer than the symbol/text gap and must not win.
    lo, hi = int(h * 0.30), int(h * 0.85)
    empty = [alpha.crop((0, y, w, y + 1)).getextrema()[1] == 0 for y in range(lo, hi)]
    best_len = best_end = run = 0
    for i, is_empty in enumerate(empty):
        y = lo + i
        run = run + 1 if is_empty else 0
        if run > best_len:
            best_len, best_end = run, y + 1
    symbol_bottom = best_end - best_len
    symbol = master.crop((0, 0, w, symbol_bottom))
    bbox = symbol.getchannel("A").getbbox()
    return symbol.crop(bbox) if bbox else symbol


def fit_inside(canvas: Image.Image, art: Image.Image, frac: float) -> Image.Image:
    cw, ch = canvas.size
    scale = min(frac * cw / art.size[0], frac * ch / art.size[1])
    tw, th = max(1, int(art.size[0] * scale)), max(1, int(art.size[1] * scale))
    resized = art.resize((tw, th), Image.LANCZOS)
    canvas.alpha_composite(resized, ((cw - tw) // 2, (ch - th) // 2))
    return canvas


if __name__ == "__main__":
    master = Image.open(MASTER).convert("RGBA")
    symbol = symbol_only(master)
    print("symbol (wordmark removed at transparent gap):", symbol.size)

    square = fit_inside(Image.new("RGBA", (1024, 1024), (0, 0, 0, 0)), symbol, 0.72)
    out = os.path.join(ROOT, "docs", "brand", "zr4_symbol_1024.png")
    square.save(out, optimize=True)
    print(os.path.relpath(out, ROOT), square.size)

    # In-app HD assets, each placed where it is legible:
    #  - brand_symbol.png: symbol ONLY for the 47-56dp top-bar tile (the title
    #    beside it already says "ZR 4"; wordmark text at that size is noise).
    #  - brand_icon.png: the finished 1024 launcher tile, shown large on About.
    # 512px is >= xxxhdpi for both display sizes, without shipping 1024px
    # bitmaps in the APK (masters stay in docs/brand).
    nodpi = os.path.join(RES, "drawable-nodpi")
    os.makedirs(nodpi, exist_ok=True)
    sym512 = square.resize((512, 512), Image.LANCZOS)
    sym512.save(os.path.join(nodpi, "brand_symbol.png"), optimize=True)
    print("app/src/main/res/drawable-nodpi/brand_symbol.png", sym512.size)
    icon1024 = Image.open(os.path.join(ROOT, "docs", "brand", "zr4_icon_1024.png")).convert("RGBA")
    icon512 = icon1024.resize((512, 512), Image.LANCZOS)
    icon512.save(os.path.join(nodpi, "brand_icon.png"), optimize=True)
    print("app/src/main/res/drawable-nodpi/brand_icon.png", icon512.size)

    # Monochrome layer for Android themed icons: the symbol's alpha as a
    # single-colour silhouette (the system tints it). Reusing the colour
    # foreground here would ignore the user's theme colour entirely.
    silhouette = Image.new("RGBA", symbol.size, (0, 0, 0, 255))
    silhouette.putalpha(symbol.getchannel("A"))

    for dens, sz in FG.items():
        fg = fit_inside(Image.new("RGBA", (sz, sz), (0, 0, 0, 0)), symbol, 0.60)
        path = os.path.join(RES, f"mipmap-{dens}", "ic_launcher_foreground.png")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        fg.save(path, optimize=True)
        print(os.path.relpath(path, ROOT), fg.size, "alpha bbox:", fg.getchannel("A").getbbox())

        mono = fit_inside(Image.new("RGBA", (sz, sz), (0, 0, 0, 0)), silhouette, 0.60)
        mpath = os.path.join(RES, f"mipmap-{dens}", "ic_launcher_monochrome.png")
        mono.save(mpath, optimize=True)
        print(os.path.relpath(mpath, ROOT), mono.size, "alpha bbox:", mono.getchannel("A").getbbox())

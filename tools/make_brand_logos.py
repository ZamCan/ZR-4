#!/usr/bin/env python3
"""
Premium PNG brand assets for ZamCan ZR-4 + TANESCO Meter Companion.
Drawn at 4x supersample then downscaled for crisp edges.
TANESCO mark is DERIVED from the real TANESCO identity:
  yellow upper swoosh + green lower swoosh (tilted ellipse) + lightning-bolt 'N'.
ZR-4 mark: observatory antenna + concentric RF arcs in gold/teal on ink-green.
Outputs 1024 app-icons (full-bleed), transparent marks and lockups.
"""
from PIL import Image, ImageDraw, ImageFont, ImageFilter
import math, os

S = 4                      # supersample
OUT = os.path.join(os.path.dirname(__file__), "out")
os.makedirs(OUT, exist_ok=True)

BOLD   = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
BOLDOB = "/usr/share/fonts/truetype/dejavu/DejaVuSans-BoldOblique.ttf" \
         if os.path.exists("/usr/share/fonts/truetype/dejavu/DejaVuSans-BoldOblique.ttf") else BOLD

def font(p, sz): return ImageFont.truetype(p, int(sz * S))

def vgrad(size, top, bottom, diag=True):
    w, h = size
    g = Image.new("RGB", (1, 256))
    for i in range(256):
        t_ = i / 255
        g.putpixel((0, i), tuple(int(top[c] + (bottom[c]-top[c])*t_) for c in range(3)))
    g = g.resize((w, h))
    if diag:
        g = g.rotate(12, resample=Image.BICUBIC, expand=False)
    return g.convert("RGBA")

def glow(img, cx, cy, radius, color, alpha):
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    r = radius * S
    d.ellipse([cx*S-r, cy*S-r, cx*S+r, cy*S+r], fill=color + (alpha,))
    layer = layer.filter(ImageFilter.GaussianBlur(90 * S // 4))
    img.alpha_composite(layer)

def gloss(img):
    w, h = img.size
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    d.ellipse([-w*0.25, -h*0.62, w*1.05, h*0.28], fill=(255, 255, 255, 46))
    layer = layer.filter(ImageFilter.GaussianBlur(24 * S))
    img.alpha_composite(layer)

def ring_border(img, radius_dp=225, w_dp=5, color=(255, 255, 255), alpha=110, inset=10):
    d = ImageDraw.Draw(img)
    w, h = img.size
    p = inset * S
    d.rounded_rectangle([p, p, w-p, h-p], radius=radius_dp*S, outline=color+(alpha,), width=w_dp*S)

def bolt_points(cx, cy, w, h):
    """Lightning bolt polygon centred at (cx,cy), size w x h (in design units)."""
    pts = [(0.18, 0.00), (0.62, 0.00), (0.44, 0.36), (0.72, 0.36),
           (0.20, 1.00), (0.30, 0.56), (0.02, 0.56)]
    return [(cx + (x-0.37)*w, cy + (y-0.5)*h) for x, y in pts]

def scale_pts(pts): return [(x*S, y*S) for x, y in pts]

# ---------------------------------------------------------------- TANESCO ---
def tanesco_mark(size=1024, background=True, wordmark=False, dark_text=False):
    W = size
    H = int(size * (1.30 if wordmark else 1.0))
    if background:
        img = vgrad((W*S, H*S), (4, 32, 17), (18, 102, 46))
        glow(img, W*0.82, H*0.12, 360, (255, 205, 8), 70)
        glow(img, W*0.10, H*0.92, 380, (46, 190, 96), 60)
    else:
        img = Image.new("RGBA", (W*S, H*S), (0, 0, 0, 0))

    cy_mark = H * (0.38 if wordmark else 0.50)

    # tilted elliptical swooshes (real TANESCO geometry: yellow top, green bottom)
    orbit = Image.new("RGBA", (W*S, H*S), (0, 0, 0, 0))
    od = ImageDraw.Draw(orbit)
    cx, cy = W*0.5, cy_mark
    rx, ry = W*0.405, H*(0.30 if wordmark else 0.315)
    od.arc([ (cx-rx)*S, (cy-ry)*S, (cx+rx)*S, (cy+ry)*S ], start=150, end=352,
           fill=(255, 205, 8, 255), width=int(W*0.075*S))                      # yellow arc (upper)
    od.arc([ (cx-rx)*S, (cy-ry)*S, (cx+rx)*S, (cy+ry)*S ], start=-28, end=172,
           fill=(43, 197, 94, 255) if background else (21, 122, 46, 255),
           width=int(W*0.075*S))                                                # green arc (lower)
    orbit = orbit.rotate(14, resample=Image.BICUBIC, center=(cx*S, cy*S))
    img.alpha_composite(orbit)

    d = ImageDraw.Draw(img)
    # lightning-bolt 'N' heart of the mark
    bx, by, bw, bh = W*0.50, cy_mark, W*0.30, H*(0.40 if wordmark else 0.42)
    glow_c = Image.new("RGBA", img.size, (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow_c)
    gd.polygon(scale_pts(bolt_points(bx, by, bw*1.18, bh*1.12)), fill=(255, 205, 8, 120))
    img.alpha_composite(glow_c.filter(ImageFilter.GaussianBlur(18*S)))
    d.polygon(scale_pts(bolt_points(bx+6, by+8, bw, bh)), fill=(0, 0, 0, 70))
    d.polygon(scale_pts(bolt_points(bx, by, bw, bh)), fill=(255, 214, 32, 255))

    if wordmark:
        txt = "TANESCO"
        f = font(BOLDOB, W*0.145)
        fill = (13, 92, 38, 255) if dark_text else (255, 255, 255, 255)
        # letter-spaced wordmark, hand centred
        spacing = W*0.022
        widths = [d.textlength(ch, font=f) for ch in txt]
        total = sum(widths) + spacing*S*(len(txt)-1)
        x = (W*S - total)/2
        y = H*S*0.72
        for ch, wd in zip(txt, widths):
            if not dark_text:
                d.text((x+3*S, y+3*S), ch, font=f, fill=(0,0,0,90))
            d.text((x, y), ch, font=f, fill=fill)
            x += wd + spacing*S
        sub = "METER  COMPANION"
        f2 = font(BOLD, W*0.042)
        sub_fill = (86, 110, 92, 255) if dark_text else (255, 224, 102, 255)
        sw = d.textlength(sub, font=f2)
        d.text(((W*S-sw)/2, H*S*0.895), sub, font=f2, fill=sub_fill)

    if background:
        gloss(img)
        ring_border(img, radius_dp=int(W*0.22))
        mask = Image.new("L", img.size, 0)
        ImageDraw.Draw(mask).rounded_rectangle([0, 0, img.size[0], img.size[1]],
                                                radius=int(W*0.235*S), fill=255)
        out = Image.new("RGBA", img.size, (0, 0, 0, 0))
        out.paste(img, (0, 0), mask)
        img = out

    return img.resize((W, H), Image.LANCZOS)

# -------------------------------------------------------------------- ZR4 ---
def zr4_mark(size=1024, background=True, wordmark=False):
    W = size
    H = int(size * (1.24 if wordmark else 1.0))
    GOLD = (214, 181, 109, 255)
    TEAL = (110, 231, 183, 255)
    if background:
        img = vgrad((W*S, H*S), (5, 13, 9), (20, 54, 36))
        glow(img, W*0.86, H*0.08, 340, (214, 181, 109), 66)
        glow(img, W*0.08, H*0.86, 360, (45, 150, 110), 58)
    else:
        img = Image.new("RGBA", (W*S, H*S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    cx = W*0.5
    top = H*(0.185 if wordmark else 0.205)
    hub_y = top + H*0.055

    # concentric RF arcs (both sides of the mast)
    for i, r in enumerate([0.13, 0.21, 0.29]):
        rr = W*r
        col = GOLD if i != 1 else TEAL
        width = int(W*0.028*S)
        d.arc([(cx-rr)*S, (hub_y-rr)*S, (cx+rr)*S, (hub_y+rr)*S], start=128, end=232, fill=col, width=width)
        d.arc([(cx-rr)*S, (hub_y-rr)*S, (cx+rr)*S, (hub_y+rr)*S], start=-52, end=52, fill=col, width=width)

    # broadcast hub + mast + observatory base
    hub_r = W*0.038
    glow_c = Image.new("RGBA", img.size, (0,0,0,0))
    ImageDraw.Draw(glow_c).ellipse([(cx-hub_r*2.2)*S, (hub_y-hub_r*2.2)*S,
                                    (cx+hub_r*2.2)*S, (hub_y+hub_r*2.2)*S], fill=(214,181,109,110))
    img.alpha_composite(glow_c.filter(ImageFilter.GaussianBlur(16*S)))
    d = ImageDraw.Draw(img)
    d.ellipse([(cx-hub_r)*S, (hub_y-hub_r)*S, (cx+hub_r)*S, (hub_y+hub_r)*S], fill=GOLD)
    mast_bot = hub_y + H*0.30
    d.rounded_rectangle([(cx-W*0.013)*S, hub_y*S, (cx+W*0.013)*S, mast_bot*S], radius=int(W*0.013*S), fill=GOLD)
    d.polygon(scale_pts([(cx-W*0.115, mast_bot), (cx+W*0.115, mast_bot),
                         (cx+W*0.175, mast_bot+H*0.055), (cx-W*0.175, mast_bot+H*0.055)]), fill=GOLD)
    d.rounded_rectangle([(cx-W*0.075)*S, (mast_bot+H*0.075)*S, (cx+W*0.075)*S, (mast_bot+H*0.102)*S],
                        radius=int(W*0.012*S), fill=TEAL)

    # ZR 4 wordmark inside the icon (and always in lockup mode)
    f_big = font(BOLD, W*0.20)
    y_txt = (mast_bot + H*0.135) * S
    zr, four = "ZR", "4"
    w_zr = d.textlength(zr, font=f_big); w_4 = d.textlength(four, font=f_big)
    gap = W*0.035*S
    x0 = (W*S - (w_zr + gap + w_4))/2
    d.text((x0+3*S, y_txt+3*S), zr, font=f_big, fill=(0,0,0,110))
    d.text((x0, y_txt), zr, font=f_big, fill=(240, 242, 233, 255))
    d.text((x0+w_zr+gap+3*S, y_txt+3*S), four, font=f_big, fill=(0,0,0,110))
    d.text((x0+w_zr+gap, y_txt), four, font=f_big, fill=GOLD)

    if wordmark:
        sub = "RF  OBSERVATORY"
        f2 = font(BOLD, W*0.045)
        sw = d.textlength(sub, font=f2)
        d.text(((W*S-sw)/2, H*S*0.92), sub, font=f2, fill=TEAL)

    if background:
        gloss(img)
        ring_border(img, radius_dp=int(W*0.22), color=(214, 181, 109), alpha=140)
        mask = Image.new("L", img.size, 0)
        ImageDraw.Draw(mask).rounded_rectangle([0, 0, img.size[0], img.size[1]],
                                                radius=int(W*0.235*S), fill=255)
        out = Image.new("RGBA", img.size, (0, 0, 0, 0))
        out.paste(img, (0, 0), mask)
        img = out
    return img.resize((W, H), Image.LANCZOS)

def circle_crop(img):
    m = Image.new("L", img.size, 0)
    ImageDraw.Draw(m).ellipse([0, 0, img.size[0], img.size[1]], fill=255)
    o = Image.new("RGBA", img.size, (0, 0, 0, 0))
    o.paste(img, (0, 0), m)
    return o

if __name__ == "__main__":
    jobs = [
        ("tanesco_icon_1024.png",   tanesco_mark(1024, True,  False)),
        ("tanesco_mark_1024.png",   tanesco_mark(1024, False, False)),
        ("tanesco_lockup_dark.png", tanesco_mark(900,  False, True, dark_text=False)),
        ("tanesco_lockup_light.png",tanesco_mark(900,  False, True, dark_text=True)),
        ("zr4_icon_1024.png",       zr4_mark(1024, True,  False)),
        ("zr4_mark_1024.png",       zr4_mark(1024, False, False)),
        ("zr4_lockup_dark.png",     zr4_mark(900,  False, True)),
    ]
    for name, im in jobs:
        im.save(os.path.join(OUT, name), optimize=True)
        print(name, im.size)
    circle_crop(jobs[0][1]).save(os.path.join(OUT, "tanesco_icon_round_1024.png"), optimize=True)
    circle_crop(jobs[4][1]).save(os.path.join(OUT, "zr4_icon_round_1024.png"), optimize=True)
    print("round icons done")

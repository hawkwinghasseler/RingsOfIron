#!/usr/bin/env python3
"""Generates the first pass of the SVG asset pack in assets/art.

The SVG files are the source art: open and edit them in any vector editor. This script
only exists to draw the many variants (sizes, wall joins, powered states) consistently.
Re-running it overwrites every file it writes, so once a file has been edited by hand,
remove it from this script (or stop running the script). See documentation/art/Design.md.

    python3 tools/art/generate_svgs.py
"""
import math
import os
import random
import shutil
import tomllib

ROOT = os.path.normpath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT = os.path.join(ROOT, "assets", "art")
T = 64  # SVG units per tile (one tile is one meter in game)

# ---------------------------------------------------------------------------
# Palette (documented in documentation/art/Design.md)
# ---------------------------------------------------------------------------
INK = "#10141a"          # outlines
DECK = "#3a434f"         # floor base
DECK_PLATE = "#444e5b"
DECK_SEAM = "#2a3039"
DECK_HI = "#566170"
STEEL = "#5b6675"        # light hull / housings
STEEL_MID = "#4b5563"
STEEL_DARK = "#373f4b"
STEEL_HI = "#7d8898"
HOUSING = "#2f3641"
HOUSING_HI = "#465060"
GLASS = "#0c1a26"
LENS = "#1b2028"         # an unlit light
POWER = "#f2b134"
HAZARD = "#f2c230"
RED = "#ff4d4d"
GREEN = "#4dff88"
SEAT = "#2b2f36"
SEAT_DARK = "#1e2227"

ACCENT = {
    "reactor": "#ff9a3c",
    "shield_emitter": "#4fd1ff",
    "turret_mount": "#ff5a5a",
    "life_support": "#5ee08a",
    "helm": "#6aa8ff",
    "weapons": "#c58cff",
    "cargo": "#c8a26a",
    "airlock": HAZARD,
    "docking_port": HAZARD,
    "engine": "#9fd8ff",
    "escape_pod": "#ff7a2a",
}

# What a tank holds, by component: its band color (see Fluid in interior-grid)
FLUIDS = {"tank": ("propellant", "#e0a040"), "oxygen_tank": ("oxygen", "#6ab8ff"),
          "nitrogen_tank": ("nitrogen", "#a8b4c8")}
PIPE, PIPE_HI, PIPE_LO = "#4e7484", "#86aebd", "#2e4652"  # life support and propellant pipes

SIZES = ["s", "m", "l", "xl"]
PRIMARY = {"s": (1, 1), "m": (2, 2), "l": (3, 3), "xl": (5, 5)}
AUX = {"s": (1, 2), "m": (2, 3), "l": (3, 7), "xl": (5, 12)}
PORT = {"s": (3, 1), "m": (5, 2), "l": (7, 3)}

written = []


def f(v):
    """Short number formatting for SVG attributes."""
    v = round(v, 2)
    return str(int(v)) if v == int(v) else str(v)


class Art:
    """One asset's drawing, in layers: base (everything that is lit by the scene),
    emissive (everything that gives off its own light, drawn on top), meta
    (invisible collider and interaction shapes) and, for exterior hulls, collision
    (what the craft collides on, when that is not its layout's tiles), relief (the outline and
    height of each raised part, which casts a shadow) and height (a height map, gray for height,
    which the art packer turns into a normal map)."""

    def __init__(self):
        self.b = ""
        self.c = ""
        self.e = ""
        self.m = ""
        self.r = ""
        self.h = ""
        self.defs = ""


def write(path, w, h, art, title, viewbox=None, unpowered=None, transform=None):
    """Writes the asset; with `unpowered`, also writes that path with the emissive layer
    left out (a powered object switched off or without power)."""
    def emit(p, with_emissive, t):
        full = os.path.join(OUT, p)
        os.makedirs(os.path.dirname(full), exist_ok=True)
        vb = viewbox or f"0 0 {f(w)} {f(h)}"
        tr = f' transform="{transform}"' if transform else ""
        svg = (f'<svg xmlns="http://www.w3.org/2000/svg" width="{f(w)}" height="{f(h)}" viewBox="{vb}">\n'
               f'  <title>{t}</title>\n')
        if art.defs:
            svg += f"  <defs>{art.defs}</defs>\n"
        svg += f'  <g id="base"{tr}>\n{art.b}  </g>\n'
        if art.c:
            svg += f'  <g id="collision"{tr}>\n{art.c}  </g>\n'
        # data only, hidden from a browser or editor: the art packer reads them
        if art.r:
            svg += f'  <g id="relief" display="none"{tr}>\n{art.r}  </g>\n'
        if art.h:
            svg += (f'  <g id="height" display="none" data-base="{f(HEIGHT_BASE)}" data-scale="{f(HEIGHT_SCALE)}"{tr}>\n'
                    f'{art.h}  </g>\n')
        if with_emissive and art.e:
            svg += f'  <g id="emissive"{tr}>\n{art.e}  </g>\n'
        if art.m:
            svg += f'  <g id="meta"{tr}>\n{art.m}  </g>\n'
        svg += "</svg>\n"
        with open(full, "w", newline="\n") as fh:
            fh.write(svg)
        written.append(p)
    emit(path, True, title)
    if unpowered:
        emit(unpowered, False, title + ", unpowered")


def unp(path):
    return path.replace(".svg", "_unpowered.svg")


def uid(name):
    return name.replace("/", "_").replace(".svg", "")


# Lighting the art leaves to the game (documentation/art/Design.md, "Light and shadow"): an
# element marked BAKED is light or shade painted on (a sheen, a lit edge), which the art packer
# leaves out of the flat art a lit shader draws; one marked RAISED stands up off the roof, drawn
# again over the shadows the roof takes, so a part's own shadow falls past it and not on it.
BAKED = ' data-baked="1"'
RAISED = ' data-raised="1"'
# The height layer's gray: HEIGHT_BASE is the bare roof, and each step of 1 above it is
# HEIGHT_SCALE meters higher (a seam is a little below the roof, space is 0)
HEIGHT_BASE, HEIGHT_SCALE = 0.25, 1.5


def gray(h):
    """The height layer's fill for `h` meters above the bare roof."""
    v = round(max(0.0, min(1.0, HEIGHT_BASE + h / HEIGHT_SCALE)) * 255)
    return f"#{v:02x}{v:02x}{v:02x}"


def relief(a, shape_, h):
    """A raised part of `h` meters: its outline in the relief layer (what casts its shadow) and
    its top in the height layer. `shape_` is ("poly", points), ("circle", cx, cy, r) or
    ("line", x1, y1, x2, y2, width), in SVG units."""
    hh = f' data-h="{f(h)}"'
    if shape_[0] == "circle":
        _, cx, cy, r = shape_[:4]
        a.r += circle(cx, cy, r, "#000", extra=hh)
        a.h += circle(cx, cy, r, gray(h))
    elif shape_[0] == "line":
        _, x1, y1, x2, y2, w = shape_
        # the outline of a round-capped run: its line, a capsule as wide as it is
        dx, dy = x2 - x1, y2 - y1
        n = math.hypot(dx, dy) or 1.0
        ux, uy, hw = dx / n, dy / n, w / 2
        pts = [(x1 - ux * hw - uy * hw, y1 - uy * hw + ux * hw), (x2 + ux * hw - uy * hw, y2 + uy * hw + ux * hw),
               (x2 + ux * hw + uy * hw, y2 + uy * hw - ux * hw), (x1 - ux * hw + uy * hw, y1 - uy * hw - ux * hw)]
        a.r += poly(pts, "#000", extra=hh)
        a.h += line(x1, y1, x2, y2, gray(h), sw=w)
    else:
        a.r += poly(shape_[1], "#000", extra=hh)
        a.h += poly(shape_[1], gray(h))


# ---------------------------------------------------------------------------
# Primitives (return SVG strings)
# ---------------------------------------------------------------------------

def rect(x, y, w, h, fill, rx=0, stroke=None, sw=2, extra=""):
    s = f' stroke="{stroke}" stroke-width="{f(sw)}"' if stroke else ""
    r = f' rx="{f(rx)}"' if rx else ""
    return f'    <rect x="{f(x)}" y="{f(y)}" width="{f(w)}" height="{f(h)}"{r} fill="{fill}"{s}{extra}/>\n'


def circle(cx, cy, r, fill, stroke=None, sw=2, extra=""):
    s = f' stroke="{stroke}" stroke-width="{f(sw)}"' if stroke else ""
    return f'    <circle cx="{f(cx)}" cy="{f(cy)}" r="{f(r)}" fill="{fill}"{s}{extra}/>\n'


def line(x1, y1, x2, y2, stroke, sw=2, extra=""):
    return (f'    <line x1="{f(x1)}" y1="{f(y1)}" x2="{f(x2)}" y2="{f(y2)}" stroke="{stroke}" '
            f'stroke-width="{f(sw)}" stroke-linecap="round"{extra}/>\n')


def poly(points, fill, stroke=None, sw=2, extra=""):
    pts = " ".join(f"{f(x)},{f(y)}" for x, y in points)
    s = f' stroke="{stroke}" stroke-width="{f(sw)}" stroke-linejoin="round"' if stroke else ""
    return f'    <polygon points="{pts}" fill="{fill}"{s}{extra}/>\n'


def path(d, fill="none", stroke=None, sw=2, extra=""):
    s = (f' stroke="{stroke}" stroke-width="{f(sw)}" stroke-linejoin="round" stroke-linecap="round"'
         if stroke else "")
    return f'    <path d="{d}" fill="{fill}"{s}{extra}/>\n'


def bevel(x, y, w, h, hi, lo, sw=1.5):
    """Top-left highlight and bottom-right shadow just inside a rectangle (light from top-left).
    Baked light: a lit shader draws the edge from a normal map instead."""
    o = sw / 2
    return (path(f"M{f(x+o)},{f(y+h-o)} L{f(x+o)},{f(y+o)} L{f(x+w-o)},{f(y+o)}", stroke=hi, sw=sw, extra=BAKED)
            + path(f"M{f(x+w-o)},{f(y+o)} L{f(x+w-o)},{f(y+h-o)} L{f(x+o)},{f(y+h-o)}", stroke=lo, sw=sw, extra=BAKED))


def plate(x, y, w, h, fill, hi=STEEL_HI, lo=INK, rx=2, sw=2):
    return rect(x, y, w, h, fill, rx=rx, stroke=INK, sw=sw) + bevel(x + 1, y + 1, w - 2, h - 2, hi, lo)


def rivet(x, y, r=1.6, fill=STEEL_HI):
    return circle(x, y, r, fill, extra=' opacity="0.9"')


def hazard_band(x, y, w, h, key):
    """Yellow and black diagonal stripes clipped to a rectangle."""
    cid = f"hz_{key}_{int(x)}_{int(y)}"
    stripes = ""
    step = 8
    for i in range(-int(h) - step, int(w + h) + step, step * 2):
        stripes += poly([(x + i, y + h), (x + i + step, y + h), (x + i + step + h, y), (x + i + h, y)], INK)
    return (f'    <clipPath id="{cid}"><rect x="{f(x)}" y="{f(y)}" width="{f(w)}" height="{f(h)}"/></clipPath>\n'
            + rect(x, y, w, h, HAZARD)
            + f'    <g clip-path="url(#{cid})">\n{stripes}    </g>\n'
            + rect(x, y, w, h, "none", stroke=INK, sw=1.5))


def glow_def(gid, color):
    return (f'<radialGradient id="{gid}"><stop offset="0" stop-color="#ffffff" stop-opacity="0.95"/>'
            f'<stop offset="0.35" stop-color="{color}" stop-opacity="0.9"/>'
            f'<stop offset="1" stop-color="{color}" stop-opacity="0"/></radialGradient>')


def halo_def(gid, color):
    return (f'<radialGradient id="{gid}"><stop offset="0" stop-color="{color}" stop-opacity="0.55"/>'
            f'<stop offset="1" stop-color="{color}" stop-opacity="0"/></radialGradient>')


def lit(s, kind, reach=None, intensity=None, direction=None, cone=None):
    """Wraps emissive shapes in a group that tells the game how they light the scene
    (tools/art/extract_lights.py): `glow` only glows, `point` and `cone` light the room.
    Reach is in tiles, direction and cone in degrees with 0 = +x and 90 = up the canvas."""
    attrs = f' data-light="{kind}"'
    for key, v in (("reach", reach), ("intensity", intensity), ("direction", direction), ("cone", cone)):
        if v is not None:
            attrs += f' data-{key}="{f(v)}"'
    return f"    <g{attrs}>\n{s}    </g>\n"


def light(a, cx, cy, r, color, halo=True):
    """A status lamp: a dark lens in the base layer, its light in the emissive layer. It glows
    but does not light the room."""
    a.b += circle(cx, cy, r, LENS, stroke=INK, sw=1)
    s = circle(cx, cy, r * 2.4, color, extra=' opacity="0.18"') if halo else ""
    a.e += lit(s + circle(cx, cy, r * 0.8, color), "glow")


def screen(a, x, y, w, h, color, seed=0):
    """A console screen: glass in the base layer, the picture in the emissive layer."""
    a.b += rect(x, y, w, h, GLASS, rx=1.5, stroke=INK, sw=1.5)
    a.e += rect(x + 1.5, y + 1.5, w - 3, h - 3, color, rx=1, extra=' opacity="0.25"')
    rows = max(1, int((h - 4) // 4))
    for i in range(rows):
        ly = y + 3.5 + i * 4
        lw = (w - 6) * (0.45 + 0.5 * (((i + seed) * 37) % 7) / 7)
        a.e += line(x + 3, ly, x + 3 + lw, ly, color, sw=1.2, extra=' opacity="0.9"')


def strip_light(a, x, y, w, h, color="#fff7d6"):
    a.b += rect(x, y, w, h, LENS, rx=min(w, h) / 2, stroke=INK, sw=1)
    a.e += rect(x + 1, y + 1, w - 2, h - 2, color, rx=min(w, h) / 2)


def seat(a, cx, cy, facing="up", s=1.0):
    """A crew seat; `facing` is the way the occupant looks."""
    w, d = 18 * s, 16 * s
    if facing in ("up", "down"):
        a.b += rect(cx - w / 2, cy - d / 2, w, d, SEAT, rx=5 * s, stroke=INK, sw=1.5)
        by = cy + d / 2 - 5 * s if facing == "up" else cy - d / 2 - 1 * s
        a.b += rect(cx - w / 2, by, w, 6 * s, SEAT_DARK, rx=2, stroke=INK, sw=1.2)
        a.b += rect(cx - w / 2 - 3 * s, cy - d / 2 + 2 * s, 3 * s, 10 * s, SEAT, rx=1.5, stroke=INK, sw=1)
        a.b += rect(cx + w / 2, cy - d / 2 + 2 * s, 3 * s, 10 * s, SEAT, rx=1.5, stroke=INK, sw=1)
    else:
        a.b += rect(cx - d / 2, cy - w / 2, d, w, SEAT, rx=5 * s, stroke=INK, sw=1.5)
        bx = cx + d / 2 - 5 * s if facing == "left" else cx - d / 2 - 1 * s
        a.b += rect(bx, cy - w / 2, 6 * s, w, SEAT_DARK, rx=2, stroke=INK, sw=1.2)
        a.b += rect(cx - d / 2 + 2 * s, cy - w / 2 - 3 * s, 10 * s, 3 * s, SEAT, rx=1.5, stroke=INK, sw=1)
        a.b += rect(cx - d / 2 + 2 * s, cy + w / 2, 10 * s, 3 * s, SEAT, rx=1.5, stroke=INK, sw=1)


def console(a, x, y, w, h, color, seed=0):
    """A desk console seen from above: housing, a screen and a key strip."""
    a.b += rect(x, y, w, h, HOUSING, rx=3, stroke=INK, sw=1.5)
    a.b += bevel(x + 1, y + 1, w - 2, h - 2, HOUSING_HI, INK, sw=1)
    if w >= h:
        screen(a, x + 4, y + 3, w - 8, h * 0.55, color, seed)
        a.b += rect(x + 4, y + h * 0.55 + 5, w - 8, h * 0.45 - 8, HOUSING_HI, rx=1, stroke=INK, sw=1)
    else:
        screen(a, x + 3, y + 4, w * 0.55, h - 8, color, seed)
        a.b += rect(x + w * 0.55 + 5, y + 4, w * 0.45 - 8, h - 8, HOUSING_HI, rx=1, stroke=INK, sw=1)


def collider_rect(x, y, w, h):
    return f'    <rect id="collider" x="{f(x)}" y="{f(y)}" width="{f(w)}" height="{f(h)}" fill="none" stroke="none"/>\n'


def collider_circle(cx, cy, r):
    return f'    <circle id="collider" cx="{f(cx)}" cy="{f(cy)}" r="{f(r)}" fill="none" stroke="none"/>\n'


def interact_point(x, y):
    return f'    <circle id="interact" cx="{f(x)}" cy="{f(y)}" r="2" fill="none" stroke="none"/>\n'


# ---------------------------------------------------------------------------
# Floors
# ---------------------------------------------------------------------------

def deck(x=0, y=0):
    """One steel deck plate tile at (x, y)."""
    s = rect(x, y, T, T, DECK_SEAM)
    s += rect(x + 1, y + 1, T - 2, T - 2, DECK, rx=2)
    s += rect(x + 4, y + 4, T - 8, T - 8, DECK_PLATE, rx=2)
    s += bevel(x + 4, y + 4, T - 8, T - 8, DECK_HI, DECK_SEAM)
    for gx in range(3):
        for gy in range(3):
            cx, cy = x + 14 + gx * 18, y + 14 + gy * 18
            if (gx + gy) % 2 == 0:
                s += line(cx - 3, cy + 3, cx + 3, cy - 3, DECK_SEAM, sw=2, extra=' opacity="0.55"')
            else:
                s += line(cx - 3, cy - 3, cx + 3, cy + 3, DECK_SEAM, sw=2, extra=' opacity="0.55"')
    for (rx_, ry_) in ((8, 8), (T - 8, 8), (8, T - 8), (T - 8, T - 8)):
        s += rivet(x + rx_, y + ry_, 1.8, DECK_HI)
    return s


def floors():
    a = Art()
    a.b = deck()
    write("tiles/floor.svg", T, T, a, "floor: steel deck plate")

    # Power conduit: a junction box drawn over the tile's own floor (and any covering on it), so
    # it has no deck of its own; arms are overlays drawn toward each conduit neighbor. The
    # junction's lamp shows whether the circuit is live.
    a = Art()
    a.b = rect(20, 20, 24, 24, HOUSING, rx=3, stroke=INK)
    a.b += bevel(21, 21, 22, 22, HOUSING_HI, INK)
    a.b += circle(32, 32, 6, "#6b5a2e", stroke=INK, sw=1.5)
    a.e += lit(circle(32, 32, 9, POWER, extra=' opacity="0.2"') + circle(32, 32, 5, POWER)
               + circle(32, 32, 2.5, "#fff3c4"), "glow")
    write("tiles/conduit.svg", T, T, a, "conduit: power junction overlay, drawn over the tile's floor", unpowered=unp("tiles/conduit.svg"))

    a = Art()
    a.b = rect(26, 0, 12, 22, "#20262e", stroke=INK, sw=1.5)
    a.b += rect(29.5, 0, 5, 22, "#6b5a2e")
    for yy in (5, 14):
        a.b += rect(26, yy, 12, 2.5, STEEL_DARK)
    a.e += lit(rect(29.5, 0, 5, 5, POWER) + rect(29.5, 7.5, 5, 6.5, POWER) + rect(29.5, 16.5, 5, 5.5, POWER)
               + line(32, 0, 32, 22, "#fff3c4", sw=1.2, extra=' opacity="0.6"'), "glow")
    write("tiles/conduit_arm.svg", T, T, a,
          "conduit arm overlay: runs from the junction to the top edge; rotate toward each conduit neighbor",
          unpowered=unp("tiles/conduit_arm.svg"))

    a = Art()
    a.b = rect(0, 0, T, T, "#5a2a33")
    for yy in range(2, T, 4):
        a.b += line(0, yy, T, yy, "#6b333d", sw=1.2)
    for xx in range(0, T, 16):
        a.b += line(xx, 0, xx, T, "#4a2229", sw=0.8, extra=' opacity="0.6"')
    write("tiles/covering_carpet.svg", T, T, a, "covering_carpet: carpet laid over the deck (fire fuel)")

    a = Art()
    a.b = rect(0, 0, T, T, "#c9d6d4")
    for xx in range(0, T + 1, 32):
        a.b += line(xx, 0, xx, T, "#a9b9b6", sw=1.2)
    for yy in range(0, T + 1, 32):
        a.b += line(0, yy, T, yy, "#a9b9b6", sw=1.2)
    a.b += rect(26, 16, 12, 32, "#b8433f") + rect(16, 26, 32, 12, "#b8433f")
    write("tiles/covering_medbay.svg", T, T, a, "covering_medbay: a medbay's clean deck, a bed for the wounded (no fuel)")


# ---------------------------------------------------------------------------
# Full-tile walls: one file per join pattern
# ---------------------------------------------------------------------------

WALLS = {
    # id: (side face, top plate, plate highlight, band width, groove width, bolt radius)
    "hull_light": ("#4a5462", "#6a7586", "#8e99a8", 4, 4, 1.6),
    "hull_medium": ("#3d4552", "#5b6675", STEEL_HI, 6, 6, 2.2),
    "hull_heavy": ("#2c333d", "#4b5563", "#6d7888", 8, 10, 2.8),
    "internal_wall": ("#4f5866", "#727d8b", "#98a3b1", 5, 5, 1.8),
}
WALLS["hull"] = WALLS["hull_medium"]
# Composite armor (materials.toml composite_armor): grey-green ceramic tiles over a backing
# plate instead of welded steel beams, so it reads as a different material, not a tint.
COMPOSITE = {
    "composite_armor_light": ("#3e4a48", "#5f6d69", "#8a9893", 4, 0, 1.4),
    "composite_armor_medium": ("#323d3b", "#53605c", "#7b8984", 6, 0, 1.8),
    "composite_armor_heavy": ("#262f2e", "#45514e", "#6c7a75", 8, 0, 2.2),
}
WALLS.update(COMPOSITE)
# Tile art families by material: hull_steel walls are hull_<weight>, others <material>_<weight>
MATERIAL_PREFIX = {"hull_steel": "hull", "composite_armor": "composite_armor"}


def ceramic(x0, y0, w, h, top, hi, key):
    """Square ceramic armor tiles, 16 units with chamfered corners, on the global 64-unit grid
    so joined tiles continue the pattern without a seam. Clipped to the rectangle given."""
    cid = f"cer_{key}_{int(x0)}_{int(y0)}_{int(w)}_{int(h)}"
    s = f'    <clipPath id="{cid}"><rect x="{f(x0)}" y="{f(y0)}" width="{f(w)}" height="{f(h)}"/></clipPath>\n'
    s += f'    <g clip-path="url(#{cid})">\n'
    for row in range(4):
        for col in range(4):
            x, y = col * 16, row * 16
            shade = ((row * 7 + col * 3) % 5) / 5
            c = 2.5
            s += poly([(x + 1 + c, y + 1), (x + 15 - c, y + 1), (x + 15, y + 1 + c), (x + 15, y + 15 - c),
                       (x + 15 - c, y + 15), (x + 1 + c, y + 15), (x + 1, y + 15 - c), (x + 1, y + 1 + c)],
                      top, stroke=INK, sw=0.8, extra=f' opacity="{f(0.75 + shade * 0.25)}"')
            s += path(f"M{f(x + 2)},{f(y + 13 - c)} L{f(x + 2)},{f(y + 2 + c)} L{f(x + 2 + c)},{f(y + 2)} "
                      f"L{f(x + 14 - c)},{f(y + 2)}", stroke=hi, sw=0.9, extra=' opacity="0.6"')
            s += path(f"M{f(x + 14)},{f(y + 2 + c)} L{f(x + 14)},{f(y + 14 - c)} L{f(x + 14 - c)},{f(y + 14)} "
                      f"L{f(x + 2 + c)},{f(y + 14)}", stroke=INK, sw=0.9, extra=' opacity="0.35"')
    return s + "    </g>\n"
MASKS = ["".join(d for d, bit in zip("nesw", (m & 1, m & 2, m & 4, m & 8)) if bit) for m in range(16)]


def wall_tile(tid, mask):
    """A wall tile that joins its neighbors of the same type on the sides in `mask`
    ('n' up, 'e' right, 's' down, 'w' left). Joined sides run straight into the
    neighbor's art with only a faint seam; open sides show the wall's face."""
    face, top, hi, band, groove, bolt = WALLS[tid]
    a = Art()
    s = rect(0, 0, T, T, top)
    exposed = [d for d in "nesw" if d not in mask]
    if tid in COMPOSITE:
        # the backing plate shows as a dark line between tiles; open sides keep their face band
        s = rect(0, 0, T, T, face) + ceramic(0, 0, T, T, top, hi, f"{tid}_{mask or '0'}")
    sides = {"n": (0, 0, T, band), "s": (0, T - band, T, band), "w": (0, 0, band, T), "e": (T - band, 0, band, T)}
    # grooves from the center toward each joined side, so a run of walls reads as one beam
    c0, c1 = 32 - groove / 2, 32 + groove / 2
    g = {"n": (c0, 0, groove, 32), "s": (c0, 32, groove, 32), "w": (0, c0, 32, groove), "e": (32, c0, 32, groove)}
    for d in (mask if tid not in COMPOSITE else ""):
        x, y, w, h = g[d]
        s += rect(x, y, w, h, face)
        if d in "ns":
            s += line(c0 + 0.8, y, c0 + 0.8, y + h, hi, sw=1, extra=' opacity="0.7"')
        else:
            s += line(x, c0 + 0.8, x + w, c0 + 0.8, hi, sw=1, extra=' opacity="0.7"')
    boss = groove + 8
    if tid not in COMPOSITE:
        s += rect(32 - boss / 2, 32 - boss / 2, boss, boss, top, rx=2, stroke=INK, sw=1.5)
        s += bevel(32 - boss / 2 + 1, 32 - boss / 2 + 1, boss - 2, boss - 2, hi, INK, sw=1)
    if tid == "hull_heavy":
        s += circle(32, 32, 3, hi, stroke=INK, sw=1)
    # side faces on open sides; lit from the top-left
    for d in exposed:
        x, y, w, h = sides[d]
        s += rect(x, y, w, h, face)
        shade = ('#ffffff" opacity="0.12' if d in "nw" else '#000000" opacity="0.22')
        s += rect(x, y, w, h, shade)
    # the inner edge of each face, and the outer edge of the wall
    for d in exposed:
        if d == "n":
            s += line(0, band, T, band, INK, sw=1.2) + line(0, 1, T, 1, INK, sw=2)
        if d == "s":
            s += line(0, T - band, T, T - band, INK, sw=1.2) + line(0, T - 1, T, T - 1, INK, sw=2)
        if d == "w":
            s += line(band, 0, band, T, INK, sw=1.2) + line(1, 0, 1, T, INK, sw=2)
        if d == "e":
            s += line(T - band, 0, T - band, T, INK, sw=1.2) + line(T - 1, 0, T - 1, T, INK, sw=2)
    # inside corners: where two joined sides meet, the neighbors' faces turn the corner here
    # (assumes the diagonal neighbor is open, as it is for walls one tile thick)
    for d1, d2, (x, y) in (("n", "e", (T - band, 0)), ("e", "s", (T - band, T - band)),
                           ("s", "w", (0, T - band)), ("w", "n", (0, 0))):
        if d1 in mask and d2 in mask:
            s += rect(x, y, band, band, face)
            # the two edges of the square that face this tile's interior
            vx = x + band if x == 0 else x
            hy = y + band if y == 0 else y
            s += line(vx, y, vx, y + band, INK, sw=1.2) + line(x, hy, x + band, hy, INK, sw=1.2)
    # faint seams on joined sides (east and south only, so each seam is drawn once)
    for d in mask:
        if d == "e":
            s += line(T - 0.5, 0, T - 0.5, T, INK, sw=1, extra=' opacity="0.25"')
        if d == "s":
            s += line(0, T - 0.5, T, T - 0.5, INK, sw=1, extra=' opacity="0.25"')
    # bolts along open sides
    for d in (exposed if tid not in COMPOSITE else ""):
        for k in (16, 48):
            if d == "n":
                s += circle(k, band + 5, bolt, hi, stroke=INK, sw=0.8)
            if d == "s":
                s += circle(k, T - band - 5, bolt, hi, stroke=INK, sw=0.8)
            if d == "w":
                s += circle(band + 5, k, bolt, hi, stroke=INK, sw=0.8)
            if d == "e":
                s += circle(T - band - 5, k, bolt, hi, stroke=INK, sw=0.8)
    a.b = s
    return a


def walls():
    for tid in WALLS:
        for mask in MASKS:
            name = f"walls/{tid}_{mask or '0'}.svg"
            write(name, T, T, wall_tile(tid, mask),
                  f"{tid}, joined {('on ' + ', '.join(mask)) if mask else 'on no side'}")

    # Hull wedges: right triangle with legs on the left and bottom edges, sloped face
    # toward +x/+y (up and right on screen).
    for prefix in ("hull", "composite_armor"):
      for weight in ("light", "medium", "heavy"):
        tid = f"{prefix}_{weight}"
        face, top, hi, band, groove, bolt = WALLS[tid]
        for (bw, bh) in ((1, 1), (1, 2), (2, 1)):
            w, h = bw * T, bh * T
            tri = [(0, 0), (0, h), (w, h)]
            a = Art()
            a.b = poly(tri, top, stroke=INK, sw=2)
            if tid in COMPOSITE:
                cid = f"wedge_{tid}_{bw}x{bh}"
                a.b += (f'    <clipPath id="{cid}"><polygon points="0,0 0,{h} {w},{h}"/></clipPath>\n'
                        f'    <g clip-path="url(#{cid})">\n')
                for oy_ in range(0, h, T):
                    for ox_ in range(0, w, T):
                        a.b += f'    <g transform="translate({ox_},{oy_})">\n' + ceramic(0, 0, T, T, top, hi, f"{cid}_{ox_}_{oy_}") + "    </g>\n"
                a.b += "    </g>\n" + poly(tri, "none", stroke=INK, sw=2)
            # the sloped face: a band inside the hypotenuse
            hyp = math.hypot(w, h)
            inner = [(0, band * hyp / w), (w - band * hyp / h, h)]
            a.b += poly([(0, 0), (w, h), inner[1], inner[0]], face, stroke=INK, sw=1.2)
            a.b += poly([(0, 0), (w, h), inner[1], inner[0]], "#ffffff", extra=' opacity="0.14"')
            if tid not in COMPOSITE:
                a.b += circle(12, h - 12, bolt, hi, stroke=INK, sw=0.8)
            write(f"walls/{tid}_wedge_{bw}x{bh}.svg", w, h, a,
                  f"{tid} wedge {bw}x{bh}: legs on the left and bottom edges, sloped face toward +x/+y at rotation 0")
    shutil.copy(os.path.join(OUT, "walls/hull_medium_wedge_1x1.svg"), os.path.join(OUT, "walls/hull_wedge_1x1.svg"))
    shutil.copy(os.path.join(OUT, "walls/hull_medium_wedge_1x2.svg"), os.path.join(OUT, "walls/hull_wedge_1x2.svg"))
    shutil.copy(os.path.join(OUT, "walls/hull_medium_wedge_2x1.svg"), os.path.join(OUT, "walls/hull_wedge_2x1.svg"))
    written.extend(["walls/hull_wedge_1x1.svg", "walls/hull_wedge_1x2.svg", "walls/hull_wedge_2x1.svg"])


# ---------------------------------------------------------------------------
# Edge walls: segments along tile edges, joints at the grid's vertices, doors
# ---------------------------------------------------------------------------

EDGE = {"light": (6, "#6a7586"), "medium": (9, "#5b6675"), "heavy": (12, "#4b5563")}


# Edge wall colors by material: (fill, highlight); composite armor is segmented ceramic.
EDGE_MATERIAL = {"": (None, STEEL_HI), "composite_armor_": ({"light": "#5f6d69", "medium": "#53605c", "heavy": "#45514e"},
                                                            "#8a9893")}


def edge_wall_set(mat, fills, hi_col):
    """Edge wall segments and joints for one material (file prefix `mat`)."""
    for weight, (t, fill) in EDGE.items():
        fill = fills[weight] if fills else fill
        ceramic_ = bool(fills)
        lo, hi_ = 8 - t / 2, 8 + t / 2
        a = Art()
        a.b = rect(0, lo, T, t, fill)
        a.b += line(0, lo, T, lo, INK, sw=1.5) + line(0, hi_, T, hi_, INK, sw=1.5)
        a.b += line(0, lo + 1.5, T, lo + 1.5, hi_col, sw=1)
        if ceramic_:
            for xx in range(8, T, 8):  # tile joints across the wall
                a.b += line(xx, lo, xx, hi_, INK, sw=0.9, extra=' opacity="0.6"')
        else:
            for xx in (16, 48):
                a.b += rivet(xx, 8, 1.3)
        write(f"edges/edge_wall_{mat}{weight}_h.svg", T, 16, a,
              f"edge_wall_{mat}{weight}, horizontal: wall along a tile edge (edge at y=8), open ends meet joints")
        a = Art()
        a.b = rect(lo, 0, t, T, fill)
        a.b += line(lo, 0, lo, T, INK, sw=1.5) + line(hi_, 0, hi_, T, INK, sw=1.5)
        a.b += line(lo + 1.5, 0, lo + 1.5, T, hi_col, sw=1)
        if ceramic_:
            for yy in range(8, T, 8):
                a.b += line(lo, yy, hi_, yy, INK, sw=0.9, extra=' opacity="0.6"')
        else:
            for yy in (16, 48):
                a.b += rivet(8, yy, 1.3)
        write(f"edges/edge_wall_{mat}{weight}_v.svg", 16, T, a,
              f"edge_wall_{mat}{weight}, vertical: wall along a tile edge (edge at x=8)")
        # Joints at a vertex, by which of the four edges meeting there carry a wall.
        for mask in MASKS[1:]:
            a = Art()
            s = rect(lo, lo, t, t, fill)
            if "n" in mask:
                s += rect(lo, 0, t, lo, fill)
            if "s" in mask:
                s += rect(lo, hi_, t, 16 - hi_, fill)
            if "w" in mask:
                s += rect(0, lo, lo, t, fill)
            if "e" in mask:
                s += rect(hi_, lo, 16 - hi_, t, fill)
            # outline: walk each side of the center square; extend along joined arms
            if "n" in mask:
                s += line(lo, 0, lo, lo, INK, sw=1.5) + line(hi_, 0, hi_, lo, INK, sw=1.5)
            else:
                s += line(lo, lo, hi_, lo, INK, sw=1.5)
            if "s" in mask:
                s += line(lo, hi_, lo, 16, INK, sw=1.5) + line(hi_, hi_, hi_, 16, INK, sw=1.5)
            else:
                s += line(lo, hi_, hi_, hi_, INK, sw=1.5)
            if "w" in mask:
                s += line(0, lo, lo, lo, INK, sw=1.5) + line(0, hi_, lo, hi_, INK, sw=1.5)
            else:
                s += line(lo, lo, lo, hi_, INK, sw=1.5)
            if "e" in mask:
                s += line(hi_, lo, 16, lo, INK, sw=1.5) + line(hi_, hi_, 16, hi_, INK, sw=1.5)
            else:
                s += line(hi_, lo, hi_, hi_, INK, sw=1.5)
            if len(mask) >= 3 or mask in ("ne", "es", "sw", "nw"):
                s += rivet(8, 8, 1.4)
            a.b = s
            write(f"edges/edge_wall_{mat}{weight}_joint_{mask}.svg", 16, 16, a,
                  f"edge_wall_{mat}{weight} joint, centered on a vertex, walls toward {', '.join(mask)}")


def edges():
    for mat, (fills, hi_col) in EDGE_MATERIAL.items():
        edge_wall_set(mat, fills, hi_col)

    # Bulkhead doors: sealed and open, powered and unpowered, both orientations.
    for state in ("sealed", "open"):
        a = Art()
        a.b += rect(0, 2, 8, 12, STEEL_MID, rx=1, stroke=INK, sw=1.5)
        a.b += rect(T - 8, 2, 8, 12, STEEL_MID, rx=1, stroke=INK, sw=1.5)
        if state == "sealed":
            a.b += rect(8, 4, 24, 8, "#7a8594", stroke=INK, sw=1.5)
            a.b += rect(32, 4, 24, 8, "#7a8594", stroke=INK, sw=1.5)
            a.b += hazard_band(10, 6.5, 20, 3, f"door_{state}_a")
            a.b += hazard_band(34, 6.5, 20, 3, f"door_{state}_b")
            col = RED
        else:
            a.b += rect(8, 4, 5, 8, "#7a8594", stroke=INK, sw=1.5)
            a.b += rect(T - 13, 4, 5, 8, "#7a8594", stroke=INK, sw=1.5)
            col = GREEN
        light(a, 4, 8, 2, col, halo=False)
        light(a, T - 4, 8, 2, col, halo=False)
        write(f"edges/bulkhead_door_{state}_h.svg", T, 16, a, f"bulkhead door, {state}, horizontal",
              unpowered=f"edges/bulkhead_door_{state}_unpowered_h.svg")
        write(f"edges/bulkhead_door_{state}_v.svg", 16, T, a, f"bulkhead door, {state}, vertical",
              unpowered=f"edges/bulkhead_door_{state}_unpowered_v.svg", transform="translate(16,0) rotate(90)")


def edge_conduits():
    """Power conduits run along tile edges (inside thin walls or over full-tile walls), drawn
    over the wall like edge walls: a segment per edge, a joint at each vertex, and a tap box
    where a tile's conduit arm meets an edge run. The live core is a glow, not a light."""
    TR, CORE, BAND = "#20262e", "#6b5a2e", STEEL_DARK
    lo, hi_ = 4.5, 11.5  # trough across the edge line at 8

    def run_h(a, x0, x1):
        """Horizontal run from x0 to x1 along y = 8 (open ends)."""
        a.b += rect(x0, lo, x1 - x0, hi_ - lo, TR)
        a.b += line(x0, lo, x1, lo, INK, sw=1.2) + line(x0, hi_, x1, hi_, INK, sw=1.2)
        a.b += rect(x0, 6.5, x1 - x0, 3, CORE)

    def clamp_h(a, x):
        a.b += rect(x - 2, lo - 1, 4, hi_ - lo + 2, BAND, rx=0.8, stroke=INK, sw=1)

    def box(a, cx, cy, r, glow):
        a.b += rect(cx - r, cy - r, 2 * r, 2 * r, HOUSING, rx=1.5, stroke=INK, sw=1.3)
        a.b += bevel(cx - r + 1, cy - r + 1, 2 * r - 2, 2 * r - 2, HOUSING_HI, INK, sw=1)
        a.b += circle(cx, cy, r * 0.45, "#6b5a2e", stroke=INK, sw=1)
        glow.append(circle(cx, cy, r * 0.75, POWER, extra=' opacity="0.25"') + circle(cx, cy, r * 0.35, POWER)
                    + circle(cx, cy, r * 0.18, "#fff3c4"))

    # one-tile runs: clamps at the quarter points break the lit core into three
    a = Art()
    run_h(a, 0, T)
    for x in (16, 48):
        clamp_h(a, x)
    e = ""
    for (x0, x1) in ((0, 14), (18, 46), (50, T)):  # the lit core shows between the clamps
        e += rect(x0, 6.5, x1 - x0, 3, POWER) + line(x0, 8, x1, 8, "#fff3c4", sw=0.8, extra=' opacity="0.6"')
    a.e = lit(e, "glow")
    p = "edges/edge_conduit_h.svg"
    write(p, T, 16, a, "edge_conduit, horizontal: conduit along a tile edge (edge at y=8), open ends meet joints",
          unpowered="edges/edge_conduit_unpowered_h.svg")
    write("edges/edge_conduit_v.svg", 16, T, a, "edge_conduit, vertical: conduit along a tile edge (edge at x=8)",
          unpowered="edges/edge_conduit_unpowered_v.svg", transform="translate(16,0) rotate(90)")

    # joints at a vertex, by which of the four edges meeting there carry a conduit
    for mask in MASKS[1:]:
        a = Art()
        glow = []
        arms = {"n": (lo, 0, hi_ - lo, 8), "s": (lo, 8, hi_ - lo, 8), "w": (0, lo, 8, hi_ - lo), "e": (8, lo, 8, hi_ - lo)}
        cores = {"n": (6.5, 0, 3, 8), "s": (6.5, 8, 3, 8), "w": (0, 6.5, 8, 3), "e": (8, 6.5, 8, 3)}
        for side in mask:
            x, y, w, h = arms[side]
            a.b += rect(x, y, w, h, TR)
        for side in mask:  # outlines along each arm
            x, y, w, h = arms[side]
            if side in "ns":
                a.b += line(x, y, x, y + h, INK, sw=1.2) + line(x + w, y, x + w, y + h, INK, sw=1.2)
            else:
                a.b += line(x, y, x + w, y, INK, sw=1.2) + line(x, y + h, x + w, y + h, INK, sw=1.2)
        plain = len(mask) == 1 or mask in ("ns", "ew")
        gap = 4.5 if plain else 6  # half the clamp or box the core runs under
        lit_cores = {"n": (6.5, 0, 3, 8 - gap), "s": (6.5, 8 + gap, 3, 8 - gap),
                     "w": (0, 6.5, 8 - gap, 3), "e": (8 + gap, 6.5, 8 - gap, 3)}
        e = ""
        for side in mask:
            x, y, w, h = cores[side]
            a.b += rect(x, y, w, h, CORE)
            e += rect(*lit_cores[side], POWER)
        if plain:
            # an end cap, or a straight joint: a plain clamp
            a.b += rect(lo - 1, lo - 1, hi_ - lo + 2, hi_ - lo + 2, BAND, rx=1.2, stroke=INK, sw=1.2)
            a.e = lit(e, "glow")
        else:
            # a corner, T or cross: a junction box with its own status lamp
            box(a, 8, 8, 5.5, glow)
            a.e = lit(e + glow[0], "glow")
        name = f"edges/edge_conduit_joint_{mask}.svg"
        write(name, 16, 16, a, f"edge_conduit joint, centered on a vertex, conduits toward {', '.join(mask)}",
              unpowered=f"edges/edge_conduit_joint_unpowered_{mask}.svg")

    # tap: where a tile-center conduit's arm meets an edge run, at the edge's middle
    a = Art()
    glow = []
    box(a, 8, 8, 6.5, glow)
    for (x, y) in ((3, 3), (13, 3), (3, 13), (13, 13)):
        a.b += rivet(x, y, 0.9)
    a.e = lit(glow[0], "glow")
    write("edges/edge_conduit_tap.svg", 16, 16, a,
          "edge_conduit tap: junction box centered on an edge's middle, where a tile's conduit arm meets the edge run",
          unpowered="edges/edge_conduit_tap_unpowered.svg")


def pipes():
    """Pipes from tanks to the systems that draw on them, drawn like conduits but passive
    (no emissive, no unpowered variant): across a tile, a flanged fitting with arms toward
    each pipe neighbor; along an edge, a segment per edge, a joint at each vertex and a
    tee where a tile's arm meets an edge run. They run under the deck plating, so the
    fitting is an inspection plate over the pipe."""
    lo, hi_ = 5, 11  # the pipe across the edge line at 8

    def run_h(a, x0, x1, y=8, r=3):
        a.b += rect(x0, y - r, x1 - x0, 2 * r, PIPE)
        a.b += line(x0, y - r, x1, y - r, INK, sw=1.2) + line(x0, y + r, x1, y + r, INK, sw=1.2)
        a.b += line(x0, y - r + 1.5, x1, y - r + 1.5, PIPE_HI, sw=1, extra=' opacity="0.8"')

    def flange_h(a, x, y=8, r=3):
        a.b += rect(x - 1.8, y - r - 1.5, 3.6, 2 * r + 3, PIPE_LO, rx=0.8, stroke=INK, sw=1)

    # across a tile: an inspection plate with a valve wheel, and an arm overlay
    a = Art()
    a.b += rect(18, 18, 28, 28, DECK_SEAM, rx=4, stroke=INK, sw=1.5)
    a.b += rect(20, 20, 24, 24, PIPE_LO, rx=3)
    a.b += circle(32, 32, 9, PIPE, stroke=INK, sw=1.5)
    a.b += circle(32, 32, 6.5, "none", stroke=PIPE_HI, sw=1.5)
    for k in range(4):
        ang = math.pi / 4 + k * math.pi / 2
        a.b += line(32, 32, 32 + math.cos(ang) * 6.5, 32 + math.sin(ang) * 6.5, PIPE_HI, sw=1.2)
    a.b += circle(32, 32, 2.2, INK)
    for (x, y) in ((22, 22), (42, 22), (22, 42), (42, 42)):
        a.b += rivet(x, y, 1.1)
    write("tiles/pipe_hub.svg", T, T, a, "pipe_hub overlay: valve plate over a pipe across the tile, under the arms")

    a = Art()
    a.b = rect(28, 0, 8, 20, PIPE) + line(28, 0, 28, 20, INK, sw=1.2) + line(36, 0, 36, 20, INK, sw=1.2)
    a.b += line(29.5, 0, 29.5, 20, PIPE_HI, sw=1, extra=' opacity="0.8"')
    a.b += rect(26.5, 6, 11, 3.6, PIPE_LO, rx=0.8, stroke=INK, sw=1)
    write("tiles/pipe_arm.svg", T, T, a,
          "pipe arm overlay: runs from the valve plate to the top edge; rotate toward each pipe neighbor")

    # along an edge: flanges at the quarter points
    a = Art()
    run_h(a, 0, T)
    for x in (16, 48):
        flange_h(a, x)
    write("edges/edge_pipe_h.svg", T, 16, a,
          "edge_pipe, horizontal: pipe along a tile edge (edge at y=8), open ends meet joints")
    write("edges/edge_pipe_v.svg", 16, T, a, "edge_pipe, vertical: pipe along a tile edge (edge at x=8)",
          transform="translate(16,0) rotate(90)")

    for mask in MASKS[1:]:
        a = Art()
        arms = {"n": (lo, 0, hi_ - lo, 8), "s": (lo, 8, hi_ - lo, 8), "w": (0, lo, 8, hi_ - lo), "e": (8, lo, 8, hi_ - lo)}
        for side in mask:
            x, y, w, h = arms[side]
            a.b += rect(x, y, w, h, PIPE)
        for side in mask:
            x, y, w, h = arms[side]
            if side in "ns":
                a.b += line(x, y, x, y + h, INK, sw=1.2) + line(x + w, y, x + w, y + h, INK, sw=1.2)
            else:
                a.b += line(x, y, x + w, y, INK, sw=1.2) + line(x, y + h, x + w, y + h, INK, sw=1.2)
        if len(mask) == 1 or mask in ("ns", "ew"):
            # an end cap or a straight joint: a coupling
            a.b += rect(lo - 1.5, lo - 1.5, hi_ - lo + 3, hi_ - lo + 3, PIPE_LO, rx=1.5, stroke=INK, sw=1.2)
        else:
            # an elbow, tee or cross: a round fitting
            a.b += circle(8, 8, 5.5, PIPE_LO, stroke=INK, sw=1.3)
            a.b += circle(8, 8, 3, PIPE, stroke=PIPE_HI, sw=1)
        write(f"edges/edge_pipe_joint_{mask}.svg", 16, 16, a,
              f"edge_pipe joint, centered on a vertex, pipes toward {', '.join(mask)}")

    a = Art()
    a.b += circle(8, 8, 6.5, PIPE_LO, stroke=INK, sw=1.3)
    a.b += circle(8, 8, 4, PIPE, stroke=PIPE_HI, sw=1)
    a.b += circle(8, 8, 1.5, INK)
    write("edges/edge_pipe_tap.svg", 16, 16, a,
          "edge_pipe tap: tee centered on an edge's middle, where a tile's pipe arm meets the edge run")


# ---------------------------------------------------------------------------
# Components. Each size is its own design; sizes share parts and palette, not layout.
# ---------------------------------------------------------------------------

def housing(a, w, h, inset=4):
    a.b += rect(0, 0, w, h, DECK_SEAM)
    a.b += plate(inset, inset, w - 2 * inset, h - 2 * inset, HOUSING, HOUSING_HI, INK, rx=4)


def pipe(a, x1, y1, x2, y2, wdt=6, color=STEEL_MID):
    a.b += line(x1, y1, x2, y2, INK, sw=wdt + 3)
    a.b += line(x1, y1, x2, y2, color, sw=wdt)
    a.b += line(x1, y1, x2, y2, STEEL_HI, sw=1, extra=' opacity="0.5"')


def pump(a, cx, cy, r):
    a.b += circle(cx, cy, r, STEEL_DARK, stroke=INK, sw=2)
    for i in range(5):
        ang = 2 * math.pi * i / 5
        a.b += path(f"M{f(cx)},{f(cy)} Q{f(cx + math.cos(ang + 0.6) * r * 0.8)},{f(cy + math.sin(ang + 0.6) * r * 0.8)} "
                    f"{f(cx + math.cos(ang + 1.0) * r * 0.85)},{f(cy + math.sin(ang + 1.0) * r * 0.85)}",
                    stroke=STEEL_HI, sw=max(1.5, r * 0.12))
    a.b += circle(cx, cy, r * 0.22, HOUSING_HI, stroke=INK, sw=1)


def tank(a, x, y, w, h, fill="#2a4a38", hi="#4e8a66"):
    r = min(w, h) / 2
    a.b += rect(x, y, w, h, fill, rx=r, stroke=INK, sw=2)
    if h >= w:
        a.b += rect(x + w * 0.15, y + r * 0.4, w * 0.2, h - r * 0.8, hi, rx=w * 0.1)
    else:
        a.b += rect(x + r * 0.4, y + h * 0.15, w - r * 0.8, h * 0.2, hi, rx=h * 0.1)


# --- Reactor -----------------------------------------------------------------

def reactor(size):
    n = PRIMARY[size][0]
    w = h = n * T
    c = ACCENT["reactor"]
    a = Art()
    gid = f"reactor_{size}_g"
    core = {"s": (3, 0.7), "m": (4.5, 0.8), "l": (6, 0.85), "xl": (7, 0.9)}[size]  # reach, intensity
    a.defs = glow_def(gid, c) + halo_def(gid + "h", c)
    housing(a, w, h)
    cx = cy = w / 2
    if size == "s":
        # compact core: a sealed canister with three cooling fins and one lamp
        for i in range(3):
            ang = -math.pi / 2 + 2 * math.pi * i / 3
            a.b += line(cx, cy, cx + math.cos(ang) * 24, cy + math.sin(ang) * 24, INK, sw=9)
            a.b += line(cx, cy, cx + math.cos(ang) * 24, cy + math.sin(ang) * 24, STEEL, sw=6)
        a.b += circle(cx, cy, 13, STEEL_DARK, stroke=INK, sw=2)
        a.b += circle(cx, cy, 7, "#4a3320", stroke=INK, sw=1.5)
        a.e += lit(circle(cx, cy, 12, f"url(#{gid})"), "point", *core)
        light(a, 54, 54, 2.5, c)
    elif size == "m":
        # fission pile: core, ring of six control rods, coolant pipes to the corners
        for (px, py) in ((12, 12), (w - 12, 12), (12, h - 12), (w - 12, h - 12)):
            pipe(a, cx, cy, px, py, 6)
            a.b += circle(px, py, 5, STEEL_MID, stroke=INK, sw=1.5)
        a.b += circle(cx, cy, 44, STEEL_DARK, stroke=INK, sw=2)
        a.b += circle(cx, cy, 34, HOUSING, stroke=INK, sw=1.5)
        for i in range(6):
            ang = 2 * math.pi * i / 6
            px, py = cx + math.cos(ang) * 39, cy + math.sin(ang) * 39
            a.b += circle(px, py, 3.5, "#4a3320", stroke=INK, sw=1)
            a.e += lit(circle(px, py, 2.5, c), "glow")
        a.b += circle(cx, cy, 16, "#4a3320", stroke=INK, sw=1.5)
        a.e += lit(circle(cx, cy, 26, f"url(#{gid})")
                   + circle(cx, cy, 20, "none", stroke=c, sw=1.5, extra=' opacity="0.8"'), "point", *core)
    elif size == "l":
        # tokamak: a segmented torus around the core, four coolant pumps, a control panel
        for (px, py) in ((30, 30), (w - 30, 30), (30, h - 30), (w - 30, h - 30)):
            pipe(a, cx, cy, px, py, 8)
            pump(a, px, py, 17)
        a.b += circle(cx, cy, 66, STEEL_DARK, stroke=INK, sw=2.5)
        a.b += circle(cx, cy, 46, HOUSING, stroke=INK, sw=2)
        for i in range(12):
            ang = 2 * math.pi * i / 12
            a.b += line(cx + math.cos(ang) * 47, cy + math.sin(ang) * 47, cx + math.cos(ang) * 65,
                        cy + math.sin(ang) * 65, INK, sw=2)
        for i in range(12):
            ang = 2 * math.pi * (i + 0.5) / 12
            a.b += rect(cx + math.cos(ang) * 56 - 3, cy + math.sin(ang) * 56 - 3, 6, 6, STEEL, rx=1, stroke=INK,
                        sw=1)
        a.b += circle(cx, cy, 22, "#4a3320", stroke=INK, sw=2)
        a.e += lit(circle(cx, cy, 56, "none", stroke=c, sw=7, extra=' opacity="0.45"')
                   + circle(cx, cy, 56, "none", stroke="#ffe0b8", sw=1.5, extra=' opacity="0.9"')
                   + circle(cx, cy, 34, f"url(#{gid})"), "point", *core)
        console(a, cx - 30, h - 26, 60, 18, c)
    else:
        # fusion plant: outer magnet ring with twelve coils, inner plasma ring, four pump
        # stations, a coolant loop and two control desks
        a.b += circle(cx, cy, 128, "none", stroke=INK, sw=13)
        a.b += circle(cx, cy, 128, "none", stroke=STEEL_MID, sw=9)
        for (px, py) in ((40, 40), (w - 40, 40), (40, h - 40), (w - 40, h - 40)):
            pipe(a, px, py, cx + (px - cx) * 0.55, cy + (py - cy) * 0.55, 10)
            pump(a, px, py, 24)
        a.b += circle(cx, cy, 104, STEEL_DARK, stroke=INK, sw=2.5)
        for i in range(12):
            ang = 2 * math.pi * i / 12
            px, py = cx + math.cos(ang) * 92, cy + math.sin(ang) * 92
            a.b += circle(px, py, 13, "#8a6a3a", stroke=INK, sw=2)
            a.b += circle(px, py, 8, "#b38a4a", stroke=INK, sw=1)
        a.b += circle(cx, cy, 72, HOUSING, stroke=INK, sw=2)
        a.b += circle(cx, cy, 34, STEEL_DARK, stroke=INK, sw=2)
        a.b += circle(cx, cy, 22, "#4a3320", stroke=INK, sw=2)
        a.e += lit(circle(cx, cy, 62, "none", stroke=c, sw=12, extra=' opacity="0.4"')
                   + circle(cx, cy, 62, "none", stroke="#ffe0b8", sw=2.5)
                   + circle(cx, cy, 40, f"url(#{gid})"), "point", *core)
        console(a, 24, cy - 40, 22, 80, c, 1)
        console(a, w - 46, cy - 40, 22, 80, c, 3)
        for ang in (0.25, 0.75, 1.25, 1.75):
            light(a, cx + math.cos(ang * math.pi) * 116, cy + math.sin(ang * math.pi) * 116, 3, c)
    return w, h, a


# --- Shield emitter ----------------------------------------------------------

def shield_emitter(size):
    n = PRIMARY[size][0]
    w = h = n * T
    c = ACCENT["shield_emitter"]
    a = Art()
    gid = f"shield_{size}_g"
    a.defs = glow_def(gid, c)
    housing(a, w, h)
    cx = cy = w / 2

    def emitter(x, y, r, prongs, main=True):
        pts = [(x + r * math.cos(math.pi / 2 + i * 2 * math.pi / prongs),
                y - r * math.sin(math.pi / 2 + i * 2 * math.pi / prongs)) for i in range(prongs)]
        a.b += poly(pts, STEEL_DARK, stroke=INK, sw=2)
        for (px, py) in pts:
            a.b += circle(px, py, max(2.5, r * 0.11), "#1f4a5a", stroke=INK, sw=1.2)
            a.e += lit(circle(px, py, max(1.8, r * 0.08), c), "glow")
        a.b += circle(x, y, r * 0.6, HOUSING, stroke=INK, sw=1.5)
        dome = (circle(x, y, r * 0.55, f"url(#{gid})")
                + circle(x, y, r * 0.35, "none", stroke=c, sw=1.2, extra=' opacity="0.85"'))
        a.e += lit(dome, "point", {"s": 2, "m": 3, "l": 4}[size], 0.6) if main else lit(dome, "glow")

    def capacitors(x, y, count, vertical):
        for i in range(count):
            px, py = (x, y + i * 22) if vertical else (x + i * 22, y)
            a.b += rect(px, py, 18, 18, "#24414d", rx=3, stroke=INK, sw=1.5)
            a.b += rect(px + 4, py + 4, 10, 10, "#1b2f38", rx=2)
            a.e += lit(rect(px + 6, py + 7, 6, 4, c, extra=' opacity="0.8"'), "glow")

    if size == "s":
        emitter(cx, cy + 2, 22, 3)
    elif size == "m":
        emitter(cx, cy, 46, 6)
    elif size == "l":
        emitter(cx, cy - 6, 62, 6)
        capacitors(14, h - 32, 7, False)
        light(a, 14, 14, 3, c)
        light(a, w - 14, 14, 3, c)
    else:
        # field projector ringed by six emitter nodes, capacitor banks on both sides
        a.b += circle(cx, cy, 110, STEEL_DARK, stroke=INK, sw=2)
        for i in range(6):
            ang = 2 * math.pi * i / 6
            nx_, ny_ = cx + math.cos(ang) * 92, cy + math.sin(ang) * 92
            pipe(a, cx, cy, nx_, ny_, 6, "#2f5664")
            emitter(nx_, ny_, 18, 3, main=False)
        a.b += circle(cx, cy, 50, HOUSING, stroke=INK, sw=2)
        dome = circle(cx, cy, 48, f"url(#{gid})")
        for k in (0.75, 0.5, 0.25):
            dome += circle(cx, cy, 48 * k, "none", stroke=c, sw=1.2, extra=' opacity="0.85"')
        a.e += lit(dome, "point", 5, 0.65)
        capacitors(14, 30, 12, True)
        capacitors(w - 32, 30, 12, True)
    return w, h, a


# --- Turret mount: mounting hardware and the operator's station ---------------

def turret_mount(size):
    """The turret's base under the deck: bearing ring, traverse drive, feed, and the
    operator's station. The turret itself (turrets/) sits on top at the mount's center."""
    n = PRIMARY[size][0]
    w = h = n * T
    c = ACCENT["turret_mount"]
    a = Art()
    housing(a, w, h)
    cx = cy = w / 2

    def ring(x, y, r, bolts):
        a.b += circle(x, y, r, STEEL_DARK, stroke=INK, sw=2)
        a.b += circle(x, y, r * 0.86, "none", stroke=INK, sw=1, extra=' stroke-dasharray="3 3"')
        for i in range(bolts):
            ang = 2 * math.pi * i / bolts
            a.b += circle(x + math.cos(ang) * r * 0.93, y + math.sin(ang) * r * 0.93, max(1.3, r * 0.035), STEEL_HI)
        a.b += circle(x, y, r * 0.72, STEEL_MID, stroke=INK, sw=1.5)
        a.b += circle(x, y, r * 0.3, HOUSING, stroke=INK, sw=1.5)
        a.b += circle(x, y, r * 0.12, STEEL_HI)

    def drive(x, y, r):
        """A traverse motor meshing with the bearing ring."""
        a.b += circle(x, y, r, HOUSING_HI, stroke=INK, sw=1.5)
        for i in range(8):
            ang = 2 * math.pi * i / 8
            a.b += line(x + math.cos(ang) * r, y + math.sin(ang) * r, x + math.cos(ang) * (r + 3),
                        y + math.sin(ang) * (r + 3), INK, sw=2)
        a.b += circle(x, y, r * 0.4, STEEL_HI, stroke=INK, sw=1)

    def drive_at(r_ring, r_drive, deg):
        d = r_ring + r_drive + 2
        drive(cx + math.cos(math.radians(deg)) * d, cy + math.sin(math.radians(deg)) * d, r_drive)

    # The bearing ring is centered on the mount: the turret's pivot is the mount's center.
    if size == "s":
        ring(cx, cy, 15, 8)
        a.b += rect(10, 50, 44, 11, HOUSING_HI, rx=2, stroke=INK, sw=1.5)
        screen(a, 13, 51.5, 22, 8, c)
        a.b += circle(44, 55.5, 3.5, SEAT, stroke=INK, sw=1)  # control grip
        light(a, 56, 8, 2.2, c)
    elif size == "m":
        ring(cx, cy, 32, 12)
        drive_at(32, 8, -45)
        a.b += rect(8, 14, 14, 60, "#3a3226", rx=2, stroke=INK, sw=1.5)  # ammunition feed
        for yy in range(18, 72, 6):
            a.b += line(10, yy, 20, yy, "#8f6b3e", sw=2)
        console(a, 8, 102, 56, 18, c)
        seat(a, 84, 111, "left", 0.8)
        light(a, 116, 12, 2.5, c)
    elif size == "l":
        ring(cx, cy, 50, 16)
        drive_at(50, 10, -135)
        drive_at(50, 10, -45)
        a.b += rect(8, 40, 18, 100, "#3a3226", rx=2, stroke=INK, sw=1.5)  # ammunition hoist
        for yy in range(44, 138, 7):
            a.b += line(11, yy, 23, yy, "#8f6b3e", sw=2.5)
        a.b += rect(w - 26, 40, 18, 100, HOUSING_HI, rx=2, stroke=INK, sw=1.5)  # power and cooling
        pipe(a, w - 17, 48, w - 17, 132, 5, "#2f5664")
        console(a, 14, 154, 60, 22, c)
        seat(a, 92, 165, "left", 0.9)
        console(a, 124, 156, 52, 18, c, 2)  # loader's panel
        light(a, 14, 14, 3, c)
        light(a, w - 14, 14, 3, c)
    else:
        # barbette: a large bearing race with two drives, an ammunition conveyor, the
        # gunner's station and a loader's station
        ring(cx, cy, 82, 24)
        drive_at(82, 14, -135)
        drive_at(82, 14, -45)
        a.b += rect(14, 40, 26, 196, "#3a3226", rx=3, stroke=INK, sw=2)
        for yy in range(46, 230, 9):
            a.b += rect(18, yy, 18, 6, "#8f6b3e", rx=1, stroke=INK, sw=0.8)
        a.b += rect(w - 40, 40, 26, 196, HOUSING_HI, rx=3, stroke=INK, sw=2)
        for xx in (w - 34, w - 22):
            pipe(a, xx, 48, xx, 228, 4, "#2f5664")
        console(a, 30, 250, 96, 24, c)
        seat(a, 78, 292, "up")
        console(a, 186, 252, 96, 22, c, 3)
        seat(a, 234, 292, "up")
        for xx in (24, w - 24):
            light(a, xx, 24, 3.5, c)
    return w, h, a


# --- Crew rooms: helm and weapons --------------------------------------------

def crew_room(kind, size):
    tw, th = AUX[size]
    w, h = tw * T, th * T
    c = ACCENT[kind]
    a = Art()
    a.b = rect(0, 0, w, h, "none", stroke=INK, sw=2)

    def holo(cx, cy, r):
        a.b += circle(cx, cy, r, HOUSING, stroke=INK, sw=2)
        a.b += circle(cx, cy, r * 0.8, GLASS, stroke=INK, sw=1)
        a.e += circle(cx, cy, r * 0.8, c, extra=' opacity="0.2"')
        for k in (0.55, 0.3):
            a.e += circle(cx, cy, r * k, "none", stroke=c, sw=1, extra=' opacity="0.8"')
        a.e += line(cx - r * 0.7, cy, cx + r * 0.7, cy, c, sw=0.8, extra=' opacity="0.6"')
        a.e += line(cx, cy - r * 0.7, cx, cy + r * 0.7, c, sw=0.8, extra=' opacity="0.6"')
        if kind == "weapons":
            for (dx, dy) in ((0.3, -0.2), (-0.25, 0.35), (0.45, 0.3)):
                a.e += poly([(cx + dx * r, cy + dy * r - 3), (cx + dx * r + 3, cy + dy * r + 2),
                             (cx + dx * r - 3, cy + dy * r + 2)], "#ff5a5a")

    def arc_console(cx, cy, r, a0, a1, depth):
        """A curved console bank following an arc (angles in degrees, 0 = +x, screen y down)."""
        p0 = (cx + r * math.cos(math.radians(a0)), cy + r * math.sin(math.radians(a0)))
        p1 = (cx + r * math.cos(math.radians(a1)), cy + r * math.sin(math.radians(a1)))
        r2 = r - depth
        q0 = (cx + r2 * math.cos(math.radians(a0)), cy + r2 * math.sin(math.radians(a0)))
        q1 = (cx + r2 * math.cos(math.radians(a1)), cy + r2 * math.sin(math.radians(a1)))
        large = 1 if abs(a1 - a0) > 180 else 0
        d = (f"M{f(p0[0])},{f(p0[1])} A{f(r)},{f(r)} 0 {large} 1 {f(p1[0])},{f(p1[1])} "
             f"L{f(q1[0])},{f(q1[1])} A{f(r2)},{f(r2)} 0 {large} 0 {f(q0[0])},{f(q0[1])} Z")
        a.b += path(d, fill=HOUSING, stroke=INK, sw=1.5)
        rs = r - depth * 0.45
        steps = max(2, int(abs(a1 - a0) / 18))
        for i in range(steps):
            t0 = math.radians(a0 + (a1 - a0) * (i + 0.15) / steps)
            t1 = math.radians(a0 + (a1 - a0) * (i + 0.85) / steps)
            a.b += line(cx + rs * math.cos(t0), cy + rs * math.sin(t0), cx + rs * math.cos(t1),
                        cy + rs * math.sin(t1), GLASS, sw=depth * 0.45)
            a.e += line(cx + rs * math.cos(t0), cy + rs * math.sin(t0), cx + rs * math.cos(t1),
                        cy + rs * math.sin(t1), c, sw=depth * 0.2, extra=' opacity="0.7"')

    if kind == "helm":
        if size == "s":
            # one pilot: a console with a control yoke, facing +y
            console(a, 6, 6, 52, 22, c)
            a.b += rect(24, 30, 16, 5, SEAT_DARK, rx=2, stroke=INK, sw=1)
            seat(a, 32, 50, "up")
            light(a, 32, 112, 2.5, c)
        elif size == "m":
            # pilot and copilot at a wraparound console, a nav display behind them
            arc_console(64, 96, 88, 238, 302, 20)
            seat(a, 40, 70, "up")
            seat(a, 88, 70, "up")
            console(a, 30, 140, 68, 26, c, 2)
            light(a, 12, 180, 2.5, c)
        elif size == "l":
            # bridge: forward arc with three stations, the captain's chair, nav table
            # and side consoles
            arc_console(96, 150, 140, 240, 300, 24)
            for x in (50, 96, 142):
                seat(a, x, 70 if x == 96 else 82, "up")
            seat(a, 96, 170, "up", 1.2)
            a.b += rect(80, 186, 32, 6, HOUSING_HI, rx=2, stroke=INK, sw=1)
            holo(96, 270, 42)
            console(a, 8, 220, 24, 90, c, 1)
            console(a, w - 32, 220, 24, 90, c, 4)
            for x in (40, 152):
                seat(a, x, 345, "down", 0.9)
            console(a, 20, 360, 40, 20, c, 5)
            console(a, 132, 360, 40, 20, c, 6)
            strip_light(a, 70, h - 12, 52, 5)
        else:
            # command bridge: five forward stations on a wide arc, a raised captain's dais,
            # a large plot table, side banks and a briefing table aft
            arc_console(160, 250, 236, 236, 304, 28)
            for i, x in enumerate((60, 110, 160, 210, 260)):
                seat(a, x, 76 if x == 160 else (86 if x in (110, 210) else 108), "up")
            a.b += circle(160, 220, 44, DECK_HI, stroke=INK, sw=2)
            a.b += circle(160, 220, 38, "none", stroke=DECK_SEAM, sw=1.5)
            seat(a, 160, 220, "up", 1.3)
            console(a, 136, 250, 48, 10, c)
            holo(160, 380, 70)
            for yy in (300, 420):
                console(a, 8, yy, 26, 90, c, yy)
                console(a, w - 34, yy, 26, 90, c, yy + 1)
                seat(a, 50, yy + 45, "left", 0.9)
                seat(a, w - 50, yy + 45, "right", 0.9)
            a.b += rect(100, 560, 120, 70, "#6b5a48", rx=6, stroke=INK, sw=2)
            screen(a, 112, 572, 96, 46, c, 3)
            for x in (120, 160, 200):
                seat(a, x, 650, "up", 0.85)
                seat(a, x, 540, "down", 0.85)
            for x in (60, 260):
                strip_light(a, x - 30, h - 14, 60, 6)
    else:
        if size == "s":
            # one fire-control director: a console with a targeting scope, facing +y
            console(a, 6, 6, 52, 22, c)
            a.b += circle(32, 40, 6, HOUSING_HI, stroke=INK, sw=1.5)  # scope
            a.e += circle(32, 40, 2.5, c)
            seat(a, 32, 60, "up")
            light(a, 32, 112, 2.5, c)
        elif size == "m":
            # two gunnery stations facing a tactical screen wall
            a.b += rect(6, 6, w - 12, 18, HOUSING, rx=2, stroke=INK, sw=1.5)
            screen(a, 10, 9, w - 20, 12, c)
            for x in (36, 92):
                console(a, x - 22, 40, 44, 18, c, x)
                seat(a, x, 76, "up")
            holo(64, 140, 30)
        elif size == "l":
            # fire-control room: plot table in the middle, consoles along both walls
            a.b += rect(6, 6, w - 12, 18, HOUSING, rx=2, stroke=INK, sw=1.5)
            screen(a, 10, 9, w - 20, 12, c, 1)
            holo(96, 180, 52)
            for yy in (60, 250, 330):
                console(a, 8, yy, 22, 56, c, yy)
                seat(a, 46, yy + 28, "left", 0.85)
                console(a, w - 30, yy, 22, 56, c, yy + 2)
                seat(a, w - 46, yy + 28, "right", 0.85)
            seat(a, 96, 260, "up", 1.1)
            strip_light(a, 70, h - 12, 52, 5)
        else:
            # combat information center: a ring of consoles around the tactical plot,
            # an ammunition and damage board on the aft wall
            a.b += rect(6, 6, w - 12, 22, HOUSING, rx=2, stroke=INK, sw=1.5)
            screen(a, 10, 9, w - 20, 16, c, 2)
            cx, cy = 160, 300
            holo(cx, cy, 78)
            for i in range(8):
                ang = 2 * math.pi * (i + 0.5) / 8
                px, py = cx + math.cos(ang) * 120, cy + math.sin(ang) * 120
                a.b += f'    <g transform="rotate({f(math.degrees(ang) + 90)} {f(px)} {f(py)})">\n'
                console(a, px - 22, py - 10, 44, 18, c, i)
                a.b += "    </g>\n"
                seat(a, cx + math.cos(ang) * 150, cy + math.sin(ang) * 150, "up", 0.85)
            for yy in (500, 580):
                console(a, 8, yy, 26, 64, c, yy)
                console(a, w - 34, yy, 26, 64, c, yy + 1)
            a.b += rect(30, h - 70, w - 60, 52, HOUSING, rx=3, stroke=INK, sw=2)
            for i in range(10):
                screen(a, 38 + i * 25, h - 62, 20, 36, c if i % 3 else "#ff5a5a", i)
            strip_light(a, 130, 452, 60, 6)
    # screens, holo tables and ceiling strips light the room; status lamps keep their `glow`
    a.e = lit(a.e, "point", {"s": 2, "m": 2.5, "l": 3, "xl": 3.5}[size], 0.5)
    return w, h, a


# --- Life support --------------------------------------------------------------

def life_support(size):
    tw, th = AUX[size]
    w, h = tw * T, th * T
    c = ACCENT["life_support"]
    a = Art()
    housing(a, w, h)
    if size == "s":
        # a compact scrubber: oxygen canister over a fan
        tank(a, 14, 10, 36, 48)
        a.b += rect(29, 6, 6, 6, STEEL_MID, stroke=INK, sw=1)
        pump(a, 32, 92, 22)
        light(a, 32, 34, 4, c)
    elif size == "m":
        # two oxygen tanks, a scrubber bank and a control panel
        tank(a, 12, 12, 46, 70)
        tank(a, 70, 12, 46, 70)
        pipe(a, 35, 82, 35, 100, 5)
        pipe(a, 93, 82, 93, 100, 5)
        pipe(a, 35, 100, 93, 100, 5)
        for x in (36, 92):
            pump(a, x, 140, 22)
        console(a, 30, 168, 68, 16, c)
        light(a, 35, 46, 4, c)
        light(a, 93, 46, 4, c)
    elif size == "l":
        # an air plant: electrolysis cell, water tank, scrubber columns, fans, a manifold
        tank(a, 14, 14, 164, 54, "#2a3f4a", "#4f7a8c")  # water
        tank(a, 22, 84, 64, 120)  # oxygen
        tank(a, 106, 84, 64, 120)
        a.b += rect(22, 220, 148, 64, "#2b3a33", rx=6, stroke=INK, sw=2)  # electrolysis cell
        for x in range(32, 166, 14):
            a.b += rect(x, 228, 8, 48, "#3d5a4a", rx=2, stroke=INK, sw=1)
            a.e += lit(rect(x + 2, 232, 4, 8, c, extra=' opacity="0.7"'), "glow")
        for i, x in enumerate((24, 64, 104, 144)):
            a.b += rect(x, 300, 24, 70, "#33403a", rx=12, stroke=INK, sw=2)  # scrubber columns
            a.b += line(x + 12, 306, x + 12, 364, "#556b5f", sw=3)
        pipe(a, 14, 390, 178, 390, 6, "#3f6b55")
        for x in (60, 132):
            pump(a, x, 414, 22)
        light(a, 54, 144, 4, c)
        light(a, 138, 144, 4, c)
        strip_light(a, 76, h - 12, 40, 5, c)
    else:
        # atmosphere processing: algae vats, twin oxygen tanks, a fan bank, pumps and a
        # control room
        for i in range(3):
            y = 14 + i * 110
            a.b += rect(14, y, w - 28, 96, "#1f3a2a", rx=8, stroke=INK, sw=2)
            for k in range(6):
                a.b += circle(40 + k * 48, y + 48, 18, "#2a5a3a", stroke=INK, sw=1)
                a.e += circle(40 + k * 48, y + 48, 14, c, extra=' opacity="0.28"')
            light(a, w - 26, y + 14, 3, c)
        tank(a, 20, 360, 130, 64)
        tank(a, 170, 360, 130, 64)
        tank(a, 20, 440, 130, 64)
        tank(a, 170, 440, 130, 64)
        for i in range(5):
            pump(a, 36 + i * 62, 552, 24)
        pipe(a, 14, 590, w - 14, 590, 8, "#3f6b55")
        for x in (60, 160, 260):
            pipe(a, x, 504, x, 590, 6, "#3f6b55")
        a.b += rect(14, 610, w - 28, 150, DECK, rx=4, stroke=INK, sw=2)
        console(a, 30, 624, 120, 30, c)
        console(a, 170, 624, 120, 30, c, 3)
        seat(a, 90, 680, "up")
        seat(a, 230, 680, "up")
        a.b += rect(30, 716, 260, 30, HOUSING, rx=3, stroke=INK, sw=1.5)
        for i in range(8):
            screen(a, 36 + i * 32, 720, 26, 22, c, i)
    # displays and lit vats light the room; status lamps inside keep their own `glow`
    a.e = lit(a.e, "point", {"s": 2, "m": 2.5, "l": 3, "xl": 3.5}[size], 0.5)
    return w, h, a


# --- Cargo bay (passive: no emissives) -----------------------------------------

def cargo(size):
    tw, th = AUX[size]
    w, h = tw * T, th * T
    c = ACCENT["cargo"]
    a = Art()

    def tiedowns(x0, y0, x1, y1):
        for ty in range(int((y1 - y0) // T)):
            for tx in range(int((x1 - x0) // T)):
                for (dx, dy) in ((12, 12), (52, 12), (12, 52), (52, 52)):
                    a.b += circle(x0 + tx * T + dx, y0 + ty * T + dy, 3, "none", stroke=c, sw=1.5)

    if size == "s":
        # a cargo locker: cage frame and a strap net
        a.b += rect(4, 4, 56, 120, "none", stroke=STEEL_MID, sw=4)
        for k in range(1, 6):
            a.b += line(4, 4 + k * 20, 60, 4 + k * 20 - 20, "#8f7a52", sw=1.5, extra=' opacity="0.8"')
            a.b += line(4, 4 + k * 20 - 20, 60, 4 + k * 20, "#8f7a52", sw=1.5, extra=' opacity="0.8"')
        a.b += rect(4, 4, 56, 120, "none", stroke=INK, sw=1.5)
    elif size == "m":
        a.b += rect(3, 3, w - 6, h - 6, "none", stroke=HAZARD, sw=2.5, extra=' stroke-dasharray="10 6"')
        tiedowns(0, 0, w, h)
        a.b += hazard_band(8, h - 18, w - 16, 10, "cargo_m")
    elif size == "l":
        # a bay with a gantry crane rail across it and loading lanes
        a.b += rect(3, 3, w - 6, h - 6, "none", stroke=HAZARD, sw=2.5, extra=' stroke-dasharray="10 6"')
        tiedowns(0, 0, w, h)
        for x in (10, w - 18):
            a.b += rect(x, 6, 8, h - 12, STEEL_MID, stroke=INK, sw=1.5)
        a.b += rect(10, 196, w - 20, 18, STEEL, stroke=INK, sw=2)  # gantry beam
        a.b += rect(84, 190, 24, 30, HOUSING_HI, rx=2, stroke=INK, sw=1.5)  # trolley
        a.b += circle(96, 205, 5, INK)
        a.b += line(w / 2, 20, w / 2, h - 20, HAZARD, sw=2, extra=' opacity="0.6" stroke-dasharray="14 10"')
    else:
        # a hold with two crane rails, a cargo lift and marked sectors
        a.b += rect(3, 3, w - 6, h - 6, "none", stroke=HAZARD, sw=3, extra=' stroke-dasharray="12 8"')
        tiedowns(0, 0, w, h)
        for x in (10, w - 20):
            a.b += rect(x, 6, 10, h - 12, STEEL_MID, stroke=INK, sw=1.5)
        for y in (220, 540):
            a.b += rect(10, y, w - 20, 22, STEEL, stroke=INK, sw=2)
            a.b += rect(w / 2 - 18, y - 8, 36, 38, HOUSING_HI, rx=2, stroke=INK, sw=1.5)
            a.b += circle(w / 2, y + 11, 6, INK)
        a.b += rect(w / 2 - 70, 330, 140, 110, "#33393f", stroke=INK, sw=2)  # lift platform
        a.b += hazard_band(w / 2 - 70, 330, 140, 10, "lift_a")
        a.b += hazard_band(w / 2 - 70, 430, 140, 10, "lift_b")
        for k in range(1, 4):
            a.b += line(w / 2 - 64, 340 + k * 23, w / 2 + 64, 340 + k * 23, INK, sw=1.5)
        a.b += line(20, 384, w / 2 - 74, 384, HAZARD, sw=2.5, extra=' opacity="0.7"')
        a.b += line(w / 2 + 74, 384, w - 20, 384, HAZARD, sw=2.5, extra=' opacity="0.7"')
    a.b += rect(0, 0, w, h, "none", stroke=INK, sw=2)
    return w, h, a


# --- Airlock -------------------------------------------------------------------

def airlock(size):
    tw, th = AUX[size]
    w, h = tw * T, th * T
    a = Art()
    housing(a, w, h, inset=2)

    def grate(x, y, gw, gh):
        a.b += rect(x, y, gw, gh, "#20262e", stroke=INK, sw=1.5)
        for xx in range(int(x) + 4, int(x + gw), 6):
            a.b += line(xx, y + 2, xx, y + gh - 2, STEEL_DARK, sw=2)

    def door(x, y, dw, key, col):
        a.b += hazard_band(x, y, dw, 10, key)
        light(a, w - 12 if y < h / 2 else 12, y + 18 if y < h / 2 else y - 8, 2.5, col)

    def suit(x, y):
        a.b += rect(x, y, 14, 24, "#d8dde4", rx=6, stroke=INK, sw=1.2)
        a.b += circle(x + 7, y + 7, 5, GLASS, stroke=INK, sw=1)
        a.e += circle(x + 7, y + 7, 4, "#6aa8ff", extra=' opacity="0.35"')

    if size == "s":
        grate(8, 16, 48, 96)
        door(6, 4, 52, "al_s_a", RED)
        door(6, h - 14, 52, "al_s_b", GREEN)
    elif size == "m":
        grate(8, 16, 76, h - 32)
        door(6, 4, 80, "al_m_a", RED)
        door(6, h - 14, 80, "al_m_b", GREEN)
        for y in (24, 64, 104, 144):
            suit(100, y)
        a.b += rect(94, 18, 26, h - 36, "none", stroke=STEEL_MID, sw=2)
    elif size == "l":
        # two chambers with a middle door, suit racks and EVA lockers
        grate(8, 16, w - 16, 190)
        grate(8, 230, w - 16, 186)
        door(6, 4, w - 12, "al_l_a", RED)
        a.b += rect(6, 210, w - 12, 14, STEEL_MID, stroke=INK, sw=1.5)
        a.b += hazard_band(30, 212, w - 60, 10, "al_l_mid")
        door(6, h - 14, w - 12, "al_l_b", GREEN)
        for y in range(240, 400, 34):
            suit(12, y)
            suit(w - 26, y)
        for y in (40, 100, 160):
            a.b += rect(14, y, 28, 40, "#566a7a", rx=2, stroke=INK, sw=1.5)
            a.b += line(28, y + 2, 28, y + 38, INK, sw=1)
        console(a, w - 50, 40, 36, 60, ACCENT["helm"])
    else:
        # a cargo airlock: wide outer doors, a staging floor, a suit room on one side
        door(6, 4, w - 12, "al_xl_a", RED)
        grate(8, 16, w - 16, 420)
        for k in range(1, 6):
            a.b += line(14, 16 + k * 70, w - 14, 16 + k * 70, STEEL_MID, sw=3)
        a.b += rect(6, 440, w - 12, 14, STEEL_MID, stroke=INK, sw=1.5)
        a.b += hazard_band(20, 442, w - 40, 10, "al_xl_mid")
        a.b += rect(200, 460, 112, 256, HOUSING, stroke=INK, sw=2)  # suit room
        for y in range(472, 700, 34):
            suit(212, y)
            suit(284, y)
        console(a, 20, 480, 80, 26, ACCENT["helm"])
        seat(a, 60, 524, "up")
        door(6, h - 14, w - 12, "al_xl_b", GREEN)
    return w, h, a


# --- Escape pod ------------------------------------------------------------------

POD_SEATS = {"s": (2, 2), "m": (2, 4), "l": (4, 5), "xl": (6, 8)}  # columns, rows: 4, 8, 20, 48 seats


def escape_pod(size):
    """A lifeboat in its bay, hatch toward +y (the top of the canvas) at rotation 0, where it
    launches through the hull: an orange-banded capsule with rows of crash couches facing
    the hatch and a status panel on its own battery. The bay is walkable: its own floor shows around it."""
    tw, th = AUX[size]
    w, h = tw * T, th * T
    c = ACCENT["escape_pod"]
    a = Art()
    inset = 5
    rx = min(w, h) * 0.22
    # the capsule shell
    a.b += rect(inset, inset + 8, w - 2 * inset, h - 2 * inset - 8, "#46505e", rx=rx, stroke=INK, sw=2)
    a.b += bevel(inset + 3, inset + 11, w - 2 * inset - 6, h - 2 * inset - 14, STEEL_HI, INK, sw=1.2)
    # orange flotation band around the capsule
    a.b += rect(inset + 2, h / 2 - 5, w - 2 * inset - 4, 10, c, stroke=INK, sw=1.2)
    # the hatch: a hazard-striped ring at the +y end
    a.b += hazard_band(inset + 8, 2, w - 2 * inset - 16, 10, f"pod_{size}_hatch")
    a.b += rect(w / 2 - 12, 4, 24, 6, STEEL_DARK, stroke=INK, sw=1.2)
    # crash couches, facing the hatch
    cols, rws = POD_SEATS[size]
    x0, x1 = inset + 10, w - inset - 10
    y0, y1 = inset + 26, h - inset - 16
    s = 0.7 if size == "s" else 0.9
    for j in range(rws):
        for i in range(cols):
            cx = x0 + (x1 - x0) * (i + 0.5) / cols
            cy = y0 + (y1 - y0) * (j + 0.5) / rws
            if abs(cy - h / 2) < 9:
                cy += 9 if cy >= h / 2 else -9
            seat(a, cx, cy, "up", s)
    # status panel and launch lamp, on the pod's battery
    screen(a, w - inset - 22, h - inset - 18, 14, 10, c, 3)
    light(a, inset + 12, 18, 3, GREEN)
    a.b += rect(0, 0, w, h, "none", stroke=INK, sw=2)
    return w, h, a


# --- Docking port ----------------------------------------------------------------

def docking_port(size, state):
    tw, td = PORT[size]
    w, h = tw * T, td * T
    a = Art()
    # Frame tiles at both ends of the width (hull), opening between, door on the top (outer) edge.
    for side in (0, w - T):
        for ty in range(td):
            a.b += rect(side, ty * T, T, T, "#3d4552")
            a.b += plate(side + 5, ty * T + 5, T - 10, T - 10, "#5b6675", STEEL_HI, INK, rx=3)
        a.b += hazard_band(side + 10, 10, T - 20, 8, f"dp_{size}_{state}_{side}")
    ow = w - 2 * T
    for ty in range(td):
        cy = ty * T + 40
        a.b += path(f"M{f(T + ow/2 - 14)},{f(cy)} L{f(T + ow/2)},{f(cy - 12)} L{f(T + ow/2 + 14)},{f(cy)}",
                    stroke=HAZARD, sw=4, extra=' opacity="0.85"')
    if state == "sealed":
        a.b += rect(T - 4, -1, ow + 8, 12, "#7a8594", stroke=INK, sw=2)
        a.b += hazard_band(T + 2, 2, ow - 4, 6, f"dpd_{size}")
        a.b += line(T + ow / 2, -1, T + ow / 2, 11, INK, sw=2)
        col = RED
    else:
        a.b += rect(T - 4, -1, 10, 12, "#7a8594", stroke=INK, sw=2)
        a.b += rect(T + ow - 6, -1, 10, 12, "#7a8594", stroke=INK, sw=2)
        col = GREEN
    ly = h - 14 if td > 1 else 42
    light(a, T / 2, ly, 4, col)
    light(a, w - T / 2, ly, 4, col)
    a.b += rect(0, 0, w, h, "none", stroke=INK, sw=2)
    return w, h, a


def docking_collar(size):
    """The latch collar outside a port's outer edge: W tiles wide, half a tile deep."""
    tw, _ = PORT[size]
    w, h = tw * T, T / 2
    a = Art()
    a.b += rect(T * 0.5, 4, w - T, h - 4, STEEL_MID, rx=3, stroke=INK, sw=2)
    a.b += bevel(T * 0.5 + 1, 5, w - T - 2, h - 6, STEEL_HI, INK)
    a.b += rect(T, 0, w - 2 * T, 8, HOUSING, rx=2, stroke=INK, sw=1.5)
    n = tw * 2
    for i in range(1, n):
        x = T * 0.5 + (w - T) * i / n
        a.b += rect(x - 3, h - 10, 6, 8, "#7a8594", rx=1, stroke=INK, sw=1)
    light(a, T * 0.5 + 8, h / 2 + 2, 3, HAZARD)
    light(a, w - T * 0.5 - 8, h / 2 + 2, 3, HAZARD)
    return w, h, a


# --- Tanks (passive: no emissives) ---------------------------------------------

def storage_tank(component, size):
    """A tank for one fluid: pressure vessels in a cradle, banded in the fluid's color, with a
    gauge and a manifold to the pipe connection. Bigger tanks are more and larger vessels."""
    tw, th = AUX[size]
    w, h = tw * T, th * T
    label, band = FLUIDS[component]
    a = Art()
    a.b = rect(3, 3, w - 6, h - 6, STEEL_DARK, rx=6, stroke=INK, sw=2)  # cradle

    def vessel(x, y, vw, vh):
        r = min(vw, vh) / 2
        a.b += rect(x, y, vw, vh, STEEL_MID, rx=r, stroke=INK, sw=2)
        a.b += rect(x + vw * 0.16, y + r * 0.45, vw * 0.16, vh - r * 0.9, STEEL_HI, rx=vw * 0.08, extra=' opacity="0.7"')
        for by in (y + vh * 0.3, y + vh * 0.7):
            a.b += rect(x, by - 3, vw, 6, band, stroke=INK, sw=1)
        a.b += circle(x + vw / 2, y + 6 + r * 0.2, max(2.5, r * 0.18), HOUSING_HI, stroke=INK, sw=1)  # valve

    cols = {"s": 1, "m": 2, "l": 2, "xl": 4}[size]
    rows_ = {"s": 1, "m": 1, "l": 2, "xl": 2}[size]
    pad, gap = 8, 6
    vw = (w - 2 * pad - (cols - 1) * gap) / cols
    vh = (h - 2 * pad - 22 - (rows_ - 1) * gap) / rows_
    for i in range(cols):
        for j in range(rows_):
            vessel(pad + i * (vw + gap), pad + 22 + j * (vh + gap), vw, vh)
    # manifold along the near end, to the pipe connection in the middle of the slot
    pipe(a, pad + vw / 2, 16, w - pad - vw / 2, 16, 5, PIPE)
    a.b += rect(w / 2 - 6, 8, 12, 14, PIPE_LO, rx=2, stroke=INK, sw=1.5)
    # gauge: a dial with the fluid's color on its face
    gx, gy = w - 14, 12
    a.b += circle(gx, gy, 6, "#e6e2d4", stroke=INK, sw=1.2)
    a.b += path(f"M{f(gx - 4)},{f(gy + 2)} A5,5 0 0 1 {f(gx + 4)},{f(gy + 2)}", stroke=band, sw=2)
    a.b += line(gx, gy, gx + 3, gy - 3, INK, sw=1.2)
    a.b += rect(0, 0, w, h, "none", stroke=INK, sw=2)
    return w, h, a, label


def wreck(w, h, seed, heavy):
    """Damage drawn into a component's own art: scorch, cracks, buckled plates and loose
    debris; `heavy` adds blackening, a burned-through hole and torn plating."""
    rnd = random.Random(seed)
    s = ""
    n = min(12, max(1, int(w * h / (T * T))))  # capped: the packer limits element count
    for _ in range(n * (5 if heavy else 2)):  # scorch
        x, y = rnd.uniform(6, w - 6), rnd.uniform(6, h - 6)
        r = rnd.uniform(8, 18) * (1.4 if heavy else 1)
        s += circle(x, y, r, "#0b0d10", extra=f' opacity="{f(rnd.uniform(0.25, 0.45))}"')
    for _ in range(n * (4 if heavy else 2)):  # cracks
        x, y = rnd.uniform(6, w - 6), rnd.uniform(6, h - 6)
        d = f"M{f(x)},{f(y)}"
        for _ in range(4):
            x = min(w - 2, max(2, x + rnd.uniform(-14, 14)))
            y = min(h - 2, max(2, y + rnd.uniform(-14, 14)))
            d += f" L{f(x)},{f(y)}"
        s += path(d, stroke=INK, sw=2 if heavy else 1.4, extra=' opacity="0.9"')
    for _ in range(n * (2 if heavy else 1)):  # buckled plate: a skewed shard with a bright torn edge
        x, y = rnd.uniform(10, w - 10), rnd.uniform(10, h - 10)
        k = rnd.uniform(5, 9)
        pts = [(x - k, y - k * 0.4), (x + k * 0.8, y - k), (x + k, y + k * 0.5), (x - k * 0.3, y + k)]
        s += poly(pts, STEEL_DARK, stroke=INK, sw=1.2) + line(pts[0][0], pts[0][1], pts[1][0], pts[1][1], STEEL_HI, sw=1)
    if heavy:
        s += rect(0, 0, w, h, "#05070a", extra=' opacity="0.35"')
        for _ in range(max(1, int(n ** 0.5))):  # burned through to the frame below
            x, y = rnd.uniform(16, w - 16), rnd.uniform(16, h - 16)
            pts = []
            for i in range(10):
                ang = 2 * math.pi * i / 10
                r = rnd.uniform(6, 12) * min(2.5, max(1.0, n ** 0.3))
                pts.append((x + math.cos(ang) * r, y + math.sin(ang) * r))
            s += poly(pts, "#03050a", stroke=INK, sw=1.5)
            for (px, py) in pts[::3]:
                s += line(px, py, x + (px - x) * 1.35, y + (py - y) * 1.35, STEEL_HI, sw=1.2)
    return s


def component_states(p, w, h, a, title, powered, clip=None):
    """A component's art, its unpowered variant when it is powered, and its damaged and
    destroyed states: `_damaged` (glow dimmed; `_damaged_unpowered` without it) and
    `_destroyed` (wrecked and dark). With `clip` (an SVG path), the damage stays inside it, for
    art that does not fill its canvas."""
    write(p, w, h, a, title, unpowered=unp(p) if powered else None)
    seed = sum(map(ord, p))
    cid = uid(p) + "_clip"

    def damage(heavy):
        s = wreck(w, h, seed, heavy)
        if clip is None:
            return s
        return (f'    <clipPath id="{cid}{int(heavy)}"><path d="{clip}"/></clipPath>\n'
                f'    <g clip-path="url(#{cid}{int(heavy)})">\n{s}    </g>\n')
    d = Art()
    d.defs, d.m = a.defs, a.m
    d.b = a.b + damage(False)
    d.e = f'    <g opacity="0.55">\n{a.e}    </g>\n' if a.e else ""
    dp = p.replace(".svg", "_damaged.svg")
    write(dp, w, h, d, title + ", damaged", unpowered=unp(dp) if powered else None)
    x = Art()
    x.defs, x.m = a.defs, a.m
    x.b = a.b + damage(True)
    write(p.replace(".svg", "_destroyed.svg"), w, h, x, title + ", destroyed")


def components():
    for size in SIZES:
        for name, fn in (("reactor", reactor), ("shield_emitter", shield_emitter), ("turret_mount", turret_mount)):
            w, h, a = fn(size)
            p = f"components/{name}_{size}.svg"
            component_states(p, w, h, a, f"{name} {size.upper()}: {w//T}x{h//T} primary mount", True)
        for name in ("helm", "weapons"):
            w, h, a = crew_room(name, size)
            p = f"components/{name}_{size}.svg"
            component_states(p, w, h, a, f"{name} {size.upper()}: {w//T}x{h//T} auxiliary slot", True)
        for name, fn in (("life_support", life_support), ("airlock", airlock), ("escape_pod", escape_pod)):
            w, h, a = fn(size)
            p = f"components/{name}_{size}.svg"
            component_states(p, w, h, a, f"{name} {size.upper()}: {w//T}x{h//T} auxiliary slot", True)
        from engine_svgs import engine
        w, h, a, sil = engine(size)
        component_states(f"components/engine_{size}.svg", w, h, a,
                         f"engine {size.upper()}: {w//T}x{h//T} auxiliary slot outside the hull, nozzle toward +y "
                         f"at rotation 0, mounting flange at the bottom", True, clip=sil)
        from engine_svgs import MODELS
        for model, style in MODELS.items():
            if style == "bell":
                continue
            w, h, a, sil = engine(size, style, key=f"{model}_c")
            component_states(f"components/{model}_{size}.svg", w, h, a,
                             f"{model} {size.upper()}: the {style} engine, {w//T}x{h//T} auxiliary slot outside the hull, "
                             f"nozzle toward +y at rotation 0, mounting flange at the bottom", True, clip=sil)
        w, h, a = cargo(size)
        component_states(f"components/cargo_{size}.svg", w, h, a,
                         f"cargo {size.upper()}: {w//T}x{h//T} auxiliary slot, unpowered", False)
        for comp in FLUIDS:
            w, h, a, label = storage_tank(comp, size)
            component_states(f"components/{comp}_{size}.svg", w, h, a,
                             f"{comp} {size.upper()}: {w//T}x{h//T} auxiliary slot holding {label}, unpowered", False)
    for size in PORT:
        for state in ("sealed", "open"):
            w, h, a = docking_port(size, state)
            p = f"components/docking_port_{size}" + ("_open" if state == "open" else "") + ".svg"
            component_states(p, w, h, a,
                             f"docking_port {size.upper()}: {w//T}x{h//T} port mount, door {state}, facing +y at rotation 0",
                             True)
        w, h, a = docking_collar(size)
        p = f"components/docking_collar_{size}.svg"
        write(p, w, h, a, f"docking collar {size.upper()}: sits half a tile outside the port's outer edge",
              unpowered=unp(p))


# ---------------------------------------------------------------------------
# Effects: overlays that show tile state
# ---------------------------------------------------------------------------

def flame(cx, base, height, width, colors):
    s = ""
    for k, col in enumerate(colors):
        sc = 1 - k * 0.28
        hh, ww = height * sc, width * sc
        s += path(f"M{f(cx - ww/2)},{f(base)} C{f(cx - ww/2)},{f(base - hh*0.5)} {f(cx - ww*0.1)},{f(base - hh*0.6)} "
                  f"{f(cx)},{f(base - hh)} C{f(cx + ww*0.15)},{f(base - hh*0.55)} {f(cx + ww/2)},{f(base - hh*0.45)} "
                  f"{f(cx + ww/2)},{f(base)} Z", fill=col)
    return s


def effects():
    cols = ["#ff5a1f", "#ffa62b", "#ffe58a"]
    layouts = {
        "small": [(32, 44, 22, 18)],
        "medium": [(22, 46, 26, 18), (42, 50, 30, 22), (34, 30, 18, 12)],
        "large": [(16, 58, 34, 22), (34, 60, 44, 28), (52, 56, 32, 20), (24, 30, 22, 16), (44, 28, 24, 16)],
    }
    for name, flames in layouts.items():
        a = Art()
        a.e = circle(32, 36, 26 if name != "small" else 16, "#ff5a1f", extra=' opacity="0.18"')
        for (cx, base, hh, ww) in flames:
            a.e += flame(cx, base, hh, ww, cols)
        write(f"effects/fire_{name}.svg", T, T, a, f"fire_{name}: fire overlay (Tile.FireSize.{name.upper()})")

    rnd = random.Random(4)
    a = Art()
    for i in range(14):
        ang = rnd.uniform(0, 2 * math.pi)
        r = rnd.uniform(4, 24)
        a.b += circle(32 + math.cos(ang) * r, 32 + math.sin(ang) * r, rnd.uniform(6, 13), "#0b0d10",
                      extra=f' opacity="{f(rnd.uniform(0.25, 0.45))}"')
    write("effects/scorch.svg", T, T, a, "scorch: burn mark left after a fire")

    pts = []
    rnd = random.Random(11)
    for i in range(16):
        ang = 2 * math.pi * i / 16
        r = rnd.uniform(16, 26)
        pts.append((32 + math.cos(ang) * r, 32 + math.sin(ang) * r))
    a = Art()
    a.b = poly(pts, STEEL_DARK, stroke=INK, sw=2)
    inner = [(32 + (x - 32) * 0.72, 32 + (y - 32) * 0.72) for x, y in pts]
    a.b += poly(inner, "#03050a", stroke=INK, sw=1.5)
    for (sx, sy) in ((26, 28), (38, 36), (31, 40)):
        a.b += circle(sx, sy, 0.9, "#cfe3ff")
    for i in range(0, 16, 3):
        x, y = pts[i]
        a.b += line(x, y, 32 + (x - 32) * 1.2, 32 + (y - 32) * 1.2, STEEL_HI, sw=1.5)
    write("effects/breach.svg", T, T, a, "breach: hull tile at integrity 0, open to space")

    for name, n, sw in (("light", 3, 1.2), ("heavy", 7, 2)):
        rnd = random.Random(7 if name == "light" else 9)
        a = Art()
        for i in range(n):
            x, y = rnd.uniform(10, 54), rnd.uniform(10, 54)
            d = f"M{f(x)},{f(y)}"
            for _ in range(4):
                x += rnd.uniform(-10, 10)
                y += rnd.uniform(-10, 10)
                d += f" L{f(min(62, max(2, x)))},{f(min(62, max(2, y)))}"
            a.b += path(d, stroke=INK, sw=sw, extra=' opacity="0.85"')
        write(f"effects/damage_{name}.svg", T, T, a,
              f"damage_{name}: cracks for a tile or system below {'half' if name == 'light' else 'a quarter'} integrity")

    a = Art()
    a.b = rect(3, 3, 58, 58, "#8a6d1f", rx=4, stroke=INK, sw=2)
    a.b += hazard_band(7, 7, 50, 8, "seal_top")
    a.b += hazard_band(7, 49, 50, 8, "seal_bottom")
    a.b += rect(7, 17, 50, 30, "#a8862a", stroke=INK, sw=1.5)
    for xx in (12, 24, 40, 52):
        a.b += circle(xx, 32, 2, "#d9c27a", stroke=INK, sw=1)
    write("effects/bulkhead_seal.svg", T, T, a, "bulkhead_seal: emergency seal over a tile (Tile.bulkheadSealed)")
    hull_patch()
    short_circuit()
    propellant_spill()


def hull_patch():
    # A plate bolted over a leak in the field: off-square, rivets, a weld bead and a strip of
    # hazard tape to say it is temporary
    a = Art()
    plate = [(12, 15), (52, 10), (55, 50), (14, 54)]
    a.b = poly(plate, STEEL_MID, stroke=INK, sw=2)
    a.b += poly([(16, 19), (49, 15), (51, 46), (18, 49)], "none", stroke=STEEL_HI, sw=1)
    for (rx, ry) in ((16, 18), (49, 14), (51, 47), (17, 50), (33, 15), (34, 51)):
        a.b += circle(rx, ry, 1.6, STEEL_HI, stroke=INK, sw=0.8)
    a.b += path("M20,30 L28,27 L36,31 L44,28", stroke="#d9e2ee", sw=1.4, extra=' opacity="0.8"')
    a.b += poly([(18, 37), (50, 33), (51, 39), (19, 43)], HAZARD, stroke=INK, sw=1)
    write("effects/hull_patch.svg", T, T, a, "hull_patch: a field patch over a leaking tile (Tile.isPatched)")


def short_circuit():
    # A faulted part: a jagged spark between two scorched contacts, on a dark burn mark, so a
    # shorted system or conduit reads as dead until the crew reset it
    a = Art()
    a.b = poly([(14, 22), (26, 12), (44, 14), (54, 28), (50, 46), (34, 54), (16, 48), (10, 34)],
               "#1b1f26", extra=' opacity="0.7"')
    a.b += circle(18, 40, 4, "#5a4632", stroke=INK, sw=1)
    a.b += circle(46, 24, 4, "#5a4632", stroke=INK, sw=1)
    a.b += poly([(20, 38), (30, 34), (27, 30), (38, 27), (35, 23), (45, 25), (36, 31), (39, 34), (28, 38),
                 (31, 41)], HAZARD, stroke=INK, sw=1.2)
    a.b += path("M21,38 L30,34 L27,30 L38,27 L35,23 L44,25", stroke="#fff3b0", sw=1.2)
    write("effects/short_circuit.svg", T, T, a, "short_circuit: a shorted system or conduit (DamageStates)")


def propellant_spill():
    # Propellant pooled on the deck by a burst tank: a wet, dark sheen that a fire can burn
    a = Art()
    a.b = poly([(10, 30), (16, 16), (32, 12), (48, 18), (55, 32), (50, 48), (34, 54), (18, 50)],
               "#2a3a2e", extra=' opacity="0.75"')
    a.b += poly([(18, 30), (24, 22), (36, 20), (44, 28), (40, 40), (28, 44)], "#3e5a44", extra=' opacity="0.8"')
    a.b += path("M24,28 Q32,24 38,28", stroke="#9fc4a6", sw=1.2, extra=' opacity="0.7"')
    write("effects/propellant_spill.svg", T, T, a, "propellant_spill: fuel a burst tank left on the deck (DamageStates)")


# ---------------------------------------------------------------------------
# Decor and interactive objects (sub-tile, quarter-tile grid of 16 units)
# ---------------------------------------------------------------------------

def decor():
    def out(name, w, h, a, title, powered=False):
        p = f"decor/{name}.svg"
        write(p, w, h, a, title, unpowered=unp(p) if powered else None)

    a = Art()
    a.b = rect(2, 2, 28, 28, HOUSING, rx=3, stroke=INK, sw=2)
    for yy in range(8, 27, 5):
        a.b += line(7, yy, 25, yy, INK, sw=2.5)
    a.b += bevel(3, 3, 26, 26, HOUSING_HI, INK, sw=1)
    out("vent_grate", 32, 32, a, "vent_grate: floor vent, 2x2 sub-tiles")

    a = Art()
    a.b = rect(2, 2, 28, 28, STEEL_MID, rx=2, stroke=INK, sw=2)
    a.b += rect(7, 7, 18, 18, STEEL_DARK, rx=2, stroke=INK, sw=1.5)
    a.b += rect(13, 14, 6, 4, HAZARD, stroke=INK, sw=1)
    out("floor_hatch", 32, 32, a, "floor_hatch: maintenance hatch, 2x2 sub-tiles")

    a = Art()
    a.b = rect(0, 2, 64, 5, "#4f7a8c", stroke=INK, sw=1.5)
    a.b += rect(0, 9, 64, 5, "#8c6a4f", stroke=INK, sw=1.5)
    for xx in (10, 54):
        a.b += rect(xx - 2, 0, 4, 16, STEEL_DARK, rx=1, stroke=INK, sw=1)
    a.b += line(0, 3.5, 64, 3.5, "#9fc3d1", sw=1, extra=' opacity="0.6"')
    out("pipe_run", 64, 16, a, "pipe_run: water and coolant pipes along a wall, tiles end to end")

    a = Art()
    a.b = rect(0, 2, 64, 5, "#4f7a8c", stroke=INK, sw=1.5)
    a.b += rect(0, 9, 64, 5, "#8c6a4f", stroke=INK, sw=1.5)
    a.b += circle(32, 4.5, 6, "#b33b3b", stroke=INK, sw=1.5)
    a.b += line(26, 4.5, 38, 4.5, "#e0e0e0", sw=1.5)
    out("pipe_valve", 64, 16, a, "pipe_valve: pipe run with a hand valve")

    a = Art()
    a.b = rect(0, 3, 64, 10, HOUSING, stroke=INK, sw=1.5)
    for k, col in enumerate(("#d24b4b", POWER, "#4b8bd2", "#e0e0e0")):
        a.b += line(0, 5 + k * 2, 64, 5 + k * 2, col, sw=1.4)
    for xx in (16, 48):
        a.b += rect(xx - 1.5, 2, 3, 12, STEEL_MID, stroke=INK, sw=0.8)
    out("cable_tray", 64, 16, a, "cable_tray: cables along a wall")

    a = Art()
    a.b = rect(2, 2, 28, 7, HOUSING, rx=2, stroke=INK, sw=1.5)
    a.b += rect(5, 4, 22, 3, LENS)
    # broad, diffuse light: a half-circle of light washing the room away from the wall
    a.e = lit(rect(5, 4, 22, 3, "#fff7d6")
              + path("M3,9 L29,9 L32,16 L0,16 Z", fill="#fff7d6", extra=' opacity="0.2"'),
              "cone", reach=6, intensity=0.7, direction=-90, cone=90)
    out("wall_light", 32, 16, a, "wall_light: light strip on a wall", powered=True)

    a = Art()
    a.b = hazard_band(0, 4, 64, 8, "hazstripe")
    out("hazard_stripe", 64, 16, a, "hazard_stripe: floor marking")

    a = Art()
    a.b = poly([(16, 4), (28, 18), (21, 18), (21, 28), (11, 28), (11, 18), (4, 18)], HAZARD, stroke=INK,
               sw=1.5, extra=' opacity="0.85"')
    out("floor_arrow", 32, 32, a, "floor_arrow: direction marking, points +y")

    for name, sz in (("crate_small", 32), ("crate_large", 48)):
        a = Art()
        a.b = rect(2, 2, sz - 4, sz - 4, "#7a5a33", rx=2, stroke=INK, sw=2)
        a.b += rect(6, 6, sz - 12, sz - 12, "#8f6b3e", stroke=INK, sw=1.2)
        a.b += line(6, 6, sz - 6, sz - 6, "#6a4d2b", sw=2)
        a.b += line(sz - 6, 6, 6, sz - 6, "#6a4d2b", sw=2)
        a.b += bevel(3, 3, sz - 6, sz - 6, "#b48a55", "#4d371e", sw=1)
        a.m = collider_rect(2, 2, sz - 4, sz - 4)
        out(name, sz, sz, a, f"{name}: loose crate, {sz//16}x{sz//16} sub-tiles")

    a = Art()
    a.b = circle(16, 16, 13, "#3d6b8f", stroke=INK, sw=2)
    a.b += circle(16, 16, 10, "none", stroke="#2b4d68", sw=1.5)
    a.b += circle(11, 12, 2.5, "#c9d6e0", stroke=INK, sw=1)
    a.m = collider_circle(16, 16, 13)
    out("barrel", 32, 32, a, "barrel: drum seen from above")

    a = Art()
    a.b = rect(1, 1, 30, 14, "#566a7a", rx=1, stroke=INK, sw=1.5)
    a.b += line(16, 2, 16, 14, INK, sw=1.2)
    a.b += rect(12, 9, 2, 4, "#c9d6e0") + rect(18, 9, 2, 4, "#c9d6e0")
    a.m = collider_rect(1, 1, 30, 14)
    out("locker", 32, 16, a, "locker: crew locker against a wall")

    a = Art()
    a.b = rect(2, 2, 28, 60, "#3a404a", rx=3, stroke=INK, sw=2)
    a.b += rect(5, 5, 22, 54, "#5a6b80", rx=3, stroke=INK, sw=1.2)
    a.b += rect(7, 7, 18, 10, "#d8dde4", rx=3, stroke=INK, sw=1)
    a.b += rect(5, 30, 22, 29, "#2f5a8a", rx=2, stroke=INK, sw=1)
    a.b += line(5, 33, 27, 33, "#4a78ad", sw=1.5)
    a.m = collider_rect(2, 2, 28, 60) + interact_point(16, 40)
    out("bunk", 32, 64, a, "bunk: crew bed, pillow toward +y")

    a = Art()
    a.b = rect(3, 3, 26, 26, "#6b5a48", rx=3, stroke=INK, sw=2)
    a.b += bevel(4, 4, 24, 24, "#8a755e", "#4a3e31", sw=1)
    a.b += circle(10, 11, 3, "#d8dde4", stroke=INK, sw=0.8)
    a.m = collider_rect(3, 3, 26, 26)
    out("table", 32, 32, a, "table: small table")

    a = Art()
    a.b = rect(2, 2, 60, 28, "#6b5a48", rx=3, stroke=INK, sw=2)
    a.b += bevel(3, 3, 58, 26, "#8a755e", "#4a3e31", sw=1)
    for xx in (14, 32, 50):
        a.b += circle(xx, 16, 3.5, "#d8dde4", stroke=INK, sw=0.8)
    a.m = collider_rect(2, 2, 60, 28)
    out("mess_table", 64, 32, a, "mess_table: long table, 4x2 sub-tiles")

    a = Art()
    a.b = rect(3, 4, 10, 10, SEAT, rx=3, stroke=INK, sw=1.5)
    a.b += rect(3, 1, 10, 4, SEAT_DARK, rx=1.5, stroke=INK, sw=1)
    a.m = collider_rect(3, 1, 10, 13)
    out("chair", 16, 16, a, "chair: seat facing -y (back toward +y)")

    a = Art()
    a.b = circle(8, 8, 6.5, "#4a3e31", stroke=INK, sw=1.5)
    for i in range(5):
        ang = 2 * math.pi * i / 5
        a.b += path(f"M8,8 Q{f(8+math.cos(ang)*4)},{f(8+math.sin(ang)*1)} {f(8+math.cos(ang)*7)},{f(8+math.sin(ang)*7)}",
                    stroke="#5ec46a", sw=2.5)
    a.b += circle(8, 8, 1.8, "#8fe39a")
    a.m = collider_circle(8, 8, 6.5)
    out("plant", 16, 16, a, "plant: potted plant")


def interactive():
    def out(name, w, h, a, title, powered=False):
        p = f"interactive/{name}.svg"
        write(p, w, h, a, title, unpowered=unp(p) if powered else None)

    a = Art()
    a.b = rect(4, 0, 8, 3, STEEL_DARK, stroke=INK, sw=1)
    a.b += circle(8, 9, 5, "#d33a3a", stroke=INK, sw=1.5)
    a.b += circle(8, 9, 2, "#2b2f36")
    a.b += line(8, 4, 12, 2, "#2b2f36", sw=1.5)
    a.m = collider_circle(8, 9, 5) + interact_point(8, 14)
    out("fire_extinguisher", 16, 16, a, "fire_extinguisher: crew pick it up to fight fires")

    a = Art()
    a.b = rect(2, 1, 12, 9, HOUSING, rx=1.5, stroke=INK, sw=1.2)
    a.b += rect(4, 3, 3.5, 5, LENS) + rect(8.5, 3, 3.5, 5, LENS)
    a.e = rect(4, 3, 3.5, 5, GREEN) + rect(8.5, 3, 3.5, 5, RED)
    a.m = interact_point(8, 14)
    out("bulkhead_panel", 16, 16, a, "bulkhead_panel: opens and seals a nearby bulkhead door", True)

    a = Art()
    a.b = rect(2, 1, 12, 10, "#3a414c", rx=1.5, stroke=INK, sw=1.2)
    a.b += rect(6, 3, 4, 6, SEAT_DARK, stroke=INK, sw=0.8)
    a.b += rect(6.5, 3.5, 3, 2.5, LENS)
    a.e = rect(6.5, 3.5, 3, 2.5, POWER)
    a.m = interact_point(8, 14)
    out("breaker", 16, 16, a, "breaker: switches a system on or off", True)

    a = Art()
    a.b = rect(2, 1, 28, 10, HOUSING, rx=2, stroke=INK, sw=1.5)
    screen(a, 5, 2.5, 22, 7, ACCENT["helm"])
    a.m = interact_point(16, 14)
    out("terminal", 32, 16, a, "terminal: wall terminal", True)

    a = Art()
    a.b = rect(1, 1, 30, 14, "#8a6d1f", rx=1, stroke=INK, sw=1.5)
    a.b += hazard_band(3, 3, 26, 3, "suitlocker")
    a.b += rect(6, 8, 20, 5, "#d33a3a", rx=1, stroke=INK, sw=1)
    a.b += rect(14, 9, 4, 3, "#fff")
    a.m = collider_rect(1, 1, 30, 14) + interact_point(16, 15)
    out("emergency_locker", 32, 16, a, "emergency_locker: oxygen and vacuum suits")

    a = Art()
    a.b = rect(2, 3, 12, 10, "#c46a1f", rx=1.5, stroke=INK, sw=1.2)
    a.b += rect(6, 1.5, 4, 2.5, STEEL_DARK, stroke=INK, sw=0.8)
    a.b += rect(6.5, 6, 3, 4, "#fff") + rect(5, 7, 6, 2, "#fff")
    a.m = collider_rect(2, 2, 12, 11) + interact_point(8, 8)
    out("repair_kit", 16, 16, a, "repair_kit: patches for repairs and breaches")

    a = Art()
    a.b = rect(2, 2, 28, 60, "#c9d2dc", rx=3, stroke=INK, sw=2)
    a.b += rect(5, 5, 22, 54, "#e8edf2", rx=3, stroke=INK, sw=1)
    a.b += rect(8, 7, 16, 9, "#ffffff", rx=3, stroke=INK, sw=1)
    a.b += rect(10, 46, 12, 8, GLASS, rx=1)
    a.e = rect(10, 46, 12, 8, "#5ee08a", rx=1, extra=' opacity="0.8"')
    a.e += line(13, 50, 19, 50, "#fff", sw=1.5) + line(16, 47, 16, 53, "#fff", sw=1.5)
    a.m = collider_rect(2, 2, 28, 60) + interact_point(16, 34)
    out("med_bed", 32, 64, a, "med_bed: medical bed, head toward +y", True)

    def crate(x, y, w, h, col):
        q = rect(x, y, w, h, col, rx=1.5, stroke=INK, sw=1.5)
        q += line(x + 3, y + 3, x + w - 3, y + h - 3, INK, sw=1, extra=' opacity="0.4"')
        q += line(x + w - 3, y + 3, x + 3, y + h - 3, INK, sw=1, extra=' opacity="0.4"')
        return q
    stacks = {
        1: [(8, 30, 26, 26)],
        2: [(6, 30, 24, 26), (32, 32, 26, 24), (14, 8, 28, 20)],
        3: [(4, 4, 27, 27), (33, 4, 27, 27), (4, 33, 27, 27), (33, 33, 27, 27)],
    }
    for level, crates in stacks.items():
        a = Art()
        for i, (x, y, w, h) in enumerate(crates):
            a.b += crate(x, y, w, h, ("#8f6b3e", "#7a5a33", "#a07a48", "#86643a")[i % 4])
        if level == 3:
            a.b += rect(4, 30, 56, 4, HAZARD, extra=' opacity="0.9"') + rect(30, 4, 4, 56, HAZARD, extra=' opacity="0.9"')
        a.m = interact_point(32, 32)
        out(f"cargo_stack_{level}", T, T, a,
            f"cargo_stack_{level}: one cargo stack on a bay tile, {['', 'a third', 'two thirds', 'full'][level]}")


# ---------------------------------------------------------------------------
# Exterior: ship and station hulls built on the chassis grid, turrets
# ---------------------------------------------------------------------------

def load_toml(name):
    with open(os.path.join(ROOT, "assets", "data", name), "rb") as fh:
        return tomllib.load(fh)


LETTERS = {"R": "reactor", "S": "shield_emitter", "T": "turret_mount", "L": "life_support", "H": "helm",
           "W": "weapons", "C": "cargo", "A": "airlock", "D": "docking_port", "E": "engine", "P": "tank",
           "O": "oxygen_tank", "N": "nitrogen_tank", "B": "escape_pod",
           # The freighter's freight drives and the gunship's drives are engines on the outside
           "Q": "engine", "G": "engine"}
KIND = {"reactor": "P", "shield_emitter": "P", "turret_mount": "P", "life_support": "A", "helm": "A",
        "weapons": "A", "cargo": "A", "airlock": "A", "docking_port": "D", "engine": "A", "tank": "A",
        "oxygen_tank": "A", "nitrogen_tank": "A", "escape_pod": "A"}
# The systems that reach the outside of the hull and so show on its roof; the rest are inside the
# decks, under plating like any other room
EXTERIOR = {"turret_mount", "shield_emitter", "airlock", "escape_pod", "docking_port", "engine"}
CLEAR = 0.1 * T  # how far the superstructure keeps from an exterior system's footprint
WALL_CHARS = set("#123")


def clusters(rows, letter):
    seen, out = set(), []
    for r, row in enumerate(rows):
        for c, ch in enumerate(row):
            if ch != letter or (c, r) in seen:
                continue
            stack, cells = [(c, r)], []
            seen.add((c, r))
            while stack:
                x, y = stack.pop()
                cells.append((x, y))
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= ny < len(rows) and 0 <= nx < len(rows[ny]) and rows[ny][nx] == letter \
                            and (nx, ny) not in seen:
                        seen.add((nx, ny))
                        stack.append((nx, ny))
            xs, ys = [p[0] for p in cells], [p[1] for p in cells]
            out.append((min(xs), min(ys), max(xs) - min(xs) + 1, max(ys) - min(ys) + 1))
    return out


def chassis_mounts(ch):
    """Every mount in a chassis as (component, col, row, w, h) in map cells (row 0 at the top),
    from the explicit mounts list and the map letters, the way the loader reads them."""
    rows = ch["rows"]
    H = len(rows)
    out, covered = [], set()
    sizes = {"P": {"S": (1, 1), "M": (2, 2), "L": (3, 3), "XL": (5, 5)},
             "A": {"S": (1, 2), "M": (2, 3), "L": (3, 7), "XL": (5, 12)}}
    for m in ch.get("mounts", []):
        name = m.get("component") or m.get("type", "").lower()
        # the freight and gunship drives are engines, as their letters are
        name = {"freight_drive": "engine", "gunship_drive": "engine"}.get(name, name)
        if name not in KIND:
            continue
        bw, bh = sizes[KIND[name]][m["size"]]
        w, h = (bh, bw) if m.get("rotation", 0) % 2 else (bw, bh)
        col, row = m["x"], H - (m["y"] + h)
        out.append((name, col, row, w, h))
        covered |= {(col + i, row + j) for i in range(w) for j in range(h)}
    for letter, name in LETTERS.items():
        for (x, y, w, h) in clusters(rows, letter):
            if {(x + i, y + j) for i in range(w) for j in range(h)} <= covered:
                continue
            out.append((name, x, y, w, h))
    return out


def engine_cells(ch):
    """The map cells of a chassis's engines, as a set of (col, row): all of each lies outside the
    hull (EngineLayout)."""
    cells = set()
    for (name, col, row, w, h) in chassis_mounts(ch):
        if name == "engine":
            cells |= {(col + i, row + j) for i in range(w) for j in range(h)}
    return cells


def hull_svg(ch, name, overhang=None, engines=False, superstructure=None):
    """Exterior art for a chassis: the roof over its decks, seen from above. Plating in staggered
    courses over the whole footprint (the grid's tiles and any outline past it), not the tile
    grid; a rim along the edge; the raised superstructure the ship has (SUPERSTRUCTURE, else a
    spine down its middle); small fittings (vents, boxes, hatches, conduit runs) where no exterior
    system is; the fitting of each system that reaches outside (EXTERIOR) over its footprint; and, for ships with no engine
    systems, engine mounts on the aft (-x) edge. `overhang` and `superstructure` are in SVG units."""
    rows = ch["rows"]
    H, W = len(rows), max(len(r) for r in rows)
    ox, oy = ch["origin_x"], ch["origin_y"]
    rng = random.Random(name)

    def cell(col, row):
        """SVG top-left of a map cell (body frame x64, y flipped)."""
        return (col + ox) * T, -(H - row + oy) * T

    a = Art()
    # An engine hangs outside the hull, drawn by the game over its slot: the hull has none of it
    casing = engine_cells(ch)
    solid = {(c, r) for r, row in enumerate(rows) for c, chh in enumerate(row) if chh != " "} - casing
    rectangular = len(solid) == W * H
    # Wedge hull pieces, by map cell: the corners of the cell the triangle keeps, its right angle
    # first (SVG corners: 0 top-left, 1 top-right, 2 bottom-right, 3 bottom-left). At rotation 0 its
    # legs run along the cell's left and bottom edges; each quarter turn is counter-clockwise.
    wedges = {}
    for p in ch.get("hull_pieces", []):
        if p.get("shape", "BLOCK") != "WEDGE" or p.get("width", 1) != 1 or p.get("height", 1) != 1:
            continue
        keep = {0: (3, 2, 0), 1: (2, 3, 1), 2: (1, 2, 0), 3: (0, 1, 3)}[p.get("rotation", 0) % 4]
        wedges[(p["x"], H - 1 - p["y"])] = keep
    x0, y0 = cell(0, 0)
    gw, gh = W * T, H * T
    mounts = [m for m in chassis_mounts(ch) if m[0] in EXTERIOR]

    # The footprint, as one path of the tiles (a shaped hull can run to a thousand tiles: one path
    # stays within the art packer's element limit, and has no seams between tiles) and the outline
    shape = []
    for (c, r) in sorted(solid):
        x, y = cell(c, r)
        if (c, r) in wedges:
            corners = [(x, y), (x + T, y), (x + T, y + T), (x, y + T)]
            shape.append(ring([corners[k] for k in wedges[(c, r)]]))
        else:
            shape.append(f"M{f(x)} {f(y)}h{f(T)}v{f(T)}h{f(-T)}z")
    shape = "".join(shape)
    outline = " ".join(f"{f(x)},{f(y)}" for x, y in overhang) if overhang else None
    # The footprint's outer edges, leaving out those inside the outline past the grid: each tile
    # side with no tile beyond it, and each wedge's sloped face
    edges = []
    for (c, r) in solid:
        x, y = cell(c, r)
        if (c, r) in wedges:
            corners = [(x, y), (x + T, y), (x + T, y + T), (x, y + T)]
            (ax, ay), (bx, by) = (corners[k] for k in wedges[(c, r)][1:])
            edges.append((ax, ay, bx, by))
            continue
        for (n, e) in (((c, r - 1), (x, y, x + T, y)), ((c, r + 1), (x, y + T, x + T, y + T)),
                       ((c - 1, r), (x, y, x, y + T)), ((c + 1, r), (x + T, y, x + T, y + T))):
            if n not in solid:
                edges.append(e)
    if overhang:
        edges = [e for e in edges if not inside(overhang, (e[0] + e[2]) / 2, (e[1] + e[3]) / 2)]
    rim = "".join(f"M{f(x1)} {f(y1)}L{f(x2)} {f(y2)}" for x1, y1, x2, y2 in edges)
    cid = f"{name}_roof"
    # One path, every ring of it wound the same way so they add up: JSVG (the art packer) draws
    # nothing through a clip path of more than one shape
    a.defs += (f'<clipPath id="{cid}"><path d="{ring(overhang) if overhang else ""}{shape}"/></clipPath>'
               # light from the top-left over the whole roof
               f'<linearGradient id="{name}_sheen" x1="0" y1="0" x2="1" y2="1">'
               '<stop offset="0" stop-color="#ffffff" stop-opacity="0.12"/>'
               '<stop offset="0.5" stop-color="#ffffff" stop-opacity="0"/>'
               '<stop offset="1" stop-color="#000000" stop-opacity="0.22"/></linearGradient>')

    if engines and not any(m[0] == "engine" for m in mounts):
        # engine mounts: a bracket and socket on the aft edge for every third row, centered
        for k in range(H // 3):
            r = k * 3 + (H % 3) / 2 + 1.5
            cy = y0 + r * T
            a.b += rect(x0 - T * 0.55, cy - T * 0.8, T * 0.7, T * 1.6, STEEL_DARK, rx=6, stroke=INK, sw=2)
            a.b += circle(x0 - T * 0.25, cy, T * 0.45, HOUSING, stroke=INK, sw=2)
            a.b += circle(x0 - T * 0.25, cy, T * 0.28, "#14181e", stroke=INK, sw=1.5)
            for dy in (-0.62, 0.62):
                a.b += circle(x0 - T * 0.25, cy + dy * T, 3, STEEL_HI, stroke=INK, sw=1)

    # The outline: the footprint stroked, then filled over, so only the outer half of the stroke
    # shows, along the true edge and not where the grid meets the outline past it
    if outline:
        a.b += f'    <polygon points="{outline}" fill="none" stroke="{INK}" stroke-width="6" stroke-linejoin="round"/>\n'
    if rim:
        a.b += f'    <path d="{rim}" fill="none" stroke="{INK}" stroke-width="6" stroke-linecap="square"/>\n'
    if outline:
        a.b += f'    <polygon points="{outline}" fill="{STEEL}"/>\n'
    a.b += f'    <path d="{shape}" fill="{STEEL}"/>\n'
    # The height layer: the bare roof over the footprint, then (clipped to it) what is below or above it
    if outline:
        a.h += f'    <polygon points="{outline}" fill="{gray(0)}"/>\n'
    a.h += f'    <path d="{shape}" fill="{gray(0)}"/>\n'
    a.h += f'    <g clip-path="url(#{cid})">\n'

    pts = [(x, y) for x, y in (overhang or [])]
    for (c, r) in solid:
        x, y = cell(c, r)
        pts += [(x, y), (x + T, y + T)]
    bx0, by0 = min(p[0] for p in pts), min(p[1] for p in pts)
    bx1, by1 = max(p[0] for p in pts), max(p[1] for p in pts)

    roof = ""
    # Plating: courses of plates a meter and a quarter wide, staggered, each a shade lighter or
    # darker than the next; seams in one path, a lit edge along the top of each plate in another
    course = T * 1.25
    tones = {"#56606e": [], "#616c7b": [], "#525c69": [], "#5e6977": []}
    seams, lips = [], []
    y = by0 - rng.uniform(0, course)
    while y < by1:
        x = bx0 - rng.uniform(0, 2 * T)
        while x < bx1:
            length = T * rng.choice((1.5, 2, 2, 2.5, 3, 3.5))
            if rng.random() < 0.6:
                tones[rng.choice(list(tones))].append(f"M{f(x)} {f(y)}h{f(length)}v{f(course)}h{f(-length)}z")
            seams.append(f"M{f(x)} {f(y)}v{f(course)}")
            lips.append(f"M{f(x + 2)} {f(y + 2)}h{f(length - 4)}")
            x += length
        seams.append(f"M{f(bx0 - 2 * T)} {f(y)}H{f(bx1 + T)}")
        y += course
    for tone, d in tones.items():
        if d:
            roof += f'    <path d="{"".join(d)}" fill="{tone}"/>\n'
    roof += f'    <path d="{"".join(seams)}" fill="none" stroke="{STEEL_DARK}" stroke-width="2"/>\n'
    roof += f'    <path d="{"".join(lips)}" fill="none" stroke="{STEEL_HI}" stroke-width="1.5" opacity="0.35"{BAKED}/>\n'
    # the seams are shallow grooves between the plates
    a.h += f'    <path d="{"".join(seams)}" fill="none" stroke="{gray(-0.04)}" stroke-width="3"/>\n'
    # A rim of heavier plate along the edge, standing a little proud of the roof
    if outline:
        roof += f'    <polygon points="{outline}" fill="none" stroke="{STEEL_DARK}" stroke-width="16" stroke-linejoin="round"/>\n'
        a.h += f'    <polygon points="{outline}" fill="none" stroke="{gray(0.04)}" stroke-width="16" stroke-linejoin="round"/>\n'
    if rim:
        roof += f'    <path d="{rim}" fill="none" stroke="{STEEL_DARK}" stroke-width="16" stroke-linecap="square"/>\n'
        a.h += f'    <path d="{rim}" fill="none" stroke="{gray(0.04)}" stroke-width="16" stroke-linecap="square"/>\n'
    if outline:
        roof += (f'    <polygon points="{outline}" fill="none" stroke="{STEEL_HI}" stroke-width="20" '
                 f'stroke-linejoin="round" opacity="0.18" stroke-dasharray="3 9"{BAKED}/>\n')

    # Small fittings on bare roof: tiles no system's footprint covers, not at the edge, not near a port
    taken = set()
    for (m, col, row, w, h) in mounts:
        grow = 1 if m == "docking_port" else 0
        taken |= {(col + i, row + j) for i in range(-grow, w + grow) for j in range(-grow, h + grow)}
    bare = sorted((c, r) for (c, r) in solid if (c, r) not in taken and (c, r) not in wedges
                  and all(n in solid for n in ((c - 1, r), (c + 1, r), (c, r - 1), (c, r + 1))))
    boxes, slots, hatches, runs = [], [], [], []
    for (c, r) in bare:
        if rng.random() > 0.16:
            continue
        x, y = cell(c, r)
        kind = rng.choice(("vent", "box", "box", "hatch", "run"))
        if kind == "vent":
            w_, h_ = T * rng.choice((0.5, 0.65)), T * 0.28
            vx, vy = x + rng.uniform(6, T - 6 - w_), y + rng.uniform(6, T - 6 - h_)
            boxes.append(f"M{f(vx)} {f(vy)}h{f(w_)}v{f(h_)}h{f(-w_)}z")
            relief(a, ("poly", [(vx, vy), (vx + w_, vy), (vx + w_, vy + h_), (vx, vy + h_)]), 0.12)
            for k in range(3):
                slots.append(f"M{f(vx + 4)} {f(vy + 3 + k * (h_ - 6) / 2)}h{f(w_ - 8)}")
        elif kind == "box":
            w_, h_ = T * rng.uniform(0.3, 0.6), T * rng.uniform(0.25, 0.45)
            vx, vy = x + rng.uniform(6, T - 6 - w_), y + rng.uniform(6, T - 6 - h_)
            boxes.append(f"M{f(vx)} {f(vy)}h{f(w_)}v{f(h_)}h{f(-w_)}z")
            relief(a, ("poly", [(vx, vy), (vx + w_, vy), (vx + w_, vy + h_), (vx, vy + h_)]), 0.15)
        elif kind == "hatch":
            rr = T * 0.18
            cx_, cy_ = x + T / 2, y + T / 2
            hatches.append(f"M{f(cx_ - rr)} {f(cy_)}a{f(rr)} {f(rr)} 0 1 0 {f(2 * rr)} 0a{f(rr)} {f(rr)} 0 1 0 {f(-2 * rr)} 0z")
            relief(a, ("circle", cx_, cy_, rr), 0.04)
        else:
            n = 1
            while (c + n, r) in bare and n < 3:
                n += 1
            ry = y + T * rng.choice((0.3, 0.5, 0.7))
            runs.append(f"M{f(x + 8)} {f(ry)}h{f(n * T - 16)}")
            relief(a, ("line", x + 8, ry, x + n * T - 8, ry, 7), 0.1)
    if runs:
        roof += f'    <path d="{"".join(runs)}" fill="none" stroke="{INK}" stroke-width="7" stroke-linecap="round"{RAISED}/>\n'
        roof += f'    <path d="{"".join(runs)}" fill="none" stroke="{STEEL_MID}" stroke-width="4" stroke-linecap="round"{RAISED}/>\n'
    if boxes:
        roof += f'    <path d="{"".join(boxes)}" fill="{HOUSING_HI}" stroke="{INK}" stroke-width="1.5"{RAISED}/>\n'
    if slots:
        roof += f'    <path d="{"".join(slots)}" fill="none" stroke="{INK}" stroke-width="1.5"{RAISED}/>\n'
    if hatches:
        roof += f'    <path d="{"".join(hatches)}" fill="{STEEL_DARK}" stroke="{INK}" stroke-width="1.5"{RAISED}/>\n'

    # The superstructure: raised blocks on the roof (their shadows are the lighting's, not the art's)
    # what the superstructure keeps clear of: every exterior system's footprint, a little apart
    keepout = []
    for (m, col, row, w, h) in mounts:
        if m != "engine":
            x, y = cell(col, row)
            keepout.append((x - CLEAR, y - CLEAR, x + w * T + CLEAR, y + h * T + CLEAR))
    if superstructure is None and not ch.get("station"):
        superstructure = spine(solid, wedges, cell, keepout)
    for k, shape_ in enumerate(superstructure or []):
        if overlaps(shape_, keepout):
            raise ValueError(f"{name}: superstructure shape {k} covers an exterior system")
    # lowest first, so a taller block's top stands over a lower one's in the height layer
    for shape_ in sorted(superstructure or [], key=height_of):
        if shape_[0] == "circle":
            _, cx_, cy_, rr = shape_[:4]
            roof += circle(cx_, cy_, rr, "#66717f", stroke=INK, sw=2.5, extra=RAISED)
            roof += circle(cx_, cy_, rr * 0.72, "none", stroke=STEEL_DARK, sw=2, extra=RAISED)
            roof += circle(cx_, cy_, rr, f"url(#{name}_sheen)", extra=BAKED)
        else:
            pts_ = shape_[1]
            p = " ".join(f"{f(x)},{f(y)}" for x, y in pts_)
            roof += f'    <polygon points="{p}" fill="#66717f" stroke="{INK}" stroke-width="2.5" stroke-linejoin="round"{RAISED}/>\n'
            roof += f'    <polygon points="{p}" fill="none" stroke="{STEEL_HI}" stroke-width="5" stroke-linejoin="round" opacity="0.25"{BAKED}/>\n'
            roof += f'    <polygon points="{p}" fill="url(#{name}_sheen)"{BAKED}/>\n'
        relief(a, shape_, height_of(shape_))
    roof += f'    <rect x="{f(bx0)}" y="{f(by0)}" width="{f(bx1 - bx0)}" height="{f(by1 - by0)}" fill="url(#{name}_sheen)"{BAKED}/>\n'
    a.b += f'    <g clip-path="url(#{cid})">\n{roof}    </g>\n'
    a.h += '    </g>\n'

    for (m, col, row, w, h) in mounts:
        x, y = cell(col, row)
        pw, ph = w * T, h * T
        cx, cy = x + pw / 2, y + ph / 2
        s = min(pw, ph)
        if m == "turret_mount":
            # the barbette the turret turns on
            r = s * 0.46
            a.b += circle(cx, cy, r, STEEL_DARK, stroke=INK, sw=2, extra=RAISED)
            a.b += circle(cx, cy, r * 0.8, "none", stroke=STEEL_HI, sw=1.5, extra=' stroke-dasharray="4 4"' + RAISED)
            a.b += circle(cx, cy, r * 0.3, INK, extra=RAISED)
            relief(a, ("circle", cx, cy, r), 0.3)
        elif m == "shield_emitter":
            a.b += circle(cx, cy, s * 0.4, "#2f5664", stroke=INK, sw=2, extra=RAISED)
            relief(a, ("circle", cx, cy, s * 0.4), 0.2)
            a.e += circle(cx, cy, s * 0.25, ACCENT["shield_emitter"], extra=' opacity="0.6"')
        elif m == "engine":
            pass  # outside the hull: the game draws the engine itself (engines/nacelle_<size>)
        elif m == "airlock":
            a.b += rect(x + 6, y + 6, pw - 12, ph - 12, STEEL_DARK, rx=6, stroke=INK, sw=2)
            a.b += hazard_band(x + 10, y + 10, pw - 20, 8, f"ext_al_{col}_{row}")
            light(a, cx, cy, 4, HAZARD)
        elif m == "escape_pod":
            # the pod's hull hatch: a round cover ringed in its band color, with explosive bolts
            r = s * 0.4
            a.b += circle(cx, cy, r, STEEL_DARK, stroke=INK, sw=2)
            a.b += circle(cx, cy, r * 0.78, "none", stroke=ACCENT["escape_pod"], sw=4)
            a.b += circle(cx, cy, r * 0.45, HOUSING, stroke=INK, sw=1.5)
            for k in range(6):
                ang = k * math.pi / 3
                a.b += circle(cx + math.cos(ang) * r * 0.92, cy + math.sin(ang) * r * 0.92, 2, HAZARD, stroke=INK, sw=0.8)
        elif m == "docking_port":
            a.b += rect(x, y, pw, ph, STEEL_DARK, rx=3, stroke=INK, sw=2)
            a.b += rect(x + 10, y + 10, pw - 20, ph - 20, "none", stroke=HAZARD, sw=4,
                        extra=' stroke-dasharray="12 8"')
            # collar half a tile out on the open side: the one with only space beyond the port
            if rectangular:
                side = "top" if row == 0 else "bottom" if row + h == H else "left" if col == 0 else "right"
            else:
                def open_(cells):
                    return all(cl not in solid for cl in cells)
                side = ("top" if w > h and open_([(col + i, row - 1) for i in range(w)]) else
                        "bottom" if w > h and open_([(col + i, row + h) for i in range(w)]) else
                        "left" if open_([(col - 1, row + j) for j in range(h)]) else "right")
            if side == "top":
                a.b += rect(x + T * 0.5, y - T * 0.5, pw - T, T * 0.5, STEEL_MID, rx=3, stroke=INK, sw=2)
            elif side == "bottom":
                a.b += rect(x + T * 0.5, y + ph, pw - T, T * 0.5, STEEL_MID, rx=3, stroke=INK, sw=2)
            elif side == "left":
                a.b += rect(x - T * 0.5, y + T * 0.5, T * 0.5, ph - T, STEEL_MID, rx=3, stroke=INK, sw=2)
            else:
                a.b += rect(x + pw, y + T * 0.5, T * 0.5, ph - T, STEEL_MID, rx=3, stroke=INK, sw=2)
    # navigation lights at the grid corners: red to port (+y), green to starboard
    if rectangular:
        light(a, x0 + gw - 12, y0 + 12, 4, RED)
        light(a, x0 + gw - 12, y0 + gh - 12, 4, GREEN)
        light(a, x0 + 12, y0 + 12, 4, "#ffffff")
        light(a, x0 + 12, y0 + gh - 12, 4, "#ffffff")
    else:
        # on a shaped hull, at the outermost square tiles: the foremost of the top and bottom rows
        # that reach furthest out, and the aftmost
        squares = [cl for cl in solid if cl not in wedges]
        top = min(r for _, r in squares)
        bottom = max(r for _, r in squares)
        for r_, colour, dy in ((top, RED, 12), (bottom, GREEN, T - 12)):
            cols = [c for c, r in squares if r == r_]
            fx, fy = cell(max(cols), r_)
            light(a, fx + T - 12, fy + dy, 4, colour)
            ax_, ay_ = cell(min(cols), r_)
            light(a, ax_ + 12, ay_ + dy, 4, "#ffffff")
    return a, (x0, y0, gw, gh)


def ring(points):
    """A closed subpath through `points`, wound clockwise on the page (as a tile's square is), so
    rings that overlap in one path fill their union and not leave a hole."""
    area = sum(x1 * y2 - x2 * y1 for (x1, y1), (x2, y2) in zip(points, points[1:] + points[:1]))
    pts = points if area >= 0 else points[::-1]
    return "M" + "L".join(f"{f(x)} {f(y)}" for x, y in pts) + "Z"


def inside(poly_, x, y):
    """Whether (x, y) is strictly inside the polygon `poly_` (even-odd rule); a point on its edge is not."""
    n, hit = len(poly_), False
    for i in range(n):
        (x1, y1), (x2, y2) = poly_[i], poly_[(i + 1) % n]
        if (x1 - x) * (y2 - y) - (x2 - x) * (y1 - y) == 0 and min(x1, x2) <= x <= max(x1, x2) \
                and min(y1, y2) <= y <= max(y1, y2):
            return False
        if (y1 > y) != (y2 > y) and x < x1 + (y - y1) * (x2 - x1) / (y2 - y1):
            hit = not hit
    return hit


def overlaps(shape_, keepout):
    """Whether a superstructure shape (SVG units) reaches into any of the keepout rectangles."""
    for (x0, y0, x1, y1) in keepout:
        pts = [(x0 + (x1 - x0) * i / 16, y0 + (y1 - y0) * j / 16) for i in range(17) for j in range(17)]
        if shape_[0] == "circle":
            _, cx, cy, r = shape_[:4]
            if any(math.hypot(px - cx, py - cy) < r for px, py in pts):
                return True
        elif (any(inside(shape_[1], px, py) for px, py in pts)
              or any(x0 < px < x1 and y0 < py < y1 for px, py in shape_[1])):
            return True
    return False


def spine(solid, wedges, cell, keepout=()):
    """A ship's superstructure when it has none of its own: a raised spine down the middle, along
    the longest run of square tiles on the row at the body's centerline, a tile and a bit short of
    each end, a tenth of the ship's beam wide, with chamfered ends; broken where it would cover an
    exterior system (`keepout`), keeping the pieces two meters long or more."""
    rows = {}
    for (c, r) in solid:
        if (c, r) not in wedges:
            rows.setdefault(r, set()).add(c)
    # the row whose middle is nearest body y = 0 (SVG y = 0)
    r0 = min(rows, key=lambda r: abs(cell(0, r)[1] + T / 2))
    best, run = [], []
    for c in range(min(rows[r0]), max(rows[r0]) + 2):
        if c in rows[r0]:
            run.append(c)
        else:
            best, run = max(best, run, key=len), []
    if len(best) < 5:
        return []
    xa, _ = cell(best[0], r0)
    xb, _ = cell(best[-1] + 1, r0)
    xa, xb = xa + T * 1.2, xb - T * 1.2
    # wider on a bigger ship: a tenth of its beam, a meter to three across
    beam = max(rows) - min(rows) + 1
    hw = min(max(beam * 0.1, 0.55), 1.5) * T
    ch_ = hw * 0.7
    pieces = [(xa, xb)]
    for (x0, y0, x1, y1) in keepout:
        if y0 < hw and y1 > -hw:
            pieces = [q for (pa, pb) in pieces for q in ((pa, min(pb, x0)), (max(pa, x1), pb)) if q[1] > q[0]]
    return [("poly", [(pa, -hw + ch_), (pa + ch_, -hw), (pb - ch_, -hw), (pb, -hw + ch_),
                      (pb, hw - ch_), (pb - ch_, hw), (pa + ch_, hw), (pa, hw - ch_)], SPINE_HEIGHT)
            for (pa, pb) in pieces if pb - pa >= 2 * T]


SPINE_HEIGHT = 0.5  # meters a spine stands off the roof


def height_of(shape_):
    """How tall a superstructure shape stands, in meters: its own height when it gives one, else
    0.9 for a block and 1.2 for a dome."""
    n = 4 if shape_[0] == "circle" else 2
    if len(shape_) > n:
        return shape_[n]
    return 1.2 if shape_[0] == "circle" else 0.9


# Each ship's hull outline in body meters, by chassis, drawn under the grid, so each has a shape
# of its own and not the grid's box. One that reaches past the grid also becomes the hull's
# collision layer, since a ship otherwise collides on its tiles alone. Outlines keep clear of the
# docking ports and the engines.
OVERHANG = {
    # a patrol ship: a two-stage prow, swept fins aft and a fairing between the engines
    "corvette": [(-11.0, -3.0), (-11.4, -4.1), (-10.7, -4.1), (-8.8, -3.0), (5.0, -3.0), (6.0, -1.8), (6.9, -0.5), (7.2, 0.0),
                 (6.9, 0.5), (6.0, 1.8), (5.0, 3.0), (-8.8, 3.0), (-10.7, 4.1), (-11.4, 4.1), (-11.0, 3.0),
                 (-11.0, 0.8), (-12.2, 0.5), (-12.2, -0.5), (-11.0, -0.8)],
    "freighter": [(-10.0, -6.5), (10.0, -6.5), (10.0, 6.5), (-10.0, 6.5)],
    # a fast attacker: forked mandibles at the bow and swept delta wings aft
    "raider": [(-7.5, -2.5), (-8.7, -4.3), (-7.6, -4.3), (-4.8, -2.5), (7.5, -2.5), (10.4, -0.9),
               (9.2, 0.0), (10.4, 0.9), (7.5, 2.5), (-4.8, 2.5), (-7.6, 4.3), (-8.7, 4.3), (-7.5, 2.5)],
    # a brawler: a hammerhead ram, gun sponsons amidships and an armored skirt aft
    "gunship": [(-10.0, -4.0), (1.6, -4.0), (2.1, -4.6), (4.6, -4.6), (5.1, -4.0), (10.0, -4.0),
                (10.6, -4.7), (11.7, -4.5), (12.8, -2.0), (13.2, 0.0), (12.8, 2.0), (11.7, 4.5),
                (10.6, 4.7), (10.0, 4.0), (5.1, 4.0), (4.6, 4.6), (2.1, 4.6), (1.6, 4.0), (-10.0, 4.0),
                (-10.0, 0.9), (-11.0, 0.7), (-11.0, -0.7), (-10.0, -0.9)],
    # a warship: a long tapering bow, gun sponsons forward and broad wings aft
    "frigate": [(-12.0, -5.0), (-9.2, -5.0), (-9.4, -6.4), (-7.2, -6.4), (-6.0, -5.0), (2.5, -5.0),
                (3.5, -5.7), (8.5, -5.7), (9.4, -5.0), (12.0, -5.0), (14.6, -2.6), (16.2, -0.8),
                (16.6, 0.0), (16.2, 0.8), (14.6, 2.6), (12.0, 5.0), (9.4, 5.0), (8.5, 5.7), (3.5, 5.7),
                (2.5, 5.0), (-6.0, 5.0), (-7.2, 6.4), (-9.4, 6.4), (-9.2, 5.0), (-12.0, 5.0),
                (-12.0, 1.2), (-13.2, 0.9), (-13.2, -0.9), (-12.0, -1.2)],
    # a sensor picket: a rounded sensor nose and a blister either side
    "picket": [(-7.5, -3.0), (2.0, -3.0), (2.4, -3.8), (4.0, -3.8), (4.4, -3.0)]
              + [(round(7.5 + 2.6 * math.cos(math.radians(d)), 3), round(3.0 * math.sin(math.radians(d)), 3))
                 for d in range(-90, 91, 15)]
              + [(4.4, 3.0), (4.0, 3.8), (2.4, 3.8), (2.0, 3.0), (-7.5, 3.0)],
    # a pirate: lopsided, with a raked bow, an outrigger bolted on to starboard and one fin to port
    "corsair": [(-9.0, -3.5), (-6.5, -3.5), (-6.1, -4.8), (0.2, -4.8), (0.8, -3.5), (9.0, -3.5),
                (10.2, -2.6), (11.8, -0.4), (11.0, 1.6), (9.0, 3.5), (-4.2, 3.5), (-6.2, 4.4),
                (-7.0, 4.4), (-6.8, 3.5), (-9.0, 3.5)],
}

# Each ship's raised superstructure in body meters, by chassis: ("poly", points[, height]) or
# ("circle", x, y, radius[, height]), height in meters off the roof (height_of()). A ship without
# one gets a spine down its middle (spine()).
SUPERSTRUCTURE = {
    "corvette": [("poly", [(-10.4, -0.8), (1.6, -0.8), (2.8, -0.3), (2.8, 0.3), (1.6, 0.8), (-10.4, 0.8)], 0.5),
                 ("poly", [(-6.0, -0.85), (-3.0, -0.85), (-2.3, -0.1), (-2.3, 1.1), (-3.0, 1.8), (-6.0, 1.8)], 0.9)],
    "gunship": [("poly", [(-8.5, -1.9), (0.0, -1.9), (0.8, -0.8), (6.0, -0.8), (6.6, 0.0), (6.0, 0.8),
                          (0.8, 0.8), (0.0, 1.9), (-8.5, 1.9)], 0.8),
                ("poly", [(10.2, -3.4), (12.2, -1.4), (12.2, 1.4), (10.2, 3.4)], 0.6)],
    "frigate": [("poly", [(-11.2, -1.1), (11.0, -1.1), (14.8, -0.3), (14.8, 0.3), (11.0, 1.1), (-11.2, 1.1)], 0.5),
                ("poly", [(3.8, -1.8), (7.4, -1.8), (8.4, -0.8), (8.4, 0.8), (7.4, 1.8), (3.8, 1.8)], 0.9),
                ("poly", [(-11.6, -3.6), (-8.2, -3.6), (-7.4, -2.4), (-7.4, 2.4), (-8.2, 3.6), (-11.6, 3.6)], 0.8)],
    "raider": [("poly", [(-6.8, -0.6), (3.6, -0.6), (4.3, 0.0), (3.6, 0.6), (-6.8, 0.6)], 0.5),
               ("poly", [(1.0, -0.9), (3.6, -0.9), (4.3, 0.3), (3.6, 1.8), (1.0, 1.8)], 0.8)],
    "corsair": [("poly", [(-8.2, -1.6), (1.2, -1.6), (1.8, -1.0), (1.2, -0.4), (-8.2, -0.4)], 0.6),
                ("poly", [(2.6, 0.4), (5.2, 0.4), (5.8, 1.2), (5.2, 2.9), (2.6, 2.9)], 0.9),
                ("poly", [(-5.8, -4.4), (-0.2, -4.4), (0.3, -3.2), (-6.1, -3.2)], 0.5)],
    "picket": [("poly", [(-6.8, -0.5), (6.0, -0.5), (6.0, 0.5), (-6.8, 0.5)], 0.5),
               ("poly", [(0.8, -0.4), (3.6, -0.4), (4.2, 0.8), (3.6, 2.4), (0.8, 2.4)], 0.8),
               ("circle", 8.0, 0.0, 1.5, 1.2)],
}


def to_svg(shapes):
    """Superstructure shapes from body meters to SVG units (y flipped)."""
    out = []
    for s in shapes:
        if s[0] == "circle":
            out.append(("circle", s[1] * T, -s[2] * T, s[3] * T) + tuple(s[4:]))
        else:
            out.append(("poly", [(x * T, -y * T) for x, y in s[1]]) + tuple(s[2:]))
    return out


def hulls():
    ships = load_toml("ships.toml")["ship"]
    chassis = load_toml("chassis.toml")["chassis"]
    done = set()
    for ship_id, ship in ships.items():
        name = ship["chassis"]
        if name in done:
            continue
        done.add(name)
        pts = [(x * T, -y * T) for x, y in OVERHANG.get(name, [])]
        sup = to_svg(SUPERSTRUCTURE[name]) if name in SUPERSTRUCTURE else None
        a, (x0, y0, gw, gh) = hull_svg(chassis[name], name, overhang=pts, engines=True, superstructure=sup)
        if any(x < x0 or x > x0 + gw or y < y0 or y > y0 + gh for x, y in pts):
            a.c += f'    <polygon points="{" ".join(f"{f(x)},{f(y)}" for x, y in pts)}"/>\n'
        xs, ys = [p[0] for p in pts] + [x0, x0 + gw], [p[1] for p in pts] + [y0, y0 + gh]
        vb = (min(xs) - T, min(ys) - T, max(xs) - min(xs) + 2 * T, max(ys) - min(ys) + 2 * T)
        write(f"hulls/{name}.svg", vb[2], vb[3], a,
              f"{name} exterior hull: body frame, origin at the SVG origin, bow toward +x, the roof over the chassis grid",
              viewbox=" ".join(f(v) for v in vb))
    for name, ch in chassis.items():
        if not ch.get("station"):
            continue
        a, (x0, y0, gw, gh) = hull_svg(ch, name)
        vb = (x0 - T, y0 - T, gw + 2 * T, gh + 2 * T)
        write(f"hulls/{name}.svg", vb[2], vb[3], a,
              f"{name} exterior hull: body frame, origin at the SVG origin, the roof over the chassis grid",
              viewbox=" ".join(f(v) for v in vb))

    # Engine plume, drawn behind an engine, pointing -x (exhaust), origin at the nozzle
    a = Art()
    a.defs = ('<linearGradient id="plume_g" x1="1" y1="0" x2="0" y2="0">'
              '<stop offset="0" stop-color="#ffffff"/><stop offset="0.25" stop-color="#9fd8ff"/>'
              '<stop offset="1" stop-color="#4f8fff" stop-opacity="0"/></linearGradient>')
    a.e = path("M0,-24 C-60,-20 -150,-8 -192,0 C-150,8 -60,20 0,24 Z", fill="url(#plume_g)", extra=' opacity="0.9"')
    a.e += path("M0,-10 C-40,-8 -80,-3 -110,0 C-80,3 -40,8 0,10 Z", fill="#ffffff", extra=' opacity="0.8"')
    write("hulls/thruster_plume.svg", 192, 48, a, "thruster plume: origin at the nozzle, exhaust toward -x",
          viewbox="-192 -24 192 48")


# An engine's width outside, in tiles: a little under the short side of its auxiliary slot
ENGINE_SCALE = {size: AUX[size][0] * 0.9 for size in SIZES}


def engines():
    """Engines seen from outside. nacelle_<size> is an engine system, drawn over its slot outside
    the hull (engine_svgs.py). engine_<size> is the older bolt-on engine, which now only stands in
    the engine mounts the hull art draws on a craft with no engine systems (the small size): pivot
    at the SVG origin, on the hull's outer edge, nozzle toward -x. `_unpowered` is an engine not
    burning (no hot throat), and exhaust_<size> is the plume drawn from a nozzle's exit while it
    burns. Those are the bell (Halvorsen) engines; an engine model in another maker's style
    (engine_svgs.MODELS) has its own <model>_nacelle_<size> and exhaust_<model>_<size>."""
    from engine_svgs import engine
    for size in SIZES:
        # the engine as it hangs off the hull, drawn over its slot (ExteriorArt)
        w, h, a, _ = engine(size, key="nacelle")
        write(f"engines/nacelle_{size}.svg", w, h, a,
              f"nacelle_{size}: an engine filling its {w // T}x{h // T} slot outside the hull, nozzle toward +y, "
              "mounting flange at the bottom, burning", unpowered=f"engines/nacelle_{size}_unpowered.svg")
    for size, k in ENGINE_SCALE.items():
        u = T * k
        a = Art()
        gid = f"engine_{size}_throat"
        a.defs = glow_def(gid, "#9fd8ff")
        # the bell: from the socket aft, flaring to the exit
        bell = [(-0.08 * u, -0.3 * u), (-0.92 * u, -0.5 * u), (-0.92 * u, 0.5 * u), (-0.08 * u, 0.3 * u)]
        a.b += poly(bell, STEEL_DARK, stroke=INK, sw=2)
        for t_ in (0.3, 0.55, 0.8):  # cooling bands
            x = -(0.08 + 0.84 * t_) * u
            hw = (0.3 + 0.2 * t_) * u
            a.b += line(x, -hw + 1.5, x, hw - 1.5, "#2a3039", sw=2.5)
        a.b += line(-0.1 * u, -0.29 * u, -0.9 * u, -0.48 * u, STEEL_HI, sw=1.2, extra=' opacity="0.7"')
        # exit rim seen edge-on and the dark throat
        a.b += path(f"M{f(-0.92 * u)},{f(-0.5 * u)} C{f(-1.02 * u)},{f(-0.3 * u)} {f(-1.02 * u)},{f(0.3 * u)} "
                    f"{f(-0.92 * u)},{f(0.5 * u)}", fill="#14181e", stroke=INK, sw=2)
        # the gimbal housing in the socket
        a.b += circle(0, 0, 0.32 * u, HOUSING, stroke=INK, sw=2)
        a.b += circle(0, 0, 0.2 * u, STEEL_MID, stroke=INK, sw=1.5)
        for ang in (45, 135, 225, 315):
            r = math.radians(ang)
            a.b += rivet(math.cos(r) * 0.26 * u, math.sin(r) * 0.26 * u, 1.4)
        a.e = lit(path(f"M{f(-0.92 * u)},{f(-0.44 * u)} C{f(-1.0 * u)},{f(-0.25 * u)} {f(-1.0 * u)},{f(0.25 * u)} "
                       f"{f(-0.92 * u)},{f(0.44 * u)} Z", fill="#9fd8ff", extra=' opacity="0.85"')
                  + circle(-0.85 * u, 0, 0.3 * u, f"url(#{gid})"), "glow")
        left, half = -1.05 * u, 0.55 * u
        write(f"engines/engine_{size}.svg", 1.45 * u, 2 * half, a,
              f"engine_{size}: pivot on the hull's engine mount socket, nozzle toward -x, burning",
              viewbox=f"{f(left)} {f(-half)} {f(1.45 * u)} {f(2 * half)}", unpowered=f"engines/engine_{size}_unpowered.svg")

    # each engine model's plume, and the art of the models not drawn in the plain (bell) style
    from engine_svgs import MODELS, plume
    for size in SIZES:
        a, vb, length, height = plume(size, "bell")
        write(f"engines/exhaust_{size}.svg", length, height, a,
              f"exhaust_{size}: plume behind a bell-nozzle engine of size {size}, pivot on its nozzle's exit, "
              "exhaust toward -x; scale along x with throttle", viewbox=vb)
        for model, style in MODELS.items():
            if style == "bell":
                continue
            w, h, a, _ = engine(size, style, key=model)
            write(f"engines/{model}_nacelle_{size}.svg", w, h, a,
                  f"{model}_nacelle_{size}: the {style} engine filling its {w // T}x{h // T} slot outside the hull, nozzle "
                  "toward +y, mounting flange at the bottom, burning", unpowered=f"engines/{model}_nacelle_{size}_unpowered.svg")
            a, vb, length, height = plume(size, style)
            write(f"engines/exhaust_{model}_{size}.svg", length, height, a,
                  f"exhaust_{model}_{size}: plume behind the {style} engine, pivot on the middle of its slot's nozzle "
                  "end, exhaust toward -x; scale along x with throttle", viewbox=vb)


def rcs():
    """RCS thruster blocks seen from outside, bolted to a hull edge that faces space. Pivot at
    the SVG origin, which goes on the middle of that edge; the block stands out toward -x (into
    space) and its main nozzle fires -x. `rcs_linear` has that one nozzle; `rcs_quad` adds one
    each way along the hull (+y and -y), halfway out, at the block's ends. `_destroyed` is the
    block with its hull tile shot away. `rcs_puff` is a nozzle's jet, pivot on the nozzle's
    mouth, toward -x, scaled along x with how hard it fires."""
    u = T
    out, mouth, half = 0.13 * u, 0.2 * u, 0.25 * u   # block face, main nozzle mouth, side mouths
    for kind in ("linear", "quad"):
        for destroyed in (False, True):
            a = Art()
            hl = 0.17 * u if kind == "linear" else 0.19 * u
            # the mounting plate on the hull, then the block on it
            a.b += rect(-0.03 * u, -hl - 0.04 * u, 0.06 * u, 2 * hl + 0.08 * u, STEEL_DARK, stroke=INK, sw=1.5)
            a.b += rect(-out, -hl, out, 2 * hl, HOUSING if not destroyed else "#1b1f25", rx=2, stroke=INK, sw=2)
            a.b += line(-out + 2, -hl + 3, -out + 2, hl - 3, HOUSING_HI if not destroyed else STEEL_DARK, sw=1.2)
            for yy in (-hl + 4, hl - 4):
                a.b += rivet(-0.035 * u, yy, 1.2)
            nozzles = [(-1, 0)] + ([(0, 1), (0, -1)] if kind == "quad" else [])
            for (dx, dy) in nozzles:
                if dx:  # the main nozzle, out of the block's face
                    bell = [(-out, -0.045 * u), (-mouth, -0.075 * u), (-mouth, 0.075 * u), (-out, 0.045 * u)]
                else:   # a side nozzle at the block's end, halfway out
                    cx, y0, y1 = -out / 2 - 0.0 * u, dy * hl, dy * half
                    bell = [(cx - 0.035 * u, y0), (cx - 0.06 * u, y1), (cx + 0.06 * u, y1), (cx + 0.035 * u, y0)]
                if destroyed:
                    bell = [(px * 0.9, py * 0.9) for (px, py) in bell]
                a.b += poly(bell, STEEL_DARK if not destroyed else "#14181e", stroke=INK, sw=1.5)
            if destroyed:
                a.b += circle(-out / 2, 0, 0.1 * u, "#05070a", extra=' opacity="0.7"')
                a.b += line(-out, -hl * 0.6, -out * 0.3, hl * 0.3, STEEL_HI, sw=1)
            else:
                # a status lamp on the block's face
                a.e = lit(circle(-out * 0.5, -hl + 0.06 * u, 1.6, "#9fd8ff"), "glow")
            name = f"engines/rcs_{kind}" + ("_destroyed" if destroyed else "")
            left, top = -0.27 * u, -0.27 * u
            write(name + ".svg", 0.32 * u, 0.54 * u, a,
                  f"rcs_{kind}{' destroyed' if destroyed else ''}: pivot on the middle of the hull edge it is bolted to, "
                  f"block and main nozzle toward -x (space)" + ("; side nozzles toward +y and -y" if kind == "quad" else ""),
                  viewbox=f"{f(left)} {f(top)} {f(0.32 * u)} {f(0.54 * u)}")
    a = Art()
    pid = "rcs_puff_g"
    a.defs = ('<linearGradient id="' + pid + '" x1="1" y1="0" x2="0" y2="0">'
              '<stop offset="0" stop-color="#ffffff"/><stop offset="0.3" stop-color="#cfe9ff"/>'
              '<stop offset="1" stop-color="#9fd8ff" stop-opacity="0"/></linearGradient>')
    length, w0 = 0.9 * u, 0.06 * u
    a.e = lit(path(f"M0,{f(-w0)} C{f(-0.3 * u)},{f(-0.11 * u)} {f(-0.7 * u)},{f(-0.09 * u)} {f(-length)},0 "
                   f"C{f(-0.7 * u)},{f(0.09 * u)} {f(-0.3 * u)},{f(0.11 * u)} 0,{f(w0)} Z", fill=f"url(#{pid})",
                   extra=' opacity="0.85"')
              + path(f"M0,{f(-0.035 * u)} C{f(-0.15 * u)},{f(-0.05 * u)} {f(-0.3 * u)},{f(-0.02 * u)} {f(-0.4 * u)},0 "
                     f"C{f(-0.3 * u)},{f(0.02 * u)} {f(-0.15 * u)},{f(0.05 * u)} 0,{f(0.035 * u)} Z", fill="#ffffff",
                     extra=' opacity="0.8"'), "glow")
    write("engines/rcs_puff.svg", length, 0.24 * u, a,
          "rcs_puff: an RCS nozzle's jet, pivot on the nozzle's mouth, toward -x; scale along x with how hard it fires",
          viewbox=f"{f(-length)} {f(-0.12 * u)} {f(length)} {f(0.24 * u)}")


def turrets():
    """Weapon turrets seen from above, barrel toward +x, pivot at the SVG origin. The
    pivot goes on the center of the turret mount the weapon is wired to."""
    def base(a, r):
        a.b += circle(0, 0, r, STEEL, stroke=INK, sw=2) + circle(0, 0, r * 0.7, STEEL_MID, stroke=INK, sw=1.5)

    def vb(left, right, half):
        return f"{f(left)} {f(-half)} {f(right - left)} {f(2 * half)}", right - left, 2 * half

    a = Art()
    base(a, 28)
    a.b += rect(10, -6, 74, 12, STEEL_DARK, rx=2, stroke=INK, sw=2)
    a.b += rect(10, -3, 74, 2, "#1f4a5a")
    a.e += rect(10, -3, 74, 2, "#4fd1ff", extra=' opacity="0.7"')
    a.b += rect(80, -8, 8, 16, STEEL_MID, rx=2, stroke=INK, sw=2)
    a.b += rect(-20, -16, 30, 32, HOUSING, rx=4, stroke=INK, sw=2)
    v, w, h = vb(-32, 92, 32)
    write("turrets/railgun_mk1.svg", w, h, a, "railgun_mk1 turret: pivot at origin, barrel toward +x", viewbox=v,
          unpowered="turrets/railgun_mk1_unpowered.svg")

    a = Art()
    base(a, 20)
    for dy in (-5, 5):
        a.b += rect(6, dy - 3, 40, 6, STEEL_DARK, rx=1.5, stroke=INK, sw=1.5)
    a.b += rect(-12, -12, 22, 24, HOUSING, rx=4, stroke=INK, sw=2)
    v, w, h = vb(-24, 52, 24)
    write("turrets/autocannon_mk1.svg", w, h, a, "autocannon_mk1 turret: pivot at origin, barrels toward +x", viewbox=v)

    a = Art()
    base(a, 22)
    for dy in (-6, 0, 6):
        a.b += rect(8, dy - 2.5, 50, 5, STEEL_DARK, rx=1.5, stroke=INK, sw=1.5)
    a.b += rect(44, -10, 6, 20, STEEL_MID, rx=2, stroke=INK, sw=1.5)
    a.b += rect(-14, -14, 24, 28, HOUSING, rx=4, stroke=INK, sw=2)
    a.b += hazard_band(-12, -12, 20, 5, "chaingun")
    v, w, h = vb(-26, 62, 26)
    write("turrets/chaingun_mk1.svg", w, h, a, "chaingun_mk1 turret: pivot at origin, three barrels toward +x",
          viewbox=v)

    a = Art()
    base(a, 22)
    a.b += rect(4, -7, 36, 14, HOUSING, rx=6, stroke=INK, sw=2)
    a.b += circle(40, 0, 7, "#4a1f1f", stroke=INK, sw=2)
    a.e += circle(40, 0, 10, "#ff5a5a", extra=' opacity="0.25"')
    a.e += circle(40, 0, 5.5, "#ff5a5a") + circle(40, 0, 2.5, "#ffd6d6")
    a.b += rect(-14, -10, 18, 20, STEEL_DARK, rx=3, stroke=INK, sw=2)
    v, w, h = vb(-26, 52, 26)
    write("turrets/laser_mk1.svg", w, h, a, "laser_mk1 turret: pivot at origin, emitter toward +x", viewbox=v,
          unpowered="turrets/laser_mk1_unpowered.svg")

    a = Art()
    base(a, 24)
    a.b += rect(-14, -18, 44, 36, STEEL_DARK, rx=4, stroke=INK, sw=2)
    for (cx, cy) in ((4, -8), (20, -8), (4, 8), (20, 8)):
        a.b += circle(cx, cy, 6, SEAT_DARK, stroke=INK, sw=1.5)
        a.b += circle(cx, cy, 2.5, "#8a2a2a")
    v, w, h = vb(-28, 36, 28)
    write("turrets/harpoon_launcher.svg", w, h, a, "harpoon_launcher: pivot at origin, tubes facing +x", viewbox=v)

    # flak gun: a squat twin mount with short, fat barrels and a drum feed on each side
    a = Art()
    base(a, 22)
    for dy in (-6, 6):
        a.b += rect(6, dy - 4, 30, 8, STEEL_DARK, rx=2, stroke=INK, sw=1.5)
        a.b += rect(32, dy - 5, 6, 10, STEEL_MID, rx=1.5, stroke=INK, sw=1.5)
        a.b += circle(-4, dy * 2.4, 6, HOUSING, stroke=INK, sw=1.5)
    a.b += rect(-14, -11, 22, 22, HOUSING, rx=4, stroke=INK, sw=2)
    a.b += hazard_band(-12, 6, 18, 4, "flak")
    v, w, h = vb(-26, 42, 26)
    write("turrets/flak_mk1.svg", w, h, a, "flak_mk1 turret: pivot at origin, twin barrels toward +x", viewbox=v)

    # dart rack: two slim tubes side by side on a small launcher base
    a = Art()
    base(a, 18)
    a.b += rect(-10, -12, 34, 24, STEEL_DARK, rx=3, stroke=INK, sw=2)
    for cy in (-5.5, 5.5):
        a.b += rect(-6, cy - 4, 32, 8, HOUSING, rx=4, stroke=INK, sw=1.5)
        a.b += circle(26, cy, 3.5, SEAT_DARK, stroke=INK, sw=1.2)
        a.b += circle(26, cy, 1.5, "#8a2a2a")
    v, w, h = vb(-22, 32, 22)
    write("turrets/dart_rack.svg", w, h, a, "dart_rack: pivot at origin, two tubes facing +x", viewbox=v)

    # heavy railgun: a long double-rail barrel on a big housing, coils glowing when powered
    a = Art()
    base(a, 34)
    a.b += rect(12, -9, 112, 18, STEEL_DARK, rx=2, stroke=INK, sw=2)
    a.b += rect(12, -2, 112, 4, "#1f4a5a")
    a.e += rect(12, -2, 112, 4, "#4fd1ff", extra=' opacity="0.75"')
    for x in (30, 54, 78, 102):
        a.b += rect(x, -12, 6, 24, STEEL_MID, rx=1.5, stroke=INK, sw=1.5)
    a.b += rect(118, -11, 10, 22, STEEL_MID, rx=2, stroke=INK, sw=2)
    a.b += rect(-26, -20, 40, 40, HOUSING, rx=5, stroke=INK, sw=2)
    a.b += hazard_band(-24, -18, 28, 5, "railheavy")
    v, w, h = vb(-38, 132, 38)
    write("turrets/railgun_heavy.svg", w, h, a, "railgun_heavy turret: pivot at origin, barrel toward +x", viewbox=v,
          unpowered="turrets/railgun_heavy_unpowered.svg")

    # ion cannon: a stubby barrel ending in a ring of emitter prongs, blue-white when powered
    a = Art()
    base(a, 22)
    a.b += rect(6, -6, 30, 12, STEEL_DARK, rx=3, stroke=INK, sw=2)
    a.b += circle(40, 0, 9, "#1c2a4a", stroke=INK, sw=2)
    for dy in (-9, 9):
        a.b += rect(36, dy - 1.5, 14, 3, STEEL_MID, rx=1, stroke=INK, sw=1)
    a.e += circle(40, 0, 12, "#7aa8ff", extra=' opacity="0.25"')
    a.e += circle(40, 0, 6, "#7aa8ff") + circle(40, 0, 2.5, "#e0ecff")
    a.b += rect(-14, -11, 20, 22, HOUSING, rx=3, stroke=INK, sw=2)
    v, w, h = vb(-26, 54, 26)
    write("turrets/ion_mk1.svg", w, h, a, "ion_mk1 turret: pivot at origin, emitter toward +x", viewbox=v,
          unpowered="turrets/ion_mk1_unpowered.svg")

    # railgun mk2: the mk1 refined, a slimmer barrel with a gold-trimmed housing (an ace's refit)
    a = Art()
    base(a, 28)
    a.b += rect(10, -5, 80, 10, STEEL_DARK, rx=2, stroke=INK, sw=2)
    a.b += rect(10, -2, 80, 4, "#1f4a5a")
    a.e += rect(10, -2, 80, 4, "#7ff0ff", extra=' opacity="0.8"')
    a.b += rect(86, -7, 8, 14, STEEL_MID, rx=2, stroke=INK, sw=2)
    a.b += rect(-20, -16, 30, 32, HOUSING, rx=4, stroke=INK, sw=2)
    a.b += rect(-18, -14, 26, 3, "#d4a83a") + rect(-18, 11, 26, 3, "#d4a83a")
    v, w, h = vb(-32, 98, 32)
    write("turrets/railgun_mk2.svg", w, h, a, "railgun_mk2 turret: pivot at origin, barrel toward +x", viewbox=v,
          unpowered="turrets/railgun_mk2_unpowered.svg")

    # chaingun mk2: four barrels in a ring, gold-trimmed (an ace's refit)
    a = Art()
    base(a, 22)
    for dy in (-7.5, -2.5, 2.5, 7.5):
        a.b += rect(8, dy - 2, 54, 4, STEEL_DARK, rx=1.5, stroke=INK, sw=1.2)
    a.b += rect(46, -11, 6, 22, STEEL_MID, rx=2, stroke=INK, sw=1.5)
    a.b += rect(-14, -14, 24, 28, HOUSING, rx=4, stroke=INK, sw=2)
    a.b += rect(-12, -12, 20, 3, "#d4a83a") + rect(-12, 9, 20, 3, "#d4a83a")
    v, w, h = vb(-26, 66, 26)
    write("turrets/chaingun_mk2.svg", w, h, a, "chaingun_mk2 turret: pivot at origin, four barrels toward +x",
          viewbox=v)


# ---------------------------------------------------------------------------
# Crew (top-down, centered on the SVG origin, facing +y which is SVG up)
# ---------------------------------------------------------------------------

CREW = {
    # id: (suit, suit shade, trim, head covering, title)
    "crew_ship": ("#3f5a7a", "#2c4058", "#6aa8ff", "#2a2a30", "ship crew"),
    "crew_station": ("#c8641e", "#954914", HAZARD, "#e8e2d0", "station dock hand"),
    "crew_player": ("#2f7a6a", "#1f564a", "#5ee08a", "#5a3a22", "the player"),
}


def crew_body(a, suit, shade, trim, hair, carrying):
    """A crew member about 0.6 tile across (CrewBody radius 0.3 tile = 19.2 units)."""
    # arms: at the sides, or reaching forward to hold a crate
    for sx in (-1, 1):
        if carrying:
            a.b += path(f"M{f(sx*14)},{f(2)} L{f(sx*13)},{f(-12)} L{f(sx*10)},{f(-16)}",
                        stroke=INK, sw=8)
            a.b += path(f"M{f(sx*14)},{f(2)} L{f(sx*13)},{f(-12)} L{f(sx*10)},{f(-16)}",
                        stroke=shade, sw=5)
            a.b += circle(sx * 10, -16, 3, "#c99a78", stroke=INK, sw=1)
        else:
            a.b += rect(sx * 15 - 3.5, -6, 7, 16, shade, rx=3.5, stroke=INK, sw=1.5)
            a.b += circle(sx * 15, 9, 3, "#c99a78", stroke=INK, sw=1)
    # shoulders and torso
    a.b += path("M-17,4 C-17,-6 -10,-9 0,-9 C10,-9 17,-6 17,4 C17,10 10,12 0,12 C-10,12 -17,10 -17,4 Z",
                fill=suit, stroke=INK, sw=2)
    a.b += path("M-14,-3 C-10,-7 -4,-8 2,-8", stroke="#ffffff", sw=1.5, extra=' opacity="0.25"')
    # trim: shoulder flashes and a back pack
    a.b += rect(-17, -1, 6, 3, trim, rx=1) + rect(11, -1, 6, 3, trim, rx=1)
    a.b += rect(-7, 5, 14, 6, shade, rx=2, stroke=INK, sw=1.2)
    # head, toward +y (SVG up), with the face and nose at the front
    a.b += circle(0, -1, 7.5, "#c99a78", stroke=INK, sw=1.8)
    a.b += path("M-7.5,-1 C-7.5,4 -4,6.5 0,6.5 C4,6.5 7.5,4 7.5,-1 C7.5,-4 5,-6 0,-6 C-5,-6 -7.5,-4 -7.5,-1 Z",
                fill=hair, stroke=INK, sw=1.2)
    a.b += path("M-1.5,-8.5 L0,-10 L1.5,-8.5", stroke=INK, sw=1.2)


def crew():
    for name, (suit, shade, trim, hair, title) in CREW.items():
        a = Art()
        crew_body(a, suit, shade, trim, hair, False)
        a.m = collider_circle(0, 0, 19.2)
        write(f"crew/{name}.svg", 48, 48, a,
              f"{name}: {title}, top-down, centered on the origin, facing +y", viewbox="-24 -24 48 48")

        a = Art()
        crew_body(a, suit, shade, trim, hair, True)
        # crate held in front, light neutral grey so code can tint it
        a.b += rect(-11, -34, 22, 18, "#c4c7cc", rx=2, stroke=INK, sw=2)
        a.b += bevel(-10, -33, 20, 16, "#eef0f2", "#8a8e94")
        a.b += line(-11, -25, 11, -25, "#8a8e94", sw=1.5)
        a.b += line(-4, -34, -4, -16, "#a6aab0", sw=1.2) + line(4, -34, 4, -16, "#a6aab0", sw=1.2)
        a.m = collider_circle(0, 0, 19.2)
        write(f"crew/{name}_carrying.svg", 48, 60, a,
              f"{name}_carrying: {title} holding a crate in front (crate is neutral grey, tint in code)",
              viewbox="-24 -36 48 60")


SKIN, SKIN_PALE = "#c99a78", "#a99a92"
BLOOD, BLOOD_OLD = "#8e1414", "#5a0d0d"
BANDAGE = "#ece8de"


def grey(c, t):
    """`c` (#rrggbb) washed toward a cold grey by `t` (0 to 1): the pallor of the dead."""
    r, g, b = (int(c[i:i + 2], 16) for i in (1, 3, 5))
    r, g, b = (round(v + (122 - v) * t) for v in (r, g, b))
    return f"#{r:02x}{g:02x}{b:02x}"


def blood_pool(a, cx, cy, r, color, opacity):
    """An uneven pool of blood on the deck, centered on (cx, cy), about r across."""
    rng = random.Random(f"{cx},{cy},{r}")
    pts = []
    for i in range(14):
        ang = i / 14 * math.tau
        rr = r * (0.75 + 0.35 * rng.random())
        pts.append((cx + math.cos(ang) * rr * 1.15, cy + math.sin(ang) * rr * 0.9))
    a.b += poly(pts, color, extra=f' opacity="{f(opacity)}"')
    a.b += circle(cx + r * 0.9, cy + r * 0.55, r * 0.18, color, extra=f' opacity="{f(opacity)}"')


def crew_lying(a, suit, shade, trim, hair, skin, arms):
    """A crew member down on their back, head toward +y (SVG up), body behind it toward -y:
    about 0.95 tile long, centered on the chest so the figure lies where the crew member fell.
    `arms` is "sides" (laid down) or "flung" (limp, thrown out)."""
    # legs and boots, splayed when limp
    for sx in (-1, 1):
        turn = f' transform="rotate({f(-sx * 16)} {f(sx * 5.5)} 6)"' if arms == "flung" else ""
        a.b += rect(sx * 5.5 - 4, 6, 8, 20, shade, rx=4, stroke=INK, sw=1.5, extra=turn)
        a.b += rect(sx * 5.5 - 4.5, 23, 9, 7, "#22262c", rx=3, stroke=INK, sw=1.2, extra=turn)
    # arms under the torso's outline
    if arms == "flung":
        for sx, (ex, ey) in ((-1, (-26, 2)), (1, (24, -14))):
            d = f"M{f(sx*11)},{f(-6)} L{f(ex)},{f(ey)}"
            a.b += path(d, stroke=INK, sw=8.5) + path(d, stroke=shade, sw=5.5)
            a.b += circle(ex, ey, 3, skin, stroke=INK, sw=1)
    else:
        a.b += rect(-17.5, -8, 7, 17, shade, rx=3.5, stroke=INK, sw=1.5)
        a.b += circle(-14, 9.5, 3, skin, stroke=INK, sw=1)
        a.b += rect(10.5, -8, 7, 17, shade, rx=3.5, stroke=INK, sw=1.5)
        a.b += circle(14, 9.5, 3, skin, stroke=INK, sw=1)
    # torso, seen from the front, with its shoulder flashes and a belt
    a.b += rect(-11.5, -12, 23, 21, suit, rx=6, stroke=INK, sw=2)
    a.b += path("M-8,-10 C-4,-11.5 2,-11.5 6,-10.5", stroke="#ffffff", sw=1.5, extra=' opacity="0.22"')
    a.b += rect(-11, -9, 5, 3, trim, rx=1) + rect(6, -9, 5, 3, trim, rx=1)
    a.b += rect(-11.5, 4, 23, 3, shade, stroke=INK, sw=1)
    # head on the deck, face up: hair round the crown, eyes closed
    a.b += circle(0, -19, 7.5, skin, stroke=INK, sw=1.8)
    a.b += path("M-7.4,-17.5 C-7.8,-24 -4,-26.8 0,-26.8 C4,-26.8 7.8,-24 7.4,-17.5 "
                "C5.5,-22 3,-23.5 0,-23.5 C-3,-23.5 -5.5,-22 -7.4,-17.5 Z", fill=hair, stroke=INK, sw=1.2)
    a.b += line(-4.5, -18.5, -1.8, -18, INK, sw=1.1) + line(1.8, -18, 4.5, -18.5, INK, sw=1.1)
    a.b += line(-1.8, -14.5, 1.8, -14.5, INK, sw=1)


def blood(a, d, opacity=0.9, color=BLOOD):
    a.b += path(d, fill=color, extra=f' opacity="{f(opacity)}"')


def wound_overlays():
    """Wounds drawn over a crew figure at its origin and turned with it, by `Wound`: light, serious
    and critical (bleeding), and a critical wound once stabilised, dressed. `wound_<level>` fits the
    standing figure (seen from above: marks on the shoulders, back and left arm, clear of the head)
    and `wound_<level>_down` the figures lying on their back (marks on the chest and left arm)."""
    meta = collider_circle(0, 0, 19.2)

    def emit(level, down, a, title):
        a.m = meta
        name = f"wound_{level}" + ("_down" if down else "")
        write(f"crew/{name}.svg", 48, 48, a, f"{name}: {title}, drawn over a crew figure"
              + (" lying on their back" if down else " on their feet"), viewbox="-24 -24 48 48")

    def light(a):
        # a field dressing round the left arm, a graze on the right shoulder
        a.b += rect(-18.5, 0, 7, 4, BANDAGE, rx=1, stroke=INK, sw=1)
        a.b += line(-17.5, 2, -12.5, 2, "#c9c2b2", sw=0.8)
        blood(a, "M8,-8 C11,-9 14,-7 13,-4 C12,-2 9,-3 8,-5 Z", 0.85)
        a.b += circle(15, -2, 1.2, BLOOD, extra=' opacity="0.85"')

    def serious(a, down):
        if down:
            # a wound in the chest soaking the suit round it
            blood(a, "M-7,-4 C-4,-9 3,-9 6,-5 C9,-1 7,5 2,6 C-2,7 -5,5 -6,3 C-9,1 -9,-2 -7,-4 Z", 0.75)
            blood(a, "M-3,-3 C-1,-5 2,-4 2,-2 C3,0 1,2 -1,1 C-3,1 -4,-1 -3,-3 Z", 1.0, BLOOD_OLD)
        else:
            # through the right shoulder, soaking across the back
            blood(a, "M8,-7 C12,-9 16,-6 16,-1 C16,4 13,8 9,9 C5,10 3,7 5,3 C6,0 5,-4 8,-7 Z", 0.75)
            blood(a, "M10,-4 C12,-5 14,-3 13,-1 C12,1 10,1 9,-1 Z", 1.0, BLOOD_OLD)
            blood(a, "M-6,8 C-2,9 4,9 7,8 C6,11 2,12 -2,12 C-5,12 -7,10 -6,8 Z", 0.7)
        # blood down the left arm
        blood(a, "M-17,-4 C-15,-6 -13,-5 -13,-2 L-13.5,6 C-14,8 -16,8 -16.5,6 Z", 0.8)

    def soaked(a, down, color, opacity):
        if down:
            blood(a, "M-13,-8 C-8,-12 6,-12 12,-7 C16,-2 15,7 9,10 C4,12 -6,12 -10,9 "
                     "C-15,5 -16,-3 -13,-8 Z", opacity, color)
        else:
            # the suit round the head soaked: shoulders and back, a ring clear of the head
            blood(a, "M-16,0 C-16,-7 -10,-9 -4,-9 L-3,-6 C-6,-5 -9,-3 -9,1 C-9,5 -6,8 0,8 C6,8 9,5 9,1 "
                     "C9,-3 6,-5 3,-6 L4,-9 C10,-9 16,-7 16,0 C16,8 10,12 0,12 C-10,12 -16,8 -16,0 Z",
                  opacity, color)

    for down in (False, True):
        a = Art()
        light(a)
        emit("light", down, a, "a light wound")

        a = Art()
        serious(a, down)
        emit("serious", down, a, "a serious wound")

        # critical: the wound soaking the whole suit and running off it onto the deck
        a = Art()
        soaked(a, down, BLOOD, 0.6)
        serious(a, down)
        blood(a, "M10,6 C14,7 19,9 21,13 C19,15 15,14 13,12 C11,10 10,8 10,6 Z", 0.85)
        blood(a, "M-12,7 C-15,10 -19,13 -18,16 C-16,17 -13,14 -12,11 Z", 0.85)
        for cx, cy, r in ((19, -6, 1.6), (-20, -10, 1.2), (20, 3, 1.0), (-21, 4, 1.4)):
            a.b += circle(cx, cy, r, BLOOD, extra=' opacity="0.85"')
        emit("critical", down, a, "a critical wound bleeding out (blinked in code)")

        # stabilised: the wound packed and bound, the blood round it drying
        a = Art()
        soaked(a, down, BLOOD_OLD, 0.45)
        if down:
            a.b += rect(-12, -5, 24, 7, BANDAGE, rx=2, stroke=INK, sw=1.2)
            a.b += line(-11, -1.5, 11, -1.5, "#c9c2b2", sw=0.8)
            a.b += rect(-4, -7, 8, 11, BANDAGE, rx=1.5, stroke=INK, sw=1.2)
            blood(a, "M-2,-3 C0,-4 2,-3 2,-1 C2,1 0,1 -1,0 Z", 0.7)
        else:
            # a dressing strapped over the right shoulder and across the back
            a.b += path("M7,-8 L15,-4 L4,11 L-4,10 Z", fill=BANDAGE, stroke=INK, sw=1.2)
            a.b += line(10, -4, 1, 9, "#c9c2b2", sw=0.8)
            blood(a, "M9,-3 C11,-4 12,-2 11,0 C10,1 8,0 9,-3 Z", 0.7)
        a.b += rect(-18.5, 0, 7, 4, BANDAGE, rx=1, stroke=INK, sw=1)
        emit("stabilised", down, a, "a critical wound dressed and stabilised")


def crew_states():
    """A crew member down (unconscious, or with a disabling wound) and dead, for each look; a
    wound is drawn over the figure by `wound_overlays`."""
    for name, (suit, shade, trim, hair, title) in CREW.items():
        a = Art()
        crew_lying(a, suit, shade, trim, hair, SKIN, "sides")
        a.m = collider_circle(0, 0, 19.2)
        write(f"crew/{name}_down.svg", 60, 64, a,
              f"{name}_down: {title} down on their back, head toward +y", viewbox="-30 -32 60 64")

        a = Art()
        blood_pool(a, -8, 6, 21, BLOOD_OLD, 0.85)
        crew_lying(a, grey(suit, 0.45), grey(shade, 0.45), grey(trim, 0.6), grey(hair, 0.3), SKIN_PALE, "flung")
        a.m = collider_circle(0, 0, 19.2)
        write(f"crew/{name}_dead.svg", 60, 64, a,
              f"{name}_dead: {title}'s body where they fell, limbs thrown out, head toward +y",
              viewbox="-30 -32 60 64")
    wound_overlays()


def streak(a, gid, x0, x1, half, color):
    a.defs += (f'<linearGradient id="{gid}" x1="0" x2="1" y1="0" y2="0">'
               f'<stop offset="0" stop-color="{color}" stop-opacity="0"/>'
               f'<stop offset="1" stop-color="{color}" stop-opacity="0.85"/></linearGradient>')
    a.e += path(f"M{f(x0)},0 L{f(x1)},{f(-half)} L{f(x1)},{f(half)} Z", fill=f"url(#{gid})")


def projectiles():
    # railgun slug: 20 kg tungsten dart (about 0.4 m), with a long hot streak behind it
    a = Art()
    streak(a, "slug_streak", -160, -8, 3, "#9fe8ff")
    a.b += poly([(-12, -3.5), (6, -3.5), (13, 0), (6, 3.5), (-12, 3.5)], "#8d96a3", stroke=INK, sw=1.5)
    a.b += line(-10, -1.5, 8, -1.5, "#c9d1db", sw=1)
    a.e += poly([(-12, -3.5), (6, -3.5), (13, 0), (6, 3.5), (-12, 3.5)], "#bff2ff", extra=' opacity="0.35"')
    write("projectiles/railgun_slug.svg", 176, 16, a,
          "railgun_slug: tungsten slug, pivot at origin, nose toward +x, streak behind",
          viewbox="-162 -8 176 16")

    # autocannon round: 30 mm shell with a short tracer
    a = Art()
    streak(a, "tracer", -48, -3, 2.5, "#ffb347")
    a.b += rect(-6, -2, 6, 4, "#b08a3a", stroke=INK, sw=1)
    a.b += poly([(0, -2), (3, -2), (5, 0), (3, 2), (0, 2)], "#7d8898", stroke=INK, sw=1)
    a.e += circle(-6, 0, 2.5, "#ffe0a0")
    write("projectiles/autocannon_round.svg", 56, 10, a,
          "autocannon_round: 30 mm round with tracer, pivot at origin, nose toward +x",
          viewbox="-50 -5 56 10")

    # heavy railgun slug: 60 kg, fatter and with a longer, hotter streak
    a = Art()
    streak(a, "slug_heavy_streak", -220, -12, 5, "#9fe8ff")
    a.b += poly([(-18, -5), (8, -5), (18, 0), (8, 5), (-18, 5)], "#8d96a3", stroke=INK, sw=1.5)
    a.b += line(-16, -2, 10, -2, "#c9d1db", sw=1.2)
    a.e += poly([(-18, -5), (8, -5), (18, 0), (8, 5), (-18, 5)], "#bff2ff", extra=' opacity="0.4"')
    write("projectiles/railgun_slug_heavy.svg", 242, 22, a,
          "railgun_slug_heavy: 60 kg tungsten slug, pivot at origin, nose toward +x, streak behind",
          viewbox="-222 -11 242 22")

    # flak shell: a stubby shell with a banded fuse nose and a faint tracer
    a = Art()
    streak(a, "flak_tracer", -30, -4, 2.5, "#ffd27a")
    a.b += rect(-8, -3, 8, 6, "#8a7a3a", stroke=INK, sw=1)
    a.b += poly([(0, -3), (4, -3), (7, 0), (4, 3), (0, 3)], "#c0503a", stroke=INK, sw=1)
    a.b += line(2, -3, 2, 3, HAZARD, sw=1)
    write("projectiles/flak_shell.svg", 40, 12, a,
          "flak_shell: proximity-fused shell with tracer, pivot at origin, nose toward +x",
          viewbox="-32 -6 40 12")

    # ion bolt: a crackling ball of blue light with a short tail; all emissive
    a = Art()
    a.defs += glow_def("ion_glow", "#7aa8ff") + halo_def("ion_halo", "#3a6aff")
    a.e += path("M-40,0 L-4,-4 L-4,4 Z", fill="#7aa8ff", extra=' opacity="0.45"')
    a.e += circle(0, 0, 12, "url(#ion_halo)")
    a.e += circle(0, 0, 6, "url(#ion_glow)")
    rnd = random.Random(44)
    for i in range(5):
        ang = rnd.uniform(0, 2 * math.pi)
        r0, r1 = 4, rnd.uniform(8, 11)
        a.e += line(r0 * math.cos(ang), r0 * math.sin(ang), r1 * math.cos(ang), r1 * math.sin(ang), "#e0ecff", sw=1)
    a.e += circle(0, 0, 2.5, "#ffffff")
    write("projectiles/ion_bolt.svg", 56, 28, a,
          "ion_bolt: ball of ion light with a short tail, pivot at origin, moving toward +x",
          viewbox="-42 -14 56 28")

    # missile: body in base, exhaust in emissive
    a = Art()
    a.defs += glow_def("missile_exhaust", "#ff9a3c")
    a.e += path("M-30,-5 C-50,-6 -70,-3 -86,0 C-70,3 -50,6 -30,5 Z", fill="#ff9a3c", extra=' opacity="0.55"')
    a.e += path("M-30,-3 C-42,-3 -52,-1.5 -60,0 C-52,1.5 -42,3 -30,3 Z", fill="#ffe58a")
    a.e += circle(-32, 0, 9, "url(#missile_exhaust)")
    for sy in (-1, 1):
        a.b += poly([(-28, sy * 5), (-20, sy * 5), (-26, sy * 12), (-31, sy * 12)], STEEL_DARK, stroke=INK, sw=1.5)
        a.b += poly([(6, sy * 5), (12, sy * 5), (9, sy * 9), (5, sy * 9)], STEEL_DARK, stroke=INK, sw=1.2)
    a.b += path("M-30,-5 L18,-5 C26,-5 32,-2 34,0 C32,2 26,5 18,5 L-30,5 Z", fill="#c9ced6", stroke=INK, sw=2)
    a.b += line(-28, -2.5, 20, -2.5, "#eef0f2", sw=1.2)
    a.b += rect(18, -5, 3, 10, "#ff5a5a") + rect(-16, -5, 3, 10, HAZARD)
    a.b += rect(-32, -4, 3, 8, HOUSING, stroke=INK, sw=1)
    write("projectiles/missile.svg", 124, 28, a,
          "missile: body in base, exhaust in emissive; pivot at origin, nose toward +x",
          viewbox="-88 -14 124 28")

    # decoy flare: a small casing with a hot ball of light and a sparking trail
    a = Art()
    a.defs += glow_def("flare_glow", "#ffd27a") + halo_def("flare_halo", "#ff8a3c")
    a.e += circle(0, 0, 22, "url(#flare_halo)")
    rnd = random.Random(21)
    for i in range(7):
        x = -8 - i * 6 + rnd.uniform(-2, 2)
        a.e += circle(x, rnd.uniform(-3, 3), 2.2 - i * 0.22, "#ffd27a", extra=f' opacity="{f(0.9 - i * 0.11)}"')
    a.b += rect(-4, -2.5, 8, 5, STEEL_DARK, rx=1.5, stroke=INK, sw=1)
    a.e += circle(1, 0, 8, "url(#flare_glow)")
    write("projectiles/decoy_flare.svg", 72, 48, a,
          "decoy_flare: burning flare, pivot at origin, moving toward +x, sparks behind",
          viewbox="-48 -24 72 48")

    # harpoon: barbed head on a shaft, with the tow cable trailing behind
    a = Art()
    a.b += path("M-70,0 C-60,-3 -52,3 -42,0 C-36,-2 -32,1 -28,0", stroke="#2a2a30", sw=3)
    a.b += path("M-70,0 C-60,-3 -52,3 -42,0 C-36,-2 -32,1 -28,0", stroke="#6b6f78", sw=1.2)
    a.b += rect(-28, -3, 34, 6, STEEL_MID, rx=1.5, stroke=INK, sw=1.5)
    a.b += rect(-28, -3, 6, 6, HOUSING, stroke=INK, sw=1)
    for sy in (-1, 1):
        a.b += poly([(12, sy * 2), (4, sy * 10), (8, sy * 2)], STEEL_HI, stroke=INK, sw=1.2)
    a.b += poly([(4, -4), (18, -4), (28, 0), (18, 4), (4, 4)], "#9aa4b2", stroke=INK, sw=1.5)
    a.b += line(6, -1.5, 20, -1.5, "#d6dce4", sw=1)
    write("projectiles/harpoon.svg", 102, 24, a,
          "harpoon: barbed harpoon with tow cable trailing, pivot at origin, nose toward +x",
          viewbox="-72 -12 102 24")


# ---------------------------------------------------------------------------
# Explosion frames and laser beam
# ---------------------------------------------------------------------------

def explosion_frame(n):
    """Frame n (1..4) of a burst about two tiles across, centered on the origin."""
    a = Art()
    rnd = random.Random(30)
    a.defs += glow_def("boom_core", "#ffb347") + halo_def("boom_halo", "#ff5a1f")
    blobs = [(rnd.uniform(0, 2 * math.pi), rnd.uniform(0.3, 0.8), rnd.uniform(0.35, 0.55)) for _ in range(9)]
    scale = {1: 22, 2: 40, 3: 54, 4: 60}[n]
    if n >= 2:  # smoke in the base layer, growing and thinning
        op = {2: 0.35, 3: 0.55, 4: 0.45}[n]
        for (ang, d, r) in blobs:
            a.b += circle(math.cos(ang) * d * scale, math.sin(ang) * d * scale, r * scale, "#2b2d31",
                          extra=f' opacity="{f(op)}"')
    if n <= 3:
        fire = {1: 1.0, 2: 0.9, 3: 0.55}[n]
        a.e += circle(0, 0, scale * 1.1, "url(#boom_halo)")
        for (ang, d, r) in blobs:
            if n == 3 and d > 0.6:
                continue
            cx, cy = math.cos(ang) * d * scale * 0.7, math.sin(ang) * d * scale * 0.7
            a.e += circle(cx, cy, r * scale * 0.9, "#ff5a1f", extra=f' opacity="{f(fire * 0.8)}"')
            a.e += circle(cx, cy, r * scale * 0.55, "#ffa62b", extra=f' opacity="{f(fire)}"')
        a.e += circle(0, 0, scale * (0.55 if n == 1 else 0.4), "url(#boom_core)")
    if n <= 2:  # debris sparks
        for i in range(12):
            ang = 2 * math.pi * i / 12 + rnd.uniform(-0.2, 0.2)
            r0, r1 = scale * 0.6, scale * (1.0 + rnd.uniform(0, 0.25))
            a.e += line(math.cos(ang) * r0, math.sin(ang) * r0, math.cos(ang) * r1, math.sin(ang) * r1,
                        "#ffe58a", sw=1.5)
    if n == 4:
        for (ang, d, r) in blobs[:3]:
            a.e += circle(math.cos(ang) * d * 20, math.sin(ang) * d * 20, 3, "#ff9a3c", extra=' opacity="0.6"')
    return a


def blasts():
    for n in range(1, 5):
        write(f"effects/explosion_{n}.svg", 128, 128, explosion_frame(n),
              f"explosion_{n}: frame {n} of 4 of an explosion, centered on the origin", viewbox="-64 -64 128 128")
    write("effects/explosion.svg", 128, 128, explosion_frame(2),
          "explosion: single-frame explosion (same as explosion_2), centered on the origin",
          viewbox="-64 -64 128 128")

    a = Art()
    a.defs += ('<linearGradient id="beam_glow" x1="0" x2="0" y1="0" y2="1">'
               '<stop offset="0" stop-color="#ff5a5a" stop-opacity="0"/>'
               '<stop offset="0.5" stop-color="#ff5a5a" stop-opacity="0.75"/>'
               '<stop offset="1" stop-color="#ff5a5a" stop-opacity="0"/></linearGradient>')
    a.e += rect(0, 0, 64, 16, "url(#beam_glow)")
    a.e += rect(0, 5.5, 64, 5, "#ff8a8a")
    a.e += rect(0, 7, 64, 2, "#ffe6e6")
    write("effects/beam.svg", 64, 16, a, "beam: laser beam strip, stretch along x to the beam's length")


def dock_walkway():
    """Gangway grating between two latched collars; uniform along y so it stretches or tiles."""
    a = Art()
    a.b = rect(0, 0, T, T, "#1b2027")
    a.b += rect(8, 0, 48, T, STEEL_DARK)
    for yy in range(0, T, 8):
        a.b += rect(10, yy + 1, 44, 5, STEEL_MID, rx=1)
        a.b += line(10, yy + 1.5, 54, yy + 1.5, STEEL_HI, sw=0.8, extra=' opacity="0.6"')
    a.b += rect(2, 0, 6, T, HOUSING) + line(2, 0, 2, T, INK, sw=1.5) + line(8, 0, 8, T, INK, sw=1.5)
    a.b += rect(56, 0, 6, T, HOUSING) + line(56, 0, 56, T, INK, sw=1.5) + line(62, 0, 62, T, INK, sw=1.5)
    a.b += line(4, 0, 4, T, HOUSING_HI, sw=1) + line(58, 0, 58, T, HOUSING_HI, sw=1)
    a.b += rect(4, 0, 2, T, HAZARD, extra=' opacity="0.8"') + rect(58, 0, 2, T, HAZARD, extra=' opacity="0.8"')
    write("tiles/dock_walkway.svg", T, T, a,
          "dock_walkway: gangway between two latched collars; uniform along y, stretch or tile it to the gap")


def main():
    if os.path.isdir(OUT):
        for sub in os.listdir(OUT):
            if os.path.isdir(os.path.join(OUT, sub)):
                shutil.rmtree(os.path.join(OUT, sub))
    floors()
    walls()
    edges()
    edge_conduits()
    pipes()
    components()
    effects()
    decor()
    # The furnishings yards sell, drawn by their own script
    from furnishing_svgs import furnishings
    furnishings(os.path.join(OUT, "decor"))
    interactive()
    hulls()
    turrets()
    engines()
    rcs()
    crew()
    crew_states()
    projectiles()
    blasts()
    dock_walkway()
    print(f"wrote {len(written)} SVGs to {os.path.relpath(OUT, ROOT)}")


if __name__ == "__main__":
    main()

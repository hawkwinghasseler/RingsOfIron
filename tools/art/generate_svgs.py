#!/usr/bin/env python3
"""Generates the first pass of the SVG asset pack in assets/art.

The SVG files are the source art: open and edit them in any vector editor. This script
only exists to draw the many sizes of each component consistently. Re-running it
overwrites every file it writes, so once a file has been edited by hand, remove it from
this script (or stop running the script). See documentation/art/Design.md for the style.

    python3 tools/art/generate_svgs.py
"""
import math
import os
import random
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
POWER = "#f2b134"
HAZARD = "#f2c230"

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
}

SIZES = ["s", "m", "l", "xl"]
PRIMARY = {"s": (1, 1), "m": (2, 2), "l": (3, 3), "xl": (5, 5)}
AUX = {"s": (1, 2), "m": (2, 3), "l": (3, 7), "xl": (5, 12)}
PORT = {"s": (3, 1), "m": (5, 2), "l": (7, 3)}

written = []


def f(v):
    """Short number formatting for SVG attributes."""
    v = round(v, 2)
    return str(int(v)) if v == int(v) else str(v)


def write(path, w, h, body, title, defs="", viewbox=None):
    full = os.path.join(OUT, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    vb = viewbox or f"0 0 {f(w)} {f(h)}"
    svg = (f'<svg xmlns="http://www.w3.org/2000/svg" width="{f(w)}" height="{f(h)}" viewBox="{vb}">\n'
           f'  <title>{title}</title>\n')
    if defs:
        svg += f"  <defs>{defs}</defs>\n"
    svg += body + "</svg>\n"
    with open(full, "w", newline="\n") as fh:
        fh.write(svg)
    written.append(path)


def uid(path):
    return path.replace("/", "_").replace(".svg", "")


# ---------------------------------------------------------------------------
# Primitives
# ---------------------------------------------------------------------------

def rect(x, y, w, h, fill, rx=0, stroke=None, sw=2, extra=""):
    s = f' stroke="{stroke}" stroke-width="{f(sw)}"' if stroke else ""
    r = f' rx="{f(rx)}"' if rx else ""
    return f'  <rect x="{f(x)}" y="{f(y)}" width="{f(w)}" height="{f(h)}"{r} fill="{fill}"{s}{extra}/>\n'


def circle(cx, cy, r, fill, stroke=None, sw=2, extra=""):
    s = f' stroke="{stroke}" stroke-width="{f(sw)}"' if stroke else ""
    return f'  <circle cx="{f(cx)}" cy="{f(cy)}" r="{f(r)}" fill="{fill}"{s}{extra}/>\n'


def line(x1, y1, x2, y2, stroke, sw=2, extra=""):
    return (f'  <line x1="{f(x1)}" y1="{f(y1)}" x2="{f(x2)}" y2="{f(y2)}" stroke="{stroke}" '
            f'stroke-width="{f(sw)}" stroke-linecap="round"{extra}/>\n')


def poly(points, fill, stroke=None, sw=2, extra=""):
    pts = " ".join(f"{f(x)},{f(y)}" for x, y in points)
    s = f' stroke="{stroke}" stroke-width="{f(sw)}" stroke-linejoin="round"' if stroke else ""
    return f'  <polygon points="{pts}" fill="{fill}"{s}{extra}/>\n'


def path(d, fill="none", stroke=None, sw=2, extra=""):
    s = f' stroke="{stroke}" stroke-width="{f(sw)}" stroke-linejoin="round" stroke-linecap="round"' if stroke else ""
    return f'  <path d="{d}" fill="{fill}"{s}{extra}/>\n'


def bevel(x, y, w, h, hi, lo, sw=1.5):
    """Top-left highlight and bottom-right shadow just inside a rectangle (light from top-left)."""
    o = sw / 2
    return (path(f"M{f(x+o)},{f(y+h-o)} L{f(x+o)},{f(y+o)} L{f(x+w-o)},{f(y+o)}", stroke=hi, sw=sw)
            + path(f"M{f(x+w-o)},{f(y+o)} L{f(x+w-o)},{f(y+h-o)} L{f(x+o)},{f(y+h-o)}", stroke=lo, sw=sw))


def plate(x, y, w, h, fill, hi, lo, rx=2):
    return rect(x, y, w, h, fill, rx=rx, stroke=INK, sw=2) + bevel(x + 1, y + 1, w - 2, h - 2, hi, lo)


def rivet(x, y, r=1.6, fill=STEEL_HI):
    return circle(x, y, r, fill, extra=' opacity="0.9"')


def hazard_band(x, y, w, h, uid_, horizontal=True):
    """Yellow and black diagonal stripes clipped to a rectangle."""
    cid = f"{uid_}_hz{int(x)}_{int(y)}"
    stripes = ""
    step = 8
    span = int(w + h) + step
    for i in range(-int(h) - step, span, step * 2):
        stripes += poly([(x + i, y + h), (x + i + step, y + h), (x + i + step + h, y), (x + i + h, y)], INK)
    return (f'  <clipPath id="{cid}"><rect x="{f(x)}" y="{f(y)}" width="{f(w)}" height="{f(h)}"/></clipPath>\n'
            + rect(x, y, w, h, HAZARD)
            + f'  <g clip-path="url(#{cid})">\n{stripes}  </g>\n'
            + rect(x, y, w, h, "none", stroke=INK, sw=1.5))


def glow(gid, color):
    return (f'<radialGradient id="{gid}"><stop offset="0" stop-color="#ffffff" stop-opacity="0.95"/>'
            f'<stop offset="0.35" stop-color="{color}" stop-opacity="0.9"/>'
            f'<stop offset="1" stop-color="{color}" stop-opacity="0"/></radialGradient>')


def screen(x, y, w, h, color):
    """A glowing console screen with a few lines of readout."""
    s = rect(x, y, w, h, GLASS, rx=1.5, stroke=INK, sw=1.5)
    s += rect(x + 1.5, y + 1.5, w - 3, h - 3, color, rx=1, extra=' opacity="0.22"')
    rows = max(1, int((h - 4) // 4))
    for i in range(rows):
        ly = y + 3.5 + i * 4
        lw = (w - 6) * (0.45 + 0.5 * ((i * 37) % 7) / 7)
        s += line(x + 3, ly, x + 3 + lw, ly, color, sw=1.2, extra=' opacity="0.9"')
    return s


def collider_rect(x, y, w, h):
    return f'  <rect id="collider" x="{f(x)}" y="{f(y)}" width="{f(w)}" height="{f(h)}" fill="none" stroke="none"/>\n'


def collider_circle(cx, cy, r):
    return f'  <circle id="collider" cx="{f(cx)}" cy="{f(cy)}" r="{f(r)}" fill="none" stroke="none"/>\n'


def interact_point(x, y):
    return f'  <circle id="interact" cx="{f(x)}" cy="{f(y)}" r="2" fill="none" stroke="none"/>\n'


# ---------------------------------------------------------------------------
# Tiles
# ---------------------------------------------------------------------------

def deck(x=0, y=0, seed=0):
    """One steel deck plate tile at (x, y)."""
    s = rect(x, y, T, T, DECK_SEAM)
    s += rect(x + 1, y + 1, T - 2, T - 2, DECK, rx=2)
    s += rect(x + 4, y + 4, T - 8, T - 8, DECK_PLATE, rx=2)
    s += bevel(x + 4, y + 4, T - 8, T - 8, DECK_HI, DECK_SEAM)
    # tread: short diagonal ridges
    rnd = random.Random(seed)
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


def tiles():
    write("tiles/floor.svg", T, T, deck(), "floor: steel deck plate")

    # Power conduit: a deck plate with a junction box; arms are overlays drawn toward
    # each conduit neighbor (see conduit_arm).
    s = deck()
    s += rect(20, 20, 24, 24, HOUSING, rx=3, stroke=INK)
    s += bevel(21, 21, 22, 22, HOUSING_HI, INK)
    s += circle(32, 32, 6, POWER, stroke=INK, sw=1.5)
    s += circle(32, 32, 2.5, "#fff3c4")
    write("tiles/conduit.svg", T, T, s, "conduit: deck plate with a power junction")

    s = rect(26, 0, 12, 22, "#20262e", stroke=INK, sw=1.5)
    s += rect(29.5, 0, 5, 22, POWER)
    s += line(32, 0, 32, 22, "#fff3c4", sw=1.2, extra=' opacity="0.7"')
    for yy in (5, 14):
        s += rect(26, yy, 12, 2.5, STEEL_DARK)
    write("tiles/conduit_arm.svg", T, T, s,
          "conduit arm overlay: runs from the junction to the top edge; rotate toward each conduit neighbor")

    # Hull walls by weight class: thicker plating and heavier bolts as they get heavier.
    for weight, base, plate_fill, inset, bolt in (
            ("light", "#4a5462", "#6a7586", 3, 1.6),
            ("medium", "#3d4552", "#5b6675", 5, 2.2),
            ("heavy", "#2f3641", "#4b5563", 7, 2.8)):
        s = rect(0, 0, T, T, base)
        s += plate(inset, inset, T - 2 * inset, T - 2 * inset, plate_fill, STEEL_HI, INK, rx=3)
        if weight == "light":
            s += line(inset + 6, 32, T - inset - 6, 32, STEEL_DARK, sw=1.5)
        if weight == "medium":
            for yy in (24, 40):
                s += line(inset + 4, yy, T - inset - 4, yy, STEEL_DARK, sw=2)
                s += line(inset + 4, yy + 1.5, T - inset - 4, yy + 1.5, STEEL_HI, sw=1, extra=' opacity="0.6"')
        if weight == "heavy":
            s += rect(22, 22, 20, 20, plate_fill, rx=2, stroke=INK, sw=2)
            s += bevel(23, 23, 18, 18, STEEL_HI, INK)
        for (bx, by) in ((inset + 6, inset + 6), (T - inset - 6, inset + 6),
                         (inset + 6, T - inset - 6), (T - inset - 6, T - inset - 6)):
            s += circle(bx, by, bolt, STEEL_HI, stroke=INK, sw=1)
        write(f"tiles/hull_{weight}.svg", T, T, s, f"hull_{weight}: outer hull wall, {weight} weight class")

        # Wedge hull pieces: right triangle with legs on the left and bottom edges,
        # sloped face toward +x/+y (up and right on screen).
        for (bw, bh) in ((1, 1), (1, 2)):
            w, h = bw * T, bh * T
            tri = [(0, 0), (0, h), (w, h)]
            ins = inset * 0.8
            # inner triangle: shrink toward the incenter
            a, b, c = w, h, math.hypot(w, h)
            ix, iy = (a * 0 + b * w + c * 0) / (a + b + c), (a * 0 + b * h + c * h) / (a + b + c)
            k = 1 - ins / ((a + b - c) / 2)
            inner = [(ix + (px - ix) * k, iy + (py - iy) * k) for px, py in tri]
            s = poly(tri, base, stroke=INK, sw=2)
            s += poly(inner, plate_fill, stroke=INK, sw=2)
            # highlight along the sloped face (it catches the light)
            s += line(inner[0][0] + 2, inner[0][1] + 5, inner[2][0] - 5, inner[2][1] - 2, STEEL_HI, sw=2)
            s += circle(inner[1][0] + 6, inner[1][1] - 6, bolt, STEEL_HI, stroke=INK, sw=1)
            write(f"tiles/hull_{weight}_wedge_{bw}x{bh}.svg", w, h, s,
                  f"hull_{weight} wedge {bw}x{bh}: sloped face toward +x/+y at rotation 0")

    # Internal wall: a lighter bulkhead block with a structural beam.
    s = rect(0, 0, T, T, "#545e6c")
    s += plate(4, 4, T - 8, T - 8, "#68737f", "#8b96a4", INK, rx=2)
    s += rect(4, 26, T - 8, 12, "#4a5361", stroke=INK, sw=1.5)
    for xx in range(9, 52, 10):
        s += line(xx, 28, xx + 6, 36, "#3a414c", sw=1.5)
    for (bx, by) in ((10, 10), (54, 10), (10, 54), (54, 54)):
        s += rivet(bx, by, 1.8, "#9aa5b3")
    write("tiles/internal_wall.svg", T, T, s, "internal_wall: interior bulkhead tile")

    # Carpet covering: tileable, laid over the deck.
    s = rect(0, 0, T, T, "#5a2a33")
    for yy in range(2, T, 4):
        s += line(0, yy, T, yy, "#6b333d", sw=1.2)
    for xx in range(0, T, 16):
        s += line(xx, 0, xx, T, "#4a2229", sw=0.8, extra=' opacity="0.6"')
    s += rect(0, 0, T, T, "none", stroke="#3d1b21", sw=1)
    write("tiles/covering_carpet.svg", T, T, s, "covering_carpet: carpet laid over the deck (fire fuel)")


# ---------------------------------------------------------------------------
# Edge walls and doors (drawn centered on a tile edge)
# ---------------------------------------------------------------------------

def edges():
    H = 16  # canvas height; the tile edge runs along y = 8
    for weight, thick, fill in (("light", 6, "#6a7586"), ("medium", 9, "#5b6675"), ("heavy", 12, "#4b5563")):
        y0 = 8 - thick / 2
        s = rect(0, y0, T, thick, fill, rx=1.5, stroke=INK, sw=1.5)
        s += line(2, y0 + 1.5, T - 2, y0 + 1.5, STEEL_HI, sw=1)
        for xx in (8, 32, 56):
            s += rivet(xx, 8, 1.3)
        write(f"edges/edge_wall_{weight}.svg", T, H, s,
              f"edge_wall_{weight}: thin wall on a tile edge (edge along y=8, rotate 90 for vertical edges)")

    def door(state):
        s = ""
        # frame posts at each end
        s += rect(0, 2, 8, 12, STEEL_MID, rx=1, stroke=INK, sw=1.5)
        s += rect(T - 8, 2, 8, 12, STEEL_MID, rx=1, stroke=INK, sw=1.5)
        if state == "sealed":
            s += rect(8, 4, 24, 8, "#7a8594", stroke=INK, sw=1.5)
            s += rect(32, 4, 24, 8, "#7a8594", stroke=INK, sw=1.5)
            s += hazard_band(10, 6.5, 20, 3, f"door_{state}_a")
            s += hazard_band(34, 6.5, 20, 3, f"door_{state}_b")
            light = "#ff4d4d"
        else:
            s += rect(8, 4, 5, 8, "#7a8594", stroke=INK, sw=1.5)
            s += rect(T - 13, 4, 5, 8, "#7a8594", stroke=INK, sw=1.5)
            light = "#4dff88"
        s += circle(4, 8, 2, light, stroke=INK, sw=1)
        s += circle(T - 4, 8, 2, light, stroke=INK, sw=1)
        return s
    write("edges/bulkhead_door_sealed.svg", T, H, door("sealed"), "bulkhead door in an edge wall, sealed")
    write("edges/bulkhead_door_open.svg", T, H, door("open"), "bulkhead door in an edge wall, open")


# ---------------------------------------------------------------------------
# Components
# ---------------------------------------------------------------------------

def housing(w, h, inset=4):
    return (rect(0, 0, w, h, DECK_SEAM)
            + plate(inset, inset, w - 2 * inset, h - 2 * inset, HOUSING, HOUSING_HI, INK, rx=4))


def reactor(size):
    n = PRIMARY[size][0]
    w = h = n * T
    u = uid(f"reactor_{size}")
    c = ACCENT["reactor"]
    defs = glow(f"{u}_g", c)
    s = housing(w, h)
    cx = cy = w / 2
    R = w * 0.36
    # coolant pipes to the corners
    for (px, py) in ((10, 10), (w - 10, 10), (10, h - 10), (w - 10, h - 10)):
        s += line(cx, cy, px, py, STEEL_DARK, sw=max(4, n * 3))
        s += line(cx, cy, px, py, STEEL_MID, sw=max(2, n * 1.5))
        s += circle(px, py, max(3, n * 2), STEEL_MID, stroke=INK, sw=1.5)
    s += circle(cx, cy, R, STEEL_DARK, stroke=INK, sw=2)
    s += circle(cx, cy, R * 0.8, HOUSING, stroke=INK, sw=1.5)
    rods = 6 if n < 3 else (8 if n < 5 else 12)
    if n > 1:
        for i in range(rods):
            a = 2 * math.pi * i / rods
            s += circle(cx + math.cos(a) * R * 0.9, cy + math.sin(a) * R * 0.9, R * 0.07, c, stroke=INK, sw=1)
    s += circle(cx, cy, R * 0.6, f"url(#{u}_g)")
    s += circle(cx, cy, R * 0.5, "none", stroke=c, sw=1.5, extra=' opacity="0.8"')
    s += circle(cx, cy, R * 0.22, "#fff1d6", stroke=c, sw=1.5)
    return w, h, s, defs


def shield_emitter(size):
    n = PRIMARY[size][0]
    w = h = n * T
    u = uid(f"shield_emitter_{size}")
    c = ACCENT["shield_emitter"]
    defs = glow(f"{u}_g", c)
    s = housing(w, h)
    cx = cy = w / 2
    R = w * 0.38
    hexagon = [(cx + R * math.cos(math.pi / 6 + i * math.pi / 3), cy + R * math.sin(math.pi / 6 + i * math.pi / 3))
               for i in range(6)]
    s += poly(hexagon, STEEL_DARK, stroke=INK, sw=2)
    # emitter prongs at the hex corners
    for (px, py) in hexagon:
        s += circle(px, py, max(2.5, R * 0.1), c, stroke=INK, sw=1.2)
    s += circle(cx, cy, R * 0.62, HOUSING, stroke=INK, sw=1.5)
    s += circle(cx, cy, R * 0.55, f"url(#{u}_g)")
    for k in (0.42, 0.28):
        s += circle(cx, cy, R * k, "none", stroke=c, sw=1.2, extra=' opacity="0.85"')
    s += circle(cx, cy, R * 0.1, "#e9fbff")
    return w, h, s, defs


def turret_mount(size):
    n = PRIMARY[size][0]
    w = h = n * T
    c = ACCENT["turret_mount"]
    s = housing(w, h)
    cx = cy = w / 2
    R = w * 0.4
    s += circle(cx, cy, R, STEEL_DARK, stroke=INK, sw=2)
    bolts = 8 if n < 3 else 16
    for i in range(bolts):
        a = 2 * math.pi * i / bolts
        s += circle(cx + math.cos(a) * R * 0.88, cy + math.sin(a) * R * 0.88, max(1.4, R * 0.04), STEEL_HI)
    s += circle(cx, cy, R * 0.74, STEEL_MID, stroke=INK, sw=1.5)
    # ammunition / power feed toward the bottom (into the ship), breech toward the top
    s += rect(cx - R * 0.16, cy, R * 0.32, R * 0.95, HOUSING, stroke=INK, sw=1.5)
    s += rect(cx - R * 0.3, cy - R * 0.55, R * 0.6, R * 0.7, HOUSING_HI, rx=2, stroke=INK, sw=1.5)
    s += poly([(cx, cy - R * 0.72), (cx - R * 0.14, cy - R * 0.5), (cx + R * 0.14, cy - R * 0.5)], c,
              stroke=INK, sw=1)
    s += circle(cx, cy + R * 0.45, R * 0.07, c)
    return w, h, s, ""


def console_row(x, y, w, color, seat=True):
    """One crew station facing +y (up): a desk with a screen at the top, a chair below."""
    s = rect(x + 4, y + 4, w - 8, 16, HOUSING, rx=2, stroke=INK, sw=1.5)
    s += screen(x + 9, y + 6, w - 18, 10, color)
    s += rect(x + 6, y + 20, w - 12, 4, HOUSING_HI, stroke=INK, sw=1)
    if seat:
        s += rect(x + w / 2 - 9, y + 32, 18, 16, "#2b2f36", rx=5, stroke=INK, sw=1.5)
        s += rect(x + w / 2 - 9, y + 44, 18, 6, "#1e2227", rx=2, stroke=INK, sw=1.2)
        s += rect(x + w / 2 - 12, y + 34, 3, 10, "#2b2f36", rx=1.5, stroke=INK, sw=1)
        s += rect(x + w / 2 + 9, y + 34, 3, 10, "#2b2f36", rx=1.5, stroke=INK, sw=1)
    return s


def floor_area(w, h, x0=0, y0=0):
    s = ""
    for ty in range(int(h // T)):
        for tx in range(int(w // T)):
            s += deck(x0 + tx * T, y0 + ty * T, seed=tx * 7 + ty)
    return s


def holotable(cx, cy, r, color):
    s = circle(cx, cy, r, HOUSING, stroke=INK, sw=2)
    s += circle(cx, cy, r * 0.8, GLASS, stroke=INK, sw=1)
    s += circle(cx, cy, r * 0.8, color, extra=' opacity="0.18"')
    for k in (0.55, 0.3):
        s += circle(cx, cy, r * k, "none", stroke=color, sw=1, extra=' opacity="0.8"')
    s += line(cx - r * 0.7, cy, cx + r * 0.7, cy, color, sw=0.8, extra=' opacity="0.6"')
    s += line(cx, cy - r * 0.7, cx, cy + r * 0.7, color, sw=0.8, extra=' opacity="0.6"')
    return s


def station_console(kind, size):
    """Helm and weapons consoles: crew stations facing +y with floor behind them."""
    tw, th = AUX[size]
    w, h = tw * T, th * T
    c = ACCENT[kind]
    s = floor_area(w, h)
    s += rect(0, 0, w, h, "none", stroke=INK, sw=2)
    # a console bank along the top edge, one station per tile column
    for i in range(tw):
        s += console_row(i * T, 0, T, c)
    if th >= 3:
        # second row of stations for bigger rooms, and a holotable or plot in the middle
        if th >= 7:
            for i in range(tw):
                if tw >= 3 and i == tw // 2:
                    continue
                s += console_row(i * T, 2 * T, T, c)
            s += holotable(w / 2, h * 0.62, min(w, h) * 0.3, c)
        else:
            s += holotable(w / 2, h - T * 0.9, min(w * 0.35, T * 0.7), c)
    if kind == "weapons":
        # targeting reticles on the screens
        for i in range(tw):
            s += circle(i * T + 32, 11, 3, "none", stroke=c, sw=1)
    return w, h, s, ""


def life_support(size):
    tw, th = AUX[size]
    w, h = tw * T, th * T
    c = ACCENT["life_support"]
    s = housing(w, h)
    # one oxygen tank and one scrubber fan per tile, alternating down the slot
    for ty in range(th):
        for tx in range(tw):
            x, y = tx * T, ty * T
            if (tx + ty) % 2 == 0:
                s += rect(x + 14, y + 8, 36, 48, "#2a4a38", rx=16, stroke=INK, sw=2)
                s += rect(x + 18, y + 12, 8, 40, "#4e8a66", rx=4)
                s += circle(x + 32, y + 32, 6, c, stroke=INK, sw=1.5)
                s += rect(x + 29, y + 4, 6, 6, STEEL_MID, stroke=INK, sw=1)
            else:
                s += circle(x + 32, y + 32, 22, STEEL_DARK, stroke=INK, sw=2)
                for i in range(6):
                    a = 2 * math.pi * i / 6
                    s += path(f"M{f(x+32)},{f(y+32)} Q{f(x+32+math.cos(a+0.6)*18)},{f(y+32+math.sin(a+0.6)*18)} "
                              f"{f(x+32+math.cos(a+1.0)*19)},{f(y+32+math.sin(a+1.0)*19)}",
                              stroke=STEEL_HI, sw=3)
                s += circle(x + 32, y + 32, 5, c, stroke=INK, sw=1.2)
            if ty > 0:
                s += line(x + 32, y - 4, x + 32, y + 6, c, sw=2, extra=' opacity="0.7"')
    return w, h, s, ""


def cargo(size):
    tw, th = AUX[size]
    w, h = tw * T, th * T
    c = ACCENT["cargo"]
    s = floor_area(w, h)
    # bay floor markings: a yellow boundary line and a tie-down ring on every tile
    s += rect(3, 3, w - 6, h - 6, "none", stroke=HAZARD, sw=2.5, extra=' stroke-dasharray="10 6"')
    for ty in range(th):
        for tx in range(tw):
            x, y = tx * T + 32, ty * T + 32
            for (dx, dy) in ((-20, -20), (20, -20), (-20, 20), (20, 20)):
                s += circle(x + dx, y + dy, 3, "none", stroke=c, sw=1.5)
    s += rect(0, 0, w, h, "none", stroke=INK, sw=2)
    return w, h, s, ""


def airlock(size):
    tw, th = AUX[size]
    w, h = tw * T, th * T
    u = uid(f"airlock_{size}")
    s = housing(w, h, inset=2)
    # grated chamber floor between two doors at the short ends
    gx, gy, gw, gh = 8, 16, w - 16, h - 32
    s += rect(gx, gy, gw, gh, "#20262e", stroke=INK, sw=1.5)
    for xx in range(int(gx) + 4, int(gx + gw), 6):
        s += line(xx, gy + 2, xx, gy + gh - 2, STEEL_DARK, sw=2)
    # a cross rail on every tile boundary, and suit racks along the walls of wider locks
    for ty in range(1, th):
        s += rect(gx, ty * T - 2, gw, 4, STEEL_MID, stroke=INK, sw=1)
    if tw >= 2:
        for ty in range(th):
            for side_x in (gx + 2, w - gx - 14):
                if ty * T + 22 > h - 30:
                    continue
                s += rect(side_x, ty * T + 22, 12, 22, "#d8dde4", rx=5, stroke=INK, sw=1.2)
                s += circle(side_x + 6, ty * T + 28, 4, GLASS, stroke=INK, sw=1)
                s += circle(side_x + 6, ty * T + 28, 4, "#6aa8ff", extra=' opacity="0.35"')
    s += hazard_band(6, 4, w - 12, 10, u + "a")
    s += hazard_band(6, h - 14, w - 12, 10, u + "b")
    s += circle(w - 12, 22, 3, "#ff4d4d", stroke=INK, sw=1)
    s += circle(12, h - 22, 3, "#4dff88", stroke=INK, sw=1)
    return w, h, s, ""


def docking_port(size, state):
    tw, td = PORT[size]
    w, h = tw * T, td * T
    u = uid(f"docking_port_{size}_{state}")
    # Frame tiles at both ends of the width (hull), opening between, door on the top (outer) edge.
    s = ""
    for side in (0, w - T):
        for ty in range(td):
            s += rect(side, ty * T, T, T, "#3d4552")
            s += plate(side + 5, ty * T + 5, T - 10, T - 10, "#5b6675", STEEL_HI, INK, rx=3)
        s += hazard_band(side + 10, 10, T - 20, 8, f"{u}_f{side}")
    s += floor_area(w - 2 * T, h, T, 0)
    # guidance chevrons in the opening pointing out the door
    ow = w - 2 * T
    for ty in range(td):
        cy = ty * T + 40
        s += path(f"M{f(T + ow/2 - 14)},{f(cy)} L{f(T + ow/2)},{f(cy - 12)} L{f(T + ow/2 + 14)},{f(cy)}",
                  stroke=HAZARD, sw=4, extra=' opacity="0.85"')
    if state == "sealed":
        s += rect(T - 4, -1, ow + 8, 12, "#7a8594", stroke=INK, sw=2)
        s += hazard_band(T + 2, 2, ow - 4, 6, f"{u}_d")
        s += line(T + ow / 2, -1, T + ow / 2, 11, INK, sw=2)
        light = "#ff4d4d"
    else:
        s += rect(T - 4, -1, 10, 12, "#7a8594", stroke=INK, sw=2)
        s += rect(T + ow - 6, -1, 10, 12, "#7a8594", stroke=INK, sw=2)
        light = "#4dff88"
    s += circle(T / 2, h - 12 if td > 1 else 40, 4, light, stroke=INK, sw=1.2)
    s += circle(w - T / 2, h - 12 if td > 1 else 40, 4, light, stroke=INK, sw=1.2)
    s += rect(0, 0, w, h, "none", stroke=INK, sw=2)
    return w, h, s, ""


def docking_collar(size):
    """The latch collar outside a port's outer edge: W tiles wide, half a tile deep."""
    tw, _ = PORT[size]
    w, h = tw * T, T / 2
    s = rect(T * 0.5, 4, w - T, h - 4, STEEL_MID, rx=3, stroke=INK, sw=2)
    s += bevel(T * 0.5 + 1, 5, w - T - 2, h - 6, STEEL_HI, INK)
    s += rect(T, 0, w - 2 * T, 8, HOUSING, rx=2, stroke=INK, sw=1.5)
    n = tw * 2
    for i in range(1, n):
        x = T * 0.5 + (w - T) * i / n
        s += rect(x - 3, h - 10, 6, 8, "#7a8594", rx=1, stroke=INK, sw=1)
    s += circle(T * 0.5 + 8, h / 2 + 2, 3, HAZARD, stroke=INK, sw=1)
    s += circle(w - T * 0.5 - 8, h / 2 + 2, 3, HAZARD, stroke=INK, sw=1)
    return w, h, s


def components():
    for size in SIZES:
        for name, fn in (("reactor", reactor), ("shield_emitter", shield_emitter), ("turret_mount", turret_mount)):
            w, h, s, defs = fn(size)
            write(f"components/{name}_{size}.svg", w, h, s, f"{name} {size.upper()}: {w//T}x{h//T} primary mount",
                  defs)
        for name in ("helm", "weapons"):
            w, h, s, defs = station_console(name, size)
            write(f"components/{name}_{size}.svg", w, h, s, f"{name} {size.upper()}: {w//T}x{h//T} auxiliary slot",
                  defs)
        for name, fn in (("life_support", life_support), ("cargo", cargo), ("airlock", airlock)):
            w, h, s, defs = fn(size)
            write(f"components/{name}_{size}.svg", w, h, s, f"{name} {size.upper()}: {w//T}x{h//T} auxiliary slot",
                  defs)
    for size in PORT:
        for state in ("sealed", "open"):
            w, h, s, defs = docking_port(size, state)
            name = f"docking_port_{size}" + ("_open" if state == "open" else "")
            write(f"components/{name}.svg", w, h, s,
                  f"docking_port {size.upper()}: {w//T}x{h//T} port mount, door {state}, facing +y at rotation 0")
        w, h, s = docking_collar(size)
        write(f"components/docking_collar_{size}.svg", w, h, s,
              f"docking collar {size.upper()}: sits half a tile outside the port's outer edge")


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
        s = circle(32, 36, 26 if name != "small" else 16, "#ff5a1f", extra=' opacity="0.18"')
        for (cx, base, hh, ww) in flames:
            s += flame(cx, base, hh, ww, cols)
        write(f"effects/fire_{name}.svg", T, T, s, f"fire_{name}: fire overlay (Tile.FireSize.{name.upper()})")

    rnd = random.Random(4)
    s = ""
    for i in range(14):
        a = rnd.uniform(0, 2 * math.pi)
        r = rnd.uniform(4, 24)
        s += circle(32 + math.cos(a) * r, 32 + math.sin(a) * r, rnd.uniform(6, 13), "#0b0d10",
                    extra=f' opacity="{f(rnd.uniform(0.25, 0.45))}"')
    write("effects/scorch.svg", T, T, s, "scorch: burn mark left after a fire")

    # Hull breach: a torn hole showing space, with bent plating at the edge.
    pts = []
    rnd = random.Random(11)
    for i in range(16):
        a = 2 * math.pi * i / 16
        r = rnd.uniform(16, 26)
        pts.append((32 + math.cos(a) * r, 32 + math.sin(a) * r))
    s = poly(pts, STEEL_DARK, stroke=INK, sw=2)
    inner = [(32 + (x - 32) * 0.72, 32 + (y - 32) * 0.72) for x, y in pts]
    s += poly(inner, "#03050a", stroke=INK, sw=1.5)
    for (sx, sy) in ((26, 28), (38, 36), (31, 40)):
        s += circle(sx, sy, 0.9, "#cfe3ff")
    for i in range(0, 16, 3):
        x, y = pts[i]
        s += line(x, y, 32 + (x - 32) * 1.2, 32 + (y - 32) * 1.2, STEEL_HI, sw=1.5)
    write("effects/breach.svg", T, T, s, "breach: hull tile at integrity 0, open to space")

    for name, n, sw in (("light", 3, 1.2), ("heavy", 7, 2)):
        rnd = random.Random(7 if name == "light" else 9)
        s = ""
        for i in range(n):
            x, y = rnd.uniform(10, 54), rnd.uniform(10, 54)
            d = f"M{f(x)},{f(y)}"
            for _ in range(4):
                x += rnd.uniform(-10, 10)
                y += rnd.uniform(-10, 10)
                d += f" L{f(min(62, max(2, x)))},{f(min(62, max(2, y)))}"
            s += path(d, stroke=INK, sw=sw, extra=' opacity="0.85"')
        write(f"effects/damage_{name}.svg", T, T, s,
              f"damage_{name}: cracks for a tile or system below {'half' if name == 'light' else 'a quarter'} integrity")

    # Emergency bulkhead seal: a patch plate bolted over the tile.
    s = rect(3, 3, 58, 58, "#8a6d1f", rx=4, stroke=INK, sw=2)
    s += hazard_band(7, 7, 50, 8, "seal_top")
    s += hazard_band(7, 49, 50, 8, "seal_bottom")
    s += rect(7, 17, 50, 30, "#a8862a", stroke=INK, sw=1.5)
    for xx in (12, 24, 40, 52):
        s += circle(xx, 32, 2, "#d9c27a", stroke=INK, sw=1)
    write("effects/bulkhead_seal.svg", T, T, s, "bulkhead_seal: emergency seal over a tile (Tile.bulkheadSealed)")

    # Unpowered system: a dim cover with a power-off mark, drawn over a component.
    s = rect(0, 0, T, T, "#000000", extra=' opacity="0.35"')
    s += circle(32, 32, 12, "none", stroke="#d0d6de", sw=3, extra=' opacity="0.8"')
    s += rect(29, 14, 6, 18, "#3a434f")
    s += line(32, 16, 32, 30, "#d0d6de", sw=3, extra=' opacity="0.8"')
    write("effects/unpowered.svg", T, T, s, "unpowered: overlay for a system with no power (stretch over the footprint)")


# ---------------------------------------------------------------------------
# Decor and interactive objects (sub-tile, quarter-tile grid of 16 units)
# ---------------------------------------------------------------------------

def decor():
    # Floor vent grate, 32x32
    s = rect(2, 2, 28, 28, HOUSING, rx=3, stroke=INK, sw=2)
    for yy in range(8, 27, 5):
        s += line(7, yy, 25, yy, INK, sw=2.5)
    s += bevel(3, 3, 26, 26, HOUSING_HI, INK, sw=1)
    write("decor/vent_grate.svg", 32, 32, s, "vent_grate: floor vent, 2x2 sub-tiles")

    # Floor hatch, 32x32
    s = rect(2, 2, 28, 28, STEEL_MID, rx=2, stroke=INK, sw=2)
    s += rect(7, 7, 18, 18, STEEL_DARK, rx=2, stroke=INK, sw=1.5)
    s += rect(13, 14, 6, 4, HAZARD, stroke=INK, sw=1)
    write("decor/floor_hatch.svg", 32, 32, s, "floor_hatch: maintenance hatch, 2x2 sub-tiles")

    # Pipe run along a wall, 64x16 (pipes at the top, against the wall)
    s = rect(0, 2, 64, 5, "#4f7a8c", stroke=INK, sw=1.5)
    s += rect(0, 9, 64, 5, "#8c6a4f", stroke=INK, sw=1.5)
    for xx in (10, 54):
        s += rect(xx - 2, 0, 4, 16, STEEL_DARK, rx=1, stroke=INK, sw=1)
    s += line(0, 3.5, 64, 3.5, "#9fc3d1", sw=1, extra=' opacity="0.6"')
    write("decor/pipe_run.svg", 64, 16, s, "pipe_run: water and coolant pipes along a wall, tiles end to end")

    s = rect(0, 2, 64, 5, "#4f7a8c", stroke=INK, sw=1.5)
    s += rect(0, 9, 64, 5, "#8c6a4f", stroke=INK, sw=1.5)
    s += circle(32, 4.5, 6, "#b33b3b", stroke=INK, sw=1.5)
    s += line(26, 4.5, 38, 4.5, "#e0e0e0", sw=1.5)
    write("decor/pipe_valve.svg", 64, 16, s, "pipe_valve: pipe run with a hand valve")

    # Cable tray, 64x16
    s = rect(0, 3, 64, 10, HOUSING, stroke=INK, sw=1.5)
    for k, col in enumerate(("#d24b4b", POWER, "#4b8bd2", "#e0e0e0")):
        s += line(0, 5 + k * 2, 64, 5 + k * 2, col, sw=1.4)
    for xx in (16, 48):
        s += rect(xx - 1.5, 2, 3, 12, STEEL_MID, stroke=INK, sw=0.8)
    write("decor/cable_tray.svg", 64, 16, s, "cable_tray: cables along a wall")

    # Wall light, 32x16
    s = rect(2, 2, 28, 7, HOUSING, rx=2, stroke=INK, sw=1.5)
    s += rect(5, 4, 22, 3, "#fff7d6")
    s += path("M4,9 L28,9 L24,15 L8,15 Z", fill="#fff7d6", extra=' opacity="0.25"')
    write("decor/wall_light.svg", 32, 16, s, "wall_light: light strip on a wall")

    # Hazard stripe, 64x16
    write("decor/hazard_stripe.svg", 64, 16, hazard_band(0, 4, 64, 8, "hazstripe"), "hazard_stripe: floor marking")

    # Floor arrow, 32x32
    s = poly([(16, 4), (28, 18), (21, 18), (21, 28), (11, 28), (11, 18), (4, 18)], HAZARD, stroke=INK, sw=1.5,
             extra=' opacity="0.85"')
    write("decor/floor_arrow.svg", 32, 32, s, "floor_arrow: direction marking, points +y")

    # Crates
    for name, sz in (("crate_small", 32), ("crate_large", 48)):
        s = rect(2, 2, sz - 4, sz - 4, "#7a5a33", rx=2, stroke=INK, sw=2)
        s += rect(6, 6, sz - 12, sz - 12, "#8f6b3e", stroke=INK, sw=1.2)
        s += line(6, 6, sz - 6, sz - 6, "#6a4d2b", sw=2)
        s += line(sz - 6, 6, 6, sz - 6, "#6a4d2b", sw=2)
        s += bevel(3, 3, sz - 6, sz - 6, "#b48a55", "#4d371e", sw=1)
        s += collider_rect(2, 2, sz - 4, sz - 4)
        write(f"decor/{name}.svg", sz, sz, s, f"{name}: loose crate, {sz//16}x{sz//16} sub-tiles")

    # Barrel, 32x32 (top view)
    s = circle(16, 16, 13, "#3d6b8f", stroke=INK, sw=2)
    s += circle(16, 16, 10, "none", stroke="#2b4d68", sw=1.5)
    s += circle(11, 12, 2.5, "#c9d6e0", stroke=INK, sw=1)
    s += circle(16, 16, 13, "none", stroke="#6f9bbd", sw=1, extra=' stroke-dasharray="10 30"')
    s += collider_circle(16, 16, 13)
    write("decor/barrel.svg", 32, 32, s, "barrel: drum seen from above")

    # Locker, 32x16 against a wall (wall side at the top)
    s = rect(1, 1, 30, 14, "#566a7a", rx=1, stroke=INK, sw=1.5)
    s += line(16, 2, 16, 14, INK, sw=1.2)
    s += rect(12, 9, 2, 4, "#c9d6e0")
    s += rect(18, 9, 2, 4, "#c9d6e0")
    s += collider_rect(1, 1, 30, 14)
    write("decor/locker.svg", 32, 16, s, "locker: crew locker against a wall")

    # Bunk, 32x64
    s = rect(2, 2, 28, 60, "#3a404a", rx=3, stroke=INK, sw=2)
    s += rect(5, 5, 22, 54, "#5a6b80", rx=3, stroke=INK, sw=1.2)
    s += rect(7, 7, 18, 10, "#d8dde4", rx=3, stroke=INK, sw=1)
    s += rect(5, 30, 22, 29, "#2f5a8a", rx=2, stroke=INK, sw=1)
    s += line(5, 33, 27, 33, "#4a78ad", sw=1.5)
    s += collider_rect(2, 2, 28, 60)
    s += interact_point(16, 40)
    write("decor/bunk.svg", 32, 64, s, "bunk: crew bed, pillow toward +y")

    # Table and chair
    s = rect(3, 3, 26, 26, "#6b5a48", rx=3, stroke=INK, sw=2)
    s += bevel(4, 4, 24, 24, "#8a755e", "#4a3e31", sw=1)
    s += circle(10, 11, 3, "#d8dde4", stroke=INK, sw=0.8)
    s += collider_rect(3, 3, 26, 26)
    write("decor/table.svg", 32, 32, s, "table: small table")

    s = rect(2, 2, 60, 28, "#6b5a48", rx=3, stroke=INK, sw=2)
    s += bevel(3, 3, 58, 26, "#8a755e", "#4a3e31", sw=1)
    for xx in (14, 32, 50):
        s += circle(xx, 16, 3.5, "#d8dde4", stroke=INK, sw=0.8)
    s += collider_rect(2, 2, 60, 28)
    write("decor/mess_table.svg", 64, 32, s, "mess_table: long table, 4x2 sub-tiles")

    s = rect(3, 4, 10, 10, "#2b2f36", rx=3, stroke=INK, sw=1.5)
    s += rect(3, 1, 10, 4, "#1e2227", rx=1.5, stroke=INK, sw=1)
    s += collider_rect(3, 1, 10, 13)
    write("decor/chair.svg", 16, 16, s, "chair: seat facing -y (back toward +y)")

    # Plant in a hydroponics pot, 16x16
    s = circle(8, 8, 6.5, "#4a3e31", stroke=INK, sw=1.5)
    for i in range(5):
        a = 2 * math.pi * i / 5
        s += path(f"M8,8 Q{f(8+math.cos(a)*4)},{f(8+math.sin(a)*1)} {f(8+math.cos(a)*7)},{f(8+math.sin(a)*7)}",
                  stroke="#5ec46a", sw=2.5)
    s += circle(8, 8, 1.8, "#8fe39a")
    s += collider_circle(8, 8, 6.5)
    write("decor/plant.svg", 16, 16, s, "plant: potted plant")


def interactive():
    # Fire extinguisher, 16x16 (bracketed to a wall at the top)
    s = rect(4, 0, 8, 3, STEEL_DARK, stroke=INK, sw=1)
    s += circle(8, 9, 5, "#d33a3a", stroke=INK, sw=1.5)
    s += circle(8, 9, 2, "#2b2f36")
    s += line(8, 4, 12, 2, "#2b2f36", sw=1.5)
    s += collider_circle(8, 9, 5)
    s += interact_point(8, 14)
    write("interactive/fire_extinguisher.svg", 16, 16, s, "fire_extinguisher: crew pick it up to fight fires")

    # Bulkhead control panel, 16x16 on a wall
    s = rect(2, 1, 12, 9, HOUSING, rx=1.5, stroke=INK, sw=1.2)
    s += rect(4, 3, 3.5, 5, "#4dff88")
    s += rect(8.5, 3, 3.5, 5, "#ff4d4d")
    s += interact_point(8, 14)
    write("interactive/bulkhead_panel.svg", 16, 16, s, "bulkhead_panel: opens and seals a nearby bulkhead door")

    # Breaker / power switch, 16x16
    s = rect(2, 1, 12, 10, "#3a414c", rx=1.5, stroke=INK, sw=1.2)
    s += rect(6, 3, 4, 6, "#1e2227", stroke=INK, sw=0.8)
    s += rect(6.5, 3.5, 3, 2.5, POWER)
    s += interact_point(8, 14)
    write("interactive/breaker.svg", 16, 16, s, "breaker: switches a system on or off")

    # Wall terminal, 32x16
    s = rect(2, 1, 28, 10, HOUSING, rx=2, stroke=INK, sw=1.5)
    s += screen(5, 2.5, 22, 7, ACCENT["helm"])
    s += interact_point(16, 14)
    write("interactive/terminal.svg", 32, 16, s, "terminal: wall terminal")

    # Emergency suit locker, 32x16
    s = rect(1, 1, 30, 14, "#8a6d1f", rx=1, stroke=INK, sw=1.5)
    s += hazard_band(3, 3, 26, 3, "suitlocker")
    s += rect(6, 8, 20, 5, "#d33a3a", rx=1, stroke=INK, sw=1)
    s += rect(14, 9, 4, 3, "#fff")
    s += collider_rect(1, 1, 30, 14)
    s += interact_point(16, 15)
    write("interactive/emergency_locker.svg", 32, 16, s, "emergency_locker: oxygen and vacuum suits")

    # Repair kit (breach patches), 16x16
    s = rect(2, 3, 12, 10, "#c46a1f", rx=1.5, stroke=INK, sw=1.2)
    s += rect(6, 1.5, 4, 2.5, STEEL_DARK, stroke=INK, sw=0.8)
    s += rect(6.5, 6, 3, 4, "#fff")
    s += rect(5, 7, 6, 2, "#fff")
    s += collider_rect(2, 2, 12, 11)
    s += interact_point(8, 8)
    write("interactive/repair_kit.svg", 16, 16, s, "repair_kit: patches for repairs and breaches")

    # Medical bed, 32x64
    s = rect(2, 2, 28, 60, "#c9d2dc", rx=3, stroke=INK, sw=2)
    s += rect(5, 5, 22, 54, "#e8edf2", rx=3, stroke=INK, sw=1)
    s += rect(8, 7, 16, 9, "#ffffff", rx=3, stroke=INK, sw=1)
    s += rect(10, 46, 12, 8, "#5ee08a", rx=1, extra=' opacity="0.8"')
    s += line(13, 50, 19, 50, "#fff", sw=1.5)
    s += line(16, 47, 16, 53, "#fff", sw=1.5)
    s += collider_rect(2, 2, 28, 60)
    s += interact_point(16, 34)
    write("interactive/med_bed.svg", 32, 64, s, "med_bed: medical bed, head toward +y")

    # Cargo stacks on a bay tile at three fill levels (CargoHoldComponent stacks)
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
        s = ""
        for i, (x, y, w, h) in enumerate(crates):
            s += crate(x, y, w, h, ("#8f6b3e", "#7a5a33", "#a07a48", "#86643a")[i % 4])
        if level == 3:
            s += rect(4, 30, 56, 4, HAZARD, extra=' opacity="0.9"')  # strap
            s += rect(30, 4, 4, 56, HAZARD, extra=' opacity="0.9"')
        s += interact_point(32, 32)
        write(f"interactive/cargo_stack_{level}.svg", T, T, s,
              f"cargo_stack_{level}: one cargo stack on a bay tile, {['', 'a third', 'two thirds', 'full'][level]}")


# ---------------------------------------------------------------------------
# Exterior: ship hulls, station hulls, turrets
# ---------------------------------------------------------------------------

def load_toml(name):
    with open(os.path.join(ROOT, "assets", "data", name), "rb") as fh:
        return tomllib.load(fh)


def port_clusters(chassis):
    """Docking ports in a chassis map, as (grid x, grid y, w, h) rectangles."""
    rows = chassis["rows"]
    H = len(rows)
    seen = set()
    out = []
    for r, row in enumerate(rows):
        for c, ch in enumerate(row):
            if ch != "D" or (c, r) in seen:
                continue
            stack = [(c, r)]
            cells = []
            seen.add((c, r))
            while stack:
                x, y = stack.pop()
                cells.append((x, y))
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= ny < H and 0 <= nx < len(rows[ny]) and rows[ny][nx] == "D" and (nx, ny) not in seen:
                        seen.add((nx, ny))
                        stack.append((nx, ny))
            xs = [p[0] for p in cells]
            ys = [p[1] for p in cells]
            x0, x1, r0, r1 = min(xs), max(xs), min(ys), max(ys)
            out.append((x0, H - 1 - r1, x1 - x0 + 1, r1 - r0 + 1))
    return out


def port_marks(chassis, ox, oy):
    """Port rings and collars on an exterior hull, in body coordinates scaled to SVG."""
    s = ""
    W = max(len(r) for r in chassis["rows"])
    H = len(chassis["rows"])
    for (gx, gy, w, h) in port_clusters(chassis):
        bx, by = (gx + ox) * T, -(gy + h + oy) * T  # top-left in SVG units
        bw, bh = w * T, h * T
        s += rect(bx, by, bw, bh, STEEL_DARK, rx=3, stroke=INK, sw=2)
        # facing: the side of the grid the cluster touches
        if gy + h == H:
            cx, cy, cw, ch_ = bx + T * 0.5, by - T * 0.5, bw - T, T * 0.5
        elif gy == 0:
            cx, cy, cw, ch_ = bx + T * 0.5, by + bh, bw - T, T * 0.5
        elif gx == 0:
            cx, cy, cw, ch_ = bx - T * 0.5, by + T * 0.5, T * 0.5, bh - T
        else:
            cx, cy, cw, ch_ = bx + bw, by + T * 0.5, T * 0.5, bh - T
        s += rect(cx, cy, cw, ch_, STEEL_MID, rx=3, stroke=INK, sw=2)
        s += rect(bx + 10, by + 10, bw - 20, bh - 20, "none", stroke=HAZARD, sw=4, extra=' stroke-dasharray="12 8"')
    return s


def panel_lines(x0, y0, x1, y1, step, clip_id):
    s = f'  <g clip-path="url(#{clip_id})" opacity="0.55">\n'
    x = x0 + step
    while x < x1:
        s += line(x, y0, x, y1, STEEL_DARK, sw=2)
        x += step
    y = y0 + step
    while y < y1:
        s += line(x0, y, x1, y, STEEL_DARK, sw=1.5)
        y += step
    return s + "  </g>\n"


def engine(x, y, r):
    """An engine at the aft (-x) edge, centered on (x, y): a housing and a flared bell."""
    s = rect(x - r * 1.2, y - r * 0.9, r * 1.6, r * 1.8, STEEL_DARK, rx=r * 0.25, stroke=INK, sw=2)
    s += path(f"M{f(x - r*1.2)},{f(y - r*0.75)} L{f(x - r*2.2)},{f(y - r*1.15)} L{f(x - r*2.2)},{f(y + r*1.15)} "
              f"L{f(x - r*1.2)},{f(y + r*0.75)} Z", fill=STEEL, stroke=INK, sw=2)
    s += path(f"M{f(x - r*1.35)},{f(y - r*0.6)} L{f(x - r*2.05)},{f(y - r*0.95)} L{f(x - r*2.05)},{f(y + r*0.95)} "
              f"L{f(x - r*1.35)},{f(y + r*0.6)} Z", fill=HOUSING)
    s += rect(x - r * 2.3, y - r * 1.1, r * 0.18, r * 2.2, "#ffb15c", rx=2, extra=' opacity="0.75"')
    for k in (-0.45, 0, 0.45):
        s += line(x - r * 1.0, y + r * k, x + r * 0.2, y + r * k, STEEL_MID, sw=2)
    return s


def radiator(x, y, w, h):
    """A radiator panel: fins across its length."""
    s = rect(x, y, w, h, "#3f4855", rx=3, stroke=INK, sw=2)
    k = x + 6
    while k < x + w - 4:
        s += line(k, y + 4, k, y + h - 4, "#6d7a8a", sw=2)
        k += 7
    return s


def hulls():
    ships = load_toml("ships.toml")["ship"]
    chassis = load_toml("chassis.toml")["chassis"]

    for ship_id, ship in ships.items():
        ch = chassis[ship["chassis"]]
        name = ship["chassis"]
        pts = [(x * T, -y * T) for x, y in ship["hull"]]
        xs = [p[0] for p in pts]
        ys = [p[1] for p in pts]
        minx, maxx, miny, maxy = min(xs), max(xs), min(ys), max(ys)
        pad = T * 1.5
        vb = (minx - pad, miny - T, maxx - minx + pad + T * 0.5, maxy - miny + 2 * T)
        u = f"hull_{name}"
        defs = (f'<clipPath id="{u}_clip"><polygon points="{" ".join(f"{f(x)},{f(y)}" for x, y in pts)}"/></clipPath>'
                f'<linearGradient id="{u}_shade" x1="0" y1="0" x2="0" y2="1">'
                f'<stop offset="0" stop-color="#ffffff" stop-opacity="0.12"/>'
                f'<stop offset="0.5" stop-color="#ffffff" stop-opacity="0"/>'
                f'<stop offset="1" stop-color="#000000" stop-opacity="0.25"/></linearGradient>')
        s = ""
        height = maxy - miny
        engines = 2 if height <= 6 * T else 4
        for i in range(engines):
            ey = miny + height * (i + 0.5) / engines
            s += engine(minx, ey, min(T * 0.9, height / engines * 0.32))
        s += poly(pts, STEEL_MID, stroke=INK, sw=3)
        s += panel_lines(minx, miny, maxx, maxy, T * 2, f"{u}_clip")
        s += f'  <polygon points="{" ".join(f"{f(x)},{f(y)}" for x, y in pts)}" fill="url(#{u}_shade)"/>\n'
        hps = [(hp["x"] * T, -hp["y"] * T) for hp in ship.get("hardpoints", [])]
        # bridge glazing near the bow, moved aft until it clears every hardpoint ring
        bridge_x = maxx - T * 2.6
        while any(abs(hx - (bridge_x + T * 0.45)) < T * 1.1 and abs(hy) < T * 1.6 for hx, hy in hps):
            bridge_x -= T * 0.5
        # armor strakes along the beam edges and a nose cap
        s += f'  <g clip-path="url(#{u}_clip)">\n'
        s += rect(minx, miny, maxx - minx, T * 0.45, STEEL_DARK)
        s += rect(minx, maxy - T * 0.45, maxx - minx, T * 0.45, STEEL_DARK)
        nose = max(x for x, y in pts if abs(y) >= height / 2 - 1)
        if maxx - nose > T * 0.5:
            s += rect(nose, miny, maxx - nose, height, "#5f6a7a")
            s += line(nose, miny, nose, maxy, INK, sw=2)
        s += "  </g>\n"
        # dorsal spine from the engines to the bridge
        spine_w = bridge_x - (minx + T)
        s += rect(minx + T, -T * 0.6, spine_w, T * 1.2, STEEL, rx=6, stroke=INK, sw=2)
        s += bevel(minx + T + 1, -T * 0.6 + 1, spine_w - 2, T * 1.2 - 2, STEEL_HI, INK)
        for k in range(1, int(spine_w // (T * 1.5)) + 1):
            s += line(minx + T + k * T * 1.5, -T * 0.6, minx + T + k * T * 1.5, T * 0.6, STEEL_DARK, sw=2)
        # radiators over the engine room, either side of the spine
        s += radiator(minx + T * 1.3, -T * 0.6 - T * 1.3, T * 2.2, T * 0.9)
        s += radiator(minx + T * 1.3, T * 0.6 + T * 0.4, T * 2.2, T * 0.9)
        if "freighter" in name:
            # cargo bay hatches over the two bays (grid x 4-15, either side of the spine)
            ox, oy = ch["origin_x"], ch["origin_y"]
            for (gy0, gy1) in ((7, 12), (1, 6)):
                bx, by = (4 + ox) * T, -(gy1 + oy) * T
                bw, bh = 12 * T, (gy1 - gy0) * T
                for k in range(3):
                    s += rect(bx + k * bw / 3 + 4, by + 6, bw / 3 - 8, bh - 12, "#55606e", rx=4, stroke=INK, sw=2)
                    s += bevel(bx + k * bw / 3 + 5, by + 7, bw / 3 - 10, bh - 14, STEEL_HI, INK)
                    s += line(bx + k * bw / 3 + 12, by + bh / 2, bx + (k + 1) * bw / 3 - 12, by + bh / 2,
                              HAZARD, sw=3, extra=' opacity="0.7" stroke-dasharray="10 8"')
        # bridge glazing
        s += rect(bridge_x, -T * 0.9, T * 0.9, T * 1.8, GLASS, rx=8, stroke=INK, sw=2)
        s += rect(bridge_x + T * 0.1, -T * 0.8, T * 0.3, T * 1.6, ACCENT["helm"], rx=4, extra=' opacity="0.5"')
        # hardpoint rings
        for hx, hy in hps:
            s += circle(hx, hy, T * 0.55, STEEL_DARK, stroke=INK, sw=2)
            s += circle(hx, hy, T * 0.4, "none", stroke=STEEL_HI, sw=1.5, extra=' stroke-dasharray="4 4"')
        s += port_marks(ch, ch["origin_x"], ch["origin_y"])
        # running lights: red to port (+y), green to starboard (-y)
        s += circle(maxx - T * 1.2, miny + 10, 5, "#ff4d4d", stroke=INK, sw=1)
        s += circle(maxx - T * 1.2, maxy - 10, 5, "#4dff88", stroke=INK, sw=1)
        write(f"hulls/{name}.svg", vb[2], vb[3], s,
              f"{name} exterior hull ({ship_id}): body frame, origin at the SVG origin, bow toward +x",
              defs, viewbox=" ".join(f(v) for v in vb))

    for name, ch in chassis.items():
        if not ch.get("station"):
            continue
        rows = ch["rows"]
        W, H = max(len(r) for r in rows), len(rows)
        ox, oy = ch["origin_x"], ch["origin_y"]
        x0, y0, w, h = ox * T, -(oy + H) * T, W * T, H * T
        vb = (x0 - T, y0 - T, w + 2 * T, h + 2 * T)
        u = f"hull_{name}"
        defs = f'<clipPath id="{u}_clip"><rect x="{f(x0)}" y="{f(y0)}" width="{f(w)}" height="{f(h)}"/></clipPath>'
        s = rect(x0, y0, w, h, STEEL_MID, rx=T * 0.6, stroke=INK, sw=3)
        s += panel_lines(x0, y0, x0 + w, y0 + h, T * 2, f"{u}_clip")
        # outer truss ring
        s += rect(x0 + T * 0.5, y0 + T * 0.5, w - T, h - T, "none", stroke=STEEL_HI, sw=4, extra=' opacity="0.6"')
        # reactor dome over the reactor mount
        rc = [(c, len(rows) - 1 - r) for r, row in enumerate(rows) for c, chh in enumerate(row) if chh == "R"]
        if rc:
            cx = (sum(p[0] for p in rc) / len(rc) + 0.5 + ox) * T
            cy = -(sum(p[1] for p in rc) / len(rc) + 0.5 + oy) * T
            s += circle(cx, cy, T * 2.6, STEEL, stroke=INK, sw=3)
            s += circle(cx, cy, T * 1.8, STEEL_DARK, stroke=INK, sw=2)
            s += circle(cx, cy, T * 0.8, ACCENT["reactor"], stroke=INK, sw=2, extra=' opacity="0.85"')
            for i in range(8):
                a = 2 * math.pi * i / 8
                s += line(cx + math.cos(a) * T * 1.8, cy + math.sin(a) * T * 1.8,
                          cx + math.cos(a) * T * 2.6, cy + math.sin(a) * T * 2.6, INK, sw=2)
        # cargo bay roofs over cargo mounts
        cc = [(c, len(rows) - 1 - r) for r, row in enumerate(rows) for c, chh in enumerate(row) if chh == "C"]
        if cc:
            groups = {}
            for (c, r) in cc:
                groups.setdefault(c < W / 2, []).append((c, r))
            for cells in groups.values():
                gx0, gx1 = min(p[0] for p in cells), max(p[0] for p in cells) + 1
                gy0, gy1 = min(p[1] for p in cells), max(p[1] for p in cells) + 1
                bx, by = (gx0 + ox) * T, -(gy1 + oy) * T
                s += rect(bx, by, (gx1 - gx0) * T, (gy1 - gy0) * T, "#55606e", rx=6, stroke=INK, sw=2)
                s += rect(bx + 8, by + 8, (gx1 - gx0) * T - 16, (gy1 - gy0) * T - 16, "none",
                          stroke=HAZARD, sw=3, extra=' stroke-dasharray="10 8" opacity="0.7"')
        s += port_marks(ch, ox, oy)
        # beacon lights on the corners
        for (bx, by) in ((x0 + 20, y0 + 20), (x0 + w - 20, y0 + 20), (x0 + 20, y0 + h - 20), (x0 + w - 20, y0 + h - 20)):
            s += circle(bx, by, 6, "#ffd34d", stroke=INK, sw=1.5)
        write(f"hulls/{name}.svg", vb[2], vb[3], s,
              f"{name} exterior hull: body frame, origin at the SVG origin, matches the chassis grid",
              defs, viewbox=" ".join(f(v) for v in vb))


def turrets():
    """Weapon turrets seen from above, barrel toward +x, pivot at the SVG origin."""
    def base(r):
        return circle(0, 0, r, STEEL, stroke=INK, sw=2) + circle(0, 0, r * 0.7, STEEL_MID, stroke=INK, sw=1.5)
    vb = lambda left, right, half: (f"{f(left)} {f(-half)} {f(right - left)} {f(2 * half)}",
                                    right - left, 2 * half)

    s = base(28) + rect(10, -6, 74, 12, STEEL_DARK, rx=2, stroke=INK, sw=2)
    s += rect(10, -3, 74, 2, "#4fd1ff", extra=' opacity="0.6"')
    s += rect(80, -8, 8, 16, STEEL_MID, rx=2, stroke=INK, sw=2)
    s += rect(-20, -16, 30, 32, HOUSING, rx=4, stroke=INK, sw=2)
    v, w, h = vb(-32, 92, 32)
    write("turrets/railgun_mk1.svg", w, h, s, "railgun_mk1 turret: pivot at origin, barrel toward +x", viewbox=v)

    s = base(20)
    for dy in (-5, 5):
        s += rect(6, dy - 3, 40, 6, STEEL_DARK, rx=1.5, stroke=INK, sw=1.5)
    s += rect(-12, -12, 22, 24, HOUSING, rx=4, stroke=INK, sw=2)
    v, w, h = vb(-24, 52, 24)
    write("turrets/autocannon_mk1.svg", w, h, s, "autocannon_mk1 turret: pivot at origin, barrels toward +x", viewbox=v)

    s = base(22) + rect(4, -7, 36, 14, HOUSING, rx=6, stroke=INK, sw=2)
    s += circle(40, 0, 7, "#ff5a5a", stroke=INK, sw=2)
    s += circle(40, 0, 3, "#ffd6d6")
    s += rect(-14, -10, 18, 20, STEEL_DARK, rx=3, stroke=INK, sw=2)
    v, w, h = vb(-26, 52, 26)
    write("turrets/laser_mk1.svg", w, h, s, "laser_mk1 turret: pivot at origin, emitter toward +x", viewbox=v)

    s = base(24) + rect(-14, -18, 44, 36, STEEL_DARK, rx=4, stroke=INK, sw=2)
    for (cx, cy) in ((4, -8), (20, -8), (4, 8), (20, 8)):
        s += circle(cx, cy, 6, "#1e2227", stroke=INK, sw=1.5)
        s += circle(cx, cy, 2.5, "#ff5a5a")
    v, w, h = vb(-28, 36, 28)
    write("turrets/harpoon_launcher.svg", w, h, s, "harpoon_launcher: pivot at origin, tubes facing +x", viewbox=v)

    # Engine plume, drawn behind an engine bell, pointing -x (exhaust), origin at the nozzle
    defs = ('<linearGradient id="plume_g" x1="1" y1="0" x2="0" y2="0">'
            '<stop offset="0" stop-color="#ffffff"/><stop offset="0.25" stop-color="#9fd8ff"/>'
            '<stop offset="1" stop-color="#4f8fff" stop-opacity="0"/></linearGradient>')
    s = path("M0,-24 C-60,-20 -150,-8 -192,0 C-150,8 -60,20 0,24 Z", fill="url(#plume_g)", extra=' opacity="0.9"')
    s += path("M0,-10 C-40,-8 -80,-3 -110,0 C-80,3 -40,8 0,10 Z", fill="#ffffff", extra=' opacity="0.8"')
    write("hulls/thruster_plume.svg", 192, 48, s, "thruster plume: origin at the nozzle, exhaust toward -x", defs,
          viewbox="-192 -24 192 48")


def main():
    tiles()
    edges()
    components()
    effects()
    decor()
    interactive()
    hulls()
    turrets()
    print(f"wrote {len(written)} SVGs to {os.path.relpath(OUT, ROOT)}")


if __name__ == "__main__":
    main()

"""The gutted bay, into assets/art/components: a primary (square) mount whose system was stripped
for salvage. The machine is gone and the bay it stood in is left: the mounting frame round a
sunken well, the clean square on the well floor where the machine's base kept the grime off, its
hold-down bolts sheared or pulled, the power leads it was wired to unplugged and lying slack from a dead
junction box, the pipes it drew on ending in open flanges or a blanking plate, and the nuts,
washers and bolts the salvage crew left behind. Nothing in it is live, so it has no
emissive layer and no unpowered, damaged or destroyed variant.

One drawing for each primary mount size, `gutted_bay_<size>` (S 1x1 to XL 5x5), each its own
layout like the other components. generate_svgs.py draws them through `gutted_bays`; run this
script alone to redraw just these:

    python3 tools/art/gutted_bay_svgs.py
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from generate_svgs import (INK, DECK_SEAM, STEEL_MID, STEEL_DARK, STEEL_HI, HOUSING, HOUSING_HI, LENS,
                           POWER, PRIMARY, SIZES, T, Art, f, rect, circle, line, poly, path, bevel,
                           plate, rivet, write)

# The well the machine stood in: its floor, the grime on it and the clean square under the machine
PIT, PIT_SEAM, GRIME, CLEAN = "#1d2229", "#161a20", "#0b0d10", "#2a313a"
# Pipes, as the pipe art draws them, and the dark inside an open one
PIPE, PIPE_HI, PIPE_LO, BORE = "#8c96a0", "#b4bec8", "#5a6470", "#05070a"
# A power lead's jacket
JACKET = "#23272e"

# Each size's layout, in tiles of its own canvas: the rim's width (units), the hold-down bolts on a
# grid of rows x cols, the junction boxes (x, y, side the leads hang toward, leads), the pipe stubs
# (x or y along the wall, wall, bore radius, end), the mounting rails (y as a share of the well) and
# how much litter lies about.
LAYOUT = {
    "s": dict(rim=7, bolts=(2, 2), boxes=[(0.22, 0.2, "e", 2)], pipes=[(0.72, "n", 4, "open")],
              rails=[], litter=3),
    "m": dict(rim=10, bolts=(2, 3), boxes=[(0.16, 0.22, "e", 3)],
              pipes=[(0.62, "n", 5, "open"), (0.82, "n", 4, "capped")],
              rails=[0.32, 0.72], litter=4),
    "l": dict(rim=13, bolts=(3, 3), boxes=[(0.13, 0.2, "e", 4)],
              pipes=[(0.55, "n", 7, "open"), (0.72, "n", 5, "torn"), (0.62, "e", 5, "capped")],
              rails=[0.28, 0.5, 0.72], litter=7),
    "xl": dict(rim=18, bolts=(4, 4), boxes=[(0.1, 0.17, "e", 5), (0.88, 0.84, "w", 3)],
               pipes=[(0.42, "n", 9, "open"), (0.58, "n", 7, "torn"), (0.74, "n", 6, "capped"),
                      (0.36, "e", 6, "open"), (0.28, "s", 7, "capped")],
               rails=[0.24, 0.42, 0.6, 0.78], litter=11),
}


def _well(a, w, rim):
    """The mounting frame round the sunken well, and the well floor's own plates."""
    a.b += rect(0, 0, w, w, DECK_SEAM)
    a.b += plate(3, 3, w - 6, w - 6, HOUSING, HOUSING_HI, INK, rx=4)
    # Bolt holes round the frame where the machine's cover plates were fixed; most are empty
    holes = max(2, int(w / 32))
    for i in range(holes):
        t = (i + 0.5) / holes
        for (x, y) in ((3 + t * (w - 6), 3 + rim / 2 - 1.5), (3 + t * (w - 6), w - 3 - rim / 2 + 1.5),
                       (3 + rim / 2 - 1.5, 3 + t * (w - 6)), (w - 3 - rim / 2 + 1.5, 3 + t * (w - 6))):
            a.b += circle(x, y, 1.6, BORE)
    x0 = rim
    s = w - 2 * rim
    a.b += rect(x0, x0, s, s, PIT, rx=2, stroke=INK, sw=2)
    # The floor plates under the machine, seams showing now nothing hides them
    n = max(1, round(s / 48))
    for i in range(1, n):
        p = x0 + s * i / n
        a.b += line(p, x0 + 2, p, x0 + s - 2, PIT_SEAM, sw=1.5)
        a.b += line(x0 + 2, p, x0 + s - 2, p, PIT_SEAM, sw=1.5)
    # Sunken: the frame's lip shades the top and left of the well, the far side catches the light
    a.b += path(f"M{f(x0 + 2)},{f(x0 + s - 2)} L{f(x0 + 2)},{f(x0 + 2)} L{f(x0 + s - 2)},{f(x0 + 2)}",
                stroke=GRIME, sw=3, extra=' opacity="0.8" data-baked="1"')
    a.b += path(f"M{f(x0 + s - 1.5)},{f(x0 + 3)} L{f(x0 + s - 1.5)},{f(x0 + s - 1.5)} L{f(x0 + 3)},{f(x0 + s - 1.5)}",
                stroke=HOUSING_HI, sw=1, extra=' opacity="0.7" data-baked="1"')
    return x0, s


def _grime(a, rnd, x0, s):
    """Years of grime on the well floor, except on the square the machine's base stood on."""
    k = max(3, int(s / 10))
    inner = s * 0.16
    a.b += rect(x0 + inner, x0 + inner, s - 2 * inner, s - 2 * inner, CLEAN, rx=3,
                extra=' opacity="0.9"')
    a.b += rect(x0 + inner, x0 + inner, s - 2 * inner, s - 2 * inner, "none", rx=3,
                extra=f' stroke="{GRIME}" stroke-width="2.5" stroke-dasharray="{f(s / 9)} {f(s / 30)}" opacity="0.55"')
    for _ in range(k):
        # grime collects in the corners and along the walls, out where the base did not cover
        side = rnd.randrange(4)
        along = rnd.uniform(0.05, 0.95) * s
        depth = rnd.uniform(0.02, 0.12) * s
        x, y = {0: (along, depth), 1: (s - depth, along), 2: (along, s - depth), 3: (depth, along)}[side]
        a.b += circle(x0 + x, x0 + y, rnd.uniform(0.04, 0.09) * s, GRIME,
                      extra=f' opacity="{f(rnd.uniform(0.18, 0.32))}"')
    return inner


def _rails(a, rnd, x0, s, inner, rails):
    """The rails the machine was bolted down to, cut through with a torch where it would not come free."""
    for k, share in enumerate(rails):
        y = x0 + s * share
        h = max(4, s / 26)
        cut = rnd.uniform(0.35, 0.65) * s
        gap = max(3, s / 40)
        for (xa, xb) in ((x0 + 3, x0 + cut - gap), (x0 + cut + gap, x0 + s - 3)):
            a.b += rect(xa, y - h / 2, xb - xa, h, STEEL_DARK, stroke=INK, sw=1.2)
            a.b += line(xa + 1, y - h / 2 + 1, xb - 1, y - h / 2 + 1, STEEL_MID, sw=1)
        # the torch cut: scorched, beaded ends
        for xe in (x0 + cut - gap, x0 + cut + gap):
            a.b += circle(xe, y, h * 0.9, GRIME, extra=' opacity="0.55"')
            a.b += line(xe, y - h / 2, xe + rnd.uniform(-1, 1), y + h / 2, "#6b5a48", sw=1.4)


def _bolts(a, rnd, x0, s, inner, rows, cols):
    """The hold-down bolts on the base's square: sheared stubs with a bright broken face, or empty holes."""
    for r in range(rows):
        for c in range(cols):
            x = x0 + inner + 4 + (s - 2 * inner - 8) * (c / (cols - 1) if cols > 1 else 0.5)
            y = x0 + inner + 4 + (s - 2 * inner - 8) * (r / (rows - 1) if rows > 1 else 0.5)
            rb = max(2.2, s / 34)
            if rnd.random() < 0.6:
                # sheared: the stub, its hex nut torn open, and the bright face where it broke
                a.b += poly(_hexagon(x, y, rb * 1.5, rnd.uniform(0, 1)), STEEL_MID, stroke=INK, sw=1)
                a.b += circle(x, y, rb * 0.75, "#a9b3bf", stroke=INK, sw=0.8)
                a.b += line(x - rb * 0.4, y - rb * 0.2, x + rb * 0.3, y + rb * 0.35, "#e3e8ee", sw=0.8)
            else:
                # pulled: just the threaded hole
                a.b += circle(x, y, rb * 1.1, STEEL_DARK, stroke=INK, sw=1)
                a.b += circle(x, y, rb * 0.6, BORE)


def _hexagon(cx, cy, r, turn):
    return [(cx + r * math.cos(turn + i * math.pi / 3), cy + r * math.sin(turn + i * math.pi / 3)) for i in range(6)]


def _box(a, rnd, w, x0, s, bx, by, toward, leads):
    """A junction box on the well's wall, cover hanging open, its terminals bare and its lamp
    dark, and the leads the machine took power from unplugged and lying slack across the floor."""
    u = max(1.0, s / 60)
    bw, bh = 12 * u, 16 * u
    left = toward == "e"
    x = (x0 + 1) if left else (x0 + s - 1 - bw)
    y = w * by - bh / 2 if left else w * by - bh / 2
    a.b += plate(x, y, bw, bh, HOUSING, HOUSING_HI, INK, rx=1.5, sw=1.5)
    a.b += rect(x + 2 * u, y + 3 * u, bw - 4 * u, bh - 6 * u, PIT, stroke=INK, sw=1)
    # bare terminal posts, one for each lead and a spare
    posts = []
    for i in range(leads):
        py = y + 3 * u + (bh - 6 * u) * (i + 0.5) / leads
        px = x + bw / 2
        a.b += circle(px, py, 1.4 * u, "#8a6a3a", stroke=INK, sw=0.6)
        posts.append((px, py))
    # the dead status lamp
    a.b += circle(x + bw / 2, y + 1.6 * u, 1.1 * u, LENS, stroke=INK, sw=0.6)
    # the cover, torn off one hinge and hanging askew
    cx = x + (bw + 1.5 * u if left else -1.5 * u)
    cover = [(cx, y + 1 * u), (cx + (6 if left else -6) * u, y + 3 * u),
             (cx + (7 if left else -7) * u, y + bh + 1 * u), (cx + (1 if left else -1) * u, y + bh - 1 * u)]
    a.b += poly(cover, STEEL_DARK, stroke=INK, sw=1.2)
    a.b += line(cover[0][0], cover[0][1], cover[1][0], cover[1][1], STEEL_HI, sw=0.8)
    # the leads: out of the box, slack across the floor, ending in their plugs
    sgn = 1 if left else -1
    for i, (px, py) in enumerate(posts):
        reach = s * rnd.uniform(0.16, 0.3)
        lo, hi = x0 + 6, x0 + s - 6
        ex = min(hi, max(lo, px + sgn * (bw / 2 + reach)))
        ey = min(hi, max(lo, py + rnd.uniform(-0.12, 0.18) * s))
        mx = px + sgn * (bw / 2 + reach * 0.45)
        my = min(hi, (py + ey) / 2 + rnd.uniform(0.05, 0.12) * s)
        d = f"M{f(px)},{f(py)} Q{f(mx)},{f(my)} {f(ex)},{f(ey)}"
        wdt = max(2.2, 2.6 * u)
        a.b += path(d, stroke=INK, sw=wdt + 1.6)
        a.b += path(d, stroke=JACKET, sw=wdt)
        a.b += path(d, stroke=POWER, sw=max(0.6, wdt * 0.28), extra=' opacity="0.8"')
        # the end: the plug that went into the machine's socket, unplugged, its pins covered
        _plug(a, ex, ey, math.atan2(ey - my, ex - mx), wdt)


def _plug(a, x, y, ang, wdt):
    """A lead's connector: a strain-relief boot off the jacket, a ribbed body with a locking
    collar, and the face with its pin sockets, lying at (x, y) and pointing along ang."""
    ux, uy = math.cos(ang), math.sin(ang)
    vx, vy = -uy, ux

    def box(t0, t1, half, fill, sw=0.9):
        pts = [(x + ux * t0 + vx * half, y + uy * t0 + vy * half), (x + ux * t1 + vx * half, y + uy * t1 + vy * half),
               (x + ux * t1 - vx * half, y + uy * t1 - vy * half), (x + ux * t0 - vx * half, y + uy * t0 - vy * half)]
        a.b += poly(pts, fill, stroke=INK, sw=sw)
        return pts
    # boot, body, collar
    box(-wdt * 0.4, wdt * 0.5, wdt * 0.6, JACKET)
    body = box(wdt * 0.5, wdt * 2.2, wdt * 0.95, STEEL_DARK)
    a.b += line(body[0][0], body[0][1], body[1][0], body[1][1], STEEL_HI, sw=0.7, extra=' opacity="0.8"')
    for k in (0.9, 1.3, 1.7):
        a.b += line(x + ux * wdt * k + vx * wdt * 0.8, y + uy * wdt * k + vy * wdt * 0.8,
                    x + ux * wdt * k - vx * wdt * 0.8, y + uy * wdt * k - vy * wdt * 0.8, INK, sw=0.6, extra=' opacity="0.6"')
    box(wdt * 2.2, wdt * 2.7, wdt * 1.1, STEEL_MID)
    # the face's pin sockets, dark
    fx, fy = x + ux * wdt * 2.45, y + uy * wdt * 2.45
    for o in (-0.5, 0, 0.5):
        a.b += circle(fx + vx * wdt * o, fy + vy * wdt * o, max(0.5, wdt * 0.13), BORE)


def _pipe(a, rnd, w, x0, s, along, wall, r, end):
    """A pipe stub through the well's wall from the ship's pipework, its far end where the machine
    was: an open flange, a torn-off end, or a blanking plate bolted on by a careful crew."""
    length = s * {"open": 0.2, "torn": 0.26, "capped": 0.16}[end]
    # in the wall's frame: the pipe runs from the wall (t = 0) inward along +t
    def at(t, o):
        if wall == "n":
            return w * along + o, x0 - 2 + t
        if wall == "s":
            return w * along + o, x0 + s + 2 - t
        if wall == "e":
            return x0 + s + 2 - t, w * along + o
        return x0 - 2 + t, w * along + o
    (sx, sy), (ex, ey) = at(0, 0), at(length, 0)
    a.b += line(sx, sy, ex, ey, INK, sw=2 * r + 3)
    a.b += line(sx, sy, ex, ey, PIPE, sw=2 * r)
    # lit side of the pipe toward the top-left
    if wall in ("n", "s"):
        a.b += line(sx - r * 0.45, sy, ex - r * 0.45, ey, PIPE_HI, sw=max(1, r * 0.35), extra=' opacity="0.8"')
        a.b += line(sx + r * 0.55, sy, ex + r * 0.55, ey, PIPE_LO, sw=max(1, r * 0.35), extra=' opacity="0.8"')
    else:
        a.b += line(sx, sy - r * 0.45, ex, ey - r * 0.45, PIPE_HI, sw=max(1, r * 0.35), extra=' opacity="0.8"')
        a.b += line(sx, sy + r * 0.55, ex, ey + r * 0.55, PIPE_LO, sw=max(1, r * 0.35), extra=' opacity="0.8"')
    # where it comes through the wall: a flange band
    fx, fy = at(r * 0.9, 0)
    _flange_band(a, fx, fy, wall, r, 0.7)
    if end == "open":
        # the flange the machine was bolted to, its bolts gone and the bore open
        _flange_band(a, ex, ey, wall, r, 1.0)
        a.b += circle(ex, ey, r * 0.75, BORE, stroke=PIPE_LO, sw=1)
        for i in range(4):
            ang = math.pi / 4 + i * math.pi / 2
            a.b += circle(ex + math.cos(ang) * r * 1.25, ey + math.sin(ang) * r * 1.25, max(0.8, r * 0.16), BORE)
        # what was left in it
        a.b += circle(ex + r * 0.2, ey + r * 0.25, r * 0.3, GRIME, extra=' opacity="0.6"')
    elif end == "torn":
        # wrenched off: a jagged, bent lip with the bright metal showing, and a dark run below
        pts = []
        for i in range(9):
            o = -r + 2 * r * i / 8
            t = length + (rnd.uniform(-0.25, 0.35) * r if 0 < i < 8 else 0)
            pts.append(at(t, o))
        back = [at(length - r * 0.6, r), at(length - r * 0.6, -r)]
        a.b += poly(pts + back, PIPE, stroke=INK, sw=1.2)
        a.b += path("M" + " L".join(f"{f(x)},{f(y)}" for x, y in pts), stroke="#e3e8ee", sw=0.9)
        bx, by = at(length - r * 0.15, 0)
        a.b += circle(bx, by, r * 0.55, BORE, extra=' opacity="0.9"')
        drip = [at(length + r * 0.5, -r * 0.2), at(length + r * 2.4, r * 0.3), at(length + r * 1.4, r * 0.8)]
        a.b += poly(drip, GRIME, extra=' opacity="0.5"')
    else:
        # capped off: a blanking plate on the flange, four bolts
        _flange_band(a, ex, ey, wall, r, 1.15)
        a.b += circle(ex, ey, r * 1.05, STEEL_MID, stroke=INK, sw=1.2)
        a.b += circle(ex, ey, r * 0.55, STEEL_DARK, stroke=INK, sw=0.8)
        for i in range(4):
            ang = i * math.pi / 2
            a.b += rivet(ex + math.cos(ang) * r * 0.8, ey + math.sin(ang) * r * 0.8, max(0.7, r * 0.14))


def _flange_band(a, x, y, wall, r, k):
    if wall in ("n", "s"):
        a.b += rect(x - r * 1.5 * k, y - r * 0.35, r * 3 * k, r * 0.7, PIPE_LO, rx=1, stroke=INK, sw=1)
        a.b += line(x - r * 1.5 * k + 1, y - r * 0.35 + 0.8, x + r * 1.5 * k - 1, y - r * 0.35 + 0.8, PIPE_HI, sw=0.8)
    else:
        a.b += rect(x - r * 0.35, y - r * 1.5 * k, r * 0.7, r * 3 * k, PIPE_LO, rx=1, stroke=INK, sw=1)
        a.b += line(x - r * 0.35 + 0.8, y - r * 1.5 * k + 1, x - r * 0.35 + 0.8, y + r * 1.5 * k - 1, PIPE_HI, sw=0.8)


def _litter(a, rnd, x0, s, n):
    """What the salvage crew dropped and left: nuts, washers and bolts."""
    u = 1 + s / 300
    for i in range(n):
        x, y = x0 + rnd.uniform(0.1, 0.9) * s, x0 + rnd.uniform(0.1, 0.9) * s
        kind = i % 3
        if kind == 0:
            a.b += poly(_hexagon(x, y, 2.2 * u, rnd.uniform(0, 1)), STEEL_MID, stroke=INK, sw=0.8)
            a.b += circle(x, y, 0.9 * u, BORE)
        elif kind == 1:
            a.b += circle(x, y, 2 * u, "none", stroke=STEEL_HI, sw=1.1 * u, extra=' opacity="0.8"')
        else:
            ang = rnd.uniform(0, math.pi)
            dx, dy = math.cos(ang) * 4.5 * u, math.sin(ang) * 4.5 * u
            a.b += line(x - dx, y - dy, x + dx, y + dy, INK, sw=2.6 * u)
            a.b += line(x - dx, y - dy, x + dx, y + dy, STEEL_MID, sw=1.4 * u)
            a.b += poly(_hexagon(x - dx, y - dy, 1.8 * u, ang), STEEL_DARK, stroke=INK, sw=0.7)


def gutted_bay(size):
    """The art of one gutted bay: (width, height, Art)."""
    n = PRIMARY[size][0]
    w = n * T
    lay = LAYOUT[size]
    rnd = random.Random(f"gutted_bay_{size}")
    a = Art()
    x0, s = _well(a, w, lay["rim"])
    inner = _grime(a, rnd, x0, s)
    _rails(a, rnd, x0, s, inner, lay["rails"])
    _bolts(a, rnd, x0, s, inner, *lay["bolts"])
    _litter(a, rnd, x0, s, lay["litter"])
    for (along, wall, r, end) in lay["pipes"]:
        _pipe(a, rnd, w, x0, s, along, wall, r, end)
    for (bx, by, toward, leads) in lay["boxes"]:
        _box(a, rnd, w, x0, s, bx, by, toward, leads)
    return w, w, a


def gutted_bays():
    for size in SIZES:
        w, h, a = gutted_bay(size)
        write(f"components/gutted_bay_{size}.svg", w, h, a,
              f"gutted bay {size.upper()}: {w // T}x{h // T} primary mount stripped for salvage, the machine "
              "torn out: unplugged power leads, open and capped pipe ends, sheared hold-down bolts")


if __name__ == "__main__":
    gutted_bays()

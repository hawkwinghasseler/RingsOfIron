"""Main engines, drawn to fill their auxiliary slot. An engine mounts on the outside of the hull,
so its art has no deck or housing behind it: what is not engine is clear, and space shows through.
The canvas is the slot at 64 units a tile with the nozzle toward +y (the top of the canvas) at
rotation 0; the bottom end is the mounting flange that bolts to the hull (and may sit one tile into
it). The same drawing goes inside (components/engine_<size>) and outside (engines/nacelle_<size>).

There are three styles; STYLE picks the one the game uses. generate_svgs.py draws them through
`engine`; run this script alone to redraw a sheet of all three for comparing them:

    python3 tools/art/engine_svgs.py [out_dir]
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from generate_svgs import (T, AUX, SIZES, INK, STEEL, STEEL_MID, STEEL_DARK, STEEL_HI, HOUSING, HOUSING_HI,
                           Art, f, rect, circle, line, poly, path, plate, rivet, glow_def, lit, pipe, pump,
                           light, hazard_band)

STYLES = ["bell", "nacelle", "aerospike"]
# The style the game draws
STYLE = "bell"

GLOW = "#9fd8ff"
FUEL, OXIDIZER = "#b07a2a", "#3f6f9a"
BELL_DARK = "#2a3039"
SOOT = "#14181e"


def flange(a, w, h, key):
    """The mounting flange across the bottom end: a bolted plate with the feed couplings that go
    through the hull, fuel and oxidizer, and a hazard band where the engine meets the ship."""
    d = min(T * 0.36, h * 0.16)
    y = h - d
    a.b += plate(2, y, w - 4, d - 2, HOUSING, HOUSING_HI, INK, rx=3)
    a.b += hazard_band(6, y + 3, w - 12, max(3, d * 0.22), key)
    for k in range(max(2, int(w // 24))):
        bx = 7 + (w - 14) * k / max(1, max(2, int(w // 24)) - 1)
        a.b += rivet(bx, h - d * 0.32, 1.8)
    for sx, col in ((-1, FUEL), (1, OXIDIZER)):
        cx = w / 2 + sx * max(8, w * 0.16)
        a.b += rect(cx - 4, h - d * 0.62, 8, d * 0.5, col, rx=2, stroke=INK, sw=1.2)
    return y



# --- Nozzle geometry ---------------------------------------------------------------
# A bell is drawn to Rao's thrust-optimised contour, the shape of real high-expansion de Laval
# nozzles: past the throat a short circular arc (radius 0.382 throat radii) turns the wall out to
# THETA_N, then a parabola carries it to the exit, closing to THETA_E there, over BELL_FRACTION of
# the length of a 15 degree cone of the same expansion. Most of the widening happens just past the
# throat, and the wall runs nearly straight at the exit.
THETA_N, THETA_E = math.radians(36), math.radians(7)
BELL_FRACTION = 0.9
# Exit over throat radius: an area ratio of about 25 (sea level) to 100 (a vacuum engine)
MIN_EXPANSION, MAX_EXPANSION = 5.0, 10.0


def rao_length(re, rt):
    """Throat to exit of a bell of exit radius `re` and throat radius `rt`."""
    return BELL_FRACTION * (re - rt) / math.tan(math.radians(15))


def throat_for(re, length):
    """The throat radius that makes a bell of exit radius `re` `length` long, the expansion held
    between MIN_EXPANSION and MAX_EXPANSION."""
    k = BELL_FRACTION / math.tan(math.radians(15))
    rt = re * k / (length + k)
    return min(re / MIN_EXPANSION, max(re / MAX_EXPANSION, rt))


def bell_profile(re, rt, length, n=28):
    """The bell's wall as (r, d) points from the throat (d = 0) to the exit (d = length): r out from
    the axis, d along it toward the exit."""
    rd = 0.382 * rt
    pts = []
    for k in range(7):
        a = THETA_N * k / 6
        pts.append((rt + rd * (1 - math.cos(a)), rd * math.sin(a)))
    nr, nd = pts[-1]
    er, ed = re, length
    # the parabola as a quadratic Bezier: its control point where the two tangents meet
    tn, te = math.tan(THETA_N), math.tan(THETA_E)
    qd = ((er - nr) - te * ed + tn * nd) / (tn - te)
    qd = min(max(qd, nd + 1e-3), ed - 1e-3)
    qr = nr + tn * (qd - nd)
    for k in range(1, n + 1):
        t = k / n
        u = 1 - t
        pts.append((u * u * nr + 2 * u * t * qr + t * t * er, u * u * nd + 2 * u * t * qd + t * t * ed))
    return pts


def chamber_profile(rt, rc, conv, n=10):
    """The converging section, from the chamber wall (radius `rc`, `conv` behind the throat) in to
    the throat, as (r, d) points with d negative behind the throat: a rounded shoulder, a straight
    taper, and a rounded throat."""
    pts = []
    for k in range(n + 1):
        t = k / n
        u = 1 - t
        # cubic, level with the axis at both ends
        r = u ** 3 * rc + 3 * u * u * t * rc + 3 * u * t * t * rt + t ** 3 * rt
        d = -conv * (u ** 3 + 3 * u * u * t * 0.55 + 3 * u * t * t * 0.3)
        pts.append((r, d))
    return pts


def mirror(cx, y0, pts):
    """A closed outline from right-hand (r, d) points along an axis at x = cx, d growing toward
    -y from y0."""
    right = [(cx + r, y0 - d) for r, d in pts]
    left = [(cx - r, y0 - d) for r, d in reversed(pts)]
    return "M" + " L".join(f"{f(round(x, 2))},{f(round(y, 2))}" for x, y in right + left) + " Z"


def side(cx, y0, pts, s, k=1.0, inset=0.0):
    """One wall as an open polyline, scaled toward the axis by `k`: s = 1 right, -1 left."""
    return "M" + " L".join(f"{f(round(cx + s * (r * k - inset), 2))},{f(round(y0 - d, 2))}" for r, d in pts)


def radius_at(pts, d):
    for (r0, d0), (r1, d1) in zip(pts, pts[1:]):
        if d0 <= d <= d1:
            return r0 + (r1 - r0) * (d - d0) / max(1e-6, d1 - d0)
    return pts[-1][0]


def nozzle(a, cx, y_exit, re, rt, length, gid, rc=None, conv=None, hoops=3):
    """A de Laval nozzle, exit at `y_exit` toward -y: the combustion chamber's converging section
    into the throat, then the bell. The wall is regeneratively cooled, drawn as the tubes that run
    its length, with stiffening hoops. Returns (y of the throat, y of the chamber end, rc)."""
    rc = rc or rt * 1.9
    conv = conv or rt * 1.6
    yt = y_exit + length
    bell = bell_profile(re, rt, length)
    conv_pts = chamber_profile(rt, rc, conv)
    a.b += path(mirror(cx, yt, conv_pts[:-1] + bell), fill=STEEL_DARK, stroke=INK, sw=2)
    # the tubes, following the wall
    tubes = max(3, int(re // 6))
    for k in range(1, tubes):
        q = k / tubes
        for s in (-1, 1):
            a.b += path(side(cx, yt, bell, s, q), stroke=BELL_DARK, sw=1.2, extra=' opacity="0.8"')
    # hoops across the bell, thicker toward the exit
    for j in range(1, hoops + 1):
        d = length * (0.18 + 0.72 * j / (hoops + 0.4))
        r = radius_at(bell, d)
        a.b += rect(cx - r, yt - d - 1.5, 2 * r, 3, HOUSING, stroke=INK, sw=1)
    # light on the near flank, shade on the far
    a.b += path(side(cx, yt, bell, -1, 1, 2.5), stroke=STEEL_HI, sw=1.4, extra=' opacity="0.75"')
    a.b += path(side(cx, yt, bell, 1, 1, 2.5), stroke=INK, sw=1.4, extra=' opacity="0.5"')
    # the throat band and the exit rim
    a.b += rect(cx - rt - 2, yt - 2, 2 * rt + 4, 4, STEEL_MID, rx=2, stroke=INK, sw=1.2)
    rim = max(3, re * 0.08)
    a.b += rect(cx - re - 1, y_exit, 2 * re + 2, rim, SOOT, rx=rim / 2, stroke=INK, sw=1.5)
    return yt, yt + conv, rc


def chamber(a, cx, y0, rc, ln):
    """The combustion chamber's barrel from `y0` back `ln`, with the injector dome behind it."""
    a.b += plate(cx - rc, y0, 2 * rc, ln, STEEL, rx=min(rc * 0.3, 6))
    for k in range(1, 3):
        y = y0 + ln * k / 3
        a.b += line(cx - rc + 2, y, cx + rc - 2, y, STEEL_MID, sw=1.5)
    dome = rc * 0.55
    a.b += path(f"M{f(cx - rc * 0.9)},{f(y0 + ln)} Q{f(cx - rc * 0.8)},{f(y0 + ln + dome)} {f(cx)},{f(y0 + ln + dome)} "
                f"Q{f(cx + rc * 0.8)},{f(y0 + ln + dome)} {f(cx + rc * 0.9)},{f(y0 + ln)} Z",
                fill=HOUSING_HI, stroke=INK, sw=1.5)
    return y0 + ln + dome


def thrust_frame(a, cx, y_top, half_top, y_fl, w):
    """A short frame of struts from the flange in to the engine, round a gimbal at its middle."""
    foot = min(w / 2 - 7, half_top + max(6, (y_fl - y_top) * 0.6))
    sw = 4 if w > T * 2 else 3
    for sx in (-1, 1):
        x0, x1 = cx + sx * foot, cx + sx * half_top * 0.8
        a.b += line(x0, y_fl, x1, y_top, INK, sw=sw + 1.5)
        a.b += line(x0, y_fl, x1, y_top, STEEL_MID, sw=sw - 1)
    g = max(3.5, min(w * 0.035, 9))
    gy = (y_top + y_fl) / 2
    a.b += line(cx, y_top, cx, y_fl, INK, sw=sw + 1.5)
    a.b += line(cx, y_top, cx, y_fl, STEEL_MID, sw=sw - 1)
    a.b += circle(cx, gy, g, HOUSING, stroke=INK, sw=1.5)
    a.b += circle(cx, gy, g * 0.45, STEEL_HI)


# What the frame between the engine and its flange takes at the least
FRAME = T * 0.22


def chamber_dims(rt, yt, y_fl, w):
    """The combustion chamber behind a throat of radius `rt` at `yt`: (radius, converging length,
    barrel length), as big as the slot leaves room for ahead of the frame."""
    rc = min(rt * 3.6, w * 0.32)
    conv = (rc - rt) * 1.1
    dome = rc * 0.55
    ln = min(rc * 2.6, y_fl - FRAME - yt - conv - dome)
    return rc, conv, max(rc * 0.5, ln)


def feed_lines(a, cx, y_from, y_fl, w, size, xs):
    """Fuel and oxidizer lines from the flange's couplings up to the engine at x offsets `xs`."""
    for sx, col in ((-1, FUEL), (1, OXIDIZER)):
        pipe(a, cx + sx * xs, y_from, cx + sx * max(8, w * 0.16), y_fl, 4 if size == "s" else 6, col)


def turbopumps(a, cx, y, half, w, size):
    if size == "s":
        return
    r = max(7, min(w * 0.08, 22))
    for sx in (-1, 1):
        pump(a, cx + sx * min(half + r, w / 2 - r - 3), y, r)


# --- The three styles -------------------------------------------------------------
# Each draws into Art `a` for a w x h slot and returns its silhouette as an SVG path, which clips
# the damage drawn over it.

def bell_layout(w, h, y_fl, re, body):
    """How long a bell of exit radius `re` can be, leaving `body` (in throat radii) behind its throat
    for the chamber, and its throat radius."""
    room = y_fl - 2 - T * 0.3
    length = rao_length(re, re / MAX_EXPANSION)
    rt = throat_for(re, length)
    # what the chamber needs behind the throat must fit as well
    while length + rt * body > room and length > re * 0.5:
        length *= 0.97
        rt = throat_for(re, length)
    return length, rt


def style_bell(a, w, h, size, gid):
    """A pump-fed chemical engine on a thrust frame: a big high-expansion bell filling the slot's
    width, the combustion chamber and its turbopumps behind the throat."""
    cx = w / 2
    y_fl = flange(a, w, h, f"bell_{size}")
    re = w / 2 - 3
    length, rt = bell_layout(w, h, y_fl, re, 6.5)
    yt = 2 + length
    rc, conv, ch_len = chamber_dims(rt, yt, y_fl, w)
    # the chamber is drawn first so the converging section sits over its front edge
    y_end = chamber(a, cx, yt + conv - 1, rc, ch_len)
    nozzle(a, cx, 2, re, rt, length, gid, rc=rc, conv=conv, hoops=2 if size == "s" else 3)
    turbopumps(a, cx, yt + conv + ch_len * 0.5, rc * 0.85, w, size)
    thrust_frame(a, cx, y_end, rc * 0.6, y_fl, w)
    feed_lines(a, cx, y_end - 2, y_fl, w, size, rc * 0.5)
    light(a, cx, yt + conv + ch_len * 0.5, 3 if size == "s" else 4, GLOW)
    return (mirror(cx, yt, [(rc + 2, -(y_end - yt))] + chamber_profile(rt, rc, conv)[:-1]
                   + bell_profile(re + 2, rt, length))
            + f" M2,{f(y_end - 4)} H{f(w - 2)} V{f(h - 2)} H2 Z")


def style_nacelle(a, w, h, size, gid):
    """An armoured engine: the chamber and pumps shut in a short riveted cowl that ends just past
    the throat, the whole bell bare in front of it, on a thrust frame back to the flange."""
    cx = w / 2
    y_fl = flange(a, w, h, f"nacelle_{size}")
    re = w / 2 - 4
    length, rt = bell_layout(w, h, y_fl, re, 7.5)
    yt = 2 + length
    nozzle(a, cx, 2, re, rt, length, gid, hoops=2 if size == "s" else 3)
    # the cowl: from a lip just past the throat back over the chamber and pumps
    bell = bell_profile(re, rt, length)
    lip_d = length * 0.1
    lip_r = radius_at(bell, lip_d) + 3
    rc, conv, ch_len = chamber_dims(rt, yt, y_fl, w)
    cw = min(w / 2 - 5, max(rc + 6, lip_r + 6))
    top = yt - lip_d
    ln = min(lip_d + conv + ch_len + rc * 0.55 + 4, y_fl - FRAME - top)
    bot = top + ln
    r = min(cw * 0.6, ln * 0.3)
    d = (f"M{f(cx - lip_r)},{f(top)} H{f(cx + lip_r)} C{f(cx + cw)},{f(top)} {f(cx + cw)},{f(top + r * 0.4)} "
         f"{f(cx + cw)},{f(top + r)} V{f(bot - r * 0.5)} Q{f(cx + cw)},{f(bot)} {f(cx + cw - r * 0.5)},{f(bot)} "
         f"H{f(cx - cw + r * 0.5)} Q{f(cx - cw)},{f(bot)} {f(cx - cw)},{f(bot - r * 0.5)} V{f(top + r)} "
         f"C{f(cx - cw)},{f(top + r * 0.4)} {f(cx - cw)},{f(top)} {f(cx - lip_r)},{f(top)} Z")
    a.b += path(d, fill=STEEL, stroke=INK, sw=2)
    a.b += path(f"M{f(cx - cw + 3)},{f(bot - r * 0.5)} V{f(top + r)}", stroke=STEEL_HI, sw=1.5)
    a.b += path(f"M{f(cx + cw - 3)},{f(bot - r * 0.5)} V{f(top + r)}", stroke=STEEL_MID, sw=2)
    # the lip's armour ring, and the plate seams and rivets behind it
    a.b += rect(cx - lip_r - 2, top - 2, 2 * lip_r + 4, 5, HOUSING, rx=2, stroke=INK, sw=1.2)
    seams = max(2, int(ln // (T * 0.5)))
    for k in range(1, seams + 1):
        y = top + r * 0.5 + (ln - r) * k / (seams + 1)
        a.b += line(cx - cw + 2, y, cx + cw - 2, y, INK, sw=1.5)
        for rx_ in (cx - cw * 0.75, cx + cw * 0.75):
            a.b += rivet(rx_, y + 4, 1.5)
    # intake scoops on the flanks
    for sx in (-1, 1):
        x0 = cx + sx * cw
        sl = ln * 0.35
        y0 = top + ln * 0.45
        a.b += poly([(x0, y0), (x0 + sx * max(3, w * 0.035), y0 + sl * 0.2), (x0 + sx * max(3, w * 0.035), y0 + sl),
                     (x0, y0 + sl)], HOUSING, stroke=INK, sw=1.5)
    thrust_frame(a, cx, bot, cw * 0.7, y_fl, w)
    feed_lines(a, cx, bot - 2, y_fl, w, size, cw * 0.4)
    light(a, cx, top + ln * 0.55, 3 if size == "s" else 4, GLOW)
    return (mirror(cx, yt, [(cw + 2, -(bot - yt)), (cw + 2, lip_d)] + [p for p in bell_profile(re + 2, rt, length) if p[1] > lip_d])
            + f" M2,{f(bot - 4)} H{f(w - 2)} V{f(h - 2)} H2 Z")


def style_aerospike(a, w, h, size, gid):
    """A linear aerospike: a row of thrust cells firing along both faces of a plug that tapers on a
    parabola, steep at the cells and running out parallel at its truncated end, the cells' manifold
    and pumps close behind on a short thrust frame. The whole engine sits back against the frame,
    so its truncated end may stand short of the slot's nozzle end."""
    cx = w / 2
    y_fl = flange(a, w, h, f"spike_{size}")
    bw = w - 8
    room = y_fl - 2 - T * 0.3
    spike = min(bw * 1.3, room * 0.75)
    tip = bw * 0.2
    ch = max(10, min(T * 0.35, spike * 0.18))
    mh = max(10, spike * 0.35)
    # back against the frame
    top = max(2, y_fl - FRAME - (spike + ch - 4 + mh))
    base = top + spike
    n = 24
    # the plug's half width from the cells (s = 0) to the truncated end (s = 1)
    pts = [(tip / 2 + (bw / 2 - tip / 2) * (1 - k / n) ** 2, spike * k / n) for k in range(n + 1)]
    a.b += path(mirror(cx, base, pts), fill=STEEL_DARK, stroke=INK, sw=2)
    # the near face lit, the far face shaded, the ridge down the middle
    a.b += path("M" + " L".join(f"{f(round(cx - r + 2, 2))},{f(round(base - d, 2))}" for r, d in pts)
                + " L" + f"{f(cx)},{f(top)} L{f(cx)},{f(base)} Z", fill=STEEL_MID, extra=' opacity="0.55"')
    a.b += line(cx, base - 2, cx, top + 1, INK, sw=1.2)
    # cooling channels following the taper
    for q in (0.3, 0.6, 0.85):
        for s in (-1, 1):
            a.b += path(side(cx, base, pts, s, q), stroke=BELL_DARK, sw=1.4)
    # the truncated base: a sooty end plate
    a.b += rect(cx - tip / 2 - 1, top, tip + 2, 4, SOOT, rx=1.5, stroke=INK, sw=1.2)
    # the row of thrust cells along the plug's foot
    cells = max(3, int(bw // 14))
    cw = bw / cells
    for i in range(cells):
        x = cx - bw / 2 + i * cw
        a.b += rect(x + 1, base - 3, cw - 2, ch, STEEL, rx=2, stroke=INK, sw=1)
        a.b += line(x + cw / 2, base - 1, x + cw / 2, base + ch - 4, STEEL_MID, sw=1.2)
    # the manifold feeding them and its pumps
    my = base + ch - 4
    mh = max(10, min(mh, y_fl - FRAME - my))
    a.b += plate(cx - bw * 0.45, my, bw * 0.9, mh, HOUSING, HOUSING_HI, INK, rx=4)
    for k in range(1, max(2, int(mh // (T * 0.3)))):
        y = my + mh * k / max(2, int(mh // (T * 0.3)))
        a.b += line(cx - bw * 0.45 + 3, y, cx + bw * 0.45 - 3, y, INK, sw=1.2, extra=' opacity="0.7"')
    turbopumps(a, cx, my + mh * 0.5, bw * 0.4, w, size)
    thrust_frame(a, cx, my + mh, bw * 0.3, y_fl, w)
    feed_lines(a, cx, my + mh - 2, y_fl, w, size, bw * 0.2)
    light(a, cx, my + mh * 0.5, 3 if size == "s" else 4, GLOW)
    return (mirror(cx, base, [(bw / 2 + 3, -(my + mh - base))] + [(r + 3, d) for r, d in pts])
            + f" M2,{f(my + mh - 4)} H{f(w - 2)} V{f(h - 2)} H2 Z")


DRAW = {"bell": style_bell, "nacelle": style_nacelle, "aerospike": style_aerospike}


def engine(size, style=STYLE, key="engine"):
    """An engine filling its auxiliary slot of `size`, nozzle toward the top of the canvas.
    Returns (w, h, art, silhouette): what lies outside the silhouette is left clear."""
    tw, th = AUX[size]
    w, h = tw * T, th * T
    a = Art()
    gid = f"{key}_{style}_{size}_throat"
    a.defs = glow_def(gid, GLOW)
    sil = DRAW[style](a, w, h, size, gid)
    return w, h, a, sil


def sheet(out_dir):
    """Writes every style at every size, burning, into out_dir, and an HTML page laying them out."""
    from generate_svgs import write
    import generate_svgs
    os.makedirs(out_dir, exist_ok=True)
    old = generate_svgs.OUT
    generate_svgs.OUT = out_dir
    try:
        for style in STYLES:
            for size in SIZES:
                w, h, a, _ = engine(size, style, key=f"sheet_{style}")
                write(f"{style}_{size}.svg", w, h, a, f"engine {style} {size}", unpowered=f"{style}_{size}_off.svg")
    finally:
        generate_svgs.OUT = old


if __name__ == "__main__":
    sheet(sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), "engine_sheet"))

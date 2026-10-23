"""Main engines, drawn to fill their auxiliary slot. An engine mounts on the outside of the hull,
so its art has no deck or housing behind it: what is not engine is clear, and space shows through.
The canvas is the slot at 64 units a tile with the nozzle toward +y (the top of the canvas) at
rotation 0; the bottom end is the mounting flange that bolts to the hull (and may sit one tile into
it). The same drawing goes inside (components/engine_<size>) and outside (engines/nacelle_<size>).

There are five styles; STYLE picks the one the game uses. generate_svgs.py draws them through
`engine`; run this script alone to redraw a sheet of all five for comparing them:

    python3 tools/art/engine_svgs.py [out_dir]
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from generate_svgs import (T, AUX, SIZES, INK, STEEL, STEEL_MID, STEEL_DARK, STEEL_HI, HOUSING, HOUSING_HI,
                           Art, f, rect, circle, line, poly, path, plate, rivet, glow_def, lit, pipe, pump, tank,
                           light, hazard_band)

STYLES = ["bell", "cluster", "nacelle", "aerospike", "torch"]
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


def bell(a, cx, top, base, exit_w, throat_w, gid, bands=3):
    """A bell nozzle seen from the side: from its throat at `base` it flares to `exit_w` at `top`,
    with cooling bands, a highlight and the sooty exit rim."""
    hw, tw = exit_w / 2, throat_w / 2
    ln = base - top
    d = (f"M{f(cx - tw)},{f(base)} C{f(cx - tw)},{f(base - ln * 0.45)} {f(cx - hw)},{f(top + ln * 0.25)} "
         f"{f(cx - hw)},{f(top)} L{f(cx + hw)},{f(top)} C{f(cx + hw)},{f(top + ln * 0.25)} "
         f"{f(cx + tw)},{f(base - ln * 0.45)} {f(cx + tw)},{f(base)} Z")
    a.b += path(d, fill=STEEL_DARK, stroke=INK, sw=2)
    for t_ in [(k + 1) / (bands + 1) for k in range(bands)]:
        y = base - ln * t_
        # the bell's half width at this height, following the curve closely enough
        k = t_ ** 1.6
        bw = tw + (hw - tw) * k
        a.b += line(cx - bw + 3, y, cx + bw - 3, y, BELL_DARK, sw=2.5)
    a.b += path(f"M{f(cx - tw + 2)},{f(base - 2)} C{f(cx - tw + 2)},{f(base - ln * 0.45)} "
                f"{f(cx - hw + 3)},{f(top + ln * 0.25)} {f(cx - hw + 3)},{f(top + 3)}",
                stroke=STEEL_HI, sw=1.2, extra=' opacity="0.7"')
    rim = max(3, ln * 0.06)
    a.b += rect(cx - hw, top, exit_w, rim, SOOT, rx=rim / 2, stroke=INK, sw=1.5)
    a.e += lit(rect(cx - hw + 2, top + 0.5, exit_w - 4, rim - 1, GLOW, rx=rim / 2, extra=' opacity="0.85"')
               + circle(cx, base, throat_w * 0.9, f"url(#{gid})"), "glow")


def outline_body(x, y, w, h, r):
    return f"M{f(x + r)},{f(y)} H{f(x + w - r)} Q{f(x + w)},{f(y)} {f(x + w)},{f(y + r)} V{f(y + h - r)} " \
           f"Q{f(x + w)},{f(y + h)} {f(x + w - r)},{f(y + h)} H{f(x + r)} Q{f(x)},{f(y + h)} {f(x)},{f(y + h - r)} " \
           f"V{f(y + r)} Q{f(x)},{f(y)} {f(x + r)},{f(y)} Z"


def propellant_tanks(a, x, y, w, h, n):
    """`n` pairs of spherical-ended propellant tanks, fuel and oxidizer side by side."""
    span = h / n
    tw = (w - 6) / 2
    for k in range(n):
        ty = y + k * span + 3
        tank(a, x, ty, tw, span - 6, "#4a3a22", "#8a6a3a")
        tank(a, x + tw + 6, ty, tw, span - 6, "#22344a", "#3f6f9a")


# --- The five styles -------------------------------------------------------------
# Each draws into Art `a` for a w x h slot and returns its silhouette as an SVG path, which clips
# the damage drawn over it.

def style_bell(a, w, h, size, gid):
    """A classic chemical engine: one big bell, the combustion chamber and turbopumps behind it,
    and on the longer slots the propellant tanks it runs off between them and the flange."""
    cx = w / 2
    y_fl = flange(a, w, h, f"bell_{size}")
    bell_len = min(h * 0.46, w * 1.25)
    base = 2 + bell_len
    ch_w = w * (0.5 if size == "s" else 0.42)
    ch_h = min(h * 0.18, T * 1.1)
    body_top = base + ch_h * 0.15
    # thrust frame: the struts from the flange to the chamber
    for sx in (-1, 1):
        a.b += line(cx + sx * (w / 2 - 6), y_fl, cx + sx * ch_w / 2, body_top + ch_h, INK, sw=5)
        a.b += line(cx + sx * (w / 2 - 6), y_fl, cx + sx * ch_w / 2, body_top + ch_h, STEEL_MID, sw=3)
    # propellant tanks on the long slots
    tanks_top = body_top + ch_h + 10
    if size in ("l", "xl") and y_fl - tanks_top > T:
        propellant_tanks(a, cx - w * 0.36, tanks_top, w * 0.72, y_fl - tanks_top - 6, 2 if size == "l" else 3)
    else:
        for sx, col in ((-1, FUEL), (1, OXIDIZER)):
            pipe(a, cx + sx * ch_w * 0.25, body_top + ch_h, cx + sx * max(8, w * 0.16), y_fl, 4 if size == "s" else 6, col)
    a.b += plate(cx - ch_w / 2, body_top, ch_w, ch_h, STEEL, rx=ch_w * 0.2)
    for k in range(1, 4):
        y = body_top + ch_h * k / 4
        a.b += line(cx - ch_w / 2 + 3, y, cx + ch_w / 2 - 3, y, STEEL_MID, sw=2)
    if size != "s":
        for sx in (-1, 1):
            pump(a, cx + sx * (ch_w / 2 + w * 0.1), body_top + ch_h * 0.6, max(7, min(w * 0.09, 20)))
    bell(a, cx, 2, base + 2, w - 4, w * 0.26, gid, 3 if size in ("s", "m") else 5)
    light(a, cx, body_top + ch_h / 2, 3 if size == "s" else 4, GLOW)
    return (f"M2,2 H{f(w - 2)} V{f(base)} L{f(w - 2)},{f(h - 2)} H2 Z")


def style_cluster(a, w, h, size, gid):
    """A cluster of smaller bells on one thrust plate, fed from a spherical tank stack: more bells
    the wider the slot."""
    cx = w / 2
    y_fl = flange(a, w, h, f"cluster_{size}")
    cols = {"s": 2, "m": 3, "l": 4, "xl": 5}[size]
    pitch = (w - 4) / cols
    bl = min(h * 0.3, pitch * 1.5)
    # the thrust plate the bells hang from
    plate_y = 2 + bl
    a.b += plate(3, plate_y, w - 6, max(8, T * 0.18), STEEL, rx=3)
    for i in range(cols):
        bell(a, 2 + pitch * (i + 0.5), 2, 2 + bl, pitch - 3, pitch * 0.3, gid, 2)
    # truss behind the plate, then a stack of round tanks down to the flange
    body_top = plate_y + max(8, T * 0.18)
    a.b += rect(w * 0.16, body_top, w * 0.68, y_fl - body_top, HOUSING, stroke=INK, sw=1.5)
    for k in range(int((y_fl - body_top) // 18)):
        y0 = body_top + k * 18
        a.b += line(w * 0.16 + 2, y0, w * 0.84 - 2, y0 + 18, STEEL_MID, sw=2)
        a.b += line(w * 0.84 - 2, y0, w * 0.16 + 2, y0 + 18, STEEL_MID, sw=2)
    span = y_fl - body_top - 4
    nt = max(1, int(span // (w * 0.62)))
    r = min(w * 0.3, span / nt / 2 - 2)
    for k in range(nt):
        ty = body_top + 2 + span * (k + 0.5) / nt
        col, hi = (("#4a3a22", "#8a6a3a"), ("#22344a", "#3f6f9a"))[k % 2]
        a.b += circle(cx, ty, r, col, stroke=INK, sw=2)
        a.b += circle(cx - r * 0.3, ty - r * 0.3, r * 0.3, hi, extra=' opacity="0.8"')
        a.b += line(cx - r, ty, cx + r, ty, INK, sw=1.2, extra=' opacity="0.6"')
    light(a, w - 9, body_top + 8, 2.5, GLOW)
    return f"M2,2 H{f(w - 2)} V{f(h - 2)} H2 Z"


def style_nacelle(a, w, h, size, gid):
    """An armoured nacelle: the whole engine inside a streamlined pod of riveted plates, with
    only a short bell showing at its open end and intake scoops along its flanks."""
    cx = w / 2
    y_fl = flange(a, w, h, f"nacelle_{size}")
    r = w * 0.32
    pod_top = min(h * 0.16, w * 0.5) + 2
    # the pod: rounded at the nozzle end, flared to the flange
    d = (f"M{f(w * 0.1)},{f(pod_top + r)} Q{f(w * 0.1)},{f(pod_top)} {f(w * 0.1 + r)},{f(pod_top)} "
         f"H{f(w * 0.9 - r)} Q{f(w * 0.9)},{f(pod_top)} {f(w * 0.9)},{f(pod_top + r)} "
         f"V{f(y_fl - w * 0.12)} L{f(w - 3)},{f(y_fl)} H3 L{f(w * 0.1)},{f(y_fl - w * 0.12)} Z")
    a.b += path(d, fill=STEEL, stroke=INK, sw=2)
    a.b += path(f"M{f(w * 0.1 + 3)},{f(y_fl - w * 0.12)} V{f(pod_top + r)} Q{f(w * 0.1 + 3)},{f(pod_top + 3)} "
                f"{f(w * 0.1 + r)},{f(pod_top + 3)}", stroke=STEEL_HI, sw=1.5)
    a.b += path(f"M{f(w * 0.9 - 3)},{f(pod_top + r)} V{f(y_fl - w * 0.12)}", stroke=STEEL_MID, sw=2)
    # armour seams and rivets down the pod
    seams = max(2, int((y_fl - pod_top - r) // (T * 0.55)))
    for k in range(1, seams + 1):
        y = pod_top + r * 0.6 + (y_fl - pod_top - r * 0.6) * k / (seams + 1)
        a.b += line(w * 0.1 + 2, y, w * 0.9 - 2, y, INK, sw=1.5)
        for rx_ in (w * 0.18, w * 0.82):
            a.b += rivet(rx_, y + 4, 1.5)
    # a spine plate down the middle
    a.b += rect(cx - w * 0.08, pod_top + r * 0.8, w * 0.16, y_fl - pod_top - r * 1.2, STEEL_MID, rx=2,
                stroke=INK, sw=1.2)
    # intake scoops on the flanks
    for sx in (-1, 1):
        x0 = cx + sx * w * 0.4
        y0 = pod_top + (y_fl - pod_top) * 0.55
        sl = min(T * 0.9, (y_fl - pod_top) * 0.25)
        a.b += poly([(x0, y0), (x0 + sx * w * 0.06, y0 + sl * 0.2), (x0 + sx * w * 0.06, y0 + sl), (x0, y0 + sl)],
                    HOUSING, stroke=INK, sw=1.5)
    # the short bell out of the open end
    bell(a, cx, 2, pod_top + 4, w * 0.58, w * 0.4, gid, 1)
    light(a, cx, pod_top + r * 1.1, 3 if size == "s" else 4, GLOW)
    return (f"M{f(w * 0.2)},2 H{f(w * 0.8)} L{f(w * 0.9)},{f(pod_top)} V{f(y_fl - w * 0.12)} L{f(w - 2)},{f(h - 2)} "
            f"H2 L{f(w * 0.1)},{f(y_fl - w * 0.12)} V{f(pod_top)} Z")


def style_aerospike(a, w, h, size, gid):
    """A linear aerospike: a long tapering plug down the middle with banks of small combustion
    cells firing along both of its faces, and a squared-off body behind."""
    cx = w / 2
    y_fl = flange(a, w, h, f"spike_{size}")
    spike_len = min(h * 0.5, w * 1.4)
    base = 2 + spike_len
    bw = w * 0.62
    # the body: a box with radiator ribs
    a.b += plate(w * 0.08, base, w * 0.84, y_fl - base, HOUSING, HOUSING_HI, INK, rx=3)
    ribs = max(2, int((y_fl - base) // 18))
    for k in range(1, ribs):
        y = base + (y_fl - base) * k / ribs
        a.b += line(w * 0.12, y, w * 0.88, y, INK, sw=1.5)
        a.b += line(w * 0.12, y + 1.5, w * 0.88, y + 1.5, HOUSING_HI, sw=0.8, extra=' opacity="0.7"')
    # combustion cells across the base of the plug
    cells = max(2, int(bw // 12))
    cw = bw / cells
    for i in range(cells):
        x = cx - bw / 2 + i * cw
        a.b += rect(x + 1, base - 7, cw - 2, 9, STEEL_MID, rx=1.5, stroke=INK, sw=1)
    # the plug: a wedge tapering toward the nozzle end, its faces shaded
    tip = w * 0.1
    a.b += poly([(cx - bw / 2, base - 6), (cx + bw / 2, base - 6), (cx + tip / 2, 3), (cx - tip / 2, 3)],
                STEEL_DARK, stroke=INK, sw=2)
    a.b += poly([(cx - bw / 2 + 3, base - 7), (cx, base - 7), (cx, 4), (cx - tip / 2 + 1, 4)], STEEL_MID,
                extra=' opacity="0.6"')
    a.b += line(cx, base - 7, cx, 4, INK, sw=1.2)
    for t_ in (0.25, 0.5, 0.75):
        y = base - 6 - (base - 9) * t_
        hw = bw / 2 + (tip / 2 - bw / 2) * t_
        a.b += line(cx - hw + 2, y, cx + hw - 2, y, BELL_DARK, sw=2)
    # the exhaust sheets along the plug's two faces
    a.e += lit(poly([(cx - bw / 2 - 1, base - 6), (cx - bw / 2 + 5, base - 6), (cx - tip / 2 + 1, 3),
                     (cx - tip / 2 - 2, 3)], GLOW, extra=' opacity="0.8"')
               + poly([(cx + bw / 2 + 1, base - 6), (cx + bw / 2 - 5, base - 6), (cx + tip / 2 - 1, 3),
                       (cx + tip / 2 + 2, 3)], GLOW, extra=' opacity="0.8"')
               + rect(cx - bw / 2, base - 6, bw, 3, "#ffffff", extra=' opacity="0.7"')
               + circle(cx, 6, tip * 1.2, f"url(#{gid})"), "glow")
    light(a, cx, base + (y_fl - base) * 0.5, 3 if size == "s" else 4, GLOW)
    return (f"M{f(cx - tip)},2 H{f(cx + tip)} L{f(cx + bw / 2 + 3)},{f(base - 8)} H{f(w * 0.92)} "
            f"V{f(y_fl)} H{f(w - 2)} V{f(h - 2)} H2 V{f(y_fl)} H{f(w * 0.08)} V{f(base - 8)} "
            f"H{f(cx - bw / 2 - 3)} Z")


def style_torch(a, w, h, size, gid):
    """A magnetic torch drive: a slim spine strung with field coils, a spherical core at the
    mounting end, and at the nozzle end an open cage of magnet struts holding the plasma."""
    cx = w / 2
    y_fl = flange(a, w, h, f"torch_{size}")
    cage = min(h * 0.32, w * 1.0)
    core_r = min(w * 0.34, (y_fl - cage) * 0.22)
    core_y = y_fl - core_r - 2
    sp = w * 0.18
    # radiator vanes either side of the spine on the long slots
    if size in ("l", "xl"):
        for sx in (-1, 1):
            x0 = cx + sx * (sp / 2 + 3)
            x1 = cx + sx * (w / 2 - 4)
            y0, y1 = cage + 18, core_y - core_r - 10
            a.b += rect(min(x0, x1), y0, abs(x1 - x0), y1 - y0, "#2a2f38", stroke=INK, sw=1.5)
            for k in range(int((y1 - y0) // 9)):
                yy = y0 + 4 + k * 9
                a.b += line(min(x0, x1) + 2, yy, max(x0, x1) - 2, yy, "#5a3a2a", sw=2, extra=' opacity="0.8"')
    # the spine
    a.b += plate(cx - sp / 2, cage, sp, core_y - cage, STEEL_MID, rx=3)
    # field coils along it
    coils = max(2, int((core_y - core_r - cage) // (T * 0.45)))
    for k in range(coils):
        y = cage + 6 + (core_y - core_r - cage - 12) * k / max(1, coils - 1)
        cw = w * (0.56 if size == "s" else 0.5)
        a.b += rect(cx - cw / 2, y - 4, cw, 8, "#8a6a3a", rx=3, stroke=INK, sw=1.5)
        a.b += line(cx - cw / 2 + 3, y - 1.5, cx + cw / 2 - 3, y - 1.5, "#b38a4a", sw=1.2)
    # the core
    a.b += circle(cx, core_y, core_r, STEEL_DARK, stroke=INK, sw=2)
    a.b += circle(cx, core_y, core_r * 0.62, HOUSING, stroke=INK, sw=1.5)
    a.b += circle(cx - core_r * 0.3, core_y - core_r * 0.3, core_r * 0.22, STEEL_HI, extra=' opacity="0.5"')
    a.e += lit(circle(cx, core_y, core_r * 0.4, "#ffe0b8", extra=' opacity="0.8"'), "glow")
    # the magnetic nozzle: a ring at the spine's end and struts curving out to the slot corners
    ring_y = cage
    a.b += rect(cx - w * 0.3, ring_y - 5, w * 0.6, 10, "#8a6a3a", rx=4, stroke=INK, sw=2)
    for sx in (-1, 1):
        for t_ in (0.45, 1.0):
            x_end = cx + sx * (w / 2 - 3) * t_
            a.b += path(f"M{f(cx + sx * w * 0.28)},{f(ring_y)} Q{f(cx + sx * w * 0.42 * t_)},{f(ring_y * 0.55)} "
                        f"{f(x_end)},{f(3)}", stroke=INK, sw=5)
            a.b += path(f"M{f(cx + sx * w * 0.28)},{f(ring_y)} Q{f(cx + sx * w * 0.42 * t_)},{f(ring_y * 0.55)} "
                        f"{f(x_end)},{f(3)}", stroke=STEEL_MID, sw=3)
    a.b += line(4, 3, w - 4, 3, INK, sw=4)
    a.b += line(4, 3, w - 4, 3, "#8a6a3a", sw=2)
    # the plasma held in the cage
    a.e += lit(path(f"M{f(cx - w * 0.22)},{f(ring_y)} Q{f(cx - w * 0.3)},{f(ring_y * 0.4)} {f(cx - w * 0.38)},4 "
                    f"H{f(cx + w * 0.38)} Q{f(cx + w * 0.3)},{f(ring_y * 0.4)} {f(cx + w * 0.22)},{f(ring_y)} Z",
                    fill=GLOW, extra=' opacity="0.45"')
               + circle(cx, ring_y * 0.55, w * 0.22, f"url(#{gid})"), "glow")
    return f"M2,1 H{f(w - 2)} L{f(cx + w * 0.3)},{f(cage + 6)} V{f(y_fl)} H{f(w - 2)} V{f(h - 2)} H2 V{f(y_fl)} " \
           f"H{f(cx - w * 0.3)} V{f(cage + 6)} Z"


DRAW = {"bell": style_bell, "cluster": style_cluster, "nacelle": style_nacelle, "aerospike": style_aerospike,
        "torch": style_torch}


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

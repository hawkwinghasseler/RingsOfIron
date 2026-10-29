"""Crew in suits, into assets/art/crew: a pressure suit (IVA) and an EVA suit for each crew look in
generate_svgs.CREW, on their feet, down and dead, and the EVA suit's poses outside: crawling hand
over hand along the hull (two frames, the limbs alternating) and floating free. Top-down, centered
on the origin, facing +y (SVG up), like the rest of the crew.

The pressure suit is a soft grey coverall with a clear bubble helmet and a small air pack, worn
inside the ship. The EVA suit is the bulky white one with a gold visor and the jetpack on its back.
The look's trim color marks the shoulders, and the EVA helmet's crown, so a crew member can still
be told from a dock hand in a suit. generate_svgs.py draws them through `suits`; run this script
alone to redraw just these:

    python3 tools/art/suit_svgs.py
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from generate_svgs import (INK, CREW, SKIN, SKIN_PALE, Art, f, rect, circle, line, path, write,
                           collider_circle, grey)

# The pressure suit's fabric, its shade, the air pack, the neck ring and the bubble
IVA, IVA_SHADE, IVA_PACK, IVA_RING, BUBBLE = "#b9c2cc", "#8a949f", "#6d7782", "#5d6670", "#cfeaff"
# The EVA suit, its shade, its gloves and boots, the jetpack and the visor
EVA, EVA_SHADE, EVA_GLOVE, BOOT = "#e9ecf0", "#b8bec6", "#c9ced6", "#3a3f47"
PACK, PACK_DARK, VISOR, VISOR_HI = "#5b6675", "#2f3641", "#d9a520", "#ffe9a8"
GLOVE = "#4a5058"
# The tether's clip on the EVA suit's hip, the color of the line itself
LINE = "#e8b830"


def _bubble(a, cx, cy, r):
    """A clear bubble helmet over a head at (cx, cy): the neck ring, the glass and its sheen."""
    a.b += circle(cx, cy, r + 0.6, "none", stroke=IVA_RING, sw=2.4)
    a.b += circle(cx, cy, r, BUBBLE, stroke=INK, sw=1.5, extra=' fill-opacity="0.3"')
    a.b += path(f"M{f(cx - r * 0.62)},{f(cy - r * 0.55)} C{f(cx - r * 0.4)},{f(cy - r * 0.86)} "
                f"{f(cx - r * 0.05)},{f(cy - r * 0.98)} {f(cx + r * 0.25)},{f(cy - r * 0.95)}",
                stroke="#ffffff", sw=1.4, extra=' opacity="0.75"')


def _head(a, hair):
    """A head from above, facing +y, as generate_svgs.crew_body draws it."""
    a.b += circle(0, -1, 7.5, SKIN, stroke=INK, sw=1.8)
    a.b += path("M-7.5,-1 C-7.5,4 -4,6.5 0,6.5 C4,6.5 7.5,4 7.5,-1 C7.5,-4 5,-6 0,-6 C-5,-6 -7.5,-4 -7.5,-1 Z",
                fill=hair, stroke=INK, sw=1.2)
    a.b += path("M-1.5,-8.5 L0,-10 L1.5,-8.5", stroke=INK, sw=1.2)


def iva_standing(a, trim, hair):
    """A crew member on their feet in a pressure suit."""
    for sx in (-1, 1):
        a.b += rect(sx * 15 - 3.5, -6, 7, 16, IVA_SHADE, rx=3.5, stroke=INK, sw=1.5)
        a.b += circle(sx * 15, 9, 3.2, GLOVE, stroke=INK, sw=1)
    a.b += path("M-17,4 C-17,-6 -10,-9 0,-9 C10,-9 17,-6 17,4 C17,10 10,12 0,12 C-10,12 -17,10 -17,4 Z",
                fill=IVA, stroke=INK, sw=2)
    a.b += rect(-17, -1, 6, 3, trim, rx=1) + rect(11, -1, 6, 3, trim, rx=1)
    # the air pack on the back, a hose over the shoulder to the neck ring
    a.b += rect(-7, 4, 14, 8, IVA_PACK, rx=2, stroke=INK, sw=1.2)
    a.b += line(-5, 8, 5, 8, trim, sw=1.2)
    a.b += path("M5,5 C8,2 9,-2 7,-5", stroke=INK, sw=2.6) + path("M5,5 C8,2 9,-2 7,-5", stroke="#9aa3ad", sw=1.4)
    _head(a, hair)
    _bubble(a, 0, -1, 10)


def eva_standing(a, trim):
    """A crew member on their feet in an EVA suit: bulky, the jetpack on their back."""
    for sx in (-1, 1):
        a.b += rect(sx * 17 - 4.5, -7, 9, 18, EVA_SHADE, rx=4.5, stroke=INK, sw=1.5)
        a.b += circle(sx * 17, 10.5, 3.8, EVA_GLOVE, stroke=INK, sw=1.1)
    a.b += path("M-19,4 C-19,-7 -11,-10 0,-10 C11,-10 19,-7 19,4 C19,11 11,13 0,13 C-11,13 -19,11 -19,4 Z",
                fill=EVA, stroke=INK, sw=2)
    a.b += path("M-15,-4 C-11,-8 -5,-9 1,-9", stroke="#ffffff", sw=1.5, extra=' opacity="0.5"')
    a.b += rect(-19, -2, 6, 3, trim, rx=1) + rect(13, -2, 6, 3, trim, rx=1)
    _jetpack(a, 5)
    a.b += circle(-12, 11, 1.8, LINE, stroke=INK, sw=0.8)
    _eva_helmet(a, 0, -1, trim, visor=True)


def _jetpack(a, y0):
    """The jetpack on an EVA suit's back from above, its top edge at y0: tanks, nozzles, a quad at each corner."""
    a.b += rect(-11, y0, 22, 12, PACK, rx=3, stroke=INK, sw=1.5)
    a.b += rect(-9, y0 + 1.5, 7, 9, "#6d7886", rx=2) + rect(2, y0 + 1.5, 7, 9, "#6d7886", rx=2)
    for sx in (-1, 1):
        a.b += rect(sx * 7.5 - 2.5, y0 + 11.5, 5, 3, PACK_DARK, rx=1, stroke=INK, sw=1)
        a.b += circle(sx * 11, y0 + 0.5, 1.6, PACK_DARK, stroke=INK, sw=0.8)


def _eva_helmet(a, cx, cy, trim, visor):
    """The EVA suit's hard helmet at (cx, cy), facing +y: the gold visor at its front if it shows."""
    a.b += circle(cx, cy, 9.5, "#f2f4f7", stroke=INK, sw=1.8)
    a.b += rect(cx - 1.3, cy - 1, 2.6, 9.5, trim, rx=1.2)
    if visor:
        a.b += path(f"M{f(cx - 7.2)},{f(cy - 3)} C{f(cx - 6.4)},{f(cy - 8.4)} {f(cx - 3.2)},{f(cy - 10)} {f(cx)},{f(cy - 10)} "
                    f"C{f(cx + 3.2)},{f(cy - 10)} {f(cx + 6.4)},{f(cy - 8.4)} {f(cx + 7.2)},{f(cy - 3)} "
                    f"C{f(cx + 4)},{f(cy - 5.5)} {f(cx - 4)},{f(cy - 5.5)} {f(cx - 7.2)},{f(cy - 3)} Z",
                    fill=VISOR, stroke=INK, sw=1)
        a.b += path(f"M{f(cx - 4)},{f(cy - 7.6)} C{f(cx - 2)},{f(cy - 9)} {f(cx + 1)},{f(cy - 9.2)} {f(cx + 3)},{f(cy - 8.6)}",
                    stroke=VISOR_HI, sw=1.2, extra=' opacity="0.8"')


def suited_lying(a, eva, suit, shade, trim, hair, skin, glove, boot, arms):
    """A crew member in a suit down on their back, head toward +y, as generate_svgs.crew_lying lies:
    the helmet face up (the bubble over a face, or the gold visor), `arms` "sides" or "flung"."""
    wide = 1.5 if eva else 0
    for sx in (-1, 1):
        turn = f' transform="rotate({f(-sx * 16)} {f(sx * 5.5)} 6)"' if arms == "flung" else ""
        a.b += rect(sx * 5.5 - 4 - wide / 2, 6, 8 + wide, 20, shade, rx=4, stroke=INK, sw=1.5, extra=turn)
        a.b += rect(sx * 5.5 - 4.5 - wide / 2, 23, 9 + wide, 7, boot, rx=3, stroke=INK, sw=1.2, extra=turn)
    if arms == "flung":
        for sx, (ex, ey) in ((-1, (-26, 2)), (1, (24, -14))):
            d = f"M{f(sx * 11)},{f(-6)} L{f(ex)},{f(ey)}"
            a.b += path(d, stroke=INK, sw=8.5 + wide) + path(d, stroke=shade, sw=5.5 + wide)
            a.b += circle(ex, ey, 3.3, glove, stroke=INK, sw=1)
    else:
        for sx in (-1, 1):
            a.b += rect(sx * 14 - 3.5 - wide / 2, -8, 7 + wide, 17, shade, rx=3.5, stroke=INK, sw=1.5)
            a.b += circle(sx * 14, 9.5, 3.3, glove, stroke=INK, sw=1)
    a.b += rect(-11.5 - wide, -12, 23 + 2 * wide, 21, suit, rx=6, stroke=INK, sw=2)
    a.b += rect(-11 - wide, -9, 5, 3, trim, rx=1) + rect(6 + wide, -9, 5, 3, trim, rx=1)
    if eva:
        # the chest pack: the suit's controls, its lights dark
        a.b += rect(-5, -7, 10, 7, PACK, rx=1.5, stroke=INK, sw=1)
        a.b += circle(-2.2, -3.5, 1.1, "#ff6a5a") + circle(0.6, -3.5, 1.1, "#ffd23a") + circle(3.2, -3.5, 1.1, "#5ee08a")
        a.b += circle(-12, 7, 1.8, LINE, stroke=INK, sw=0.8)
        a.b += circle(0, -19, 9.5, "#f2f4f7", stroke=INK, sw=1.8)
        a.b += rect(-1.3, -28.5, 2.6, 5, trim, rx=1.2)
        a.b += circle(0, -18.5, 6.4, VISOR, stroke=INK, sw=1)
        a.b += path("M-3.6,-21 C-2.2,-22.8 0.6,-23.2 2.6,-22.4", stroke=VISOR_HI, sw=1.2, extra=' opacity="0.8"')
    else:
        a.b += rect(-11.5, 4, 23, 3, IVA_SHADE, stroke=INK, sw=1)
        a.b += circle(0, -19, 7.5, skin, stroke=INK, sw=1.8)
        a.b += path("M-7.4,-17.5 C-7.8,-24 -4,-26.8 0,-26.8 C4,-26.8 7.8,-24 7.4,-17.5 "
                    "C5.5,-22 3,-23.5 0,-23.5 C-3,-23.5 -5.5,-22 -7.4,-17.5 Z", fill=hair, stroke=INK, sw=1.2)
        a.b += line(-4.5, -18.5, -1.8, -18, INK, sw=1.1) + line(1.8, -18, 4.5, -18.5, INK, sw=1.1)
        _bubble(a, 0, -19, 10)


def _limb(a, pts, color, width):
    d = "M" + " L".join(f"{f(x)},{f(y)}" for x, y in pts)
    a.b += path(d, stroke=INK, sw=width + 3) + path(d, stroke=color, sw=width)


def eva_outside(a, trim, arms, legs):
    """An EVA suit from above and behind, out on the hull or off it: its back and jetpack toward
    the viewer, the helmet at the front. `arms` and `legs` give each limb's joints, left then right."""
    for pts in legs:
        _limb(a, pts, EVA_SHADE, 6)
        x, y = pts[-1]
        a.b += circle(x, y, 3.6, BOOT, stroke=INK, sw=1.1)
    for pts in arms:
        _limb(a, pts, EVA_SHADE, 6)
        x, y = pts[-1]
        a.b += circle(x, y, 3.6, EVA_GLOVE, stroke=INK, sw=1.1)
    a.b += path("M-15,-4 C-15,-10 -9,-12 0,-12 C9,-12 15,-10 15,-4 L15,8 C15,13 9,15 0,15 "
                "C-9,15 -15,13 -15,8 Z", fill=EVA, stroke=INK, sw=2)
    a.b += rect(-15, -7, 5, 3, trim, rx=1) + rect(10, -7, 5, 3, trim, rx=1)
    _jetpack(a, -6)
    a.b += circle(-10, 12.5, 1.8, LINE, stroke=INK, sw=0.8)
    # the helmet's back and crown; the visor faces away, its rim just showing
    a.b += circle(0, -16, 8.5, "#f2f4f7", stroke=INK, sw=1.8)
    a.b += path("M-6,-21.5 C-3.5,-24.6 3.5,-24.6 6,-21.5", stroke=VISOR, sw=2.2)
    a.b += rect(-1.3, -21, 2.6, 9, trim, rx=1.2)


# Limb joints, from shoulder or hip out: an arm reaching ahead, one pulling back; a leg pushing
# straight back, one drawn up
REACH = [(14, -6), (17, -16), (13, -26)]
PULL = [(14, -6), (20, 1), (16, -8)]
STRAIGHT = [(6, 11), (8, 29)]
DRAWN = [(6, 11), (14, 19), (10, 27)]
FLOAT_ARM = [(14, -6), (23, -13), (29, -22)]
FLOAT_LEG = [(6, 11), (13, 20), (17, 29)]


def _side(pts, sx):
    return [(sx * x, y) for x, y in pts]


def suits():
    """Every suited crew figure, for each crew look."""
    meta = collider_circle(0, 0, 19.2)
    for name, (suit, shade, trim, hair, title) in CREW.items():
        a = Art()
        iva_standing(a, trim, hair)
        a.m = meta
        write(f"crew/{name}_iva.svg", 48, 48, a, f"{name}_iva: {title} in a pressure suit, facing +y",
              viewbox="-24 -24 48 48")

        a = Art()
        eva_standing(a, trim)
        a.m = meta
        write(f"crew/{name}_eva.svg", 48, 48, a, f"{name}_eva: {title} in an EVA suit, the jetpack on their back, facing +y",
              viewbox="-24 -24 48 48")

        for eva, kind, fabric, fshade, glove, boot in ((False, "iva", IVA, IVA_SHADE, GLOVE, BOOT),
                                                       (True, "eva", EVA, EVA_SHADE, EVA_GLOVE, BOOT)):
            a = Art()
            suited_lying(a, eva, fabric, fshade, trim, hair, SKIN, glove, boot, "sides")
            a.m = meta
            write(f"crew/{name}_{kind}_down.svg", 60, 64, a,
                  f"{name}_{kind}_down: {title} down on their back in a {'n EVA' if eva else ' pressure'} suit, head toward +y",
                  viewbox="-30 -32 60 64")

            a = Art()
            suited_lying(a, eva, grey(fabric, 0.35), grey(fshade, 0.35), grey(trim, 0.6), grey(hair, 0.3), SKIN_PALE,
                         grey(glove, 0.3), boot, "flung")
            a.m = meta
            write(f"crew/{name}_{kind}_dead.svg", 60, 64, a,
                  f"{name}_{kind}_dead: {title}'s body in a{'n EVA' if eva else ' pressure'} suit, limbs thrown out, head toward +y",
                  viewbox="-30 -32 60 64")

        # Outside: crawling hand over hand (the other arm and leg each frame) and floating free
        for frame, (reach, pull) in (("a", (-1, 1)), ("b", (1, -1))):
            a = Art()
            eva_outside(a, trim, [_side(REACH, reach), _side(PULL, pull)],
                        [_side(STRAIGHT, reach), _side(DRAWN, pull)])
            a.m = meta
            write(f"crew/{name}_eva_crawl_{frame}.svg", 60, 68, a,
                  f"{name}_eva_crawl_{frame}: {title} in an EVA suit crawling along the hull toward +y, "
                  f"frame {frame} of 2", viewbox="-30 -34 60 68")
        a = Art()
        eva_outside(a, trim, [_side(FLOAT_ARM, -1), _side(FLOAT_ARM, 1)], [_side(FLOAT_LEG, -1), _side(FLOAT_LEG, 1)])
        a.m = meta
        write(f"crew/{name}_eva_float.svg", 64, 68, a,
              f"{name}_eva_float: {title} in an EVA suit floating free, limbs spread, head toward +y",
              viewbox="-32 -34 64 68")


if __name__ == "__main__":
    suits()

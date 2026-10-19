#!/usr/bin/env python3
"""Builds assets/art/lights.json: the lights each SVG's emissive layer gives off, so the game
can place Box2D lights where the art glows without a hand-made table.

    python3 tools/art/extract_lights.py          # writes assets/art/lights.json
    python3 tools/art/extract_lights.py --check  # fails if lights.json is out of date

Every shape in an SVG's <g id="emissive"> group is measured (its bounding box, color and
opacity); shapes that touch are one light, so a lamp's white-hot core, its colored body and its
soft halo come out as a single source. A light's color is the most saturated color among its
shapes (the accent, not the white center), its strength the most opaque shape's opacity.

Coordinates are SVG units with y turned up (x, -y), so (0, 0) is the SVG origin: for hulls and
turrets that is the body origin or pivot, as in the art guide. Each entry's "canvas" gives the
canvas's bottom-left corner and size in the same frame, so art drawn from its corner (tiles,
components, decor) subtracts it to get the light's place on the footprint.

How bright a light is and how far it reaches is guessed from the art (the game scales reach
with the glowing part's size and strength with its opacity), unless a shape in the light says
otherwise with these attributes, in tiles and degrees with y up:

    data-light="glow|point|cone"   glow: lights nothing around it; point/cone: always lights
    data-reach="5"                 how far its light reaches, tiles
    data-intensity="0.8"           how strong it is, 0-1
    data-direction="-90"           a cone's direction, degrees (0 = +x, 90 = up the canvas)
    data-cone="60"                 a cone's half-angle, degrees

Put them on any shape of the light (or on a <g> around its shapes). DEFAULTS gives the same
settings for art that does not carry them yet.
"""
import colorsys
import json
import math
import os
import re
import sys
import xml.etree.ElementTree as ET

ROOT = os.path.normpath(os.path.join(os.path.dirname(__file__), "..", ".."))
ART = os.path.join(ROOT, "assets", "art")
OUT = os.path.join(ART, "lights.json")
NS = "{http://www.w3.org/2000/svg}"

# Gap in SVG units within which two shapes count as touching.
MERGE_GAP = 2.0
# Light settings for art whose SVG does not carry them (yet); the SVG's own attributes win.
# The wall light is the deck's main light: a wide cone shining away from its wall.
DEFAULTS = {
    "wall_light": {"light": "cone", "direction": -90.0, "cone": 60.0, "reach": 5.0, "intensity": 0.85},
}
SETTINGS = ("light", "reach", "intensity", "direction", "cone")
KINDS = ("glow", "point", "cone")

NUM = re.compile(r"[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?")


def color_of(value, gradients):
    """(r, g, b) in 0-1 for a fill or stroke value, or None for none/unknown. A gradient gives its
    most saturated stop, which is the accent the glow is made of."""
    if not value or value == "none":
        return None
    m = re.match(r"url\(#([^)]+)\)", value)
    if m:
        stops = gradients.get(m.group(1), [])
        return max(stops, key=saturation) if stops else None
    m = re.match(r"#([0-9a-fA-F]{6})$", value)
    if m:
        h = m.group(1)
        return tuple(int(h[i:i + 2], 16) / 255.0 for i in (0, 2, 4))
    m = re.match(r"#([0-9a-fA-F]{3})$", value)
    if m:
        return tuple(int(c * 2, 16) / 255.0 for c in m.group(1))
    if value == "white":
        return (1.0, 1.0, 1.0)
    return None


def saturation(rgb):
    return colorsys.rgb_to_hsv(*rgb)[1]


def gradients_of(root):
    out = {}
    for tag in ("radialGradient", "linearGradient"):
        for g in root.iter(NS + tag):
            out[g.get("id")] = [c for c in (color_of(s.get("stop-color"), {}) for s in g.iter(NS + "stop")) if c]
    return out


def parse_transform(text):
    """A 2D affine matrix (a, b, c, d, e, f) from an SVG transform attribute."""
    m = (1.0, 0.0, 0.0, 1.0, 0.0, 0.0)
    for name, args in re.findall(r"(\w+)\s*\(([^)]*)\)", text or ""):
        v = [float(x) for x in NUM.findall(args)]
        if name == "translate":
            t = (1, 0, 0, 1, v[0], v[1] if len(v) > 1 else 0)
        elif name == "scale":
            t = (v[0], 0, 0, v[1] if len(v) > 1 else v[0], 0, 0)
        elif name == "rotate":
            a = math.radians(v[0])
            cx, cy = (v[1], v[2]) if len(v) == 3 else (0, 0)
            ca, sa = math.cos(a), math.sin(a)
            t = (ca, sa, -sa, ca, cx - ca * cx + sa * cy, cy - sa * cx - ca * cy)
        elif name == "matrix":
            t = tuple(v)
        else:
            continue
        m = compose(m, t)
    return m


def compose(m, t):
    a, b, c, d, e, f = m
    a2, b2, c2, d2, e2, f2 = t
    return (a * a2 + c * b2, b * a2 + d * b2, a * c2 + c * d2, b * c2 + d * d2, a * e2 + c * f2 + e, b * e2 + d * f2 + f)


def apply(m, x, y):
    a, b, c, d, e, f = m
    return a * x + c * y + e, b * x + d * y + f


def points_of(el):
    """Points whose bounding box is the shape's (before transforms; curves by their control points)."""
    tag = el.tag[len(NS):]
    g = lambda k, dflt=0.0: float(el.get(k, dflt))
    if tag == "circle":
        x, y, r = g("cx"), g("cy"), g("r")
        return [(x - r, y - r), (x + r, y + r), (x - r, y + r), (x + r, y - r)]
    if tag == "ellipse":
        x, y, rx, ry = g("cx"), g("cy"), g("rx"), g("ry")
        return [(x - rx, y - ry), (x + rx, y + ry), (x - rx, y + ry), (x + rx, y - ry)]
    if tag == "rect":
        x, y, w, h = g("x"), g("y"), g("width"), g("height")
        return [(x, y), (x + w, y), (x, y + h), (x + w, y + h)]
    if tag == "line":
        return [(g("x1"), g("y1")), (g("x2"), g("y2"))]
    if tag in ("polygon", "polyline"):
        v = [float(n) for n in NUM.findall(el.get("points", ""))]
        return list(zip(v[0::2], v[1::2]))
    if tag == "path":
        return path_points(el.get("d", ""))
    return []


def path_points(d):
    """Absolute points of a path's commands; relative ones are resolved, arcs by their end point."""
    pts, x, y, sx, sy = [], 0.0, 0.0, 0.0, 0.0
    for cmd, args in re.findall(r"([MmLlHhVvCcSsQqTtAaZz])([^MmLlHhVvCcSsQqTtAaZz]*)", d):
        v = [float(n) for n in NUM.findall(args)]
        rel = cmd.islower()
        c = cmd.upper()
        if c == "Z":
            x, y = sx, sy
            continue
        step = {"M": 2, "L": 2, "T": 2, "H": 1, "V": 1, "C": 6, "S": 4, "Q": 4, "A": 7}[c]
        for i in range(0, len(v) - step + 1, step):
            a = v[i:i + step]
            if c == "H":
                x = a[0] + (x if rel else 0)
                pts.append((x, y))
            elif c == "V":
                y = a[0] + (y if rel else 0)
                pts.append((x, y))
            elif c == "A":
                x, y = (x + a[5], y + a[6]) if rel else (a[5], a[6])
                pts.append((x, y))
            else:
                bx, by = (x, y) if rel else (0.0, 0.0)
                for j in range(0, step, 2):
                    pts.append((bx + a[j], by + a[j + 1]))
                x, y = pts[-1]
            if c == "M" and i == 0:
                sx, sy = x, y
    return pts


def settings_of(el, inherited):
    """The light settings an element's data-* attributes give, over those of its groups."""
    out = dict(inherited)
    for key in SETTINGS:
        v = el.get("data-" + key)
        if v is None:
            continue
        if key == "light":
            if v not in KINDS:
                raise ValueError("data-light must be one of %s, not %r" % ("/".join(KINDS), v))
            out[key] = v
        else:
            out[key] = float(v)
    return out


def shapes_of(group, gradients):
    """(x0, y0, x1, y1, rgb, alpha, settings) for each drawn shape in the group, transforms applied."""
    out = []

    def walk(el, m, alpha, settings):
        m = compose(m, parse_transform(el.get("transform")))
        alpha *= float(el.get("opacity", 1))
        settings = settings_of(el, settings)
        tag = el.tag[len(NS):]
        if tag == "g":
            for ch in el:
                walk(ch, m, alpha, settings)
            return
        rgb = color_of(el.get("fill", "#000000" if el.get("stroke") in (None, "none") else "none"), gradients)
        a = alpha * float(el.get("fill-opacity", 1))
        if rgb is None:
            rgb = color_of(el.get("stroke"), gradients)
            a = alpha * float(el.get("stroke-opacity", 1))
        pts = [apply(m, px, py) for px, py in points_of(el)]
        if rgb is None or not pts:
            return
        half = float(el.get("stroke-width", 0)) / 2 if el.get("stroke") not in (None, "none") else 0.0
        xs, ys = [p[0] for p in pts], [p[1] for p in pts]
        out.append((min(xs) - half, min(ys) - half, max(xs) + half, max(ys) + half, rgb, a, settings))

    for ch in group:
        walk(ch, (1.0, 0.0, 0.0, 1.0, 0.0, 0.0), 1.0, settings_of(group, {}))
    return out


def cluster(shapes):
    """Groups shapes whose boxes touch, by union-find."""
    parent = list(range(len(shapes)))

    def find(i):
        while parent[i] != i:
            parent[i] = parent[parent[i]]
            i = parent[i]
        return i

    for i, a in enumerate(shapes):
        for j in range(i + 1, len(shapes)):
            b = shapes[j]
            if a[0] - MERGE_GAP <= b[2] and b[0] - MERGE_GAP <= a[2] and a[1] - MERGE_GAP <= b[3] and b[1] - MERGE_GAP <= a[3]:
                parent[find(i)] = find(j)
    groups = {}
    for i in range(len(shapes)):
        groups.setdefault(find(i), []).append(shapes[i])
    return list(groups.values())


def light_of(group, name):
    x0 = min(s[0] for s in group)
    y0 = min(s[1] for s in group)
    x1 = max(s[2] for s in group)
    y1 = max(s[3] for s in group)
    rgb = max((s[4] for s in group), key=saturation)
    light = {
        "x": round((x0 + x1) / 2, 2),
        "y": round(-(y0 + y1) / 2, 2),
        "w": round(x1 - x0, 2),
        "h": round(y1 - y0, 2),
        "color": "#%02x%02x%02x" % tuple(round(c * 255) for c in rgb),
        "alpha": round(max(s[5] for s in group), 3),
    }
    settings = dict(DEFAULTS.get(name, {}))
    for s in group:
        settings.update(s[6])
    if settings.get("light") == "cone" and "direction" not in settings:
        settings["direction"] = -90.0
    if settings.get("light") == "cone" and "cone" not in settings:
        settings["cone"] = 45.0
    for key in SETTINGS:
        if key in settings:
            light[key] = settings[key]
    return light


def lights_of(path, name):
    root = ET.parse(path).getroot()
    group = next((g for g in root.iter(NS + "g") if g.get("id") == "emissive"), None)
    if group is None:
        return None
    shapes = shapes_of(group, gradients_of(root))
    if not shapes:
        return None
    vb = [float(n) for n in NUM.findall(root.get("viewBox", ""))] or [0, 0, float(root.get("width")), float(root.get("height"))]
    lights = sorted((light_of(g, name) for g in cluster(shapes)), key=lambda l: (-l["y"], l["x"]))
    return {"canvas": [vb[0], -(vb[1] + vb[3]), vb[2], vb[3]], "lights": lights}


def build():
    out = {}
    for folder in sorted(os.listdir(ART)):
        d = os.path.join(ART, folder)
        if not os.path.isdir(d):
            continue
        for f in sorted(os.listdir(d)):
            if not f.endswith(".svg"):
                continue
            name = f[:-4]
            entry = lights_of(os.path.join(d, f), name)
            if entry:
                out[folder + "/" + name] = entry
    return json.dumps(out, indent=1, sort_keys=True) + "\n"


def main():
    text = build()
    if "--check" in sys.argv:
        current = open(OUT).read() if os.path.exists(OUT) else ""
        if current != text:
            sys.exit("assets/art/lights.json is out of date; run tools/art/extract_lights.py")
        return
    with open(OUT, "w", newline="\n") as fh:
        fh.write(text)
    print("Wrote %d entries to %s" % (text.count('"canvas"'), os.path.relpath(OUT, ROOT)))


if __name__ == "__main__":
    main()

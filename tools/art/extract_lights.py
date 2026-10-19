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

Lights of wall-mounted decor shine away from the wall (down the canvas) as cones; see CONES.
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
# Art whose light is a cone: name -> (direction in degrees, y up, 0 = +x; half-angle in degrees).
CONES = {"wall_light": (-90.0, 60.0)}

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


def shapes_of(group, gradients):
    """(x0, y0, x1, y1, rgb, alpha) for each drawn shape in the group, transforms applied."""
    out = []

    def walk(el, m, alpha):
        m = compose(m, parse_transform(el.get("transform")))
        alpha *= float(el.get("opacity", 1))
        tag = el.tag[len(NS):]
        if tag == "g":
            for ch in el:
                walk(ch, m, alpha)
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
        out.append((min(xs) - half, min(ys) - half, max(xs) + half, max(ys) + half, rgb, a))

    for ch in group:
        walk(ch, (1.0, 0.0, 0.0, 1.0, 0.0, 0.0), 1.0)
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
    if name in CONES:
        light["direction"], light["cone"] = CONES[name]
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

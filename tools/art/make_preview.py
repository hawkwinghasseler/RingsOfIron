#!/usr/bin/env python3
"""Builds assets/art/preview.html: every SVG in the pack on a contact sheet, plus each
chassis in assets/data/chassis.toml drawn with the pack's tile and component art.

    python3 tools/art/make_preview.py            # links the SVGs by relative path
    python3 tools/art/make_preview.py --inline   # embeds them, for a single shareable file
"""
import base64
import os
import re
import sys
import tomllib

ROOT = os.path.normpath(os.path.join(os.path.dirname(__file__), "..", ".."))
ART = os.path.join(ROOT, "assets", "art")
T = 64
SCALE = 0.5  # preview pixels per SVG unit on the chassis plans

INLINE = "--inline" in sys.argv
OUT = sys.argv[sys.argv.index("--out") + 1] if "--out" in sys.argv else os.path.join(ART, "preview.html")

TILE_ART = {"#": "hull", "1": "hull_light", "2": "hull_medium", "3": "hull_heavy",
            "I": "internal_wall", ".": "floor", "+": "conduit"}
COMPONENTS = {"R": ("reactor", "PRIMARY"), "S": ("shield_emitter", "PRIMARY"), "T": ("turret_mount", "PRIMARY"),
              "L": ("life_support", "AUX"), "H": ("helm", "AUX"), "W": ("weapons", "AUX"),
              "C": ("cargo", "AUX"), "A": ("airlock", "AUX"), "D": ("docking_port", "PORT")}
SIZES = {"PRIMARY": {"s": (1, 1), "m": (2, 2), "l": (3, 3), "xl": (5, 5)},
         "AUX": {"s": (1, 2), "m": (2, 3), "l": (3, 7), "xl": (5, 12)},
         "PORT": {"s": (3, 1), "m": (5, 2), "l": (7, 3)}}
TYPE_LETTER = {v[0].upper(): k for k, v in COMPONENTS.items()}

_cache = {}


def src(rel):
    if not INLINE:
        return rel
    if rel not in _cache:
        with open(os.path.join(ART, rel), "rb") as fh:
            _cache[rel] = "data:image/svg+xml;base64," + base64.b64encode(fh.read()).decode()
    return _cache[rel]


def svg_size(rel):
    with open(os.path.join(ART, rel)) as fh:
        head = fh.read(400)
    w = float(re.search(r'width="([\d.]+)"', head).group(1))
    h = float(re.search(r'height="([\d.]+)"', head).group(1))
    return w, h


def img(rel, x, y, w, h, rot=0):
    """An image whose rotation-0 size is w x h, rotated rot quarter turns CCW about the
    center of its rotated footprint, which has its top-left corner at (x, y)."""
    fw, fh = (h, w) if rot % 2 else (w, h)
    cx, cy = x + fw / 2, y + fh / 2
    return (f'<img src="{src(rel)}" style="position:absolute;left:{cx - w / 2:.1f}px;top:{cy - h / 2:.1f}px;'
            f'width:{w:.1f}px;height:{h:.1f}px;transform:rotate({-90 * rot}deg)">')


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


def size_for(kind, w, h):
    for name, (bw, bh) in SIZES[kind].items():
        if (bw, bh) == (w, h):
            return name, 0
        if (bh, bw) == (w, h):
            return name, 1
    return None, 0


def chassis_plan(cid, ch):
    rows = ch["rows"]
    H, W = len(rows), max(len(r) for r in rows)
    px = T * SCALE
    parts = []
    covered = set()
    mounts = []
    for m in ch.get("mounts", []):
        letter = TYPE_LETTER.get(m.get("type", ""))
        if not letter:
            continue
        name, kind = COMPONENTS[letter]
        bw, bh = SIZES[kind][m["size"].lower()]
        rot = m.get("rotation", 0)
        fw, fh = (bh, bw) if rot % 2 else (bw, bh)
        top = H - (m["y"] + fh)  # mounts use (0, 0) at the bottom-left tile
        mounts.append((name, m["size"].lower(), rot, m["x"], top, fw, fh))
        covered |= {(m["x"] + i, top + j) for i in range(fw) for j in range(fh)}
    for letter, (name, kind) in COMPONENTS.items():
        for (x, y, w, h) in clusters(rows, letter):
            cells = {(x + i, y + j) for i in range(w) for j in range(h)}
            if cells <= covered:
                continue
            size, rot = size_for(kind, w, h)
            if size is None:
                continue
            if kind == "PORT":
                if y == 0:
                    rot = 0
                elif y + h == H:
                    rot = 2
                elif x == 0:
                    rot = 1
                else:
                    rot = 3
            mounts.append((name, size, rot, x, y, w, h))
    for r, row in enumerate(rows):
        for c, chh in enumerate(row):
            art = TILE_ART.get(chh, "floor" if chh in COMPONENTS else None)
            if art:
                parts.append(img(f"tiles/{art}.svg", c * px, r * px, px, px))
            if chh == "+":
                # one arm toward each conduit neighbor: up, left, down, right = 0-3 quarter turns
                for rot, (dc, dr) in enumerate(((0, -1), (-1, 0), (0, 1), (1, 0))):
                    nr, nc = r + dr, c + dc
                    if 0 <= nr < H and 0 <= nc < len(rows[nr]) and rows[nr][nc] == "+":
                        parts.append(img("tiles/conduit_arm.svg", c * px, r * px, px, px, rot))
    for (name, size, rot, x, y, w, h) in mounts:
        bw, bh = (h, w) if rot % 2 else (w, h)
        parts.append(img(f"components/{name}_{size}.svg", x * px, y * px, bw * px, bh * px, rot))
    return (f'<figure><div class="plan" style="width:{W * px:.0f}px;height:{H * px:.0f}px">{"".join(parts)}</div>'
            f'<figcaption>{ch.get("name", cid)} <code>{cid}</code></figcaption></figure>')


def main():
    with open(os.path.join(ROOT, "assets", "data", "chassis.toml"), "rb") as fh:
        chassis = tomllib.load(fh)["chassis"]
    sections = []
    for folder in ("tiles", "edges", "components", "effects", "decor", "interactive", "turrets", "hulls"):
        order = {"s": 0, "m": 1, "l": 2, "xl": 3}
        def key(name):
            m = re.match(r"(.*?)_(s|m|l|xl)(_.*)?\.svg$", name)
            return (m.group(1), order[m.group(2)], m.group(3) or "") if m else (name[:-4], -1, "")
        files = sorted((f for f in os.listdir(os.path.join(ART, folder)) if f.endswith(".svg")), key=key)
        cards = []
        for f in files:
            rel = f"{folder}/{f}"
            w, h = svg_size(rel)
            scale = 1.0 if folder in ("hulls",) else (2.0 if max(w, h) <= 128 else 1.0)
            if folder == "hulls":
                scale = min(1.0, 520 / w)
            cards.append(f'<figure class="card"><div class="chk"><img src="{src(rel)}" '
                         f'style="width:{w * scale:.0f}px;height:{h * scale:.0f}px"></div>'
                         f'<figcaption>{f[:-4]}<br><span>{int(w)}x{int(h)}</span></figcaption></figure>')
        sections.append(f'<h2>{folder}</h2><div class="grid">{"".join(cards)}</div>')
    plans = "".join(chassis_plan(cid, ch) for cid, ch in chassis.items())
    style = """
:root { --bg:#0e1116; --panel:#171b22; --text:#d7dde5; --muted:#8b95a3; --line:#262c36;
        --stage:#1d222a; --space:#05070b; color-scheme:dark; }
@media (prefers-color-scheme: light) { :root:not([data-theme="dark"]) { --bg:#eef1f5; --panel:#ffffff; --text:#1a1f27;
        --muted:#5b6574; --line:#d5dae1; color-scheme:light; } }
:root[data-theme="light"] { --bg:#eef1f5; --panel:#ffffff; --text:#1a1f27; --muted:#5b6574; --line:#d5dae1;
        color-scheme:light; }
* { box-sizing:border-box; }
body { margin:0; padding-block:24px 64px; padding-left:max(16px, env(safe-area-inset-left, 0px));
       padding-right:max(16px, env(safe-area-inset-right, 0px)); background:var(--bg); color:var(--text);
       font:14px/1.45 system-ui, -apple-system, "Segoe UI", sans-serif; }
main { max-width:1200px; margin:0 auto; }
h1 { font-size:22px; margin:0 0 4px; text-wrap:balance; }
h2 { font-size:13px; text-transform:uppercase; letter-spacing:.08em; color:var(--muted); margin:36px 0 12px;
     border-bottom:1px solid var(--line); padding-bottom:6px; }
p.lede { color:var(--muted); margin:0 0 8px; max-width:70ch; }
.grid { display:flex; flex-wrap:wrap; gap:12px; align-items:flex-start; }
.card { margin:0; background:var(--panel); border:1px solid var(--line); border-radius:8px; padding:10px;
        max-width:100%; min-width:0; }
.chk { background:var(--stage); border-radius:4px; padding:8px; display:flex; align-items:center;
       justify-content:center; overflow:auto; max-width:100%; }
.chk img { display:block; max-width:none; }
figcaption { font-size:12px; margin-top:6px; } figcaption span { color:var(--muted); font-variant-numeric:tabular-nums; }
.plans { display:flex; flex-wrap:wrap; gap:24px; align-items:flex-start; }
.plans figure { margin:0; max-width:100%; min-width:0; overflow-x:auto; }
.plan { position:relative; background:var(--space); }
.plan img { display:block; max-width:none; }
code { color:var(--muted); }
"""
    content = f"""<main>
<h1>Rings of Iron art pack</h1>
<p class="lede">Every SVG in <code>assets/art</code>, and the chassis in <code>assets/data/chassis.toml</code>
drawn with the tile and component art. One tile is 64 SVG units; tiles and components are drawn at rotation 0.</p>
<h2>chassis</h2><div class="plans">{plans}</div>
{"".join(sections)}
</main>
"""
    head = ('<title>Rings of Iron Art</title>\n<meta name="claude:viewport-fit-sides" content="cover">\n'
            f"<style>{style}</style>\n")
    if INLINE:
        # published as an Artifact, which supplies the document skeleton
        html = head + content
    else:
        html = ('<!doctype html>\n<html lang="en"><head><meta charset="utf-8">'
                '<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">\n'
                + head + "</head><body>\n" + content + "</body></html>\n")
    with open(OUT, "w", newline="\n") as fh:
        fh.write(html)
    print(f"wrote {OUT}")


if __name__ == "__main__":
    main()

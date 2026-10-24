#!/usr/bin/env python3
"""One-off migration: moves every chassis's main engines one tile further out, so that each lies
wholly outside the hull, its flange against the outer wall instead of set into it (see
interior-grid's EngineLayout). Every engine lies along x at the aft edge of the map (nozzle -x); the
map grows one void column aft, so origin_x moves a tile aft and every listed x moves one up, but
the listed engines stay at x 0. The wall tile each flange stood in is hull again, and body
coordinates are unchanged, so pipes and feeds keep their tiles. Integer coordinates in a chassis's
comments move with it.

    python3 tools/layout/engines_off_hull.py
"""
import os
import re
import sys
import tomllib

ROOT = os.path.normpath(os.path.join(os.path.dirname(__file__), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools", "art"))
sys.path.insert(0, os.path.dirname(__file__))
from generate_svgs import chassis_mounts, WALL_CHARS  # noqa: E402
from engines_outboard import shift_comment  # noqa: E402

PATH = os.path.join(ROOT, "assets", "data", "chassis.toml")
ENGINE_LINE = r'(component = "(engine|gunship_drive|freight_drive)"|type = "ENGINE")'


def migrate_section(name, ch, text):
    rows = ch["rows"]
    H = len(rows)
    engines = [m for m in chassis_mounts(ch) if m[0] == "engine"]
    listed = sum(1 for m in ch.get("mounts", []) if re.search(ENGINE_LINE, f'component = "{m.get("component")}" type = "{m.get("type")}"'))
    if not engines:
        return text, False
    tile = ch.get("tile_size", 1.0)
    grid = [list(" " + r) for r in rows]
    # the listed engines come first, and stay as they are: only the drawn ones are redrawn
    for (_, col, row, w, h) in engines[listed:]:
        assert col == 0 and w > h, (name, col, row, w, h)
        for r in range(row, row + h):
            letter = rows[r][0]
            # the hull beside the engine, in the rows above and below it, gives the wall's kind
            wall = next((rows[q][w - 1] for q in (r - 1, r + 1, r - 2, r + 2) if 0 <= q < H and rows[q][w - 1] in WALL_CHARS), "#")
            for c in range(w):
                grid[r][c] = letter
            grid[r][w] = wall
    new_rows = ["".join(g) for g in grid]

    rows_text = "rows = [\n" + "".join(f'  "{r}",\n' for r in new_rows) + "]"
    text = re.sub(r"rows = \[\n(?:\s*\".*\",?\n)+\]", lambda _: rows_text, text, count=1)
    text = re.sub(r"(?m)^origin_x = (-?[\d.]+)", lambda m: f"origin_x = {float(m.group(1)) - tile:.1f}", text, count=1)
    out = []
    for line in text.split("\n"):
        if line.lstrip().startswith("#"):
            line = shift_comment(line, 1)
        elif re.search(ENGINE_LINE, line):
            pass  # a listed engine stays at x 0, now wholly outside
        elif "{" in line:
            line = re.sub(r"(?<![\w.])(x = )(-?\d+)", lambda m: f"{m.group(1)}{int(m.group(2)) + 1}", line)
        out.append(line)
    return "\n".join(out), True


def main():
    with open(PATH, "rb") as fh:
        data = tomllib.load(fh)["chassis"]
    with open(PATH, encoding="utf-8") as fh:
        text = fh.read()
    starts = [m.start() for m in re.finditer(r"(?m)^\[chassis\.", text)]
    bounds = []
    for s in starts:
        lines_before = text[:s].split("\n")
        i = len(lines_before) - 2
        while i >= 0 and lines_before[i].startswith("#"):
            i -= 1
        bounds.append(len("\n".join(lines_before[:i + 1])) + 1 if i >= 0 else 0)
    bounds.append(len(text))
    out = text[:bounds[0]]
    for n, b in enumerate(bounds[:-1]):
        section = text[b:bounds[n + 1]]
        name = re.search(r"(?m)^\[chassis\.(\w+)\]", section).group(1)
        section, moved = migrate_section(name, data[name], section)
        if moved:
            print(f"{name}: engines off the hull")
        out += section
    with open(PATH, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(out)


if __name__ == "__main__":
    main()

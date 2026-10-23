#!/usr/bin/env python3
"""One-off migration: moves every chassis's main engines out of the aft engine room onto the
outside of the aft hull (see interior-grid's EngineLayout). Each engine that lay along x against
the aft hull (nozzle -x) is rebuilt outside it, its flange taking the place of the hull tile it was
against; the room it stood in becomes deck. The map grows k void columns aft for the longest
engine, so origin_x moves k tiles aft and every listed x moves k up; body coordinates are unchanged.
Where a pipe reached the old engine, a pipe now runs across the deck it leaves to the tile behind
the flange, which feeds the engine. Integer coordinates in a chassis's comments move with it.

    python3 tools/layout/engines_outboard.py
"""
import os
import re
import sys
import tomllib

ROOT = os.path.normpath(os.path.join(os.path.dirname(__file__), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools", "art"))
from generate_svgs import chassis_mounts, WALL_CHARS  # noqa: E402

PATH = os.path.join(ROOT, "assets", "data", "chassis.toml")
LETTERS = set("EQG")


def migrate_section(name, ch, text):
    rows = ch["rows"]
    H = len(rows)
    engines = [m for m in chassis_mounts(ch) if m[0] == "engine"]
    if not engines:
        return text, None
    for (_, col, row, w, h) in engines:
        assert col == 1 and w > h, (name, col, row, w, h)
        for r in range(row, row + h):
            assert rows[r][0] in WALL_CHARS, (name, r)
    k = max(w for (_, _, _, w, _) in engines) - 1
    tile = ch.get("tile_size", 1.0)

    # Rows: k void columns aft, the old engine rooms decked over, the engines outside
    grid = [list(" " * k + r) for r in rows]
    for (_, col, row, w, h) in engines:
        letter = rows[row][col]
        for r in range(row, row + h):
            for c in range(col, col + w):
                grid[r][c + k] = "."
            for i in range(w):
                grid[r][k - (w - 1) + i] = letter
    new_rows = ["".join(g) for g in grid]

    # Pipes that reached an old engine: run one across its deck from the tile behind the flange
    pipes = ch.get("pipes", [])
    added = []
    for (_, col, row, w, h) in engines:
        for r in range(row, row + h):
            y = H - 1 - r
            touch = None
            for j in range(w):
                x = col + j
                if any(p["x"] == x and p["y"] == y for p in pipes):
                    touch = j
                    break
            if touch is None:
                continue
            for j in range(touch):
                x = col + j
                if not any(p["x"] == x and p["y"] == y and "side" not in p for p in pipes):
                    added.append((x + k, y))

    def shift_x(m):
        return f"{m.group(1)}{int(m.group(2)) + k}"

    def shift_tables(block):
        return re.sub(r"(?<![\w.])(x = )(-?\d+)", shift_x, block)

    # Rewrite the section's text: rows, origin, listed coordinates, comments
    rows_text = "rows = [\n" + "".join(f'  "{r}",\n' for r in new_rows) + "]"
    text = re.sub(r"rows = \[\n(?:\s*\".*\",?\n)+\]", lambda _: rows_text, text, count=1)
    text = re.sub(r"(?m)^origin_x = (-?[\d.]+)", lambda m: f"origin_x = {float(m.group(1)) - k * tile:.1f}", text, count=1)
    out = []
    for line in text.split("\n"):
        if line.lstrip().startswith("#"):
            line = shift_comment(line, k)
        elif re.search(r'(component = "(engine|gunship_drive|freight_drive)"|type = "ENGINE")', line):
            # a listed engine moves outside the hull with the drawn ones: its flange at the old hull's x
            length = {"S": 2, "M": 3, "L": 7, "XL": 12}[re.search(r'size = "(\w+)"', line).group(1)]
            line = re.sub(r"(?<![\w.])x = -?\d+", f"x = {k - (length - 1)}", line)
        elif "{" in line:
            line = shift_tables(line)
        out.append(line)
    text = "\n".join(out)
    if added:
        extra = ", ".join(f"{{ x = {x}, y = {y} }}" for x, y in added)
        text = re.sub(r"(?m)^pipes = \[\n", lambda m: m.group(0) + f"  {extra},\n", text, count=1)
    return text, k


def shift_comment(line, k):
    """Moves the integer x coordinates a comment gives: "x 5", "x 1-6", "x 5 | 6", "(14,2)"."""
    def xs(m):
        return m.group(1) + re.sub(r"\d+", lambda d: str(int(d.group(0)) + k), m.group(2))
    line = re.sub(r"(?<!\d )(\bx )(\d+(?:\s*[-|]\s*\d+)?)", xs, line)
    line = re.sub(r"\((\d+), ?(\d+)\)", lambda m: f"({int(m.group(1)) + k},{' ' if ', ' in m.group(0) else ''}{m.group(2)})", line)
    return line


def main():
    with open(PATH, "rb") as fh:
        data = tomllib.load(fh)["chassis"]
    with open(PATH, encoding="utf-8") as fh:
        text = fh.read()
    # Split at the chassis headers, keeping each header's leading comments with it
    starts = [m.start() for m in re.finditer(r"(?m)^\[chassis\.", text)]
    pieces, prev = [], 0
    bounds = []
    for s in starts:
        # back up over the comment block right above the header
        b = s
        lines_before = text[:s].split("\n")
        i = len(lines_before) - 2
        while i >= 0 and lines_before[i].startswith("#"):
            i -= 1
        b = len("\n".join(lines_before[:i + 1])) + 1 if i >= 0 else 0
        bounds.append(b)
    bounds.append(len(text))
    out = text[:bounds[0]]
    for n, b in enumerate(bounds[:-1]):
        section = text[b:bounds[n + 1]]
        name = re.search(r"(?m)^\[chassis\.(\w+)\]", section).group(1)
        section, k = migrate_section(name, data[name], section)
        if k:
            print(f"{name}: engines outboard, {k} column(s) aft")
        out += section
    with open(PATH, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(out)


if __name__ == "__main__":
    main()

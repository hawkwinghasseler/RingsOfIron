"""Grey-box wireframes for the task-driven UI spec. Style-agnostic on purpose: boxes, labels, sample data.

Every layout is in the game's reference units (1280 x 800), the same units UiScale scales.
Run: python3 wireframes.py <out_dir>  (from documentation/ui-overhaul/functional-task: python3 wireframes.py .)
"""
import os
import sys
from xml.sax.saxutils import escape

W, H = 1280, 800
FONT = "DejaVu Sans Mono, Menlo, Consolas, monospace"


class Svg:
    def __init__(self, title, w=W, h=H):
        self.w, self.h = w, h
        self.parts = []
        self.title = title

    def add(self, s):
        self.parts.append(s)

    def rect(self, x, y, w, h, fill="#ffffff", stroke="#3a3a3a", dash=None, sw=1.5, rx=3):
        d = f' stroke-dasharray="{dash}"' if dash else ""
        self.add(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{rx}" fill="{fill}" stroke="{stroke}" stroke-width="{sw}"{d}/>')

    def text(self, x, y, s, size=12, weight="normal", fill="#222", anchor="start", style="normal"):
        self.add(f'<text x="{x}" y="{y}" font-family="{FONT}" font-size="{size}" font-weight="{weight}" '
                 f'font-style="{style}" fill="{fill}" text-anchor="{anchor}" xml:space="preserve" style="white-space:pre">{escape(s)}</text>')

    def box(self, x, y, w, h, tag, title, lines=(), kind="widget", note=None):
        """kind: widget (solid), cond (dotted: shown only when its trigger holds), world (dashed, drawn in the world)."""
        if kind == "widget":
            self.rect(x, y, w, h)
        elif kind == "cond":
            self.rect(x, y, w, h, fill="#fbfbf8", dash="2 3")
        elif kind == "world":
            self.rect(x, y, w, h, fill="none", stroke="#8a8a8a", dash="7 4", sw=1.2)
        elif kind == "alert":
            self.rect(x, y, w, h, fill="#ffffff", stroke="#111", sw=3)
        elif kind == "screen":
            self.rect(x, y, w, h, fill="#ffffff", stroke="#3a3a3a", sw=2)
        head = f"{tag}  {title}" if tag else title
        self.text(x + 7, y + 15, head, size=11.5, weight="bold", fill="#111" if kind != "world" else "#555")
        ty = y + 31
        for ln in lines:
            if ty > y + h - 4:
                break
            self.text(x + 7, ty, ln, size=11, fill="#333" if kind != "world" else "#666",
                      style="italic" if kind == "world" else "normal")
            ty += 14
        if note:
            self.text(x + w - 6, y + h - 6, note, size=9.5, fill="#777", anchor="end", style="italic")

    def screen_bg(self, label):
        self.rect(0, 0, self.w, self.h, fill="#e9e9e6", stroke="#9a9a9a", rx=0, sw=1)
        self.text(self.w - 10, 11, label, size=10, fill="#888", anchor="end")

    def ship(self, cx, cy, label="your ship"):
        self.add(f'<polygon points="{cx-34},{cy-12} {cx+26},{cy-12} {cx+40},{cy} {cx+26},{cy+12} {cx-34},{cy+12}" '
                 f'fill="#cfcfcb" stroke="#555" stroke-width="1.2"/>')
        self.text(cx, cy + 30, label, size=10, fill="#666", anchor="middle", style="italic")

    def clear_zone(self, cx=W / 2, cy=H / 2 - 10, rx=300, ry=200):
        self.add(f'<ellipse cx="{cx}" cy="{cy}" rx="{rx}" ry="{ry}" fill="none" stroke="#b9b9b4" stroke-width="1.2" stroke-dasharray="3 5"/>')
        self.text(cx, cy - ry + 16, "clear zone: no screen-fixed widgets", size=10, fill="#999", anchor="middle", style="italic")

    def save(self, path):
        body = "\n".join(self.parts)
        svg = (f'<svg xmlns="http://www.w3.org/2000/svg" width="{self.w}" height="{self.h}" '
               f'viewBox="0 0 {self.w} {self.h}" role="img" aria-label="{escape(self.title)}">\n'
               f'<title>{escape(self.title)}</title>\n{body}\n</svg>\n')
        with open(path, "w", encoding="utf-8") as f:
            f.write(svg)


# ---- shared widgets ---------------------------------------------------------------------------

def chr_(s, warp="WARP 1x", nxt="next stop: burn 1 in 1:29:51"):
    """Clock, warp, and the next stop or what holds warp. Drawn by hand: box() keeps to two lines at
    this height, which clipped the next stop in every wireframe up to round 8."""
    s.rect(16, 16, 240, 48)
    s.text(23, 30, "CHR  Day 3  14:22:05", size=11.5, weight="bold", fill="#111")
    s.text(23, 44, warp, size=11, fill="#333")
    if nxt:
        s.text(23, 58, nxt, size=11, fill="#333")


BAND_X, BAND_W = 320, 640
PLUS_W = 44  # the "+n more" slot at the end of the caution row, reserved so no cell moves when it shows


def alr(s, warn=None, cautions=(), more=0):
    if warn:
        s.box(BAND_X, 16, BAND_W, 44, "ALR", "WARNING  " + warn[0], [warn[1]], kind="alert")
    y = 66  # the caution row keeps its place whether or not a warning stands
    if cautions and not warn:
        s.text(BAND_X + BAND_W / 2, 42, "(warning row empty: the cautions keep their row)", size=10, fill="#aaa", anchor="middle", style="italic")
    cw = (BAND_W - PLUS_W - 8 * 3) / 3  # three fixed slots
    for i, c in enumerate(cautions[:3]):
        s.box(BAND_X + i * (cw + 8), y, cw, 26, "", c, kind="cond")
    if more:
        s.box(BAND_X + BAND_W - PLUS_W, y, PLUS_W, 26, "", f"+{more}", kind="cond")


def obj(s, rows):
    s.box(1024, 16, 240, 22 + 14 * len(rows) + 6, "OBJ", "Objectives", rows, kind="cond")


def ntf(s, rows, y=None):
    y = y or 16 + 22 + 14 * 3 + 6 + 8
    s.box(1024, y, 240, 22 + 14 * len(rows) + 6, "NTF", "News", rows, kind="cond")


def ann(s, lit=(), shields=True):
    s.box(16, 72, 240, 64, "ANN", "Annunciator", [], kind="widget")
    lamps = ["HULL", "FIRE", "AIR", "POWER", "PROP", "DRIVE", "ARMS", "CREW", "EVA", "SHIELD"]
    if not shields:
        lamps = lamps[:-1]
    for i, name in enumerate(lamps):
        col, row = i % 5, i // 5
        x, y = 22 + col * 46, 92 + row * 21
        state = dict(lit).get(name)
        if state == "warn":
            s.rect(x, y, 42, 17, fill="#3a3a3a", stroke="#111", sw=1.2, rx=2)
            s.text(x + 21, y + 12.5, name, size=9.5, weight="bold", fill="#fff", anchor="middle")
        elif state == "caut":
            s.rect(x, y, 42, 17, fill="#d6d6d0", stroke="#111", sw=1.6, rx=2)
            s.text(x + 21, y + 12.5, name, size=9.5, weight="bold", fill="#111", anchor="middle")
        elif state == "adv":
            s.rect(x, y, 42, 17, fill="#eeeee9", stroke="#777", sw=1.1, rx=2)
            s.text(x + 21, y + 12.5, name, size=9.5, fill="#555", anchor="middle")
        else:
            s.rect(x, y, 42, 17, fill="#f6f6f3", stroke="#c4c4bf", sw=1, rx=2)
            s.text(x + 21, y + 12.5, name, size=9.5, fill="#b0b0aa", anchor="middle")


def hnt(s, text, note=None):
    s.box(16, 768, 1248, 24, "", text, kind="cond")
    if note:  # a designer's note about the strip, not text the game shows
        s.text(1252, 784, note, size=9.5, fill="#999", anchor="end", style="italic")


def target_lock(s, cx, cy, name):
    """The locked target as the world draws it: its glyph, the lock mark round it, and the one lead
    reticle on the hull. At 3.2 km the coilgun's lead is about 9 m, so the reticle sits inside the mark."""
    s.add(f'<polygon points="{cx+18},{cy-6} {cx-12},{cy-6} {cx-20},{cy} {cx-12},{cy+6} {cx+18},{cy+6}" '
          f'fill="#d6d6d2" stroke="#777" stroke-width="1.1"/>')
    for sx, sy in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
        x, y = cx + sx * 36, cy + sy * 22
        s.add(f'<polyline points="{x - sx * 10},{y} {x},{y} {x},{y - sy * 8}" fill="none" stroke="#8a8a8a" stroke-width="1.3"/>')
    rx, ry = cx - 9, cy + 2
    s.add(f'<circle cx="{rx}" cy="{ry}" r="4" fill="none" stroke="#555" stroke-width="1.1"/>')
    s.add(f'<line x1="{rx - 7}" y1="{ry}" x2="{rx + 7}" y2="{ry}" stroke="#555" stroke-width="1"/>')
    s.add(f'<line x1="{rx}" y1="{ry - 7}" x2="{rx}" y2="{ry + 7}" stroke="#555" stroke-width="1"/>')
    s.text(cx, cy - 28, name, size=10, fill="#666", anchor="middle", style="italic")
    s.text(cx, cy + 38, "lead reticle: about 9 m, on the hull,", size=9.5, fill="#888", anchor="middle", style="italic")
    s.text(cx, cy + 50, "inside the lock mark", size=9.5, fill="#888", anchor="middle", style="italic")


def legend(s, y=None):
    y = y if y is not None else H - 22
    s.rect(16, y - 10, 12, 10, fill="#fff", stroke="#3a3a3a")
    s.text(32, y, "always in this context", size=9.5, fill="#777")
    s.rect(170, y - 10, 12, 10, fill="#fbfbf8", stroke="#3a3a3a", dash="2 3")
    s.text(186, y, "only while its trigger holds", size=9.5, fill="#777")
    s.rect(370, y - 10, 12, 10, fill="none", stroke="#8a8a8a", dash="7 4")
    s.text(386, y, "drawn in the world", size=9.5, fill="#777")


# ---- 1. zones ---------------------------------------------------------------------------------

def zones(out):
    s = Svg("Screen zones: time and alerts on top, self on the left, others on the right, motion at the bottom")
    s.screen_bg("zones  |  1280 x 800 reference units")
    s.clear_zone()
    s.ship(640, 390)
    s.box(16, 16, 240, 48, "Z1", "TIME", ["clock, warp, next stop"])
    s.box(320, 16, 640, 76, "Z2", "ATTENTION", ["row 1: one warning   row 2: up to three cautions, then +n", "each row and each slot keeps its place; every alert names its action"], kind="alert")
    s.box(1024, 16, 240, 190, "Z3", "INCOMING", ["objectives (contracts, guides)", "news (notices)", "", "slow loop: hours and days"])
    s.box(16, 72, 240, 530, "Z4", "SELF", ["annunciator: what is wrong aboard", "personal: the captain's body", "", "medium loop: minutes", "", "", "", "", "", "", "",
                                          "nothing about other ships", "goes on this side"])
    s.box(16, 610, 300, 150, "Z5", "SELF, SPATIAL", ["ship schematic (outside view,", "only when something is wrong)", "", "on deck the deck itself is", "the schematic: this zone", "stays empty"])
    s.box(1024, 214, 240, 362, "Z6", "OTHERS", ["target card", "(combat target, else nav target)", "", "contacts are drawn in the world;", "this side holds what the world", "can't show: state, hull, intent"])
    s.box(800, 584, 464, 176, "Z7", "OTHERS, SPATIAL + ARMS", ["weapons cluster (left)", "sensor scope (corner)", "", "fast loop: seconds"])
    s.box(400, 632, 392, 128, "Z8", "MOTION AND HANDS", ["flight instrument: one mode at a time", "departure / dock / burn / cruise; hidden in rails warp", "", "fast loop: seconds", "closest to the eye line"])
    s.box(16, 768, 1248, 24, "Z9", "keys for what you can do here (fades as you learn): a full-width strip; every zone ends above it", kind="cond")
    s.text(640, 310, "world overlays live here:", size=11, fill="#888", anchor="middle", style="italic")
    s.text(640, 326, "markers, velocity, burn arrows, reticle, missile paths,", size=11, fill="#888", anchor="middle", style="italic")
    s.text(640, 342, "docking corridor, deck hazards, prompts at objects", size=11, fill="#888", anchor="middle", style="italic")
    s.save(os.path.join(out, "zones.svg"))


# ---- 2. helm, quiet ---------------------------------------------------------------------------

def helm_quiet(out):
    s = Svg("Helm at rest: clock, annunciator, flight instrument, scope; nothing else")
    s.screen_bg("HELM, at rest (no target, no plan, nothing wrong)")
    s.clear_zone()
    s.ship(640, 390)
    chr_(s, "WARP 1x", "")
    ann(s, shields=False)
    s.box(420, 664, 440, 96, "FLT", "Flight: CRUISE", [
        "frame Highport (station, 3.0 km)   speed 0.4 m/s",
        "dv left 7,007 m/s   |----------|^---|  home 410",
        "[main] [RCS]   (indicators, dark at rest)"])
    s.box(1088, 584, 176, 176, "SNS", "Scope 5 km (auto)", [])  # Highport, the nearest contact, is 3.0 km off
    s.box(500, 300, 70, 28, "", "Highport", kind="world")
    s.add('<polygon points="720,470 732,476 720,482" fill="none" stroke="#8a8a8a" stroke-width="1.2"/>')
    s.text(740, 480, "freighter: a glyph, no label", size=9.5, fill="#888", style="italic")
    s.add('<circle cx="640" cy="340" r="9" fill="none" stroke="#8a8a8a" stroke-width="1.2" stroke-dasharray="3 2"/>')
    s.text(654, 336, "prograde (frame-coded)", size=9.5, fill="#888", style="italic")
    hnt(s, "W/S thrust   A/D turn   M map   Q destination   F1 controls")
    legend(s, 600)
    s.save(os.path.join(out, "helm-quiet.svg"))


# ---- 3. helm, combat (gunnery) ---------------------------------------------------------------

def gunnery_combat(out):
    s = Svg("At the weapons console in a fight")
    s.screen_bg("GUNNERY, in combat (weapons console)")
    s.clear_zone()
    s.ship(640, 390)
    chr_(s, "WARP 1x", "held: fire aboard")
    alr(s, ("MISSILE  2 inbound  impact 14 s", "F decoy   point defense engaging"),
        ("FIRE Hold  Ada", "WOUNDED Cole  nobody"))
    ann(s, [("FIRE", "caut"), ("CREW", "caut")], shields=False)
    s.box(16, 610, 300, 150, "SCH", "Ship (shown: FIRE, CREW lit)", ["", "  [deck plan: fire in the Hold,", "   crew as dots, you at", "   the weapons console]"], kind="cond")
    # A bounty's title names the target's faction (ContractPostings), so the row breaks before it
    obj(s, ["Bounty: Raider              2d 21h", "  of the Void Raiders"])
    s.box(1024, 392, 240, 160, "TGT", "Raider  HOSTILE", [
        "corvette, Void Raiders",
        "FIRING ON YOU",
        "state  fighting",
        "hull   damaged (sensor est.)",
        "range  3.2 km   closing 41 m/s",
        "CPA    0.6 km in 1:15",
        "[bounty contract]"])
    s.box(776, 560, 304, 200, "WPN", "Guns in hand, PD automatic", [
        "NOSE      coilgun  HAND  READY  tof 1.1s",
        "FWD PORT  autocan  AUTO  ENGAGING 2",
        "FWD STBD  autocan  AUTO  IDLE",
        "AFT STBD  laser    HAND  OUT OF RANGE",
        "AFT PORT  harpoon  HAND  3/4 READY lock",
        "",
        "DECOY     READY",
        "(a ship with an emitter adds a",
        " shields row: state, %, trim, 4 sectors)"])
    s.box(1088, 584, 176, 176, "SNS", "Scope 5 km (auto)", ["", "", "", "", "", "", "", "1 hostile"])
    # A shot at the ship drops an approach order (HelmOrders.threat), so the helmsman holds the heading
    s.box(400, 734, 360, 26, "HLM", "Helm: Bea   holding heading", kind="cond")
    target_lock(s, 815, 292, "Raider")
    s.box(420, 230, 150, 28, "", "missile path", kind="world")
    hnt(s, "Space fire   R missile   Tab next target   V stand up   F1 controls", "note: F decoy is not repeated, the band names it")
    legend(s, 470)
    s.save(os.path.join(out, "gunnery-combat.svg"))


# ---- 4. flight instrument modes -------------------------------------------------------------

def flt_modes(out):
    s = Svg("The flight instrument's four modes", w=1280, h=560)
    s.rect(0, 0, 1280, 560, fill="#e9e9e6", stroke="#9a9a9a", rx=0, sw=1)
    s.text(16, 26, "FLT shows one mode at a time, chosen by the first that applies, top to bottom; in rails warp it hides.", size=13, weight="bold")
    s.box(16, 44, 600, 112, "1", "DEPARTURE  (docked)", [
        "Docked at Highport, port 1 (S)       undock clearance: given",
        "dv        7,007 m/s  ok   (furthest contract needs ~3,900)",
        "repairs   none needed  ok      crew  all aboard  ok",
        "missiles  2/4   short   [Yard: fill for 2,000 cr]",
        "[U] undock          [T] station terminal"])
    s.box(664, 44, 600, 112, "2", "DOCK  (dockable target within 5 km, port armed or dock order)", [
        "your port 1/1 (S, active)  >  Highport port 2 (S)   CLEARED",
        "range 312 m     closing 6.1 m/s   safe up to 7.9 m/s",
        "|====== safe =====o==|~ caution ~|!! warning !!|",
        "offset  L 2.2 m     angle  turn 4 deg left",
        "[G] arm/ask   [5] helm: dock for me   (H only with 2+ ports)"])
    s.box(16, 176, 600, 112, "3", "BURN  (next burn starts within 3 min, or one is being flown)", [
        "Burn 1 of 2     T-1:52   (turn starts T-2:00)",
        "dv to go 3,084 m/s   burn time 4:17",
        "nose error 31 deg  [align marker on screen]",
        "flown by: flight computer (hands off)",
        "[/] warp to burn"])
    s.box(664, 176, 600, 112, "4", "CRUISE  (otherwise)", [
        "frame Raider (locked target)   speed 41 m/s",
        "dv left 7,007 m/s  |------^---|  home 410 m/s",
        "[main] [RCS]  lit while firing",
        "rotation assist OFF   (shown only when off)",
        ""])
    s.box(16, 308, 600, 112, "", "Rails warp: FLT hides", [
        "The chronometer already has the rate, the next stop (its",
        "countdown allows for the drop-out lead) and what holds warp.",
        "At 10,000x there is nothing to steer, so Z8 stays empty",
        "unless HLM has an order to show."], kind="cond")
    s.box(664, 308, 600, 112, "", "Rules for every mode", [
        "Same zone, same size class: modes swap, they don't stack.",
        "Frame: locked target, nav target or station within 5 km, else orbit.",
        "dv is always 'left' against 'what home takes' (Navigation).",
        "A key on screen in a mode is never repeated on the hint line.",
        "Who flies: HLM's line when a helmsman does; BURN names only you or the computer.",
        "Closing speed is the target card's; apsides are the map's."])
    s.text(16, 450, "Mode order is a priority list: docked beats everything; a dock approach beats a burn's countdown, but a burn",
           size=11.5, fill="#444")
    s.text(16, 466, "being flown beats docking (the helm flies a planned burn ahead of any order); a burn beats cruise.",
           size=11.5, fill="#444")
    s.text(16, 482, "Rails warp drops out for a burn, so BURN never meets it.", size=11.5, fill="#444")
    s.save(os.path.join(out, "flight-modes.svg"))


# ---- 5. deck ----------------------------------------------------------------------------------

def deck(out):
    s = Svg("On deck during damage control")
    s.screen_bg("DECK, on foot, ship damaged")
    # The starter corvette, nose to the right. The room rule gives it two rooms: the Hold aft
    # (cargo bays, tanks, engines) and the Bridge forward (helm, weapons console, life support,
    # reactor, the guns' mounts), with a wall and a door between them.
    s.rect(360, 270, 560, 210, fill="#dcdcd8", stroke="#555", sw=1.5)
    s.add('<line x1="640" y1="270" x2="640" y2="358" stroke="#555" stroke-width="2"/>')
    s.add('<line x1="640" y1="392" x2="640" y2="480" stroke="#555" stroke-width="2"/>')
    s.text(640, 262, "door", size=9, fill="#999", anchor="middle")
    s.text(500, 292, "Hold", size=11, fill="#666", anchor="middle")
    s.text(780, 292, "Bridge", size=11, fill="#666", anchor="middle")
    # hazards, each tagged with its worker's progress or "nobody"
    s.box(392, 316, 120, 40, "", "fire (medium)", ["[======    ]"], kind="world")
    s.add('<circle cx="452" cy="380" r="5" fill="#888"/>')
    s.text(462, 384, "Cole", size=10, fill="#777")
    s.box(660, 316, 120, 40, "", "conduit short", ["[=======   ]"], kind="world")
    s.add('<circle cx="700" cy="372" r="5" fill="#888"/>')
    s.text(710, 376, "Ada", size=10, fill="#777")
    s.box(796, 352, 112, 40, "", "breach, venting", ["nobody"], kind="world")
    s.add('<circle cx="880" cy="320" r="5" fill="#888"/>')
    s.text(870, 324, "Bea (helm)", size=10, fill="#777", anchor="end")
    s.add('<circle cx="700" cy="430" r="6" fill="#555"/>')
    s.text(712, 434, "you", size=10, fill="#555")
    s.box(640, 446, 176, 26, "", "Weapons console   V sit", kind="world")
    chr_(s, "WARP 1x", "held: fire aboard")
    alr(s, None, ("FIRE Hold  Cole", "VENTING Bridge  nobody", "POWER Bridge  Ada"))
    ann(s, [("FIRE", "caut"), ("POWER", "caut"), ("AIR", "caut"), ("CREW", "adv")], shields=False)
    s.box(16, 144, 240, 78, "PER", "You", [
        "health  light wound",
        "suit    pressure   air 7:40",
        "here    Bridge: thin"], kind="cond")
    # Z3 is 240 wide: a row too long for one line breaks before where the job goes, and its time
    # stays on the first line; the urgent delivery is late on the map's plan, so it is marked
    obj(s, ["Deliver 40 Food        LATE  2d 9h", "  to Shackleton Ice"])
    # A slip takes two lines at most; Bea's light wound is the CREW lamp's advisory
    ntf(s, ["Bea is lightly wounded and working", "  slower"], y=80)
    s.box(420, 734, 440, 26, "HLM", "Helm: Bea   holding heading", kind="cond")
    hnt(s, "W/A/S/D walk   O lights   F1 controls", "note: E and V are not repeated, the prompt shows them")
    legend(s, 600)
    s.save(os.path.join(out, "deck.svg"))


# ---- 6. map -----------------------------------------------------------------------------------

def map_(out):
    s = Svg("The map: plan a trip")
    s.screen_bg("MAP (full screen, any post; game keeps running)")
    s.rect(16, 100, 920, 584, fill="#f3f3f0", stroke="#999", sw=1)
    s.text(476, 382, "orbit canvas: paths, burns and handles, traffic, encounters", size=12, fill="#888",
           anchor="middle", style="italic")
    s.add('<circle cx="476" cy="392" r="150" fill="none" stroke="#aaa" stroke-width="1"/>')
    s.text(28, 122, "EARTH SYSTEM   [M] or [Esc] close", size=12, weight="bold", fill="#444")
    chr_(s, "WARP 1x", "")  # over the map the plan's times are MAP.2's and MAP.5's, not the chronometer's
    s.box(320, 16, 640, 76, "ALR", "(alerts stay visible over the map, both rows)", kind="cond")
    s.box(944, 100, 320, 134, "MAP.1", "DESTINATION", [
        "Shackleton Ice  (Selene Mining Combine)",
        "relation: neutral  standing 12",
        "services: market, yard, shipyard,",
        "          contracts, hiring",
        "serves: Deliver 40 Food",
        "distance 385,479 km",
        "[Q] next   [Shift+Q] back   list >"])
    s.box(944, 242, 320, 176, "MAP.2", "PLAN", [
        "proposal: transfer, 2 burns",
        "dv needs 3,912   left 7,007",
        "after plan 3,095  home from there 520",
        "arrives Day 7 09:40",
        "deadline Day 6 00:00  LATE by 1d 9h 40m",
        "",
        "burn 1  T-1:29:51   3,084 m/s  prograde",
        "burn 2  T-3d 17h      828 m/s  retro",
        "",
        "[;] plan  [Enter] accept  [Bksp] discard"])
    s.box(944, 426, 320, 100, "MAP.3", "FLY IT", [
        "[/] warp to burn 1",
        "burn 1 flown by  Bea (helmsman)",
        "[6] autopilot           ready",
        "",
        "greyed with the reason when not allowed"])
    s.box(944, 534, 320, 60, "MAP.4", "SELECTED BURN", [
        "burn 1   prograde +3,083.6   radial +0.0",
        "drag a handle; Shift finer; Del delete"], kind="cond")
    s.box(16, 692, 1248, 92, "MAP.5", "PLAN TIMELINE  (now to arrival, log scale)", [
        "now --|burn 1 1.5h ---!deadline 2.4d ---|SOI Moon 3.3d -|payday 3.4d -|burn 2 3.7d -|arrive 3.8d",
        "click an event: centre the map on it      double-click: warp to it"])
    s.box(24, 592, 220, 84, "MAP.6", "Layers", [
        "[x] stations  [x] traffic",
        "[x] pirates   [ ] derelicts",
        "[ ] faction tension"], kind="cond")
    s.save(os.path.join(out, "map.svg"))


# ---- 7. station terminal --------------------------------------------------------------------

def terminal(out):
    s = Svg("Station terminal: one screen for every service a station sells")
    s.screen_bg("TERMINAL (docked; T / J / Y open it on their tab)")
    chr_(s, "WARP 1x", "")
    s.box(320, 16, 640, 76, "ALR", "(alerts stay visible over every screen, both rows)", kind="cond")
    s.box(16, 100, 1248, 52, "TRM.H", "HIGHPORT   Terran Patrol Authority (your sponsor)   standing 22", [
        "credits 18,450 cr     hold 6 / 40 m3     contracts held 1 / 3                         [Esc] back to the deck"])
    # Highport repairs and refits but sells no ships (the Patrol's shipyard is Anvil Yards), so no SHIPYARD tab
    tabs = ["CONTRACTS", "MARKET", "YARD", "CREW"]
    x = 16
    for i, t in enumerate(tabs):
        s.rect(x, 160, 150, 28, fill="#ffffff" if i == 0 else "#f0f0ec", stroke="#3a3a3a", sw=2 if i == 0 else 1)
        s.text(x + 75, 179, t, size=12, weight="bold" if i == 0 else "normal", anchor="middle")
        x += 158
    s.text(x + 4, 179, "(no SHIPYARD tab: Highport sells no ships; Anvil Yards does)", size=10, fill="#999", style="italic")
    # The day's five, and the urgent delivery Shackleton Ice's shortage posted here (an extra, WorldEvents)
    s.box(16, 196, 760, 588, "TRM.1", "Offers (5 today, 1 operation; 1 urgent)", [
        "type   job                            pay      standing   time    reach",
        "OPER.  Clear the Lanes (1/3)          8,000    +6         --      LOCKED: Lieutenant",
        "BOUNTY Raider of the Void Raiders     7,500    +6         3d      here     ok",
        "DELIV. 40 Food to Shackleton Ice      2,620    +5 Selene  2d 9h   3.8 d    LATE 1d 9h",
        "ESCORT Hauler Kestrel to Anvil Yards  1,500    +4         2d      2.1 h    ok  dv 3,000",
        "PATROL Deepwell Depot and back        1,000    +3         2d      2 h      ok  dv 1,860",
        "DELIV. 18 Food to Anvil Yards         860      +3         2d      2.1 h    ok  dv 3,000",
        "",
        "'reach' = travel time and dv from here, checked against dv left and the deadline",
        "'pay' for a delivery = its fee, plus the buyer's price for the cargo, less its price here",
        "'standing' names the giver when it isn't your sponsor",
        "",
        "sorted by: pay / time / distance (player's choice)"])
    # Shackleton Ice ran short of food on day 3 (WorldEvents): an urgent delivery, due 3 days after the
    # roll, posted at Highport for the short station's owner, sized to the whole 40 m3 hold
    s.box(784, 196, 480, 588, "TRM.2", "Deliver 40 Food to Shackleton Ice", [
        "why     Shackleton Ice is short of Food (urgent)",
        "giver   Selene Mining Combine, posted here",
        "cargo   40 food (40 m3), 25 cr each here: 1,000",
        "pay     500 fee + 78 cr each at the buyer = 3,620",
        "        net of the cargo 2,620 cr",
        "standing +5 with Selene   fail -2.5",
        "deadline Day 6 00:00 (2d 9h)",
        "",
        "route   Shackleton Ice, at the Moon",
        "        transfer ~3,900 m/s, ~3.8 d",
        "        arrive Day 7 09:40 -> 1d 9h 40m late",
        "hold    needs 40 m3, 34 free -> 6 m3 short",
        "",
        "[Take]        [Show route on map]"])
    s.save(os.path.join(out, "terminal.svg"))


# ---- 8. ship's book ----------------------------------------------------------------------------

def book(out):
    s = Svg("Ship's book: the ship, crew, contracts, career and log, anywhere")
    s.screen_bg("BOOK (anywhere; I / J / L open it on their tab)")
    chr_(s, "WARP 1x", "")
    s.box(320, 16, 640, 76, "ALR", "(alerts stay visible over every screen, both rows)", kind="cond")
    tabs = ["SHIP", "CREW", "CONTRACTS", "CAREER", "LOG"]
    x = 16
    for i, t in enumerate(tabs):
        s.rect(x, 100, 150, 28, fill="#ffffff" if i == 0 else "#f0f0ec", stroke="#3a3a3a", sw=2 if i == 0 else 1)
        s.text(x + 75, 119, t, size=12, weight="bold" if i == 0 else "normal", anchor="middle")
        x += 158
    s.text(1264, 119, "SHIP'S BOOK   [Esc] close", size=12, weight="bold", fill="#444", anchor="end")
    s.box(16, 136, 760, 392, "BOOK.1", "Deck plan  (layer: damage | power | air | crew)", [
        "the schematic at full size, one layer at a time",
        "click a room: what is in it, who is there, what is wrong",
        "layers reuse the deck overlays' symbols"])
    # the starter corvette, nose to the right: Hold aft, Bridge forward
    s.rect(100, 226, 560, 210, fill="#e4e4e0", stroke="#555", sw=1.4)
    s.add('<line x1="380" y1="226" x2="380" y2="314" stroke="#555" stroke-width="2"/>')
    s.add('<line x1="380" y1="348" x2="380" y2="436" stroke="#555" stroke-width="2"/>')
    s.text(240, 250, "Hold", size=11, fill="#666", anchor="middle")
    s.text(520, 250, "Bridge", size=11, fill="#666", anchor="middle")
    s.text(240, 336, "fire", size=10, fill="#444", anchor="middle", weight="bold")
    s.text(470, 336, "short", size=10, fill="#444", anchor="middle", weight="bold")
    s.text(600, 416, "breach", size=10, fill="#444", anchor="middle", weight="bold")
    s.text(380, 470, "starter corvette: two rooms by the naming rule", size=10, fill="#888", anchor="middle", style="italic")
    s.box(784, 136, 480, 392, "BOOK.2", "Systems", [
        "REACTOR (M)    3.0 MW   on    ok",
        "draw now 2.4 MW    store 2 s  ok",
        "LIFE SUPPORT   on    serving 2 of 2 rooms",
        "   O2 tank 82%   N2 tank 90%",
        "ENGINES  2 (S) ok    RCS 7/8 (1 wrecked)",
        "PROPELLANT 1,640 kg  -> dv 7,007 m/s",
        "PORT   1 (S, active)  ok",
        "POD    1 (S), 4 seats",
        "HOLD   6 / 40 m3  (manifest >)",
        "",
        "switches live at the consoles; this page",
        "says where to go ('walk there' marker)"])
    s.box(16, 536, 1248, 224, "BOOK.3", "Damage and work", [
        "what                  where         who's on it                progress",
        "fire (medium)         Hold          Cole                       shrinking",
        "conduit short         Bridge        Ada                        [=======   ]",
        "breach, venting       Bridge        nobody: no patches left    needs a yard (Y there)",
        "RCS block wrecked     outside, aft  waiting: EVA is held       [let out]"])
    s.save(os.path.join(out, "book.svg"))


# ---- 9. boarding --------------------------------------------------------------------------------

def boarding(out):
    s = Svg("Boarding panel beside the deck view")
    s.screen_bg("BOARDING (K; latched to a ship, or a beaten one within 300 m)")
    s.rect(120, 230, 360, 200, fill="#dcdcd8", stroke="#555", sw=1.5)
    s.text(300, 220, "your ship (deck)", size=10, fill="#777", anchor="middle", style="italic")
    s.rect(500, 260, 300, 160, fill="#d0d0cc", stroke="#555", sw=1.5)
    s.text(650, 250, "Raider (taken)", size=10, fill="#777", anchor="middle", style="italic")
    s.box(540, 300, 160, 40, "", "Ada: stripping laser", ["Cole: waiting to carry"], kind="world")
    chr_(s, "WARP 1x", "")
    ann(s, shields=False)
    # The Raider near Highport flies with no crew and no cargo (GameScenario), so the first order took
    # it (Boarding.takeUndefended). Its value is 96 hull tiles and its guns, 213,000 (Difficulty):
    # a station pays a twentieth of a part's price (Salvage.SALE_SHARE) and of the prize, and a
    # fiftieth of what is left once anything is off (Shipyard), each gun taken off taking its price.
    # A tile's work is 10 s at the usual rate, so a Small turret's gun takes 10 s, the Medium reactor 40 s.
    s.box(904, 72, 360, 684, "BRD", "Boarding: Raider", [
        "state     TAKEN, nobody aboard (was: reactor down)",
        "link      latched, port to port",
        "cost      none (outlaw)",
        "",
        "ACROSS                status",
        "Ada (engineer)        stripping the laser",
        "Cole (marine)         waiting to carry",
        "                      [Recall all]",
        "",
        "SALVAGE           sells  work  crate",
        "Laser Mk1         2,250  10 s  1 m3  stripping 40%",
        "Reactor           2,000  40 s  1 m3  [strip]",
        "Harpoon Launcher  1,500  10 s  1 m3  marked",
        "Engine            1,250  20 s  1 m3  [strip]",
        "11 more, 5,500 in all       [show all]",
        "",
        "CARGO  hold empty",
        "hold after all of it: 21 of 40 m3",
        "",
        "PRISONERS  none aboard",
        "",
        "PRIZE  a station within 2 km takes it in",
        "sale 10,650 cr whole",
        "     2,760 once the marked parts are off",
        "or turn it in at Highport: +6 standing"])
    hnt(s, "K close   W/A/S/D walk   F1 controls")
    s.save(os.path.join(out, "boarding.svg"))


# ---- 10. crisis -----------------------------------------------------------------------------------

def crisis(out):
    s = Svg("Disabled: what can still be done")
    s.screen_bg("CRISIS panel (ship disabled)")
    s.clear_zone()
    s.ship(640, 520, "your ship, drifting")
    chr_(s, "WARP 1x", "")
    alr(s, ("SHIP DISABLED  no reactor running", "options below, or let the crew work"), ())
    ann(s, [("POWER", "warn"), ("HULL", "caut"), ("AIR", "caut")], shields=False)
    s.box(400, 120, 480, 200, "CRS", "Options", [
        "crew working:  Ada resetting the reactor short, ~20 s",
        "               (breaker recloses by itself in 41 s)",
        "",
        "[wait]          let the crew work",
        "[B][B] tow      fee 9,600 cr, -3 standing, 3 days,",
        "                docks at Highport with ship and crew",
        "[P][P] abandon  pod: 1 (S), 4 seats for 4 aboard",
        "",
        "air aboard: 41 min at this crew"])
    hnt(s, "F1 controls", "note: the crisis keys live in the panel, not here")
    s.save(os.path.join(out, "crisis.svg"))


if __name__ == "__main__":
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    for f in (zones, helm_quiet, gunnery_combat, flt_modes, deck, map_, terminal, book, boarding, crisis):
        f(out)
    print("wrote", sorted(os.listdir(out)))

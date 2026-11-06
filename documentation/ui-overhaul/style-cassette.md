# Cassette-Futurism: Style Guide and Design Language

**Working name: "Yard Standard"** — the console hardware a working shipyard bolts into a hull.

Artist 3 of 3 in the UI overhaul. This document sets the visual and interaction language. It does not
decide *what* information exists or *when* it is visible; the three functional specs in
`documentation/ui-overhaul/` do that. Section 14 says how a functional spec is translated into this
language, and that is where this style is more than a skin.

![The flight console in the Yard Standard style](cassette/mockup-flight.png)

![Controls, instruments, palette and type](cassette/mockup-components.png)

---

## 1. The premise

The ship's UI is not a screen painted over the game. It is **equipment**: a console deck with cathode
ray tubes, needles, lamps, counters and switches, built cheaply and to last, by people who expected it
to be kicked, rewired and repaired. The view of space is what you see past the console, through the
gunsight glass.

Five principles follow from that, and every later rule comes from one of them.

1. **Every element is a physical object with a place.** A gauge does not pop up; it is mounted, and it is
   always there (lit or dark). Information moves *between* fixed instruments, it does not spawn windows.
   This gives the player muscle memory: power is always the third needle from the left.
2. **Each kind of data has one kind of instrument.** Rates and trends are needles; exact quantities are
   drum counters; on/off states are lamps; space and lists are CRTs. The instrument's shape tells you
   what sort of number you are reading before you read it (section 8).
3. **Controls have travel and state.** A switch is up or down and *stays* there, so the console itself
   shows what you have set. Dangerous actions sit under guards. Nothing important is a flat text button.
4. **The console runs on the ship.** The instruments are powered by the ship's circuits (interior-grid's
   `PowerRoutingSystem`). A browned-out bus dims its CRTs and drops its needles; a fire in the bridge
   makes the console flicker. The player reads the ship's health from the console's behaviour, not only
   from a number (section 11).
5. **Warm, worn, legible.** Beige enamel, brown and black, amber and green phosphor. Wear is visible but
   never in the way of a reading. When the style and readability disagree, readability wins.

### What this style is not

The other two artists take neighbouring ground. To keep the three clearly different:

| | Nasa-punk (artist 1) | 90s VFD (artist 2) | **Cassette-futurism (this)** |
|---|---|---|---|
| Surface | Black glass, white and grey vector | Dark smoked glass, segment cells | **Painted metal and plastic, beige and brown, screws and seams** |
| Light | Crisp emissive lines, no glow | Cyan-teal VFD glow, dot matrix and 7-segment | **Amber and green CRT phosphor with afterglow; incandescent lamp lenses** |
| Numbers | Clean sans, tabular | Segmented digits | **Rolling drum counters, needles on printed dials** |
| Controls | Touch, soft keys around an MFD | Membrane keys, beeps | **Toggles, rockers, rotary selectors with detents, guarded switches** |
| Motion | Smooth, precise, quick | Instant, stepwise flicker | **Mechanical: spring, inertia, roll, warm-up** |
| Feel | Clean mission control | Consumer electronics | **Heavy industry, a 1979 freight hauler** |

Hard rules that keep us out of their space: **no cyan or teal anywhere**, no seven-segment digits, no
dot-matrix text, no thin-line glass cockpit framing, no flat touch buttons.

---

## 2. The screen, as hardware

The screen is divided into the **viewport** (space, seen through glass) and the **deck** (the console).

```
┌───────────────────────────────────────────────────────────────────────────┐
│ ▣ CHRONO 0012:07:44:10            viewport                  WARP ◉ 1×   │  ← header strip: 2 small
│                                                                           │    bracket instruments
│                     space, the ship, markers, the gunsight                │
│                     (projected in amber phosphor on the glass)            │
│                                                                           │
├──┬────────────┬──────────────────┬──────────────┬──────────────────────┬──┤
│  │  SCOPE     │  FLIGHT          │ ANNUNCIATOR  │  MAIN CRT  (pages)   │  │  ← the deck: 22–26% of
│  │  (round    │  counters,       │ lamp grid +  │  amber raster        │  │    height at 16:9,
│  │   PPI CRT) │  needles, compass│ master C/W   │  ◉ page selector     │  │    with a 1-unit lip
│  │            │                  │              │                      │  │
│  └ range ◉   └ switch row ──────┴──────────────┴ soft keys ───────────┘  │
└───────────────────────────────────────────────────────────────────────────┘
```

* **The deck sits along the bottom** like the dashboard of a vehicle. It is built of **pods**: a cluster
  pod, display pods whose tubes sink into the deck when a spec folds them (pop-up tubes, like pop-up
  headlights), and a sill for the station board, so a spec that hides part of the band can do it
  mechanically. It is the only place persistent
  UI lives. It has a hard top edge (a rolled metal lip with a highlight) so the eye reads it as solid.
* **The viewport is kept clear.** Only things that belong *in space* are drawn there: contact brackets,
  the gunsight, missile tracks, the dock approach. They are drawn as a **reflector-sight projection**:
  amber phosphor on glass, slightly glowing, no backgrounds, no panels. Nothing rectangular floats over
  space.
* **Two header brackets** at the top corners carry the chronometer (mechanical counter) and the warp
  selector (a rotary knob with detents). They are the only hardware above the deck, small, and mounted
  on visible brackets so they read as objects, not overlays.
* **Bays.** The deck is built of bays, each a separate faceplate with its own screws and seam. A
  functional spec's "widget" becomes an instrument in a bay. Bays are a fixed width in grid units
  (section 6), so the deck is laid out like a rack, not a free canvas.
* **Large tasks take hardware, not a window.** Trade, the yard, missions, boarding and the map are
  built from tubes, controls and paper. Small ones run on the deck's main CRT; full-screen modes
  **raise the big console**: the deck slides down and a wide console rises in its place; side panels
  are cabinets on the side rail (section 9). There are no draggable windows.

### Interior view

When the player walks the deck, the console is not in front of them, so the deck is replaced by a
**wrist unit** (bottom left: a chunky beige forearm terminal with a small amber CRT, two needles and
four push buttons) and **wall consoles** in the world that the avatar uses. A console the avatar walks
up to opens as its *own* faceplate rising from the bottom edge, in that console's livery.

### Station equipment has its own livery

When docked, station services (market, yard, mission board) are **the station's terminal**, patched into
the deck by a visible cable and plug. The station's terminal uses its faction's hardware livery
(section 4.4): a corporate station is clean off-white plastic with a logo plate; a frontier mine is
olive drab with stencils. The player reads *whose equipment this is* at a glance, and the ship's own
deck is never re-skinned by a station.

---

## 3. Palette

All colours are sRGB hex. Hardware colours are **materials**, lit by a soft top-left key light; phosphor
and lamp colours are **emissive** and the only colours that may glow.

### 3.1 Hardware (non-emissive)

| Token | Hex | Use |
|---|---|---|
| `enamel` | `#D9CFB4` | Main faceplates, the deck. Warm putty beige. |
| `enamel-shade` | `#B8AD92` | Lower bevels, recesses, panel seams' lower lip. |
| `enamel-light` | `#ECE4CE` | Top bevel highlights, rolled edges. |
| `chassis` | `#4A3F36` | Bay surrounds, the frame between bays, knob skirts. Chocolate brown. |
| `chassis-deep` | `#2B241F` | Gaps, screen surrounds, shadows behind hardware. |
| `bakelite` | `#1E1A17` | Knobs, toggle bats, switch boots, CRT bezels. |
| `alu` | `#A9A69E` | Brushed aluminium trim strips, screw heads, guard frames. |
| `rubber` | `#3A3734` | Keycaps' sides, grommets, cable. |

### 3.2 Markings and accents (non-emissive paint)

| Token | Hex | Use |
|---|---|---|
| `ink` | `#2A2420` | Engraved legends and scales on `enamel`. |
| `ink-light` | `#EFE8D6` | Legends on `chassis` and `bakelite`. |
| `safety-orange` | `#D9602B` | The deck's identity stripe; "this changes the ship" controls. |
| `signal-red` | `#B8352B` | Guards over irreversible controls; red-line zones on dials. |
| `mustard` | `#D9A637` | Hazard stripes (with `bakelite`), caution zones on dials. |
| `avocado` | `#6E7846` | Secondary panel colour, crew/life-support bays. |
| `tape-red`, `tape-black`, `tape-blue` | `#9E2A23`, `#1C1B1A`, `#2F4A7A` | Dymo label tape backgrounds. |

The deck has a **safety-orange stripe** under its top lip. It is the one decorative element that
identifies the style at a glance, the way a livery stripe identifies a vehicle.

### 3.3 Phosphor (emissive, on CRTs and the gunsight)

| Token | Hex | Use |
|---|---|---|
| `p3-amber` | `#FFB23F` | Raster CRT text and the gunsight. The *voice* of the ship. |
| `p3-amber-dim` | `#B27B2C` | Secondary text, gridlines, unselected list rows. At least 5:1 against `crt-black`, so secondary text stays readable. |
| `p3-amber-ghost` | `#4A300F` | Afterglow trails, burn-in. |
| `p1-green` | `#7CFF6A` | Vector CRTs: the scope, the orbit map, trajectories. |
| `p1-green-dim` | `#2F8A35` | Range rings, grids, predicted (not yet real) paths. |
| `crt-black` | `#0E100C` | The tube face when lit but empty (never pure black; a lit tube is grey-green). |
| `crt-off` | `#22251F` | The tube face unpowered (glass reflection, no image). |

**Amber is for words and the ship's own state; green is for space and geometry.** A player learns that
anything green is a place or a path and anything amber is a statement.

All tubes are **beam-penetration tubes** (the two-colour CRTs of 1970s air-traffic and military
displays): one tube draws green geometry, amber words and red threats. This is what lets a
multi-function display show a radar page and a text page in the same place.

Contacts on the green scope and on the gunsight use **shape first and brightness second**, not hue
(section 7.2), because a monochrome tube can only do that, and it is better for colour-blind players.
The one exception is hostile threat: a hostile missile track on the gunsight is drawn in `lamp-red`
phosphor (a period "dual-phosphor" tube), always with its own shape (a broken line with chevrons).

### 3.4 Lamps (emissive, behind coloured lenses)

| Token | Lit | Unlit lens | Meaning |
|---|---|---|---|
| `lamp-red` | `#FF4A35` | `#5A1E18` | Warning: act now. |
| `lamp-amber` | `#FFA733` | `#5A3B16` | Caution: act soon. |
| `lamp-green` | `#8CFF5A` | `#24401C` | Engaged, safe, go. |
| `lamp-white` | `#FFF3D6` | `#4A4740` | Advisory, information, "on". |

An unlit lamp is *visible* (a dark coloured lens), so the player sees every lamp that *could* light. A
lamp that is lit glows past its lens edge (bloom radius 1.5 units).

### 3.5 Colour rules

* Hue never carries meaning alone. Every lamp has an engraved legend; every warning has a sound and a
  position.
* Red means danger only. No red decoration, no red for "hostile faction" when nothing is threatening.
* Never put phosphor colours on hardware or hardware colours on a tube.
* Contrast: legends on `enamel` are `ink` (contrast 10.9:1); phosphor text on `crt-black` is ≥ 9:1
  at full brightness and ≥ 4.5:1 for `-dim` tones.

---

## 4. Materials

Materials are what makes this style tactile. Each is a 9-patch or a shader, never a flat fill.

### 4.1 The palette of surfaces

* **Painted enamel faceplates.** Slight noise (2% luminance grain), a 1-unit top-left highlight and a
  1-unit bottom-right shade on every edge. Corners are radius 3 units: folded sheet metal, not
  rounded plastic.
* **Brown chassis** between bays, recessed 2 units, with **Phillips screw heads** at each faceplate's
  corners (aluminium, randomly rotated per screw, seeded by bay so they do not change between frames).
* **Bakelite knobs and bezels.** Near-black with a soft specular band. Knobs have a printed index line
  and a knurled skirt.
* **Smoked glass over CRTs.** A CRT sits in a deep bezel, its face very slightly curved (barrel
  0.02), with a faint reflection gradient top-left. Behind the glass: scanlines, phosphor bloom, and
  persistence (section 10.4).
* **Engraved legends** on faceplates (ink fill with a 0.5-unit light lower edge, like a filled engraving).
* **Dymo tape labels** for anything the crew might have *added*: custom names, a ship's name on the
  console, the player's own bookmarks. Raised white letters on red, black or blue tape, slightly
  skewed (≤ 1.5°). Dymo is the style's handwriting: it is how the game says "this is yours".
* **Paper** for printouts: contracts, trade receipts, the mission board. Off-white `#EEE6D2`
  tractor-feed paper with sprocket holes and faint green bar banding, typed in a typewriter face.

### 4.2 Wear

Wear is a texture layer, never on a reading. Allowed: paint rubbed through to grey metal at the edges of
the most-used switches; a scuff under a handle; a faded legend that is still legible; a strip of
tape holding a cable. Forbidden: dirt over a needle, a crack across a counter, grime that reduces
contrast below the section 3.5 minimums.

### 4.3 Wear follows the ship

Each hull class gets a wear level from its age (0 to 3). A new corvette from a corporate yard is
clean, crisp enamel; a forty-year-old hauler has worn switches and a Dymo label over a broken legend.
This is cosmetic, chosen at ship creation, and is the cheapest way to make two ships feel different.

### 4.4 Liveries

Panels can be re-coloured per owner. A livery changes only `enamel`, `enamel-shade`, `enamel-light`,
the identity stripe and the logo plate; instruments, phosphor and lamps never change.

| Livery | Enamel | Stripe | Notes |
|---|---|---|---|
| Yard Standard (the player's default) | `#D9CFB4` beige | `#D9602B` safety orange | |
| Corporate | `#E6E3DA` off-white | `#2F4A7A` blue | Smooth plastic, logo plate |
| Frontier / mining | `#6E7846` avocado | `#D9A637` mustard | Stencilled, heavy wear |
| Military | `#5E6152` grey-green | `#B8352B` red | Matte, rivets instead of screws |

---

## 5. Typography

All typefaces are open licensed (the SIL Open Font License, and Apache 2.0 for Special Elite), so they
ship in `assets/fonts` next to the Inter files that are there now. Four roles, each tied to a material:

| Role | Typeface | Where | Notes |
|---|---|---|---|
| **Engraved legend** | **Barlow Semi Condensed**, SemiBold, all caps, tracking +6% | Faceplate labels, scales on dials, switch positions | Industrial, condensed, like a panel engraver's alphabet |
| **CRT text** | **IBM Plex Mono**, Regular and Medium | Everything on a raster CRT | Monospace so CRT pages align to a character grid (section 9.5) |
| **CRT display** | **VT323** | Big CRT headings (≥ 24 units) only | Raster-terminal shape; never below 24 units |
| **Counter drums** | **Barlow Condensed**, Medium | Digits on drum counters | White on black drums, tabular figures |
| **Dymo / paper** | **Special Elite** (paper), **Barlow Condensed** Bold embossed (Dymo) | Printouts and labels | Typewriter for paper; emboss shader for Dymo |
| **Stencil** | **Big Shoulders Stencil Display** | Bay numbers, bulkhead markings, the title screen | Large sizes only |

### Scale (UI units, the same units as `HudFonts`)

| Token | Size | Use |
|---|---|---|
| `legend` | 12 | Switch and lamp legends, bay titles, key caps, annunciator text. **Nothing smaller** |
| `crt-body` | 15 | CRT text (the current `BODY` size, kept so layouts carry over) |
| `crt-large` | 20 | CRT emphasis (the target's name) |
| `counter` | 14–22 | Drum counter digits (drum 18–30 units tall) |
| `crt-display` | 28 | VT323 headings on a CRT page |
| `stencil` | 44+ | Title, bay numbers |

The floor is 12 units for any text and 13 for body text at UI scale 1, as the functional specs require.
Small instruments drop their scale numerals rather than shrink them: the zones and ticks carry the
reading, and the exact value is on a counter or a page.

Rules: legends are always ALL CAPS and short (≤ 12 characters; abbreviate like a panel engraver would:
`PROPLNT`, `XPDR`, `RCS`). CRT text is mixed case. Never use the CRT face on hardware or the legend face
on a CRT.

---

## 6. Grid, framing and layout

* **Base unit: 4 UI units.** Bays are multiples of 24 units wide (6 grid units) and the deck's height is
  a multiple of 24. Everything snaps; nothing is placed by eye.
* **Bay anatomy:** 2-unit chassis gap · 3-unit faceplate bevel · 8-unit margin to the first instrument ·
  instruments on a 4-unit grid · bay title engraved top-left at `legend` size, 6 units from the edge ·
  screws in the corners, 6 units in.
* **Deck heights:** at 16:9 the deck is 216 units tall at UI scale 1 on a 1080-unit-tall screen (20%).
  The deck has three densities the player can choose in settings, and cycle with one key:
  * **Full deck** – everything above.
  * **Low profile** – the deck folds to a 96-unit strip: counters, master caution and warning, the
    switch row and the scope shrunk to a 88-unit tube. Needles become edgewise meters.
  * **Clear view** – only the gunsight, the master caution/warning lamps (bottom corners) and the
    chronometer. For screenshots and for players who fly by feel.
* **Aspect ratios.** The deck's bays are centred in a fixed-width rack (1,824 units at 16:9). Extra width
  (ultrawide) fills with **blank panels** (enamel, screws, maybe a Dymo label), never by stretching
  instruments. Narrower screens drop bays in a defined order (set per functional spec).
* **Depth layers**, back to front: space → gunsight projection → deck shadow (a soft 24-unit shadow
  cast up onto space, so the deck sits in front of the glass) → deck → side cabinets and the raised big console → paper → a tag.

---

## 7. Iconography and symbology

### 7.1 Pictograms on hardware

Pictograms are **engraved**: 2-unit strokes in `ink`, on a 16-unit grid, built from straight lines
and circular arcs only, in the manner of industrial control symbols (ISO 7000 style): a lightning bolt
for power, a droplet for propellant, a bell for alarm silence, an arrow-into-box for dock. Each sits
*beside* its legend, never instead of it.

### 7.2 Symbols on tubes

On CRTs and the gunsight, symbols are vector strokes. **Shape carries identity**, brightness carries
importance:

| Thing | Symbol |
|---|---|
| Own ship | Small cross with a heading tick |
| Station | Square |
| Neutral ship | Notched dart pointing along its heading (never an upright triangle: that is the caution glyph ▲) |
| Hostile ship | Diamond |
| Unknown contact | Open circle |
| Locked target | The symbol in a four-corner bracket that blinks once on lock |
| Missile | A dot with a short leader and a chevron at its tip, pointing where it flies |
| Waypoint | Upright cross in a circle |
| Planned burn | Double chevron on the path, pointing in the burn direction (a triangle would read as the caution glyph) |

Filled symbols are *real and current*; open/dashed are *predicted or old*. Brightness: the selected
thing is full intensity, its neighbours at 60%, distant or stale contacts at 30%.

---

## 8. Instruments: which shape for which data

This is the core of the language. A functional spec says "show X"; this table says *how*.

| The data is... | Instrument | Why |
|---|---|---|
| A rate or trend you glance at (closing speed, power draw, thrust) | **Round needle gauge**, 270° sweep | A needle's angle and its movement read faster than digits |
| A level that drains (propellant, O₂, charge) | **Edgewise meter** (vertical moving pointer on a printed scale with caution and red-line zones) | Compact, sits in a row, shows the zone at a glance |
| An exact number you read carefully (speed, range, credits, Δv, time) | **Drum counter**, white on black, rolling | Precision; the roll shows *direction and rate of change* for free |
| A direction (heading, prograde, target bearing) | **Compass card** behind a fixed lubber line, rotating | The one familiar dial for orientation |
| An on/off or a fault state | **Lamp** in the annunciator, or a lamp on its own control | A grid of lamps lets a player see "all dark = all good" |
| A word that changes (a state, a mode, a frame, a name) | **Split-flap window**, a fixed number of cells | Mechanical and legible, and the riffle shows that it changed |
| Space, paths, positions | **Vector CRT** (green) | Lines, afterglow and persistence suit orbits and sweeps |
| Words, lists, tables, choices | **Raster CRT** page (amber), a ledger tube (section 9.5) | A character grid, cursor keys and soft keys |
| A value over time (market price, power history) | **Strip chart**: a pen trace on a scrolling paper strip in a window | Physical history; it keeps scrolling while you watch |
| A one-off record (contract, receipt, log) | **Printout** on tractor paper | The player can keep it; it is obviously a document |

Instrument rules:

* **Every needle has a printed scale** with numbers at the major ticks and zones painted on it (mustard
  caution arc, red-line arc). The zones are where the functional spec's thresholds are (today's
  `ShipGauges`: amber below 50%, red below 25%).
* **Every counter has a fixed number of drums** sized for the largest value it will show, with units
  engraved to its right (`M/S`, `KM`, `CR`). Leading zeros are shown dimmed, like real drums.
  Values that overflow show a red flag drum (`OVR`) instead of breaking the layout.
* **"No data" is mechanical:** a needle parks below zero against its pin; a counter shows a striped
  `OFF` flag in a window over the drums (the classic barber-pole flag); a CRT goes to `crt-off`.
  Today's `--` readings become these flags.
* **Units are engraved, not typed.** If a counter can change units (m/s to km/s), it has a small lamp
  beside each unit legend, and the lit one is the unit in use.
* **Engravings never change.** A legend is engraved once. If its words change with the state
  (ENGAGING or idle, a mount's name, RELOAD or READY), they go on a lens that lights, on flaps or on a
  drum. A time after a state goes on a drum, with `S` engraved after it.
* **A missing system is a blanking plate.** Where a hull lacks a system that other hulls fit (shields
  on the corvette), its bay keeps its size and is covered by a plain plate on quarter-turn fasteners,
  with no legend, so it can't read as switched off.

### 8.1 The annunciator

All abnormal ship states live in one **annunciator panel**: a grid of lens tiles, each with a two-line
engraved legend (`HULL BREACH`, `FIRE BRIDGE`, `LOW PROPLNT`). It carries **cautions and warnings
only**: engaged states such as a target lock or the transponder light on their own controls, so a dark
annunciator always means nothing is wrong. Where a spec makes the annunciator dynamic (tiles in order of
severity, with values), it becomes an overhead strip of lenses whose tags are backlit and whose values
are on split-flap windows, retracted when empty. This replaces the alert lists and status text lines.

Beside it, two big push-lights: **MASTER CAUTION** (amber) and **MASTER WARNING** (red). Any new
caution or warning lights its master and the tile; pressing the master (or its key) acknowledges it,
which silences the tone and stops the flash but leaves the tile lit until the condition clears. This is
the standard aircraft pattern and it maps cleanly onto the game's alert sources (`ShipAlerts`,
`ThreatProvider`, docking clearance, mission events).

### 8.2 The scope

A round **PPI** (plan position indicator) CRT in green: range rings, a sweep line rotating at 1 turn per
2 s that brightens contacts as it passes them, contacts fading with persistence between sweeps. Range is
a **rotary selector** under the tube with detents engraved `0.5 · 2 · 10 · 50 · 250 · 1000 KM`, the
steps `RadarScope` already has. The sweep is cosmetic: contact *positions* update every frame so the
scope is never less accurate than today's.

---

## 9. Off the flight deck: modes, panels, lists, forms and the shell

The deck is for flying. Everything else the game shows is built from the same hardware by the rules in
this section: the map, the station's services, the ship's systems and crew, boarding, the record, the
controls reference, and the shell around the game (the main menu, pause, load and save, settings, the
ship-lost screen and the mod-scripts question). The three specs name these screens differently
(Context's modes M1 to M6, the Stations spec's panels in its section 10, Task's MAP, TRM, BOOK, BRD
and CON), and the rules are the same for all of them. **There are no windows**: nothing has a title
bar, a close cross, a scroll bar or a drag handle.

### 9.1 Which surface for which screen

| The spec asks for | It is built as | For example |
|---|---|---|
| A full-screen mode | **The big console** (9.2) | The map, the ship, the station, the record; the Stations spec's Plot, Yard and Shipyard |
| A side panel | **A side cabinet** (9.3) | The Stations spec's Station link and Slate; Task's boarding panel |
| A deck console's panel | **That console's own faceplate** (9.3) | The Stations spec's Engineering; Task's CON |
| A list or a table | **A ledger tube** (9.5) | Market rows, contacts, crew, saves, offers |
| A value or a choice to set | **A form control** (9.6) | A quantity, a volume, a seed, a key binding |
| A confirmation | **A guard** over the control (9.7) | Delete a save, abandon ship |
| A question the game must ask | **A tag** (9.7) | Mod scripts |
| An ending | **A telex** (9.7) | Ship lost, career over |
| A record the player keeps | **Paper** (9.8) | A contract, the log, the career, the controls reference |
| The ship editor | **The drafting station** (9.10) | The shipyard |
| The shell | **The yard office** (9.11) | The main menu, pause, saves, settings |

A screen gets one surface. A spec's window that holds a list and a form is one console or cabinet with
a ledger tube and a form bay in it, not two windows.

### 9.2 The big console

A full-screen mode raises the **big console**: a wide console that rises from the coaming in 450 ms
with a 2-unit bump while the deck drops (section 10.3). A margin of space at least 16 units wide shows
round it and the sim keeps running, so the player is still in the cockpit. The header stays above it
with the master caution and warning, and whatever the spec lets interrupt a mode (Context's P3 and
X3, Task's crisis panel) still hangs in front of it.

On the 1280 × 800 grid:

* **Head**, y 30 to 74: the mode's **nameplate**, engraved at 20 units (MAP, SHIP, STATION, BOARDING,
  RECORD, CONTROLS); the tabs as piano keys (9.4); where the player is, on split flaps; and the close
  keycap at the right (the mode's own key, and `Esc`).
* **Body**, y 80 to 750: bays on the section 6 grid, each with its engraved title.
* **Foot**, y 754 to 786: a one-line **feed tube** for the newest message, and the keycaps for the
  tabs and for closing.

A station's mode is the station's equipment, not the ship's: it is in the station's livery (4.4), a
jack plate on its edge carries the cable to the ship, and undocking pulls the plug, so its tubes go
dark and the console sinks.

### 9.3 Side cabinets and faceplates

A **side panel** is a cabinet that slides in along the side rail in 300 ms, at the spec's width (the
Stations spec's 480, Task's 400), with a rolled lip on the edge that faces the view. It has the big
console's head, body and foot at its own width. The view beside it stays live, and a second panel
replaces the first rather than stacking on it.

A **deck console's panel** is that console's own faceplate, opening where the spec puts it (rising
from the bottom edge as section 2 says, or beside the console for Task's CON), in its system's livery:
a reactor's faceplate carries the hazard stripe, life support's is avocado, a gun's is military
grey-green. The console's name is on a cast plate at its top. Walking away closes it.

### 9.4 Tabs are interlocked piano keys

A mode's or a panel's tabs are a row of **interlocked piano keys** in its head, like the preset keys of
an old radio: pressing one releases the others. Each key carries its tab's engraved legend and its key
on a small keycap under it. The latched key sits 2 units lower with a safety-orange foot.

* A tab the spec offers only sometimes (a shipyard, at a station without one) keeps its place as a
  **blanking plate** with two screws, so the keys never move and the player's hand learns them. The
  Context spec closes the row up instead; there, the keys close up.
* A tab with something waiting for the player (a contract to hand in, a crew member asking for them)
  carries a small **amber jewel** on its key. Never a count badge.
* A row holds at most eight keys. More choices than that is a list, not tabs.

### 9.5 The ledger tube: lists and tables

Every list or table is an amber **ledger tube**: a raster tube laid out on a **character grid** in IBM
Plex Mono at `crt-body` (15 units: a column is 9 units and a row 18, so a 320-unit bay holds 34
columns). Columns align by character, never by pixel. If a table doesn't fit its bay's columns, it has
too many columns for that context.

* **The header row** is in `p3-amber-dim` with a rule under it. The sorted column carries a small drawn
  triangle (▲ ascending, ▼ descending), never a font glyph. The sort changes with a soft key or a
  rotary, not by clicking a header.
* **Rows never wrap.** Text too long for its column is cut with an ellipsis (`Shackleton I…`). Numbers
  are right-aligned on their units. A value that doesn't apply is `--` on a tube (a counter shows its OFF flag), and a stale one is dim.
* **The selected row** is in reverse video (`crt-black` on `p3-amber`). The cursor keys move it and a
  click sets it.
* **Severity** on a row is a drawn mark at its left (◆ warning, ▲ caution, the section 7.2 shapes), so
  it never rests on the phosphor's colour.
* **An empty list says so** in words on its first row (`No saves yet`). A tube is never left blank.
* **Scrolling** shows on a small **position meter** in the tube's bezel: the pointer's height is the
  position, and a white band is the share of the list in view. There are no scroll bars. The wheel
  scrolls, and Page Up and Page Down move a tubeful.
* **A row's actions** are soft keys under the tube, with their labels on its bottom row. A key that
  can't act on the selected row is struck through, and the reason is at the tube's bottom left
  (`SELL 1` struck, `NONE HELD`). Up to five actions that rarely change may be push buttons on the
  bay instead.
* **One headline value** for the whole list (the market's credits, the plan's Δv) is a drum counter or
  a meter on the bay beside the tube, not a row.

A tube's page has the same anatomy at every size: a title row in reverse video with the clock, the
content, a rule, and the soft-key labels in boxes on the bottom row. The cursor is a solid block that
blinks at 1.5 Hz. On a deck tube, pages are chosen with a **page selector** (a rotary with engraved
positions, such as `NAV · TGT · SHIP · CREW · COMMS`), and the soft keys take `1` to `6` while the
tube has focus.

### 9.6 Form controls

A form (settings, a new career, a quantity, a name) is built from the section 10.1 controls, never
from widgets drawn to look like a web page:

| A form needs | The control |
|---|---|
| On or off (a check box) | A **bat toggle**, its legend above it and ON and OFF engraved at its throws |
| A level (a slider) | A **slide pot** in a slot with an engraved 0 to 10 scale, and a drum beside it when the exact value matters |
| One of a fixed set of up to eight (a select) | A **rotary selector**, each position engraved |
| One of a set the game builds (window sizes, HUDs) | A **flap selector**: split flaps show the choice, with ◀ and ▶ keys |
| One of two or three shown side by side (a toggle group) | **Interlocked piano keys** |
| Words (a text field) | A **label holder**: a card in an aluminium holder, typed in Special Elite with a block cursor, its placeholder pencilled in grey |
| A number | A **thumbwheel**, a wheel for each digit |
| A key binding | A **patch-panel row**: the action on Dymo tape, its key on a keycap |
| An action (a button) | A **momentary push button**, barred with its reason while it can't act |

Controls are at least 24 units across their short side, and their legends at least 12. A form's
controls stand in reading order, top to bottom and left to right, so `FocusNav` and the gamepad step
through them in the order they are read. Changes take effect on `APPLY`, which is lit while there are
changes, except where the spec makes a control act at once.

### 9.7 Confirmations, questions and endings

There are no dialog boxes. The three jobs a dialog does each have their own object:

1. **A confirmation is a guard.** An action that can't be undone (delete a save, abandon ship, quit
   with progress unsaved) sits under a red flip guard. The first press lifts the guard and lights the
   lamp beside it, and the second acts. The guard drops again after 4 s, or on `Esc`. Nothing asks
   "Are you sure?".
2. **A question the game must ask is a tag.** It hangs on a wire over the dimmed screen like a lock-out
   tag on a switch: a yellow card with a black CAUTION band and a grommet, the question typed on it in
   Special Elite, and its answers on a small plate riveted to its foot. The safe answer is a push
   button and the dangerous one is guarded. `Esc` gives the safe answer, and a "remember this" choice
   is a bat toggle. Everything else waits until the tag is answered.
3. **An ending is a telex.** Ship lost and career over arrive as a telex that prints over the dimmed
   view at 30 rows a second: a header line with the day and time, the title, the message in capitals
   (a telex had no lower case), and the keys the screen offers on a plate under the sheet. A key it
   doesn't offer (Enter, with no autosave) is a blanking plate.

### 9.8 Paper

Paper is for records the player keeps and reads, never for something they must act on quickly. Each
kind of record has its own stock, so the player knows it by its paper:

| Record | Stock |
|---|---|
| A contract, an offer's terms | **Fanfold** with green bars and sprocket holes, torn off and clipped |
| The notice log | A **teleprinter roll**: a line for each notice with its day and time, the newest at the platen |
| The controls reference | **Ring-bound cards**, yellow, a card for each context (flight, guns, deck, the map), flipped with the tabs |
| The captain's record (career, standing) | A **service record book** in buff card, with rubber stamps |
| An ending | A **telex** (9.7) |

Paper is typed in Special Elite at 13 units or more, in `ink` on the stock. It scrolls by **feeding**:
the wheel and the cursor keys feed the sheet through, with the line-feed jitter of section 10.3.
Accepting a contract feeds its sheet out of a slot (with the printer's sound), and the sheet can be torn
off and pinned to the clipboard at the side of the deck. Clicking a pinned sheet enlarges it over the
view. Paper is always put away with one click or `Esc`.

### 9.9 The map

The map is the big console's **plotting tube**: a large green vector tube with persistence. The
predicted path is drawn in `p1-green-dim`, the track behind the ship glows and fades, bodies are their
outlines with their names in amber, and burn handles are hollow triangles. Dragging a handle shows the
burn's Δv on drums in the bezel's corner.

The spec's other parts of the map are the console's other bays: the plan and the target are amber
ledger tubes with push buttons for their actions (ACCEPT, DISCARD) and their totals on drums; the
timeline is a long, low **strip tube** along the foot, its events as ticks with their names; layers are
a row of bat toggles; a sort is a rotary (`DIST · KIND`); zoom is a rocker.

### 9.10 The ship at large

The ship's plan is a green vector tube: the rooms as outlines, hazards in red phosphor with their
shapes, damage in amber, and the crew as dots with their initials. The systems list beside it is a
ledger tube, and power is a bay of needles and drums (section 8).

**Damage control is a T-card board**, the planning board of an old maintenance shop: a column for each
crew member and one for UNASSIGNED, and each job a card in a slot, typed with the job, its place and its
state. Assigning a job moves its card to a column, with the mouse or with the arrow keys and a number
key. A job under way has a green tab and a blocked one a barber strip.

**The ship editor is a drafting station**: the hull on a large green tube, drawn with a **light pen**
(the mouse); the parts catalogue as **index cards** in a drawer, by category; and the totals (mass,
cost, Δv, power) on drums across the top. A part that can't go where it is pointed draws dashed, with
the reason on the tube's bottom row. Placing furniture on deck keeps the prompt plate the deck already
uses.

### 9.11 The shell: the yard office at night

The shell stands outside the fiction but is built from the same hardware: a **yard office** at night,
a desk console in Yard Standard under a big window. Today's backdrop (the drifting stars and the
planet's limb) is the view through that window. The specs list what each screen holds; this is how it
is built.

* **Main menu.** A cast **builder's plate** with RINGS OF IRON in stencil and A NEWTONIAN SHIP SIM
  engraved under it. A column of large push buttons: CONTINUE, NEW GAME (Task calls it NEW CAREER),
  FIRST PLAY, LOAD, SETTINGS, QUIT. Beside CONTINUE, a **tape deck** holds the newest
  save as a cassette, its label showing the save's name and details. With no save the deck is empty,
  and CONTINUE and LOAD are barred with NO SAVES YET on flaps. The version is stamped on a small serial
  plate in the corner.
* **New game** (Task's new career). The console's bay becomes a **commission panel**: the seed in a
  label holder (`random` pencilled in), the sponsor on interlocked keys (TERRAN PATROL, SELENE
  COMBINE), the spec's notes typed on cards clipped under each, then START and BACK.
* **Pause.** The **maintenance hatch**: the console's faceplate swings open, the view dims and a PAUSED
  lens lights. RESUME is the big green push button, with SAVE, LOAD, SETTINGS (and CONTROLS, where the
  spec has it) under it. CALL A TOW is there only while a tow can be called, as today, on an amber push
  button under an engraved BEACON. QUIT TO MENU and QUIT GAME stand apart behind a painted red line.
  ESC RESUMES is engraved on the hatch, and the world (`SEED 4417` or `DESIGNED START`) is on Dymo
  tape.
* **Load and save: the tape library.** Each save is a **cassette** in a rack, its name and details (the
  day, the ship and the credits) typed on the spine's label and the date it was saved in a blue rubber
  stamp. The selected cassette slides half out; LOAD is a push button and DELETE is guarded. The quick
  save and the three autosaves have printed labels (QUICK, AUTO 1 to 3), so they read as the game's
  own. Saving shows a blank cassette with the next free name typed on its label (`Save 4`), and the
  rack under it to write over one instead. An OVERWRITE lens lights while the name is a save already;
  this is new, since the game overwrites without saying so today.
* **Settings: the maintenance panel.** Bays for SOUND, DISPLAY, INTERFACE and CONTROLS, with the
  section 9.6 controls. The key bindings are a **patch panel**: a row for each action, its name on Dymo
  tape and its key on a keycap. Pressing a keycap lifts it out of its socket and lights the PRESS A KEY
  lens (`Esc` cancels). A key bound twice lights a TWICE lens on both rows; this is new, since the game
  doesn't check for it today. RESET KEYS sits in the CONTROLS bay, and APPLY (lit while there are
  changes) and BACK stand along the foot.
* **Ship lost and career over** are the telex (9.7), and **mod scripts** is the tag (9.7).

### 9.12 On the glass

Marks in the view are the reflector-sight projection of section 2, and these join them: a burn cue is
a double chevron, a fire arc is a thin amber arc with ticks at its ends, and a hit lane is dashed. The
picture-in-picture views are **optics ports**: a round lens bolted to the canopy (an aluminium ring and
four screws), monochrome green video with scanlines, the craft's name on Dymo tape under it, and its
"+3 others" on a tab that opens a small ledger tube. An optics port is the one piece of hardware
allowed over space, because it is a window, not a panel.

### 9.13 The developer layer

Developer tools (the console, the debug overlays) are **service equipment**, so no player mistakes them
for the ship's: bare aluminium, a red SERVICE ONLY Dymo strip, white IBM Plex Mono on black, no
phosphor glow and no motion. They draw in their own band at the bottom left, never over the deck's
instruments.

---

## 10. Controls and motion

### 10.1 The control catalogue

| Control | Use for | States | Key binding display |
|---|---|---|---|
| **Toggle switch** (bakelite bat) | Two-state settings: lights, autopilot, transponder | Up / down; unpowered (dark legend) | Keycap legend beside it |
| **Guarded toggle** (red flip cover) | Irreversible or dangerous: master arm, undock, jettison, self-destruct | Guard closed / guard open / switch on | First press opens the guard, second throws it |
| **Rocker** | Mode with two directions: warp − / +, zoom | Center / up / down | Two keycaps |
| **Push-light** (lit square button) | Actions that have a state: lock target, dock request, ack master caution | Unlit / lit / flashing | Keycap engraved on the cap |
| **Momentary push button** (round, travel) | One-shot actions: fire, decoy | Up / pressed | Keycap legend |
| **Rotary selector** (detents) | Choices of 3–12: page, radar range, view mode | Each detent | Two keys step it |
| **Thumbwheel** | Numeric entry: quantities to buy, Δv, a heading | Each digit | Arrow keys on the focused wheel |
| **Slide pot** | Continuous: throttle limit, volumes, brightness | Position | Shown only in maintenance panel |
| **Interlocked piano keys** | Tabs; one of two to eight shown side by side | One down, the rest up; a blanking plate | A keycap under each key |
| **Flap selector** | One of a set the game builds: window sizes, HUDs | Each choice, with a riffle as it changes | ◀ and ▶ keycaps |
| **Label holder** | Words: a seed, a save's name | Empty (placeholder pencilled), typing (block cursor), set | Focus moves to it; then typing |

Each control has a **keycap legend**: a tiny keycap shape (8 units, `rubber` with `ink-light` letter)
next to it, showing its binding. The keyboard is the primary input; the console tells the player which
key does what without a help line at the bottom of the screen. (The help bar that exists today is
removed.)

### 10.2 Interaction states

* **Hover** is a slight brightening and a 0.5-unit *lift* of the control's top highlight (the hand
  reaching for it), never an outline.
* **Pressed** moves the control 1 unit down/in and shifts its shadow.
* **Engaged/latched** is shown by the control's physical position and, if it has one, its lamp.
* **Disabled** = **unpowered**: legend lamps dark, the control physically still there. If the reason is
  not power (e.g., "need dock clearance"), an amber `INTLK` (interlock) lamp beside it lights on hover
  and the CRT prints why.
* **Focus** (keyboard navigation through `FocusNav`) is a small amber **pointer tab** (a triangle on the
  faceplate) beside the focused control, the way a technician's pen points at it.

### 10.3 Motion

Motion is physics, not easing. Values:

| Thing | Motion |
|---|---|
| Needles | Second-order spring: ω = 14 rad/s, ζ = 0.55 (a small overshoot, settles in ~0.35 s). Needles stick slightly when the console's circuit is damaged |
| Drum counters | Each drum rolls to its digit; lower drums spin, higher drums carry at the end like an odometer. Max spin 20 digits/s, then the low drums blur |
| Toggles and rockers | Snap in 50 ms with 1 frame of overshoot |
| Rotary selectors | Step 70 ms per detent with a 1-unit bounce |
| Lamps | Filament: on in 40 ms, off in 120 ms (warm fade). Flashing at 2 Hz, 50% duty |
| CRT power-on | 250 ms: a dot, a horizontal line, the raster opens vertically, brightness settles |
| CRT page change | 120 ms: the old page drops to the ghost colour, a vertical roll of 1 frame, the new page draws in top to bottom at 60 rows/s |
| Big console | 450 ms mechanical rise with a 2-unit bump at the top; the deck drops in the same motion |
| Side cabinet | 300 ms slide along the rail, with a 1-unit settle |
| Faceplates (a console's panel, the pause hatch) | Swing open on the hinge in 350 ms |
| Piano keys | The pressed key drops in 60 ms and the one it releases springs up in 90 ms |
| Tag | Drops on its wire in 250 ms and swings, damped, for 1 s |
| Cassette | Slides half out of the rack in 150 ms |
| Printout | Paper feeds at 30 rows/s with a line-feed jitter |

The **player's input is acknowledged in the first frame** (the switch moves, the click plays) even
when the *displayed value* follows with inertia. Mechanical lag is allowed on readouts, never on
controls.

### 10.4 CRT rendering

* **Scanlines** at the tube's native line count (240 lines for the deck CRTs, 480 for the big console's tubes),
  25% darkening between lines.
* **Bloom**: a 2-pass blur of the phosphor layer at 35% strength.
* **Persistence**: the phosphor layer renders into a frame buffer that decays toward `crt-black` with
  a half-life of 120 ms (amber) or 300 ms (green). This makes the radar sweep, orbits and missile
  tracks glow and fade without extra code in those widgets.
* **Curvature**: barrel 0.02, vignette 15% at the corners.
* **Flicker and noise**: off by default; only when the circuit is damaged (section 11).
* All CRT effects can be turned down or off in settings (section 12).

### 10.5 Reduced motion

A setting replaces needle springs with critically damped motion, rolls drums instantly, removes CRT
roll and wobble, hangs tags still, and keeps everything else. State is still shown by position and lamps.

---

## 11. Diegetic state: the console belongs to the ship

This is the largest departure from a skin. The deck is wired to the ship's systems, which exist
already in interior-grid:

* **Power.** Each bay is on a circuit (`PowerCircuit`). A circuit losing power **browns out** its bay:
  CRTs shrink and dim, needles sag toward their pins, lamps go dark. A bay with no power is dark
  except its legends. The annunciator and master caution/warning are on the **battery bus** and are the
  last to die, so the player is always told *why* the console is going dark.
* **Damage.** A fire or breach in the room the console is in causes CRT flicker and needle jitter in
  proportion, capped so readings stay readable. A destroyed bridge console swaps to a backup set of
  instruments on a smaller **emergency panel** (fewer bays, worse visuals) until repaired.
* **Crew.** Stations the crew operates (weapons, engineering) have a small `MANNED` lamp; an unmanned
  station's instruments still read, but its controls show `INTLK` until someone is at it, matching
  how the game already gates consoles.
* **Docking.** The station terminal is literally plugged in (section 2), so a station's services are
  visibly a cable away and visibly gone on undock.

Accessibility override: an option, **"Console always readable"**, stops brownouts and damage from
affecting any *reading* (effects only on decoration), for players who find it frustrating.

---

## 12. Accessibility and readability

* Minimum text sizes: legends 10 units, CRT text 15 units at UI scale 1; the existing `UiScale` scales
  the whole deck, hardware included.
* Every CRT effect (scanlines, bloom, persistence, curvature, noise) has a slider; a **"Clean tubes"**
  preset turns them all off and keeps the colours.
* Colour is never the only signal (sections 3.5, 7.2). A **high-contrast lamps** option adds a white
  outline and a letter (`W`, `C`, `A`) to lit annunciator tiles.
* A **phosphor colour** option allows amber, green, or white for each CRT class, for players who
  find one hard to read.
* Every control is reachable by keyboard; every hardware control also accepts a mouse click.
* Sounds (section 13) all have subtitles on the CRT's bottom line when the sound is meaningful
  (warnings, comms).

---

## 13. Sound

The console sounds like objects. All cues go through audio-spatial so they come from where the control
is on screen (pan by x position).

| Event | Sound |
|---|---|
| Toggle, rocker | Short heavy snap, with a pitch variance of ±4% |
| Guard flip | Spring-loaded clack, metallic |
| Rotary detent | Small click per detent; a heavier one at the end stop |
| Push-light | Plastic thunk; a relay click 30 ms later when it engages something |
| Drum counter roll | Soft ratchet tick, rate-limited to 25/s |
| CRT power-on | Thump and a low hum swelling; a 15 kHz whine is **off by default** (ear fatigue) |
| CRT page change | Faint static tick |
| Printer | Dot-matrix chatter per line, paper tear |
| Master caution | Two-tone chime, repeating every 4 s until acknowledged |
| Master warning | Klaxon pulse, repeating every 1.5 s until acknowledged |
| Missile incoming (< 5 s) | Rising tone stepping in pitch with time to impact |
| Brownout | Relays dropping in sequence, hum falling in pitch |

Ambient: the deck has a low electrical hum and occasional relay ticks. Volume follows a **Console**
slider separate from game effects.

---

## 14. Applying this to a functional spec

The three functional-design threads will define which widgets exist, where, and when. For each element
in a spec, the translation is:

1. **Classify the data** with section 8 and pick the instrument. If the spec shows a value as text,
   decide whether it is a rate (needle), a level (edgewise), an exact value (counter), a state (lamp) or
   a list (CRT page).
2. **Assign it a bay.** Persistent elements go into a deck bay; context elements become a CRT page or a
   page *position* that appears in context (the station ring on the selector, the boarding page while
   latched). "Pop-up" elements become either an annunciator tile (if it is a state) or a printout (if it
   is a record).
3. **Size it on the grid** and check the deck still fits at 16:9 at UI scale 1; if not, the spec's
   priority decides which bay moves to a page.
4. **Give every action a control** from section 10.1, dangerous ones guarded, and a key binding legend.
5. **Wire it to a circuit** and decide its brownout behaviour.
6. **Check context visibility** in the spec: things that a spec hides become *dark* (powered down) rather
   than absent if they are part of the deck, and absent if they are pages. A dark instrument is
   information too: it tells the player the system is off.

When a spec asks for something this language does poorly (free-floating tooltips, dense
spreadsheets, drag-and-drop), the answer is a page, a printout or the big console (section 9), and I will note
the adaptation in the skinned spec rather than bend the style.

---

## 15. Implementation notes (libGDX)

Written so this can be built on the existing `ui-hud` module without a rewrite of its providers.

* **Skin.** A `CassetteSkin` next to `HudSkin`, built from a texture atlas (`assets/ui/cassette.atlas`)
  of 9-patch faceplates, bezels, keycaps, switch frames and lens tiles at 1x, 2x and 3x, chosen by
  `UiScale`. `HudFrame` can draw the faceplates procedurally until the atlas exists.
* **Fonts.** Add Barlow Semi Condensed, Barlow Condensed, IBM Plex Mono, VT323, Special Elite and Big
  Shoulders Stencil Display (OFL, and Apache 2.0 for Special Elite) to `assets/fonts` with their
  licences; extend `HudFonts` with the roles in section 5.
* **New widgets** (Scene2D `Widget`s that take a provider, as the current ones do):
  * `NeedleGauge` (scale, zones, spring), `EdgewiseMeter`, `DrumCounter` (drums, units, `OFF` flag),
    `CompassCard`;
  * `Annunciator` + `MasterCautionWarning` (tile registry fed by `ShipAlerts`, `ThreatProvider`, ...);
  * `CrtScreen` (an off-screen `FrameBuffer` with the persistence decay, then a scanline/bloom/
    curvature `ShaderProgram` on composite), with `RasterPage` (character grid) and `VectorPage`
    (line drawing) contents;
  * `RotarySelector`, `ToggleSwitch`, `GuardedToggle`, `PushLight`, `Thumbwheel`, `KeycapLegend`;
  * `Deck` (the rack of bays, densities, the big console's raise) and `Printout`.
* **Existing widgets map onto these:** `ShipGauges` → edgewise meters and the power needle;
  `FlightReadoutSystem` text → counters and compass; `RadarScope` → a `CrtScreen` with a PPI
  `VectorPage`; `AlertList` and `WireframeMinimap`'s alert list → annunciator tiles; `TradeWindow`,
  `YardWindow`, `MissionWindow`, `BoardingWindow`, `CrewWindow` → ledger tubes (`RasterPage`s) in a
  big console or a side cabinet; `ConsoleWindow` → the console's faceplate; `ManeuverMap` and
  `OrbitMap` → the big console's plotting tube (a `VectorPage`); `ShipyardWindow` (the ship editor) →
  the drafting station; `ThreatOverlay` and `NavMarkers` → the gunsight projection layer;
  `PictureInPicture` → optics ports; `DevConsoleWindow` → service equipment.
* **The screens outside the HUD** (section 9): the shell's menus (`ShellMenus`) → the yard office; the
  crash screen → the telex; the notice log and the controls panel → paper; `ModTrustDialog` → the tag.
  These, the windows and the map are swapped through the **shared framework hook** that lets a HUD
  variant replace the game's windows, the map and the shell's menus. It comes from the VFD thread as
  one change on the variants framework, and the Cassette builds are made on it, not on a hook of their
  own. New widgets for them: `BigConsole`, `SideCabinet`, `PianoKeys`, `LedgerTube` (a `RasterPage`
  with the sort mark, the position meter and soft keys), `FlapSelector`, `LabelHolder`, `SlidePot`,
  `PatchPanel`, `Tag` and `Telex`.
* **Persistence cost:** one extra frame buffer per CRT (at most four on screen); deck CRTs render at
  their native line count (e.g. 320×240) and are upscaled, which is both cheaper and more authentic.
* **Power wiring:** a `ConsolePowerProvider` maps bay ids to `PowerCircuit` state; every instrument
  takes a `supply` (0–1) and degrades by it.

---

## 16. Mockups

Mockups are HTML/SVG renders in `cassette/` (source: `cassette/mockups.html`, rendered with the
pre-installed Playwright: `node cassette/render.js`). They show the style, not a final layout; the
layout will come from the functional specs.

The variations in `variations/*-cassette/` are drawn on the game's 1280 × 800 grid with the kit's grid
section and supersede these two as the reference for sizes.

* `mockup-flight.png` – the flight console at 16:9: a raider locked at 1.3 km, a caution lit for
  low propellant, the main CRT on the target page.
* `mockup-components.png` – palette, type, the control catalogue in its states, and the instruments.
* `shell/` (source: `cassette/shell/shell.html`, captions in its README) – the screens of section 9
  that every spec shares: S1 the main menu, S2 a new game, S3 pause, S4 load and S5 save (the tape
  library), S6 settings, S7 ship lost, S8 the mod-scripts tag, and K4, the off-deck kit (the big
  console's head, piano keys, the ledger tube's states, the form controls, the guard, the paper
  stocks, the optics port).

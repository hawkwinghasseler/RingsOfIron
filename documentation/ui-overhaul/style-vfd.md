# FILAMENT: the 90s VFD design language

Artist 2 of 3 for the UI overhaul. This guide defines how the game's interface looks, moves and sounds in the style of 90s vacuum fluorescent displays and early digital readouts, and how the limits of that hardware change layout and interaction. The functional specs decide what is shown, where and when; this guide decides what it is built from.

![The FILAMENT specimen sheet](vfd/specimen.jpg)

Contents

1. [The idea](#1-the-idea)
2. [What this style is not](#2-what-this-style-is-not)
3. [The hardware kit](#3-the-hardware-kit)
4. [Palette and drive levels](#4-palette-and-drive-levels)
5. [Lettering](#5-lettering)
6. [Icons](#6-icons)
7. [Building widgets](#7-building-widgets)
8. [The projected HUD](#8-the-projected-hud)
9. [Motion](#9-motion)
10. [Sound](#10-sound)
11. [Data visualisation](#11-data-visualisation)
12. [What the hardware changes in layout and interaction](#12-what-the-hardware-changes-in-layout-and-interaction)
13. [Semantic roles](#13-semantic-roles)
14. [Lexicon](#14-lexicon)
15. [Accessibility and settings](#15-accessibility-and-settings)
16. [Building it in the game](#16-building-it-in-the-game)
17. [Skinning a functional spec](#17-skinning-a-functional-spec)

---

## 1. The idea

The ship's electronics come out of the same catalogue as a 1994 car stereo, a fax machine and a cash register. Every display is a sealed glass tube made for one job. Its digits, words and icons were laid down in phosphor at the factory; the only thing the ship can do at run time is choose which of them glow, and how brightly.

Everything in this language follows from that:

- **A readout can only show what its glass was built to show.** Every fact has a fixed home and a fixed shape.
- **Unlit segments are still there.** Behind the smoked glass the player sees every segment that could light, faintly. They learn a panel by where things are, not by reading it.
- **Light is the only ink.** Nothing normal needs to glow. A healthy ship is a dark console; trouble is what lights up.
- **Glyphs are made of segments and dots.** Numbers sit in fixed-width fields, words are short, and nothing is set in a typeface.
- **Things switch; they never glide.** A state change is instant, with a brief afterglow.

Touchstones for mood, not to copy: 90s car stereos and digital dashboards, the VFD head-up display in some 1989 cars, hi-fi decks and VCR fronts, calculators, pole displays, fax and copier panels, cockpit annunciator panels of the period, digital watches with an electroluminescent backlight, pagers and the first PDAs.

**Pillars**, ranked. When two collide, the higher one wins.

1. **Built for one job.** Each module has a fixed purpose and a fixed vocabulary.
2. **Position is meaning.** Nothing moves or reflows. A fixed place lights or stays dark.
3. **Light is information.** Nominal is dark or dim; abnormal is bright.
4. **Discrete, never smooth.** Values step, bars step, motion steps.
5. **Terse.** Character cells are expensive. Write like a telex.

---

## 2. What this style is not

The three artist styles must stay far apart. This is where FILAMENT stands against the other two:

| | **FILAMENT (VFD)** | Nasa-punk | Cassette-futurism |
|---|---|---|---|
| Display technology | Sealed vacuum fluorescent glass: segments, dot matrix and fixed legends. LCD for personal devices | Glass cockpit: free raster and vector graphics | CRT monitors, mechanical counters, incandescent lamps |
| Emissive lettering | Generated from segments and dots; never a font | Clean proportional sans | Terminal monospace on a raster |
| Colour | One phosphor (zinc blue-green) plus amber and red filters in fixed places | A broad semantic palette | Monochrome CRT phosphor and painted plastic |
| Lines | None. Dot chains and segments | Thin anti-aliased vectors | Scanlines and raster |
| Motion | Instant switching, afterglow, character-step scrolling | Smooth tweens | Mechanical travel, rolling, warm-up |
| Material | Smoked glass in satin-black moulded bezels, rubber keys, LEDs | Flat glass, minimal bezel | Beige and grey plastics, toggles, knobs, screws |
| Sound | Piezo beeps and relay clicks | Soft synthetic tones | Clunks, tape and motor noise |

**Never in FILAMENT:** anti-aliased lines or curves on an emissive layer; a typeface for emissive text (the fixed legends are shapes, see 5.4); gradients or transparency inside an emissive element; scanlines, screen curvature, chromatic aberration or rolling bars (those are CRT); eased or tweened movement; arbitrary colour; drop shadows; translucent panels floating over the world.

---

## 3. The hardware kit

Every widget in a functional spec is built from these parts and nothing else. If a widget can't be built from them, the widget changes, not the kit.

### 3.1 Housings

| Part | What it is | Rules |
|---|---|---|
| **Module** | A window of smoked glass with its own segments, cells and legends | Corner radius 3 units; 1.5-unit black gasket; a faint glare across the top-left third; 2 to 4 filament wires visible across it. Contents keep a 6-unit margin from the glass edge |
| **Rack** | A satin-black bezel plate along a screen edge that holds modules, printed legends and keys | Opaque. Heights on an 8-unit grid. Never transparent, never floating |
| **Slab** | A free-standing module with a thin bezel of its own, for things that appear and go (overlay, side panels) | Powers up when it has something to show and powers down when it has nothing (section 9). Never semi-transparent |
| **Pod** | A round housing for a ring or scope | May stand up to 24 units proud of its rack's top edge |

### 3.2 Emissive parts

| Part | Shows | Sizes (cap height, units) | Use for |
|---|---|---|---|
| **Seven-segment digits** | 0 to 9, minus, a few letters (`E r o L P H t`) | S 12, M 18, L 26, XL 40 | Every number that changes: speeds, ranges, times, counts |
| **Fourteen-segment characters** | A to Z, 0 to 9, `- + / * < > =` | 12, 16, 22 | Short words that change: mode names, page titles, a contact's name up to 8 characters |
| **Dot-matrix cells** | 5x7 glyphs from the character ROM (5.3) | Pitch 1.5, 2, 3 (cap 10.5, 14, 21) | Free text: names, lists, callouts, table cells |
| **Graphic dot panel** | A full grid of dots | Pitch 2 to 3 | Plots: the orbit plot, the ship plan, the shield ring |
| **Annunciator legends** | Fixed words laid down in phosphor | Size 10 to 14 | A fixed vocabulary: units, frames, states, page names, alert tags |
| **Icons** | Fixed segmented pictograms | 12, 16, 24 | States and alerts (section 6) |
| **Bars** | A row of discrete segments | 10, 20 or 30 segments | Quantities with thresholds |
| **Rings** | Annular sectors | 16, 32 or 96 sectors | Bearings, coverage, rotation |
| **LEDs** | Round 3-unit lamps in the bezel: red, amber, green | | The state of a key: on, armed, pending |
| **Lamp keys** | Translucent key caps lit from behind | | The master warning and master caution lamps, which are also the acknowledge buttons |
| **LCD** | Reflective liquid crystal, dark on grey-green, with an electroluminescent backlight | | The captain's personal kit and the ship's backup strip (7.11) |

### 3.3 Grid

- Everything snaps to a 2-unit grid. Racks and modules snap to 8.
- A character cell is 6 x 9 dots: the 5x7 glyph, one column of space, two rows of leading. At pitch 2 a cell is 12 x 18 units; at pitch 1.5 it is 9 x 13.5.
- **Fields are counted in cells, not measured.** A spec's "name, up to 16 characters" is 16 cells, and the module is sized from the cell counts of its fields.

### 3.4 Resolution

Segment glyphs, bars, rings and icons are geometry and stay sharp at any scale. Dots need at least 2 pixels each:

- Dot pitch is snapped to whole pixels, at least 2.
- From 3 pixels a dot gets its gap (80% fill) and rounded corners; at 2 pixels it is a plain square and dot text reads as a crisp pixel font.
- So that pitch 1.5 never drops below 2 pixels, FILAMENT asks for the automatic UI scale to follow `window height / 800` (1.35 on a 1080-line window, where the 1280 x 800 layout still fits) rather than `window height / 1080`. A fixed scale below 1.35 still works; dot text then grows by whole cells and modules widen with it.

---

## 4. Palette and drive levels

### 4.1 Phosphors

| Token | Hex | Hot core | Role |
|---|---|---|---|
| `zinc` | `#46F6CC` | `#DEFFF6` | The bare phosphor, zinc oxide's blue-green. Everything by default |
| `amber` | `#FFAC34` | `#FFEECC` | A filter printed on the glass: caution regions |
| `red` | `#FF402E` | `#FFD8CC` | A filter printed on the glass: warning and hostile regions |

At full drive the centre of a segment whitens toward its hot core while the glow around it keeps the hue.

**Filters are places, not paint.** An amber or red filter covers a fixed region of a module's glass, so that region is always that colour, and nothing outside it can be. A warning cannot turn a zinc readout red; it lights a red element that was built for it. Every module that can raise a caution or a warning therefore has its amber or red element designed in at a fixed place: the low end of a bar, an annunciator cell, a tile's frame. This is the single biggest layout consequence of the style (section 12).

### 4.2 Drive levels

Brightness is the grid's duty cycle. Four levels, no others:

| Level | Name | Look | Use |
|---|---|---|---|
| L0 | **Ghost** | Unlit; 7.5% tint seen through the glass, no glow | Every segment, dot, legend and icon that is off |
| L1 | **Dim** | 40%, a little glow | Labels, secondary values, nominal values |
| L2 | **Normal** | 80%, full colour and glow | Primary values, active states |
| L3 | **Full** | 100%, white-hot core, wide bloom | The one thing that matters now: warnings, the focused row, the locked target |

Budgets: at most one L3 element per module outside an alarm. A nominal value is never above L1.

### 4.3 The dimmer

The brightness the player sees is `setting x bus x module`:

- **Setting**: the player's brightness, 25% to 100%.
- **Bus**: the ship's power. A brownout dims every tube, with a 1 to 3% jitter; the floor is 35% so the UI stays readable.
- **Module**: a console without power goes dark (all ghost, then not even that); a damaged console flickers and loses segments (9.5).

### 4.4 Inks that do not glow

| Token | Hex | Use |
|---|---|---|
| `glass` | `#040605` to `#08100E` | Module windows, top to bottom |
| `bezel` | `#141517`, edge `#26282B`, shadow `#08090A` | Racks and slab rims |
| `silk` | `#8D949A`, dim `#5B6167` | Printed legends on the bezel |
| `key` | `#26282C` | Rubber keys |
| `led` | red `#FF3C32`, amber `#FFAA28`, green `#5AFF78` | LEDs |
| `lcd` | `#A7B294` to `#8F9A7F`, ink `#1A2016`, backlight `#4FD6C4` | Personal devices, backup strip |
| `card` | `#E9E6DC`, ink `#1C1C1C` | Out-of-fiction help (7.12) |

---

## 5. Lettering

Four lettering systems, each with one job.

### 5.1 Seven segments: numbers

- Slant 8 degrees, aspect 0.54, segment thickness 13% of the height, a visible gap at every joint.
- **Fixed-width fields**, right aligned, leading blanks (never leading zeros, except in clock fields). The minus sign takes its own cell. The layout never moves when a value changes.
- **Autoranging**, like a multimeter: the field keeps its width and the unit legend beside it steps (`M` to `KM`, `M/S` to `KM/S`) with the decimal point. A 4-digit field reads ` 312 M`, `1.24 KM`, `12.4 KM`, ` 124 KM`.
- **No data** is `----` (segment g only) at L1. **Over range** is `OL`. **Estimated** values are L1 with an `EST` legend lit.
- **Refresh rate** is fixed per readout: 5 Hz for fast values (speed, range), 2 Hz for slow ones, 1 Hz for clocks. Never per frame: the last digit must be readable.

### 5.2 Fourteen segments: short words

- Same slant. At most 8 cells per field.
- For words that change: page titles (`SENSORS`), modes (`APPROACH`), the locked contact's name (`RAIDER 6`). A longer name moves to dot matrix.

### 5.3 Dot matrix: free text

- **5x7 glyphs from the character ROM** in `vfd/src/vfd.js`: `A` to `Z`, digits, punctuation and a page of custom glyphs: `↑ ↓ ← → ° Δ ▲ ▼ ▶ ◀ █ · × ± ³ … ¢ ♦`.
- **Upper case only.** Writers write in sentence case; the display folds it. Long text reads like a telex, which is deliberate.
- **Pitch**: 1.5 for table cells and tags (the minimum), 2 for body and callouts, 3 for headlines.
- Monospaced cells; no kerning; word space is one cell.
- **Line length is the module's cell count.** Wrap at spaces only, never hyphenate; a word longer than its field is cut with `…`. Only the focused row of a list may scroll its overflow (9.3).

### 5.4 Legends: printed words

- Only for a **fixed vocabulary** (section 14): units, frames, states, page and station names, alert tags.
- Face: Inter Bold (already in `assets/fonts`), condensed horizontally to 80%, tracking +6%, all capitals, size 10 to 14.
- **If it can change state, it is phosphor; if it never changes, it is silk.** A phosphor legend is lit or ghosted (`ASSIST`, `DOCKED`, `PIN`); a silk legend is printed on the bezel in grey and never glows (a module's title, a key's name).

---

## 6. Icons

- **Segmented.** Each icon is built from separate strokes and fills with visible breaks where the glass separates them, on a 16-unit grid, strokes 1.5 units, no detail under 2 units.
- Every icon has its ghost (always visible on its home module) and its lit state, in one colour. An icon never changes shape.
- The first set, drawn in the specimen sheet: missile, lock, dock, propellant, power, oxygen, fire, breach, crew, cargo, warp, autopilot, save (a floppy disk), comms, caution, shield, heat, ship, station, orbit, clock, tow, message, point defence, target, cassette.
- Icons stand beside a word, not instead of one, wherever space allows. On the projected HUD they are dot sprites instead (section 8).

---

## 7. Building widgets

### 7.1 Readouts

A label (phosphor legend at L1, or silk), a seven-segment value (L2) and a unit legend chosen from a fixed set beside it. The value's field width is part of the design: `VEL` is 6 digits, `RNG` 4 with autoranging, a clock `DDD HH:MM:SS`.

### 7.2 Bars

- 10, 20 or 30 segments, 1.2 units apart. Thresholds are printed as silk ticks under the bar.
- **Fixed filter zones.** A depleting resource (propellant, air, shield mass): the lowest 20% of segments are red, the next 30% amber, the rest zinc. A rising hazard (heat, strain, closing speed): the top 20% red, the 20% before amber.
- **Markers ride on the bar, not over it.** A limit or a target (the Δv needed to get home) is one segment lit at L2 beyond the fill, plus a silk notch. A range on the bar (the Δv a plan will spend) lights a thin second rail of segments under the bar: a two-rail bar.
- Ballistics: a bar rises at once and falls at 30 segments a second; a peak segment holds for 1.5 s where a peak matters.

### 7.3 Rings

16, 32 or 96 annular sectors. A bearing lights a sector; a coverage ring lights sectors in up to three concentric rails (thin, medium, thick) to show strength in steps instead of a smooth thickness.

### 7.4 Scopes

- **Sector scope** (the default radar): 16 bearings by 4 range rings of fixed cells, plus a red-filtered rim of 16 threat segments. A contact lights the cell it is in; a hostile also lights the rim at its bearing. Precise positions are the world view's job; the scope answers "what is around me, how far, and from where is the threat". The range ring legends are lit to show the scale.
- **Plotting scope**: a round graphic dot panel, for pages that need positions (a contact list beside it carries the numbers).

### 7.5 Plots on graphic panels

- Lines are dot chains on the panel's grid. No anti-aliasing, no stroke widths.
- Fills are ordered dither: 25%, 50%, 75%. A planet is a 50% disc with a solid limb.
- Labels are 5x7 text on the same grid. The selected item is shown by inverse text and a 1 Hz blink.

### 7.6 Lists and tables

- Columns are counted in cells. Headers are legends (L1 phosphor or silk); rows are dot matrix at L2; numbers right aligned. Seven segments are for single values, not table cells.
- **Focus is inverse video**: the focused row's cells invert, and a `▶` sits in column 0. There is no hover state: the pointer moves the cursor.
- **Paging, not scrolling.** A list shows a fixed number of rows; `PG 1/3` and lit `▲ ▼` legends say there is more. The cursor steps by row; Page Up and Page Down step by page.

### 7.7 Soft keys

- A module may have a row of up to six rubber keys under it. The module's bottom row of cells draws each key's label (up to 6 cells) above it, and each key cap prints its keyboard shortcut in silk.
- The active page or mode's label is inverse. A key that toggles something has an LED above it: green when on. A key waiting for a second press (abandon ship, call a tow) has its LED blinking amber.
- **Keys are keys.** Wherever the UI names a key, it draws a small key cap with the key's legend, never `[E]` in brackets.

### 7.8 Annunciator cells

Two forms:

- **Fixed panel**: one cell per condition, ghosted when inactive. For small, fixed sets: a station's own conditions on its module.
- **Tile row**: for a list ordered by severity, where conditions come and go. A tile is a cell holding a legend tag and a dot-matrix value. A **caution** tile sits behind an amber filter and is drawn as an outlined cell; a **warning** tile sits behind a red filter and is drawn **inverse** (a solid lit block with dark letters). Severity is told apart by colour, form and blink rate together.

### 7.9 Master lamps

Two lamp keys, `MASTER WARN` (red) and `MASTER CAUT` (amber). Lit and flashing while an unacknowledged alert of that severity exists: warning at 2 Hz, caution at 1 Hz. Pressing one (a click, or the acknowledge key) is the acknowledgement: the flashing and the alarm stop, the lamp stays lit while the condition holds.

### 7.10 Prompts and responses

A prompt is a small slab over its console: the console's name in legend type, then each verb with its key cap. A response (the answer to a key pressed where it can't work) replaces the prompt's text for 4 s, with the refused-input buzz.

### 7.11 LCD: the captain's kit and the backup

The ship glows; the captain's own kit reflects.

- **Personal devices** (the slate, the suit readout) are reflective LCD: dark segments and dots on grey-green, each with a faint shadow cast on the reflector behind, no glow. The electroluminescent backlight (blue-green) comes on for 3 s on an alert or a key press.
- **The backup strip**: every rack has a narrow LCD strip that shows nothing while the ship has power. When power is lost the tubes go dark and the strip shows what keeps the crew alive: hull, air, time, and the master alarm.

### 7.12 Out-of-fiction help

Tutorial guide cards and the controls reference (F1) are **printed quick-reference cards**: off-white laminated card with black ink and key caps, the only light surface in the UI. They are plainly not part of the ship. Menus (main, pause, settings) are the ship's computer in setup mode and use the full kit.

---

## 8. The projected HUD

The symbology over the outside view is written by a dot-matrix projector, the descendant of those early car head-up displays. It follows its own rules:

- **Dots only.** Every marker is a dot sprite on a 2-unit grid (dots 1.6 units). Lines are dot chains with dots every 4 to 6 units. There are no solid strokes and no curves.
- **Two channels.** Zinc for everything; red for threats (hostiles, incoming missiles, the impact cross). Amber appears only for the collision cue and an unsafe docking corridor.
- No ghosting and no mesh (it is projected), the same bloom.
- **Shape before colour**, so every role reads without colour:

| Role | Sprite |
|---|---|
| Prograde | Circle of dots with three ticks |
| Retrograde | Circle of dots with a cross |
| Friendly | Square corner brackets |
| Neutral | A small open diamond; icon only unless near or hovered |
| Hostile | Red triangular corners, with the class in 5x7 |
| Station | An open square with a centre dot |
| Locked target | Full corner brackets at L3 with four ticks; blinks three times on lock |
| Navigation target | Diamond brackets (corners turned 45 degrees) |
| Contract waypoint | A hollow diamond with a dotted tail |
| Burn arrow | A dot chain with an arrowhead sprite and a 5x7 countdown |
| Threat track | A red dot chain, dots every 6 units, ending in an impact cross |
| Point-defence kill | A ring of eight dots that steps out three times (16 ms each) and fades |
| Lead reticle | Four dots in a diamond with a centre dot |
| Fire arc | Dot chains along the arc's edges, sparse dots along its rim |
| Docking corridor | Two dotted rails: zinc while safe, amber when closing too fast |
| Collision cue | An amber cross of dots at the closest approach, with a dot chain to it |
| Edge marker | A dot chevron at the edge of the view with a 5x7 label; missiles carry seconds to impact |
| Picture-in-picture ring | A circle of dots every 4 units with a 5x7 caption |

- Labels are 5x7 at pitch 2, at most 12 cells a line: the name at L2, the range under it at L1.
- Sprites snap to the 2-unit grid as they move, so they step rather than slide.

---

## 9. Motion

### 9.1 Switching

- **On**: rises within one frame.
- **Off**: afterglow, decaying over 120 ms. A digit that changes leaves a faint trace of its old segments.
- **Power-up** (a module or slab appearing, a console coming to life): 240 ms self-test. Every segment lights at L2 for 120 ms, the module goes blank for 60 ms, then its content draws top to bottom one text line every 16 ms, as if over a slow bus.
- **Power-down**: content blanks at once and decays; a slab's glass then goes.
- **Changing page or mode**: the module blanks and redraws. No slides, no fades, no tweens.

### 9.2 Blinking

- One global blink clock; every blinking thing on screen is in phase.
- Caution 1 Hz (500 ms on, 500 off). Warning 2 Hz (250 on, 250 off). Nothing blinks faster.
- Only the master lamps, unacknowledged tiles, a newly locked target (three blinks) and a text-entry cursor blink.

### 9.3 Scrolling

- Text that overflows its field marquees one cell at a time, 8 cells a second, with a 1 s hold at each end. Only in the focused row or a ticker line.
- Lists move by whole rows and pages.

### 9.4 Attention without motion

- **Level kick**: when a value crosses a threshold it jumps from its level to L3 for 2 s and settles back. This replaces flashing for anything that is not a caution or a warning.
- **Busy**: a "racetrack" around one digit's outer segments at 12 Hz, or three dots chasing.

### 9.5 Power and damage

- **Brownout**: every tube dims with the bus, with a slight jitter.
- **Power lost**: tubes go dark over 300 ms; the LCD backup strip takes over.
- **Damaged console**: its modules flicker (a 1 to 3% drop at 10 to 30 Hz) and lose a fixed scatter of segments, the same ones every time for that module, so the player can learn to read around them.
- **Destroyed console**: its modules are dark, the glass cracked (a non-emissive overlay).

---

## 10. Sound

A piezo beeper and a relay. Square waves, short, never a melody. Every alarm is tied to the master lamps and stops on acknowledge.

| Event | Sound | Today's hook |
|---|---|---|
| Key or click | 2.4 kHz tick, 6 ms | new `UI_KEY` |
| Soft key confirmed | Two tones, 1.8 then 2.4 kHz, 40 ms each | new `UI_CONFIRM` |
| Refused input | 440 Hz buzz, 120 ms | new `UI_REFUSE` |
| Advisory | One 1.2 kHz bip, 50 ms | `UI_NOTICE` |
| Caution | Three 1 kHz beeps (80 ms on, 80 off), repeated once after 4 s if not acknowledged | new `UI_CAUTION` |
| Warning | 2.8 kHz pips at 4 a second, in phase with the blink clock, until acknowledged | `UI_WARNING` |
| Module power-up | A 200 ms filament whine and a relay click | new `UI_POWERUP` |
| View change, dock latch, mode switch | Relay clack | new `UI_RELAY` |
| Autosave | Floppy-drive seek chatter, 400 ms | new `UI_SAVE` |
| Brownout | The tubes' whine drops in pitch | new `UI_BROWNOUT` (looped) |

---

## 11. Data visualisation

| Data | Built as |
|---|---|
| A value that changes | Seven segments in a fixed field, unit legend, autoranging |
| A quantity with thresholds | A bar with fixed filter zones; limits and targets ride on it (7.2) |
| A quantity against a budget (Δv against the trip home, demand against generation) | Two rails on one scale, sharing a limit segment |
| A bearing or coverage | A ring of sectors |
| Positions | A graphic dot panel, or a sector scope when the world view already holds the precision |
| A trend over time | Spectrum columns: 16 columns of 8 segments, the newest on the right, stepping left once a second |
| Several similar things (stations, mounts, launchers) | A row of identical cells: small multiples, the same layout in each |
| A state | A legend lit or ghosted; never the words ON and OFF |

---

## 12. What the hardware changes in layout and interaction

These are the ways FILAMENT changes more than the look. They are what the functional designs adopt when skinned in this style; where one clashes with a spec, it is raised with that spec's designer as a critique, not quietly changed.

1. **Racks, not floating panels.** Persistent instruments live in opaque racks along the screen edges. The outside view is the window between them, and the camera centres the ship in that window, not on the screen.
2. **Fixed homes.** A module never moves or resizes. In a rack, a field with nothing to show goes dark (ghosted) instead of collapsing, so every other field stays where the player learned it. In the overlay, a slab with nothing to show powers down and is gone. "Hidden when empty" means dark in a rack and gone in the overlay.
3. **Character budgets.** Every text field has a cell count, and every module is sized from those counts. Specs give maximum lengths; this guide's lexicon (section 14) shortens the rest.
4. **Soft keys instead of buttons.** Every action in a module is a soft key with a key cap. Mouse clicks land on soft keys and rows; nothing changes under a hovering pointer.
5. **States are lit words, not text.** `ASSIST ON/OFF` becomes the legend `ASSIST`, lit or ghosted. Booleans never take text.
6. **Modes instead of windows.** A module changes what it shows (its lit mode legend says which) rather than opening a window over another module. Only slabs overlap the world, and they never overlap each other.
7. **Paging instead of scrolling.**
8. **Colour by place.** Amber and red exist only where the glass was filtered, so every alerting element has a reserved home in its module.
9. **Personal kit is LCD.** The slate and the suit readout reflect light instead of emitting it, which tells the player at a glance whether they are looking at the ship or at their own kit.
10. **The UI runs on the ship's power.** A brownout dims it, a dead console is dark, and the backup strip keeps the essentials when everything else is gone.

---

## 13. Semantic roles

How the semantic roles the functional specs name are told apart. Each is distinct without colour.

| Role | Channel | Level | Form | Motion |
|---|---|---|---|---|
| Nominal | zinc | L1, or dark | plain | none |
| Advisory | zinc | L2 | plain | level kick on arrival |
| Caution | amber filter | L2 | outlined cell, `▲` | 1 Hz until acknowledged |
| Warning | red filter | L3 | inverse cell, `◆`-like `♦` | 2 Hz until acknowledged |
| Own ship | zinc | L2 | hollow sprites | none |
| Friendly | zinc | L2 | square brackets | none |
| Neutral | zinc | L1 | small diamond, icon only | none |
| Hostile | red | L2 | triangular corners | none |
| Navigation | zinc | L2 | diamond brackets, dotted lines | none |
| Locked target | zinc | L3 | full brackets with ticks | three blinks on lock |
| Selected or focused | as its element | inverse | inverse cells and `▶` | text cursor at 1 Hz |
| Unavailable | none | ghost only | `----`, `NO PWR` on the backup strip | none |
| Player / crewed / automated | zinc | L3 / L2 / L1 | `YOU` inverse / initials / `AUTO` legend | none |
| Out of fiction | printed card | n/a | off-white card, black ink | none |

---

## 14. Lexicon

The legend vocabulary and the abbreviations dot-matrix fields use when a word doesn't fit. Text in callouts and tables is written in full where its field allows.

| Abbreviation | Meaning | Abbreviation | Meaning |
|---|---|---|---|
| `VEL` | velocity | `ROT` | rotation rate |
| `ALT` | altitude | `AP`, `PE` | apoapsis, periapsis |
| `PER` | period | `SOI` | sphere of influence |
| `RNG` | range | `CLS` | closing speed |
| `CPA` | closest approach | `TTI` | time to impact |
| `TOF` | time of flight | `BRG` | bearing |
| `ΔV` | delta-v | `HOME` | Δv the trip home needs |
| `PROP` | propellant | `HULL` | hull integrity |
| `PWR` | power | `GEN`, `DMD` | generation, demand |
| `STOR` | power store | `O2` | oxygen |
| `PRES` | pressure | `ATMOS` | atmosphere |
| `SHLD` | shields | `PD` | point defence |
| `MSL` | missile | `DCY` | decoy |
| `TGT` | target | `NAV` | navigation |
| `ORB` | orbit frame | `PORT` | docking port frame |
| `BRN` | burn | `WARP` | time warp |
| `AUTO` | autopilot or automated | `ASSIST` | rotation assist |
| `DCK` | docked | `CLR` | clearance |
| `STRN` | collar strain | `CR` | credits |
| `QTY` | quantity | `STK` | stock |
| `RDY` | ready | `RLD` | reloading |
| `HLD` | held (guns) | `FREE` | firing at will |
| `ENG` | engineering | `TAC` | tactical |
| `CMD` | command | `MED` | medical |
| `ACK` | acknowledge | `PIN` | pinned |
| `PG` | page | `EST` | estimated |
| `NO PWR` | no power | `OL` | over range |

---

## 15. Accessibility and settings

- **Never colour alone.** Severity is also told by the form of the cell (outlined or inverse), the blink rate and the legend word; contacts by their sprite shape.
- **Flashing** is at most 2 Hz and limited to the elements in 9.2. *Reduce flashing* turns every blink into a steady L3 with inverse.
- **Legibility mode** turns off the mesh, filaments, ghosts and bloom, fills dots to squares and raises L1 to 60%.
- **Glow** strength (bloom) and **afterglow** each have a slider.
- **Brightness** (the dimmer's setting) from 25% to 100%.
- **Minimum sizes**: dot text pitch 1.5 (cap 10.5 units, the same cap height as a 14-unit typeface), body pitch 2, legends size 10 and up, seven segments 12 units and up.

---

## 16. Building it in the game

The mockups are drawn by `vfd/src/vfd.js`, which doubles as the reference for the glyph tables and drawing rules. In the game:

| Piece | Role |
|---|---|
| `VfdGlyphs` | The seven-segment and fourteen-segment masks and the 5x7 ROM, ported from `vfd.js` |
| `VfdDraw` | Segments, dots, bars, rings and icons as batched quads from the skin's white region, the way `HudFrame` and `HudDraw` draw now |
| `VfdLayer` | Two frame buffers at UI resolution: base (glass, ghosts, bezels) and emissive (lit parts). Bloom is the emissive buffer at half resolution through a 9-tap Gaussian twice, added over the base |
| `VfdClock` | The global blink phase, and afterglow: each part keeps its last mask and the time each segment went off |
| `VfdDimmer` | Setting x bus x module, fed from `ShipStatusProvider` |
| `VfdModule`, `Rack`, `Slab` | Scene2D widgets laid out in cells |

Only the legends use a font (Inter Bold, drawn with a horizontal scale); everything else is geometry, so every segment can be ghosted, dimmed, blinked or burned out on its own.

---

## 17. Skinning a functional spec

How each functional design becomes a FILAMENT variation:

1. List every widget and its data; pick its parts from section 3.
2. Give every field a cell count; shorten with section 14.
3. Give every widget a rack, module or slab and a fixed home.
4. Map every semantic role with section 13.
5. Reserve the amber and red filtered places for every caution and warning element.
6. Define each module's power-up, power-down and alert behaviour, and its sounds.
7. Mock up the spec's walkthroughs, critique them, send functional critiques to the spec's designer, and repeat.

The variations live in [`variations/`](variations/), with the round log in [`variations/vfd-rounds.md`](variations/vfd-rounds.md).

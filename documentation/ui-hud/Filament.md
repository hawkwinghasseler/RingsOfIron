# FILAMENT: the VFD HUDs' drawing kit

The three FILAMENT variants (`vfd.stations`, `vfd.context`, `vfd.task`) share one kit in
`com.ringsofiron.uihud.variants.vfd`. It draws the 90s vacuum-fluorescent look from geometry, the
way the mockups' renderer does (`documentation/ui-overhaul/vfd/src/vfd.js`, style guide
`documentation/ui-overhaul/style-vfd.md`): every segment and dot can be unlit (its ghost shows), dim,
normal or full on its own, so the game matches the mockups.

## Drawing

A HUD is a `Filament.Painter`: one `paint(Filament f)` that draws everything with the kit's calls.
`Filament.render` runs it once per layer, and each call draws only what belongs to the layer being
drawn:

| Layer | What it draws |
|---|---|
| Probe | Nothing: counts what the frame holds, so empty layers are skipped |
| Projected | The projected HUD over the world (`f.o().proj()`), into the glow buffer |
| Base | Bezels, glass, keys, printed silk, the LCD, and every unlit element's ghost |
| Glass | Lit phosphor behind the glass, into the glow buffer |
| Cut | Letters cut out of lit blocks (inverse fields), darkened again after the glow |
| Modules | Each window's filaments and glare |
| Late | Lamp-key legends, printed over their own glow |

So the painter must draw the same things in every layer of a frame. Read the game and lay out in
`update`, into fields, and paint only from those fields.

Options come from `f.o()`, a shared set reset on each call to it:
`f.seg7(x, y, 18, "12", f.o().cells(3).amber())`. Levels are `Drive`'s (ghost, dim, normal, full) and
colours `Phosphor`'s (zinc, amber, red). `f.dark = true` around a module draws it unpowered: nothing
lights and every element shows its ghost, for a blackout.

## Coordinates

UI units from the top left of the stage, y down, as in the mockups; text sits on its baseline. The
mockups are laid out on a 1280 x 800 grid. On a bigger stage, `f.origin(x, y)` moves the origin of
everything drawn after it, so a group can be anchored where the mockup puts it: a top column at
`origin((width - 1280) / 2, 0)`, a bottom band at `origin(that, height - 800)`. Each layer starts at the
stage's top left again. The world's own positions come from `HudVariantHost.viewports().worldToUi`,
which is y up: draw at `height - y`.

## On the stage

`FilamentActor` is the actor that draws a painter on the HUD stage. Size it to the stage
(`host.width()`, `host.height()`) and give it the UI scale (`setUnitsPx(host.uiScale())`). It takes
clicks only inside the boxes the layout adds each frame (`clearBoxes`, then `box(id, x, y, w, h)` in
the same y-down units), so the world under it still gets every other click; `Clicks.clicked(id)` is
told which box was hit. Its `Filament` holds GL resources: dispose the actor on `detach`.

## Words and numbers

`Fmt` cuts values to fixed fields rather than growing a field to its value: `fit(7.67, 3, 2)` is
"7.67" ('.' joins the cell before), a value too big for its cells shows nines, NaN shows blank.
Ranges are metres under a kilometre and kilometres above; times are m:ss, then h:mm:ss, then days.
`name("Pirate raider 6", 8)` is "RAIDER 6": a name drops its leading words before it cuts one, so the
words that tell two ships apart stay. `place("FWD PORT PD")` is "FWD PORT": a gun's mount by where it
sits, less the kind the weapon's name already says ("GUN (FWD PORT)", "AUTOCANNON MK1 (FWD STBD)"); a
mount the game could not place gives "", and keeps its whole name.

`Kit` holds the helpers the three variants share: the sector scope, brackets and nameplates for the
projected HUD, velocity markers, the lead reticle, missile glyphs and the impact cross, edge
markers (`spread` moves several on one edge apart, keeping their order), deck tags, the state rows
and the pips.

`DeckPlan` draws the player's ship's deck plan on a graphic dot panel: walls lit round a room with
something wrong in it, fire as a dense checker, smoke as a dither, a breach as an X, a room without
air in red, and the last hull hit flashing. Context's H4 face and Task's schematic both use it; call
`update` each frame and `paint` into a box, and it fits whole tiles to its dot pitch.

## The menus and the map

The three HUDs give the game's menus and map one look between them (style guide, section 18.5),
through the shared screens hook (`Variants.md`): `VfdScreens` holds what each hands on, and frees it
on `detach`.

**The menus** (`VfdShellLook`) are the ship's computer in setup mode. Before a game starts the racks
are dark, the annunciators and the clock ghosts, and the top rack shows SETUP in fourteen segments,
over the stars and the planet's limb; a menu too tall for the window between the racks comes up in
front of the windows it covers. Over a paused game the world goes under a scrim. Each menu keeps the
classic one's rows, buttons, keys and order: it is built from `SetupSkin`, whose styles carry the
classic names and the ROM's metrics (`RomFont`, the ROM on a texture) but draw nothing, and
`SetupPaint` reads the widgets each frame and paints them through the kit:

| Widget | Shown as |
|---|---|
| Button | A field of cells at pitch 2, a cursor column first; focus lights ▶ and inverts the field, disabled is dim |
| One of a group (a sponsor) | Its word in a lit frame when picked |
| Choice | ◀ value ▶, the value inverse with the focus |
| Check box | A lit block, or a ghost frame |
| Text field | Its cells with their ghosts, a rule under them, a blinking block cursor |
| Slider | A bar of 20 segments |
| List | Its rows in view, and its place as a column of segments; it never slides sideways, so a row too long is cut short |
| Heading, title | Fourteen segments; a section's name a legend in the glass |

The game's focus (`FocusNav`) tints the focused control; the painter reads that tint
(`SetupPaint.FOCUS`, kept equal to it by a test) and shows the inverse field instead. The legend of
keys sits in the bottom rack, split round a menu that comes down over it, or on a slab under the menu
over the game. The mod scripts dialog has its title in a red strip. If the painter ever fails, the
fonts' own glyphs show so the menu can still be read and left, and the next backdrop hands the menus
back to the classic look.

**The map** (`VfdMapPainter`) is a graphic dot panel at pitch 3 (`Plot`): every piece lights the
panel's own dots and the brightest call on a dot wins. The ship's orbit is every second dot at L2,
the plan solid at L3, a proposal every third at L3, the target's orbit and spheres of influence
every second at L1, a moon's orbit every third at L1. A body is a solid limb over a dither; one
drawn past 60 units is the ground under the orbits, a quarter of its dots at L1. Labels are ROM text
at pitch 1.5 on a ground that keeps the dots under them dark, and one under the map's heading moves
down clear of it. The map's side panel stays its HUD's own. Everything is drawn in one pass of the
kit when the map gives its last piece of the frame.

`tools/playtest/shell-vfd.sh <spec>` drives the menus, a game paused, the map and the mod scripts
dialog in a real window and screenshots each.

## The terminals

The game's windows under a FILAMENT HUD are terminals (style guide, section 18.2): panes of glass on
the character grid, pages rather than scrolling, and soft keys. Each HUD lays its windows out as its
spec has them, from parts in `vfd.screens` that the three share:

| Part | What it is |
|---|---|
| `Glass` | The grid, 9 units a cell at pitch 1.5 (`cells(w)` fit in a width): panes with their names, section heads with a ghost rule, text, rows with the focus inverse and its cursor in column 0, severity pips, key caps with their words, the pager, seven-segment figures with their units, and ◀ choices ▶ |
| `Pages` | A list's focus and the page that shows it: ↑ and ↓ move the focus, Page Up, Page Down and the wheel turn the page, Home and End go to the ends |
| `SoftKeys` | The rubber keys under the glass, each action in the glass over its key. A key with a key of its own prints it on its cap; the rest are walked with ← and →, and Enter presses the one walked to. A key that can't be taken now stays in its place, dim, and the walk never moves on from it to a key that can't be undone |
| `Slate` | The captain's slate (section 18.4): a graphic LCD in a rubber housing, the grid in dark dots on the grey-green |
| `LostCard` | The ship lost or the career over: the tubes dark, and a printed card with what happened and the ways on, each with the game's key for it; a click runs a way as its key would |
| `Boards` | What every page of a terminal reads at once, a few times a second and straight after an order: the windows' providers, and the station docked at, its owner, the standing, its services, the tanks, the infirmary's record and the held deliveries this market can supply |
| `ShipSystems` | The ship's systems by kind, as a terminal lists them: health, power, state and switch; a kind the ship has more than one of is a group that switches them together; each gun by its weapon and mount |

**FILAMENT × Context** shows the windows as its spec's full-screen modes (functional-context, section
10), each the whole screen under the top rack with its tabs on the digits: M1 the map's panels round
the game's orbit canvas, M2 the ship (systems on its plan, crew, cargo, loadout), M3 the station
terminal (market, yard, shipyard, missions, hire, with the station's rail), M4 boarding, M5 the
captain's record on the slate (log, career, standing, contracts), and M6 the controls card. P3 and X3
still interrupt over a mode. `tools/playtest/windows-vfd.sh context` drives every mode in the game
and screenshots each.

**FILAMENT × Stations** shows them as its spec's panels (functional-stations, section 10), one at a
time, each standing for the window of its page so the game's keys, Esc and the game's own checks
work on it. A panel is a terminal or the slate, 480 wide at the right with the world beside it, down
the screen on deck and over the band seated; Tab steps its pages, and a page that needs the room (the
Yard, the Shipyard) takes the screen's width under the top column:

| Panel | Pages | Opens on |
|---|---|---|
| The Station link (`StationLink`) | Market, Yard, Shipyard, the contract Board, Hiring, the Infirmary, each only where the station offers it; away from a yard, the crew's field refit alone | T, Y, J while docked; Y away from a yard |
| The slate (`CaptainSlate`) | Ship, Crew, Medical, EVA, Boarding (latched), Prisoners (held), Contracts, Career, the Log | I at the Crew, K, L; J where there is no board |
| The Plot (`PlotPanel`) | Map, Plan, Targets, in a terminal at the right of the game's orbit canvas, which keeps its own keys and draws no title of its own | M |
| A console's panel (`ConsolePanel`) | The pages of the stations the console hosts, the MFDs' pages repeated: the Helm console panel (Helm, Navigation, Comms), the Tactical board (Contacts, Weapons, Defense), a gun's panel (its mount), Engineering at the reactor and life support (Power, Atmos, Damage); under them the console itself: its system and how it is, its mount, who works it, its switch on a soft key (a click alone) and the game's orders at it with their keys | E at the console, on the page of its worst live alert, else its focus page |
| The controls card (`ControlsCard`) | A printed card under the top column, its Keyboard and Gamepad sides on index tabs; the keys for where the captain is first, marked HERE, then the keys that work anywhere and the MFDs' for a command seat; the game's window keys say what they open here | F1 |

The ship lost lays `LostCard` over the dark HUD, every tube off. `tools/playtest/windows-vfd-stations.sh`
drives every panel in the game and screenshots each.

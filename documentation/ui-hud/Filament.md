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

# FILAMENT × Task-driven

The task-driven functional design (`functional-task.md`) built in the FILAMENT style ([`style-vfd.md`](../style-vfd.md)). The spec decides what each widget shows, where and when; this page records how FILAMENT builds it, what the style changes beyond the look, and the critique of each round.

Mockups are drawn by [`vfd/src/task.js`](../vfd/src/task.js) over the game's hull art and a real deck capture; `vfd/src/export.sh` re-renders them.

## The idea: a 90s digital dash

The other two FILAMENT variations are built like consoles: racks along the edges. The task design's widgets live in corners and sides, by what the player is doing, so this variation is built like the dashboard of a 1990s car with a digital cluster: **no rack spans the view; each corner carries its own moulded pod.**

- The **cluster** (top left) holds the clock and the annunciator, which becomes a block of **tell-tales**: ten lamps, each an icon beside a word, the way a dashboard shows its warning lamps.
- The **message centre** (top centre) is the alert band: a window behind red glass for the one warning, a row behind amber glass for up to three cautions.
- The **flight instrument** (bottom centre) is the speedometer: its five modes are fixed legends along the top, the one in use lit inverse, like a deck's PLAY and REC row.
- The **weapons pod** (bottom right) is the trip computer: a table of mounts, with the scope as a square graphic panel beside it.
- The **hint line** is a printed legend strip: rubber key caps and words that go dark as the player learns them.

## Screens

**Helm at rest.** No target, no plan, nothing wrong: the clock, ten dark tell-tales, the flight instrument in CRUISE, the scope at 10 km and the hint strip. Nearby objects are glyphs only, because nothing is targeted.

![Helm at rest](vfd/task-helm-quiet.jpg)

**Gunnery in combat.** Two missiles inbound at 14 s: the message centre's red window carries the warning with the action that fits the post (`F DECOY`); fire and a starved power store are the cautions; FIRE, POWER and ARMS tell-tales are lit. The target card, the weapons table and the scope fill the right; the helmsman's order sits where the flight instrument would be; the schematic shows the fire.

![Gunnery in combat](vfd/task-gunnery-combat.jpg)

**Deck during damage control.** A fire in the crew spaces with Bo on it, a shorted reactor with Ada resetting it, a breach in the hold with nobody on it, the captain in a pressure suit in thin air. Hazards are tagged on the deck itself; the warp clock says why it's held.

![Deck during damage control](vfd/task-deck-damage-control.jpg)

## How it is built

| Spec widget | FILAMENT part |
|---|---|
| CHR chronometer | The cluster's top module: day and time in medium seven segments with colon cells; `NEXT` and `HELD` as a pair of legends saying what the window below holds; warp rate, `X`, `RAILS`, and a 13-cell dot-matrix window for the next stop or the hold reason |
| ANN annunciator | Ten tell-tales in a 5 × 2 block: icon and word in a dual-filter cell (amber frame for a caution, red inverse block for a warning), ghosted when dark |
| PER personal status | Reflective LCD under the cluster: health, suit, the room's air, suit air in LCD segments |
| ALR alert band | The message centre: a red-glass warning window (title in fourteen segments, count, bearing, time to impact in large seven segments, the action with its key cap) and an amber-glass caution row of three two-line cells |
| OBJ, NTF | Slabs in Z3; a contract's time left in seven segments with inline units (`2d21h`); `LATE` has its own amber place on each row |
| TGT target card | A slab: the five relations as fixed words with `HOSTILE` behind red; the name; `FIRING ON YOU` and `HAS YOU LOCKED` behind red; hull as a four-cell bar with its band word and `EST` |
| WPN weapons cluster | A table with a pip pair leading each mount, then launcher, decoys, shields (state, cloud share, trim, four sectors as a ring of three rails), heat with a rising filter zone |
| SNS sensor scope | A square graphic dot panel, range rings in dots, hostile count behind red |
| SCH ship schematic | A graphic dot panel in its own pod, the plan with fire and crew |
| FLT flight instrument | The mode row, frame in dot matrix, speed in large seven segments, `ASSIST`, `MAIN`, `RCS`, Δv with the home notch |
| HLM helm status | A one-line dot-matrix slab |
| HNT hint line | A legend strip of key caps; a learned key's word goes dark |
| OVL deck overlays | Projected tags on the deck with who is on each hazard; the deck view darkens a soft pill under each tag so it reads over lit conduits |

## What FILAMENT changes beyond the look

- **Pods, not racks.** Each zone's widgets share a moulded housing; between them the world reaches the screen edges.
- **The band's colours are places.** The warning window is red glass and the caution row amber glass, so the band's rows can't swap: with only cautions standing, the red window is gone and the amber row stays where it was (the spec's deck wireframe moves cautions up; see the notes).
- **Tell-tales.** The annunciator's lamps carry icons, so the block is learned by shape as well as position and word.
- **The hint line is printed.** It can't fade like a text line, so its words go dark one by one as they're learned, and the strip is as long as what it holds.

## Round 1 critique

1. **Type is below the minimums.** Legends at 7.5 to 9.5 units and tell-tale icons at 11, against the guide's legends 10+ and icons 12+. *Round 2:* re-lay at the minimums.
2. **The caution row floats when there is no warning.** On the deck it sits at y 72 with nothing above it. That is the fixed place doing its job, but it looks lost. *Round 2:* the warning window stays as dark ghost glass whenever the band is up, so the row hangs from hardware.
3. **The weapons table is dense.** Five columns at pitch 1.5 with the gun type at L1 is near the limit; at the minimum sizes it won't fit 278 units. *Round 2:* the gun type becomes a two-letter code or an icon, and the time of flight moves into the state cell when the gun is ready.
4. **Scope keys are too small to click.** `−` and `+` are 20 × 11 units. *Round 2:* 24-unit keys.
5. **Fixed in round 1:** columns that ran into each other in the weapons table; the objective's name running into its time; the helm status and the hint line overlapping the weapons pod; the hold reason cut short (`FIRE ABOAR`); deck tags unreadable over lit conduits (scrims added); hulls drawn at two scales.

## Functional notes sent to the task designer

1. **SCH is missing from the gunnery wireframe.** FIRE and POWER are lit there, and section 6 shows SCH at gunnery while any of HULL, FIRE, AIR, POWER, CREW or EVA is lit. The mockup draws it in Z5.
2. **WPN and HNT overlap.** In the gunnery wireframe WPN (800 to 1080, 560 to 784) and the hint line (320 to 960, 768 to 790) share x 800 to 960, y 768 to 784. The mockup ends the hint strip at its last key.
3. **Orbit-frame speed at rest.** The helm-quiet wireframe shows 0.0 m/s in the ORBIT frame, but CRUISE in the orbit frame shows the orbital speed (about 7,670 m/s in low orbit).
4. **Decoys have no stock.** WPN's `DCY 3` and the compact pip row's "decoys left": the game has a 3 s cooldown and no count (`PlayerGunnery.DECOY_COOLDOWN`). Suggest "ready, or seconds".
5. **Cautions keep their row.** The deck wireframe moves cautions to the band's top line when no warning stands. Suggest the spec say the caution row keeps its place, so a warning arriving never shoves the cautions down.
6. **Room names from the ship.** The deck wireframe's rooms (Bridge, Galley, Crew, Engineering) aren't the starter corvette's: `chassis.toml` has the hold and engineering aft, the walkway, the crew spaces with the bridge, and the reactor room forward. Alert text should take room names from the chassis.

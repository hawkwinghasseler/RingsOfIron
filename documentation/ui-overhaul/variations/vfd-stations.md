# FILAMENT × Ship stations

The ship-stations functional design (`functional-stations.md`, round 9) built in the FILAMENT style ([`style-vfd.md`](../style-vfd.md)). The spec decides what each widget shows, where and when; this page records how FILAMENT builds it, what the style changes beyond the look, and the critique of each round.

Mockups are drawn by [`vfd/src/stations.js`](../vfd/src/stations.js) over real game captures and the game's own hull art; `vfd/src/export.sh` re-renders them.

## Screens

**C. Seated at the helm, cruising (Green).** The ship is well, so the console is dark: the cluster shows the reference, the next burn and Δv against the trip home; both MFDs are folded to their soft-key rails; the station board is dim, `YOU` included, with Cole named at TAC; the prompt line offers three keys.

![Helm, cruising](vfd/stations-helm.jpg)

**E. At the weapons console, in a fight (Red).** The First Play fight: the corvette against the pirates' corvette, which has thrown harpoons. The missile warning fills from `MASTER WARN`, the hostile caution from `MASTER CAUT`; the tactical cluster carries the target, the two guns in hand with their own hit chances (Cole has the nose gun), point defence, the launcher's salvo behind amber and the decoy; the shield slot is blank glass, since the corvette has no emitter. SENSORS and DAMAGE are up on the MFDs.

![Weapons console, in a fight](vfd/stations-weapons.jpg)

**B. On deck, fire and missiles inbound.** The deck view in battle lighting with the fire in the bridge and the hold venting drawn on the hull, the same top column, three callouts in the ticker (the SHIP lines say only where and who) and the suit readout as the captain's LCD: the player is in the hold, its pressure falling.

![On deck, fire and missiles inbound](vfd/stations-deck.jpg)

## How it is built

| Spec widget | FILAMENT part |
|---|---|
| Z1s status strip | A rack module of fixed legends: `YELLOW`, `RED`, `BATTLE STATIONS`, `DOCKED`, `LATCHED`, then a dot-matrix field for the emergency text. Dark in Green |
| Z1 annunciator | One row: `MASTER WARN` and `MASTER CAUT` lamp keys at the ends, six dual-filtered tiles between. Warnings fill inward from WARN as red inverse blocks, cautions inward from CAUT as amber outlines; past six, the tile where they meet reads `+n MORE` |
| Z1w warp strip | A single dot-matrix line with the rate in seven segments |
| Z2 callout ticker | Three dot-matrix lines at pitch 2 behind one glass, each led by a severity pip pair and the station tag; 54 cells for the speaker and the text |
| Z3b helm cluster | One module, five fixed slots: velocity (frame legends, seven segments autoranging `M/S` to `KM/S`, control chip, `ASSIST`, thrust lamps), reference, next event, trigger, Δv. Δv is a two-rail bar: the fill, the home notch, the plan bracket |
| Z3b tactical cluster | The target block on top; a chip per gun in hand (state, its own hit chance in seven segments, the crew member seated at it); one `PD AUTO` chip; the launcher (`MSL 4 OF 4`, ready or seconds, `SALVO` behind amber when one press empties it), the decoy, the shields (blank glass on a hull with no emitter) |
| Z3a, Z3c MFDs | Modules with soft keys. A page not needed folds: the glass sinks into the rack, the rail stays at L1 |
| Z3d station board | Six cells: the station's number and name on a key, the holder in dot matrix. Dim in Green; `YOU` lit in Yellow and Red; an alert lights the cell's own pip in any condition |
| Z7 prompt line | Learning hints on a small plate over the cluster: key caps and words |
| Z6 suit readout | Reflective LCD, backlit only while it warns |
| World symbology | The projected HUD: dot sprites, zinc and red channels, drawn under the band |

## What FILAMENT changes beyond the look

- **The band is a rack.** The seated band is an opaque moulded rack across the bottom, not panels over the world; the camera already lifts the ship by half the band's height (spec 6.2), which FILAMENT relies on.
- **Severity has an end of the row.** Warnings grow from one end and cautions from the other, so severity reads by position as well as colour and form.
- **Folded MFDs keep their rail.** "Fold" is literal: the glass sinks, the soft-key rail stays where the hand learned it.
- **Missing hardware is blank glass.** A system the hull doesn't carry (the corvette's shields) leaves its place as glass with nothing printed on it, so the player never reads it as "off".
- **The suit is LCD.** The captain's own kit reflects; the ship glows.

## Round 1 critique (resolved in round 2)

1. **Type below the minimums.** The band re-laid at the guide's round-2 minimums; the cluster's slots lost their least useful secondary values first.
2. **Inverse text filled in.** Cut letters are re-darkened after the bloom (guide 7.8), so the warning tiles read.
3. **Folded MFD rails too bright.** A folded rail idles at L1.
4. **The station board talked in Green.** The spec now dims it in Green (its round 6), and the mockup does.
5. **The top column was a 192-unit block.** The spec moved to one annunciator row with `+n MORE` (its round 6); the ticker sits at y 108.

## Round 2 critique

The mockups were redrawn against spec round 8: the corvette's real mounts (coilgun, point-defence autocannons, harpoon launcher, laser), harpoons arriving in seconds, the starter crew, the rooms BRIDGE and HOLD, one annunciator row, SHIP lines that add only what a tile can't, a dim board in Green.

1. **The shield slot was ghosted** (`SHLD`, `RAISED`, `88%` printed dark) on a corvette that has no emitter, so it read as shields switched off. *Fixed:* blank glass, as the tell-tales already do.
2. **The annunciator's zones were fixed at three and three.** A fourth warning had nowhere to light while amber tiles stood empty, though the spec lets any mix fill six. *Fixed:* every tile is dual-filtered and the split moves; past six, the tile where warnings and cautions meet counts the rest (guide 7.8).
3. **The deck didn't show its own trouble.** The fire and the venting were told only by tiles and beacons. *Fixed:* the fire's glow in the bridge and the vented air fanning out of the hold are drawn on the deck, as the game draws them.
4. **Kept:** the missile impact mark carries its count (`2`) in the world: the stations spec doesn't forbid it, and it is the only place on deck that says how many will hit where.

No meaningful criticism of the visual build remains open. The note below is functional.

## Round 3

Spec round 9 took the round-2 note: a callout is at most 50 characters after the station tag, the speaker's name included, and a longer one is the style's to wrap or scroll, never to cut. E's line is now "Cole: harpoons from port. PD has them." (38), and the mockup carries it. The spec also adopted FILAMENT's two choices: a slot for a system the hull lacks is blank with no legend, and `+n MORE` goes on the last tile to fill, so warnings and cautions may fill from opposite lamps.

Nothing meaningful remains to criticise, visually or functionally: this variation has converged. It reopens if the spec changes something visible.

## Functional notes sent to the stations designer

Round 1 (all taken in round 6): one annunciator row with `+n MORE`, SHIP callouts that don't restate tiles, the station board dim in Green.

Round 2 (taken in round 9):

1. **Callouts need a length.** FILAMENT's ticker holds 54 characters after the station tag at pitch 2; E's line "Cole: harpoons off the Raider, port side. PD has them." is exactly 54. Suggest 13.5 cap a callout at about 50 characters with the speaker's name counted, and say what happens past it (FILAMENT marquees it, one cell at a time with a hold at each end).

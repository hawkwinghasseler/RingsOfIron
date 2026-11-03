# FILAMENT round log

Each round, the three FILAMENT variations ([stations](vfd-stations.md), [context](vfd-context.md), [task](vfd-task.md)) are re-mocked against the latest functional specs, critiqued, and improved; functional critiques go to the spec's designer. The loop ends for a variation when no meaningful criticism remains.

| Variation | Spec version used | Status after the latest round |
|---|---|---|
| Ship stations | `functional-stations.md` round 14 | Converged in round 7: C's closest approach, and the lead reticle on the target |
| Context-driven | `functional-context.md` version 9 | Converged in round 8: version 9 took both of round 7's notes, each as FILAMENT had drawn it |
| Task-driven | `functional-task.md` round 8 | Converged in round 7: the helm's order on a two-row HLM, the closest approach, the reticle on the target |

A variation that has converged reopens when its spec changes something visible, or a designer's answer to a note does.

## Round 8

**Context** (version 9). The designer took both of round 7's notes: H6's example closes at 12 m/s with a 2.2 s shot, and wireframe 04's H1 reads the kiter's 12 m/s in the LOCK frame (11.4 and 11.5 are one fight, three seconds apart). Both are as FILAMENT drew them, so no screen changed.

All three FILAMENT variations have converged: each spec has taken every note, and no meaningful criticism remains.

## Round 7

All three specs moved again, from the other artists' notes.

**Stations** (spec round 14). C's closest approach is `5:03`. Walkthrough F's changes are on a sheet FILAMENT doesn't draw.

**Context** (version 8). The kiter holds its range along the line of sight: H6 `CLOSING 12 M/S`, `SHOT 2.2 S`, H1's LOCK-frame speed 12, and the LOCK-frame prograde marker on the line to the raider (it had been nudged 23° off it). H4's power row shows the output alone, `OF` and the rating dark until damage holds the output under them. 12.4's values at 15 were already met: seven segments 12 tall stand taller than a 15-unit typeface's cap.

**Task** (round 8). The closest approach is `1:15` and the helm's order `APPROACHING RAIDER TO 2,000 M`, which didn't fit HLM's one line: HLM is now two rows, the order in a window the slab's width.

**Everywhere: the lead reticle sits on the target.** The context and task specs now say what the game's physics gives: at fight ranges the lead is tens of metres. FILAMENT's three reticles sat 200 to 500 m of lead off their targets (E's on the far side of a closing one). Each now sits on the hull inside the lock mark, and the world view dims a soft disc under it, since its dots otherwise read as the hull's own lights (guide section 8).

**Guide.** The lead reticle's sprite and its place at fight ranges (8); how FILAMENT meets the specs' value size of 15 (15).

**Sent to the designers.** Context: H6's sample block still reads 85 m/s and 2.1 s; the wireframe's H1 still reads 38 m/s in the LOCK frame.

## Round 6

All three designers took both of round 5's notes.

**Stations** (spec round 13). The seat's station board and its world symbology go dark with the seat, and the prompt line shows neither the tow nor abandoning while the strip carries them: both as FILAMENT drew them, so no screen changed. The spec adds the deck's side of a blackout (the suit's marks stay; emergency lighting, door lights and alarm beacons lit), which the guide's emergency bus now gives (7.11).

**Context** (version 7). Point defense has one spelling across the three specs. H7's missile row is the total across launchers, ready while any can fire: 11.4 reads `MSL 7 LEFT READY`. The count now has two seven-segment cells, since a total can pass 9.

**Task** (round 7). The missile alert carries the count and the time, and no bearing: direction is drawn in the world and on the scope. The spec moved its sample harpoons to 3.2 km, just thrown, for its 14 s; FILAMENT keeps them 1.4 km out at 4 s, which the designer confirms fits the same formula.

No notes went out this round. All three FILAMENT variations have converged: each spec has taken every note, and no meaningful criticism remains.

## Round 5

All three specs moved, so all three variations were rebuilt.

**Stations** (spec rounds 11 and 12). The emergency text names the state and the ways out (`DISABLED`, P twice, `ABANDON`, B twice, `TOW`: 28 of the strip's 39 cells) while the cause has its own tile, including the new HULL tile. The kit's blacked-out seat is a fourth screen, the first in an emergency: the seat's instruments unpowered with their NO POWER face, the overlay on the emergency bus. Every MFD soft key carries its page's full name, on three page keys and a split pin/fold key. E's harpoons count their burn (2.4 and 2.5 km, 8 and 9 s) and SENSORS opens at the new 5 km step.

**Context** (version 6). P2's code field is 11 cells and handling 3, with a cell's space kept after a full code; 11.4's lock at 3.4 km, missiles 4 left and ready, the missiles drawn where `T-4 S` puts them under a harpoon's burn, the scope at 5 km; the corvette's three crew in 11.7 and X2.

**Task** (round 6). The SHIELD lamp; the scope at its new 5 km step; `IMPACT 4 S`, since 9.2's formula puts a harpoon 1.4 km out about 4 s from impact, not 14.

**Everywhere.** Point defense is spelled the game's way, on screen and in the guide.

**Renderer and guide.** A scene can draw unpowered: nothing lit and every ghost showing. Drawing the blackout found that cutting a module's power after drawing it left its lit segments blank (a lit segment has no ghost under it), so a dead module is now drawn from scratch. The guide defines the NO POWER face and the emergency bus (7.11, 9.5, principle 10) and the MFD rail (7.7), counts the emergency text's cells again, and leaves the burn arrow's label to the spec. The specimen's soft keys carry full page names.

**Sent to the designers.** Stations: what else goes dark in a blackout (the board, the projected HUD); the tow's hint while disabled. Context: point defense as the game spells it; H7's missile row on a hull with two launchers. Task: the gunnery sample's 14 s against harpoons 1.4 km out; what a bearing is measured from.

## Round 4

**Stations** (spec round 10, from the Cassette artist's notes). Four changes reach FILAMENT's screens: the burn arrow carries only its number; the emergency text has a 44-character short form, which needed one more cell than the status strip's field held, so the strip's legend cells were tightened and each named key is one inverse cell (guide 7.7); Command's board tile carries only the printed `LATCHED` badge; on deck a speaking mark replaces the bubble (guide section 8). Re-rendered; converged again. Context and task specs have not changed since round 3.

## Round 3

**Stations** (spec round 9). The designer took the callout note (50 characters after the station tag, wrapped or scrolled by the style, never cut) and adopted FILAMENT's blank slot for missing hardware and its `+n MORE` on the last tile to fill. E's Cole line is now 38 characters; the weapons screen is re-rendered with it. Converged.

**Task** (spec round 5). The designer took all four notes: short forms in two parts at a double space, each within 19 characters; a reserved `+n` slot (Z2 is 640 wide); a screen starts below the band wherever the style puts it; the prompt names its object once. Each matches what FILAMENT already drew, so no screen changed. Converged.

**Context** (spec version 5). The designer took all four notes: X2's state on a row of its own; X3 waits while power is due back by itself, and 11.7's reactor is damaged instead, so P2 lists `NO POWER  DAMAGED  YOU` third; the master cue stays lit under X3; the sample data. The deck emergency and boarding screens are re-rendered with them. Checking against version 5 found X2 at 182 units against a 150 zone (10% allowed); its keys now carry their actions printed on them, so it is 155. Converged.

All three FILAMENT variations have converged: each spec has taken every note, and no meaningful criticism remains.

## Round 2

**Renderer.** Inverse letters are kept on a cut mask and darkened again after the bloom, so warning tiles, the seat in use and active modes read at full drive. Legends are set in Inter SemiBold. The renderer now warns when any text is under the guide's minimum or overflows its field; every mockup and the specimen render with no warnings.

**Type to the minimums** (guide section 15): legends 13 (12 for silk, key caps and lamp words), seven segments 12, dot matrix at pitch 1.5 for column text and 2 for running text and world labels, LCD text 12, anything clickable 24. Where a module no longer fit, content was cut by priority, not shrunk. The specimen sheet was redrawn at the same sizes.

**Stations** (spec round 5 to round 8): the corvette's real mounts and the First Play raider's harpoons, arriving in seconds; the starter crew and the rooms BRIDGE and HOLD; one annunciator row with `+n MORE`; SHIP lines that add only what a tile can't; the board dim in Green. Then from the critique: the shield slot is blank glass on a hull with no emitter; the annunciator's tiles are dual-filtered so the warning and caution split moves; the deck draws its fire and its venting.

**Context** (version 2 to version 4): P2 in fixed fields with handling and timers, P3 on two lines, the boarding screen (11.13), the master lamps on the tower's shelf or standing alone, hazard tags on the deck, the gunship in combat. From the critique: the frame tag reads on the side away from the ship; sample data matched to the game (a delivery the corvette can carry, missiles that arrive in seconds).

**Task** (first version to round 4): four FLT modes, `ASSIST OFF` only when it is off, markers with names and no numbers, the starter crew, nine lamps on the corvette. The tell-tales have four states printed in register; the message centre's caution cells are two lines with a `+n MORE` cell; the weapons table has a header row; the console's prompt is a slab. From the critique: the weapons columns told apart by drive level, the scope's count in two cells, sample cargo the economy has.

**Guide edits:** no bare signs (5.1); pitch and sizes (5.3, 5.4); keys at least 24 units and folded rails at L1 (7.7); tell-tales in register, the message centre, the `+n MORE` rule, the cut mask, dual-filtered tiles (7.8); deck tags by severity, the hazard tag, the square prograde marker for a station or target frame, the beaten row, the frame tag (8); the ticker's 54 cells (9.3); retired instruments sink, the master lamps' shelf (12.2); the minimum sizes (15); the cut mask in `VfdLayer` (16).

**Sent to the designers.** Stations: a length for callouts. Context: X2's state line overflows; X3 offers costly exits while power is coming back; keep the master cue lit under X3; sample data (an ice mine buys food, missiles arrive in seconds). Task: the short form's double space is a line break; reserve `+n more` in Z2; INT names the object once; Z2's height at the minimum sizes.

## Round 1

**Made.** Three screens per variation: stations (helm cruising, weapons in a fight, deck with fire and missiles), context (helm cruise, helm combat, deck emergency), task (helm at rest, gunnery in combat, deck damage control). Backgrounds are real deck captures and the game's hull art.

**Fixed during the round** (found by rendering the first drafts):

- Zoned bars lit their red and amber feet on every healthy bar, so a ship at 81% hull looked alarmed. Depleting bars are now zinc with notches, and their alarm moved to the row's leading pips; nominal rows sit at L1. The guide's 7.2 and 7.6 now say so.
- Inverse dot matrix (the master cue) was unreadable once bloomed; it became a cut-out legend, and the guide sets a minimum size for inverse text (7.8).
- The deck emergency showed the whole corvette, so its off-screen pointers pointed at things on screen. The deck is zoomed in on the captain.
- The emergency brownout dimmed the caution list; it now dims only the strip and the feed (guide 4.3, spotlight).
- The LCD backlight outshone every alarm; it is a dim teal now (guide 7.11).
- Data the game doesn't have: a decoy count (decoys have a 3 s cooldown), a hit-chance word scale (the game has a probability).
- Layout collisions in the task variation: weapons columns, objective title against its time, the helm status and hint line over the weapons pod, deck tags over lit conduits (scrims, guide 8).
- New icons for the tell-tales: drive, EVA.

**Open for round 2** (all three variations):

1. **Type to the minimums.** The mockups break the guide's own minimums (legends 10, segments 12, body pitch 2) and the specs' (body 13, annunciator 12, values 15). Every module is re-laid at those sizes; where it no longer fits, content is cut by priority, not shrunk.
2. **Inverse text through the bloom.** Cut letters are re-darkened after the bloom so warning tiles, the seat in use and active modes stay readable.
3. Variation items: folded MFD rails and the station board at L1 (stations); H6's state row as one word and the ship's real plan in H4 (context); the caution row hung from a ghost warning window, a narrower weapons table and 24-unit scope keys (task).

**Sent to the designers.** Stations: one annunciator row by default, SHIP callouts that don't restate tiles, a dark station board in Green. Context: keep P2 lit under X3, decoys have no stock, shields in kilograms, D3 never fires on the starter ship, 12.4 against the columns. Task: SCH missing at gunnery, WPN and the hint line overlap, orbit-frame speed at rest, decoys have no stock, cautions keep their row, room names from the chassis.

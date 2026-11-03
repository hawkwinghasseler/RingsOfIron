# FILAMENT round log

Each round, the three FILAMENT variations ([stations](vfd-stations.md), [context](vfd-context.md), [task](vfd-task.md)) are re-mocked against the latest functional specs, critiqued, and improved; functional critiques go to the spec's designer. The loop ends for a variation when no meaningful criticism remains.

| Variation | Spec version used | Status after the latest round |
|---|---|---|
| Ship stations | `functional-stations.md` round 10 | Converged again in round 4, after the spec's round 10 changed four things on screen |
| Context-driven | `functional-context.md` version 5 | Converged in round 3: the spec took all four notes, nothing left to criticise |
| Task-driven | `functional-task.md` round 5 | Converged in round 3: the spec took all four notes, nothing left to criticise |

A variation that has converged reopens when its spec changes something visible, or a designer's answer to a note does.

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

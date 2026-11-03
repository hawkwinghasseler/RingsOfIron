# FILAMENT × Context-driven

The context-driven functional design (`functional-context.md`, version 2) built in the FILAMENT style ([`style-vfd.md`](../style-vfd.md)). The spec decides what each widget shows, where and when; this page records how FILAMENT builds it, what the style changes beyond the look, and the critique of each round.

Mockups are drawn by [`vfd/src/context.js`](../vfd/src/context.js) over the game's hull art and a real deck capture; `vfd/src/export.sh` re-renders them.

## Screens

**11.1 Helm, cruise.** About ten lines of text: the status strip, the objective, navigation, the flight tower and four dark ship lights. The scope has folded to its key: three contacts beyond 50 km.

![Helm, cruise](vfd/context-helm-cruise.jpg)

**11.4 Helm, combat.** A raider has fired two missiles and holed cargo 2. The threat slab, lock card and armament power up in their zones; the ship-condition face rises over its lights because the hull light is red; the master cue in the flight tower caught the eye when the breach came.

![Helm, combat](vfd/context-helm-combat.jpg)

**11.7 Deck, emergency.** No power and drifting. The emergency card offers the two ways out with their costs; the caution list holds what is burning and who is on it; the off-screen pointers point aft at the fire and the casualty; the captain's own vitals are LCD. The strip and the feed are on the dimmer bus.

![Deck, emergency](vfd/context-deck-emergency.jpg)

## How it is built

| Spec widget | FILAMENT part |
|---|---|
| P1 status strip | A thin rack across the top: the seat as fixed legends with the one in use inverse; helm authority in dot matrix with a caution pip; `BATTLE STATIONS` behind amber; the full-screen chip as two pips with counts; clock and warp in seven segments; `PAUSED` |
| P2 caution and warning | A slab in L-TOP. Each row: a red pip and an amber pip in fixed columns, the code, the detail at L1, a crew glyph with the count on it, a timer |
| P3 threat | A slab: `MISSILES` in fourteen segments, count and seconds in large seven segments, all behind red glass; decoy and point-defence states as legends |
| P4 message feed | A slab of dot-matrix lines, speaker at L1 |
| P5 objective | A slab: heading and place in dot matrix, time left in seven segments with an inline unit (`1.4d`) |
| H1 flight display | The tower of the bottom rack: frame legends with `AUTO`, speed in large seven segments, closing and opening as legends, six thrust lamps, `ASSIST`, Δv with the propellant bar. The master alert cue is a dual-filter cell in the pilot's eyeline |
| H2 navigation | A slab of labelled rows; times in seven segments with colon cells; `SHORT` behind amber |
| H4 ship condition | Compact face: four dual-filter lamp cells on the rack's left wing (hardware, always there). Expanded face: a slab above them with the plan on a graphic dot panel and one row per light, pips leading |
| H5 contact scope | Collapsed: a key on the right wing with the count beyond 50 km. Open: a round pod with a plotting scope |
| H6, H7 lock and armament | Slabs in R-MID; states as lit words, `HOSTILE` and `FIRING ON YOU` behind red |
| X3 emergency | A slab in TOP-CENTRE headed by a red inverse bar; each way out with its key cap, presses and cost |
| D1 captain vitals | Reflective LCD, backlit while it warns |
| D3 off-screen pointers | Projected chevrons with the subject and distance in tiles |

## What FILAMENT changes beyond the look

- **Two racks, not a floating set.** P1 is a thin rack along the top and the seat's instruments sit in a rack along the bottom with H1 as a tower in the middle. Everything conditional is a slab that powers up in its zone's slot and is gone when empty.
- **H4's compact face is hardware.** The four lights are lamp cells on the rack, always present; the expanded face is a slab that rises above them. The player never loses the lights' positions.
- **The emergency spotlight is the dimmer.** The spec dims "everything but H4 and D1" while X3 shows. FILAMENT does that with the bus: the strip and the feed drop to 45%. The caution list stays at full drive (see the notes).
- **Hit chance and decoys follow the game.** Best hit is a percentage (`HitChance` returns a probability); decoys are `READY` or seconds, since the game has a 3 s cooldown and no stock.

## Round 1 critique

1. **Type is below the minimums.** Legends at 7.5 to 9.5 units, H6's states at 7.5 to 8.5, seven-segment fields at 10 to 12, against the spec's 12.4 (body 13, values 15) and the guide's own (legends 10, segments 12). *Round 2:* re-lay every slab at the minimums.
2. **State rows don't survive the size fix.** H6 lists five ghost states (`FIRING ON YOU FLEEING DISABLED SURRENDERED WRECK`) in 296 units; at 13 units they won't fit. *Round 2:* `FIRING ON YOU` keeps its red cell; the target's state becomes one dot-matrix word.
3. **The H4 plan is generic.** It is a box layout, not the ship's own deck. *Round 2:* plot the plan from the corvette's chassis (its hold, engineering, walkway, crew spaces and reactor room) on the dot grid.
4. **The P3 slab is mostly empty glass** at the spec's 500 units. Acceptable; the ghost cells are the slab's capacity (a second line for `1 DOWN`).
5. **Fixed in round 1:**
   - Zoned bars lit red and amber feet on every healthy bar (H4 and the propellant bar looked alarmed at 81% hull). Now zinc with notches and leading pips, and nominal rows at L1.
   - The master cue's inverse dot matrix was unreadable; it is now a cut-out legend.
   - The deck emergency showed the whole corvette, so the "off-screen" pointers pointed at things on screen. The deck is now zoomed in on the captain, and the pointers point aft.
   - The brownout dimmed the caution list and half of the emergency card; it now dims only the strip and the feed.
   - H7 showed a decoy count the game doesn't have; H6's hit chance was a three-word scale where the game has a number; H6 had an unexplained empty row; H5's key read `3 > KM`.

## Functional notes sent to the context designer

1. **Keep P2 lit under X3.** "Dims everything but H4 and D1" also dims the caution list, which is the evidence for X3's choice (what is burning, who is down, who is on it). Suggest X3 dims everything but P2, H4 and D1.
2. **Decoys have no stock.** The game has a 3 s cooldown (`PlayerGunnery.DECOY_COOLDOWN`) and no count; H7's "Decoys: left" should be "ready, or seconds".
3. **Shields in kilograms.** H4's shields bar shows "cloud mass of capacity"; `230 KG` means nothing to a player. Suggest the share on the face and the mass in F3.
4. **D3 never fires on the starter ship.** At the default deck zoom the corvette fits on screen, so nothing is off screen. The deck emergency needs on-screen hazard tags too (fire, breach, casualty, with who is on each), as the task design has; D3 then covers the rest.
5. **12.4 against the columns.** At 13 units a 308-unit column holds about 26 legend characters; rows that list every state (H6) can't meet both. Suggest the spec ask for the current state only, as a word.

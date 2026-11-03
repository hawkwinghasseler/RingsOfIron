# FILAMENT × Context-driven

The context-driven functional design (`functional-context.md`, version 4) built in the FILAMENT style ([`style-vfd.md`](../style-vfd.md)). The spec decides what each widget shows, where and when; this page records how FILAMENT builds it, what the style changes beyond the look, and the critique of each round.

Mockups are drawn by [`vfd/src/context.js`](../vfd/src/context.js) over the game's hull art and a real deck capture; `vfd/src/export.sh` re-renders them.

## Screens

**11.1 Helm, cruise.** About ten lines of text: the status strip, the objective, navigation, the flight tower with its master lamps dark on the shelf, and four dark ship lights. The scope has folded to its key: three contacts beyond 50 km.

![Helm, cruise](vfd/context-helm-cruise.jpg)

**11.4 Helm, combat.** A gunship (it has a shield emitter, which the starter corvette lacks) under fire: a raider has fired two missiles and holed the ENGINES room. P3 powers up behind red glass with the decoy and point defence; the lock card and armament fill the right; the ship-condition face has risen over its lights because the hull light is lit, the plan showing the breach; `MASTER WARN` on the tower's shelf carries the breach.

![Helm, combat](vfd/context-helm-combat.jpg)

**11.7 Deck, emergency.** The corvette disabled, its reactor stunned and its hold airless. The emergency card offers the two ways out with their presses and costs; P2 lists, in order, what nobody is handling, what the crew are on, the power coming back in 12 s and `+1 MORE: SMOKE`; each hazard in view carries a tag; the captain's own kit is LCD; the master lamps stand alone at the bottom centre. The strip is on the dimmer bus.

![Deck, emergency](vfd/context-deck-emergency.jpg)

**11.13 Boarding, latched.** The fight is over and the ship is latched to the beaten raider. The flight instruments have sunk into the rack and only the shelf with the master lamps stands proud; X2 names the raider and the party's work, with `K` and `U` as soft keys under it; the objective has the bounty's next step.

![Boarding, latched](vfd/context-boarding.jpg)

## How it is built

| Spec widget | FILAMENT part |
|---|---|
| P1 status strip | A thin rack across the top: the seat as fixed legends with the one in use lit; helm authority in dot matrix; `BATTLE STATIONS` behind amber; the full-screen chip, which is the master alert cue while a full-screen mode is open; clock and warp in seven segments; `PAUSED` |
| P2 caution and warning | A slab in L-TOP, six rows of fixed fields: severity in two filtered pip columns, the code (10 cells), the detail at L1 (10), handling (`YOU` inverse, a dot per crew member on it, or `BACK` and the recovery time), and the timer in seven segments. The sixth row is `+n MORE:` with the codes that fit |
| P3 threat | A slab: the count, `MISSILES INBOUND` and the soonest impact on one line behind red glass; the decoy behind its key cap and point defence's state below |
| P4 message feed | A slab of dot-matrix lines, speaker at L1 |
| P5 objective | A slab of two lines: the job, then where and the time left in seven segments with an inline unit |
| H1 flight display | The tower of the bottom rack: frame legends with `AUTO` and the frame's name, speed in large seven segments, six thrust lamps around a hull glyph, `ASSIST`, Δv with the propellant bar. The prograde and retrograde markers carry the frame's tag in the world, on the side away from the ship |
| Master alert cue | Two lamp keys: `MASTER WARN` with the newest warning's code, `MASTER CAUT`; a press acknowledges. On the tower's shelf at the helm; on deck, docked or latched, on a plate of their own at the bottom centre |
| H2 navigation | A slab of labelled rows: labels legends at L1, words dot matrix, numbers seven segments with legend units; `SHORT` behind amber |
| H4 ship condition | Compact face: a lamp cell per light on the rack's left wing (hardware, always there; three on a hull with no shield emitter). Expanded face: a slab above them with the ship's own plan on a graphic dot panel and a row per light, pips leading |
| H5 contact scope | Collapsed: a key on the right wing with the count beyond 50 km. Open: a round pod with a plotting scope and its zoom keys |
| H6, H7 lock and armament | Slabs in R-MID. H6: the state in one word (`HOSTILE` behind red), `FIRING ON YOU` as its own red flag, range with closing or opening, time of flight and hit chance with its word. H7: guns, missiles and shields as rows of fixed state words |
| X2 boarding | A slab where the station panel would be: the target, `LATCHED` or `EVA`, the party, salvage and cargo, with the two actions as soft keys (legends on the glass's bottom row, rubber keys under it) |
| X3 emergency | A slab under P3 headed by a red bar with the state cut out of it; each way out on its own row with its cost, its key on the bezel beside it with an LED that blinks while the first press holds |
| D1 captain vitals | Reflective LCD, backlit while it warns |
| D3 hazards | Projected tags on each hazard in view (red `♦` warning, amber `▲` caution); edge pointers for the rest |

## What FILAMENT changes beyond the look

- **Two racks, not a floating set.** P1 is a thin rack along the top and the seat's instruments sit in a rack along the bottom with H1 as a tower in the middle. Everything conditional is a slab that powers up in its zone's slot and is gone when empty.
- **Retired instruments sink.** Docked or latched, H1's tower sinks into the rack and its master-lamp shelf stays proud: the cue never moves and never vanishes.
- **H4's compact face is hardware.** The lights are lamp cells on the rack, always present; the expanded face is a slab that rises above them. The player never loses the lights' positions.
- **The emergency spotlight is the dimmer.** While X3 shows, everything the spec dims is on the dimmer bus at 45%; P2, P3, H4, D1, D3 and the master lamps stay at full drive.

## Round 1 critique (resolved in round 2)

1. **Type below the minimums.** Every slab re-laid at the guide's round-2 minimums and the spec's 12-unit labels; content cut by priority where it no longer fit.
2. **H6's state row didn't survive the size fix.** The state is one dot-matrix word and `FIRING ON YOU` its own flag (the spec made the same change in version 3).
3. **The H4 plan was generic.** It is the ship's own deck plan on the dot grid.
4. **P3 was mostly empty glass.** P3 is two lines now (the spec's version 3), and the ghost cells are its capacity.

## Round 2 critique

The mockups were rebuilt against version 4: P2's fixed-field rows with handling and timers, P3 on two lines, the boarding screen, the master cue on the shelf or standing alone, on-screen hazard tags, the gunship in combat and the corvette everywhere else.

1. **The retrograde marker's frame tag crossed the hull** (`LOCK` over the stern). *Fixed:* a frame tag reads on the side away from the ship; the guide's marker table says so.
2. **Sample data the game doesn't have.** P5 delivered 120 water to Shackleton Ice: an ice mine produces water and buys food and machinery (`economy.json`), and the corvette's hold takes 40 units of food. P3 counted 37 s to impact from 7.7 km, but a harpoon (60 kN on 400 kg) or a dart crosses that in under 10 s. *Fixed in the mockups:* `DELIVER 40 FOOD`, and `T-4 S` with the missiles a third of the way in.
3. **Kept:** the master lamps on deck stand alone at the bottom centre with nothing around them. That is the spec's point (the cue never moves), and the plate keeps them hardware.

No meaningful criticism of the visual build remains open. The notes below are functional.

## Functional notes sent to the context designer

Round 1 (all answered by version 3): keep P2 lit under X3, decoys have no stock, shields as a share, on-screen hazard tags for D3, states as one word.

Round 2:

1. **X2's state line overflows.** Its worst case (the target, `LATCHED`, the party's activity and the salvage) runs past 34 characters at 12.4's sizes. FILAMENT gives the party, salvage and cargo a row each (seven rows). Suggest the spec size X2 for that.
2. **X3 offers costly exits while power is coming back.** In 11.7 P2 says power is back in 12 s while X3 offers abandon ship and a 3,400 cr tow. Suggest X3 wait while a recovery timer runs, or show the time beside the choices.
3. **Keep the master cue lit under X3.** The dim list doesn't name it, and on deck it stands alone and is the acknowledge control.
4. **Sample data.** "Deliver 120 Water → Shackleton Ice": an ice mine sells water and buys food and machinery, and a delivery posted to the corvette is at most 40 units (`ContractPostings` caps it by the hold). P3's "T-37 s" at 7.7 km: harpoons and darts arrive in under 10 s (`weapons.toml`); the stations design corrected the same number in its round 6.

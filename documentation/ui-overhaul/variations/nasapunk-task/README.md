# Nasa-punk × Task-driven

The task-driven functional spec (`functional-task.md`, round 8 when drawn, on branch
`claude/project-thread-nfiuf0`) skinned in the Nasa-punk style
(`../../nasapunk/style-nasapunk.md`), with its words from
[`../../nasapunk/abbrev.md`](../../nasapunk/abbrev.md). Mockups are 1280 x 800 UI units drawn at
2x; sources are the HTML files here, using `task.js` and the shared kit in
`../../nasapunk/mockups/`. From `documentation/ui-overhaul`, render with
`node nasapunk/mockups/render.js variations/nasapunk-task` and check with
`node nasapunk/mockups/check.js variations/nasapunk-task`.

Round history is in [`../nasapunk-round-log.md`](../nasapunk-round-log.md).

## How the style maps the spec

The spec fixes nine zones (section 5); the style keeps them and fills each with its hardware. Z2 is
the spec's 640 (320 to 960). The left column is 272, widened inside the spec's allowance of a
quarter so the lamps' 12-unit legends fit their lenses.

| Spec widget (zone) | Nasa-punk container |
|---|---|
| CHR chronometer (Z1) | A small clock block: the day and the time large; WARP and the rate; when warp is held, why, at the right. |
| ANN annunciator (Z4) | A ten-lens caution and warning panel in two rows of five, dark when well. A lamp lit for a warning also gets a red ring, so the level reads without colour. A ship without shields has a blanking plate where the SHIELD lamp would be. |
| PER personal (Z4) | The suit's chest display (DCM): condition, suit and its air, the air where you stand. |
| ALR alert band (Z2) | One warning as a red-framed glass: a red lens, the number that matters large, the action on a key cap; no bearing, since the world and the scope show where a threat comes from. Under it the caution row: amber cells sized to their text, filling from the left, each naming who handles it, and the last 44 units reserved for `+n`, so no caution moves when it lights. |
| OBJ objectives, NTF news (Z3) | Slips with a kapton tab (magenta for the navigation target's row); news on comm-loop slips. |
| SCH ship schematic (Z5) | A synoptic plan DU: hazards hatched in their room by severity, crew as dots (amber when hurt), the captain ringed. No text, as the spec asks. |
| TGT target card (Z6) | A DU: name and class, FIRING ON YOU lit, the hull estimate as four cells and a word, RNG and CLOSING at body size, CPA, the contract in magenta. |
| FLT flight instrument (Z8) | A DU led by an FMA-style mode box (CRUISE); the frame's reference and the speed; ΔV on a bar with the home notch; MAIN and RCS lamps. |
| HLM helm status (Z8) | A mode strip: who has the helm, and the order in an FMA box (green while it flies, white while it holds or waits). At gunnery it spans the gap between SHIP and WEAPONS, so an order with a whole contact name fits. |
| WPN weapons (Z7) | A DU: one row per mount (where, what, who works it, state, time of flight), with the decoy as the last row, worked by hand like the launcher. |
| SNS scope (Z7 corner) | A scope DU. The range is automatic, with − and + caps either side of its figure to override it, as the game's scope has them; the count of hostiles on its own row under the scope, so nothing moves when one appears. |
| Interaction prompt | A bezel plate: the object's name once, then each key with its verb (`WEAPONS CONSOLE  E OPEN  V SIT`). |
| HNT hints (Z9) | Key caps with their verbs. |

## Screens

![Helm at rest](t1-helm-rest.png)

**T1, helm at rest near Highport.** The spec's at-rest test: the clock, nine dark lamps and a
blanking plate, FLT in CRUISE in Highport's frame, the scope, and four keys and F1 on the hint
line. The scope's automatic range is 5 km, the step that holds the nearest contact (a freighter at
2.5 km) with room to spare; the third contact, 9 km off, is a tick on the rim. Contacts are glyphs
only, since nothing is targeted, and Highport carries its name.

![Gunnery in combat](t2-gunnery-combat.png)

**T2, the weapons console in a fight with the raider Vex.** Vex is 3.2 km off, closing at 41 m/s
to pass 0.6 km off in 1:15 if nothing changes, and Bea is flying an approach that will hold the
ship 2,000 m off it, boxed green as an order in flight. Two harpoons
inbound a second apart, the first 1.1 km out and 0:03 from impact with its burn counted, the second
1.4 km out (the one warning, with F for a decoy), each drawn on its predicted path to the hull; a
fire in the hold with Ada on it and Cole wounded with no medic free (two cautions). WPN as the
starting corvette really carries its guns: the coilgun and the laser in hand (the laser out of
range at its 3 km), the two autocannons on point defense, the harpoon launcher at 3 of 4, then the
decoy. One lead reticle, the coilgun's, ringing Vex: its round takes 1.05 s, and Vex moves 44 m
in that time. The point-defense ring at 4 km. The scope's automatic range is 5 km, which holds Vex
and both harpoons; a freighter 7 km off is a tick on the rim. SCH shows, because FIRE and CREW
are lit.

![Deck, damage control](t3-deck-damage.png)

**T3, on foot in the bridge after a hit.** The fire is in the hold with Cole on it; the bridge
vents with nobody free to patch it; a short has cut power to the bridge and Ada resets it, her
progress on its tag. No warning, three cautions in their short forms. On deck the deck is the
schematic, so Z5 is empty and FLT, WPN and SNS are hidden; the console prompt names the weapons
console once, then E OPEN and V SIT, so the hint line doesn't repeat them. The sponsor's payday
is a news slip under the delivery.

## Round 5 critique

Round 8 of the spec took all three round 4 notes: the scope's − and + are back, the 5 km step's
reason says fights reach 5 km, and there is one lead reticle, for the selected gun that needs the
most lead (all guns in hand are selected by default, and clicking WPN's rows picks fewer). From its
own check it gives the gunnery card's closest approach as 1:15 and the helm line as an approach to
2,000 m, which fits a closing range. Both are drawn.

What works:

- The at-rest screen is quiet, and the ten-lens panel is the most Nasa-punk element of the three
  variations: dark when well, and it says which system at a glance.
- The band has room at the spec's 640: three short-form cautions and the reserved `+n` slot fit
  without a caution ever moving, and the warning carries only the count, the time and the key.
- At 5 km the scope holds T2's fight in its outer half, with the harpoons clear of the own-ship
  mark, and its caps let the captain step out to see the freighter on the rim.
- The deck tags answer "is anyone on it?" where the hazard is.
- T2's numbers agree with each other: range, closing, the closest approach, the reticle's lead and
  the helm's order all describe one pass.

Problem in the spec, sent to the designer (sample text only):

- **One ship, two names.** The gunnery wireframe calls the target Raider 'Vex' on the card, its
  label and the contract, but "Raider" on the helm line and in the flight readout's frame. The
  word list gives each thing one name, and with two raiders near "approaching Raider" would not
  say which. Drawn with the whole name: `APPROACHING RAIDER 'VEX' TO 2 000 M` fits a 380-wide strip
  at 12.

Found in my own screens and fixed this round:

- **T2's helm strip** was 320 wide, which held `HOLDING HEADING` but not the round 8 order with
  the target's whole name. It spans the 404 between SHIP and WEAPONS now, 12 clear of each.

Resolved without a change:

- **The caution cells stay sized to their text.** The ten-lens panel is the fixed hardware that
  says which system; the band is a message list, like an EICAS, where each line is as long as its
  words. Fixed thirds would waste the 640 on short cautions and cut long ones.

**Converged.** No meaningful criticism remains for Task-driven × Nasa-punk beyond the sample-text
note above, which changes nothing in these screens if the spec takes it.

## Deviations from the spec

- **Warp reads `1×`** with the multiplication sign, where the spec writes `1x`.
- **Countdowns are M:SS** (`IMPACT 0:03`, `CPA 0.6 KM  T−1:15`), where the spec writes seconds.
- **Thousands take a thin space**, the style guide's rule, where the spec writes a comma.

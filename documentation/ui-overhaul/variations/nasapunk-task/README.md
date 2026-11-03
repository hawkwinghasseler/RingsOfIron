# Nasa-punk × Task-driven

The task-driven functional spec (`functional-task.md`, round 7 when drawn, on branch
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
| HLM helm status (Z8) | A mode strip: who has the helm, and the order in an FMA box (green while it flies). |
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
to pass 0.6 km off in 1:15, and Bea holds the heading with no order standing. Two harpoons
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

## Round 4 critique

Rounds 6 and 7 of the spec answered every round 3 note: impact times count the missile's burn, so
MISSILE's 20 s warning means 20 s; the automatic range holds every inbound missile and the locked
target, with a new 5 km step; the lamp is SHIELD; point defense is spelled as the game spells it.
Round 7 also took the bearing out of the missile alert, since direction is drawn in the world.

What works:

- The at-rest screen is quiet, and the ten-lens panel is the most Nasa-punk element of the three
  variations: dark when well, and it says which system at a glance.
- The band has room at the spec's 640: three short-form cautions and the reserved `+n` slot fit
  without a caution ever moving, and the warning now carries only the count, the time and the key.
- At 5 km the scope holds T2's fight in its outer half, with the harpoons clear of the own-ship
  mark.
- The deck tags answer "is anyone on it?" where the hazard is.

Problems in the spec, found while drawing round 7 and sent to the designer:

- **The scope's − and + went missing.** Rounds 5 and 6 ended the range rule with "The minus and
  plus buttons override it until the post changes"; round 7's new sample sentence replaced it, and
  the change log doesn't say the buttons were cut. The game's scope has them today, either side of
  its range (`RadarScope`). Drawn with them.
- **"The corvette's 4 km reach."** Round 6 gives that as the 5 km step's reason, from my own round
  3 note, which was wrong: the coilgun reaches 5 km (`coilgun_mk1`, `max_range = 5000`), and a
  kiting ship holds out to 4.5 km (`Tactics.KITE_RANGE`). The step is right; the reason should say
  fights reach 5 km.
- **Two lead reticles would sit on top of each other.** OVL gives one reticle per distinct muzzle
  speed, at most two. In this game's fights the lead is tens of metres (below), so at fight zooms
  each reticle is within a few units of the target, inside its lock brackets: a coilgun's and a
  heavy cannon's would be two rings 3 units apart on one target. One reticle, for the selected
  weapon WPN's rows already pick, reads; two don't.

Found in my own screens and fixed this round:

- **The lead reticle was 490 m off Vex**, which at the coilgun's 1.05 s needs Vex crossing at
  460 m/s; the target card has it closing at 41 with a pass at 0.6 km, 42 m/s in all. The game's
  pilots match your velocity and close at 80 m/s at most (140 backing off when kiting), with
  25 m/s of drift (`NpcCombatComponent`), so the lead is tens of metres and the reticle sits on
  the target. It now rings Vex inside its lock brackets.
- **T2's helm held a position off Vex**, which would keep the range the target card shows closing.
  No order stands now, and Bea holds the heading, as on T3 (`HelmOrderComponent.Mode.NONE`).
- **T2's closest approach read 1:12**; 3.2 km closing 41 m/s with 0.6 km at closest is 1:15 away.
- **The scope had no − and +**, and T2's range read `5 KM AUTO` where T1's, also automatic, read
  `5 KM`. Both scopes are one DU now, the range between its caps and no AUTO word: the automatic
  range is the normal state, and a dark cockpit names only what isn't normal.
- WPN's DECOY row stood apart under a rule; it is the table's last row, worked by hand like the
  launcher above it.
- T3's payday read in capitals with PAYDAY as its tag. News is prose under its source's tag, so it
  is a SPONSOR slip in sentence case now.
- SVG folded the double space in `CPA 0.6 KM  T−1:15`; text keeps its spaces now.

Resolved without a change:

- **The caution cells stay sized to their text.** The ten-lens panel is the fixed hardware that
  says which system; the band is a message list, like an EICAS, where each line is as long as its
  words. Fixed thirds would waste the 640 on short cautions and cut long ones.

Still wrong: nothing in these three screens that the HUD can answer. Round 5 checks the spec's
answers to the notes above.

## Deviations from the spec

- **Warp reads `1×`** with the multiplication sign, where the spec writes `1x`.
- **Countdowns are M:SS** (`IMPACT 0:03`, `CPA 0.6 KM  T−1:15`), where the spec writes seconds.
- **Thousands take a thin space**, the style guide's rule, where the spec writes a comma.

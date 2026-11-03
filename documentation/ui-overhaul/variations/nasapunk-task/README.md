# Nasa-punk × Task-driven

The task-driven functional spec (`functional-task.md`, round 5 when drawn, on branch
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
| ALR alert band (Z2) | One warning as a red-framed glass: a red lens, the number that matters large, the action on a key cap. Under it the caution row: amber cells sized to their text, filling from the left, each naming who handles it, and the last 44 units reserved for `+n`, so no caution moves when it lights. |
| OBJ objectives, NTF news (Z3) | Slips with a kapton tab (magenta for the navigation target's row); news on comm-loop slips. |
| SCH ship schematic (Z5) | A synoptic plan DU: hazards hatched in their room by severity, crew as dots (amber when hurt), the captain ringed. No text, as the spec asks. |
| TGT target card (Z6) | A DU: name and class, FIRING ON YOU lit, the hull estimate as four cells and a word, RNG and CLOSING at body size, CPA, the contract in magenta. |
| FLT flight instrument (Z8) | A DU led by an FMA-style mode box (CRUISE); the frame's reference and the speed; ΔV on a bar with the home notch; MAIN and RCS lamps. |
| HLM helm status (Z8) | A mode strip: who has the helm, and the order in an FMA box (green while it flies). |
| WPN weapons (Z7) | A DU: one row per mount (where, what, who works it, state, time of flight), then the decoy. |
| SNS scope (Z7 corner) | A scope DU; range automatic. |
| Interaction prompt | A bezel plate: the object's name once, then each key with its verb (`WEAPONS CONSOLE  E OPEN  V SIT`). |
| HNT hints (Z9) | Key caps with their verbs. |

## Screens

![Helm at rest](t1-helm-rest.png)

**T1, helm at rest near Highport.** The spec's at-rest test: the clock, nine dark lamps and a
blanking plate, FLT in CRUISE in Highport's frame, the scope, and four keys and F1 on the hint line. Contacts
are glyphs only, since nothing is targeted, and Highport carries its name.

![Gunnery in combat](t2-gunnery-combat.png)

**T2, the weapons console in a fight with the raider Vex.** Bea holds the ship 3.2 km off it. Two
harpoons inbound a second apart, the first at 0:03 and 1.1 km out, the second 1.4 km out (the one
warning, with F for a decoy), each drawn on its predicted path to the hull; a fire in the hold with
Ada on it and Cole wounded with no medic free (two cautions). WPN as the starting corvette really
carries its guns: the coilgun and the laser in hand (the laser out of range at its 3 km), the two
autocannons on point defense, the harpoon launcher at 3 of 4. One lead reticle, for the coilgun;
the point-defense ring at 4 km. SCH shows, because FIRE and CREW are lit.

![Deck, damage control](t3-deck-damage.png)

**T3, on foot in the bridge after a hit.** The fire is in the hold with Cole on it; the bridge
vents with nobody free to patch it; a short has cut power to the bridge and Ada resets it, her
progress on its tag. No warning, three cautions in their short forms. On deck the deck is the
schematic, so Z5 is empty and FLT, WPN and SNS are hidden; the console prompt names the weapons
console once, then E OPEN and V SIT, so the hint line doesn't repeat them.

## Round 3 critique

What works:

- The at-rest screen is quiet, and the ten-lens panel is the most Nasa-punk element of the three
  variations: dark when well, and it says which system at a glance.
- The band now has room to breathe at the spec's 640: three short-form cautions and the reserved
  `+n` slot fit without a caution ever moving.
- The deck tags answer "is anyone on it?" where the hazard is.

Problems in the spec, found while drawing and sent to the designer:

- **What the impact time is.** MISSILE's 20 s split and every impact countdown read the game's
  estimate, `CombatThreats`: closest approach at the present relative speed. A harpoon doesn't
  burn flat out; it closes at 30 m/s² (`MissileDef.closingBurn`, which `weapons.toml` leaves at
  that for the harpoon) after a 0.6 s dark start, so the estimate runs long and falls faster than
  the clock while it burns. From 3.2 km a harpoon lands 13 to 15 s after launch; from 7.7 km about
  22 s, and the estimate reads 20 s with 12 s left and 10 s with 8 s left; it is near the truth
  only in the last 5 s. So the warning at 20 s fires with 10 to 14 s left, and the full text's
  "impact 14 s" and the short form's "38 s" are both readings a harpoon gives. The spec should say
  the time is an estimate that runs fast, or ask for one with the burn in it (new: with closing
  speed v, distance d and closing burn a, t = (−v + √(v² + 2ad)) / a).
- **SNS's automatic range ignores missiles.** The rule holds the nearest hostile or the Lock, so in
  T2 (Vex at 3.2 km) it picks 10 km, and both harpoons, 1.1 and 1.4 km out, sit within 12 units of
  the own-ship mark. While a missile is inbound the range could hold the nearest missile (2 km
  here, with Vex as a rim tick at its bearing). The game's steps also jump from 2 to 10 km, while
  the corvette's reach ends at 4 km (the laser's 3, point defense's 4); a 5 km step would be new. T2 draws the rule as written.
- **SHLD and SHIELD.** The lamp is SHLD while the alert id and the prose say shield. One name for
  one thing: SHIELD fits a 49-unit lens at 12 (41 units), so Nasa-punk writes it whole.
- **"Point defence"** in the prose, where the game's own text says "point defense" in every file
  that names it. A small thing, but the game's word is the one the player will read.

Found in my own screens and fixed this round:

- T2 put the harpoons 3.5 km out at 0:03, from a flight time worked out for a harpoon burning flat
  out, which it doesn't. They are now where 0:03 puts them, and each
  track runs from the missile to the hull, as the game draws a predicted path.
- T1's and T2's scopes didn't place their contacts where the world view does (Vex was at 5.1 km on
  the scope against 3.2 km in the world). Every contact now sits at its world offset over the
  10 km rim.

What is still wrong in these screens, for round 4:

- The caution cells are sized to their text, so their left edges move between screens (T2's two,
  T3's three). That is allowed, but fixed thirds would let the eye learn where the second caution
  sits. Worth trying against the 640.
- WPN's DECOY row stands alone at the foot of the DU; it could join the launcher's row, since both
  are what the captain throws.

## Deviations from the spec

- **Warp reads `1×`** with the multiplication sign, where the spec writes `1x`.
- **Countdowns are M:SS** (`IMPACT 0:03`, `CPA 0.6 KM T−1:12`), where the spec writes seconds.
- **The lamp is SHIELD**, not SHLD (see above).
- **Thousands take a thin space**, the style guide's rule, where the spec writes a comma.

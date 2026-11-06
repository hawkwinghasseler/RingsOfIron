# Nasa-punk × Task-driven

The task-driven functional spec (`functional-task.md`, round 9 when drawn, on branch
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
to pass 0.6 km off in 1:15, and Bea holds the heading with no order: the launch dropped any order
but hold position and match velocity. Warp is held by the fire aboard. Two harpoons
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

### The windows the overhaul skipped

The shell's pages are in [nasapunk-shell](../nasapunk-shell/README.md); these are the spec's own
panels, drawn after the first rounds.

![The map, a transfer proposed](t4-map.png)

**T4, MAP from any post (M), near Highport on day 3.** The player has asked for a transfer to
Shackleton Ice, where the held food delivery goes, and the computer has proposed one: two burns,
3 912 m/s of the 7 007 left. Not yet accepted, it is a modification, so the path and its burns are
dashed white, PLAN's title reads MOD and EXEC's bar is lit: Enter (EXEC) makes it the planned path,
solid magenta, and Backspace (ERASE) drops it. It arrives 22 h 40 m after the contract's
deadline, so ARRIVES carries an amber LATE flag and the timeline puts the deadline before the
arrival. The orbit's numbers live here and nowhere else, under the plot's title. The numbers are
the game's: Shackleton Ice orbits the Moon 100 km up, and the Moon, 384 400 km out, moves 50
degrees in the 3.8 days the transfer takes.

![Boarding, taken](t5-boarding.png)

**T5, BRD in its third phase: taken (section 7).** The party took the raider Vex and went home,
and the salvage is under way. The panel runs down the right side so the deck stays in sight, and
the raider lies the way the game docks two ships, half a turn round over the corvette's port side.
On its deck the workers carry their work tags (Ada stripping the laser, Cole carrying the ore
across the collar), and the parts marked to strip carry cyan outlines. The salvage list is the
game's, dearest first, priced at a twentieth of each part's price. A mark box sits on each part
that can be marked, filled cyan once it is; the engines hang outside the hull and come off only on
EVA, so they have none and say so. Enter marks the cursor's row, and the scratchpad says what that
will do. The prize sells for 21 000 CR whole and 8 400 once a part is off, the second line in
amber, since Ada's laser is the first part. Prisoners are shown against the free berths, and the
cargo with the hold after all of it.

![Station terminal, contracts](t6-terminal.png)

**T6, TRM on its CONTRACTS tab, docked at Highport.** The task spec's frame: CHR and ALR stay
above, the header row says where you are (the station, its owner, your standing, credits, the
hold and contracts held), the tabs run under it on their keys (J, T, Y and I, the game's), and the
body is the list on the left with the selected offer's terms on the right. Highport sells no ships,
so there is no SHIPYARD tab, and the row says why. The offers are the game's, sorted by pay with
operations first: a bounty pays 7 500 and +6 over 3 days, an escort 1 500 and +4, a patrol 1 000
and +3, and a delivery its fee, 15% of the cargo's value and at least 500, on top of the agreed
price for the cargo. Clear the Lanes waits for the rank of lieutenant. REACH, the spec's addition,
is the travel time and delta-v from here. The delivery to Shackleton Ice is at the Moon, so it is
due in 8 days, and its 3.8-day transfer leaves 4 days spare. Its terms show the food bought here,
the fee and the price paid there, what that clears, half the standing lost on failure, and the
route and the hold after it. TAKE (Enter) and SHOW ROUTE (M) are keys under the glass.

## Round 6 critique

Round 8 of the spec took all three round 4 notes: the scope's − and + are back, the 5 km step's
reason says fights reach 5 km, and there is one lead reticle, for the selected gun that needs the
most lead (all guns in hand are selected by default, and clicking WPN's rows picks fewer). Round 9
took my round 5 note, so a ship is named in full wherever it is named, and corrected two samples
from the designer's own review against the code. A shot at the ship or a missile launched at it
drops every helm order but hold position and match velocity (`HelmOrders.threat`), so T2's helm
holds the heading. No warp guard holds for hostiles, so T2's warp is held by the fire aboard, the
game's own reason. Both are drawn.

What works:

- The at-rest screen is quiet, and the ten-lens panel is the most Nasa-punk element of the three
  variations: dark when well, and it says which system at a glance.
- The band has room at the spec's 640: three short-form cautions and the reserved `+n` slot fit
  without a caution ever moving, and the warning carries only the count, the time and the key.
- At 5 km the scope holds T2's fight in its outer half, with the harpoons clear of the own-ship
  mark, and its caps let the captain step out to see the freighter on the rim.
- The deck tags answer "is anyone on it?" where the hazard is.
- T2's numbers agree with each other and with the game: range, closing, the closest approach, the
  reticle's lead, the helm under fire and the warp hold all describe one moment.

Problems in the spec: none found this round. Round 9 draws as written, with the one deviation
below on the scope's range.

Found in my own screens and fixed this round: T2's helm and its warp hold, to round 9 (above).
The helm strip keeps its width across the gap between SHIP and WEAPONS, 12 clear of each, so an
order with a whole contact name still fits when one stands.

Resolved without a change:

- **The caution cells stay sized to their text.** The ten-lens panel is the fixed hardware that
  says which system; the band is a message list, like an EICAS, where each line is as long as its
  words. Fixed thirds would waste the 640 on short cautions and cut long ones.

**Converged.** No meaningful criticism remains for Task-driven × Nasa-punk.

## Deviations from the spec

- **Warp reads `1×`** with the multiplication sign, where the spec writes `1x`.
- **Countdowns are M:SS** (`IMPACT 0:03`, `CPA 0.6 KM  T−1:15`), where the spec writes seconds.
- **Thousands take a thin space**, the style guide's rule, where the spec writes a comma.
- **The scope's range has no AUTO word** (`5 KM`, where the spec writes `5 km (auto)`). Automatic
  is the normal state, and a dark cockpit names only what isn't normal. A range the captain has set
  with − or + reads in cyan, the style's colour for a commanded value, until the post changes.

# Nasa-punk × Task-driven

The task-driven functional spec (`functional-task.md`, round 1, on branch
`claude/project-thread-nfiuf0`) skinned in the Nasa-punk style
(`../../nasapunk/style-nasapunk.md`). Mockups are 1280 x 800 UI units drawn at 2x; sources are
the HTML files here, using `task.js` and the shared kit in `../../nasapunk/mockups/`. Render with
`node nasapunk/mockups/render.js variations/nasapunk-task` from `documentation/ui-overhaul`.

Round history is in [`../nasapunk-round-log.md`](../nasapunk-round-log.md).

## How the style maps the spec

The spec fixes nine zones (section 5); the style keeps them and fills each with its hardware.

| Spec widget (zone) | Nasa-punk container |
|---|---|
| CHR chronometer (Z1) | A small clock block: day and time large; time warp and, when warp is held, why. |
| ANN annunciator (Z4) | A ten-lens caution and warning panel in two rows of five. Unlit when well (the dark cockpit); a lamp lit for a warning also gets a red ring, so the level reads without colour. |
| PER personal (Z4) | The suit's chest display (DCM): condition, suit and its air, the air where you stand. |
| ALR alert band (Z2) | One warning as a red-framed glass: a red lens, the number that matters large, the action on a key cap. Cautions as amber lens strips, two to a row, each naming who handles it. |
| OBJ objectives, NTF news (Z3) | Slips with a kapton tab (magenta for the navigation target's row); news on comm-loop slips. |
| SCH ship schematic (Z5) | A synoptic plan DU: hazards hatched in their room by severity, crew as dots (amber when hurt), the captain ringed, the hit's flash on the hull. No text, as the spec asks. |
| TGT target card (Z6) | A DU: name and class, FIRING ON YOU boxed, the hull estimate as four cells and a word, range and closing at body size, closest approach, the contract in magenta. |
| FLT flight instrument (Z8) | A DU led by an FMA-style mode box (CRUISE); the frame and the speed in it; delta-v on a bar with the home notch; ASSIST, MAIN and RCS lamps. |
| HLM helm status (Z8) | A mode strip: who has the helm, and the order in an FMA box (green while it flies). |
| WPN weapons (Z7) | A DU: one row per mount (where, what, who works it, state, time of flight), then the launcher, decoys and shields with heat and a four-sector ring. |
| SNS scope (Z7 corner) | A scope DU; range automatic. |
| HNT hints (Z9) | Key caps with their verbs. |
| OVL world overlays | HUD-layer symbology with halos. On deck, hazard tags are glass slips on leaders and the prompt is a bezel plate (the style guide's deck exception). |

## Screens

![Helm at rest](t1-helm-rest.png)

**T1, helm at rest near Highport.** The spec's at-rest test: the clock, ten dark lamps, FLT in
CRUISE, the scope, four keys. Contacts are glyphs only, since nothing is targeted. About ten lines
of text.

![Gunnery in combat](t2-gunnery-combat.png)

**T2, weapons console in a fight with the raider Vex.** Ada holds 2 km off it. Two missiles
inbound (the one warning, F for a decoy), a fire in the galley and Ivo wounded (two cautions).
WPN as the game's Patrol Corvette really carries its guns: the coilgun and the laser in hand, the
two autocannons on point defence by themselves, the harpoon launcher at 3 of 4. One lead reticle,
for the coilgun. The point-defence ring at 4 km. SCH shows, because FIRE and CREW are lit.

![Deck, damage control](t3-deck-damage.png)

**T3, on foot after a hit.** The camera follows the captain to the life-support console. A short
by the galley door threw sparks and lit the galley: Bo fights the fire, Ada resets the short (its
progress on her tag). Cargo 1 vents out of sight to the left, so an edge arrow points at it, and the
air here is thin. No warning, three cautions. On deck the deck is the schematic: Z5 is empty, and
FLT, WPN and SNS are hidden.

## Round 1 critique

What works:

- The at-rest screen really is quiet, and the ten-lens panel is the most Nasa-punk element of the
  three variations: a status board that is dark when well and says which system at a glance.
- One alert band with named actions reads fast, and the band's cautions agree with the lamps.
- The deck tags answer "is anyone on it?" where the hazard is.

What was wrong in the spec, found while drawing and sent to the designer (14 notes):

1. WPN's rows don't match the game's corvette: the autocannons are point defence, automatic at
   the console, and there is no separate PD mount; the laser is aft to starboard with a 3 km
   reach; the harpoon magazine is 4; the coilgun's time of flight at 3.2 km is about 1.1 s.
2. So the console shows one lead reticle on this ship, the coilgun's.
3. Decoys have no count (unlimited, 3 s cooldown).
4. POWER on "the store below half" would flicker: the store holds 2 s and the laser empties it on
   every pulse. Light it for shorts, stuns, trips and unpowered systems; show store dips as a
   gun's "charging". The gunnery wireframe's "store empty, guns starved" caution also breaks the
   spec's rule against cautions for what WPN shows.
5. The gunnery wireframe has no SCH, though FIRE and POWER are lit.
6. "Reactor shorted" on a one-reactor ship is the NO POWER warning, not a caution.
7. A crew reset takes 5 s, so "reset 6 s" can't happen; crew already show job progress.
8. The deck wireframe lights AIR and points at a breach but has no VENTING caution.
9. Rooms have no names in the code; the spec needs the naming rule the other two adopted.
10. "ORBIT frame, 0.0 m/s" near Highport: define "near" and pick the station frame (0.0) or the
    orbit frame (about 7.7 km/s, which the prograde marker needs).
11. The at-rest wireframe labels Highport and a freighter that its own label rule makes glyphs.
12. The at-rest test allows four keys; the wireframe shows five.
13. "Green within the safe speed" names a colour in a functional spec.
14. Z9 overlaps Z7; Z5 is twice as tall as the deck plan needs.

Deviations in these screens, each following from a note above:

- WPN rows, the one reticle, decoys without a count (notes 1 to 3).
- T2's second caution is CREW (Ivo wounded) rather than POWER "store empty" (note 4).
- T3's short is a conduit by the galley, a caution, not the reactor (note 6); its reset shows 3 s
  of 5 with a progress bar (note 7); the band holds VENTING (note 8).
- T1 shows the orbit frame at 7,672 m/s and four keys (notes 10, 12).
- WPN is 200 tall, ending at y 760 above the hint line; SCH is 146 tall (note 14, within the
  spec's rule that artists may resize zones).

What is still wrong in these screens, fixed in round 2:

- **Type below the floor.** The spec says nothing the player must read is under 12 units, and the
  style guide's labels are 11 to 12; WPN's and TGT's labels (RANGE, CLOSING, HULL, MSL, SHLD,
  HEAT) are 10 to 10.5. Raise them and refit the DUs.
- **Thousands separators** are commas; the guide says a thin space.
- **Room names** (GALLEY, CARGO 1) follow the kit, until the spec has a naming rule.
- **The shield ring** at 19 u radius barely shows the four sectors' differences; give it more room
  or mark the thin sector in amber.
- **T3's deck is the kit's stand-in deck**, not the game's real corvette interior; the room layout
  is illustrative.

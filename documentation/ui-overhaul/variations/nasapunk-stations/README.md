# Nasa-punk × Ship Stations

The Ship Stations functional spec (`functional-stations.md`, round 3 when drawn) skinned in the
Nasa-punk style (`../../nasapunk/style-nasapunk.md`). Mockups are 1280 x 800 UI units drawn at 2x;
sources are the HTML files here, using `stations.js` and the shared kit in
`../../nasapunk/mockups/`. Render with
`node nasapunk/mockups/render.js variations/nasapunk-stations` from `documentation/ui-overhaul`.

Round history is in [`../nasapunk-round-log.md`](../nasapunk-round-log.md).

## How the style maps the spec

| Spec element | Nasa-punk container |
|---|---|
| Bottom band: left MFD, station cluster, right MFD | Three DUs in one bezel row along the bottom (y 616). An MFD with nothing to say folds down to its rail of soft keys. |
| Station cluster (five fixed slots) | The centre DU. Each slot is an instrument row (pointer box, tape, bar), not a line of text; an empty slot goes dark in place. |
| Annunciator (top centre) | Two-line lens tiles, 94 x 36. When any tile is lit, MASTER WARN and MASTER CAUT lenses sit at its ends. |
| Ticker | Comm-loop callout slips under the annunciator: a loop tag, a speaker, the line. |
| Station board (bottom strip) | A hardware strip of six station tiles, then a chronometer glass, a state lamp and the condition lens; a condition stripe above it in Yellow and Red. |
| Prompt line | Quiet `dim` text above the band. |
| Console page (Engineering) | A console face standing as a side panel (480 wide, full height): tabs on soft keys, a status strip, a synoptic of the power network, instruments, the exceptions, a NEXT timeline, the response line in the footer. |
| Suit readout | The EMU chest display (DCM): light suit-grey bezel, small glass. |
| World labels and cues | HUD-layer symbology with halos: contact shapes, velocity markers, burn arrows, missile tracks, impact marks. |

## Screens

![Helm, cruising](s1-helm-cruise.png)

**S1, helm, cruising, condition Green.** Nothing is wrong, so the overlay is empty and both MFDs
are folded to their rails. The cluster's five slots: velocity (ORBIT frame box, 7.67 km/s,
MANUAL, ASSIST, thruster lenses), reference (Highport), next event (burn 1 in 12:04), delta-v
(pointer, plan bracket, home notch) and the trigger slot dark. The nav target gets a CCTV feed.

![Weapons console in a fight](s2-weapons-fight.png)

**S2, weapons console in a fight, condition Red.** Two missiles inbound (the MISSILE tile with its
countdown, both master lenses lit), fired upon, a shield trip. Sensors MFD left, the tactical
cluster with mount chips, defence MFD pinned right. One impact cross per missile on the side of
the hull it will hit.

![Reactor console during a fire](s3-reactor-fire.png)

**S3, on deck at the reactor console.** A fire in the galley with Okafor on it, cargo 2 venting
with nobody on it, two missiles inbound, and a short in the galley that has cut the starboard
turret off. The Engineering face stands beside the live deck: the synoptic shows where power stops
and why, output against rating, the store (62%, 0.8 s), only the systems that are not working with
cause and what clears each, and a NEXT timeline of what recovers by itself.

## Round 1 critique

What works:

- One bottom band carries the whole fast loop, and the condition lens plus the station board give
  the crew picture in one strip.
- The synoptic answers "why is the starboard turret dead" in one look: the dead conduit runs from
  the short to the turret.
- The dark cockpit holds: S1 shows no lit lens and no list.

What was wrong in the spec, found while drawing and sent to the designer (all adopted in the
spec's rounds 4 and 5):

- The power page assumed demand, priorities and brownout. The game routes power by circuits: a
  reactor's output fills a store of 2 s (`PowerRoutingSystem.STORE_SECONDS`), burst loads draw
  from it, and nothing browns out. S3 draws supply and the store instead.
- There is no galley subsystem (`SubsystemType`), so the galley can't be a load.
- A short recloses by itself after 45 s (`DamageStates.AUTO_RESET`), and stuns count down, so the
  exceptions list can say when each comes back.
- Decoys are unlimited on a cooldown (`PlayerGunnery.DECOY_COOLDOWN`), not counted.
- Rooms have no names in the code; the spec now names them by system or hull position.

What is still wrong in these screens, fixed in round 2:

- **S2's mounts don't match the ship.** The game's Patrol Corvette (`assets/data/ships.toml`)
  carries a coilgun in the nose, an autocannon each side, a harpoon launcher aft to port and a
  laser aft to starboard. The autocannons are point defence: at the weapons console they stay
  automatic (`PlayerGunnery.inHand`), so PORT and STBD should read AUTO, not YOU or CHEN, and there
  is no separate PD mount. The laser and launcher are in hand. `DECOY 6` is a count the game
  doesn't have.
- **S2's fire arcs** are 56° wedges, but the guns' arcs are 300°. The wedges claim a limit that
  isn't there: drop them, and say "out of arc" on the chip when it happens.
- **Type below the guide's floor.** Labels at 9.5 to 10.5 u are under the style's own 11 (scale
  labels) and 12 (labels) sizes. Raise them and refit the chips.
- **Thousands separators.** The guide says a thin space, never a comma; the screens use commas.
- **Spec rounds 4 and 5:** the status strip at y 16 with condition and state; the board's right
  end holds only the chronometer; battle lighting on deck in Red; the camera shifts 240 left while
  a panel is open; gun modes AT WILL, YOURS and TRIGGER; mount chips say "charging" while the store
  refills; NO SUIT in the suit readout; one impact mark per hull side with a count.

## Deviations from the spec

- The annunciator puts the master lenses at the ends of the tile row rather than on a separate
  strip, so the eye finds them where the tiles are.
- Console tabs are soft keys with their legends on the glass, per the style.

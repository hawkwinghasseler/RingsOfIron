# Nasa-punk × Ship Stations

The Ship Stations functional spec (`functional-stations.md`, round 13 when drawn; round 13's
blackout and tow-hint rules touch none of these three screens) skinned in the
Nasa-punk style (`../../nasapunk/style-nasapunk.md`), with its words from
[`../../nasapunk/abbrev.md`](../../nasapunk/abbrev.md). Mockups are 1280 x 800 UI units drawn at
2x; sources are the HTML files here, using `stations.js` and the shared kit in
`../../nasapunk/mockups/`. From `documentation/ui-overhaul`, render with
`node nasapunk/mockups/render.js variations/nasapunk-stations` and check with
`node nasapunk/mockups/check.js variations/nasapunk-stations` (text overlaps, crowding, text
against or across a frame, text cut by its glass, and the 12-unit floor).

Round history is in [`../nasapunk-round-log.md`](../nasapunk-round-log.md).

## How the style maps the spec

| Spec element | Nasa-punk container |
|---|---|
| Bottom band: left MFD, station cluster, right MFD | Three DUs in one bezel row along the bottom (y 616). An MFD with nothing to say folds down to its rail of soft keys. |
| MFD page keys | Soft keys under the glass. Page names keep their whole spelling, the same as on the console tabs. PIN and FOLD are short function keys and take a little over half a page key's room each. |
| Station cluster (five fixed slots) | The centre DU. Each slot is an instrument row (pointer box, tape, chip), not a line of text. A slot with nothing to say yet shows its lenses unlit, so it reads as reserved; a slot for a system the hull lacks is blank, with no legend, as hardware not fitted. |
| Annunciator (top centre) | Two-line lens tiles. When any tile is lit, MASTER WARN and MASTER CAUT lenses sit at the row's ends. |
| Status strip | One glass across the top, with the condition (GREEN, YELLOW, RED) as a lit block at its left. |
| Ticker | Comm-loop slips under the annunciator: the station tag, the speaker, and a line of at most 50 characters. |
| Station board (bottom strip) | A hardware strip of six station tiles (HELM, NAV, TAC, ENG, COMMS, CMD), then the day and the time. |
| Prompt line | Key caps with their verbs, quiet, above the band. |
| Console page (Engineering) | A console face standing as a side panel (480 wide, full height): tabs on soft keys, the reactor and store as pointer scales, a synoptic of the power network, the systems not working with cause and cure, a NEXT timeline, and the footer's soft keys. |
| Suit readout | The suit's own chest display (DCM): suit-grey bezel, small glass. |
| SENSORS range | The step at the page header's right with a − and a + cap either side of it, as the game's scope has its buttons today. |
| World labels and cues | HUD-layer symbology with halos: contact shapes, velocity markers, burn arrows carrying only their number, missile tracks, impact marks, the lead reticle, and a camera inset (OPT) on the target. On deck a crew member who speaks shows a speaking mark; the words are in the ticker. |

## Screens

![Helm, cruising](s1-helm-cruise.png)

**S1, helm, cruising, condition Green.** Nothing is wrong, so the overlay is empty and both MFDs
fold to their rails. The cluster's slots are velocity (the ORBIT frame, 7.67 km/s, MANUAL and
ASSIST, the MAIN and RCS lenses dark), reference (Highport with range, closing and its closest
approach, 140 m in 5:03), the next event (burn 1, T−12:04, which the world's burn arrow marks with
its number alone), the trigger slot with its GUNS, MSL and DECOY lenses unlit, and ΔV (the pointer,
the plan's bracket and the notch for home). The CMD tile names nobody: Command is the captain's
slate. The nav target has a camera inset.

![Weapons console in a fight](s2-weapons-fight.png)

**S2, the weapons console in walkthrough E, condition Red.** Two harpoons inbound from port, 2.4
and 2.5 km out on their predicted paths, landing in 8 and 9 s with their burn counted: the MISSILE
tile with its count and the first impact (`2  0:08`), both master lenses lit, HOSTILE FIRING. The
cluster lists the guns in hand by position, each with its own hit chance (NOSE with Cole at it,
charging; AFT STBD burning at 60%), the point-defense autocannons on one PD chip, the launcher with
its four harpoons and the salvo warning, and the blank Shields slot. SENSORS is open on the left
MFD at 5 km, the smallest step that holds the Raider and both harpoons, with its − and + caps;
DAMAGE is on the right. The coilgun's lead reticle rings the Raider: at 2.8 km its round takes
0.92 s, and the Raider moves 39 m in that time.

![Reactor console during a fire](s3-reactor-fire.png)

**S3, walkthrough F: on deck at the reactor console, condition Yellow.** A short has cut off the
aft starboard gun, and its sparks lit a fire in the bridge, which Ada has called and is fighting;
her speaking mark is over her, and the newest line is at the ticker's foot. The SHIP line carries
no countdown, since a spoken number goes stale while it holds. The Engineering face stands beside
the live deck (the world shifted 240 left): the reactor at 2.6 of 3.0 MW and damaged, the store
full, the synoptic showing where power stops, only the systems not working with what clears each,
and a NEXT line for what comes back by itself.

## Round 4 critique

Round 12 of the spec answered every round 3 note: one name per page, F 8 s after the short, one
spelling (point defense), an impact time with the burn in it, and a 5 km step with the scope opening
on the smallest step that holds the target and every missile in Yellow and Red. Its round 10 took
the burn arrow's time, the CMD tile's occupancy and speech on deck.

What works:

- One bottom band carries the fast loop, and the board gives the crew picture in one strip.
- The synoptic answers "why is the aft starboard gun dead" in one look: the dead conduit runs from
  the short to the gun.
- One name per page holds across the band and the console: SENSORS, WEAPONS, DEFENSE, DAMAGE,
  POWER and ATMOS are the same words on S2's keys, S1's rails and S3's tabs, and now in the spec.
- The dark cockpit holds: S1 has no lit lens and no list, and its unlit trigger lenses say the
  slot is there without saying anything is wrong.
- At 5 km, E's fight fills the SENSORS scope's outer half instead of its middle fifth.

Problems in the spec, found while drawing round 12 and sent to the designer:

- **The NEXT line repeats its row.** F's POWER page lists "AFT STBD gun, no power, short, 37 s",
  then "NEXT AFT STBD gun back in 37 s": one countdown twice on one page. Each row already carries
  its own countdown, so NEXT always repeats one of them. Ordering the rows by when each comes back
  would make the first row the next one, and NEXT could go.
- **One state, two words.** At the weapons seat a shorted gun's chip reads "shorted" with its
  breaker's seconds (8.3, round 12), while the POWER page files the same gun as "no power" with
  "short" as the cause. Shorted is the better word on both.
- **A's closest approach.** Highport at 1.2 km closing 3.9 m/s with 140 m at closest is 5:03 away
  at constant velocity (t = closing × range / speed², with a speed of 3.93 m/s), not 2:10; 2:10
  would put the closest approach at about 910 m. Drawn at 5:03.
- **F's ticker order.** The wireframe lists Ada's line above the SHIP line, but the stack puts the
  newest at the bottom (7.2), and a short's sparks can light a flammable tile beside it
  (`DamageStates`), so if they lit this fire, Ada's call is the newer line. Drawn that way.

Found in my own screens and fixed this round:

- **The lead reticle was hundreds of metres off the target.** S2 drew it 505 m from the Raider,
  which at the coilgun's 0.92 s needs the Raider moving at 550 m/s against us; the cluster says
  closing 40. The game's pilots match your velocity and close at 80 m/s at most (140 backing off
  when kiting), with 25 m/s of drift (`NpcCombatComponent`), so in these fights the lead is tens of
  metres and the reticle sits on the target. It now rings the Raider inside its lock brackets,
  wide enough to leave glass round the target's shape.
- **SENSORS had no range control**, though range is one of the page's owned controls (9.2). It
  has − and + caps either side of the step now, as the game's scope does.
- SVG folded the double space between a count and a time, so `2  0:08` read as `20:08`. Text keeps
  its spaces now, on every screen.
- S1's dark trigger slot read as missing. Its three lenses are drawn unlit: an unlit lens is
  hardware waiting, a blank plate is hardware not fitted.
- S3's ticker had Ada's line on top; the newest line goes at the foot.

Resolved without a change:

- S2's first impact stays at the tile's size. The tile grid is fixed hardware, and with the
  double space kept, `2  0:08` reads at a glance; a larger number would break the row.

Still wrong: nothing in these three screens that the HUD can answer. Round 5 checks the spec's
answers to the notes above.

## Deviations from the spec

- **The annunciator puts the master lenses at the ends of the tile row** rather than on a strip of
  their own, so the eye finds them where the tiles are.
- **Console tabs are soft keys** with their legends on the glass, per the style.
- **Countdowns are M:SS throughout** (`0:08`, `RECLOSES IN 0:37`), as the word list has it, where
  the spec writes seconds ("37 s"). Prose keeps the spec's form.
- **A's closest approach is drawn at 5:03**, not 2:10 (see above).
- **F's ticker has Ada's line at the foot**, as the newest (see above).

# Nasa-punk × Ship Stations

The Ship Stations functional spec (`functional-stations.md`, round 9 when drawn) skinned in the
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
| Station cluster (five fixed slots) | The centre DU. Each slot is an instrument row (pointer box, tape, chip), not a line of text. A slot for a system the hull lacks is blank, with no legend. |
| Annunciator (top centre) | Two-line lens tiles. When any tile is lit, MASTER WARN and MASTER CAUT lenses sit at the row's ends. |
| Status strip | One glass across the top, with the condition (GREEN, YELLOW, RED) as a lit block at its left. |
| Ticker | Comm-loop slips under the annunciator: the station tag, the speaker, and a line of at most 50 characters. |
| Station board (bottom strip) | A hardware strip of six station tiles (HELM, NAV, TAC, ENG, COMMS, CMD), then the day and the time. |
| Prompt line | Key caps with their verbs, quiet, above the band. |
| Console page (Engineering) | A console face standing as a side panel (480 wide, full height): tabs on soft keys, the reactor and store as pointer scales, a synoptic of the power network, the systems not working with cause and cure, a NEXT timeline, and the footer's soft keys. |
| Suit readout | The suit's own chest display (DCM): suit-grey bezel, small glass. |
| World labels and cues | HUD-layer symbology with halos: contact shapes, velocity markers, burn arrows, missile tracks, impact marks, and a camera inset (OPT) on the target. |

## Screens

![Helm, cruising](s1-helm-cruise.png)

**S1, helm, cruising, condition Green.** Nothing is wrong, so the overlay is empty and both MFDs
fold to their rails. The cluster's slots are velocity (the ORBIT frame, 7.67 km/s, MANUAL and
ASSIST, the MAIN and RCS lenses dark), reference (Highport with range, closing and CPA), the next
event (burn 1, T−12:04), the trigger slot dark, and ΔV (the pointer, the plan's bracket and the
notch for home). The nav target has a camera inset.

![Weapons console in a fight](s2-weapons-fight.png)

**S2, the weapons console in walkthrough E, condition Red.** Two harpoons inbound from port, 2.0
and 2.2 km out on their predicted paths: the MISSILE tile with its count and the first impact
(0:09, as the spec's catalogue has it), both master lenses lit, HOSTILE FIRING. The
cluster lists the guns in hand by position, each with its own hit chance (NOSE with Cole at it,
charging; AFT STBD burning at 60%), the point-defense autocannons on one PD chip, the launcher with
its four harpoons and the salvo warning, and the blank Shields slot. SENSORS is open on the left
MFD, DAMAGE on the right, both keyed by their whole names.

![Reactor console during a fire](s3-reactor-fire.png)

**S3, walkthrough F: on deck at the reactor console, condition Yellow.** A fire in the bridge with
Ada on it, and a short that has cut off the aft starboard gun. The Engineering face stands beside
the live deck (the world shifted 240 left): the reactor at 2.6 of 3.0 MW and damaged, the store
full, the synoptic showing where power stops, only the systems not working with what clears each,
and a NEXT line for what comes back by itself.

## Round 3 critique

What works:

- One bottom band carries the fast loop, and the board gives the crew picture in one strip.
- The synoptic answers "why is the aft starboard gun dead" in one look: the dead conduit runs from
  the short to the gun.
- One name per page now holds across the band and the console: SENSORS, WEAPONS, DEFENSE, DAMAGE,
  POWER and ATMOS are the same words on S2's keys, S1's rails and S3's tabs.
- The dark cockpit holds: S1 has no lit lens and no list.

Problems in the spec, found while drawing and sent to the designer:

- **Two names per page.** The wireframes key the MFD pages SNS, WPN, DEF, DMG, PWR and ATM while the
  text, the page table and the console's sub-tabs spell them out (and the sub-tab is "Atmosphere"
  where the page is ATMOS). Each page should have one name. Nasa-punk draws the whole words, which
  fit once PIN and FOLD take less room.
- **F's timing.** "Back in 31 s" on a 45 s breaker (`DamageStates.AUTO_RESET`) puts the short 14 s
  back, but a caution line holds 10 s (7.2), so the SHIP line would have faded. A store emptied
  14 s ago refills in 2 s, so it would be full, not "62%, full in 0.8 s", unless something draws
  it, and nothing fires in F. Drawn 8 s after the short: 37 s left, the store full, the line up.
- **"DEFENSE" and "point defence".** The page is spelled the American way and the prose the
  British way. The game's own text says "point defense".
- **What the impact time is.** The change log says "missiles arrive in seconds, not in 37 s". A
  harpoon closes at 30 m/s² (`MissileDef.closingBurn`; `weapons.toml` leaves the harpoon's at
  that) after a 0.6 s dark start, so from E's 2.8 km it lands about 12 s after launch. The time the
  game shows (`CombatThreats`) is closest approach at the present relative speed, which runs long
  while the harpoon burns: about 47 s at launch, 37 s a second later, 20 s with 9 s left, and near
  the truth only in the last 5 s. So 37 s is a reading the game gives, and a countdown drawn from
  it falls faster than the clock. Seconds are right for the samples; the spec should say the time
  is an estimate, or ask for one with the burn in it (new).
- **The scope's steps.** `RadarScope` jumps from 2 km to 10 km, and the corvette's reach ends at
  4 km (the laser's 3, point defense's 4). On SENSORS at 10 km, E's Raider and both harpoons sit
  within 12 units of the centre of a 40-unit scope; at 2 km all three are past the rim. A 5 km
  step would be new data, and the one that fits this fight.

Found in my own screens and fixed this round:

- S2's harpoons read 0:03 and 0:04 at 2.2 and 2.6 km, which no harpoon can do (see above). They
  read 0:09 and 0:12 at 2.0 and 2.2 km now, a second apart, each track drawn from the missile on.
- S2's SENSORS scope put the Raider at 6 km against the 2.8 km in the world and the list. Every
  contact now sits at its world offset over the 10 km rim.

What is still wrong in these screens, for round 4:

- The impact time on S2's MISSILE tile and in the SENSORS list is the only number in the overlay
  and could be larger: it is the number that matters most in E.
- S1's empty fourth slot (trigger, dark in Green) reads as missing rather than reserved. A dark
  slot label would contradict the blank-slot rule, so the slot needs its own quiet mark.
- The spec's round 10 landed after these were drawn: S1's burn cue keeps only its number (the
  time is the Next slot's), the CMD tile shows no occupancy, and a speaking mark replaces speech
  on deck.

## Deviations from the spec

- **The annunciator puts the master lenses at the ends of the tile row** rather than on a strip of
  their own, so the eye finds them where the tiles are.
- **Console tabs are soft keys** with their legends on the glass, per the style.
- **Countdowns are M:SS throughout** (`0:03`, `RECLOSES IN 0:37`), as the word list has it, where
  the spec writes seconds ("31 s"). Prose keeps the spec's form.
- **F is drawn 8 s after the short**, not 14 s (see above).

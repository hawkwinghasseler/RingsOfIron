# Cassette-futurism round log

Each functional design (Ship Stations, Context-driven, Task-driven) is combined with the Yard Standard style ([style-cassette.md](../style-cassette.md)), critiqued, and improved, round by round. Each round's critique goes into the variation's README; functional notes go to that spec's designer. A variation is finished when a round finds nothing meaningful left to fix.

| Variation | Spec version used | Round | Screens | Open criticism |
|---|---|---|---|---|
| [Ship Stations × Cassette](stations-cassette/README.md) | Stations round 9 | 2 | C helm, D approach, E weapons, B deck, G docked; kit sheets K, K2, K3 | 1 style, 9 functional |
| [Context-driven × Cassette](context-cassette/README.md) | Context v7 | 2 | 01 cruise, 03 approach, 04 combat, 05 fire control, 07 deck emergency, 08 docked, 12 orders menu, 13 boarding | 3 style, 6 functional |
| [Task-driven × Cassette](task-cassette/README.md) | Task round 9 | 2 | 01 helm at rest, 02 gunnery, 03 deck, 04 terminal, 05 map, 06 crisis, 07 FLT modes and lamp states, 08 ship's book, 09 relations and states | None in the stills (3 for the in-game build), 3 functional |

The screens the three specs share outside the flight deck (the main menu, pause, load and save, settings, the ship-lost screen and the mod-scripts question) are drawn once for all three, in [cassette/shell](../cassette/shell/README.md).

## Round 0: the style guide itself

The first pass at combining the guide with a 1280 × 800 spec showed problems in the guide. They were fixed before round 1:

- **Text was too small.** The guide's first mockup was drawn at 1920 px with 9 to 11 px legends, which is 7 to 8 units at UI scale 1. The specs set 12 for tags and 13 for body text. The type scale now starts at 12, and every variation is drawn with a kit (`cassette/kit.js`, grid section) that holds that floor.
- **Two kinds of tube can't share an MFD.** The guide had amber raster tubes for words and green vector tubes for geometry, but the specs' MFDs show a radar page or a text page in the same place. The tubes are now beam-penetration tubes, a 1970s air-traffic-control technology: one tube draws green geometry, amber words and red threats.
- **The annunciator mixed status and alarms.** The guide's mockup lit TGT LOCK and XPDR ON tiles in the caution panel, which breaks the dark-cockpit rule all three specs share. The annunciator now carries cautions and warnings only. Engaged states are lit on their own controls.
- **One deck for everything doesn't fit the specs.** The specs hide the band on deck and fold the MFDs in quiet flight. The deck is now built of pods: the cluster pod, MFD pods whose tubes sink into the deck when folded, and a sill for the station board. On deck there is no deck at all, only the overhead units and the forearm suit unit.

## Round 1: Ship Stations

Stations spec round 2. Four screens: helm in Green, weapons console in Red, the deck in an emergency, docked with the Station link. Critique and changes are in the [variation's README](stations-cassette/README.md#round-1-critique). Notes sent to the stations designer:

1. The band is drawn flush with the bottom edge (same 184 units). Is that acceptable as a style choice?
2. Rows "fold away" in the cluster; Cassette keeps them in place and darkens them. Could the spec allow "go dark in place"?
3. The same readout shows twice while seated: the nav target and target labels repeat the Reference and Target rows. The missile countdown shows three times.
4. The Reference row has three alternative contents but room for one. It needs a priority, or the pilot's trigger needs its own row.
5. The "no helm order" state has no label.
6. The docked berth is in two places: the cluster's Docked line and the DOCK page.
7. The station board has no place for the layered DOCKED and EMERG states.
8. Does the ticker stay put when the annunciator is empty?
9. With both MFDs folded the band is still as tall as the cluster (160 + board), so "the shift follows" changes nothing. Is a smaller shift intended?

## Round 1: Context-driven

Context spec version 2. Five screens: helm in cruise, the docking approach, helm in combat, the deck in an emergency, and the orders menu. The spec has no console band, so the hardware is a frame around the view: a header rail (P1), a coaming along the bottom edge, and slide rails on the side edges, with every widget a unit that hangs, slides or stands on that frame by its zone. Critique and changes are in the [variation's README](context-cassette/README.md#round-1-critique).

Two fixes to the shared kit came out of this round. They also apply to the stations variation:

- **Dim phosphor text was too faint.** It was 3.8:1 against the tube. The `p3-amber-dim` token is now `#B27B2C`, 5.4:1.
- **Tube scanlines struck through small text.** They are now a 2-unit pitch at lower opacity.

Notes sent to the context designer:

1. The spec's examples don't fit its 308 columns at its own 13-unit floor (the wireframes use 10.5): H2's ORBIT line, P2's PROPELLANT LOW, P1's autopilot line. Each text field needs a maximum length.
2. P2's "by time left" puts recovery timers (stun) above harm timers (bleed-out). Wireframe 07 doesn't follow the stated order either.
3. Wireframe 07's life support is on EMERGENCY with a caution light, but EMERGENCY O2 is a warning missing from P2.
4. "+n more" is clickable but 22 units tall.
5. An approach shows the time to contact twice, the closing speed twice and the distance three times.
6. Shield raised and trim show in both H4 and H7 at the helm in combat.
7. Decoys don't run out in the game (3 s cooldown).
8. The game's power model has no demand.
9. P1 at the helm by hand: empty field, or "Helm order: none" as in wireframe 12?
10. P3 and P4 both report an intercept.
11. H2's compact face: NAV and NEXT, or NEXT only?
12. The deck's master cue needs its own place when D1 is hidden.
13. Labels (legends, units, key names) have no size; can they be 12?

Plus where a real control surface helps: the orders menu (interlocked keys, guarded switches for the press-twice orders), the emergency card, H5's range keys, H1's frame pin, F3's trim, and D2's guarded switches for reactor and life support.

## Round 1: Task-driven

Task spec round 3. Seven sheets: the six reference screens the spec suggests (helm at rest, gunnery in combat, deck in damage control, the docked terminal, the map with a late plan, the crisis panel) and a sheet of FLT's five modes, the compact weapons strip and the lamp states. The spec changes the HUD by post, so each post is a **fit** of units in its own finish (yard beige for the helm, blue-grey for the guns, avocado for the captain's own kit), and four repeaters are the same everywhere: the master unit, the visor, the incoming rack and the key sill. Critique and changes are in the [variation's README](task-cassette/README.md#round-1-critique).

Checked against the game data this round: the corvette's two rooms and tile layout (for the deck view and the schematic), the starting crew (Ada, Bea, Cole), the faction names (Void Raiders, Selene Mining Combine), Shackleton Ice's yard and hiring, and the shield emitter's heat (it exists, so the spec is right to show it).

Notes sent to the task designer:

1. WARP repeats CHR's rate and next stop on the same screen.
2. BURN repeats HLM: HLM's countdown, and "flown by" when a helmsman flies.
3. The flight-modes wireframe shows "end Ap" in BURN and "closing" in CRUISE.
4. Section 8 and HNT disagree on whether the crisis keys repeat in the hint line.
5. An alert's or a prompt's key repeats in HNT ("F decoy" at gunnery; "E sit" on deck).
6. Three cautions get about 22 characters each at 13 units. They need a short form.
7. DEPARTURE's "crew aboard against berths" never reads ok on a ship with spare berths.
8. "Bo" in the FIRE samples isn't a crew name the game gives.
9. The gunnery wireframe's missile path repeats the band's impact time in the world.

## Round 2: Ship Stations

Stations spec round 9. Eight sheets: the four screens of round 1 redrawn, the docking approach (D), and three kit sheets that draw every element of the spec's kit (17.5) except the full-screen panel frame. Critique and changes are in the [variation's README](stations-cassette/README.md#round-2-critique).

The biggest fix was to the weapons cluster. The kit sheet had drawn point defence, the launcher and the shields at their own sizes, wider than their places on screen, and the shields slot in E could hold no shield readout. The cluster's foot is now a rail of three bays of fixed width (point defence, the launcher, shields), drawn by the same code on screen and on the kit sheet, with the decoy moved up beside the guns. A hull without an emitter shows the shields bay as a blanking plate with no legend, as round 9 asks.

Three fixes to the shared kit came out of this round. They also apply to the context and task variations:

- **Neutral ships were upright triangles**, the caution glyph. They are now notched darts pointing along their heading. Burns are double chevrons, and a missile is a dot with a leader and a chevron at its tip, so ◆ and ▲ stay with the lamps.
- **The PIN latch's surround** was aluminium, too bright beside the dark soft keys; it is now dark.
- **Printed legends must not change their words.** A state that changes goes on a lens, flaps or a drum, and a time on a drum after the state.

Notes sent to the stations designer:

1. The burn's arrow and the Next slot both carry its countdown at the helm.
2. 7.7's emergency text is 74 characters; the status strip holds about 44.
3. The DOCK page repeats the Ports field during an approach.
4. 7.2's clearance callout never plays at the helm (13.5).
5. The CMD tile's occupancy part is undefined.
6. The Market hides cargo the station doesn't trade (8 of the sample's 12 m³).
7. Wireframe G lists a Shipyard at Highport, which has none, and leaves out the Infirmary; its rows are out of catalog order.
8. The cluster holds the corvette's weapons; the frigate and destroyer carry more launchers and guns.
9. (Minor) A crew callout shows twice on deck, in the bubble and the ticker.

## Round 2: Context-driven

Context spec version 7 (built against version 6, with version 7's two changes taken in: "point defense" as the game spells it, and H7's missile count totalled across launchers). Eight screens: the five of round 1 redrawn, and fire control (05), docked (08) and boarding (13) drawn for the first time. Critique and changes are in the [variation's README](context-cassette/README.md#round-2-critique).

All five style points from round 1 are fixed: flush rail windows, headless bars, notched frame tags, lighter world labels and 14-unit thruster lamps. The render found thirteen more faults, fixed in the round. The biggest were the closing gauge, whose scale now spans the top half only so the FAST flag and the distance counter have the lower face, and F2's master cue, which covered two column heads and now stands on a glareshield bracket above the rack. Every number moved to a drum at the spec's 15-unit value size, which turned H4's text readings into instruments.

One fix to the shared kit's use came out of this round, and applies to the other two variations: on a printed flap face (YOU on yellow), the flaps' seam is drawn pale, behind the ink, since a dark seam reads as a strike-through.

Notes sent to the context designer:

1. Wireframe 03's sample can't happen: the corvette's port is S and Highport's port 6 is M, and at 820 m the safe speed is 12.8 m/s, so 6.2 isn't fast. Suggested: port 2 at 120 m, 6.2 against 4.9, T-19 s.
2. X1's "Shipyard 3 ships · 3 to hire" at Highport, which has no shipyard; hiring needs its own line.
3. F4's missile bearing has no stated reference (the task design dropped its bearing for this reason).
4. Does 12.4's 15-unit value size apply to numbers inside sentences?
5. H4's POWER line doesn't fit at 15 units beside a bar; the capacity could move to the hover.
6. F2 has no room for the master cue; the 40 units above the rack would hold it.

## Round 2: Task-driven

Task spec round 9 (drawn against round 8, with round 9's changes taken in). Nine sheets: the seven of round 1 redrawn, and the ship's book (08) and the relation set (09) drawn for the first time. Critique and changes are in the [variation's README](task-cassette/README.md#round-2-critique).

All of round 1's style points are fixed:

- Orders print their legends bright, and an unavailable order takes the barber strip and its reason.
- Burns are chevrons.
- The brown-out lights a BATT lamp.
- Every flap is checked against the game's longest names.

The render found more, fixed in the round. The biggest were the words still printed on legends though they change: the target plate's relation, FLT's frame kind, DOCK's clearance, the departure checks, MAP.2's plan kind, the crisis WAIT text and the sill's verbs. Each moved to flaps, drums, a tube or a fixed-legend lens. MAP.1's services became a gang of lenses. A struck ship's reason, `FIRING ON IT`, read as if the ship were firing, and now reads `HOLD YOUR FIRE`.

One fix to the shared kit came out of this round, and applies to the other two variations: **lit white lamps barely read on the cream face**, so every small lamp now has a dark inner bezel ring.

Nothing is left to fix in the stills. Three things are for the in-game build to settle, because a still can't show them: motion (flap and drum travel, the 3 s blink), the four faces as bitmap fonts at UI scale 1, and the cost of the tube's glow.

Notes sent to the task designer:

1. TRM's wireframe still shows a SHIPYARD tab at Highport, which sells no ships.
2. The DOCK sample's Highport port 6 is an M port; the corvette's port is S, and Highport's S ports are 1 and 2.
3. MAP.1's services for Shackleton Ice leave out its shipyard and hiring.

## Off the flight deck: the screens the overhaul skipped

The first rounds covered flight, the deck and docking, but not the screens around them: the map, the station's services, the ship's systems, boarding, the record, the controls reference, and the shell (the main menu, pause, load and save, settings, the crash screen and the mod-scripts question). The style guide's section 9 is rewritten to cover them. It says which surface each kind of screen becomes (a big console for a full-screen mode, a side cabinet for a side panel, the console's own faceplate for a deck console's panel), and how tabs, lists and tables, form controls, confirmations, questions, endings, paper, the map, the ship editor, the shell and the developer layer are built. Full-screen modes, side panels and the shell are swapped in through the shared framework hook for windows, the map and the shell's menus, and the Cassette builds will use it.

The shell is drawn first, since all three specs share it: eight screens and a kit sheet in [cassette/shell](../cassette/shell/README.md). Two small additions came out of drawing it, flagged as new in its README: a TWICE lens when a key is bound to two actions (the game doesn't check today), and an OVERWRITE lens when a save's name is taken.

Next: Context round 3 draws its full-screen modes (the map, the station, the ship's systems), then the Stations spec's panels and Task's boarding and console panels.


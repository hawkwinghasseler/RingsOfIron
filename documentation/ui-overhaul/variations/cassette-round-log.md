# Cassette-futurism round log

Each functional design (Ship Stations, Context-driven, Task-driven) is combined with the Yard Standard style ([style-cassette.md](../style-cassette.md)), critiqued, and improved, round by round. Each round's critique goes into the variation's README; functional notes go to that spec's designer. A variation is finished when a round finds nothing meaningful left to fix.

| Variation | Spec version used | Round | Screens | Open criticism |
|---|---|---|---|---|
| [Ship Stations × Cassette](stations-cassette/README.md) | Stations round 2 | 1 | C helm, E weapons, B deck, G docked | 5 style, 5 functional |
| [Context-driven × Cassette](context-cassette/README.md) | Context v2 | 1 | 01 cruise, 03 approach, 04 combat, 07 deck emergency, 12 orders menu | 6 style, 13 functional |
| Task-driven × Cassette | Task round 1 | – | – | – |

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

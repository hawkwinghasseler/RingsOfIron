# Cassette-futurism round log

Each functional design (Ship Stations, Context-driven, Task-driven) is combined with the Yard Standard style ([style-cassette.md](../style-cassette.md)), critiqued, and improved, round by round. Each round's critique goes into the variation's README; functional notes go to that spec's designer. A variation is finished when a round finds nothing meaningful left to fix.

| Variation | Spec version used | Round | Screens | Open criticism |
|---|---|---|---|---|
| [Ship Stations × Cassette](stations-cassette/README.md) | Stations round 2 | 1 | C helm, E weapons, B deck, G docked | 5 style, 5 functional |
| Context-driven × Cassette | Context v2 | – | – | – |
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

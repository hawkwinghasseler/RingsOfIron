# FILAMENT × Ship stations

The ship-stations functional design (`functional-stations.md`, round 5) built in the FILAMENT style ([`style-vfd.md`](../style-vfd.md)). The spec decides what each widget shows, where and when; this page records how FILAMENT builds it, what the style changes beyond the look, and the critique of each round.

Mockups are drawn by [`vfd/src/stations.js`](../vfd/src/stations.js) over real game captures and the game's own hull art; `vfd/src/export.sh` re-renders them.

## Screens

**C. Seated at the helm, cruising (Green).** The ship is well, so the console is dark: the cluster shows the reference, the next burn and Δv against the trip home; both MFDs are folded to their soft-key rails; the station board says who holds each station.

![Helm, cruising](vfd/stations-helm.jpg)

**E. At the weapons console, in a fight (Red).** Two missiles inbound and a raider firing: the annunciator's red zone fills from `MASTER WARN`, the amber zone from `MASTER CAUT`; the tactical cluster carries one chip per mount; SENSORS and DEFENSE pages are up on the two MFDs.

![Weapons console, in a fight](vfd/stations-weapons.jpg)

**B. On deck, fire and missiles inbound.** The deck view in battle lighting, the same top column, three callouts in the ticker and the suit readout as the captain's LCD.

![On deck, fire and missiles inbound](vfd/stations-deck.jpg)

## How it is built

| Spec widget | FILAMENT part |
|---|---|
| Z1s status strip | A rack module of fixed legends: `YELLOW`, `RED`, `BATTLE STATIONS`, `DOCKED`, `LATCHED`, then a dot-matrix field for the emergency text. Dark in Green |
| Z1 annunciator | A tile row with two filtered zones: `MASTER WARN` lamp key at the left with warnings filling inward, `MASTER CAUT` at the right with cautions filling inward. Warning tiles are red inverse blocks, cautions amber outlines; empty tiles are ghost cells |
| Z1w warp strip | A single dot-matrix line with the rate in seven segments |
| Z2 callout ticker | Three dot-matrix lines behind one glass, each led by a severity pip pair and the station tag |
| Z3b helm cluster | One module, five fixed slots: reference, next event, velocity (seven segments, autoranging `M/S` to `KM/S`), trigger, Δv. Δv is a two-rail bar: the fill, the home notch, the plan bracket |
| Z3b tactical cluster | One chip per mount (small multiples), then missiles, decoys, shields, the target block |
| Z3a, Z3c MFDs | Modules with six soft keys each. A page not needed folds: the glass sinks into the rack, the rail stays |
| Z3d station board | Six cells: the station's number and name as a key, the holder in dot matrix, `YOU` inverse |
| Z6 suit readout | Reflective LCD, backlit only while it warns |
| World symbology | The projected HUD: dot sprites, zinc and red channels, drawn under the band |

## What FILAMENT changes beyond the look

- **The band is a rack.** The seated band is an opaque moulded rack across the bottom, not panels over the world; the camera already lifts the ship by half the band's height (spec 6.2), which FILAMENT relies on.
- **Severity has an end of the row.** The annunciator's two zones make warning and caution readable by position as well as colour and form.
- **Folded MFDs keep their rail.** "Fold" is literal: the glass sinks, the soft-key rail stays where the hand learned it.
- **The suit is LCD.** The captain's own kit reflects; the ship glows.

## Round 1 critique

What the mockups show, and what round 2 does about it.

1. **Type is below the guide's own minimums.** Legends are drawn at 7 to 9.5 units, seven-segment fields at 9 to 12 and body text at pitch 1.5, against the guide's legends 10+, segments 12+, body pitch 2 and the spec's body 13+ and annunciator 12+. *Round 2:* the whole band is re-laid at the minimums; the cluster's slots lose their least useful secondary values first.
2. **Inverse text fills in.** The red warning tiles (`MISSILE 2 0:37`, `BREACH AFT STBD`) are cut-out dots in a lit block, and the bloom fills the cuts: the most urgent text on screen is the hardest to read. *Round 2:* cut letters are re-darkened after the bloom, and inverse text is never smaller than legend 9 or pitch 2 (now in the guide, 7.8).
3. **Folded MFD rails are too bright.** `NAV PLAN PIN FOLD` and `SNS WPN DEF PIN FOLD` glow at L2 in fourteen segments, the brightest words on a quiet helm. *Round 2:* a folded module's rail idles at L1; only the active page's label is L2.
4. **The station board talks in Green.** Six lit holders (`YOU`, `OKAFOR`, `--`) along the bottom edge are always on. *Round 2:* nominal holders at L1, a vacant station ghosted, a station with an alert at L2.
5. **The top column is a 192-unit block.** Status strip, two annunciator rows and a 60-unit ticker hold their slots even when one tile is lit, so most of the column is ghost glass over the world (see the functional notes).
6. **Fixed in round 1:** the suit LCD's backlight was the brightest thing on the deck (now a dim teal); the bars' red and amber feet lit on every healthy bar (now zinc with notches and pips, guide 7.2).

## Functional notes sent to the stations designer

1. **One annunciator row is enough by default.** Twelve tiles are rare; a second row reserved at all times pushes the ticker to y 148 and leaves 40 units of dark glass in nearly every fight. Suggest one row of six with `+n MORE`, the ticker at y 108, and the second row only as a full-screen list.
2. **SHIP callouts restate tiles.** "SHIP: two missiles inbound, port side, 37 seconds" repeats the `MISSILE 2 0:37` tile. Suggest SHIP lines carry only what the tile can't: the bearing, and who is (or isn't) on it.
3. **The station board in Green.** It answers "who holds each station", which matters when something goes wrong or when the player reassigns crew. Suggest it may go dark (or dim) in Green with nothing to report, like the status strip.

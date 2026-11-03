# Nasa-punk × Context-driven

The context-driven functional spec (`functional-context.md`, version 2 when drawn, on branch
`claude/project-thread-ukjzrp`) skinned in the Nasa-punk style
(`../../nasapunk/style-nasapunk.md`). Mockups are 1280 x 800 UI units drawn at 2x; sources are
the HTML files here, using `context.js` and the shared kit in `../../nasapunk/mockups/`. Render
with `node nasapunk/mockups/render.js variations/nasapunk-context` from `documentation/ui-overhaul`.

Round history is in [`../nasapunk-round-log.md`](../nasapunk-round-log.md).

## How the style maps the spec

| Spec element | Nasa-punk container |
|---|---|
| P1 context strip | The glareshield strip: seat, who has the helm, the BATTLE STATIONS lens, the clock and time warp. |
| P2 ship alerts | An EICAS-style crew alerting list: severity shape (◆ warning, ▲ caution), short code, detail, crew dots for who is on it (a NO CREW flag when nobody is), timer. A sixth row becomes "+n more". |
| P3 threat alert | A red-framed glass with a lens, IMPACT T−x large and a 60 s tape of the inbound missiles. |
| P4 message feed | Comm-loop callout slips. |
| P5 objective | A two-line slip with a magenta tab (magenta is the style's plan and objective colour). |
| P6 hints | Key caps with verbs, above the primary instrument. |
| H1 flight display | A DU under a hood that carries the MASTER WARN and MASTER CAUT lenses, so the master cue sits over the primary instrument. |
| H2 navigation | A DU with an orbit picture, apsides, the NAV block, the next event on a timeline and the plan's delta-v against what is left. |
| H4 ship condition | Four condition lenses, dark when well. |
| H5 contacts | A scope DU, folded to a key with a count when nothing is near. |
| F1 lock, F2 weapons, F3 shields, F4 threats | DUs. F3 is a ring of sectors around the hull outline, and the ring itself is the trim control. F2 has a row per mount with hit-chance pips in cyan. |
| X3 emergency | A guarded card with hazard-stripe framing: the ways out, their keys and costs. |
| D1 personal | The EMU chest display (DCM). |
| D3 off-screen pointers | HUD-layer edge chevrons with what and how far. |

## Screens

![Helm, cruising](c1-helm-cruise.png)

**C1, helm, cruising in Earth orbit, nothing wrong.** P2, P3 and P4 draw nothing; H4 is four
unlit lenses; H5 has folded to a key. The objective is two lines, H2 shows the orbit and the plan,
H1 sits bottom centre under its hood, and the nav target is off screen with one edge marker.

![Fire control](c2-fire-control.png)

**C2, weapons console in a fight.** Bea holds the helm. One missile inbound with point defence on
it: the threat alert with its 60 s tape, the threats DU, the shield ring (trim by clicking a side),
the weapons rack with hit-chance pips, the lock DU and the scope.

![Deck emergency](c3-deck-emergency.png)

**C3, on deck, ship disabled and venting.** P2 ranks the warnings, with SUIT AIR in it because the
captain's air is under 240 s. The emergency card gives the ways out; the off-screen pointers to the
fire and the casualty are dimmed under it. Bottom left: H4's deck face (no plan, since the deck is
the plan) and the captain's DCM.

## Round 1 critique

What works:

- P2 as an EICAS list ranks cleanly, and the crew dots say who is handling each alert without
  another column of names.
- The guarded emergency card makes the irreversible choices look guarded.
- The hood puts the master cue where the eyes already are in each context.

What was wrong in the spec, found while drawing and sent to the designer (all 12 adopted for v3):

- Power as supply and the 2 s store, not demand; a SYS DOWN caution.
- Decoys ready or seconds, not counted.
- A room naming rule (by system or hull position), since rooms have no names in the code.
- P2 short codes of 10 characters at most; ranking puts what needs you first, and SUIT AIR always
  first.
- L-TOP and R-TOP 150 tall, with the MID zones from y 194; insets 140 x 112 plus 22.
- Wireframe 07 fixed; D3 stays lit under X3.
- A master cue that latches for warnings until acknowledged, on a new key (`\`).
- P5 in two lines; the F3 ring as the trim control; hit-chance colours left to the styles.

What is still wrong in these screens, fixed in round 2:

- **C2's rack has the hand flags backwards.** On the game's Patrol Corvette the autocannons are
  point defence and stay automatic at the weapons console (`PlayerGunnery.inHand`), while the
  laser and the harpoon launcher are in hand. So the autocannon rows lose their hit chance and
  read AUTO, the laser and launcher rows get the hand mark, and the laser's 3 km reach
  (`max_range`) belongs on its row.
- **Fire arcs** drawn as narrow wedges, where the guns' arcs are 300°.
- **D3 dimmed under X3**, which v3 reverses.
- **Type below the guide's floor** (labels at 9.5 to 10.5 u) and **commas as thousands
  separators**, where the guide says a thin space.
- **v3's layout:** L-TOP and R-TOP 150, MID from 194, P2 codes of 10 characters, the new ranking,
  the latched master cue with its key, insets 140 x 112.

## Deviations from the spec

- SUIT AIR appears in C3's P2 list though the spec's wireframe leaves it out: the captain's air is
  under 240 s, which the spec's own rule makes a P2 row. (v3 adopts this.)
- P3 shows the time to impact as a tape as well as a number, so two missiles' spacing reads at a
  glance.

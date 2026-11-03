# Nasa-punk round log

The Nasa-punk artist's share of the nine-variation loop: the Nasa-punk style
(`../nasapunk/style-nasapunk.md`) over each of the three functional specs. Each round draws or
updates the three variations, critiques each, sends the functional findings to the spec's
designer, and carries the rest into the next round. A variation is done when a round finds nothing
meaningful left in it: no claim the game's code contradicts, every spec rule met or a deviation
argued, the style's own rules kept (type sizes, separators, colour semantics), and no collisions
at 1280 x 800.

| Variation | Folder | Spec version drawn |
|---|---|---|
| Nasa-punk × Ship Stations | [`nasapunk-stations/`](nasapunk-stations/README.md) | round 9 |
| Nasa-punk × Context-driven | [`nasapunk-context/`](nasapunk-context/README.md) | version 5 |
| Nasa-punk × Task-driven | [`nasapunk-task/`](nasapunk-task/README.md) | round 5 |

## Round 1 (3 November 2026)

**Drawn:** three screens per variation, nine in all: a quiet helm, a fight at the weapons
console, and trouble on deck, for each spec.

**Findings sent to the designers**, each checked against the game's code:

| Spec | Notes | Outcome |
|---|---|---|
| Ship Stations | 9: the power model (supply and a 2 s store, no demand or brownout), no galley subsystem, shorts reclosing after 45 s, stun countdowns, decoys uncounted, room names, and layout points | All adopted in the spec's rounds 4 and 5, with an audit of the rest (gun modes, no gun heat, ammunition only where limited, a status strip, battle lighting, the camera shift) |
| Context-driven | 12: supply and store, a SYS DOWN caution, decoys, a room naming rule, P2 codes and ranking, zone sizes, the latched master cue, P5 in two lines, insets, the F3 ring as trim | All adopted in version 3 |
| Task-driven | 14: the weapons loadout, one lead reticle, decoys, the store's flicker, a reactor short as NO POWER, the 5 s reset, a missing VENTING caution, room names, the velocity frame near a station, label rules, the key count, a colour word, zone overlaps | All adopted in its round 2 (rooms named by rule, "near" as 5 km, POWER lit for a dead reactor, not the store) |

**Found in my own screens**, carried to round 2:

- S2 and C2 draw the corvette's guns wrongly. The game's Patrol Corvette (`ships.toml`) has a
  coilgun in the nose, an autocannon each side, a harpoon launcher aft to port and a laser aft to
  starboard; the autocannons are point defense and stay automatic at the weapons console
  (`PlayerGunnery.inHand`), and the laser reaches 3 km. T2 has it right; S2 and C2 get the same
  rows. S2 also counts decoys (`DECOY 6`), and both draw 56° fire arcs for guns whose arcs are 300°.
- Labels at 9.5 to 10.5 u in all three variations sit under the style guide's 11 and 12, and
  under the task spec's 12-unit floor.
- Thousands are written with commas; the guide says a thin space.
- The style guide promised an abbreviations table with the first skinned spec; it isn't written.
- Room names come from the kit's stand-in deck; each spec now has, or needs, a naming rule.

**Style guide changes this round:** the fixed slots became the style's default layout, with a
spec's own zones kept and mapped (section 9); a console face may stand as a side panel (section
6); a slot's fixed key may be printed on its cap where a spec keeps bindings in view (section 6);
deck hazard tags and prompts are the one boxed thing allowed over the world (section 9).

## Round 2 (3 November 2026)

**Drawn:**

- Stations: all three screens to the spec's round 7: the First Play fight with no shields, the
  status strip, the gun modes and the station board.
- Context: C1 to C3 to version 4 (the 12-unit floor, P2's handling column, the master cue on
  deck), and a fourth screen, C4, for boarding while latched (X2).
- Task: to its round 3 (nine lamps and a blanking plate, the two-row alert band, a row per mount,
  the corvette's real deck), then round 4 (short-form cautions, no ASSIST box while it is on, no
  key repeated on the hint line).

**Names.** Each page, station and datum is written one way on every screen. The MFD page keys
keep their whole names, since PIN and FOLD take less room, and DELTA-V, THR, RR, TTI, PWR, STRUC,
MET and SHLD give way to the forms the other screens use. The guide's own sheets follow.

**Kit:** the corvette's deck and plan drawn from the chassis data; B612 widths measured, not
guessed; soft keys weighted; a value's unit set 4 units after it; and `check.js`, which flags text
that overlaps, crowds, touches or crosses a frame, is cut by its glass, or is under the floor.

**Findings sent to the designers**, checked in `assets/data` and `GameScenario.PLAYER_SHIP`:

| Spec | Notes | Outcome |
|---|---|---|
| Ship Stations | 8: walkthrough E's guns against the real corvette (the autocannons are point defense and out of hand at the console, the coilgun's time of flight at 3 000 m/s, the laser's 3 km, a magazine of 4, the laser most often charging); then no shield emitter on the corvette, its two rooms, no airlock, and which room F's fire is in | All adopted by round 9: the mounts are NOSE, FWD PORT, FWD STBD, AFT PORT and AFT STBD, E has no shields, and the rooms are BRIDGE and HOLD |
| Context-driven | 2: no shield emitter on the corvette, and its two rooms | Both adopted in version 3: F3 is hidden on a ship without an emitter, 11.5 is drawn on a gunship, and the rooms are BRIDGE and HOLD |
| Task-driven | None new: its round 2 had answered round 1 | |

Drawing version 4, I also found eight problems in the context spec (the crew in 11.7, the party
in X2, a source for P4, the range in 11.5, the rack in wireframe 05, W3 repeating H2, the scope
in H5 and the raider in wireframe 13). Version 5 was already being written, so I held them until
it landed, then checked each again; all eight are in round 3's note.

## Round 3 (3 November 2026)

**Drawn:**

- Context, to version 5: C1's orders panel in the spec's group order, with the guarded orders
  (abandon, the tow) last at the far corner, and the trim as a five-way selector. C3's reactor is
  damaged, so nothing comes back by itself; its P2 rows are in version 5's order, and the tow is
  shown armed, its guard up and a bar running out. X2 has seven rows.
- Task, to round 5: the band at the spec's 640 with a reserved `+n` slot; the console prompt
  names its object once (`WEAPONS CONSOLE  E OPEN  V SIT`); the clock's warp reads WARP; the
  first harpoon 0:03 out.
- Stations, to round 9: Cole's and the SHIP callouts within the ticker's 50 characters; the
  corvette's Shields slot blank.
- All nine: countdowns as M:SS, percent with no space, warp as `1×`, and every word from the word
  list below.

**The word list.** [`../nasapunk/abbrev.md`](../nasapunk/abbrev.md), which the guide promised in
round 1, is written and checked against every screen: seven rules (one name for each thing, whole
words unless a field can't hold one, one short form for each word, nothing invented on a screen,
units, keys, the game's own spelling), then tables for units, time, flight, combat, the ship,
names, P2's codes in this style, and the words that belong only in these documents. The style
guide's word rules now point to it. Its clock is the game's (`DAY 0 · 01:12:40`), not MET, and
DV, RR, TTI and PWR are gone from its text.

**Checks:** all nine screens pass `check.js`; the guide's sheet and flight deck pass it too, with
the floor at 1, since they draw parts at the guide's sizes and no spec binds them.

**Findings sent to the designers:**

| Spec | Notes |
|---|---|
| Ship Stations (round 9) | 5: two names for each MFD page (SNS, WPN, DEF, DMG, PWR and ATM in the wireframes; whole words in the text, the page table and the console's tabs, where the tab is Atmosphere and the page ATMOS); walkthrough F's timing (31 s left on a 45 s breaker puts the short 14 s back, past the 10 s a caution line holds, and the store would long since be full); DEFENSE against "point defence"; what the impact time is (below); the scope's jump from 2 to 10 km |
| Context-driven (version 5) | 10: the eight held from round 2; what the impact time is (below); and P2's code spellings (HULL DMG, SHLD DOWN, SHLD WEAK and DOCK STRN where the spec spells the words whole elsewhere, and LOW O2 against PROP LOW) |
| Task-driven (round 5) | 4: what the impact time is (below), which sets when MISSILE's 20 s warning really fires; SNS's automatic range, which ignores inbound missiles; SHLD against SHIELD; "point defence" |

**What the impact time is**, sent to all three. A harpoon doesn't burn flat out: it closes at
30 m/s² (`MissileDef.closingBurn`, which `weapons.toml` leaves at that for the harpoon) after a
0.6 s dark start, so it lands 13 to 15 s after launch from 3.2 km, about 22 s from 7.7 km and about
31 s from its 15 km reach. The time the game shows (`CombatThreats`) is closest approach at the
present relative speed, so it runs long and falls faster than the clock until the last 5 s or so:
from 7.7 km it reads 37 s 5 or 6 s after launch and 20 s with 12 s left. Two specs had dropped a
37 s sample as one a harpoon can't give (the context spec's version 5, from the VFD artist's
notes, and the stations spec's round 9, whose change log credits this thread); it is one. These
screens had been drawn from the same mistaken flat-out figure.

**Found in my own screens and fixed:** the harpoons in T2, S2 and C2 sat where no harpoon could be
at their countdowns, from the same flat-out figure; and T1, T2, S2 and C2 drew their scopes'
contacts at other ranges than the world view does. Each harpoon is now where its countdown puts it,
with its track drawn from the missile on, and each scope contact at its world offset. C2's Raider 2,
drawn on screen at 3.5 km against F4's 14 km, is an edge marker.

**Still wrong**, carried to round 4 (each README lists them):

- S2: the first impact is the most important number in walkthrough E and the only number in the
  overlay, and it could be larger.
- S1: the dark trigger slot reads as missing rather than reserved; it needs a quiet mark of its
  own that isn't a legend.
- T2 and T3: caution cells sized to their text move between screens. Fixed thirds of the 640 are
  worth trying, now the spec leaves it to the style.
- T2: WPN's DECOY row stands alone at the foot; it could join the launcher's row.
- C1: the trim selector is never shown live; it only is on a ship with an emitter.
- C3: the bridge's consoles read only by their outlines. A floor-path strip would help, but that
  is world art, not the HUD, so it stays a note.

**Style guide changes this round:** section 4's word rules point to the word list; time is the
game's clock; the layout diagram's master lenses are MASTER WARN and MASTER CAUT; DU-2's pages
are in whole words.

## Round 4 (next)

- Apply each designer's answers to round 3's notes, and the stations spec's round 10 (which landed
  after round 3 was drawn): S1's burn cue keeps only its number, the CMD tile shows no occupancy,
  and a speaking mark replaces speech on deck.
- Fix the five carried items that are the HUD's (all but C3's floor path), then critique again.
- A variation is done when a round finds nothing meaningful left in it.

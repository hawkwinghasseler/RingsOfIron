# Nasa-punk round log

The Nasa-punk artist's share of the nine-variation loop: the Nasa-punk style
(`../nasapunk/style-nasapunk.md`) over each of the three functional specs. Each round draws or
updates the three variations, critiques each, sends the functional findings to the spec's
designer, and carries the rest into the next round. A variation is done when a round finds nothing
meaningful left in it: no claim the game's code contradicts, every spec rule met or a deviation
argued, the style's own rules kept (type sizes, separators, colour semantics), and no collisions
at 1280 x 800.

| Variation | Folder | Spec version drawn | State |
|---|---|---|---|
| Nasa-punk × Ship Stations | [`nasapunk-stations/`](nasapunk-stations/README.md) | round 14; S7 at round 15, S9 at 16, S8 at 18 | Converged in round 5; S7 to S9 drawn in round 7, and S5 to S8 redrawn for the crewless Raider |
| Nasa-punk × Context-driven | [`nasapunk-context/`](nasapunk-context/README.md) | version 17; C11 at 18 | Converged in round 5, and again at versions 13 to 18 in round 7; C11's questions and the crewless Raider are with the designer |
| Nasa-punk × Task-driven | [`nasapunk-task/`](nasapunk-task/README.md) | round 14 | Converged in round 5; round 9 drawn in round 6, rounds 11 to 14 in round 7 |

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

## Round 4 (3 November 2026)

**Drawn:**

- Stations, to rounds 10 to 13: S1's burn arrow carries only its number and the CMD tile names
  nobody; S2's harpoons are 2.4 and 2.5 km out, landing in 8 and 9 s with their burn counted, and
  SENSORS opens at the new 5 km step; S3's SHIP callout has no countdown, and Ada has a speaking
  mark instead of a bubble.
- Context, to versions 6 and 7: W3 without its countdown, P2's whole-word codes in an 11-character
  field, F2's mounts and launchers as the sample has them, H5 at 5 km.
- Task, to rounds 6 and 7: both scopes at 5 km, the alert band without a bearing, SHIELD.

**The designers' answers.** Every round 3 note was taken: 5 by the stations spec (its round 12),
10 by the context spec (version 6) and 4 by the task spec (round 6). All three now count the
missile's burn in the impact time, and all three added a 5 km scope step.

**Found in my own screens and fixed**, on a fresh pass over all ten:

- **Every lead reticle was hundreds of metres off its target**, drawn as a textbook lead rather
  than worked out. In S2, T2 and C2 the offsets (430 to 505 m) needed targets moving at 190 to
  550 m/s, against closing speeds of 12 to 41 m/s on the same screens. The game's pilots match the
  ship's velocity and close at 80 m/s at most (140 backing off when kiting), with up to 25 m/s of
  drift (`NpcCombatComponent`), and the shots take 1 to 2.2 s, so the lead is 27 to 44 m: 2 to 4
  units at fight zooms. Each reticle now rings its target inside the lock brackets, and the kit has
  one reticle for all three.
- **Numbers that disagreed with each other:** C2's lock card (REL 38 at a course nearly straight at
  the ship, against CLOSING 12); T2's helm holding a position off a target the card shows closing,
  and its closest approach (1:15, not 1:12); S1's closest approach, from the spec (5:03, not 2:10);
  C1's periapsis (half an orbit after the apoapsis, T−1:10:22).
- **Controls the specs ask for and the screens lacked:** the scope's − and + on T1, T2 and S2's
  SENSORS page, placed either side of the range as the game's scope has them.
- **One datum, two homes:** C2's P3 said PD ENGAGING, which is F4's at fire control.
- **Smaller:** SVG folded double spaces in values (`2  0:08` read as `20:08`), so `text()` keeps
  them; S1's trigger slot shows its lenses unlit, so it reads as reserved; T2's DECOY is the
  table's last row; T3's payday is a SPONSOR slip in sentence case; S3's ticker has the newest line
  at its foot; T2's range lost its AUTO word, which T1's, also automatic, never had.

**Resolved without a change:** S2's impact stays at the tile's size, since the tile grid is fixed
hardware and the kept double space makes it read; T2's caution cells stay sized to their text,
since the band is a message list and the ten-lens panel is the fixed hardware.

**Findings sent to the designers:**

| Spec | Notes |
|---|---|
| Ship Stations (round 13) | 4: F's NEXT line repeats its row's 37 s; a shorted gun is "shorted" on its chip and "no power" on POWER; C's closest approach (140 m from 1.2 km at 3.9 m/s is 5:03 away, not 2:10); F's ticker order, if the short's sparks lit the fire |
| Context-driven (version 7) | 2: F1's sample can't be a kiter (120 m/s relative at 85 closing is 85 m/s across, and a kiter doesn't circle); "fights sit inside 4 km" (the coilgun reaches 5, a kiter holds out to 4.5) |
| Task-driven (round 7) | 3: round 7 dropped the scope's − and +; "the corvette's 4 km reach", from my own wrong round 3 note; two lead reticles (one per muzzle speed) would sit on top of each other and the target |

All three also heard the reticle finding, since each spec keeps the game's lead reticle: in these
fights it sits on the target, inside the lock brackets.

**Style guide changes this round:** the lead reticle has its size and its rule (section 7): it
rings the target inside the lock brackets, since that is where the lead puts it in these fights.
The word list notes that version 6 of the context spec took its P2 spellings, and that callouts
carry no countdown.

**State of the variations.** Each now has nothing left that the HUD can answer, pending the
designers' answers to the notes above. Two notes stay open in the context variation, neither a
fault in what is drawn: the trim selector is never live on the corvette, and C3's floor path is
world art.

## Round 5 (3 November 2026)

**The designers' answers.** Every round 4 note was taken: all 4 by the stations spec (its round
14), both by the context spec (version 8, which also took the cassette artist's notes; version 9
then changed only H6's sample and wireframe 04, neither drawn here), and all 3 by the task spec
(round 8, which also checked my 1:15 and gave T2's helm an approach to 2,000 m).

**Drawn:**

- Stations, to round 14: S3's POWER page lists what is not working in the order it comes back.
  The shorted gun is on top (SHORTED, back in 0:37, its breaker's 45 s wait as a bar 8 s along),
  then the nose gun (DISABLED, health 24%, repair). The NEXT line has gone.
- Context, to version 8: F4 names each missile's shooter; H2's numbers take their lines' 13, while
  F3's heat and H4's figures read at 15 as values; H4's power row gives the store and the output,
  with the rating beside it while damage holds the output under it. The master cue already sat
  over the rack at fire control, and F1 already had the kiter's numbers.
- Task, to round 8: T2's helm flies an approach to 2,000 m off Raider 'Vex', boxed green as an
  order in flight. The strip spans the gap between SHIP and WEAPONS so the whole name fits.

**Found in my own screens and fixed**, on a fresh pass over all ten:

- S3's MASTER WARN lens vanished when only cautions were live, leaving a gap where S2 has it lit.
  The master lenses are the row's fixed hardware, so it now sits unlit in its place.
- S1's camera inset carried a TV tag, a short form the word list lacks for a mode the game
  doesn't have; it is gone, as on S2's inset. A pass over every string on all ten screens against
  the word list's "never" forms found nothing else.
- C3's H4 named the life support reserve EMERGENCY, the spec's word, beside P2's EMERG O2 for the
  same datum; the word list gives an instrument EMERG, so the row reads EMERG O2.
- My round 4 notes, README and S1's source called the Highport walkthrough A; it is C.

**Findings sent to the designers:**

| Spec | Notes |
|---|---|
| Ship Stations (round 14) | None: round 14 draws as written |
| Context-driven (versions 8 and 9) | None: both draw as written |
| Task-driven (round 8) | 1, sample text only: the gunnery wireframe names the target Raider 'Vex' on the card, its label and the contract, but "Raider" on the helm line and in the frame tag |

**State of the variations.** All three have converged: the fresh pass found nothing meaningful left
in any of them that the spec or the style can answer. The task note changes nothing drawn. The two
open context notes (the trim selector is never live on the corvette, and C3's floor path is world
art) are coverage and world art, not faults in what is drawn.

## Round 6 (3 November 2026, task only)

**The designer's answer.** Round 9 of the task spec took the round 5 note (a ship is named in full
wherever it is named) and corrected two samples from its own review against the code: a shot at
the ship or a missile launched at it drops every helm order but hold position and match velocity
(`HelmOrders.threat`), so round 8's approach could not stand under fire; and no warp guard holds
for hostiles, so the gunnery and deck clocks read `held: fire aboard`.

**Drawn:** T2's helm holds the heading with no order, and its warp is held by the fire aboard. The
at-rest scope keeps `5 KM` without the spec's `(auto)`: automatic is the normal state, so the style
marks a range set by hand instead, in cyan. That is argued in the task README's deviations.

**Findings sent to the designer:** none.

**State.** All three variations stay converged.

## Round 7 (6 November 2026): the windows the overhaul skipped

**The ask.** The overhaul covered the in-flight HUD and left the rest of the game's windows as they
were: the main menu and the other shell pages, the map, boarding, the station's services, the
crew, the log, the controls and the consoles. Each spec already places them (the context spec's
M1 to M6, the task spec's section 7, the stations spec's panels in 10.2 to 10.4), so they are drawn
from each designer's own words, with the contents of today's windows (shot in the game, one by one)
and the game's numbers.

**Drawn so far:**

| Screen | Task | Context | Stations |
|---|---|---|---|
| The shell (shared by all three) | [SH1 to SH8](nasapunk-shell/README.md) | | |
| The map | T4, a proposed transfer waiting for EXEC, late against the deadline | C5, the accepted plan with burn 1 selected | S4, the full-screen Plot with a cursor reading the path |
| Boarding | T5, BRD once the ship is taken, with the salvage marked | C6, M4 with the party fighting aboard | S5, the slate's Boarding tab before the party goes |
| The station's services | T6, the station terminal on Contracts | C7, M3 on its Market tab, a panel over the docked view | S6, the station link's Yard tab |
| Ship and crew | T7, the ship's book on Crew | C8, M2 on Systems in the corvette's fight | S7, the slate's Crew tab, with the new station order |
| The log and the controls | T8, the book's Log tab; T9, the controls reference | C9, M5 on its Log tab; C10, M6 on its Keyboard tab | S8, the slate's Log tab |
| The consoles | T10, CON at the same mount, anchored below it | C11, the inspect card pinned on the same mount, with the station panel docked | S9, the gun panel at a point-defense mount |

**New pieces in the kit.** A mark box for lists where Enter marks or leaves a row: filled cyan when
marked (a command, so cyan), hollow and dim when not, and absent where the row can't be marked,
which then says why. Work tags on deck: white slips on leaders, with a bar for work that has an
end. Prisoners and carried crates on the deck plan (`corvetteDeck`'s `held` and `carry`).

**Checked against the code.** Every boarding number is the game's. The party is the ship's marines,
or its crewmen if it has none (`Boarding.party`), so on the starting corvette it is Cole alone. A
station pays a twentieth of a part's price, and stripping takes an engineer 10 s for each tile of
the part's mount. Every part comes off as a 1 m³ crate, and the engines only on EVA. A prize
sells for a twentieth of its value whole and a fiftieth once a part is off. A pressure suit holds
10 minutes of air and uses it only where the air is not breathable. Storming forces every shut
door, prying with the power down.

**Critique, round 1.** S5's world first showed the two ships apart. They are latched now, the
raider drawn on the corvette's hull since it is the corvette refitted. S5's party table first
listed 20 minutes of air, and C6 listed 18:40. Both now show the pressure suit's 10:00, which stays
full in a pressurised ship. S5 had no key for STORM, and its response line repeated the soft
key's legend. STORM is now on F, and with no key pressed the response line is empty. T5's mark
outlines were a row off on the half-turned raider, and the prisoners stood on the tanks; both are
fixed.

**For the designers** (sample text only, nothing structural):

| Spec | Note |
|---|---|
| Context (X2) | The sample "party 2 aboard · holding" can't happen on the corvette: its party is Cole alone, and a party that takes a ship stands down and comes home |
| Task (section 7) | The taken phase's "Recall all" is refused by the game once the ship is taken ("No boarding under way"). The sample part values differ from the game's: the harpoon launcher sells for 1 500, the reactor for 2 000, and every part is a 1 m³ crate |

**Next.** The station's services, ship and crew, the record and log, the controls and the
consoles, in each spec's own terms. Then all of it goes into the game on the shared screens hook,
one pull request per spec.

**The context spec's versions 10 to 13.** The cassette direction's round 3 made the context
spec's samples true to the game's geography (version 10): the trip to Shackleton Ice is planned a
leg at a time, since it orbits the Moon, the modes' tabs carry their number keys with the close
keys at the foot, and M3 is a panel over the docked view. Checked against the game's code, four
notes went back, and version 11 took all four, adding P2's Held state (the crews' rule of keeping
clear of where crew were hit, and inside, at battle stations). Drawing C1 found a fifth, the cruise
scope's fixed 50 km, which put the station just left under the own-ship mark; version 12 takes the
smallest step that holds every contact inside 50 km. Drawing C8 found a sixth, below, which
version 13 took. All eight context screens are redrawn to version 13: C1's scope on the 5 km step, C3's held vacuum at battle stations, C4's party coming
home, C5's leg-at-a-time plan, C7's panel, C8's board and its caution chip. The context
[README](nasapunk-context/README.md) has the round's critique.

| Spec | Note sent this round | Landed |
|---|---|---|
| Context (wireframe 11) | The systems list gives the RCS blocks' power as "ok", but the game's blocks draw none: one works while the hull tile it is bolted to stands (`RcsThruster`), and fires unless out of propellant (`RcsThrust`) | Version 13: a dash, and the list dashes any value the game doesn't have. The designer counts Context × Nasa-punk converged |

**The yard, the ship's book and the source comments.** S6's yard listed the nose gun at 24%, but
the crew mend a damaged system in seconds (a tenth of full health a second), so a yard never sees
one. The first row is now the bow's starboard hull tile, which a field repair frames back up only
to 20%, and the bill is about 920 CR. T7 had Ada repairing an engine with nothing damaged; an
engineer never mans a console, so with nothing to do Ada is idle, in the game's word. Comments in
four stations screens, one task screen and one context screen gave crew gendered pronouns; they
name the crew now.

**New pieces in the kit.** P2's held mark (two white bars) and P1's caution chip (`cwChip`: a push
lens 24 tall with each count in its severity's colour, lit red while a new warning waits) in
`context.js`. `systemPlot` can end a path at a sphere of influence (`leg: 'soi'`), marking the
crossing with a bar, and draws the ship where its orbit puts it (`shipLead`). The word list adds
SOI and the countdown over a day (`T−4D 4H`).

**The slate's Crew tab, version 14 and my own day forms.** S7 draws the stations spec's Crew tab
with its new order, station assignments, as a selector over the consoles a person can man (AUTO,
the helm, TAC and the two guns a gunner works; the point-defense autocannons keep to themselves and
the launcher waits for the captain), with what each does to the guns by the game's rules. The
context spec's version 14 gave the map's ghost of the Moon the time it stands for; C5 already drew
both Moons, so only its label changed, and S4 and T4 now give the ghost the same name, MOON AT
CPA. A sweep of my screens against the word list found day and hour values with spaces or
decimals (T6's `3.8 D`, S4's `T-2.1 D`) and hyphens for the minus in S4 and T4. They read `3D 19H`,
`T−2D 2H` and so on now, and T6's travel times under a day are H:MM:SS. T7 counted 0.6 h of work
for Cole beside an engineering skill of 50, but every second of work raises engineering, and 0.6 h
would have taken it to about 87; Cole's record has no work now, and T7's TAKES ON line adds the
rescues, fires and wounded a marine takes on.

| Spec | Note sent this round | Landed |
|---|---|---|
| Stations (10.4, 20.7) | What a station assignment does is unwritten. S7 draws it as which console a person mans while nothing else needs them, AUTO for the crew's own choice, over only the consoles a person can work: the helm, the weapons console and the guns a gunner works | Round 15: as drawn, and the selector offers only what the person's role mans, so a marine has no HELM (`CrewRole`). S7 is redrawn to it |
| Stations (10.4) | Letting go can't be undone: the game removes the person (`CrewHiring.dismiss`), who doesn't go back on the station's hiring list. Should it take two presses, as the beacon and abandon ship do? | Round 15: two presses, "Let Cole go? [Enter] again: Cole leaves the ship for good" |
| Stations (10.4) | Two people set to one console: which gives way? A suggestion: the newer order wins, the other goes back to AUTO, and the footer says so | Round 15: as suggested, with "Bea goes back to AUTO" on the footer |

Round 15 of the stations spec took all three, and S7 is redrawn to it: Cole's selector reads AUTO,
TAC, NOSE GUN and AFT STBD GUN, one under another with what each does to the guns beside it, and
LET GO sits on Enter, pressed twice.

**The captain's record, the controls, and the context spec's version 15.** C9 draws M5 on its Log
tab at First Play's NOTICES step ("the fight, the bounty and the yard's work are all there"), from
the game's own notices with P4's source tags, severity as P2's marks rather than colour, and a rail
that filters by source and severity with each one's count. C10 draws M6 on its Keyboard tab in the
spec's own example, "Here: Helm, approach", from the game's hint list with the design's changes to
the keys, each section a hint context of the game's under the design's name for it. C8, C6, C4,
C10 and C9 now tell one story, First Play's fight and the way home, and C9's log carries the
others' events at their times. Version 15, from the VFD direction's check of C8's tab, made the
breach and the stopped RCS block one hit on one tile and gave every system its switch (the
reactor's guarded); C8 is redrawn to it. My own fixes with them: C4's feed and C8's foot read the
game's notices word for word ("Raider is taken: 4 held"), C9's messages take the word list's CR
after a number, as prose does, and the arrow keys' caps are drawn (`arrowKey` and `anyKey` in
`context.js`), since the arrow glyphs at 12 units were too faint.

| Spec | Note sent this round | Landed |
|---|---|---|
| Context (10.5, P2, P3) | The Log is "every message", but P3 replaces the missile notices and P2's conditions post nothing, so a fight's record would keep none of its hits. Should the Log keep P3's threats and P2's conditions as they come and clear? C9 keeps the game's missile notices, as warnings | Version 16: yes, Log-only and at their row's severity: each condition raised and cleared, each missile fired at the ship and how it ended |
| Context (10.5, P4) | Does the Log keep each gunnery event, or the feed's merged 5 s lines? C9 keeps the merged lines, which hold a fight to a page | Version 16: the merged lines, each opening to its events with their times |
| Context (10.5) | The filters have no keys, and no notice has a source. C9 steps the source on ← and →, the severity with Shift, and scrolls on ↑ ↓ and the wheel as today; it tags contracts SPONSOR, prisoners handed over STATION, and a wound CREW, as C8 does | Version 16: the filters are controls at the top, stepped with ← and → under `FocusNav` or by a click; the list scrolls with ↑ ↓, Page Up and Down and the wheel; P4 gives each notice's source, as C9 tagged them |
| Context (10.6) | The game's hints have no gun seat, though P1 has one. Should M6 give it a section (aim, fire, stand up, zoom)? C10 lists the game's contexts only | Version 16: yes, and the hint registry gains a gun context |

Version 16 took all four, and C9 and C10 are redrawn to it. C9's Log now holds the fight's whole
record: the raider's three missiles (two shot down, one into the hold), the breach, the hold's
pressure and vacuum and the two guns down, each as it was raised and as it cleared, with the time
it held, and the line of hits that disabled the raider opened to its four hits. Its two filters sit
at the top of the tab, each a row of positions with their counts, and severity has CAUTION now that
cautions are in the record. C10 gives the gun seat its own section under fire control's. Drawing the record found four
more questions.

| Spec | Note sent this round | Landed |
|---|---|---|
| Context (P4) | P4 gives no source for a condition or a missile. C9 puts the hull, the air and a system down under Engineering, as its repairs are, and a missile under Gunnery, as point defense is | Version 17: as drawn, and the rest by what each is about (a wound under Crew, shields under Shields, boarders under Boarding, the orbit and the approach under Helm); missiles are warnings |
| Context (P2, P4) | P2 says the long form is the Log's wording; P4's example uses the short code ("VACUUM HOLD cleared, 2:10"). C9 writes "Hull breach: HOLD" and "Hull breach: HOLD, cleared after 11:04" | Version 17: the long form, in C9's format, which P4's example now uses |
| Context (P2) | A room that goes from low pressure to vacuum and back: P2's example has no PRESS LOW row beside a VACUUM one. C9 takes the vacuum to stand in for the low pressure while it holds, and logs four entries | Version 17: a room's air is one row, the first that holds of BREACH, VACUUM (sealed, still airless) and PRESS LOW (refilling), and the Log follows that course with the clearing timed from the first; C9 and C3 are redrawn to it |
| Context (10.5, P4) | The hits the ship takes from guns aren't in the record: the raider's gun hits show only as the SYSTEM DOWN they raise. Should the Log take a merged line for hits taken, opening to where each landed? | Version 17: yes, "Gunnery: 2 hits from Raider", notable, opening to where each landed and what it did; the game's hit event gains its shooter |

Version 17 took all four, with my two choices (severity as a threshold, a merged line at its first
event's time). C9 is redrawn to it, with the raider's two gun hits on the corvette as a line of their
own, opened, and the hold's air as one course from breach to refilled; C3's P2 row and master cue
read BREACH. The context screens are converged at version 17.

**The book's Log and the controls reference.** T8 draws the book on its Log tab after the taking
of Vex, from the game's notices on day 3, with the band's alerts logged in their full text, as
section 9 says, under WARNINGS with the band's level as the mark. T9 draws the controls reference
F1 opens, every action by post and then by screen, with the game's default key and the pad's
button, and a dash where the pad has none. Checking my own task screens against the code found four
errors, all fixed: T5's objective kept the bounty as taken, to be turned in at Highport, but a
bounty is paid the moment its ship is beaten (`ContractBoard`), so OBJ is hidden there; T3's news
was a payday on day 3, but the budget is paid weekly from the start (`ServiceBudget`), so the slip
carries the game's own news that the pirate is breaking off (`HostileTraffic`); T3's objective was
still the ice to Freehold, which round 10 made the food delivery to Shackleton Ice; and T9 first
put zoom and the lights under the deck and the helm, though they work at every post. Z3 is 240
wide and holds few of the game's notices on one line, so a news slip or an objective that doesn't
fit takes a second line, broken where the two are most even (a deviation).

| Spec | Note sent this round | Landed |
|---|---|---|
| Task (round 10, TRM, MAP.2) | The late delivery can't happen on day 3: a delivery to another body is due 8 days after the day's board posts it (`ContractPostings`, `FAR_DAYS`), so the 3.8-day transfer has about 4 days spare. An urgent delivery for a shortage (`WorldEvents`) is due 3 days after the day's roll whatever the distance: rolled on day 3, it is due on day 6 at 00:00, 2d 9h away at 14:22, and the transfer arrives 1d 9h 40m late. The samples could take that one with those numbers (its giver is Shackleton Ice's owner, its standing +5). T6 keeps the ordinary delivery, and T3 and T4 round 10's numbers, until then | Round 11: the late delivery is the urgent one Shackleton Ice's food shortage posts at Highport (`WorldEvents`), for +5 with the Selene Mining Combine, due Day 6 00:00 and 1d 9h 40m late; the list's pay is net of the cargo, and its standing names a giver who isn't the sponsor. T3, T4 and T6 draw it |
| Task (OBJ) | OBJ's done state ("collect at the giver") has nothing to show: the game pays every contract as it ends, a bounty the moment its ship is beaten | Round 11: the done state is gone; a contract leaves the tracker as it ends, and the pay is news |
| Task (7, LOG) | T8 logs each alert as it is raised, under WARNINGS with the band's level as its mark. Is its clearing logged? The intercept is a WARNING notice with no alert of its own: as a caution, what are its code and action? The game keeps 50 notices, and a day like T8's fills 19 of them | Round 11: an alert is logged once, in full text with its level, and gains its clear time (`cleared 14:31`) rather than a second entry; filters are alerts, important and info; the log keeps 500 and is saved. The intercept is a caution, INTERCEPT, and every warning notice has a home (9.2's table) |
| Task (10, pad) | The pad can't aim at the weapons console: the guns follow the pointer (`PlayerGunnery`), and there only the weapons answer, so both sticks are free and the right one could aim. Nor does the pad reach the shields (9, 8), zoom, the lights, the next port (H), the map's Q, ; and /, the book's I and L, F1 or a quick load | Round 11: section 10's table closes the gaps: the right stick aims, R3 back to the lock; the bumpers work the shields; Back with the right stick zooms; R3 on deck works the lights; HUD focus, the map as a screen and the pause menu cover the rest |
| Task (10) | Esc closes "the top layer", but the closing order puts the controls reference after screens and panels, so with F1 open over the map, Esc closes the map under it first | Round 11: the controls reference opens over everything and closes first |
| Task (7, Z3) | Z3 is 240 wide, and few of the game's notices fit it on one line (even "DELIVERY: ICE TO FREEHOLD 1D 4H" is 246). May a news slip or an objective take a second line? T3 breaks one where the two lines are most even | Round 11: an OBJ row or an NTF slip may take a second line, never a third; OBJ breaks before where the job goes, else at a word, with the time on the first line |

Round 11 took all six, and round 12 followed, matching the alert list to main's alert registry
(the HUD framework, #265). The screens are redrawn to both. T6's cursor is on the urgent delivery,
with the designer's numbers checked against `WorldEvents.urgentDelivery`: 40 food to fill the
hold, 25 a unit here, 78 there (half again the buyer's 52), the 500 fee, and +5 with Selene; the
list gains the ordinary food delivery to Anvil Yards and the urgent count, and pays net. T4 plans
the same delivery: LATE 1D 9H 40M against Day 6 00:00, and the timeline gains the first payday, on
Day 7, which the old timeline put wrongly at 9.6 h. T3's objective is the delivery, LATE in amber
before its time, and its news Bea's light wound, the game's words; the captain's suit display now
reads LIGHT WOUND, as the designer's PER always had and my T3 never did. T2's wounded caution is
round 11's (`WOUNDED  Cole down   nobody free to treat`), and its objective takes the game's title,
which names the bounty's faction. T8 logs each alert once with its clear time, under the filters
ALERTS, IMPORTANT and INFO, and keeps 500, saved; its intercept is INTERCEPT, and Cole's wound is
the serious one T2 shows, so it is WOUNDED and not the light-wound notice it was. T9 takes section
10's pad: the aim, the shields, zoom, the lights, HUD focus, the map's buttons and the pause menu.
The OBJ break is now the spec's, so the two-line deviation is gone; a news slip past two lines is
cut with an ellipsis. Round 12's changes (levels by id, the caution row by 9.2's order, LOCKED ON,
air under 30% oxygen, a wound being treated as advisory) left the drawings as they were: their
cautions are already in 9.2's order, and no screen has a hostile locked on that isn't the target.

| Spec | Note sent this round | Landed |
|---|---|---|
| Task (OBJ, TRM) | The game's bounty title names the target's faction: `Bounty: Raider 'Vex' of the Void Raiders` (`ContractPostings.bounties` builds the target as label + " of the " + faction), so OBJ's sample row takes two lines in Z3. The game names a rails pirate `Pirate raider 3` (`HostileSpawns`) and an ace by a callsign (`Black Sal`); 'Vex' is the spec's own. T2 and T8 draw the game's title with the spec's name | Round 13: the pirate corvette near Highport is `Raider`, of the Void Raiders, in the bounty, TGT, HLM and BRD; the alert samples use `Pirate raider 3`; OBJ breaks the bounty before `of the Void Raiders` |
| Task (TRM) | The terminal wireframe's bounty pays 5,000; the game's pays 7 500 (`BOUNTY_PAY` × (1 + tier) × `Difficulty`, which is 1 for a new ensign against a matched ship). T6 keeps 7 500 | Round 13: 7,500, +6, 3 days |
| Task (MAP.5) | The map wireframe's timeline leaves out the first payday, Day 7 00:00 (`ServiceBudget` pays a week after the career starts), which the 3.8-day transfer meets between the Moon's sphere and burn 2. MAP.5 lists paydays, so T4 shows it | Round 13: the payday at 3.4 days, between the Moon's sphere and burn 2 |
| Task (10, pad) | The pad still can't cut a door: B on the keyboard, while the pad's B is the missile's R (`GamepadKeys`), and section 10's gaps don't list it. T9 leaves its dash | Round 13: it could already: Back with R2 sends B (`GamepadKeys`), which cuts a door, calls the tow and selects the next burn; section 10 names it |

Round 13 took all four, and T2 and T5 to T9 are redrawn to it. The Raider is the Raider in the
world, on the target card (`CORVETTE, VOID RAIDERS` under the name, which TGT never shortens), on
the boarding panel, in the prisoners' heading and through the log, and the terminal's bounty row is
the game's title. The log loses its INTERCEPT: the Raider waits near Highport, and the rails'
traffic doesn't fly it, so it never moves to intercept (the stations spec says the same of it in
10.4), which takes the alert count to 8. T9's door cut is Back with RT, and B's other work is
there too: the tow, stranded, joins Abandon ship under ANY POST, and the console rows move under
the deck's, where they are used, so the third column fits again. T4 already had the payday.

**T10, the console panel at a gun.** The consoles row was the stations variation's alone (S9), so
the task variation now draws CON at the same mount, the starboard autocannon, docked at Highport
as in T7. It is anchored below the mount, where the hull meets space, on a leader, so the middle of
the screen stays the world's. It is the INT prompt's plate grown: the name engraved beside the
kapton strip, health, power and state on a glass, and the keys as caps, Enter under a toggle that
shows where the switch stands and V to take the gun. While it is open the prompt stands down, since
the panel carries its keys (a deviation in the README). Drawing it found two faults in screens
already drawn. T3's weapons console prompt offered E OPEN, but the spec has E and V both sit at
the helm and the weapons console, so it now reads V SIT, as the spec's `Helm   V sit` does. And T7
had Ada idle in the hold while Crew 3, a prisoner, lay seriously wounded and untreated: a ship's
crew treat the prisoners they hold (`CrewHealthSystem`), so Ada is treating a patient, the game's
words for it, and Crew 3's wound is being treated (`CrewMedical.condition`).

| Spec | Note sent this round | Landed |
|---|---|---|
| Task (CON) | A point-defense gun's panel could say what taking it costs: while the captain sits at a mount, that mount is in hand and hunts no missile (`PlayerGunnery.inHand`), so its side has only the other autocannon, which can't see across the hull. The stations spec's gun panel says so (10.2); CON lists only the switch and `V  take the gun` | Round 14: under `V  take the gun`, a point-defense gun's panel reads `point defense: in hand, it hunts no missile`, and the starter corvette's other autocannon can't cover that side |

**C11, the inspect card on a gun.** The consoles row had no context screen, since the context spec
gives a gun no panel: E at a gun takes it, and the system's numbers and switch are the inspect
card's (D2). C11 draws that card pinned on the starboard autocannon, docked at Highport on day 0,
with the P6 prompt beside the hull under the mount and, docked, the station panel (X1) in the right
column, all three drawn for the first time. X1's lines are the game's on day 0: four of the board's
five offers left once the delivery is taken, three of the day's three to five looking for a berth,
nothing to repair, the propellant at C1's 72%, and the starting 5 000 with an empty hold. Checking
it against the code raised five questions. The task designer's round 14 found a sixth, which the
context screens draw too: First Play's Raider flies with nobody aboard.

| Spec | Note sent this round | Landed |
|---|---|---|
| Context (X1) | The sample's standing names a level ("Trusted (+42)"), but the game names none: standing is a number for each faction. C11 writes STANDING +0 | |
| Context (X1) | The infirmary line when nobody was treated. C11 leaves it out, as it does a service the station doesn't offer. Should it say instead that nobody needed treating? | |
| Context (D2) | A gun's Type: the game's system type is TURRET_MOUNT and its console is "Gun". C11 gives the weapon on the mount (Autocannon Mk1), as H7 lists it | |
| Context (P1) | The helm field while docked: the game's bridge line has no docked case, so a helmsman at the helm "holds the heading" (`GameHudText.bridgeLine`). C11 draws that. Should it be empty, or say docked? | |
| Context (H7, P6) | H7 says a point-defense mount is never in hand, but at a gun seat the game puts that gun in hand, point defense too (`PlayerGunnery.inHand`), and a gun aimed by hand hunts no missile. P6 at a point-defense gun could say what taking it costs, as the task and stations specs now do | |
| Context (X2, 11.13) | First Play's Raider flies with nobody aboard: `GameScenario` spawns it with no crew and no cargo, while `HostileSpawns` crews the rails' pirates. Once it is disabled, the first strip order takes it (`Boarding.takeUndefended`), with no party, no defender, no prisoner and no cargo. X2's sample and 11.13, and so C4 and C6, assume a crewed Raider; a rails pirate ("Pirate raider 3") would have one. 11.13's P5 also keeps a bounty step, but the game pays a bounty the moment its ship is beaten (`ContractBoard`) | |

**Round 14** took the CON note, and the designer's own review against the code found two more
things on drawn screens. TGT's states follow the game: a hostile with nothing locked is `not
engaged`, which the Raider stays until the captain fires on it, so T2's Raider, firing on the ship,
is still `fighting`. And First Play's Raider flies with nobody aboard and carries no cargo
(`GameScenario`), so the first strip order takes it (`Boarding.takeUndefended`), with no party, no
prisoners and an empty hold. T5, T7, T8 and T10 are redrawn to it. T5's state is TAKEN with nobody
aboard. Ada strips the laser while Cole waits beside the mount to carry it, the list shows the four
dearest parts at round 14's prices and work with a row for the other eleven, and the prize reads
10 650 whole and 2 760 once the marked laser and harpoon launcher are off. Its engines lose the EVA
ONLY I had given them: the game finds a part out of reach only once its strip is ordered
(`Salvage`), so an unmarked engine reads as strippable, as the sample has it. The panel is now 408
wide, so the game's longest status fits beside the work. T7 holds no prisoners, so the berths are 4
of 8, Ada is idle, the game's word for it (`Idle`), and Cole's record is one ship beaten, with the
marine skill at its starting 50. T8's log loses the forced airlock and the prisoners, and the Raider
is taken 36 s after the latch, with the laser in the hold two minutes on. T10 loses the prisoners in
the hold and gains the point-defense line under V. The same finding takes the prisoners out of my
stations screens, and the context screens wait on their designer.

**The slate's Log and the gun panel.** S8 draws the slate on its Log tab, docked after the fight,
the latch and the way home, and S9 the gun panel at the forward starboard autocannon, docked. The
Log's lines are the game's where the game has one: the disablement notice is "Raider is disabled:
reactor down" (`GameBootstrap`), the handover "Crew 4 (Void Raiders) handed over: +400 cr" for
each prisoner (`Prisoners`), the breaking off is `HostileTraffic`'s, and the short's 45 s
`DamageStates.AUTO_RESET`. The gunner's callouts are 5.4's: in range and missile down. Today's
window at a gun is titled "Gun" (`ShipConsoles`) and reads the health and one of ONLINE, OFF, NO
POWER or DESTROYED (`ConsoleWindow`); S9's READY is 8.3's word for a gun. Seated at a gun, just
that gun is in hand, point defense too (`PlayerGunnery.inHand`), a mount aimed by hand hunts no
missile (`WeaponControlSystem`), and let go it hunts again; V stands the captain up with "You
leave the gun" (`GameInput`). The two autocannons face their own sides with 300° arcs, so each is
blind across the hull to the other's side. One thing the check found is the game's, not the UI's:
point defense picks the missile due soonest within its reach whatever its arc
(`WeaponControlSystem.pickMissileThreat`), so the port autocannon can hold on a missile from
starboard that it can't turn to, while one inside its arc waits.

| Spec | Note sent this round | Landed |
|---|---|---|
| Stations (10.4, 13.3) | The Log is "every alert and callout". S8 keeps one line per alert: the callout if someone called it, with the alert's mark, or else the alert's own text; then its clearing, as 13.3's record at the alert's severity, hollow. Is that the rule? | Round 16: yes, in 10.4; a line revised in place is rewritten, an escalation adds a line, and advisories and crew lines answering an order are logged, repeats counted ("x3") |
| Stations (13.6) | Responses are the player's own answers, so S8 doesn't log them. Should it? | Round 16: no; what an order changes that matters later, such as a tow and its cost, is an advisory and is logged (13.6) |
| Stations (10.4, 16.3) | The Log's filters have no keys. S8 steps the focused row with ← and → under `FocusNav` and scrolls with ↑ ↓, and takes severity as a threshold, each position keeping what is more serious, as the context spec does | Round 16: as drawn (10.4) |
| Stations (13.3, 13.4) | 13.3 gives one cleared record ("Fire in the bridge out after 1:42"). S8 writes the rest "<alert>, cleared after m:ss", with alert texts of my own ("Raider firing on you", "Pirates inbound", "1 system without power", "Missiles inbound"). Could the catalogue give each alert its text and its record? | Round 16: 13.7 gives every alert a raised text and a record ("Raider firing on you: cleared after 2:40"), and S8 uses them |
| Stations (7.2, 13.5) | Today's notices reach the ticker as advisories, SHIP speaking ("SHIP: docked at Highport, port 2"). Which station tags each, and is the notice's own text the line, as S8 has it? | Round 16: 13.4's station tags each (COMMS for docking and salvage, CMD for boarding progress), the speaker rule picks who speaks, and the line is the notice's own text where it fits 50 characters; a notice with no station goes under CMD (13.5) |
| Stations (E) | Walkthrough E's clock reads Day 3, but First Play's fight is on day 0, S5's clock; S8 keeps day 0 | Round 16: E reads Day 0 00:06:44 (`GameHudText` counts days from 0) |
| Stations (10.2, 3.5) | The gun panel: a point-defense mount in hand hunts no missile, so S9 says so before the captain sits, with what the other autocannon can't cover. S9 keeps the console window's switch on Enter, though 10.2 doesn't list it and Engineering's Systems tab has the switches, and gives point defense's mode as AUTO. Confirm all three? | Round 16: all three, in 10.2; the mode reads PD AUTO, then YOURS in hand, in the WEAPONS page's words; the arc finding is passed on as a game issue |

Round 16 took all seven, and the screens are redrawn to it. S8's records are 13.7's ("Missiles gone
after 0:13", "Pirates broke off after 4:47", "Aft starboard gun back after 0:45"), its notices
come under 13.4's stations in the game's own words where they fit (the docking and the chaingun
under COMMS; the boarding, the prisoners and the raider's disablement under CMD, spoken by Cole
where Cole deals with it), and the fight runs on First Play's day 0 with walkthrough E at 00:06:44,
so S2's clock reads it and S5's latch is at 00:27:10, its line under CMD. The raider's breaking off
is the record of the PIRATES caution it clears, so S8 logs it once. S9's mode reads PD AUTO, then
YOURS in hand. Drawing it raised three small points.

| Spec | Note sent this round | Landed |
|---|---|---|
| Stations (13.4, 13.7) | The raider's breaking off is today's INFO notice ("Raider is breaking off", `HostileTraffic`) and also what clears PIRATES ("Pirates broke off after 4:47"). S8 logs the record only. Does the notice fold into the record? | Round 17: only when it is the same event. PIRATES now holds while a hunting pirate is beyond 50 km and clears when the last one closes ("Pirates closed to 50 km after 8:00") or turns back first ("Pirates broke off after 8:00"); a raider breaking off in the fight is its own TAC advisory (10.4, 13.4, 13.7) |
| Stations (5.6, 13.5) | Comms' row says station messages come through the ticker "as COMMS lines from the station's name", but 13.5 has them spoken by SHIP with nobody at Comms ("COMMS  SHIP: docked at Highport, port 2."). S8 follows 13.5 | Round 17: a station speaks its own messages in its own name ("COMMS  Highport: clearance granted, port 2."); the ship's own Comms events, such as the latch, are SHIP's, or the crew member's at Comms (5.6, 7.2, 13.5) |
| Stations (5.4) | Is the callout "target struck" a hit on the target, or the target striking (surrendering, as 8.3's target states use the word)? The game posts "cuts thrust and surrenders" for the one and nothing for the other. S8 calls neither | Round 17: "target struck" is gone. TAC calls out what hostiles do in the game's words ("Raider cuts thrust and surrenders", "Raider is breaking off"); the target's states are breaking off and `DisabledComponent.Reason`'s labels (5.4, 8.3). What raiders do in a fight and other ships' disablement are TAC advisories, the pirates' news CMD's (13.4, 13.5) |

Round 17 took all three, and S8 is redrawn to it and then to round 18. Highport's clearance is now Highport's own line
("Highport: clearance granted, port 2."), and the Raider's disablement moves to TAC, spoken by Cole.
Checking the fight against round 17's pirates rule found my own error: First Play's raider is a
ship `GameScenario` places beside Highport, turned on the player by `provokeRaider` once they are
2 km clear. It is not a pirate on the rails, so `HostileTraffic` never flies it: it posts no
intercept and never breaks off, and fights until it is beaten. So S8 loses the intercept, the
PIRATES caution and its record, and the hit that shorts the aft starboard gun is the chaingun's,
not a parting shot. The bounty now has its line: the game pays it the moment the Raider is beaten
("Contract done: Raider of the Void Raiders is beaten. Paid 7,500 cr, standing +6 with Terran
Patrol Authority", `ContractBoard.end`), an advisory under CMD (13.4), too long for 50 characters.
Round 18 gave its form, which S8 uses: a contract's end names the contract, how it ended and what
it paid ("Raider bounty done: +7 500 CR, +6 standing"), and the whole sentence stays on the slate's
Contracts tab, under Ended. Round 18 also has the crews hold fire on a disabled ship
(`GunCrews.hostile`); no screen shows a crew gun on the beaten Raider. That also showed the slates' credits were wrong: S5 read 5 000 during the
boarding, after the bounty had paid, and S6, S7 and S8 read 2 950, a figure with no source. They
are now the game's sum: 12 500 aboard the Raider (5 000 to start and the bounty's 7 500), and
13 700 at Highport once the three prisoners are handed over.

| Spec | Note sent this round | Landed |
|---|---|---|
| Stations (10.4, 13.4) | 10.4's example has "the Raider" breaking off after closing to 50 km. First Play's raider can do neither: it is placed beside Highport (`GameScenario`) and `HostileTraffic` doesn't fly it, so it posts no intercept and never breaks off. The example would hold for a pirate on the rails ("Pirate raider 3 is breaking off"). My S8 had the First Play raider intercept and break off, which is where the example came from; S8 no longer does | Round 18: the example is "Pirate raider 3 is breaking off", and 10.4 says First Play's Raider posts no intercept and never breaks off, fighting until it is beaten or surrenders (5.4, 10.4) |

**The stations screens without the prisoners.** Task round 14's finding holds for my stations
screens too: First Play's Raider flies with nobody aboard (`GameScenario` gives it no crew and no
cargo), so the first part marked takes it (`Boarding.takeUndefended`), and the game's boarding
window hides the party's orders and opens the salvage at once (`BoardingWork`). My S5 to S8 had
boarded a crewed raider, which was my error, not the spec's. S5 now draws the Boarding tab as the
game would fill it for this ship: the state, why no party goes, and the salvage, most valuable
first, with the cursor on the chaingun that S6 fits. S7's Cole has one ship beaten, the marine
skill at its starting 50 and morale at 75%, since a taking counts only for the crew aboard the ship
or on their way across (`CrewCareer`). S8 loses the party, the forced airlock, the prisoners and
their handovers, and logs "Raider is taken" 16 s after the latch, with the chaingun's line in the
game's own words, which fit 50 characters. With no handovers, the credits at Highport are 12 500
(5 000 to start and the bounty's 7 500) in S6, S7 and S8, not the 13 700 above. Drawing S5 raised
one point for the stations designer.

| Spec | Note sent this round | Landed |
|---|---|---|
| Stations (10.4) | The Boarding tab gives "the boarding party (send, recall)". For a disabled ship nobody defends, such as First Play's Raider, the game hides the party's orders and opens the salvage at once, and the first part marked takes it (`BoardingWork`, `Boarding.takeUndefended`). S5 keeps the PARTY row to say why none goes ("NONE NEEDED: NOBODY ABOARD", "THE FIRST PART MARKED TAKES IT") and opens the salvage. Is that the tab's rule for an undefended ship? | |

## Next

The windows the overhaul skipped, as above. The three HUD variations themselves stay converged. If
a spec changes again (the other artists' rounds can still move them), the new version is drawn and
checked the same way.

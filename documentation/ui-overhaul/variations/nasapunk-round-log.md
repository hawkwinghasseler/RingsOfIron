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
| Nasa-punk × Ship Stations | [`nasapunk-stations/`](nasapunk-stations/README.md) | round 14 | Converged in round 5 |
| Nasa-punk × Context-driven | [`nasapunk-context/`](nasapunk-context/README.md) | version 14 | Converged in round 5, and again at versions 13 and 14 in round 7 |
| Nasa-punk × Task-driven | [`nasapunk-task/`](nasapunk-task/README.md) | round 9 | Converged in round 5; round 9 drawn in round 6 |

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

## Next

The windows the overhaul skipped, as above. The three HUD variations themselves stay converged. If
a spec changes again (the other artists' rounds can still move them), the new version is drawn and
checked the same way.

# Nasa-punk × Context-driven

The context-driven functional spec (`functional-context.md`, version 5 when drawn, on branch
`claude/project-thread-ukjzrp`) skinned in the Nasa-punk style
(`../../nasapunk/style-nasapunk.md`), with its words from
[`../../nasapunk/abbrev.md`](../../nasapunk/abbrev.md). Mockups are 1280 x 800 UI units drawn at 2x; sources are
the HTML files here, using `context.js` and the shared kit in `../../nasapunk/mockups/`. From
`documentation/ui-overhaul`, render with `node nasapunk/mockups/render.js variations/nasapunk-context`
and check with `node nasapunk/mockups/check.js variations/nasapunk-context` (text overlaps, text
touching a frame, text cut by its glass, and the 12-unit floor).

Round history is in [`../nasapunk-round-log.md`](../nasapunk-round-log.md).

## How the style maps the spec

| Spec element | Nasa-punk container |
|---|---|
| P1 status strip | One long glass across the top. Each field has a fixed place and its reserved width, so a field that empties leaves dark glass and nothing moves. BATTLE STATIONS is an amber lens set into it. |
| P2 caution and warning | An EICAS list of rows 24 tall: severity shape (◆ warning, ▲ caution), code, detail, handling, timer. Handling is a cyan YOU flag (needs the captain), a white dot per crew member on it, or BACK with the recovery time. Harm timers are white, recovery timers dim. The sixth row is "+n MORE" with a chevron, since a click opens Ship › Systems. |
| Master alert cue | MASTER WARN and MASTER CAUT lenses, 24 tall since a click acknowledges them, on a hood across the top of the seat's primary instrument. On deck, docked or latched the two lenses stand alone at the bottom centre, but only while one is lit. A lit lens shows the newest code. |
| P3 threat alert | A red-framed glass with a lit lamp carrying the missile glyph, the count at 22, the decoy with its key cap and point defense on the second line. |
| P4 message feed | Comm-loop slips: a source tag, then the message in sentence case (speech and events are prose; instruments are caps). |
| P5 objective | A two-line slip with a kapton tab. |
| H1 flight display | A DU under the hood. The frame is an FMA box, green while picked automatically and white when pinned. The speed is in that frame, beside the assist box. Below them are the thruster pictogram, delta-v, and the propellant tape. |
| H2 navigation | Label, value and time columns: ORBIT, NAV in magenta with its ring, NEXT with its diamond, PLAN. Values at 15 with units dim at 12. |
| H4 ship condition | Lenses in a bezel, dark when well: three on a ship with no shield emitter. Expanded on deck, each lens heads a row with its reading and a tape. |
| H5 scope | A 200 x 200 DU with − and + at 24, the range in the corner, and off-scale contacts as rim ticks at their bearing. In cruise it folds to a 48 x 48 key with the count. |
| C1 orders menu | A mode control panel, as on a flight deck, its groups in the spec's order read across and down: HELM, then AUTOPILOT and CREW, then SHIELDS and SHIP. The helm orders are interlocked lit keys under their number caps. Autopilot and crew orders are lit keys whose lower line is their state. The trim is a five-way selector: the hull in miniature with a mark on each side it can lean to and a dot for even, the chosen one lit. Abandon and the tow sit under hinged guards with striped rims, last and furthest from the helm keys. A barber-pole strip marks an order that can't be given now, and the panel's foot gives each reason once with the keys it blocks. |
| F1 lock card | The name in the allegiance colour with the lock brackets. HOSTILE is a box, and FIRING ON YOU a lit red flag. Then RNG, REL, what it is doing and what is known. |
| F2 weapons rack | One row per mount: mount, weapon, state, hit chance as cyan pips with the number and word, rounds left, who fires it (YOU in cyan, PD). Point defense engaging reads green, like an engaged autoflight mode. |
| F3 shield control | A ring of eight sectors around the ship's outline, each as wide as the cloud there. The trim is a cyan arc outside the side leaned to, and the ring itself is the control. Beside it: cloud mass, RAISED, TRIM, the emitter and its heat against the trip. |
| F4 threat list | Rows 24 tall (a click locks): the missile's impact time, bearing and PD ON, then the other hostiles. |
| X2 boarding panel | A DU of seven rows: the other ship and how it is held, its state on a row of its own, the party, salvage, cargo and prisoners, then K BOARDING and U CAST OFF as buttons 26 tall. |
| X3 emergency card | A hazard-striped card: the cause at 22, then the ways out as the same guarded switches as C1, with the tow's costs. Armed, a switch's guard stands open, the switch lights amber, and the card's foot says what the second press does while a bar runs out its 3 s. |
| D1 captain vitals | The suit's own chest display (the DCM): suit-grey bezel, orange tab. |
| D3 hazard markers | Tags on the deck by P2's code or the casualty's name, with a leader to the spot. |
| The ships | The game's own hull outlines (`HULL_ART`), plate grey for your ship, red-brown for hostiles, dark for a beaten one. |

## Screens

![Helm, cruising, orders open](c1-helm-cruise.png)

**C1, helm, cruising in Earth orbit with the orders menu open (11.1 and 11.12).** Nothing is
wrong, so P2, P3 and P4 draw nothing. H4 is three dark lenses and H5 has folded to a key. No helm
order is running, so P1's helm field is empty and no helm key is down. Four helm orders need a Nav
in the bubble, the shield orders need an emitter, and the tow is refused while the ship can fly
home. Each carries the strip, and the foot says why once. P5 has the delivery: 40 food to
Shackleton Ice.

![Fire control](c2-fire-control.png)

**C2, the weapons console of a gunship (11.5).** Bea holds the helm. One harpoon is inbound with
point defense on it, T−0:04 in F4, so P3 keeps only the count and the decoy. The shield ring leans
to the bow. In F2 the nose cannon has the hit chance on the Lock, the autocannons are point defense
(one engaging, one idle), and the harpoon launchers show their rounds. The scope is at 10 km with
Raider 2, 14 km off, as a rim tick, and the world gives it an edge marker at the same bearing.

![Deck emergency](c3-deck-emergency.png)

**C3, on deck, the corvette disabled (11.7, version 5).** The reactor is damaged, so no power is
due back by itself and X3 offers the ways out; the hold is at vacuum. Bea fights the fire in the
bridge and Cole treats Ada, so nobody is free for the rest: P2 ranks EMERG O2, VACUUM and NO POWER
as needing the captain, then CREW CRIT and FIRE with their one worker each, and folds SMOKE into
"+1 MORE". The captain, suited in the hold, has pressed B once: the tow is armed, its guard up and
its bar running out. P1 and P5 are dimmed under X3; the master cue stays lit at the bottom centre.
D3 tags the fire, the vacuum and Ada. Bottom left is H4's deck face over the DCM.

![Boarding, latched](c4-boarding.png)

**C4, latched to a taken raider (11.13).** Latched, the screen is the docked one. P5 has the
bounty's next step, X2 sits where the station panel would be, with the raider's state on its own
row, and H4 has its quiet lights. The raider lies the way the game docks two ships: half a turn
apart, port to port. That puts it over the corvette's port side, upside down and nose aft. Its lock
brackets and name stay, but there is no lock card and no range.

## Round 3 critique

What works:

- The 12-unit floor reshaped the variation for the better. With no room for bezel titles, each
  widget is known by its zone and its face, and the labels that remain are ones a reader uses.
- C1 now reads in the spec's order, and the most dangerous orders sit in the far corner under
  guards, which is where a flight deck puts them.
- The armed state completes the guarded order: C3 shows the guard up, the amber switch and the
  window running out, and C1's switches are the same hardware.
- P2's handling column reads at a glance: cyan for you, white dots for crew, dim BACK for a
  condition clearing itself.

Problems in the spec, sent to the designer (the first eight were found in round 2 and are still in
version 5; the last two in round 3):

- **11.7's crew.** "Of the four crew, Ada is down, one tends her and two fight the fire." The
  starting corvette carries three (`GameScenario`: Ada, Bea and Cole), so with Ada down and Cole
  tending her, Bea alone is on the fire. Drawn with one dot on FIRE and one on CREW CRIT.
- **X2's "PARTY 4 aboard"** is more people than the corvette carries. Drawn as 2 aboard.
- **P4's sources** lack Boarding, which 11.13's feed line ("the Raider is taken") needs. Drawn with
  a BOARDING tag.
- **11.5's hit chance.** The heavy cannon opens fire at 50% around 3.7 km (`HitChance.OPEN_FIRE`),
  so "62% fair" needs the Lock at about 3 km, not 7.7. Drawn at 3.4 km.
- **The rack in wireframe 05.** The autocannons are point defense (`autocannon_mk1`), so at the
  console they read PD, not YOU; the mounts are FWD PORT, FWD STBD, AFT PORT and AFT STBD; a
  harpoon launcher's cooldown is 1 s, not 4 s.
- **W3 repeats H2.** The burn cue's countdown is the same datum as H2's NEXT line, on screen at the
  same time. Drawn as the spec says.
- **H5's 20 km.** Wireframe 05 heads the scope "Contact scope · 20 km", which is not one of the
  scope's steps (`RadarScope.RANGES`: 0.5, 2, 10, 50, 250 and 1 000 km). Drawn at 10 km, with
  Raider 2 as a rim tick. The steps jump from 2 to 10 km while a fight happens inside 4 km (the
  corvette's laser reaches 3, point defense 4), so a 5 km step would be worth asking for.
- **The raider in wireframe 13** sits ahead of the corvette's nose. Latched or docked, the game
  holds two ships half a turn apart, port to port (`CorvettePairDockingTest`), so the raider lies
  over the port side, upside down and nose aft. Drawn that way.
- **What the impact time is.** Version 5 changed the missile times to "what a harpoon or dart can
  fly (T-4 s at 7.7 km, not T-37 s)". Both are readings the game gives in one flight. A harpoon
  closes at 30 m/s² (`MissileDef.closingBurn`; `weapons.toml` leaves the harpoon's at that) after
  a 0.6 s dark start, so from 7.7 km it lands about 22 s after launch. The time the game shows
  (`CombatThreats`) is closest approach at the present relative speed, which runs long while the
  harpoon burns: it reads 37 s 5 or 6 s after launch, 20 s with 12 s left, 4 s with about 4 s
  left. A countdown drawn from it falls faster than the clock. The spec should say the time is an
  estimate, or ask for one with the burn in it (new).
- **P2's code spellings.** HULL DMG, SHLD DOWN, SHLD WEAK and DOCK STRN shorten words the spec
  spells whole elsewhere (the DAMAGE page, the SHIELDS orders), and the whole words fit P2's code
  column at 13 bold (HULL DAMAGE is 94 units of 96). LOW O2 and LOW PRESS also take the other
  order from PROP LOW and POWER LOW. The word list writes them HULL DAMAGE, SHIELD DOWN, SHIELD
  WEAK, DOCK STRAIN, O2 LOW and PRESS LOW.

Found in my own screens and fixed this round:

- C2 drew Raider 2 on screen at 3.5 km while F4 gives it 14 km. At C2's zoom 14 km is off
  screen, so it is an edge marker now, with no range, as W1's off-screen rule has it.
- C2's harpoon read T−0:02 at 1.3 km from where it would strike, a reading it can't give there
  (see above); it reads T−0:04 now, and its bearing is the one the world shows, 050.
- C2's scope put the harpoon at 1.5 km against 1.8 km in the world; it sits at its world offset.

What is still wrong in these screens, for round 4:

- A gunship's C1 is the only place the trim selector would be live; no screen shows it lit.
- C3's deck is still dark enough that the bridge's consoles read only by their outlines. An
  emergency floor-path strip, as aircraft have, would help, but it is world art, not HUD.

## Deviations from the spec

- **Thousands take a thin space** ("1 240 KM", "7 672 M/S"), the style guide's rule, where the
  spec writes a comma.
- **Countdowns are M:SS** (`T−0:04`, P2's `3:40`), where the spec writes seconds in places
  ("T-3 s"). A time left over a day is days and hours (`1D 10H LEFT`), as the task spec writes it,
  where this spec writes "1.4 d".
- **The clock carries its seconds** (`DAY 0 · 01:12:40`), as the game's clock does.
- **Instruments are in caps**, as the guide has it; P4's messages are prose and stay in sentence
  case.
- **No bezel titles.** The floor applies to legends, and titles at 12 would crowd the bezels. The
  zone says what a widget is.
- **The master cue is hidden on deck, docked or latched while it is unlit.** Dark there, it would
  be hardware with nothing to say over the deck.
- **No hand mark in F2 at the console.** Every mount is in hand there, so the mark carries nothing.
  It belongs to the gun seat's single row.
- **No fire arcs at fire control.** The nose cannon's arc is 270° and would cover the view. The
  hit lane and the lead reticle (with its line back to the target, as `LeadReticle` draws it) show
  where the shot goes.

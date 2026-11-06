# Nasa-punk × Context-driven

The context-driven functional spec (`functional-context.md`, version 9 when drawn, on branch
`claude/project-thread-ukjzrp`; version 9 changed only H6's sample and wireframe 04, neither on
these screens) skinned in the Nasa-punk style
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
| P3 threat alert | A red-framed glass with a lit lamp carrying the missile glyph, the count at 22, the decoy with its key cap and point defense on the second line. At fire control F4 has each missile's time, its shooter and point defense, so P3 keeps the count and the decoy. |
| P4 message feed | Comm-loop slips: a source tag, then the message in sentence case (speech and events are prose; instruments are caps). |
| P5 objective | A two-line slip with a kapton tab. |
| H1 flight display | A DU under the hood. The frame is an FMA box, green while picked automatically and white when pinned. The speed is in that frame, beside the assist box. Below them are the thruster pictogram, delta-v, and the propellant tape. |
| H2 navigation | Label, value and time columns: ORBIT, NAV in magenta with its ring, NEXT with its diamond, PLAN. Its numbers sit inside lines, so they take the line's 13 (version 8's rule for values), with units dim at 12. |
| H4 ship condition | Lenses in a bezel, dark when well: three on a ship with no shield emitter. Expanded on deck, each lens heads a row with its reading, its figure at 15 and a tape. POWER gives the store, then the output, with the rating beside it only while damage holds the output under it. |
| H5 scope | A 200 x 200 DU with − and + at 24, the range in the corner, and off-scale contacts as rim ticks at their bearing. In cruise it folds to a 48 x 48 key with the count. |
| C1 orders menu | A mode control panel, as on a flight deck, its groups in the spec's order read across and down: HELM, then AUTOPILOT and CREW, then SHIELDS and SHIP. The helm orders are interlocked lit keys under their number caps. Autopilot and crew orders are lit keys whose lower line is their state. The trim is a five-way selector: the hull in miniature with a mark on each side it can lean to and a dot for even, the chosen one lit. Abandon and the tow sit under hinged guards with striped rims, last and furthest from the helm keys. A barber-pole strip marks an order that can't be given now, and the panel's foot gives each reason once with the keys it blocks. |
| F1 lock card | The name in the allegiance colour with the lock brackets. HOSTILE is a box, and FIRING ON YOU a lit red flag. Then RNG, REL, what it is doing and what is known. |
| W5 lead reticle | A cyan ring with four ticks where the gun's solution puts the shot. In this game's fights it sits a few units off the target, so it rings the target inside its lock brackets. |
| F2 weapons rack | One row per mount: mount, weapon, state, hit chance as cyan pips with the number and word, rounds left, who fires it (YOU in cyan, PD). Point defense engaging reads green, like an engaged autoflight mode. |
| F3 shield control | A ring of eight sectors around the ship's outline, each as wide as the cloud there. The trim is a cyan arc outside the side leaned to, and the ring itself is the control. Beside it: cloud mass, RAISED, TRIM, the emitter and its heat against the trip. |
| F4 threat list | Rows 24 tall (a click locks): the missile's impact time, the ship that fired it in red and PD ON, then the other hostiles. |
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
Shackleton Ice. The burn arrow carries no countdown, which is H2's NEXT line (version 6), and the
periapsis is half an orbit after the apoapsis.

![Fire control](c2-fire-control.png)

**C2, the weapons console of a gunship (11.5).** Bea holds the helm. One harpoon is inbound with
point defense on it, T−0:03 in F4 with its burn counted and the Raider named as its shooter, so P3
keeps only the count and the decoy.
The Raider is kiting at gun range, 3.4 km off and easing in at 12 m/s; a kiter doesn't circle, so
its motion relative to the ship runs along the line of sight, and the heavy cannon's 2.2 s shot
leads it by 27 m: the lead reticle rings it. The shield ring leans to the bow. In F2 the nose
cannon has the hit chance on the Lock, the autocannons are point defense (one engaging the
missile, one ready), and the harpoon launchers are ready with 3 and 4 left. The scope is at 5 km,
version 6's new step, with Raider 2, 14 km off, as a rim tick, and the world gives it an edge
marker at the same bearing.

![Deck emergency](c3-deck-emergency.png)

**C3, on deck, the corvette disabled (11.7).** The reactor is damaged, so no power is due back by
itself and X3 offers the ways out; the hold is at vacuum. Bea fights the fire in the bridge and
Cole treats Ada, so nobody is free for the rest: P2 ranks EMERG O2, VACUUM and NO POWER as needing
the captain, then CREW CRIT and FIRE with their one worker each, and folds SMOKE into "+1 MORE".
The captain, suited in the hold, has pressed B once: the tow is armed, its guard up and its bar
running out. P1 and P5 are dimmed under X3; the master cue stays lit at the bottom centre. D3 tags
the fire, the vacuum and Ada. Bottom left is H4's deck face over the DCM: the hull at 81%, the
store empty and the reactor giving 0 of its 3.0 MW, and 42 kg left of the 60 kg emergency oxygen.

![Boarding, latched](c4-boarding.png)

**C4, latched to a taken raider (11.13).** Latched, the screen is the docked one. P5 has the
bounty's next step, X2 sits where the station panel would be, with the raider's state on its own
row, and H4 has its quiet lights. The raider lies the way the game docks two ships: half a turn
apart, port to port. That puts it over the corvette's port side, upside down and nose aft. Its lock
brackets and name stay, but there is no lock card and no range.

### The windows the overhaul skipped

The shell's pages are in [nasapunk-shell](../nasapunk-shell/README.md); these are the spec's own
full-screen modes, drawn after the first rounds.

![The map, burn 1 selected](c5-map.png)

**C5, M1, the map as a full-screen mode (10.1).** P1 stays above; below it come the mode's frame,
a title row, the Plan panel on the left and Target and traffic on the right, with the timeline
between them under the map and the foot row with the one-line feed and the close keys. It shows the
task variation's transfer, here accepted: the planned path solid magenta, and burn 1 selected in
cyan with its handles and its result, an apoapsis at the Moon's distance. The arrival still misses
the delivery's deadline, so the total carries LATE and the timeline shows the deadline before the
arrival. The timeline's cursor is on burn 1, the next event, with its warp-to.

![Boarding, the party aboard](c6-boarding.png)

**C6, M4, the boarding mode (10.4), two minutes before C4.** The raider's reactor is down, the
dock's door was pried (the raider has no power to cut against), and the party has swept the hold
and is fighting in the bridge. The party is the game's: the ship's marines, so Cole alone, at 64%
and in a pressure suit whose air stays full while the bridge holds pressure. The plan of both
ships shows who is where, with the rooms swept and fighting. The state line is the game's own
("Boarding: 1 of ours aboard, 1 of theirs standing"). SEND PARTY and STORM are barred while the
party is aboard, with the reason once on the strip under them, and RECALL is live. Salvage, cargo
and prisoners wait, each saying what it will offer once the ship is taken.

![Station, market](c7-station.png)

**C7, M3 on its Market tab (10.3), docked at Highport on day 0, loading food for the delivery.**
P1 stays above, then the title row with the tabs the station offers on their keys (Highport sells
no ships, so there is no Shipyard tab), the tab's panel, the rail every tab shares, and the foot
row with the feed. Highport is an agricultural dome, so it makes food and uses water and
machinery, at today's prices. Bought cargo crosses the dock in the loaders' arms, so a buy is an
order until it is in the hold: HELD and ON ORDER read the game's "12 (+10)". The order is under
way with its progress, and the foot row carries the game's own line for it. Each row has today's
four trade buttons as lit push buttons; SELL is barred where nothing is held, with the reason
once at the foot. The rail has credits, standing, the hold with what is coming, tanks, the
infirmary, and the delivery the cargo is for.

## Round 5 critique

Version 8 took both round 4 notes: F1's kiter now moves along the line of sight (closing 12, REL 12,
course 235°, shot 2.2 s, as drawn), and H5's 5 km step says fights reach 5 km. W5 adds that the
lead reticle sits inside the lock mark at fight zooms, which is how it is drawn. From the other
critiques it names each missile's shooter in F4, says which numbers are values drawn at 15 (12.4),
moves the master cue above the rack at fire control, and gives H4's power line the output with the
rating only under damage. All are drawn.

What works:

- The 12-unit floor reshaped the variation for the better. With no room for bezel titles, each
  widget is known by its zone and its face, and the labels that remain are ones a reader uses.
- C1 reads in the spec's order, and the most dangerous orders sit in the far corner under guards,
  which is where a flight deck puts them.
- The armed state completes the guarded order: C3 shows the guard up, the amber switch and the
  window running out, and C1's switches are the same hardware.
- P2's handling column reads at a glance: cyan for you, white dots for crew, dim BACK for a
  condition clearing itself. The whole-word codes fit their 11 characters.
- At fire control each datum has one home: the missile's time, shooter and point defense are F4's,
  the split of the launchers F2's, the Lock's motion F1's, and F1's numbers agree with the reticle.
- Numbers differ in size for a reason: a value drawn on its own (the figures beside H4's bars,
  F3's heat) reads at 15, a DU's one lead figure (H1's speed, F3's cloud) is larger, and a number
  inside one of H2's lines takes the line's 13.

Problems in the spec: none found this round. Versions 8 and 9 draw as written.

Found in my own screens and fixed this round:

- **H4's life support row read EMERGENCY**, the spec's word, though the word list gives an
  instrument EMERG and P2's code and the master cue on the same screen say EMERG O2 for the same
  reserve. The row's reading is now EMERG O2, 42 KG: one name for one datum.

Two notes stay open, neither a fault in what is drawn:

- C1's trim selector is only live on a ship with an emitter, and C1 is the corvette, so the
  selector is drawn struck through. F3 shows the trim live at fire control; a gunship's orders
  menu would be a fifth screen, for coverage.
- C3's deck is dark enough that the bridge's consoles read only by their outlines. An emergency
  floor-path strip, as aircraft have, would help, but it is world art.

**Converged.** No meaningful criticism remains for Context-driven × Nasa-punk: version 9 and these
four screens agree.

## Deviations from the spec

- **Thousands take a thin space** ("1 240 KM", "7 672 M/S"), the style guide's rule, where the
  spec writes a comma.
- **Countdowns are M:SS** (`T−0:03`, P2's `3:40`), where the spec writes seconds in places
  ("T-3 s"). A time left over a day is days and hours (`1D 10H LEFT`), as the task spec writes it,
  where this spec writes "1.4 d".
- **The clock carries its seconds** (`DAY 0 · 01:12:40`), as the game's clock does.
- **Instruments are in caps**, as the guide has it; P4's messages are prose and stay in sentence
  case.
- **H4's reserve reads EMERG O2**, where the spec writes EMERGENCY: the word list's instrument form,
  and the name P2 and the master cue give the same reserve.
- **No bezel titles.** The floor applies to legends, and titles at 12 would crowd the bezels. The
  zone says what a widget is.
- **The master cue is hidden on deck, docked or latched while it is unlit.** Dark there, it would
  be hardware with nothing to say over the deck.
- **No hand mark in F2 at the console.** Every mount is in hand there, so the mark carries nothing.
  It belongs to the gun seat's single row.
- **No fire arcs at fire control.** The nose cannon's arc is 270° and would cover the view. The
  hit lane and the lead reticle show where the shot goes. `LeadReticle`'s line back to the target
  is 2 units long at C2's lead, so it doesn't show.

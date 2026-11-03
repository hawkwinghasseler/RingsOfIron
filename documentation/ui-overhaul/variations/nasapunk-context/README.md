# Nasa-punk × Context-driven

The context-driven functional spec (`functional-context.md`, version 4 when drawn, on branch
`claude/project-thread-ukjzrp`) skinned in the Nasa-punk style
(`../../nasapunk/style-nasapunk.md`). Mockups are 1280 x 800 UI units drawn at 2x; sources are
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
| P3 threat alert | A red-framed glass with a lit lamp carrying the missile glyph, the count at 22, the decoy with its key cap and point defence on the second line. |
| P4 message feed | Comm-loop slips: a source tag, then the message in sentence case (speech and events are prose; instruments are caps). |
| P5 objective | A two-line slip with a kapton tab. |
| H1 flight display | A DU under the hood. The frame is an FMA box, green while picked automatically and white when pinned. The speed is in that frame, beside the assist box. Below them are the thruster pictogram, delta-v, and the propellant tape. |
| H2 navigation | Label, value and time columns: ORBIT, NAV in magenta with its ring, NEXT with its diamond, PLAN. Values at 15 with units dim at 12. |
| H4 ship condition | Lenses in a bezel, dark when well: three on a ship with no shield emitter. Expanded on deck, each lens heads a row with its reading and a tape. |
| H5 scope | A 200 x 200 DU with − and + at 24, the range in the corner, and off-scale contacts as rim ticks at their bearing. In cruise it folds to a 48 x 48 key with the count. |
| C1 orders menu | A mode control panel, as on a flight deck: the helm orders are interlocked lit keys under their number caps. Autopilot, crew and shield orders are lit keys whose lower line is their state. Abandon and the tow sit under hinged guards with striped rims. A barber-pole strip marks an order that can't be given now, and the panel's foot gives each reason once with the keys it blocks. |
| F1 lock card | The name in the allegiance colour with the lock brackets. HOSTILE is a box, and FIRING ON YOU a lit red flag. Then RNG, REL, what it is doing and what is known. |
| F2 weapons rack | One row per mount: mount, weapon, state, hit chance as cyan pips with the number and word, rounds left, who fires it (YOU in cyan, PD). Point defence engaging reads green, like an engaged autoflight mode. |
| F3 shield control | A ring of eight sectors around the ship's outline, each as wide as the cloud there. The trim is a cyan arc outside the side leaned to, and the ring itself is the control. Beside it: cloud mass, RAISED, TRIM, the emitter and its heat against the trip. |
| F4 threat list | Rows 24 tall (a click locks): the missile's impact time, bearing and PD ON, then the other hostiles. |
| X2 boarding panel | A DU of label and value rows, with K BOARDING and U CAST OFF as buttons 26 tall. |
| X3 emergency card | A hazard-striped card: the cause at 22, then the ways out as the same guarded switches as C1, with the tow's costs. |
| D1 captain vitals | The suit's own chest display (the DCM): suit-grey bezel, orange tab. |
| D3 hazard markers | Tags on the deck by P2's code or the casualty's name, with a leader to the spot. |
| The ships | The game's own hull outlines (`HULL_ART`), plate grey for your ship, red-brown for hostiles, dark for a beaten one. |

## Screens

![Helm, cruising, orders open](c1-helm-cruise.png)

**C1, helm, cruising in Earth orbit with the orders menu open (11.1 and 11.12).** Nothing is
wrong, so P2, P3 and P4 draw nothing. H4 is three dark lenses and H5 has folded to a key. No helm
order is running, so P1's helm field is empty and no helm key is down. Four helm orders need a Nav
in the bubble, the shield orders need an emitter, and the tow is refused while the ship can fly
home. Each carries the strip, and the foot says why once.

![Fire control](c2-fire-control.png)

**C2, the weapons console of a gunship (11.5).** Bea holds the helm. One missile is inbound with
point defence on it: P3 has the count and the decoy, F4 the impact time. The shield ring leans to
the bow. In F2 the nose cannon has the hit chance on the Lock, the autocannons are point defence
(one engaging, one idle), and the harpoon launchers show their rounds. The scope is at 10 km with
Raider 2 as a rim tick.

![Deck emergency](c3-deck-emergency.png)

**C3, on deck, the corvette disabled (11.7).** The reactor is stunned and the hold is at vacuum.
Bea fights the fire in the bridge and Cole treats Ada. The suited captain stands in the hold. P2
ranks the five warnings and folds SMOKE into "+1 MORE". X3 offers the ways out; P1 and P5 are
dimmed under it. D3 tags the fire, the vacuum and Ada. Bottom left is H4's deck face (no plan,
since the deck is the plan) over the DCM, and the master cue stands alone at the bottom centre.

![Boarding, latched](c4-boarding.png)

**C4, latched to a taken raider (11.13).** Latched, the screen is the docked one. P5 has the
bounty's next step, X2 sits where the station panel would be, and H4 has its quiet lights. The
raider lies the way the game docks two ships: half a turn apart, port to port. That puts it over
the corvette's port side, upside down and nose aft. Its lock brackets and name stay, but there is
no lock card and no range.

## Round 2 critique

What works:

- The 12-unit floor reshaped the variation for the better. With no room for bezel titles, each
  widget is known by its zone and its face, and the labels that remain are ones a reader uses.
- C1's panel shows every order, whether it can be given, and why not. It does that with the
  hardware's own grammar (interlocks, guards, flags) rather than a list.
- X3's guarded switches are C1's, so a player who has seen the menu knows the emergency card.
- P2's handling column reads at a glance: cyan for you, white dots for crew, dim BACK for a
  condition clearing itself.

Problems in the spec, found while drawing and sent to the designer:

- **11.5's numbers don't fit the game.** F1's Lock is at 7.7 km, while F2 gives the nose heavy
  cannon 62% fair. The heavy cannon opens fire at 50% around 3.7 km (`HitChance.OPEN_FIRE`), so at
  7.7 km it would be poor. Drawn at 3.4 km.
- **H5's 20 km is not a scope step.** `RadarScope.RANGES` are 0.5, 2, 10, 50, 250 and 1 000 km.
  Drawn at 10 km, the smallest step holding the Lock and the missile, with Raider 2 as a rim tick.
- **Wireframe 05's rack.** The autocannons are point defence (`autocannon_mk1` has
  `point_defense = true`), so at the console they read PD, not YOU. The rows name PORT and STBD
  twice; the mounts are FWD PORT, FWD STBD, AFT PORT and AFT STBD. A harpoon launcher's cooldown
  is 1 s, not 4 s.
- **11.7's crew dots.** FIRE ●● and CREW CRIT ● need three workers. With Ada down, the starting
  crew has two (Bea and Cole). Drawn with one on each.
- **11.13's party.** "PARTY 4 aboard" is more people than the corvette carries. The crew is three,
  and the captain is at the helm. Drawn as 2 aboard.
- **11.13's feed.** "Boarding: the Raider is taken" has a source, Boarding, that P4's list of
  sources lacks. Drawn with a BRD tag.
- **Wireframe 13** draws the raider ahead of the nose. The corvette's port is on its port side, so
  the latched raider lies alongside.
- **W3 repeats H2.** The burn cue's countdown is the same datum as H2's NEXT line, on screen at the
  same time. Drawn as the spec says; one home per datum would drop the cue's countdown at the
  helm.

What is still wrong in these screens, for round 3:

- The orders panel's groups are paired to fit its 620 width (AUTOPILOT with SHIP, CREW with
  SHIELDS), not in the spec's order.
- The trim is a step key, not a five-way selector. On the corvette both shield orders are dark, so
  it doesn't show here, but a gunship's menu should get the selector.
- C3's alarm beacons and the dark deck make the bridge's floor hard to read away from the fire.
- No screen yet shows an armed guarded order (amber ARMED), which C1 and X3 both need for the 3 s
  between presses.

## Deviations from the spec

- **Thousands take a thin space** ("1 240 KM", "7 672 M/S"), the style guide's rule, where the
  spec writes a comma.
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

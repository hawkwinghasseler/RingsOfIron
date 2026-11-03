# UI Overhaul: Functional Design by Player Context

Functional design 1 of 3 for the ground-up UI and UX overhaul. This one starts from the player's
**context**: where the captain is, what is happening around the ship, and what they are trying to do.
Every widget, window and world marker here is defined by what it shows, where it sits and when it is
on screen. Nothing here sets a look: the three art directions (NASA-punk, 90s VFD, cassette-futurism)
skin and adapt it. Section 12 is the contract for them.

Wireframes are grey boxes at the 1280 x 800 UI-unit layout the HUD already uses (`UiScale`), in
[`wireframes/`](wireframes/). Positions and sizes are in UI units. `wireframes/wireframes.html` draws
them and `node documentation/ui-overhaul/wireframes/render.js` renders them again.

**Version 2.** The design is revised in rounds with the three art directions; the revision log is at
the end.

## Contents

1. [The design in one page](#1-the-design-in-one-page)
2. [What the UI shows today](#2-what-the-ui-shows-today)
3. [What is wrong with it](#3-what-is-wrong-with-it)
4. [The context model](#4-the-context-model)
5. [Screen grammar](#5-screen-grammar)
6. [The persistent layer](#6-the-persistent-layer)
7. [Seat instruments](#7-seat-instruments)
8. [Situation panels](#8-situation-panels)
9. [World overlays](#9-world-overlays)
10. [Full-screen modes](#10-full-screen-modes)
11. [Context walkthroughs](#11-context-walkthroughs)
12. [Contract for the art directions](#12-contract-for-the-art-directions)
13. [What is cut, and why](#13-what-is-cut-and-why)
14. [What the simulation must provide](#14-what-the-simulation-must-provide)
15. [Input, focus and the Esc stack](#15-input-focus-and-the-esc-stack)
16. [Building it](#16-building-it)
17. [Questions for Joe](#17-questions-for-joe)

---

## 1. The design in one page

**The seat defines the screen.** The captain is a person on a ship. They see outside only from a seat:
the helm, the weapons console or a gun. On deck they see the ship itself. So the first question for any
piece of information is *which seat needs it*. The helm gets flight and navigation instruments; fire
control gets weapons, the lock and the shields; the deck gets almost nothing, because the deck is the
display: fire, smoke, crew and damage are drawn in the world.

**Situations modify the seat.** On top of the seat, the game is always in one or more situations:
cruising, under time warp, approaching a port, docked, in combat, boarding, or in an emergency. Each
situation brings its own instrument forward and pushes others back, automatically. The player never
arranges panels.

**Dark cockpit.** Borrowed from airliner flight decks: when everything is normal, nothing is lit. A
ship at full health shows four quiet status lights, not four bars at 100% and "ALL SYSTEMS NOMINAL".
Detail appears when something departs from normal, and goes away when it recovers.

**One home per datum.** Each piece of information has exactly one place on screen in a given context.
Today the locked target appears in three places and the missile warning in three; here each appears
once.

**Conditions are not events.** Something that *is true now* (a fire, low propellant, a missile
inbound) goes to the caution and warning panel and stays while it is true. Something that *happened*
(a contract paid, a crewman back inside) goes to the message feed and fades. Today both share one
column and one timer.

**The captain's question is "is someone on it?"** The crew fight fires, seal breaches and treat the
wounded on their own. So a fire is shown with whether crew are on it, not only where it is.

**Four layers**, top to bottom:

| Layer | What | Who controls visibility |
|---|---|---|
| Interrupt | Threat alert (missiles inbound), emergency card | The situation, always on top |
| Persistent | Status strip, caution and warning panel, message feed, objective | Always present, sizes to content |
| Context | Seat instruments and situation panels | Seat and situation, automatically |
| On demand | Full-screen modes (map, ship, station, boarding, captain's record, controls) and the orders menu | The player |

**Progressive disclosure.** Every instrument has a compact face (the default) and an expanded face
(hover, or hold the detail key) with the secondary numbers enthusiasts want: heading in degrees, the
period in seconds, kilograms of propellant. Those numbers are not removed from the game; they are
removed from the default face.

---

## 2. What the UI shows today

Everything the player can see, from `ui-hud`, core's `GameHudText` and the render overlays, after
PR #258. The verdict column says what this design does with it; section 13 gives the reasons for cuts.

### 2.1 Always-on HUD panels (exterior view)

| Element | Where now | Data | Verdict |
|---|---|---|---|
| FLIGHT panel (`FlightReadoutSystem`) | Top left | Speed (m/s, orbital), heading (deg), markers frame (orbit / TARGET + rel. speed), view mode "EXTERIOR [V]" | Speed and frame merge into H1 Flight display. Heading to expanded face. View mode cut (the seat indicator replaces it) |
| TARGET section, same panel | Top left | Lock name, range, closing, "shot 1.3 s" | Moves to H6 Lock card |
| ORBIT section, same panel | Top left | Body, altitude, Ap, Pe, period (s) | Moves to H2 Navigation block; period to expanded face, formatted as time |
| SHIP STATUS gauges (`ShipGauges`) | Top left, second panel | Propellant % + kg, structure %, power % + MW, oxygen % or EMERGENCY kg | Split: propellant to H1 (as delta-v), the rest to H4 Ship condition with a dark-cockpit compact face |
| RADAR (`RadarScope`) | Top left, third panel | Contacts, lock ring, heading line, range buttons | Becomes H5 Contact scope, bottom right, shown when it has something to show |
| SHIP panel (`WireframeMinimap` + `AlertList`) | Bottom left | Deck plan with hazards; HULL %, BREACH, FIRE, VACUUM, LOW PRESSURE, SMOKE, or ALL SYSTEMS NOMINAL | Plan goes into H4's expanded face; alerts go to P2 Caution and warning panel. "ALL SYSTEMS NOMINAL" cut |
| ORBIT panel (`OrbitMap` small) | Bottom right | Small orbit plot, caption hints ("N plans a burn", "Q picks a navigation target"), burn readout | Cut. The map is one key away; the next burn and next apsis go to H2 |
| Missile warning banner (`ThreatOverlay`) | Top centre | MISSILE WARNING, n inbound, impact s | Becomes P3 Threat alert |
| Dock banner | Top centre | "Docked at Highport [T] Trade [Y] Yard [J] Missions" | Becomes X1 Station panel |
| Interact prompt (`InteractPromptSystem`) | Bottom centre, fixed | "Press [E] for the Helm panel [V] sit and fly" | Becomes P6 Prompt, anchored to the object |
| Key hint line (`HintRegistry.line`) | Bottom edge | Up to 5 key hints for the context, then "F1 controls" | Becomes learning-mode hints in P6; off once learned |

### 2.2 The status column (core's `GameHudText.drawStatus`), top right, line by line

| Line | Example | Verdict |
|---|---|---|
| Clock and warp | "Day 0 00:00:33  Warp 1x (rails)" | P1 Status strip |
| Lock | "Target: Raider (gun reloading) FIRING ON YOU" | Third copy of the lock. Cut here; H6 has it, and "firing on you" becomes a lock state |
| Missile status | "Missile: ready (4 left)" | H7 Armament strip, in combat only |
| Career | "Ensign Budget 2,000 cr/wk in 6d 23h" | M5 Captain's record; payday becomes a feed message |
| On-deck missile warning | Large red line on deck | P3 Threat alert (same widget on deck and outside) |
| Abandon ship | "ABANDON SHIP: crew to the pods (P launches now)" | X3 Emergency card |
| Ship disabled | "SHIP DISABLED: reason  P twice abandons ship, B twice calls a tow" | X3 Emergency card |
| Notices (4 newest) | Mixed info, important and warnings | Split: conditions to P2, events to P4 Message feed |
| Docking readout | "Dock port 1/1: S active, free"; "To Highport #6: 3,015.1 m off -2.2 m turn -0 deg in -0.08 m/s"; "Safe closing speed here: up to 24.6 m/s"; "Docked at Highport strain 0%" | H3 Docking display (graphical), only while approaching. Strain cut unless high |
| Navigation readout | Nav target, distance, closing; closest approach; next burn and later burns; helm order; autopilot; delta-v needed | H2 Navigation block; helm order and autopilot to P1 |
| Contracts held | "Deliver ...: 1.4 d left, 340 km" | P5 Objective (the tracked one); all in M5 |
| First Play objective, opening guide | Gold text | P5 Objective |
| Bridge line | "Helm: the helmsman holds the heading" / "nobody; the ship drifts" | P1 Status strip, helm authority |
| EVA line | "EVA: Ada on the hull, 16 m of line out. A hard burn can throw them off" | P2 caution "Crew outside" while it matters; detail in M2 Crew |

### 2.3 World overlays (exterior view)

| Element | Data | Verdict |
|---|---|---|
| Brackets, nameplates, ranges, edge arrows (`HudRenderSystem`) | Every marked entity: name and range | W1 Contact markers, with label priority and decluttering |
| Lead reticle (`LeadReticle`) | Lock's lead point | W5, kept |
| Prograde / retrograde (`NavMarkers`) | Orbit frame or target frame (magenta, TGT) | W2 Velocity markers, frame shared with H1 |
| Burn arrows with countdown labels (`NavOverlay`) | Each planned burn's direction, number and countdown | W3 Burn cues, next burn only by default |
| Docking approach corridor (`NavOverlay`) | Safe / fast / danger along the port's axis | W4, kept |
| Fire arcs and hit lanes (`FireArcOverlay`) | Arcs, dodge lanes coloured by hit chance | W5, kept |
| Missile paths, impact crosses, edge arrows, PD KILL rings (`ThreatOverlay`) | Each missile | W6, kept; edge arrows merge with W1's |
| Picture-in-picture bubbles (`PictureInPicture`) | Own ship, lock, nav target magnified; "[+] 3 others" lists | W7 Inset views, kept, at fixed slots |

### 2.4 Windows

| Window | Key | Data | Verdict |
|---|---|---|---|
| Console window (`ConsoleWindow`) | E at a console | Console id, system type, health, on/off switch; "Take fire control" | Replaced: D2 Inspect card (system switch) and the seat itself |
| Map (`ManeuverMap` + `TrafficLayer`) | M | Orbits, burns and handles, traffic, tension readout, side panel of orbit, burn and plan text, buttons | M1 Map, restructured panels and a timeline |
| Market (`TradeWindow`) | T | Credits, hold, per commodity stock / buy / sell / held | M3 Station, Market tab |
| Yard (`YardWindow`) | Y | Repairs, tanks, refits per hardpoint, furnishings, Rebuild decks, Ships for sale | M3 Station, Yard tab; field refit moves to M2 Loadout |
| Shipyard (`ShipyardWindow`) | Via yard, or Y with a prize | Ships for sale, trade-in, prize sale or turn-in | M3 Station, Shipyard tab |
| Mission board (`MissionWindow`) | J | Career line, tension, offers, held contracts | Offers to M3 Missions tab; held contracts and career to M5 |
| Boarding (`BoardingWindow`) | K | Latched ship's state, party orders, parts to strip, cargo | M4 Boarding |
| Crew roster (`CrewWindow`) | I | Berths, wages, each member, EVA orders, suits, prisoners, hires | M2 Ship, Crew tab; hires to M3 Hire tab |
| Notice log (`HudText.drawLog`) | L | Last 50 notices with day and time | M5, Log tab, filterable |
| Controls (`HudText.drawHelp`) | F1 | Every hint by context | M6 Controls, current context first |
| Crash / career over | - | SHIP LOST / CAREER OVER and load options | Shell, kept |

### 2.5 Shell and developer

| Element | Verdict |
|---|---|
| Main menu, pause menu, save panel, load list, settings (`ShellMenus`) | Kept. Settings gains an Interface section (section 10.7) |
| Mod trust dialog (`ModTrustDialog`) | Kept as a shell dialog |
| Lighting toggle (O) | Moves to Settings, Graphics; the key goes to the developer layer |
| Developer console (~), debug menu (F3), overlays (heat and others), scenario tools | Developer layer only, section 10.8 |
| "Scenario: ...", "Content loaded with errors" notices | Developer layer |

---

## 3. What is wrong with it

Findings from the inventory and the screenshots in `documentation/ui-hud/polish/`. Each one is a rule
in the design that follows.

1. **Everything is always on.** In open space with nothing near, the flight panel, gauges, radar, ship
   panel, orbit panel and status column all show, about 40 lines of text, most of it unchanging. →
   Context decides visibility (section 4).
2. **The same datum in several places.** The lock: flight panel, status column ("Target: Raider"),
   navigation readout. The orbit: flight panel and orbit panel. Missiles: banner, notices, deck line. →
   One home per datum per context.
3. **Two different "targets" with the same word.** "Target" in the flight panel is the fire-control
   lock (Tab); "Target" in the navigation readout is the navigation target (Q). → Two names
   everywhere: **Lock** and **Nav** (section 4.4).
4. **Numbers where a picture is needed.** Docking is flown from "off -2.2 m turn -0 deg in -0.08
   m/s" and a second line with the safe speed. → A graphical docking display (H3).
5. **Numbers that never change.** Orbital speed 7,672 m/s, heading 0.0 deg, period 5545 s, strain 0%.
   → The default face carries what changes decisions; the rest is one key away.
6. **Conditions and events share one list and one timer.** A fire posted as a notice fades after 8 s
   while the ship still burns. → Caution and warning panel for conditions, message feed for events.
7. **Debug data reads as game UI.** "Dock port 1/1: S active, free", "strain 0%", "Markers orbit",
   "View EXTERIOR [V]", "Lighting on". → Cut or moved to the developer layer.
8. **The ship's real state has no display.** Shields have no gauge (the combat design notes "the HUD
   gauge and coverage overlay" as not built); the captain's own suit air and wounds are only a notice;
   who is manning what, battle stations, and whether crew are handling a fire are invisible. → New
   instruments (F3, D1, P1, P2 handling state).
9. **Station services are scattered.** Market, yard, shipyard, missions and hiring are five windows
   with five keys, plus a dock banner, plus hints. → One station terminal with tabs (M3) and one docked
   panel (X1).
10. **Key hints compete with information.** The bottom line and the orbit panel caption permanently
    teach keys the player already knows. → Learning-mode hints that retire (P6).
11. **The deck view borrows the flight HUD.** On deck the flight panel and gauges stay up, though the
    captain can't fly from there. → The deck gets its own small set (D1 to D3).
12. **Contact labels pile up.** Several contacts at similar bearings stack names and ranges at the
    screen edge. → Label priority and clustering (W1).

---

## 4. The context model

The UI's state is three independent things: the **seat** (where the captain is), the set of
**situations** (what is happening), and the **mode** (a full-screen view, if one is open).

### 4.1 Seats

| Seat | Entered by | View | Primary task |
|---|---|---|---|
| **Helm** | Sitting at the helm console (V beside it) | Exterior | Fly, navigate, dock; may also fire guns (Space) and missiles (R) |
| **Fire control** | Sitting at the weapons console | Exterior | Aim and fire every gun, missiles, decoys, shields |
| **Gun** | Taking a turret mount | Exterior | Aim and fire one gun |
| **Deck** | Standing up (V), or unseated | Interior | Walk, use objects, inspect, carry, survive |

`-PviewAnywhere` (`GameSession.VIEW_ANYWHERE`) is treated as Helm when the view is exterior.

Deck applies aboard any craft: the player's ship, a station across a docked walkway, or a ship being
boarded. Aboard anything but the player's own ship, P1 names the craft ("DECK · Highport").

The seat comes from the game's `Bridge` (`playerAtHelm`, `playerOnGuns`) and `ViewModeController`.
The UI gets it as one value, not by inferring it from the view mode.

### 4.2 Situations

Several can hold at once. Each has an entry and exit rule with hysteresis so panels don't flicker.

| Situation | Enters when | Leaves when | Brings forward |
|---|---|---|---|
| **Cruise** | No other situation holds | Any other enters | Navigation, objective |
| **Warp** | Time warp above 1x (physics or rails) | Warp back to 1x | Upcoming events, warp limit reason |
| **Approach** | A free compatible port within 5 km (`PlayerDocking.READOUT_RANGE`) **and** one of: the nav target is that craft, a port is armed, or the helm has a dock order | Over 5.5 km, docked, or the trigger is gone for 3 s | Docking display, scope at 2 km |
| **Docked** | Port state DOCKED | Undocked | Station panel; flight instruments retire |
| **Combat** | Own ship at battle stations (`ShipTaskBoardComponent.battleStations`), or missiles inbound, or a hostile locked within weapons range | None of those for 10 s | Lock, armament, scope, ship condition expanded |
| **Boarding** | The boarding window is available (latched, or a beaten ship within 300 m on EVA) | Not available | Boarding panel |
| **Emergency** | Ship disabled, abandoning, or the captain's suit air under 240 s (`AirWatch`) | Condition cleared | Emergency card; everything non-essential dims |
| **Guided** | First Play or the opening walkthrough is running | It ends | Objective shows the step; hints on |

Precedence, when two want the same slot: Emergency > Combat > Approach > Boarding > Docked > Warp >
Cruise. Combat during an approach keeps the docking display but adds the lock card and threat alert;
the scope takes the combat range.

### 4.3 Modes

A mode is a full-screen view the player opens. One at a time; opening another replaces it. The game
keeps running underneath (it is real time), and the interrupt and persistent layers stay on top.

| Mode | Key (unchanged where possible) | Available |
|---|---|---|
| M1 Map | M | Always |
| M2 Ship (Systems, Crew, Cargo, Loadout) | I | Always |
| M3 Station (Market, Yard, Shipyard, Missions, Hire) | T, Y, J while docked | Docked |
| M4 Boarding | K | Boarding situation |
| M5 Captain's record (Career, Standing, Contracts, Log) | J away from a station; L opens the Log tab | Always |
| M6 Controls | F1 | Always |

Using a console on deck (E) opens the mode that console belongs to, on the right tab: E at the
engineering or reactor console opens Ship › Systems at that system; E at the medbay opens Ship › Crew
sorted by condition. The keys open the same modes from anywhere: walking to a console is the diegetic
route, not a tax.

### 4.4 Lock and Nav

Two selections, always named apart:

- **Lock**: the fire-control target (Tab, or a click on a ship). Weapons, lead reticle, hit lanes and
  the lock card follow it.
- **Nav**: the navigation target (Q, a click on a station or ship, or the map). Closest approach,
  transfers, helm orders and the docking display follow it.

A click on a ship sets both, as now. Each has its own marker shape in the world, on the scope and on
the map (section 12 lists them as semantic tokens), and its own block. Neither is ever labelled just
"Target".

### 4.5 The reference frame

Speed, closing speed and the velocity markers share one reference frame, shown on the flight display:

| Frame | Chosen automatically when | Speed shown |
|---|---|---|
| PORT | Approach situation | Closing speed to the port |
| NAV | Nav target in the bubble (within 50 km) | Speed relative to it |
| LOCK | Lock in the bubble and no nav target there | Speed relative to it |
| ORBIT | Otherwise | Orbital speed |

The player can pin a frame (a key cycles Auto, Orbit, Nav, Lock). Today the markers follow the lock
only; this widens it (see question 1).

An automatic change of frame never happens while the player is thrusting or turning: it waits until
the controls are released, so the number under the player's eye doesn't jump mid-manoeuvre. When the
frame changes, the frame tag on H1 and on the velocity markers is highlighted for 2 s.

### 4.6 The visibility matrix

● shown in full · ◐ compact face · ! only while its own condition holds · – hidden.
"Full-screen" means any of M1 to M6 is open.

| Widget | Helm cruise | Helm warp | Helm approach | Helm combat | Fire control | Gun | Deck | Deck combat or emergency | Docked | Full-screen |
|---|---|---|---|---|---|---|---|---|---|---|
| P1 Status strip | ● | ● | ● | ● | ● | ● | ● | ● | ● | ● |
| P2 Caution and warning | ! | ! | ! | ! | ! | ! | ! | ● | ! | chip in P1 |
| P3 Threat alert | ! | ! | ! | ! | ! | ! | ! | ! | ! | ! |
| P4 Message feed | ! | ! | ! | ! | ! | ! | ! | ! | ! | ! (1 line) |
| P5 Objective | ◐ | ● | – | !¹ | !¹ | !¹ | ◐ | – | ◐ | – |
| P6 Prompt and hints | ! | – | ! | – | – | – | ! | ! | ! | – |
| H1 Flight display | ● | ◐ | merged into H3 | ● | – | – | – | – | – | – |
| H2 Navigation | ● | ● | ◐ | ◐ | – | – | – | – | ! (planned departure) | – |
| H3 Docking display | – | – | ● | ● | – | – | – | – | – | – |
| H4 Ship condition | ◐² | ◐² | ◐² | ◐² | ● (with F3) | ◐² | ! | ● | ◐² | – |
| H5 Contact scope | ! | – | ● | ● | ● | ● | – | – | – | – |
| H6 Lock card | ! | – | ! | ● | F1 instead | F1 instead | – | – | – | – |
| H7 Armament strip | – | – | – | ● | F2 instead | F2 (one gun) | – | – | – | – |
| F1 Lock card, full | – | – | – | – | ● | ● | – | – | – | – |
| F2 Weapons rack | – | – | – | – | ● | ◐ | – | – | – | – |
| F3 Shield control | – | – | – | – | ● | – | – | – | – | – |
| F4 Threat list | – | – | – | – | ! | ! | – | – | – | – |
| D1 Captain vitals | – | – | – | – | – | – | ! | ● | ! | – |
| D2 Inspect card | – | – | – | – | – | – | on hover | on hover | on hover (deck) | – |
| D3 Off-screen pointers | – | – | – | – | – | – | ! | ● | – | – |
| X1 Station panel | – | – | – | – | – | – | – | – | ● | – |
| C1 Orders menu | on key | on key | on key | on key | on key | on key | on key | on key | on key | – |
| X2 Boarding panel | ! | – | – | ! | ! | – | ! | ! | – | – |
| X3 Emergency card | ! | ! | ! | ! | ! | ! | ! | ● | ! | ! |
| W1 to W7 world overlays | ● | ◐ (W1 only) | ● | ● | ● | ● | – | – | ◐ | – |

Rows marked "!" have their trigger in their own section.

¹ In combat, P5 shows (compact) only when the tracked objective is itself a fight: a defence, a bounty
or a convoy, with its progress ("2 of 3 beaten").

² H4 shows its compact face while the ship is nominal, and expands by itself when any of its lights is
not (section 7, H4). Combat alone does not expand it: a healthy ship in a fight stays dark.

---

## 5. Screen grammar

### 5.1 Zones

The screen is divided into fixed zones, so that a widget appearing in one zone never moves a widget in
another. Each widget belongs to one zone and stacks inside it.

```
 0                                                                          1280
 +--------------------------------------------------------------------------+ 0
 | P1 STATUS STRIP  seat | helm authority | battle stations ... clock | warp | 26
 +--------------------+----------------------------------+------------------+ 38
 | L-TOP              |  TOP-CENTRE (interrupts)         | R-TOP            |
 | P2 caution/warning |  P3 threat alert                 | P4 message feed  |
 |                    |  X3 emergency card               |                  |
 +--------------------+  ........................ 212    +------------------+ 170
 | L-MID              |                                  | R-MID            | 176
 | P5 objective       |  :  CENTRE, kept clear       :   | H6/F1 lock card  |
 | H2 navigation      |  :  220 .. 590               :   | H7 armament      |
 | F4 threat list     |  :  no fixed widget, ever    :   | X1 station panel |
 |                    |  :                           :   | X2 boarding      |
 +--------------------+  ................................+------------------+ 552
 | L-BOTTOM           |  P6 prompt (when not anchored)   | R-BOTTOM         | 558
 | H4 ship condition  +----------------------------------+                  |
 | F3 shield control  | BOTTOM-CENTRE                    |  H5 contact scope|
 | D1 captain vitals  | H1 flight / H3 docking / F2 rack |                  |
 +--------------------+----------------------------------+------------------+ 788
   12 .. 320                330 .. 950                       960 .. 1268
```

| Zone | x | y | w | h | Holds, outer edge first |
|---|---|---|---|---|---|
| P1 strip | 0 | 0 | 1280 | 26 | P1 |
| L-TOP | 12 | 38 | 308 | 132 | P2 |
| L-MID | 12 | 176 | 308 | 376 | P5, H2, F4 |
| L-BOTTOM | 12 | 558 | 308 | 230 | H4 or F3 with H4; on deck H4 then D1 |
| TOP-CENTRE | 390 | 38 | 500 | 174 | P3, X3 |
| CENTRE (clear) | 360 | 220 | 560 | 370 | nothing fixed |
| BOTTOM-CENTRE | 330 | 598 | 620 | 190 | H1, H3 or F2; C1 opens above it |
| R-TOP | 960 | 38 | 308 | 132 | P4 |
| R-MID | 960 | 176 | 308 | 376 | H6 or F1, H7, X1, X2 |
| R-BOTTOM | 960 | 558 | 308 | 230 | H5, right-aligned |

- **Side columns** are 308 units wide.
- **Bottom-centre** is the primary instrument for the seat: 400 units wide at the helm, up to 620 at
  fire control. It sits directly below the ship, which is drawn at the centre, so the eye travels
  down from the ship to its instrument and back.
- **The centre** holds no fixed widget, ever. World overlays, anchored prompts (P6), hover cards (D2,
  W1) and the orders menu (C1) may enter it.
- At wider aspect ratios the side columns stay at the edges and the centre grows. At UI scales that
  leave fewer than 1280 x 800 units, `UiScale` already holds the scale down.

### 5.2 Stacking and space

- A zone's widgets stack from the zone's outer edge inward, in the order given in each zone above.
- A slot that empties closes up only after 2 s, and only if nothing else is about to fill it, so a
  flickering condition doesn't make the column jump.
- When a zone overflows, the lowest-priority widget in it collapses to its compact face, then to a
  one-line chip.
- Widgets never overlap each other. World overlays may pass under widgets; labels avoid them (as
  today's nameplates already rise clear of the ship panel).

### 5.3 Motion

- Context changes (seat, situation) cross-fade over the same 0.3 s as the view zoom (`ZoomEase`).
- A widget appearing because of a warning arrives at once; anything else may ease in.
- Numbers never animate their layout: a value's width is reserved so changing digits do not shift
  the text around them.
- Flashing is for warnings only, at most twice a second, and only for the first 2 s unless the
  warning is time-critical (inbound missile, suit air, bleed-out). A setting turns flashing off.

### 5.4 Number formats

| Quantity | Format |
|---|---|
| Distance | under 1 km: "850 m"; under 100 km: "12.4 km"; over: "1,240 km" |
| Speed | under 10 m/s: "4.2 m/s"; over: "386 m/s"; closing/opening as words or arrows, never a bare sign |
| Time to go | under 1 min: "T-42 s"; under 1 h: "T-12:05"; under 1 day: "T-3 h 05 m"; over: "T-2.4 d" |
| Elapsed time and clock | "Day 12 · 14:05" (seconds in the expanded face) |
| Delta-v | "1,240 m/s Δv" |
| Mass | "2,000 kg" |
| Percent | "64%" with no decimals; bars carry the fine detail |
| Money | "12,000 cr" |

---

## 6. The persistent layer

### P1 Status strip

**Purpose.** Where the captain is, who is flying, and what time it is. The only widget always on screen.

**Zone.** Top edge, full width, 26 units tall.

**Content, left to right:**

| Field | Shows | States |
|---|---|---|
| Seat | HELM · FIRE CONTROL · GUN (bow) · DECK | Neutral |
| Helm authority | Only when the captain is not at the helm, or an order or autopilot flies: "Helm: Bea, holding heading"; "Helm order: approach Highport to 500 m"; "Autopilot → Shackleton Ice, next burn T-2 h 05 m"; "Autopilot held: crew on EVA"; "Helm: nobody, drifting" | Neutral; caution when nobody holds the helm while the ship spins or drifts toward something |
| Battle stations | "BATTLE STATIONS" while the crew are at them | Caution |
| Caution and warning chip | In full-screen modes only: "▲ 2 · ◆ 1" (warnings, cautions); a click closes the mode | Severity of the worst |
| Clock | "Day 0 · 00:33" | Neutral |
| Warp | "1x", "10x", "1,000x rails"; with the reason when warp is held or capped: "rails held: crew outside" | Advisory when held |
| Paused | "PAUSED" | Advisory |

**Data.** `TimeWarpController`, `Bridge.helmsman`, `HelmOrders.describe`, `Autopilot.describe`,
`ShipTaskBoardComponent.battleStations`, `Spacewalks` (warp hold reason).

**Replaces.** Status column clock line, bridge line, navigation readout's helm and autopilot lines,
"View EXTERIOR [V]".

### P2 Caution and warning panel

**Purpose.** Everything that is wrong *now*, ranked, with whether it is being handled.

**Zone.** Left top. Up to 5 rows; "+n more" as a sixth row opens Ship › Systems. Hidden when there is
nothing to show (the dark cockpit). In full-screen modes it collapses to the chip in P1.

**Row anatomy.** Severity · code · detail · handling · timer.

```
 ◆ FIRE           Galley, Engineering        crew on it (2)
 ◆ CREW CRITICAL  Ada                         medic on it      T-3:40
 ▲ PROPELLANT LOW home needs 410 m/s, have 520
```

**Ordering.** Warnings before cautions; within a severity, rows with a timer by time left; then by
age. Advisories never appear here (they go to the strip or the feed).

**Behaviour.** A new warning row arrives at once and pulses for 2 s; the game's existing UI warning
sound plays with it (`NoticeAudio`). A row stays while its condition holds and leaves when it clears.
Hovering a row (or focusing it with the pad) shows where on the ship it is, in H4's plan or the deck.

**Master alert cue.** P2 sits at the edge of the screen, away from where the player looks. So a new
warning also lights a small cue on the seat's primary instrument (H1, H3 or F2; on deck, beside D1's
slot), the way an airliner's master warning sits in the pilot's eyeline. It shows the warning's code,
and clears when the player hovers P2, or after 5 s. A new caution lights it without the code, for 2 s.

**The condition catalogue.** This is the complete list; nothing else may add rows without being added
here.

| Code | Severity | Condition | Detail | Handling | Timer | Source |
|---|---|---|---|---|---|---|
| SUIT AIR | Warning | Captain's suit air ≤ 240 s and not breathing ship air | "Your suit" | "refilling" while at a tank | Air left | `AirWatch`, `CaptainSafety` |
| CAPTAIN WOUNDED | Warning | Captain bleeding or worse | Wound level | "medic on it" | Bleed-out | Crew health |
| IMPACT | Warning | Orbit meets a surface (`OrbitInfo.impactWarning`) and no plan avoids it | Body | "plan avoids it" when the planned path clears it | Time to impact | Orbital |
| COLLISION | Warning | A craft or rock in the bubble will pass within its radius plus ours in the next 60 s, closing faster than the safe docking speed | What, bearing | – | Time to contact | New: closest approach of bubble contacts |
| FIRE | Warning | Burning tiles > 0 | Rooms | Crew assigned to extinguish | – | `ShipAlerts`, task board |
| HULL BREACH | Warning | Breaches > 0 | Count, rooms | Crew sealing | – | `ShipAlerts`, task board |
| VACUUM | Warning | A room at vacuum (not an airlock cycling) | Rooms | Crew sealing | – | `ShipAlerts` |
| CREW CRITICAL | Warning | A crew member critically wounded | Name | Medic assigned | Bleed-out | Crew health |
| BOARDERS | Warning | Hostile crew aboard | Count, rooms | Marines engaging | – | Crew service |
| BEING BOARDED | Warning | A pirate latched for its boarding (`PirateBoarding`) | Ship | "fire on it to break off" | Time until it is done | `PirateBoarding` |
| NO POWER | Warning | No working reactor feeds the main circuit | Reason (stunned, damaged, off) | Crew resetting or repairing | Stun time | Power routing |
| EMERGENCY O2 | Warning | Oxygen tanks dry, life support on reserve | kg left | – | – | `ShipStatusProvider.emergencyOxygenKg` |
| LOW PRESSURE | Caution | A room under 70% pressure | Rooms | Crew sealing | – | `ShipAlerts` |
| SMOKE | Caution | Thick smoke in a room | Rooms | – | – | `ShipAlerts` |
| HULL DAMAGED | Caution | Mean hull integrity under the damaged threshold | Percent | Crew repairing | – | `ShipAlerts` |
| POWER LOW | Caution | Store under 25% for 3 s, or a stunned reactor | Output vs demand | – | Stun time | Power circuits |
| SHIELDS DOWN | Caution | In combat with shields lowered, or every emitter out | Why | – | – | Shield cloud |
| SHIELD WEAK | Caution | Cloud under 25% of capacity, or an emitter tripped | Sector or emitter | – | Trip recovery | Shield cloud |
| LOW O2 | Caution | Oxygen tanks under 25% | Percent | – | – | Ship status |
| PROPELLANT LOW | Caution | `FlightWarnings` low-propellant rule (return trip × 1.25, or 20%) | Home needs vs have | – | – | `FlightWarnings` |
| PLAN SHORT | Caution | Planned delta-v exceeds what is left | Need vs have | – | – | `Navigation.deltaVNeeded/Left` |
| CREW OUTSIDE | Caution | Crew on EVA while thrusting, or a burn due within 60 s | Names, where | – | Time to burn | `Spacewalks` |
| NOBODY AT HELM | Caution | Captain away, no helmsman, ship flying | – | – | – | `Bridge` |
| APPROACH FAST | Caution, Warning at 2x | Closing faster than the safe speed for the distance | Over by | – | Time to contact | `PlayerDocking.safeSpeed` |
| DOCK STRAIN | Caution | Docking strain over 50% | Percent | – | – | `DockingSystem.strain` |
| AUTOPILOT HELD | Caution | Autopilot holding | Reason | – | – | `Autopilot` |

**Replaces.** `AlertList`, the ship gauges' "LOW O2 / VACUUM / EMERGENCY" tags, warning notices that
describe conditions, "SHIP DISABLED"/"ABANDON SHIP" lines (those go to X3), "NOT ENOUGH PROPELLANT",
"SLOW DOWN", "IMPACT" in the orbit line, the EVA status line.

### P3 Threat alert

**Purpose.** The one interrupt for incoming fire: missiles inbound. On every seat, on deck and over
every mode.

**Zone.** Top centre, 500 x 60.

**Content.** "MISSILES INBOUND · 2 · impact T-37 s", the soonest first; "decoy ready (F)" when a decoy
is available; "point defence engaging" when PD is firing at one. Each missile intercepted shows a brief
"1 down" before the count drops.

**When.** From the first inbound missile until none is inbound. On deck it is the same widget (no
separate deck line).

**Data.** `ThreatProvider`, `UiHud.flashIntercept`, decoy count.

**Replaces.** `ThreatOverlay` banner, the on-deck missile line, the "MISSILE WARNING" notices.

### P4 Message feed

**Purpose.** Events: things that happened, briefly.

**Zone.** Right top. At most 3 messages, newest on top. In full-screen modes, one line at the foot of
the mode.

**Message anatomy.** Source tag · text. Sources: Helm, Gunnery, Engineering, Medical, Crew, Station,
Sponsor, Comms, Log.

**Behaviour.** Routine messages hold 5 s, notable ones 8 s (today's INFO and IMPORTANT). A repeat
within the hold counts ("×3") instead of stacking (as `NoticeLog` already does). Every message goes to
the Log (M5). A message that only restates a condition in P2 is not posted to the feed.

**Examples.** "Station: clearance granted, port 6"; "Sponsor: payday, 2,000 cr"; "Crew: Ada back
inside"; "Gunnery: Raider disabled"; "Log: autosaved".

**In combat** the feed would flood with hits and kills, so it summarises: gunnery and point-defence
events from the last 5 s merge into one line ("Gunnery: 3 hits on Raider · PD: 2 down"), and routine
messages from other sources wait in the Log until combat ends, then show as one line ("4 messages in
the log"). Notable messages still show.

**Replaces.** The notices in the status column.

### P5 Objective

**Purpose.** What the player is trying to do next.

**Zone.** Left middle, top of the column.

**Content.** Exactly one tracked objective:

1. While Guided, the First Play objective or the walkthrough step, keys named from the player's
   bindings (`HintRegistry.withKeys`).
2. Otherwise the tracked contract: its heading, progress ("2 of 3 beaten"), time left, distance to
   its waypoint, and a "set as Nav" affordance when it isn't the Nav already. The player picks which
   contract is tracked in M5; by default the one with the soonest deadline.

"+2 more contracts" under it when more are held. Compact face: heading and time left only.

In combat, P5 shows only when the tracked contract is a fight (a defence, a bounty, a convoy), compact,
with its progress.

**Data.** `ContractBoard.describe/progress`, `FirstPlay.objective`, `OpeningGuide.text`.

**Replaces.** The gold contract lines, the First Play objective and the walkthrough text in the status
column.

### P6 Prompt and learning hints

**Purpose.** What the player can do right here, with which key.

**Zone.** Anchored in the world beside the object it refers to (a console, a locker, a door, a crew
member down), clamped to stay out of the side columns; bottom-centre above the primary instrument when
nothing on screen is the subject.

**Content.**

- **Prompts** (always on): the object's name and up to three verbs with keys: "Helm · [E] sit and
  fly"; "Suit locker · [E] suit up"; "Engineering console · [E] systems"; "Door (jammed) · [F] force".
  E is always the object's main action: at a seat it sits (as V does), at a system's console it opens
  Ship › Systems at that system (section 4.3), at a locker it suits up. Today E at the helm opens a
  power panel, which D2 replaces.
- **Learning hints** (by setting): for each action in the context's hint list, the hint shows until
  the player has used that action 3 times, then retires; F1 always lists all. Settings: Learning
  (default), Always, Off.

**Replaces.** The fixed-position "Press [E] for the ... panel" prompt, the bottom key hint line and
the orbit panel's caption hints.

### C1 Orders menu

**Purpose.** Every order the captain can give, in one place, with whether it can be given now. Today
the orders are scattered over keys (1 to 6, 8, 9, P, B) and two windows, and the player has no way to
see which are possible.

**Zone.** Opens above the seat's primary instrument (bottom centre), over the world; closes on an order,
the key again, or Esc. It doesn't pause.

**Key.** O (freed by moving the lighting toggle to Settings). Gamepad: given a button when the pad's
layout is next revised; until then the pad's existing order buttons stay as they are.

**Content,** grouped, each order with its own key and, when it can't be given, why:

| Group | Orders | Source |
|---|---|---|
| Helm | Fly the planned burns · Match velocity with Nav · Hold position on Nav · Approach Nav (distance) · Dock at Nav · Autopilot to Nav · Cancel | `HelmOrders`, `Autopilot` (keys 1 to 6, 0) |
| Shields | Raise or lower · Trim even, bow, port, stern, starboard | `ShieldModel` (9, 8) |
| Crew | Suits at battle stations on or off · EVA: hold everyone in, or let them out | `CrewProvider.toggleSuits/toggleEva` |
| Ship | Abandon ship · Call a tow (with its fee) | `AbandonShip`, `DistressBeacon` (P, B; both still need a second press) |

The direct keys keep working without the menu; the menu is how a player finds them.

---

## 7. Seat instruments

### H1 Flight display (Helm)

**Purpose.** Fly the ship: how fast against what, which way it is pushing, how much it has left.

**Zone.** Bottom centre, 400 x 100.

**Compact face:**

| Field | Shows |
|---|---|
| Speed | Speed in the reference frame, with the frame tag and name: "NAV · Highport · 12.4 m/s closing" |
| Frame | AUTO or pinned; tag ORBIT, NAV, LOCK, PORT |
| Thrust | Which thrusters are firing: main forward and back, turn left and right, strafe left and right |
| Assist | Rotation assist on or off |
| Delta-v | Delta-v left (m/s) with the propellant share as a bar |

**Expanded face** adds heading (deg), turn rate (deg/s), propellant (kg), acceleration available
(m/s²) and mass.

**Warp.** Under warp the compact face shrinks to delta-v and frame only.

**Data.** Physics and `PhysicsManager.getOriginV*` (speed), `FlightControlComponent` (thrust and
assist; needs exposing), `Navigation.deltaVLeft` (delta-v), `ShipStatusProvider` (propellant).

**Replaces.** FLIGHT section, the "Markers" line, the propellant gauge.

### H2 Navigation block (Helm)

**Purpose.** Where the ship is going and what happens next.

**Zone.** Left middle, below P5.

**Face:**

```
 ORBIT   Earth  alt 400 km      Ap 403 km T-24:10   Pe 397 km T-1 h 10 m
 NAV     Shackleton Ice  1,240 km  opening 220 m/s
         closest 3.2 km at T-2 h 05 m, 14 m/s
 NEXT    burn 1 of 2  T-12:05   420 m/s   flown by: helm
 PLAN    Δv need 860 / have 1,240 m/s
```

- The ORBIT line shows the time to each apsis. Period, eccentricity and inclination-like details are
  in the expanded face and the map.
- The NAV lines appear only with a nav target.
- NEXT names the next event of any kind: a burn, a change of sphere of influence ("enters Moon SOI
  T-2.4 d"), an impact, an arrival. Under warp it lists the next three events, each with a
  warp-to-it affordance.
- PLAN appears only with burns planned; it turns caution when short (and P2 carries PLAN SHORT).

**Compact face** (approach, combat): the NAV first line and NEXT only.

**Data.** `OrbitInfoProvider`, `Navigation.readout` pieces (structured, not strings),
`Navigation.approach`, `nextBurn`, `deltaVNeeded/Left`, `SoiPredictor` events, apsis times (new).

**Replaces.** ORBIT section, the navigation readout in the status column, the small orbit panel.

### H3 Docking display (Helm, Approach)

**Purpose.** Fly into a port without reading numbers.

**Zone.** Bottom centre, taking H1's place and growing to 400 x 190; H1's speed and thrust fields
become a strip along its foot.

**Anatomy.**

| Part | Shows | States |
|---|---|---|
| Port line | Our port n of m (size) → their port #k; armed; clearance: not asked, asked, granted, refused (reason) | Neutral; caution on refused |
| Alignment picture | Lateral offset (m) and angle error (deg) as one two-axis picture; centre is aligned | Within tolerance, off, far off |
| Closing gauge | Distance to the port, closing speed against the safe speed for that distance (`PlayerDocking.safeSpeed`), the safe band marked | Safe, fast, danger (the same three as the corridor W4) |
| Time to contact | "contact T-48 s" | – |
| Capture state | Collar has hold, drawing in; docked | Advisory |
| Helm order | "Helm is docking: aligning" when a dock order flies it | – |

Port selection (H) and arming (G) act on the port line. With one port, the "n of m" is not shown.

**When.** Approach situation. On capture it shows the capture state for 2 s, then X1 takes over.

**Data.** `PlayerDocking.approach` (`DockingSystem.Approach`), port states, clearance (needs
exposing), `HelmOrders`.

**Replaces.** The three docking lines in the status column, "SLOW DOWN".

### H4 Ship condition (Helm, fire control, gun; deck when abnormal)

**Purpose.** The ship's health in one glance; the detail when it matters.

**Zone.** Left bottom.

**Compact face** (dark cockpit), 300 x 40: four status lights with labels, Hull · Shields · Power ·
Life support. Each is nominal (unlit), caution or warning, by the same thresholds as P2.

**Expanded face**, 308 x 230, when any light is not nominal, or on hover (or the detail key). It
stays expanded for 10 s after the last light goes dark, so a flickering condition doesn't make it
bounce:

- The ship's plan (today's `WireframeMinimap`), with fire, breach, vacuum, smoke, hit flashes,
  disabled and stunned systems, crew positions as dots, and where crew are working.
- Four bars: Hull (integrity), Shields (cloud mass of capacity, raised or lowered, trim), Power (store
  share, output against demand), Life support (oxygen tanks, or EMERGENCY kg; worst room pressure).

On deck the plan is left out (the deck is the plan) and the face appears only when a light is lit.

**Data.** `ShipStatusProvider`, `WireframeSyncSystem`, shield status (new), power demand (new).

**Replaces.** SHIP STATUS gauges (all but propellant), SHIP panel.

### H5 Contact scope (Helm, fire control, gun)

**Purpose.** What is around the ship, and which way.

**Zone.** Right bottom, 200 x 200.

**Content.** Contacts as glyphs by allegiance (own, friendly, neutral, hostile, beaten, stale); the
Lock, Nav and objective marks; missiles inbound and outbound as their own glyphs; the ship's heading;
range rings and the range setting. North up, like the view.

**Range.** Automatic by situation (approach 2 km; combat: the smallest step that holds the lock and
every inbound missile; cruise: 50 km), with − and + to set it by hand until the situation changes. The
steps stay `RadarScope.RANGES`.

**When.** Approach and combat always. In cruise, only while a contact is inside 50 km; otherwise it
collapses to a 48 x 48 button showing the count of contacts beyond.

**Interaction.** Click a contact: Lock if a ship, Nav if a station or object (as now).

**Replaces.** RADAR panel. Adds missiles and the Nav mark.

### H6 Lock card, compact (Helm)

**Purpose.** What is locked and whether a shot will land.

**Zone.** Right middle, 308 x 110.

**Content.** Name; allegiance and state (hostile, firing on you, fleeing, disabled, surrendered,
wreck); range; closing or opening; time of flight for the guns ("shot 1.3 s", or "no solution"); best
gun's hit chance.

**When.** Whenever a lock is held at the helm, except under warp and during an approach without
combat.

**Data.** `TargetingProvider`, `HostileLabel` states (structured), `HitChance`.

**Replaces.** TARGET section, the status column's lock line and "FIRING ON YOU".

### H7 Armament strip (Helm, combat)

**Purpose.** Can I shoot, and with what.

**Zone.** Right middle, under H6, 308 x 70.

**Content.** Guns: ready, reloading (s), no power, out of arc; who fires them: you, the gunner, or
point defence only. Missiles: left, reload. Decoys: left. Shields: raised or lowered and trim, as one
item (the full control is F3).

**Replaces.** "Missile: ready (4 left)", "(gun reloading)".

### F1 Lock card, full (Fire control, gun)

Everything in H6, plus: speed relative to the ship and its direction; per gun (or the one gun) in arc
or not and the hit chance; what the target is known to be doing (closing to range, kiting, flanking,
fleeing, from the NPC combat stance where it is known); and its known condition (disabled systems,
surrendered), where sensors allow. Zone: right middle, 308 x 190.

### F2 Weapons rack (Fire control; one gun at a gun seat)

**Purpose.** Every mount at a glance, as a row each.

**Zone.** Bottom centre, up to 620 x 150.

**Row anatomy.** Mount (nose, port, starboard ...) · weapon · state (ready, reloading s, no power,
damaged, stunned s, out of arc) · hit chance on the lock · ammunition for launchers · controller
(you, crew, PD). The mounts "in hand" (all at the console, one at a gun) are marked.

Missiles and decoys are rows of their own. At a gun seat the rack shrinks to that gun's row plus
missiles and decoys if the console fires them.

**Data.** Weapon mounts (`WeaponMountComponent`: state, `ammo`, cooldown), power, `HitChance`, arc
(needs a provider).

### F3 Shield control (Fire control)

**Purpose.** Where the shield is thin, and lean it.

**Zone.** Left bottom, merged with H4's expanded face, 308 x 230.

**Content.**

- A ring around the ship's outline in 8 sectors, each showing the cloud's thickness there (summed from
  `ShieldCloudComponent.line` and `anchored`), so a dead emitter's gap shows.
- Total: cloud mass of capacity, scattered and returning, adrift.
- Raised, lowered, stowing (3 s); lowered for docking.
- Trim: Even, Bow, Port, Stern, Starboard, the current one marked; 8 cycles it, and the five can be
  clicked.
- Emitters: each one's holding share, heat against trip, tripped and back-in time.

**Data.** `ShieldCloudComponent` and the emitters (new provider). The combat design lists this gauge
and coverage overlay as not built.

### F4 Threat list (Fire control, gun)

**Purpose.** What is shooting at us, in order of danger.

**Zone.** Left middle, 308 x up to 160.

**Content.** Inbound missiles sorted by impact time: time to impact, bearing, point defence engaging
or not, decoy advised. Then hostile ships in the fight: name, range, state. A click on one locks it.

**When.** Fire control and gun seats, while a missile is inbound or a hostile is in the fight.

### D1 Captain vitals (Deck)

**Purpose.** The captain's own survival.

**Zone.** Left bottom, 308 x 90.

**Content.** Condition (OK, light, serious, critical with bleed-out time, unconscious); suit on or
off; suit air (mm:ss) while on and not breathing ship air; the air where they stand (normal, thin,
vacuum); what they carry, if anything.

**When.** On deck whenever any field is not normal or the suit is on. Hidden on a healthy captain in
good air. At a seat, a problem shows through P2 instead (SUIT AIR, CAPTAIN WOUNDED).

**Data.** The avatar's crew health and suit (new provider over `PlayerAvatarComponent`, `Suit`, the
tile's atmosphere).

### D2 Inspect card (Deck)

**Purpose.** Make the deck the interface: hover or click anything to learn about it and act on it.

**Zone.** Beside the cursor (or the focused object with the pad), kept out of the side columns.

Hovering shows the card; a click pins it, and only a pinned card's buttons act, so a stray click on
the deck never switches anything. Switching off a reactor or life support asks again. With a gamepad
the subject is the object nearest the captain in the direction they face (as the interact prompt
picks a console today), and a button cycles to the next.

| Subject | Shows | Actions |
|---|---|---|
| Crew member | Name, role, condition, activity ("sealing a breach", "lounging on the couch"), suit, morale | Open in Ship › Crew; the EVA order that applies (send out, call in, let out) |
| System | Type, health, powered, on or off, stunned (s), circuit | Switch on or off (today's console power switch) |
| Tile or room | Pressure, temperature, fire, smoke | – |
| Door | Open, shut, jammed, held | Force (F), cut (B) as today |
| Cargo bay | Contents, volume | Open Ship › Cargo |

**Replaces.** `ConsoleWindow`'s default power panel. The developer console's click-to-select stays in
the developer layer.

### D3 Off-screen pointers (Deck)

**Purpose.** On a ship larger than the view, point at trouble off screen.

**Zone.** Screen edge, on the line from the captain toward the subject.

**Content.** Fires, breaches, crew down, boarders, each with its glyph and distance in tiles. At most 5,
nearest first; warnings before cautions.

**When.** Deck, while such a subject is off screen.

---

## 8. Situation panels

### X1 Station panel (Docked)

**Zone.** Right middle, 308 x 260.

**Content.**

```
 HIGHPORT · Terran Patrol Authority          standing: Trusted (+42)
 Port 6 · docked
 [T] Market      [Y] Yard: 2 repairs, tanks 86%     [J] Missions: 4 offers
 Shipyard: 3 ships        Hire: 3 looking            (tabs in the terminal)
 Infirmary: treating 1 (bill 150 cr)
 Credits 5,000 cr     Hold 12 / 40 m³
 [U] Undock                          autosaved
```

Services the station doesn't offer are left out. The counts tell the player whether a service is
worth opening.

**Replaces.** The dock banner, "Docked at Highport strain 0%", the docked key hints, "You are docked at
your home station. J opens its mission board".

### X2 Boarding panel (Boarding)

**Zone.** Right middle, under any lock card.

**Content.** The other ship's name and state (disabled: why, surrendered, being boarded, taken);
latched or by EVA; boarding party: n across, fighting, holding; salvage: parts marked and stripping,
cargo coming across; prisoners alongside. "[K] Boarding" opens M4.

### X3 Emergency card (Emergency)

**Zone.** Top centre, under P3 if both show, 500 x up to 140.

**Content.**

- **Disabled:** "SHIP DISABLED: no power". The ways out with their keys and what they cost:
  "Abandon ship: P twice"; "Call a tow: B twice, 3,400 cr and 3 standing" (only when the beacon allows
  it, `DistressBeacon.refusal`). Second presses within 3 s confirm, as now; the card shows the pending
  confirmation.
- **Abandoning:** pods: crew aboard n of m; "P launches now".
- **Suit air critical:** "Your suit: 1:40. Nearest air: Galley, 6 tiles" (direction pointer via D3).

**When.** Emergency situation. While it shows, the context layer dims everything but H4 and D1.

---

## 9. World overlays

Drawn in the world, exterior seats only (the deck has its own world art). All follow the label rules
below.

### W1 Contact markers and labels

- **Glyph** for every marked contact, by allegiance and kind (station, ship, derelict, pod, missile).
- **Lock** and **Nav** marks: two different shapes, either or both on a contact.
- **Labels** by priority: Lock, Nav, objective, hostile in the fight > stations > other ships >
  derelicts. Labels show the name; range is shown only for Lock, Nav and hostiles.
- **Decluttering.** Labels that would overlap merge into one cluster label ("3 contacts"); hovering
  expands it (the inset views already do this with "[+] 3 others"). Below the top two priorities,
  labels drop out first when space runs short.
- **Off screen.** One edge marker per contact that matters (Lock, Nav, objective, hostile, inbound
  missile), with range or time to impact. Others don't get edge markers.
- **Under warp.** Only Nav and objective labels.
- **Hover card.** Hovering a contact (or its scope glyph) shows a small card: name, kind, faction,
  state (hostile, fleeing, disabled, surrendered, wreck, docked), range, and the one-line detail the
  traffic map already has ("to Gateway, 2 h"). A click selects as today. This is how a player learns
  about a contact without locking it.

### W2 Velocity markers

Prograde and retrograde 70 units from the ship, as now, in the reference frame of H1 (section 4.5),
with the frame tag on them when it is not ORBIT.

### W3 Burn cues

The next burn's arrow from the ship with its countdown. Later burns appear only on hover of H2's NEXT
line, or in the map. (Today all are drawn with numbers.)

### W4 Approach corridor

Kept: from the port, coloured safe, fast or danger like H3's closing gauge. Approach situation only.

### W5 Fire arcs, hit lanes, lead reticle

Kept as `FireArcOverlay` and `LeadReticle` draw them, at fire control and gun seats, and at the helm in
combat with a lock. Outside combat at the helm they are hidden.

### W6 Missile tracks

Predicted paths, the intercept cross and point-defence kills, as `ThreatOverlay` draws them. Their edge
arrows join W1's edge markers.

### W7 Inset views

The picture-in-picture bubbles for the own ship, Lock and Nav when the view is zoomed out too far to
see them. Kept, anchored in the world as today (over the craft, beside it with a line to it, or at the
edge toward it), but clamped to the world area: a bubble that would fall under a side column or the
bottom-centre instrument slides along its edge marker's line until it is clear. Each inset carries its
Lock or Nav mark. Its "[+] 3 others" list is W1's cluster label, so the two read alike.

---

## 10. Full-screen modes

Every mode has the same frame: a title row with its tabs, the content, and a foot row with the one-line
message feed and the keys to change tab and close. P1 stays above, P3 and X3 still interrupt.

### 10.1 M1 Map

Keeps everything the `ManeuverMap` does (zoom, pan, burn handles, transfers, traffic, patched conics).
Its panels are rebuilt:

| Panel | Zone | Content |
|---|---|---|
| Plan | Left, 320 wide | Orbit now (body, Ap and Pe with times, period); the burns as a list (number, T-, Δv, prograde and radial); the selected burn's result; totals (Δv need and have, arrival); actions: Helm flies burns, Autopilot, Warp to next burn, Clear |
| Target and traffic | Right, 320 wide | The Nav card (name, kind, detail line, distance, closest approach); transfer: Plan, Accept, Discard; a contact list sortable by distance or kind; layer toggles: stations, haulers, pirates, derelicts, objectives, faction tension |
| Timeline | Bottom, full width between the panels, 90 tall | A time axis from now to the last event: burns, apsides, sphere-of-influence changes, impact, arrival, contract deadlines. Click an event to centre the map on it; a warp-to on each future event |

The tension readout leaves the default map face and becomes a layer.

### 10.2 M2 Ship

Tabs: **Systems**, **Crew**, **Cargo**, **Loadout**. Key I opens the last tab used (Crew the first
time).

| Tab | Content |
|---|---|
| Systems | The ship's plan at large scale, every system coloured by state; a list grouped by type with health, powered, on or off (switch), stunned, circuit; power: circuits, output, demand, store; **damage control board**: every hazard and repair task with who has it, or "unassigned" |
| Crew | Today's `CrewWindow` roster (role, skills, condition, morale, wage, history), sortable; EVA orders (send out, call in, let out); suits at battle stations; prisoners and what can be done with them here |
| Cargo | Hold contents (commodity, units, volume), capacity; salvaged parts aboard |
| Loadout | Hardpoints and what each carries; field refit (today Y away from a yard); furnishings aboard; ship figures (mass, full Δv, berths) |

### 10.3 M3 Station

Docked only. Tabs: **Market** (T), **Yard** (Y), **Shipyard**, **Missions** (J), **Hire**. A right rail
on every tab: credits, standing with the station's faction, hold used of capacity, tanks.

| Tab | Content |
|---|---|
| Market | Today's `TradeWindow`: per commodity stock, buy, sell, held, on order, and the trade buttons |
| Yard | Repairs (the damage list and Repair all), supplies (tanks and air, with their cost), refits (each hardpoint with new, used and salvaged options), furnishings (buy and arrange), Rebuild decks (opens the ship editor) |
| Shipyard | Ships for sale (price, after trade-in, locked and why); your ship's trade-in; a prize in tow (sell or turn in, with terms) |
| Missions | Offers (title, giver, terms, note, locked and why), each with Take; the faction tension line; the contracts already held, one line each with Give up, so the player can weigh a new job against them (their full detail is in M5) |
| Hire | Who is looking for a berth (role, skills, terms), berths free and the week's wages |

The station's infirmary is passive and shows in X1 and the rail.

### 10.4 M4 Boarding

Today's `BoardingWindow`, with the same orders: board, storm, recall; parts to strip (mark, value,
status); cargo to take; prisoners to bring aboard. Adds a plan of the other ship with the boarding
party's positions while they are aboard.

### 10.5 M5 Captain's record

Tabs: **Career** (rank, sponsor, budget and next pay, promotions, bounty record), **Standing** (each
faction, and tension by region), **Contracts** (held contracts in full, which one is tracked by P5,
set as Nav, give up), **Log** (every message, with day and time, filterable by source and severity;
L opens this tab).

### 10.6 M6 Controls

The F1 panel as today, with the current seat and situations first ("Here: Helm, approach") and the
rest below. Gamepad layout as its own tab.

### 10.7 Shell

Main menu, pause menu, save and load, game over: unchanged in function. Settings gains **Interface**:

| Setting | Options |
|---|---|
| UI scale | As today |
| Key hints | Learning (default), Always, Off |
| Reference frame | Auto (default), Orbit |
| Readouts | Compact (default), Expanded (for players who want every number by default) |
| Message feed | Hold time: short, normal, long |
| Flashing | On, Off |

The lighting toggle moves to Settings › Graphics.

### 10.8 Developer layer

The debug menu (F3), the developer console (~), its overlays (heat and the others), scenario tools,
scenario and content-error notices, and the lighting toggle key belong here. Rules:

- Only in builds or runs with developer tools on.
- Drawn in its own band and its own visual style, never inside the player's zones, so it is never
  mistaken for game UI and never moves it.
- Nothing in the player HUD may depend on it.

---

## 11. Context walkthroughs

Each walkthrough is what is on screen, nothing more. The grey-box wireframe for each is in
[`wireframes/`](wireframes/).

### 11.1 Helm, cruise

![Helm, cruise](wireframes/01-helm-cruise.png)

P1 (HELM, clock, warp) · P5 objective (compact) · H2 navigation · H1 flight display · H4 four quiet
lights · H5 as a small button (no contacts within 50 km). P2, P3 and P4 are empty and invisible. About
10 lines of text, against about 40 today.

### 11.2 Helm, warp

![Helm, warp](wireframes/02-helm-warp.png)

P1 shows the warp rate and any hold reason. H2 expands to the next three events, each with warp-to. H1
is reduced to delta-v. H5, H6, H7 and W1 labels other than Nav and objective are gone.

### 11.3 Helm, approach

![Helm, approach](wireframes/03-helm-approach.png)

H3 docking display takes bottom centre. H5 scope at 2 km. H2 is down to the Nav line. W4 corridor in
the world. P2 shows APPROACH FAST only if the player is coming in too fast.

### 11.4 Helm, combat

![Helm, combat](wireframes/04-helm-combat.png)

A raider fires two missiles and one hit has holed the cargo bay. P3 shows the missiles. H6 lock card
and H7 armament on the right; H5 scope at the range that holds the raider and the missiles; H4 has
expanded by itself because the breach lit its hull light, and P2 lists the breach with the crew sealing
it; the master alert cue on H1 caught the eye when the breach came. H1 stays; H2 is down to one line.
P1 shows BATTLE STATIONS. Had the ship been unhurt, H4 would still be four dark lights.

### 11.5 Fire control, combat

![Fire control](wireframes/05-fire-control.png)

F2 weapons rack at bottom centre; F1 full lock card right; F4 threat list left; F3 shield control with
H4 bottom left; H5 scope bottom right; P1 shows FIRE CONTROL and "Helm: Bea, holding heading".

### 11.6 Deck

![Deck](wireframes/06-deck.png)

P1 (DECK, helm authority), P5 compact, and nothing else until something calls for it: the P6 prompt
appears at the helm console as the captain walks up; D2 appears over whatever the pointer rests on.

### 11.7 Deck, emergency

![Deck, emergency](wireframes/07-deck-emergency.png)

The ship is disabled and venting. X3 offers abandon ship and the tow with their costs; P2 lists FIRE,
VACUUM and CREW CRITICAL with who is handling each; D1 shows the captain's suit air; D3 points to the
fire and the casualty off screen.

### 11.8 Docked

![Docked](wireframes/08-docked.png)

X1 station panel on the right; P5 objective; H4 compact (or expanded if there is damage, which the yard
can fix); P1. The flight instruments retire.

### 11.9 Map

![Map](wireframes/09-map.png)

### 11.10 Station terminal

![Station terminal](wireframes/10-station.png)

### 11.11 Ship, systems

![Ship systems](wireframes/11-ship-systems.png)

### 11.12 Orders menu

![Orders menu](wireframes/12-orders-menu.png)

O at the helm in cruise. The orders that need the Nav in the bubble say so, and the tow says why it is
refused; nothing is hidden, so the player learns what exists.

---

## 12. Contract for the art directions

This is what the three art directions must keep, and what is theirs to change. The functional
designs from the other two threads may differ; this section applies to skinning this one.

### 12.1 Fixed

- The data each widget shows, its states, and when it appears (sections 6 to 10).
- Zone membership and stacking order (section 5). A widget may move within its zone.
- The centre clear area.
- Priority order in P2, W1 labels and the zone overflow rules.
- One home per datum: an art direction may not duplicate a value elsewhere for decoration.
- Every state below must be distinguishable **without colour** (shape, icon, label, fill or pattern
  as well), for colour-blind players.

### 12.2 Theirs to design

- Every visual: shape, framing, typography, colour, texture, line, glow, sound cues for UI.
- How a widget is built: a "two-axis alignment picture" may be a crosshair, a pair of needles, a
  segmented display or a scope trace; a "bar" may be a gauge, a stack of segments or a dial.
- Diegetic dressing: bezels, screws, scanlines, labels on hardware, the seat's console frame around
  the view, as long as the zones stay readable.
- Motion style within section 5.3.
- Exact geometry within ±10% of the stated sizes, and the inner layout of each widget.

### 12.3 Semantic tokens to style

| Group | Tokens |
|---|---|
| Severity | nominal, advisory, caution, warning, warning-urgent (time-critical) |
| Allegiance | own, friendly (sponsor and allies), neutral, hostile, beaten (disabled, surrendered, wreck), unknown or stale |
| Selection | lock, nav, objective, hover, focus (pad), selected |
| Path | actual, planned, predicted (missile), corridor-safe, corridor-fast, corridor-danger |
| Handling | unassigned, crew on it, resolved |
| Control | idle, hover, focus, pressed, disabled, active (toggle on) |
| Data | live, stale (last known), unavailable ("--") |
| Hit chance | good, fair, poor (today green, yellow, red) |

### 12.4 Sizes and legibility

- Body text at least 13 UI units; values at least 15; P3 at least 22. (Today: body 15, banners 22.)
- Anything that can be clicked is at least 24 units in its smaller dimension.
- Widget sizes in this document are targets at 1280 x 800 units; `UiScale` scales them.

---

## 13. What is cut, and why

| Cut | From | Why | Where it went, if anywhere |
|---|---|---|---|
| "View EXTERIOR [V]" | Flight panel | The seat says it, and the player sees which view it is | P1 seat |
| "Markers orbit / TARGET" line | Flight panel | Separate line for a frame that belongs with the speed | H1 frame tag |
| Heading (deg) | Flight panel | The ship's drawing shows its heading; alignment for docking is in H3 | H1 expanded |
| Orbital speed as the default speed | Flight panel | ~7,670 m/s in low orbit, unchanging, rarely a decision | H1 speeds in the reference frame; ORBIT frame keeps it |
| Period in seconds | Flight panel | Unreadable; times to apsides are what matter | H2 expanded, as a time |
| Second and third copies of the lock | Status column, nav readout | Duplicates | H6 / F1 |
| "Target: none", "No target [Tab]" | Status column, flight panel | Says nothing | – (a learning hint teaches Tab) |
| "Missile: ready" out of combat | Status column | Not a decision until combat | H7 |
| Budget line | Status column | Not a flight decision | M5; payday in the feed |
| Small orbit panel and its caption hints | Bottom right | Duplicates the map and the orbit numbers | M1; H2 |
| "ALL SYSTEMS NOMINAL", four bars at 100% | Ship panel, gauges | Dark cockpit | H4 compact lights |
| "Dock port 1/1: S active, free" | Status column | Debug phrasing; shown even far from any port | H3 port line, approach only |
| "strain 0%" | Status column | Debug value | P2 DOCK STRAIN above 50% |
| Raw approach numbers | Status column | Unflyable as text | H3 |
| Fixed bottom key hint line | Bottom edge | Permanent teaching of known keys | P6 learning hints; M6 |
| "Lighting on/off" (O) | Notices | Graphics option, not play | Settings › Graphics; developer layer; O opens the orders menu |
| Range under every nameplate | World | Clutter | Lock, Nav and hostiles only |
| All later burns' arrows and numbers | World | Clutter | W3 next burn only; M1 |
| "Scenario: ...", "Content loaded with errors" | Notices | Developer information | Developer layer |
| Console power panel on every console | E | A modal window for a single switch | D2 inspect card |

---

## 14. What the simulation must provide

The design needs these from the game, through ui-hud's provider pattern (interfaces in
`com.ringsofiron.uihud.providers`, filled in core):

| Provider | New or changed | Data |
|---|---|---|
| `UiContextProvider` | New | Seat (helm, fire control, gun and which, deck); situation flags with their reasons; helm authority (who, order, autopilot, held reason); battle stations; warp hold reason |
| `ConditionProvider` | New | The P2 catalogue as structured rows: code, severity, detail, location tiles, handling (assigned crew count), timer |
| `MessageProvider` | Change `NoticeLog` | Each notice with a source and a kind split into event or condition; conditions stop posting as notices |
| `ShieldStatusProvider` | New | Stock, capacity, cloud, scattered, adrift, stowed, raised, lowered for dock, trim, sector thickness (8), emitters (holding, heat, trip) |
| `WeaponStatusProvider` | New | Per mount: name, position, weapon, state, reload, power, stunned, in arc, hit chance on the lock, ammunition, controller; missiles; decoys; point defence active |
| `CaptainStatusProvider` | New | Condition and bleed-out, suit on, suit air, local air, carried item |
| `FlightStatus` | Extend `ShipStatusProvider` or new | Thrust per axis, rotation assist, delta-v left, acceleration, mass, frame velocities |
| `NavigationProvider` | New, over `Navigation` | Structured readout: nav target, distance, closing, closest approach, next events (burns, apsides with times, SOI changes, impact, arrival), plan Δv |
| `DockingProvider` | New, over `PlayerDocking` | Ports, selected, armed, clearance state, the approach (distance, lateral, angle, closing, safe speed), capture state |
| `StationProvider` | New, gathering existing | Station name, faction, standing, services available with counts, infirmary |
| Contact state | Change `HudMarkerComponent` | Allegiance and state as a value (from `HostileLabel`), not only tint and label text |
| Inspect | New | What is under the pointer on deck: crew, system, tile, door, bay, with their fields |

The existing providers (`TradeProvider`, `YardProvider`, `MissionProvider`, `BoardingProvider`,
`CrewProvider`, `ShipyardProvider`, `OrbitPathProvider`, `ManeuverPlannerProvider`,
`TrafficMapProvider`, `ThreatProvider`, `TargetingProvider`) carry the full-screen modes as they are.

---

## 15. Input, focus and the Esc stack

- **Keys keep their actions** (section 4.3 maps them to modes). New: a detail key (hold) to expand
  every instrument, a frame key to cycle the reference frame, and O for the orders menu (C1), which
  the lighting toggle gives up. All rebindable. E becomes each object's main action (P6).
- **Esc closes the topmost thing**: the inspect card, then a mode, then (with nothing open) the pause
  menu, as `GameShell.hudWindowOpen` does today.
- **The same key closes the mode it opened**; a mode's tab keys switch tabs inside it.
- **One mode at a time.** Opening another replaces it.
- **Gamepad.** Every mode and every clickable widget is reachable with `FocusNav`. In the HUD, the pad's
  Back layer opens modes as today. The inspect card follows the focused object.
- **Mouse.** Hover expands an instrument; click on contacts, scope and map selects as today.

---

## 16. Building it

For whoever implements it. In order, each step shippable on its own:

1. **Context and zones.** `UiContextProvider`; the zone layout in `UiHud`; P1 to P4 replacing the
   status column; the cut list; the orders menu (C1), which only gathers orders that already exist.
   The biggest visible change for the least work.
2. **Helm.** H1, H2, H3, H4, H5, H6, H7; the reference frame; W1 decluttering and W3.
3. **Fire control.** F1 to F4 with `WeaponStatusProvider` and `ShieldStatusProvider`.
4. **Deck.** D1 to D3; the inspect card replaces the console power panel.
5. **Modes.** M2 Ship, M3 Station and M5 Captain's record consolidating today's windows; X1 to X3.
6. **Map.** M1's new panels and the timeline.

Each step keeps `UiScaleTest`-style layout tests: no widget overlaps another at 1280 x 800 and at the
largest scale, and the centre stays clear.

---

## 17. Questions for Joe

1. **Reference frame.** The speed and velocity markers would follow the port, then the Nav, then the
   Lock, then the orbit, automatically (section 4.5). Today the markers follow the Lock only. Is the
   wider rule what you want?
2. **Modes from anywhere.** The keys open Ship, Map and Captain's record from any seat; consoles on deck
   open them too, preselected. Or should some (Ship › Systems) need the right console?
3. **Key hints.** Learning mode by default, retiring a hint after it has been used 3 times?
4. **Modes and time.** Full-screen modes don't pause the game. Should any of them (the station
   terminal while docked, say) pause or slow time?
5. **Lock and Nav.** Keep them as two selections with two names, or merge them into one "selected
   contact" with weapons and navigation both following it?
6. **Acknowledging warnings.** Should warnings need an acknowledge press to stop pulsing and sounding,
   as in aircraft, or is the 2 s pulse and the master alert cue enough?
7. **O for orders.** The orders menu takes O from the lighting toggle, which moves to Settings. Is a
   different key better for you?

---

## Revision log

| Version | Round | Changes |
|---|---|---|
| 1 | 0 | First version: inventory, context model, widget catalogue, wireframes |
| 2 | 1 (self-critique) | H4 no longer expands just for combat, only when a light is lit (dark cockpit held in fights). Master alert cue on the primary instrument for new warnings. Objective shows in combat for fight contracts. The reference frame never switches mid-manoeuvre. Message feed summarises during combat. New C1 orders menu on O, gathering every order with why it can't be given. E is each object's main action. Inspect card: pin before acting, confirm risky switches, gamepad subject rule, EVA orders. Hover card for exterior contacts. COLLISION condition. Inset views stay world-anchored, clamped clear of widgets. Station panel no longer misassigns I and Y. Missions tab shows held contracts. Zones given exact bounds; the centre clear area moved to 220..590 so interrupts fit above it. Deck applies aboard any craft. Time format examples fixed. Wireframe 12 added |

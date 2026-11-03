# Ship Stations: a diegetic functional design for the UI

Functional design 3 of 3 for the UI overhaul. Approach: **the UI is the ship's own instruments.** Information is organised by crew station (helm, navigation, tactical, engineering, comms and cargo, command), the screen stays dark while the ship is well, and information comes forward when it matters.

This document is style-agnostic. It says which widgets exist, what data each carries, where it sits and when it is visible. It says nothing about colour, typeface or shape beyond semantic roles; the artist threads decide those (see [Notes for the artists](#17-notes-for-the-artists)).

**Revision: round 5.** What changed in each round is listed in [the changelog](#21-changelog).

Contents

1. [Summary](#1-summary)
2. [What is wrong now](#2-what-is-wrong-now)
3. [Principles](#3-principles)
4. [Access: where the player is decides what they can do](#4-access-where-the-player-is-decides-what-they-can-do)
5. [The six stations](#5-the-six-stations)
6. [Layers and screen zones](#6-layers-and-screen-zones)
7. [The always-on overlay](#7-the-always-on-overlay)
8. [Seated clusters](#8-seated-clusters)
9. [Multi-function displays and pages](#9-multi-function-displays-and-pages)
10. [Panels](#10-panels)
11. [World symbology](#11-world-symbology)
12. [Ship condition](#12-ship-condition)
13. [Alerts and callouts](#13-alerts-and-callouts)
14. [Visibility](#14-visibility)
15. [Context walkthroughs](#15-context-walkthroughs)
16. [Input](#16-input)
17. [Notes for the artists](#17-notes-for-the-artists)
18. [What happens to every current element](#18-what-happens-to-every-current-element)
19. [Implementation notes](#19-implementation-notes)
20. [Open questions for Joe](#20-open-questions-for-joe)
21. [Changelog](#21-changelog)

---

## 1. Summary

Rings of Iron already has the bones of a diegetic interface. The player's avatar walks the deck, uses consoles that are real tiles in the ship (`ShipConsoles`: helm, weapons, gun, life support, reactor), and must sit at a console to see outside (GDD §6). Crew man those consoles (`Bridge`, `GunCrews`). The HUD ignores all of this: every panel is on screen at every console, on deck and docked alike.

This design makes the existing physical model the organising principle of the UI:

- **Six stations** are the jobs aboard: **Helm, Navigation, Tactical, Engineering, Comms & Cargo, Command.** Consoles are the hardware that hosts them. A small ship's helm console hosts three stations; a big ship may have a console for each.
- **Two verbs at every console.** **[E] Use** opens the console's panel, with a tab for each station it hosts. **[V] Sit** (helm, weapons and guns) gives the outside view with that seat's instruments.
- **Where the avatar is decides what is on screen.** Seated at the helm: the helm's cluster, two multi-function displays and a station board along the bottom edge. At the weapons console: fire control. Walking the deck: the deck, and nothing else unless something is wrong.
- **Read anywhere, act at the station.** Any station's pages can be read on a repeater display at a command seat or on the captain's slate. Flying, fighting and switching systems need the console, or an order to the crew member manning it.
- **A dark ship is a healthy ship.** No "ALL SYSTEMS NOMINAL", no empty "No target" rows, no full bars. In quiet flight even the multi-function displays fold down to their page tabs.
- **Alerts pull information forward, and their severity says whether anyone is dealing with it.** A fire the crew are fighting is a caution; a fire nobody is fighting is a warning. The engineer calls it out by name, the engineering console glows on deck, and a seated player's right-hand display turns to the damage page by itself, then back when it is out.
- **Ship condition (Green, Yellow, Red) is the threat level** and sets which symbology is drawn and which pages the displays open on. Docked and Emergency are states that layer over it.

---

## 2. What is wrong now

From the flight, docked, deck and yard screenshots in `documentation/ui-hud/polish/`:

| Problem | Example |
|---|---|
| **Everything is always on.** Nine blocks are on screen in normal flight whatever the player is doing. | Flight, Target, Orbit, Ship status, Radar, Orbit minimap, Ship minimap, status column, key line, all at once. |
| **The same fact in several places.** | The target is in the Flight panel, the status column ("Target: Raider") and as a world label. The missile warning was in the banner and the status column. The orbit is in the Flight panel and the Orbit minimap. |
| **Empty and nominal states take space.** | "No target [Tab]", "HULL 100% ALL SYSTEMS NOMINAL", four full bars at 100%, "Missile: ready". |
| **Raw numbers instead of answers.** | "Speed 7672.5 m/s" is orbital speed, useless when closing on a station; "Propellant 2,000 kg" does not say whether the ship can get home; "Heading 0.0 deg" says nothing the picture does not. |
| **One text column for everything.** | The top-right column holds the clock, warp, target, missiles, rank, budget, notices, docking, navigation, contracts, guides, the helm and EVA in seven colours, with no order of importance. |
| **Key hints as permanent furniture.** | The bottom line and "N plans a burn, Q picks a navigation target" under the orbit minimap never go away. |
| **No idea of where the player is.** | Docked, the flight readout still shows 7.6 km/s. On deck, the flight panel and orbit are still up although the player cannot see outside. |
| **Labels pile up.** | "Freighter 3.0 km / Highport 3.0 km / Freighter 3.0 km" stacked in a column at the bottom of the view. |
| **Each module brought its own window.** | Market, yard, mission board, boarding, crew and console windows each have their own layout and keys, unrelated to each other or to the ship. |

---

## 3. Principles

### 3.1 The ship is the interface
Every piece of information on screen has a source aboard: a sensor, a console or a crew member. The UI shows what that source can show. When a source is gone (the console destroyed, no power, nobody at it), the UI says so instead of carrying on as if nothing happened. This is already true in the simulation (`Bridge` takes the helm away when the helm loses power); the UI should follow.

### 3.2 Stations are jobs, consoles are hardware
A **station** is a job: a set of pages, controls, symbology and alerts. A **console** is a tile on the deck. Each console hosts one or more stations, by a data table, so a corvette's single helm console can host Helm, Navigation and Comms, while a cruiser can give each its own console. Every station must be reachable on every ship (see [5.1](#51-hosting-which-console-hosts-which-station)), so no hull can soft-lock the player out of a function.

### 3.3 Read anywhere, act at the station
- **Read**: any station's pages can be viewed from any command seat (on a repeater display) or from the captain's slate, wherever the avatar is aboard.
- **Act**: controls that move the ship, fire weapons or switch systems need the avatar at a console that hosts the station, or an order to the crew member manning it.
- **Exception, docked services**: while docked, the station's own staff do the work, so the market, yard, shipyard, contract board, hiring and infirmary can be used from anywhere aboard.
- **Exception, out of fiction**: time warp, saving, settings and the controls panel are the player's, not the ship's, and work anywhere, as now.

### 3.4 Dark ship: nominal is silent
Borrowed from the "dark cockpit" philosophy of airliner flight decks: when every system is normal, no light is lit. A widget whose content is nominal, empty or unchanged is hidden or folded. The player learns that anything on screen means something.

### 3.5 Modes are always shown
The exception to 3.4. A **mode** is a setting that changes how the controls or the ship respond: rotation assist, the velocity frame, a helm order or the autopilot in force, shields raised or lowered, who fires a gun (a crew member at will, the player in hand, or only the trigger). Modes are not values that can be "nominal", so the active mode is always shown where it applies, compactly, the way an aircraft's flight-mode annunciator always shows what is engaged.

### 3.6 Alerts pull information forward
Information moves up the disclosure tiers when its state becomes abnormal and back down when it recovers. The player should never have to go looking for something that is going wrong; they should have to go looking for things that are going right.

| Tier | Where | Example |
|---|---|---|
| T0 Ambient | In the world and in sound only | A console's screen glows amber on deck; a door's light shows vacuum beyond; the alarm sounds |
| T1 Notice | Annunciator tile, callout line | FIRE / CARGO 1 tile; "ENG  Okafor: fire in cargo 1, I'm on it" |
| T2 Instrument | Seated cluster, MFD page | The damage page on the right display |
| T3 Panel | A console panel or the slate | The Engineering tab at the reactor console |
| T4 Record | The ship's log | The log line, kept after it ends |

### 3.7 Severity says who is dealing with it
Whether the player must act depends on whether someone aboard already is. An alert that the crew are handling is a **caution**; the same alert with nobody handling it, or getting worse, is a **warning**. The UI never hides the problem either way; it tells the player whether it is theirs. (Section 13.)

### 3.8 One fact, one home
Each fact has one home widget. Other widgets may summarise it or link to it (an annunciator tile summarises a fire; the damage page is its home), but the same full readout never appears twice on one screen. Callouts follow the same rule (13.5).

Two consequences that apply everywhere:
- **The world identifies; the instruments measure.** While seated, the object whose numbers are in the cluster (the Reference or Target row) is labelled in the world with its name only. Its range, closing speed and the rest live in the cluster, where they hold still and can be read.
- **One countdown per missile salvo on screen.** The annunciator tile owns the count and the soonest impact. A missile track on screen carries no countdown of its own (its impact cross shows where); an edge marker keeps its seconds, because the track is off screen; the SENSORS list keeps each missile's time, because it is the table of every contact.

### 3.9 Decision-ready numbers
Show the number the player decides with, not the one the simulation stores:

| Instead of | Show |
|---|---|
| Propellant 2,000 kg | Δv 1,840 m/s, with the margin over what the trip home needs |
| Speed 7,672.5 m/s (orbital) | Speed in the frame that matters now: relative to the target, the docking port or the orbit, with the frame named |
| Heading 0.0 deg | Nothing, normally. Before a burn: the alignment error to the burn |
| Distance to a contact | Range, closing speed, and closest approach (how near, how soon) |
| Missile reload 3 s | Ready, or seconds to ready, on the launcher's own chip |
| Power 100% 3.0 MW | Which systems are without power, why, and when they come back; the reactor's output only when damage has cut it below its rating |
| Oxygen 100% | Hours of air at the current crew; only shown when it falls under a day |

Precision fits the decision: one decimal below 100, whole numbers below 10,000, then km or km/s with two significant decimals.

### 3.10 The world is the main display
In a top-down game the outside view is the best display there is. Symbology drawn into the world (markers, brackets, tracks, corridors) is preferred over panels that describe the world in words. Panels hold what the world cannot show: numbers, lists and orders.

---

## 4. Access: where the player is decides what they can do

| Presence | How | What they get |
|---|---|---|
| **Seated** | [V] at a helm, weapons or gun console (as now) | The outside view, that seat's **cluster** (section 8), its **symbology** (11), two **MFDs** (helm and weapons only), the **station board**, and the seat's controls |
| **At a console** | [E] at any console, seated or standing | The **console panel** (10.2): one tab per hosted station, every page owned, with its controls |
| **Repeater** | An MFD at a command seat showing another station's page | That station's pages, read-only, plus the **orders** it takes |
| **Slate** | Anywhere aboard: the captain's own handheld | The Command station in full; every other station's pages read-only; docked services |
| **Away** | Avatar off the ship (boarding, a station's deck) | The slate's Command tabs only; station pages read "NO LINK" until back aboard |

What needs which presence (current keys in brackets):

| Station | Read | Act |
|---|---|---|
| Helm | Anywhere | **Seated at the helm**: thrust, turn, strafe, assist [W A S D Z C X]. **At the helm console** (seated or by [E]): ports, clearance, undock [H G U]. **Any command seat**, or the slate while a crew member has the helm (new): helm orders [1 to 6, 0] |
| Navigation | Anywhere: the plot opens read-only | **At a console hosting Navigation**: plan, move and delete burns, plan to target, accept, warp to burn |
| Tactical | Anywhere | **Seated at the weapons console**: every gun in hand. **Seated at a gun**: that gun. **Seated at the helm**: the pilot's trigger, as now: fire the guns on the locked target, missiles, decoys [Space R F]. Shields raise and trim from the helm or weapons seat [9 8], as now |
| Engineering | Anywhere | **At the reactor or life support console**: switches. From the slate: damage-control priority (new) |
| Comms & Cargo | Anywhere | **At the helm console**: clearance. **At the helm or the slate**: distress beacon [B twice]. **Anywhere aboard while docked**: station services [T Y J] |
| Command | Anywhere (it is the slate) | **Anywhere aboard**: crew orders, EVA, boarding, prisoners, contracts, abandon ship [P twice] |

When the player presses a key for something they cannot do where they are, a **response** (13.6) says where to go: "Undock from the helm: [V] at the helm, then [U]." This replaces the notices posted for the same purpose today.

Console panels belong to their console: walking out of `INTERACT_RADIUS` closes one. The slate is carried: it stays open while the avatar walks. With a panel or the slate open, WASD still walks and the panel takes the arrow keys, Enter and Esc (`FocusNav`).

---

## 5. The six stations

### 5.1 Hosting: which console hosts which station

Default hosting, as data (see [19](#19-implementation-notes)) so hulls and mods can change it:

| Console (`SubsystemType`) | Seat [V] | Console panel [E] tabs | Notes |
|---|---|---|---|
| `HELM` | Helm cluster + MFDs | **Helm, Navigation, Comms & Cargo** | The command seat. The pilot's trigger works here. Comms moves off it on hulls with a comms console |
| `WEAPONS` | Tactical cluster + MFDs | **Tactical** (the Tactical board) | The second command seat: helm orders and shields work here, as now |
| `TURRET_MOUNT` (gun) | Gunner sight | **Gun**: that mount in detail, take it in hand | No MFDs: a gunner sees their gun |
| `REACTOR` | None | **Engineering**, opening on Power | |
| `LIFE_SUPPORT` | None | **Engineering**, opening on Atmosphere | |
| Captain's slate | n/a | **Command** tabs, plus a page viewer for every station | Not a tile; carried by the avatar |
| Future: nav table, comms, damage control, medbay | Optional | Navigation, Comms & Cargo, Engineering (Damage), Command (Medical) | For larger hulls; each takes its station off the helm |

Fallback rules, so no hull loses a function:
1. If no console hosts a station, the **helm console** hosts it, as an extra tab.
2. If the hosting console is **destroyed**, the station can still be read (from repeaters and the slate) and acting falls back to the helm console, except Helm itself, which falls back to nothing: the ship drifts, as `Bridge` already rules.
3. If the hosting console is **unpowered or stunned**, its pages read "NO POWER" everywhere, its controls do nothing, and a caution names it.

### 5.2 Helm

**Job:** fly the ship, moment to moment: attitude, thrust, docking, and the helmsman's orders.

| Item | Content |
|---|---|
| Cluster | Velocity, reference, Δv, next event, chronometer (8.1); docking approach mode (8.2) |
| Symbology | Prograde and retrograde markers in the active frame; burn arrows with countdown; nav target bracket; docking corridor; collision cue (new); with a target locked, the lead reticle and intercept lines for the pilot's trigger; threat tracks always |
| MFD pages | HELM (who has the helm, the order in force and how it is going), DOCK (ports, clearance, strain) |
| Console panel tab | Helm: the HELM and DOCK pages side by side |
| Manned by crew | The helmsman holds the heading or flies an order (`HelmOrders`). Callouts: order taken, finished, dropped under fire; burn started and done; "nobody at the helm, the ship drifts" |
| Alerts owned | Helm unmanned with an order waiting; helm unpowered; burn missed or cut short; collision risk (new); docking: closing too fast, collar strain, torn loose |
| Data | `FlightReadoutSystem`, `NavMarkerSystem`, `Navigation`, `HelmOrders`, `PlayerDocking`, `TimeWarpController` |

### 5.3 Navigation

**Job:** where the ship is going over hours and days: orbits, burns, transfers, traffic.

| Item | Content |
|---|---|
| Cluster | None of its own; it feeds the helm cluster's next-event row and Δv |
| Symbology | Burn arrows (shared with Helm); contract waypoint marker; nav target bracket |
| MFD pages | NAV (body, altitude, Ap, Pe, period, next SOI change or impact, a small orbit drawing), PLAN (burns: number, time to, Δv; total against Δv left; transfer proposal; autopilot) |
| Console panel tab | Navigation: NAV and PLAN, and **Open plot** |
| Plot | The full-screen map (today's `ManeuverMap` with `TrafficLayer`), unchanged in function, its side panel reorganised into the panel grammar of 10.1 |
| Manned by crew | No navigator role today. The helmsman flies the plan (`HELM_FLY_BURNS`) |
| Alerts owned | Impact trajectory; the plan needs more Δv than the tanks hold; Δv margin for the trip home low; burn due in 60 s (advisory); SOI change (advisory) |
| Data | `OrbitInfoProvider`, `OrbitPathProvider`, `ManeuverPlannerProvider`, `TrafficMapProvider`, `Navigation`, `PirateSightings`, `FactionTension` |

### 5.4 Tactical (and Gunner)

**Job:** see, track and fight: contacts, guns, missiles, decoys, point defence, shields.

| Item | Content |
|---|---|
| Cluster (weapons seat) | Fire-control strip: a chip per mount, launchers, decoys, point defence, shields, target with hit chance (8.3) |
| Cluster (gun seat) | Gunner sight (8.4) |
| Symbology | Hostile brackets with class; lead reticle and intercept lines showing the hit chance (`FireArcOverlay`); fire arcs of guns in hand; threat tracks with impact cross (`ThreatOverlay`); point-defence kill marks; picture-in-picture optics on the target |
| MFD pages | SENSORS (radar scope and contact list), WEAPONS (mounts: weapon, ammunition where limited, state, who works it and so how it fires), DEFENSE (shield coverage around the hull, mass, trim, emitters; decoys; point defence) |
| Console panel tab | **Tactical board**: SENSORS' contact table at full size, WEAPONS with hold and free per gun, DEFENSE |
| Manned by crew | Gunners fire at will on hostiles in range (`GunCrews`). Callouts: hostile in range, launching, dart down, target struck, gun out of action |
| Alerts owned | Missile inbound; fired upon; boarders latched (`PirateBoarding`); shield emitter tripped; shield cloud low; launcher empty; gun destroyed or stunned |
| Data | `TargetingProvider` (`CombatTargetingProvider`), `ThreatProvider`, `RadarSystem`, `PlayerGunnery`, `GunCrews`, `ShieldModel` (new provider), `HostileTargeting`, `PirateBoarding` |

The DEFENSE page is the "HUD gauge and coverage overlay" that the combat design lists as not built: a ring of the 96 shield segments around a hull outline, thickness by `ShieldCloudComponent.line`, weak spots where an emitter is out, and the trim side marked.

### 5.5 Engineering

**Job:** keep the ship alive: power, air, heat, fire, hull, tanks, damage control.

| Item | Content |
|---|---|
| Cluster | None: engineering consoles have no seat |
| MFD pages | DAMAGE (the ship plan: hull integrity, breaches, fires, smoke, venting, patches left, repairs under way and by whom; today's `WireframeMinimap`), POWER (reactor output against its rating; each circuit's store and its refill; then only the systems that are not working, each with its cause and what clears it; 9.2), ATMOS (rooms by pressure, oxygen and smoke; tank levels; hours of air) |
| Console panel tab | **Engineering**, with sub-tabs Power, Atmosphere, Damage, Systems (every subsystem: health, state, on/off; replacing today's console window), Tanks & propulsion, Shields (emitter heat and trip state) |
| Manned by crew | Engineers patch leaks, fight fires and repair (`LeakPatches`, the crew task queue). Callouts: fire, breach, patched, out of patches, short circuit, tank burst, system back |
| Alerts owned | Fire; breach venting; out of patches; oxygen tanks dry; systems without power, and no working reactor at all; a system stunned; short circuit; tank burst; hull integrity thresholds |
| Data | `ShipStatusProvider` (`PlayerShipStatus`), `WireframeSyncSystem`, `DamageControl`, `AirWatch`, `PowerRoutingSystem`, `GridHazardSystem`, `IonStun` |

### 5.6 Comms & Cargo

**Job:** the ship's dealings with others: clearance, what stations say, the beacon, the hold, and the station's services while docked.

| Item | Content |
|---|---|
| Cluster | None; clearance shows in the helm's docking mode |
| MFD pages | COMMS (clearance, ports and their state, beacon, standing with the nearest station's faction), CARGO (hold: volume used, each cargo and its value; the latched ship's hold when latched) |
| Console panel tab | Comms: ports and clearance, beacon (with its fee and standing cost), messages (what stations have said: clearance, refusals, warnings such as venting at the berth); Cargo: the manifest |
| Station link | Docked only, anywhere aboard: Market, Yard, Shipyard, Contract board, Hiring, Infirmary as tabs of one panel (10.3) |
| Manned by crew | No comms role today; station messages come through the ticker as COMMS lines from the station's name |
| Alerts owned | Clearance refused; stations will refuse the ship after a hostile act; clearance granted, hold full (advisories) |
| Data | `PlayerDocking`, `DistressBeacon`, `TradeProvider`, `YardProvider`, `ShipyardProvider`, `MissionProvider` (offers), `CrewProvider` (hires), `StationInfirmary` |

### 5.7 Command

**Job:** the captain's own work: the crew, the career, the contracts, the record. Lives on the slate.

| Item | Content |
|---|---|
| MFD pages | CREW (who mans which station, the wounded with their timers, who is outside), CONTRACTS (held: what, time left, where) |
| Slate tabs | Ship, Crew, Medical, EVA, Boarding (latched only), Prisoners (when held), Contracts, Career, Log (10.4) |
| Orders | Suits at battle stations, EVA hold and orders, boarding party, let go and hire, prisoners; new: station assignments (today the crew AI chooses who mans what), battle stations, helm orders while a crew member has the helm; abandon ship |
| Alerts owned | Crew air running out; crew critically wounded; crew adrift or left behind; crew in the engines' exhaust; promotion and payday (advisory); contract done, failed or called off (advisory) |
| Data | `CrewProvider`, `BoardingProvider`, `MissionProvider` (held), `Spacewalks`, `CrewMedical`, `AirWatch`, `NoticeLog`, budget and standing |

---

## 6. Layers and screen zones

### 6.1 Layer stack

From the bottom up. Nothing above layer 5 covers the annunciator except the out-of-fiction layer.

| # | Layer | Contents |
|---|---|---|
| 1 | World | Outside view or deck view |
| 2 | World symbology | Markers, brackets, tracks, corridors, nameplates, PiP optics (section 11) |
| 3 | Seated band | When seated: cluster, MFDs, station board (section 8, 9) |
| 4 | Panel | One at a time: a console panel, the slate, the Station link, or the full-screen plot (section 10) |
| 5 | Overlay | Status strip, annunciator, warp strip, callout ticker, prompt line, suit readout, guide card (section 7) |
| 6 | Out of fiction | Pause menu, settings, controls (F1), crash and career-over screens, mod trust dialog, developer console |

### 6.2 Zones

In UI units on the 1280 x 800 layout grid that `UiScale` already scales. Margins 16. Zones anchor to the screen's edges and centre, so a wider window gives the extra width to the world.

```
x: 0    16            280                                   1000          1264 1280
   +---------------------------------------------------------------------------+  y 0
   |  Z5 guide    |  Z1s status strip       720 x 24        |                   |  16
   |  card        |  Z1  annunciator row 1  720 x 36        |                   |  44
   |  248 wide    |  Z1  annunciator row 2  720 x 36        |                   |  84
   |  (out of     |  Z1w warp strip         720 x 20        |                   |  124
   |  fiction)    |  Z2  callout ticker     720 x 60        |                   |  148..208
   |                                                                           |
   |                          Z0  world and symbology                          |
   |                                                                           |
   |                                                          Z4 panel, right: |
   |                                                          480 wide         |
   |                                                                           |
   |  Z6 suit readout (deck)          Z7 prompt line 720 x 24                  |  568 (seated)
   |----------+-------------------+--------------------------+----------------|  600
   |   16..96 | Z3a MFD 288 x 160 | Z3b cluster 480 x 160    | Z3c MFD 288x160|  (band x 96..1184)
   |          | Z3d station board 1088 x 20                                   |  784
   +---------------------------------------------------------------------------+  800
```

| Zone | Size (units) | Anchor | Notes |
|---|---|---|---|
| Z1s status strip | 720 x 24 | Top centre, y 16 | Condition, state and emergency text (7.7); dark in Green with no state |
| Z1 annunciator | 720 x 36, a second row of 36 at y 84 | Top centre, y 44 | Master lamps 56 x 36 at each end; 6 tiles of 94 x 36 per row with 6 gaps |
| Z1w warp strip | 720 x 20 | Top centre, y 124 | Only in warp |
| Z2 callout ticker | 720 x 60 | Top centre, y 148 | Three lines, left-aligned within the column |
| Z5 guide card | 248 wide, grows down | Top left | Moves under the ticker when a panel is open |
| Z4 panel | 480 wide | Right, 16 in | On deck: full height. Seated: stops 8 above the band. Plot and Yard: full screen |
| Z7 prompt line | 720 x 24 | Bottom centre: y 760 on deck, 8 above the band seated | Response, else learning hints (console prompts sit in the world, by the console) |
| Z6 suit readout | 200 x 80 | Bottom left | Deck only |
| Z3 seated band | 1088 x 184 | Bottom centre, 16 above the edge or flush with it (y 616 to 800): the style's choice | MFDs 288 x 160, cluster 480 x 160, 16 apart; station board 1088 x 20 under them, 4 below |

**Slots hold still.** Every slot in the top column has a fixed place. When something is absent its slot is dark (or, if the style prefers, retracts), but nothing below it moves up to fill the gap and nothing moves down when it appears: the ticker is always at y 148 whether or not a tile is lit. The same holds for the rows of the clusters (8.1). Stable positions are worth more to a player's eye than the space saved by closing gaps, and a dark slot still meets 3.4.

While seated, the camera's centre moves up by half the band's height (92 units), so the ship stays in the middle of the visible world (new, `CameraFollowSystem`). The band's height does not change while seated (folding the MFDs leaves the cluster's 160 standing), so neither does the shift.

While a side panel is open, on deck or seated, the camera's centre also moves left by half the panel's width (240 units), eased as the zoom is (`ZoomEase`, 0.3 s), so the avatar or the ship stays in the middle of the world that is still visible and rule 4 of 6.3 seldom has to move a label.

### 6.3 Collision rules

1. The status strip and the annunciator are never covered by anything but the out-of-fiction layer.
2. With a panel open on the right, the top-centre column (Z1, Z1w, Z2) recentres in the space left of the panel; the guide card drops under the ticker.
3. A full-screen panel (Plot, Yard, Shipyard) hides the band and draws its own annunciator and ticker in Z1 and Z2.
4. World labels never sit under a panel, the band or the top-centre column: a label that would is moved to its contact's edge marker.
5. World labels that overlap each other merge (11.2).
6. One panel at a time. Opening another replaces it; Esc closes it.

---

## 7. The always-on overlay

What can be on screen in every context, including on deck. When nothing is wrong, all of it is empty.

### 7.1 Annunciator and master lamps

**Purpose:** the one place where everything abnormal is listed, briefly.

| Element | Content | Visible |
|---|---|---|
| Tile | Two lines of upper-case ASCII, 8 characters each at most: a tag ("FIRE") and a value ("CARGO 1", "0:37", "x2"). ASCII keeps tags renderable in any style, including segment displays: Δv is written "DELTA-V" | While its condition holds |
| One tile per kind | Several instances of one kind share a tile: the tag, the worst instance and a count ("FIRE" / "CARGO 1+1") | |
| Rows | 6 tiles a row, 2 rows at most; past 12, the last tile reads "+n MORE" and opens the list | |
| Order | Warnings first, then cautions; within each, newest first. Advisories never take a tile | |
| Master WARNING lamp | Left end. Lit and flashing (2 Hz or slower) while any warning is unacknowledged | Only when lit (a style may show both lamps unlit whenever any tile is up) |
| Master CAUTION lamp | Right end, the same for cautions | As above |
| Interaction | **Acknowledge** (16.2) stops the flashing and the repeating alarm; the tiles stay while their conditions hold. Clicking a tile shows its home page (13.2) | |

A condition that clears removes its tile with a short fade and leaves a line in the log.

### 7.2 Callout ticker

**Purpose:** what the crew and the ship say, in words, with who said it.

| Element | Content |
|---|---|
| Line | Station tag, speaker, text: "ENG  Okafor: fire in cargo 1, I'm on it." / "HELM  Ruiz: holding your heading, Captain." / "COMMS  SHIP: clearance granted, Highport port 2." |
| Speaker | The crew member at the owning station, or the one dealing with it; "SHIP" when nobody is (13.5) |
| Stack | 3 lines, newest at the bottom. Each holds 6 s (advisory) or 10 s (caution, warning), scaled by a reading-speed setting, then fades. A repeat within its hold counts ("x3") instead of stacking, as `NoticeLog` does now |
| Placement | Z2, under the annunciator. On deck, a line spoken by a crew member in view also shows as a short bubble over them for its first 3 s (new) |
| Interaction | Clicking a line shows its home page. The full history is the slate's Log |

### 7.3 Prompts and the prompt line

| Element | Content | Where, when |
|---|---|---|
| Console prompt | The console's name and verbs: "HELM  [E] Use  [V] Sit"; at a reactor "REACTOR  [E] Use" | In the world, by the console tile, within `INTERACT_RADIUS` |
| Door prompt | "[F] Force  [B] Cut" | In the world, by the door |
| Response | The answer to the player's own input: "Navigation target: Highport", "Undock from the helm: [V] at the helm, then [U]" | Z7 for 4 s; in a panel's footer when a panel is open |
| Learning hints | At most 3 contextual hints from `HintRegistry`, each retired once the player has used that action 3 times (new; counts kept in settings) | Z7 when no response is showing |

### 7.4 Suit readout

**Purpose:** the avatar's own survival, as their suit's display would show it. Deck only, Z6.

| Field | Shown when |
|---|---|
| Air in the suit (m:ss) | The avatar is suited and their room's air is not breathable |
| NO SUIT, in the warning role | The avatar is not suited and their room's air is not breathable. The deck then marks the nearest free suit (11.5) |
| Room pressure (kPa) and oxygen | The room's air is not breathable or is falling |
| Temperature, or FIRE | The room is on fire or heating |
| The captain's condition ("Light wound", "Critical, 4:10") | The avatar is wounded |

Hidden otherwise.

### 7.5 Warp strip

Only while time warp is above 1x: "WARP 100x  rails   drops out in 4:12 for burn 2". When warp is refused or held back, its reason replaces the drop-out text ("held: crew outside", "held: hull venting"; `WarpNotices`). Z1w.

### 7.6 Guide card

The tutorial layer (First Play objectives and the opening walkthrough) is **out of fiction** and looks it: one card, Z5, with the current objective and its keys. It never shares the callout ticker, as it shares the status column today. Hidden when no guide is running.

### 7.7 Status strip

**Purpose:** the ship's state, in one place that every presence can see. Z1s, the top slot of the top column (y 16, 720 x 24).

| Part | Content | Lit |
|---|---|---|
| Condition | YELLOW or RED (12); RED with "BATTLE STATIONS" when the player called it | In Yellow and Red; dark in Green |
| State | DOCKED or LATCHED | While it holds |
| Emergency text | The cause and the player's options: "SHIP DISABLED: no power.  [P] twice abandons ship.  [B] twice calls a tow" | In Emergency |

In Green, undocked and with no emergency, the whole strip is dark (3.4).

---

## 8. Seated clusters

### 8.1 Helm cluster

Z3b while seated at the helm. Five fixed slots. A slot with nothing to show goes dark in place; the others never move (6.2, "slots hold still"). The slots' arrangement inside the 480 x 160 cluster is the style's choice; their content and priority are not.

| Slot | Content | Lit |
|---|---|---|
| **Reference** | With a locked target, else the nav target: name, range, closing speed, closest approach ("CPA 140 m in 2:10"). With neither: the orbit's Ap and Pe, live while the engine or RCS fires and for 5 s after. When space runs short, keep in this order: name, range, closing, CPA | When there is something to show |
| **Next event** | The next thing due: "Burn 1 in 12:04  210 m/s prograde", "Autopilot: Shackleton Ice, leg 2", "SOI change in 2.4 d (Moon)". Within 60 s of a burn: alignment error and time to ignition. Priority: what, when, how much | When something is due |
| **Velocity** | Speed in the active frame, with the frame's tag: "ORBIT 7.67 km/s", "TARGET 3.9 m/s closing", "PORT 2.4 m/s closing", "DOCKED". Rotation rate when above 0.5°/s. Mode chips (3.5): **control** (MANUAL: the player flies; BURN 1: the flight computer is flying planned burn 1 while the player's hands are off the stick; HELD APPROACH: an order waiting while the player has the helm), **ASSIST** (on, or OFF flagged), and THR and RCS lamps while they fire | Always while seated |
| **Trigger** | The pilot's trigger: GUNS ready or reloading, MSL count and ready or seconds, DECOY ready or seconds (decoys are unlimited, with a 3 s cooldown), and SHIELDS when lowered | With a target locked, or in Red |
| **Δv** | Δv left in the tanks (m/s), with a notch at what the trip home needs (`Navigation`'s return estimate; no notch when it has none) and the planned burns' total as a bracket | Always while seated |

The chronometer is not in the cluster: it sits at the right end of the station board (8.5), the same at every seat.

The velocity frame follows the existing marker rule (`NavMarkerSystem`): the orbit, or the locked target when it has a body in the bubble, plus a new PORT frame during a docking approach and DOCKED once latched.

### 8.2 Docking approach mode

Takes over the Reference and Next event slots when the selected port is armed, or a free port is within `PlayerDocking.READOUT_RANGE` (5 km) of a station that is the nav target. It ends when the ports latch.

| Field | Slot | Content |
|---|---|---|
| Ports | Reference | Own port selected (n of m, size), the target port ("Highport port 2, S") and clearance: not asked, asked, granted, refused (with the reason) |
| Approach | Next event | Range to the port; closing speed against the safe closing speed for that range, as a bar with the limit marked; lateral offset (m) and angle error (degrees) |

Once latched, the Velocity slot's frame reads DOCKED and both slots go dark. Where the ship is berthed, its port and the collar's strain live on the DOCK page (its home, and the left MFD's default while docked); strain over 60% is a caution tile.

World symbology: the approach corridor (`NavOverlay`), marked by safety as now: safe, over the limit, well over.

### 8.3 Tactical cluster

Z3b while seated at the weapons console. Fixed slots, as at the helm.

| Slot | Content |
|---|---|
| **Mount chips** | One chip per gun: position (Nose, Port...), state (ready, reloading, charging: waiting on the circuit's store, no power, stunned, destroyed), and how it fires, which follows who works it (`GunCrews`): AT WILL (a gunner at the mount, by initials, or a crew member at the weapons console), YOURS (in the player's hand), or TRIGGER (nobody works it: it aims at the locked target and fires only on the trigger). Point-defence mounts marked PD, with AUTO or YOURS |
| **Launchers** | Each launcher: missiles left, ready or seconds to ready |
| **Countermeasures** | Decoy: ready, or seconds to ready (decoys are unlimited; `DECOY_COOLDOWN` 3 s) |
| **Shields** | Mode (RAISED, LOWERED, STOWING); cloud mass as a share of capacity; trim (EVEN, BOW, PORT, STERN, STBD) on a small rose |
| **Target** | Locked target: name, class, range, closing, time of flight, hit chance with the guns in hand (`HitChance`), and its state where known (struck, disabled, venting) |

### 8.4 Gunner sight

Seated at a gun. No MFDs and no station board beyond the gun's own tile: a deliberately narrow view.

| Field | Content |
|---|---|
| Sight | The gun's arc and lead reticle, in the world |
| Gun | State, ammunition where limited, time to ready |
| Target | Name, range, time of flight, hit chance |

### 8.5 Station board

Z3d, a strip under the band at a command seat; a row at the top of the slate's Ship tab everywhere else.

```
 1 HELM you    2 NAV you    3 TAC Chen 3/4    4 ENG Okafor (!)    5 COMMS you    6 CMD          Day 3 14:22:05
```

| Tile state | Meaning |
|---|---|
| you | The player is at a console hosting it (lit) |
| name | A crew member mans it (unlit: crewed is normal) |
| empty | Nobody; automated only (unlit) |
| caution, warning | The station has an active alert; the tile lights in that role |
| struck through | Its console is destroyed or unpowered |

Occupancy (you, a name, empty) and alert (caution, warning) are separate parts of a tile and both show: the weapons tile in a fight is the player's and carries the MISSILE warning at once.

Each tile is a soft key: clicking it (or Alt with its number, 16.2) puts that station's first page on the right MFD; again cycles its pages.

The board's right end is the chronometer: day and time (the warp level is in the warp strip). The condition and the ship's state are not on the board: they are in the status strip (7.7), which every presence can see.

Budget: six tiles of 150 (900), the chronometer 188.

---

## 9. Multi-function displays and pages

### 9.1 How the MFDs behave

- Two MFDs, left and right, at each command seat (helm and weapons). None at a gun, none on deck.
- An MFD shows one **page** of one station. Pages of the stations the seat's console hosts are **owned**, with their controls; others are **repeaters**: read-only, with the orders that station takes.
- **Soft keys** along each MFD's bottom edge name the pages of the station on show, plus **pin** and **fold**.
- **Folded**: an MFD can fold down to its soft-key rail (288 x 24), leaving the world visible above it. Pressing a page's soft key unfolds it.
- **Defaults**, when the player sits down, come from the seat and the condition. Once the player picks or folds an MFD, their choice holds until they stand up; a change of condition then leaves that MFD alone.

| Seat | Condition | Left MFD | Right MFD |
|---|---|---|---|
| Helm | Green | NAV, folded | SENSORS, folded |
| Helm | Yellow | SENSORS | NAV, folded |
| Helm | Red | SENSORS | DAMAGE |
| Helm | Docked | DOCK | CARGO, folded |
| Weapons | Green, Yellow | SENSORS | WEAPONS |
| Weapons | Red | SENSORS | DEFENSE |

- **Auto-paging**: when a caution or warning is raised, the right MFD unfolds and turns to the alert's home page, unless the player pinned it; then the left MFD does, unless it is pinned too. When the alert clears, after 10 s, the MFD goes back to what it showed before, folded again if it was. With several alerts, the most severe and then the newest has the MFD; the rest wait on the annunciator.
- A station whose console has no power shows a dead page ("ENG: NO SIGNAL") rather than a stale one.

### 9.2 Page catalogue

| Page | Station | Content | Owned controls | Repeater orders |
|---|---|---|---|---|
| NAV | Navigation | Body, altitude, Ap, Pe, period; next SOI change or impact; small orbit drawing | Open plot | None |
| PLAN | Navigation | Burns (number, time to, Δv), total against Δv left; transfer proposal; autopilot | Accept, discard, warp to burn | Helm: fly burns (1) |
| HELM | Helm | Who has the helm, the order in force, how it is going | Orders 1 to 6, 0 | Orders 1 to 6, 0 |
| DOCK | Helm | Ports with size and state; clearance; latched or docked; strain | Next port, arm and ask clearance, undock | None |
| SENSORS | Tactical | Radar scope (`RadarScope`, ranges 0.5 to 1,000 km) and contact list: class, name, range, closing, CPA, hostile or not, sorted by threat | Lock target, set nav target, range | Lock target, set nav target |
| WEAPONS | Tactical | Mounts: weapon, ammunition where limited, state, who works it and so how it fires (AT WILL, YOURS, TRIGGER) | Take a gun in hand or hand it back; hold fire (new: the game has no hold-fire order today, and a captain wanting a prize undamaged needs one) | Hold fire, all guns (new) |
| DEFENSE | Tactical | Shield ring (96 segments), mass, trim, emitters; decoy readiness; point defence | Raise, lower, trim | None |
| DAMAGE | Engineering | Ship plan: integrity, breaches, fires, smoke, venting, patches, repairs and who is on each (from the crew task queue, new) | Damage-control priority (new) | Damage-control priority |
| POWER | Engineering | Each reactor's output against its rating (it falls with damage); each circuit's store, its charge and time to full. Then only the systems that are not working, each with its cause and what clears it: switched off; no power (conduit cut, or shorted, with the breaker's reclose countdown, `DamageStates.AUTO_RESET`); stunned (countdown); disabled (under 30% health); destroyed. A NEXT line names the soonest that comes back by itself | Switches | None |
| ATMOS | Engineering | Rooms: pressure, oxygen, smoke; tanks; hours of air | None | None |
| COMMS | Comms & Cargo | Clearance, ports, beacon, standing with the local faction | Ask clearance, beacon | None |
| CARGO | Comms & Cargo | Hold: volume, each cargo, value | None | None |
| CREW | Command | Who mans which station; the wounded and their timers; who is outside | Suits at battle stations; station assignments (new) | Same (Command is the captain's) |
| CONTRACTS | Command | Held contracts: what, time left, where, range | Set as nav target | Same |

Locking a target and setting the nav target count as orders to the ship's sensors, so they work from a SENSORS repeater too.

---

## 10. Panels

### 10.1 Panel grammar

- **Side panel**: 480 wide on the right (Z4). On deck it runs the full height; seated, it stops above the band so the instruments stay visible. The world keeps running and stays visible, so a player at the reactor console can watch their crew fight the fire they are routing power around.
- **Full screen**: the Plot, the Yard, the Shipyard and the ship editor. They hide the band and bring their own annunciator and ticker.
- Every panel has the same parts: a **title bar** (station and console, or the slate's owner, or the station berthed at), **tabs**, a **body** of tables and controls, and a **footer** of soft keys with their key caps, with responses shown in it. The footer replaces the per-window "Close [Y]" buttons: Esc always closes.
- Panels open on the tab that matters: an alert's link opens its tab; a shortcut key opens its tab; a console opens on the action tab of the most severe, then newest, live alert owned by a station it hosts, and with none, on its focus tab (Power at the reactor, Atmosphere at life support).

### 10.2 The panels

| Panel | Opened by | Tabs | Replaces |
|---|---|---|---|
| **Helm console panel** | [E] at the helm, seated or standing | Helm, Navigation (with Open plot), Comms, Cargo | Helm console window, docking readout lines, tow prompts |
| **Plot** | [M] at a console hosting Navigation; [M] elsewhere opens it read-only | Map (with traffic), Plan, Targets | `ManeuverMap` side panel, orbit minimap, navigation readout lines |
| **Tactical board** | [E] at the weapons console | Contacts, Weapons, Defense | Status column's target and missile lines |
| **Gun panel** | [E] at a gun | The mount: weapon, state, ammunition, who works it; take it in hand | The gun's console window and "Take the gun" button |
| **Engineering** | [E] at the reactor (Power) or life support (Atmosphere) | Power, Atmosphere, Damage, Systems, Tanks & propulsion, Shields | `ConsoleWindow`'s default layout, ship gauges, ship minimap |
| **Station link** | Docked only, anywhere aboard: [T], [Y], [J], or the berth card | Market, Yard, Shipyard, Contract board, Hiring, Infirmary | `TradeWindow`, `YardWindow`, `ShipyardWindow`, `MissionWindow`'s offers, `CrewWindow`'s hires |
| **Slate** | Anywhere aboard: [I], [J] when not docked, [K], [L], or the CMD tile | Ship, Crew, Medical, EVA, Boarding, Prisoners, Contracts, Career, Log | `CrewWindow`, `BoardingWindow`, `MissionWindow`'s held contracts, notice log, budget line |

### 10.3 Station link

One panel for everything a station offers, with the berth on its title bar: "HIGHPORT  Terran Patrol Authority   Credits 5,000   Hold 12 / 40 m3".

| Tab | Content (from today's windows, reorganised) |
|---|---|
| Market | Commodities: stock, buy, sell, carried; one quantity control (1, 10, all) instead of four buttons a row; the trade's total before confirming |
| Yard | Repairs (priced by the damage, Repair all), tanks (oxygen, nitrogen, propellant, shield media when built), refits per hardpoint (fitted, choice, new, used, salvaged), furnishings; "Rebuild decks" opens the editor |
| Shipyard | Ships for sale with the price after trade-in and why one is locked; a prize in tow with its terms |
| Contract board | Offers: pay, standing, time, where; take, or why not yet. A line at the foot: "Holding 2 of 3 contracts", linking to the slate |
| Hiring | People looking for a berth, wages, sign-on |
| Infirmary | Who is being treated, and how long |

Only the tabs the station offers appear. On docking, a **berth card** goes up for 8 s under the ticker: "Docked at Highport.  [T] Market  [Y] Yard  [J] Board", then folds into a badge on the COMMS tile of the station board. This replaces the permanent "Docked at Highport [T] Trade [Y] Yard [J] Missions" world label.

### 10.4 The slate

The captain's own panel, anywhere aboard. Its title bar carries the captain's rank, the ship's name and the credits.

| Tab | Content |
|---|---|
| Ship | The station board; then a **page viewer** that shows any station's page (the same pages as the MFDs, larger), read-only, with that station's orders. Clicking an annunciator tile on deck opens the slate here on the tile's home page |
| Crew | Roster: name, role, skills, condition, morale, wage, record; the station each mans; station assignment (new); let go (docked) |
| Medical | The wounded: wound, bleed-out timer, who is treating them; medical stores |
| EVA | Who is outside, tethered or adrift, their air; hold everyone in; send out, call in |
| Boarding | When latched: the other ship's state, the boarding party (send, recall), then what can be stripped and carried over (`BoardingWindow`) |
| Prisoners | When held: who, faction, what can be done with them here |
| Contracts | Held: what, time left, where; set as nav target; give up |
| Career | Rank, sponsor, standing per faction, weekly budget and next payday, record; faction tension |
| Log | Every alert and callout, newest first, filterable by station and severity; the last 500 |

---

## 11. World symbology

### 11.1 Layers by seat and condition

| Symbology | Helm | Weapons | Gun |
|---|---|---|---|
| Prograde / retrograde markers | Shown | Hidden | Hidden |
| Burn arrows with countdown | Shown | Hidden | Hidden |
| Nav target bracket | Shown | Shown | Hidden |
| Docking corridor | When approaching | Hidden | Hidden |
| Collision cue (new) | When a contact's closest approach is under 200 m within 60 s | Same | Hidden |
| Hostile brackets with class | Yellow and Red | Shown | Shown |
| Lead reticle, intercept lines | With a target locked | Shown | Own gun |
| Fire arcs | Red, with a target locked | Guns in hand | Own gun |
| Threat tracks, impact cross, PD kills | Shown (safety) | Shown | Shown |
| Contract waypoint | Shown | Hidden | Hidden |
| Picture-in-picture optics | Target and nav target | Target | Target |
| Edge markers | Nav target, target, hostiles, waypoint, missiles | Target, hostiles, missiles | Target, missiles |

**Impact marks** are one per side of the hull that missiles are due to strike (bow, port, stern, starboard), drawn just outside the hull's outline with a count when more than one is due on that side, so the own-ship symbol is never covered.

### 11.2 Contact labels

Today every marked contact carries a name and a range. New rules:

| Contact | Label |
|---|---|
| Locked target, nav target | Name only, always; their numbers are in the cluster (3.8). In the Plot, where there is no cluster: name and range |
| Missile | Its track and impact cross; no countdown on screen (3.8) |
| Hostile | Class and range in Yellow and Red; icon only in Green |
| Contract objective | Name; always |
| Station | Name; range only when the nav target |
| Neutral traffic | Icon only; name and range on hover, or within 2 km |
| Own crew adrift | Name and range, always (`Spacewalks` marker) |

Labels that would overlap within 48 units merge into one cluster label, "Highport +2", which opens on hover or click (as the PiP's "[+] 3 others" does now). Distances under 1 km in metres, above in km with one decimal.

### 11.3 Picture-in-picture optics

The PiP bubbles (`PictureInPicture`) are the ship's long-range optics, so they belong to the seats: on deck there are none. They show the locked target and the nav target. The player's own ship gets one only when the camera is zoomed out so far its hull is under 12 pixels. A bubble keeps clear of the band, panels and the top-centre column (rule 4 of 6.3).

### 11.4 Edge markers

Off-screen items that matter (11.1) get an edge marker with a pointer and range; incoming missiles add the seconds to impact (as `ThreatOverlay` does). Everything else off screen is on the SENSORS page, not on the edge.

### 11.5 Deck symbology

On deck the world is the ship's interior, and almost everything shows itself: fire, smoke, crew at work. Symbology there is kept to:

| Mark | When |
|---|---|
| Console and door prompts | Within reach (7.3) |
| Crew member who needs help: critically wounded, unconscious, out of air | Always, with their timer when they have one |
| The nearest free suit | While the avatar is unsuited in air that is not breathable (7.4) |
| Crew speech bubbles | For their callouts, 3 s |
| The room an alert names | While the alert holds, when the player clicks its tile or callout: a brief outline of the room |

### 11.6 In-world cues on deck (art and lighting)

T0 cues that need art or lighting work, listed so the artists can plan for them:

- A console's screen glows in the alert role while its station has an active caution or warning, so the player can see which console to walk to.
- Door indicator lights: vacuum, fire or smoke on the far side.
- Alarm beacons in rooms with a fire or a breach.
- Interior lighting follows the condition: battle lighting in Red, so a player on deck knows the ship's state at a glance (the lighting thread's call how; the strip, 7.7, says it in words).
- Crew speech bubbles (11.5).

---

## 12. Ship condition

**Condition is the threat level**, derived automatically every frame. It sets the default symbology (11.1), the MFD defaults (9.1) and the contact labels (11.2). Faults aboard (fire, fuel, power) do not change it: they have alerts and auto-paging of their own.

| Condition | When | Effect on the UI |
|---|---|---|
| **Green** | No hostile contact within 50 km | Least: nav symbology, hostiles as icons, MFDs folded |
| **Yellow** | A hostile contact within 50 km, or pirates intercepting | Hostile brackets and class labels; SENSORS unfolds |
| **Red** | Fired upon, a missile inbound, a hostile inside its weapons' range, boarders latched, or **battle stations** called by the player | Fire arcs and hostile brackets at the helm too; MFDs SENSORS and DAMAGE; Tactical board reachable from the TAC tile |

Two states layer over the condition:

| State | When | Effect |
|---|---|---|
| **Docked** | Latched to a station | Combat symbology off (guns hold when docked, as now); MFDs DOCK and CARGO; berth card; Station link. A Red condition while docked restores the threat symbology over this |
| **Latched** | Latched to another ship | The slate's Boarding tab; CARGO shows the other ship's hold |
| **Emergency** | Ship disabled or abandon ship ordered | The status strip's emergency text with the cause and the player's options: "SHIP DISABLED: no power.  [P] twice abandons ship.  [B] twice calls a tow". The condition carries on beside it |

**Battle stations** (new action, 16.2) sets Red until the player stands down or the ship docks. Whether it should also send the crew to their stations is an open question (20.5).

**Where it shows.** The condition and the state are in the status strip (7.7) at the top of the screen, at every presence: on deck, at a console, seated and in the Plot. In Green with no state, the strip is dark. On deck the ship's own lighting echoes the condition (11.6), and a style may also echo it on the band's frame. Its thresholds (50 km, weapons range) are tunables.

---

## 13. Alerts and callouts

### 13.1 Severities

Today's notices have three kinds (`INFO`, `IMPORTANT`, `WARNING`), chosen by how they look. The new levels are chosen by what the player must do:

| Severity | Meaning | Annunciator | Master lamp | Ticker | Auto-page | Sound |
|---|---|---|---|---|---|---|
| **Warning** | Act now, or lose the ship, a life or the career; nobody aboard has it in hand | Tile | Warning | Yes, 10 s | Yes | Alarm, repeating until acknowledged (a setting can limit repeats) |
| **Caution** | Act soon, or something is degraded; or a warning-level problem that the crew have in hand | Tile | Caution | Yes, 10 s | Yes | Chime once |
| **Advisory** | Worth knowing; nothing to do now | No | No | Yes, 6 s, unless its home is already on screen | No | Soft tick (the important ones `UI_NOTICE`, as now) |
| **Response** | The answer to the player's own input | No | No | No: the prompt line or the panel footer | No | The UI click only |

About half of today's notices are **responses** ("Lighting on", "Navigation target: Highport", "Take the helm to undock"). They move out of the alert stream to where the player is looking.

**Escalation** follows 3.7: an alert is raised at the level the situation deserves, then moves between warning and caution as the crew take it on or lose it:

| Alert | Caution while | Warning when |
|---|---|---|
| Fire | Crew are fighting it | Nobody has been on it for 10 s, or it has spread to a second room |
| Breach venting | A patch is under way | No patches left, or nobody on it for 10 s |
| Crew critically wounded | A medic is with them | Nobody is with them |
| Suit air running out | Someone is bringing air, or they are heading for air | Nobody is, and under 2 minutes are left |

(The "crew are on it" data comes from ai-crew's task queue; new in the providers.)

### 13.2 The alert record

| Field | Example |
|---|---|
| `kind` | `fire` (one tile per kind) |
| `instance` | The room, for one alert per room under the kind's tile |
| `severity` | Caution, or the escalation rule that sets it |
| `station` | Engineering |
| `tag`, `value` | "FIRE", "CARGO 1" (ASCII, 8 characters each) |
| `text` | "Fire in cargo 1" |
| `crewText` | "{name}: fire in cargo 1, I'm on it." |
| `homePage` | DAMAGE, focused on the room: what the MFD auto-pages to and what the slate's page viewer shows |
| `actionTab` | Engineering: Damage, the panel tab where it is acted on (for the tile's link when the player is at that console) |
| `active` | A condition: the alert clears itself when it goes false |
| `rearm` | Hysteresis, as `FlightWarnings` re-arms low propellant only past 25% |

Clicking a tile or a callout shows `homePage`: on the MFD when seated, in the slate's page viewer on deck, or the `actionTab` when the player is already at its console.

**Places need names (new).** Rooms have no names in the game today, and an alert that cannot say where is half an alert. A room is named after the most important system in it, in this order: reactor, helm, weapons, life support, engine, shield emitter, turret, airlock, docking port, escape pod, tank, cargo; numbered when a ship has more than one ("CARGO 2"). A room with none of these is named by where it lies in the hull: FWD, MID or AFT, with PORT or STBD ("AFT STBD"). Names fit the tile's 8 characters ("LIFE SUP"). A hull designer or a mod may name rooms itself later. The same name is used on the tile, in the callout, in the log and on the DAMAGE page.

### 13.3 Lifecycle

1. **Raised** when its condition turns true (or posted, for events). Warning and caution: tile, lamp, callout, auto-page, sound.
2. **Acknowledged** by the player: the lamp stops flashing and the alarm stops. The tile stays.
3. **Updated** in place: countdowns tick in the tile; revisions (as `AirWatch` revises its warning) replace the callout instead of adding one; escalation changes its severity and raises the lamp again if it rises.
4. **Cleared** when the condition goes false: the tile fades, the MFD reverts after 10 s, and a record goes to the log ("Fire in cargo 1 out after 1:42").
5. **Re-armed** only past its hysteresis.

Events with no lasting condition (a contract done, a payday, a door forced) are advisories or responses and take no tile.

### 13.4 Catalogue

Today's warnings and notices with their new level. Tags and values are suggestions within the 8-character rule.

| Source | Today | Severity | Tile | Station | Home page |
|---|---|---|---|---|---|
| `FlightWarnings` missile | Red banner, notice | Warning | MISSILE / 2 0:37 | Tactical | DEFENSE |
| `PirateBoarding` latched on | None | Warning | BOARDERS / 1:20 | Tactical | SENSORS |
| Impact trajectory (`OrbitInfoProvider`) | Orbit panel text | Warning | IMPACT / 12:40 | Navigation | NAV |
| Collision risk (new) | None | Caution, Warning under 20 s | COLLIDE / 0:42 | Helm | SENSORS |
| Ship disabled (`ShipDisablement`) | Red status line | Emergency (status strip) | | Command | Slate Ship |
| Abandon ship (`AbandonShip`) | Red status line | Emergency (status strip) | | Command | CREW |
| Fire (`GridHazardSystem`) | Minimap alert list | Escalating | FIRE / CARGO 1 | Engineering | DAMAGE |
| Breach venting (`DamageControl`) | Notice, minimap | Escalating | BREACH / CARGO 2 | Engineering | DAMAGE |
| Out of patches (`DamageControl`) | Notice | Caution | PATCHES / NONE | Engineering | DAMAGE |
| Short circuit, tank burst (`DamageStates`) | Notice | Caution | SHORT / room, TANK / BURST | Engineering | DAMAGE. The callout names what the short cut off: "Short in cargo 1: the starboard turret has no power" |
| Oxygen tanks dry (`AirWatch`) | Warning notice | Warning | O2 TANKS / DRY | Engineering | ATMOS |
| Crew air running out (`AirWatch`) | Revised warning | Escalating | SUIT AIR / 3:50 | Command | CREW |
| Crew critically wounded (`CrewMedical`) | Notice | Escalating | CASUALTY / 1 4:10 | Command | CREW |
| Crew wounded or unconscious | Notice | Caution | WOUNDED / 2 | Command | CREW |
| Low propellant, Δv margin (`FlightWarnings`) | Warning notice | Caution | DELTA-V / LOW | Navigation | PLAN |
| Systems without power: conduit cut, or shorted (`PowerRoutingSystem`) | Power gauge | Caution; Warning when no reactor works at all | POWER / 2 SYS, POWER / NONE | Engineering | POWER |
| System stunned (`IonStun`) | None | Caution | STUN / RCTR 22 | Engineering | POWER |
| Shield emitter tripped (`ShieldModel`) | None | Caution | SHIELD / TRIP | Tactical | DEFENSE |
| Shield cloud under 25% | None | Caution | SHIELD / LOW | Tactical | DEFENSE |
| Fired upon (hostile act at the ship) | Status line "FIRING ON YOU" | Caution | HOSTILE / FIRING | Tactical | SENSORS |
| Pirates intercepting (`NoticeFeeds`) | Warning notice | Caution | PIRATES / INBOUND | Tactical | SENSORS |
| Crew adrift or left behind (`Spacewalks`) | Notice, marker | Warning | ADRIFT / 1 | Command | CREW |
| Crew in the exhaust (`Spacewalks`) | Orange status line | Caution | EXHAUST / EVA | Command | CREW |
| Helm empty with an order, or helm down (`Bridge`) | Grey status line | Caution | HELM / EMPTY, HELM / DOWN | Helm | HELM |
| Burn missed or cut short (`HelmOrders`) | Notice | Caution | BURN / MISSED | Navigation | PLAN |
| Collar strain over 60%, torn loose (`PlayerDocking`) | Notice | Caution | COLLAR / STRAIN | Helm | DOCK |
| Clearance refused | Notice | Caution | DOCKING / REFUSED | Comms | COMMS |
| Stations will refuse the ship (hostile act) | Warning notice | Caution | STANDING / HOSTILE | Comms | COMMS |
| Clearance granted, latched, undocked | Notices | Advisory | | Comms | |
| Burn due in 60 s; SOI change; warp dropped | Notices | Advisory | | Navigation | |
| Contract done, failed, called off | Important notice | Advisory | | Command | CONTRACTS |
| Promotion, payday | Important notice | Advisory | | Command | Slate Career |
| Yard work done, salvage, boarding progress | Notices | Advisory | | Comms, Command | |
| Contract taken, door forcing progress, nav target picked, reload refused, warp step refused | Notices | Response | | | |
| Career over, ship lost | Crash screen | Out of fiction | | | |

### 13.5 Crew callouts

- A callout's speaker is the crew member dealing with the alert, else the one at its station, else "SHIP".
- With a speaker, the line uses `crewText` and says what they are doing about it. Without one, it is the bare fact.
- **Safety never depends on manning.** Every warning and caution is raised whether anyone is at the station or not: the ship's own sensors raise them. Manning changes the words and, through 13.1's escalation, says whether the player must act; it never hides a problem.
- An advisory whose home is already on screen is logged but not called out (the helm cluster already shows "Burn 1 in 0:58" to a player at the helm).
- Callouts are limited to one per station every 3 s; the rest go to the log only.

### 13.6 Responses

A response shows where the player is looking: on the prompt line (Z7), or in the footer of an open panel. 4 s, no sound beyond the UI click. It never goes in the ticker.

---

## 14. Visibility

### 14.1 By presence

● shown; A only while an alert or state calls for it; C on demand; – hidden.

| Widget | Deck | At a console (panel open) | Helm seat | Weapons seat | Gun seat | Plot |
|---|---|---|---|---|---|---|
| Status strip | A | A | A | A | A | A |
| Annunciator, master lamps | A | A | A | A | A | A |
| Callout ticker | A | A | A | A | A | A |
| Prompt line (responses, hints) | A | Footer | A | A | A | Footer |
| Console and door prompts | A | – | – | – | – | – |
| Suit readout | A | A (on deck) | – | – | – | – |
| Guide card | A | A | A | A | A | A |
| Helm cluster | – | – | ● | – | – | – |
| Tactical cluster | – | – | – | ● | – | – |
| Gunner sight | – | – | – | – | ● | – |
| MFDs | – | – | ● (may be folded) | ● | – | – |
| Station board | In the slate | – | ● | ● | Own tile | – |
| Seat symbology (11.1) | – | – | ● | ● | ● | In the map |
| Deck symbology (11.5) | ● | ● (on deck) | – | – | – | – |
| PiP optics | – | – | ● | ● | ● | – |
| Slate | C | Replaces the panel | C | C | C | C |

### 14.2 States that modify it

| State | Changes |
|---|---|
| Docked | Berth card for 8 s; Station link available anywhere aboard; the status strip reads DOCKED; the helm's frame reads DOCKED; MFD defaults DOCK and CARGO; combat symbology off unless Red |
| Latched to a ship | Slate's Boarding tab appears; the CMD tile shows a badge; the status strip reads LATCHED; CARGO shows the other ship's hold |
| Warp above 1x | Warp strip. The cluster's slots go dark except Next event, and the MFDs fold (nothing on them changes fast enough to read); any caution or warning restores them |
| Yellow, Red | Symbology and MFD defaults as in 11.1 and 9.1 |
| Emergency | The status strip's emergency text |
| A side panel open while seated | The band stays; the panel stops above it; the top-centre column recentres left of it |

---

## 15. Context walkthroughs

Wireframes on the 1280 x 800 grid, not to scale. Shapes and styles are the artists'.

### A. On deck, nothing wrong

```
+------------------------------------------------------------------------------------------+
|                                                                                          |
|                                                                                          |
|                                                                                          |
|                               (deck view: the ship's interior)                           |
|                                                                                          |
|                                       [avatar]                                           |
|                                    HELM  [E] Use  [V] Sit                                |
|                                                                                          |
|                                                                                          |
|                                                                                          |
|                                                                                          |
+------------------------------------------------------------------------------------------+
```

Nothing else is on screen. Today the same moment has the flight panel, the orbit, four gauges, the status column and a key line.

### B. On deck, fire and missiles inbound

```
+------------------------------------------------------------------------------------------+
|          RED                                                                             |
|          (W) [MISSILE ] [ BREACH ] [  FIRE  ]                              (C)           |
|              [2  0:37 ] [AFT STBD] [CARGO 1 ]                                            |
|          ENG    Okafor: fire in cargo 1, I'm on it.                                      |
|          TAC    SHIP: two missiles inbound, port side, 37 seconds.                       |
|          ENG    SHIP: aft starboard venting, nobody on it.                               |
|                                                                                          |
|                    (deck view in battle lighting: the reactor console glows red,         |
|                     door lights, alarm beacons in cargo 1 and aft starboard)             |
|                                       [avatar]                                           |
|  SUIT                                                                                    |
|  Air 4:10                                                                                |
|  Room 31 kPa  O2 low                                                                     |
+------------------------------------------------------------------------------------------+
```

The status strip says RED, because missiles are inbound. FIRE is a caution (Okafor is on it); BREACH is a warning (nobody is), so it sorts ahead of FIRE. Clicking BREACH opens the slate on its Ship tab with the DAMAGE page, aft starboard outlined on deck. Walking to the reactor console and pressing [E] opens Engineering on Damage (the most severe live alert it owns), beside the live deck.

### C. Seated at the helm, cruising (Green)

```
+------------------------------------------------------------------------------------------+
|                                                                                          |
|                                                                                          |
|                              o                                                          |
|                                [ship]                      [ Highport ]                 |
|                                     x                                                   |
|                         -> 1  12:04                                                     |
|                                                                                          |
|           +---------------------------------------------+                                |
|           | Highport  1.2 km  closing 3.9  CPA 140 m 2:10|                                |
|           | Burn 1 in 12:04   210 m/s prograde           |                                |
|           | ORBIT 7.67 km/s      MANUAL  ASSIST          |                                |
|           |                 (Trigger: dark)              |                                |
|  [NAV][PLAN][pin][-]  DELTA-V 1,840 |======|=== home 1,210   [SNS][WPN][DEF][pin][-]    |
| 1 HELM you  2 NAV you  3 TAC --  4 ENG Okafor  5 COMMS you  6 CMD          Day 3 14:22:05 |
+------------------------------------------------------------------------------------------+
```

Both MFDs are folded to their rails: in Green there is nothing on them the player needs until they ask. Highport is labelled with its name only; its numbers are in the Reference slot. With no target locked and no threat, the Trigger slot is dark.

### D. Seated at the helm, docking approach

```
+------------------------------------------------------------------------------------------+
|                                                                                          |
|                               (station)                                                  |
|                              ||  ||   <- approach corridor, green while safe            |
|                              ||  ||                                                     |
|                               [ship]                                                    |
|                                                                                          |
+---------------------+---------------------------------------------+----------------------+
| DOCK                | Port 1/1 S armed > Highport 2 (S)  GRANTED  | (folded)             |
| Port 1/1  S  armed  | 312 m  closing |=====|== safe 3.1  +0.8 m -1.5 deg               |
| Highport 2 (S)      | PORT 2.4 m/s closing     MANUAL  ASSIST     |                      |
| Clearance granted   |                 (Trigger: dark)             |                      |
| [DOCK] [HELM] [pin] | DELTA-V 1,780 |=====|==   home 1,210        | [SNS][WPN][DEF][pin] |
+---------------------+---------------------------------------------+----------------------+
| 1 HELM you  2 NAV you  3 TAC --  4 ENG Okafor  5 COMMS you  6 CMD          Day 3 14:41:10 |
+------------------------------------------------------------------------------------------+
```

### E. Seated at the weapons console, in a fight (Red)

```
+------------------------------------------------------------------------------------------+
|          RED                                                                             |
|          (W) [MISSILE ] [HOSTILE ]                                         (C)            |
|              [2  0:37 ] [ FIRING ]                                                       |
|          TAC    Chen: dart down.                                                         |
|          HELM   Ruiz: holding position under fire.                                       |
|                           .  .  .  .  . (threat tracks; one impact mark, "2", port side) |
|                [ship]  ----- lead ---->  <Raider>                                        |
+---------------------+---------------------------------------------+----------------------+
| SENSORS             | NOSE you ready   PORT you 1.2   STBD Chen   | DEFENSE              |
| MSL x2   0:37     H | PD AUTO    MSL 4 ready    DECOY ready       | (shield ring around  |
| Raider   7.7 km   H | SHIELD RAISED 82%   trim BOW                |  the hull, weak to   |
| Dart     2.1 km   H | Raider  7.7 km  closing 2  ToF 1.3 s  64%   |  port)               |
| [SNS] [WPN] [DEF]   |                                             | [SNS] [WPN] [DEF]    |
+---------------------+---------------------------------------------+----------------------+
| 1 HELM Ruiz  2 NAV --  3 TAC you (W)  4 ENG Okafor  5 COMMS --  6 CMD       Day 3 15:02:44 |
+------------------------------------------------------------------------------------------+
```

The TAC tile is both the player's and carrying the MISSILE warning. The Raider is labelled with its name only; its range, closing, time of flight and hit chance are in the Target slot. The missiles' countdown is on the tile and, per missile, in the SENSORS list. A hull hit would turn the right MFD to DAMAGE unless the player pinned DEFENSE; the left would then take it if unpinned.

### F. At the reactor console

```
+------------------------------------------------------------------------------------------+
|  (C) [  FIRE  ] [ POWER  ]                     |  ENGINEERING   Reactor                   |
|      [CARGO 1 ] [ 2 SYS  ]                     |  [Power] [Atmos] [Damage] [Systems] [...] |
|  ENG   Okafor: fire in cargo 1, I'm on it.     |                                          |
|  ENG   SHIP: short in cargo 1: the shield      |  Reactor   2.6 of 3.0 MW (damaged)       |
|        emitter has no power.                   |  Store     62%   full in 0.8 s           |
|                                                |  -----------------------------------------|
|      (deck view stays live:                    |  Not working                             |
|       crew fighting the fire)                  |  Turret, nose    STUNNED         14 s    |
|                                                |  Shield emitter  NO POWER  short, 31 s   |
|              [avatar]                          |  Airlock 2       SWITCHED OFF            |
|                                                |  NEXT  Turret, nose back in 14 s         |
|                                                |  [Enter] Switch   [Esc] Close            |
+------------------------------------------------------------------------------------------+
```

Both alerts are cautions: the crew are on the fire, and the shield emitter's breaker recloses by itself in 31 s. The power page lists only what is not working, with the cause and what clears it; working systems are not listed. The world shifts 240 left so the deck stays centred in what the panel leaves (6.2).

### G. Docked: Station link

```
+------------------------------------------------------------------------------------------+
|                                                |  HIGHPORT  Terran Patrol Authority       |
|                                                |  Credits 5,000    Hold 12 / 40 m3        |
|      (world: the ship at its berth)            |  [Market] [Yard] [Shipyard] [Board] [...]|
|                                                |                                          |
|                                                |  Commodity  Stock  Buy  Sell  Held       |
|                                                |  Ice          420   12    10     0       |
|                                                |  Alloys        80   85    80     4       |
|                                                |  ...                                     |
|                                                |  Qty [1] [10] [All]  Buy 120 cr [Enter]  |
|                                                |                              [Esc] Close |
+------------------------------------------------------------------------------------------+
```

### H. Time warp, seated at the helm

```
+------------------------------------------------------------------------------------------+
|                      WARP 1,000x  rails   drops out in 4:12 for burn 2                   |
|                                                                                          |
|                                  (outside view)                                          |
|                                                                                          |
|           +---------------------------------------------+                                |
|           |                (dark)                       |                                |
|           | Burn 2 in 4:12   410 m/s retrograde          |                                |
|           |                (dark)                       |                                |
|  [NAV][PLAN][pin][-]             (dark)                 [SNS][WPN][DEF][pin][-]          |
| 1 HELM you  2 NAV you  3 TAC --  4 ENG Okafor  5 COMMS you  6 CMD          Day 5 02:10:44 |
+------------------------------------------------------------------------------------------+
```

## 16. Input

### 16.1 Keys kept

All current actions keep their default keys and meanings. The keys that opened windows now open the tab that holds the same thing:

| Key | Opens |
|---|---|
| E | The console's panel |
| M | The Plot (planning at a console hosting Navigation, read-only elsewhere) |
| T, Y | Station link: Market, Yard (docked) |
| J | Station link: Contract board (docked); slate: Contracts (not docked) |
| I | The slate, on its last tab (Crew the first time) |
| K | Slate: Boarding (latched) |
| L | Slate: Log |
| F1 | Controls (out of fiction, unchanged) |

### 16.2 New actions (rebindable; suggested defaults)

| Action | Suggested key | Notes |
|---|---|---|
| Acknowledge alerts | O | O is Lighting today, a render toggle that belongs in Settings and the developer console |
| Station page to the right MFD | Alt+1 to Alt+6 | Stations numbered as on the board; again cycles that station's pages |
| Station page to the left MFD | Shift+Alt+1 to 6 | |
| Pin, fold the right MFD | Alt+0, Alt+minus | Shift for the left |
| Battle stations | 7 | Calls Red; again stands down to automatic |

### 16.3 Gamepad

Keep today's layout. Add: L3 acknowledges. With Back held, a flick of the right stick picks the right MFD's station (up NAV, right SENSORS, down DAMAGE, left CREW). Panels keep `FocusNav`.

### 16.4 Mouse

Everything with a key cap can also be clicked: annunciator tiles, ticker lines, station board tiles, MFD soft keys, contacts in the world and on SENSORS.

---

## 17. Notes for the artists

The artists change how this looks, not what it says or where it is. A style may turn a soft key into a physical switch, a gauge into a segment readout or a dial, or the annunciator into a row of backlit lenses, as long as:

1. **Every semantic role stays distinguishable**, and not by colour alone (also by shape, position, outline, fill or text):

| Role | Used for |
|---|---|
| Nominal | Normal values (rare, since nominal is mostly hidden) |
| Mode | Engaged modes (3.5): assist, frame, control (MANUAL, BURN n, HELD), shields, how each gun fires |
| Advisory | Advisory callouts, informational marks |
| Caution | Caution tiles and lamp |
| Warning | Warning tiles and lamp, threat tracks, impact cross |
| Own ship | Own markers, own fire arcs, own missiles |
| Friendly / Neutral / Hostile | Contacts |
| Navigation | Nav target, burn arrows, waypoint, corridor |
| Target | The locked target |
| Selected / focused | Focus navigation, the active soft key, a pinned MFD |
| Unavailable | Dead screens, struck-through tiles, disabled controls |
| Crewed / Automated / Player | Station board tiles, mount chips |
| Out of fiction | Guide card, controls panel, menus |

2. **Zone budgets hold** (6.2). A style can redraw the band's frame but not grow it past 184 units, nor the annunciator past 720 x 76.
3. **Text stays legible at 1x**: body at least 13 units, annunciator text at least 12, numbers in tabular figures so they don't jitter. Annunciator tags and values are upper-case ASCII, 8 characters at most each, so segment and dot-matrix styles can render them.
4. **Motion is limited**: flashing at 2 Hz or slower, only the master lamps and newly raised tiles; a "reduce motion" setting turns flashing into a steady lit state.
5. **Elements to design**, the full kit:
   - Overlay: annunciator tile (raised, acknowledged, clearing, "+n MORE"); master lamps; status strip (condition, state, emergency text); warp strip; ticker line with station tag and speaker; prompt line; response; suit readout; guide card; berth card.
   - Seats: cluster slots, lit and dark (reference, next event, velocity with mode chips, trigger, Δv bar with home notch and plan bracket); docking approach (closing-speed bar with its limit, alignment); mount chip; launcher chip; shield rose; gunner sight readout.
   - MFDs: frame with title and soft keys; folded rail; pin; dead screen; radar scope; contact list; ship plan; shield ring; small orbit drawing.
   - Station board tile in five states; the board's right end: chronometer, state lamp (DOCKED, LATCHED, EMERG), condition word.
   - Panels: side and full frames with title bar, tabs and footer; table rows; quantity control.
   - World: markers, brackets, lead reticle, intercept lines, fire arcs, threat track, impact cross, PD kill, corridor, nameplate, cluster label, edge marker, PiP ring, collision cue; deck marks for crew who need help, room outline.
   - In-world cues: console glow, door lights, alarm beacons, speech bubble.

---

## 18. What happens to every current element

| Current element | Fate | Where it goes |
|---|---|---|
| FLIGHT: Speed | Changed | Helm cluster Velocity row, in the active frame (orbital speed only in the ORBIT frame) |
| FLIGHT: Heading | Dropped | Replaced by burn alignment error before a burn |
| FLIGHT: Markers frame line | Moved | The Velocity row's frame tag |
| FLIGHT: View EXTERIOR [V] | Dropped | The view is self-evident; the console prompt says "[V] Sit" |
| TARGET block | Moved | Helm Reference row; Tactical Target row; only when locked. "No target [Tab]" dropped |
| ORBIT block (flight panel) | Moved | NAV page; live Ap and Pe in the Reference row while thrusting |
| SHIP STATUS: Propellant | Changed | DELTA-V row (m/s, home notch); tank kg on Engineering's Tanks tab |
| SHIP STATUS: Structure | Moved | DAMAGE page; tiles by threshold |
| SHIP STATUS: Power | Moved | POWER page; POWER tiles |
| SHIP STATUS: Oxygen | Moved | ATMOS page; O2 tiles; suit readout on deck |
| RADAR | Moved | SENSORS page |
| ORBIT minimap and its hints | Moved | NAV page; the hints dropped (learning hints cover them) |
| SHIP wireframe minimap, HULL %, ALL SYSTEMS NOMINAL | Moved | DAMAGE page, auto-paged on damage; "nominal" text dropped |
| Status column: clock and warp | Moved | Chronometer on the station board; warp strip |
| Status column: Target line, "FIRING ON YOU" | Dropped, changed | Duplicate dropped; HOSTILE / FIRING caution |
| Status column: Missile line | Moved | Tactical launcher chips; pilot's trigger in the Reference row |
| Status column: rank and budget | Moved | Slate: Career, and its title bar |
| Status column: notices | Changed | Annunciator, ticker or responses, by severity |
| Status column: docking lines | Moved | Docking approach mode; DOCK page |
| Status column: navigation lines | Moved | Next event row; PLAN page; Plot |
| Status column: contracts | Moved | Slate: Contracts; CONTRACTS page; waypoint marker |
| Status column: First Play objective, opening guide | Moved | Guide card |
| Status column: helm line | Moved | Station board HELM tile; HELM tiles |
| Status column: EVA line | Changed | EXHAUST caution; slate: EVA |
| Status column: SHIP DISABLED, ABANDON SHIP | Changed | The status strip's emergency text |
| MISSILE WARNING banner (outside and on deck) | Changed | MISSILE warning tile with count and countdown, master warning, alarm, threat tracks |
| Bottom key hint line | Changed | Up to 3 learning hints that retire; key caps on soft keys; F1 panel kept |
| "Press [E] for the Helm panel [V] sit and fly" | Changed | Console prompt "HELM [E] Use [V] Sit", in the world |
| "Docked at Highport [T] Trade [Y] Yard [J] Missions" | Changed | Berth card for 8 s, then a badge on the COMMS tile |
| Nameplates on every contact | Changed | Label rules and cluster labels (11.2) |
| Prograde and retrograde markers | Kept | Helm symbology |
| Lead reticle, fire arcs, intercept lines | Kept | Tactical symbology; at the helm with a target locked |
| Threat overlay | Kept | Threat tracks at every seat |
| Burn arrows, docking corridor | Kept | Helm symbology |
| Picture-in-picture | Kept, limited | Target and nav target; own ship only when tiny |
| Orbit map (M) | Kept | The Plot; side panel reorganised |
| Market (T) | Merged | Station link: Market |
| Yard (Y), Ships for sale | Merged | Station link: Yard, Shipyard |
| Mission board (J) | Split | Offers in Station link: Board; held contracts on the slate |
| Boarding window (K) | Moved | Slate: Boarding |
| Crew window (I) | Split | Slate: Crew, Medical, EVA, Prisoners; hires in Station link: Hiring |
| Console window (generic) | Replaced | Engineering: Systems; Gun panel |
| Notice log (L) | Moved | Slate: Log, filterable |
| Controls panel (F1) | Kept | Out of fiction |
| Crash, career over | Kept | Out of fiction |
| Pause menu, settings, main menu, load and save | Kept | Out of fiction; restyled by the artists |
| Lighting toggle (O) | Moved | Settings and the developer console |
| Developer console (~), debug menu (F3), overlays | Kept | Developer layer, not part of this design |
| Mod trust dialog | Kept | Out of fiction |
| Ship editor | Kept | Full screen; should adopt the panel grammar later |

---

## 19. Implementation notes

For whoever builds this; nothing here is binding on the art.

**What stays.** The provider interfaces in `com.ringsofiron.uihud.providers` are the right seam and carry almost all the data this design needs. The world-space systems (`HudRenderSystem`, `NavMarkerSystem`, `ThreatOverlay`, `FireArcOverlay`, `NavOverlay`, `PictureInPicture`) stay and gain a visibility gate. `UiScale`, `HudFonts`, `FocusNav`, `HintRegistry` and `KeyBindings` stay.

**New pieces.**

| Piece | Role |
|---|---|
| `Station` enum and `StationHosts` | The six stations, and which console ids host which, read from data (a `[consoles]` table in modding-data) with the fallback rules of 5.1 |
| `Presence` | Where the avatar is: seated (console), at a console (panel open), deck, away; from `Bridge` and `InteractPromptSystem` |
| `ShipCondition` | Green, Yellow, Red from `ThreatProvider`, hostile contacts and `PirateBoarding`, plus battle stations; Docked and Emergency from docking and disablement |
| `AlertBus` | Grows out of `NoticeLog`: alerts with kind, instance, severity (or an escalation rule), station, tag, value, active condition and re-arm (13.2). `NoticeLog.post(text)` stays and posts an advisory, so no system breaks; systems move over one at a time |
| `StatusStrip`, `Annunciator`, `CalloutTicker`, `PromptLine` | Widgets on `ShipCondition` and `AlertBus` |
| `RoomNames` | Names each room after its most important system, or its place in the hull (13.2) |
| `MfdPage`, `Mfd` | A page per row of 9.2; the two displays with soft keys, pin, fold and auto-paging. `RadarScope`, `WireframeMinimap` and a small `OrbitMap` become pages |
| `HelmCluster`, `TacticalCluster`, `GunnerSight`, `StationBoard` | Sections 8.1 to 8.5, replacing `FlightReadoutSystem`'s panel and `ShipGauges` |
| `Panel` | The shared panel grammar (10.1); today's windows become its tabs |
| `Visibility` | A pure function from (presence, condition, states, open panel) to the visible widgets: section 14 as code, unit-tested row by row |
| `ShieldStatusProvider` | Shield mass, segments, trim and emitters for DEFENSE (the model has the data; the provider is new) |

**New data needed.** The POWER page needs nothing new: reactor rating and output, each circuit's store, and each system's switch, circuit, short with its reclose timer, stun, health and destruction are all in interior-grid today. What is new: collision risk (closest approach of contacts against the ship's path over a short horizon); whether a crew member is on a given fire, breach or casualty (ai-crew task queue, for escalation); per-room damage-control priority; learning-hint use counts (settings); crew assignment per station (ai-crew's `ManConsoleTask`); hours of air at the current crew.

**Order of work.** Each step leaves a playable game:

1. `AlertBus`, annunciator, ticker, prompt line and responses; the status column goes.
2. `Presence` and `Visibility`; on deck, everything but the overlay goes.
3. Helm cluster, MFD frame with NAV, SENSORS and DAMAGE pages from today's widgets, station board; the flight, gauge, radar and minimap panels go.
4. Tactical cluster, gunner sight, WEAPONS and DEFENSE pages; `ShipCondition`.
5. Console panels and the Engineering panel; the console window goes.
6. Station link and slate; the trade, yard, shipyard, mission, boarding and crew windows become tabs.
7. Label rules, cluster labels, deck symbology, in-world cues, escalation.

`tools/playtest/ui-tour.sh` should take a screenshot per walkthrough in section 15, so each step can be checked against this document.

---

## 20. Open questions for Joe

1. **Should manning a station give more information, not just different words?** For example a sensor operator classifying contacts sooner. Recommendation: words and escalation only at first (13.1, 13.5); keep information-by-manning as a later gameplay lever.
2. **Should the plot be readable from anywhere?** Recommendation: yes, read-only, with planning only at the helm. Without it the player walks to the helm to check a contract's destination.
3. **Helm orders from the slate while a crew member has the helm?** Recommendation: yes; it fits "order the crew" and saves a walk when the player is fighting a fire.
4. **Dedicated consoles on bigger hulls** (nav table, comms, damage control, medbay)? Recommendation: yes, as data, after the overhaul; the hosting table already allows it.
5. **Should battle stations also move the crew** (to their stations, into suits where that setting is on)? Recommendation: yes, but as a separate change to ai-crew; the UI only needs the condition.
6. **Lighting toggle (O)** to Settings, freeing O for acknowledge? Recommendation: yes.
7. **Two new captain's orders**: hold fire (the game has none, so a crewed gun keeps firing on a ship the player wants to take whole) and station assignments (today the crew AI chooses who mans what). Recommendation: yes to both; they are small, and both are orders a captain would expect to give.

---

## 21. Changelog

### Round 5 (self-audit: every number checked against the game)

After round 4 showed the power page was invented, every other piece of data in the spec was checked against the code.

| Problem found | Change |
|---|---|
| Decoys were counted ("DECOY 6"), but the game's decoys are unlimited with a 3 s cooldown (`PlayerGunnery.DECOY_COOLDOWN`) | Decoys show ready or seconds to ready (8.1, 8.3, walkthrough E) |
| Guns were AT WILL or HELD, but the game has no hold-fire order: how a gun fires follows who works it (`GunCrews`) | Mount chips show AT WILL, YOURS or TRIGGER; hold fire is proposed as a new order (8.3, 9.2, 20.7) |
| Gun heat doesn't exist; ammunition is limited only on some mounts (`WeaponMountComponent.ammo`, -1 for none) | "Ammunition where limited", no heat (8.4, 9.2) |
| Station assignments were listed as an existing order; the crew AI chooses today | Marked new and put to Joe (5.7, 9.2, 10.4, 20.7) |
| A "hails log" implied a hailing system the game doesn't have | Comms keeps a log of what stations have said (5.6) |
| "Repairs and who" needs the crew task queue | Marked as the same new data the escalation rules need (9.2) |

Everything else in the spec maps to data the game has today, or is marked new in section 19.

### Round 4 (Nasa-punk artist's notes on Ship Stations x Nasa-punk)

| Problem found | Change |
|---|---|
| The power data was invented: the game has no per-system demand and nothing browns out; a system is powered or not, the store holds 2 s of output for burst loads, and there is no galley | The POWER page shows each reactor's output against its rating, each store and its refill, then only the systems not working with cause and what clears it (switched off, no power from a cut conduit or a short with its breaker countdown, stunned, disabled, destroyed) and a NEXT line. POWER / LOW dropped; one POWER kind that is a caution with "n SYS" and a warning at NONE; mount chips gain "charging" (3.9, 5.5, 9.2, 13.4, walkthrough F) |
| Rooms have no names in the game, so FIRE / GALLEY could not be built | Places are named after a room's most important system, or its place in the hull (13.2); examples now use CARGO 1 and AFT STBD |
| The condition was only on the station board, which is hidden on deck and at a console, so a player at the reactor with missiles inbound saw nothing that said RED | A status strip at the top of the screen, at every presence, holds the condition, the docked or latched state and the emergency text; the board keeps the chronometer; battle lighting on deck in Red (7.7, 8.5, 11.6, 12) |
| Board tile states read as exclusive, but a tile can be the player's and alerting at once | Occupancy and alert are separate parts of a tile and both show (8.5) |
| A side panel on deck hid the right third of the world, avatar included | The camera moves 240 left while a side panel is open (6.2) |
| The suit readout assumed a suit | NO SUIT in the warning role, and the deck marks the nearest free suit (7.4, 11.5) |
| Which tab a console opens with several live alerts was ambiguous | The action tab of the most severe, then newest, live alert owned by a station it hosts (10.1) |
| A short's callout didn't say what it cut off | It names the systems left without power (13.4) |
| "Hit-chance colour" and "coloured by safety" fixed colours that every style spends on severity | Reworded as "showing the hit chance" and "marked by safety" (5.4, 8.2) |
| Two missiles put two impact crosses over the own-ship symbol | One impact mark per side of the hull, with a count, outside the outline (11.1) |

### Round 3 (Cassette artist's notes on Ship Stations x Cassette)

| Problem found | Change |
|---|---|
| Seated, the nav target's and target's world labels repeated the cluster's numbers; a missile countdown showed three times | The world identifies, the instruments measure: seated, the reference object is labelled by name only; on-screen missiles carry no countdown (3.8, 11.2) |
| The Reference row had three alternative contents and room for one | The pilot's trigger has its own slot; Reference has a priority order for truncation; the chronometer moved to the station board to keep five slots (8.1) |
| Rows that fold away make the layout jump | Fixed slots that go dark in place, in the clusters and the top column alike (6.2, 8.1) |
| No label for "no helm order" | A control mode chip: MANUAL, BURN n, or HELD with the waiting order (8.1) |
| The berth was shown in the cluster and on the DOCK page | Docking mode ends at latch; the frame reads DOCKED; the DOCK page is the berth's home (8.2) |
| No place on the board for the layered states | One state lamp beside the condition: EMERG, else LATCHED, else DOCKED (8.5) |
| Unclear whether the ticker moves when the annunciator is empty | It never moves: every top-column slot has a fixed place (6.2) |
| Folding both MFDs was said to shrink the band, but the cluster keeps it 160 tall | Band height and camera shift are constant while seated (6.2) |
| Hardware styles don't float | The band may sit flush with the bottom edge or 16 above it (6.2) |

### Round 2 (self-critique)

| Problem found in round 1 | Change |
|---|---|
| Annunciator tiles could not fit their text: 8 tiles in 640 units left 80 a tile for "BREACH  CARGO 2" | Two-line tiles of 8 ASCII characters a line, 6 a row in 720 units, one tile per kind with a count (7.1) |
| The ticker at top left collided with the annunciator and the guide card | Ticker under the annunciator in one top-centre column; guide card alone at top left (6.2) |
| A side panel covered the right MFD and part of the cluster while seated | Seated, side panels stop above the band (6.3, 10.1) |
| Two full MFDs of nominal data in quiet flight broke the dark-ship rule | MFDs fold to a soft-key rail; folded by default in Green; the player's choice holds while seated (9.1) |
| Any caution turned the condition Yellow, so a fuel caution swapped the displays to sensors | Condition is the threat level only; faults have their own alerts and auto-paging; Docked and Emergency are states over it (12) |
| A crew-handled fire and an unattended one looked the same | Severity says whether anyone is dealing with it, with escalation rules (3.7, 13.1) |
| "Standing at" a console and "seated" overlapped, and E at the helm had no defined panel | Two verbs: [E] Use opens the console panel with a tab per hosted station; [V] Sit gives the seat (4, 5.1, 10.2) |
| The pilot can fire guns and missiles from the helm today, which round 1 left out | The pilot's trigger: weapons readiness in the helm's Reference row; lead reticle and intercept lines at the helm with a target locked (8.1, 11.1) |
| Pirates latching on to rob a beaten ship had no alert | BOARDERS warning; Red (12, 13.4) |
| The slate's read-only views were undefined | Slate Ship tab is a page viewer for every station page (10.4) |
| Alert homes mixed MFD pages and panel tabs | Each alert has a home page and an action tab (13.2) |
| Responses had no single place | The prompt line (Z7), or the panel footer (7.3, 13.6) |
| Advisories repeated what the cluster already showed | Advisories whose home is on screen are logged, not called out (13.5) |
| The visibility matrix mixed places and states | Split into presence and modifying states (14) |
| Modes such as rotation assist were at risk of being hidden as "nominal" | Modes are always shown (3.5) |
| Speed in the orbit frame told a manual-burning pilot little | Live Ap and Pe in the Reference row while thrusting (8.1) |
| Docked had no rules for a fight at the berth | Red over Docked restores threat symbology (12) |
| The deck had no symbology rules | Deck symbology (11.5) |
| "Condition key" was vague and tied to crew behaviour | Battle stations action; crew behaviour left as an open question (12, 20.5) |

# Ship Stations: a diegetic functional design for the UI

Functional design 3 of 3 for the UI overhaul. Approach: **the UI is the ship's own instruments.** Information is organised by crew station (helm, navigation, tactical, engineering, comms and cargo, command), the screen stays dark while the ship is well, and information comes forward when it matters.

This document is style-agnostic. It says which widgets exist, what data each carries, where it sits and when it is visible. It says nothing about colour, typeface or shape beyond semantic roles; the artist threads decide those (see [Notes for the artists](#17-notes-for-the-artists)).

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
10. [Station panels](#10-station-panels)
11. [World symbology](#11-world-symbology)
12. [Ship condition](#12-ship-condition)
13. [Alerts and callouts](#13-alerts-and-callouts)
14. [Visibility matrix](#14-visibility-matrix)
15. [Context walkthroughs](#15-context-walkthroughs)
16. [Input](#16-input)
17. [Notes for the artists](#17-notes-for-the-artists)
18. [What happens to every current element](#18-what-happens-to-every-current-element)
19. [Implementation notes](#19-implementation-notes)
20. [Open questions for Joe](#20-open-questions-for-joe)

---

## 1. Summary

Rings of Iron already has the bones of a diegetic interface. The player's avatar walks the deck, uses consoles that are real tiles in the ship (`ShipConsoles`: helm, weapons, gun, life support, reactor), and must sit at a console to see outside (GDD §6). Crew man those consoles (`Bridge`, `GunCrews`). The HUD ignores all of this: every panel is on screen at every console, on deck and docked alike, as if the player were a disembodied debugger.

This design makes the existing physical model the organising principle of the UI:

- **Six stations** are the jobs aboard: **Helm, Navigation, Tactical, Engineering, Comms & Cargo, Command.** Consoles are the hardware that hosts them. A small ship's helm console hosts three stations; a big ship may have a console for each.
- **Where the avatar is decides what is on screen.** Seated at the helm you see the helm's instruments. At the weapons console you see fire control. Walking the deck you see the deck, and almost nothing else.
- **Read anywhere, act at the station.** Any station's status can be looked at from anywhere: on a repeater display at a command seat, or on the captain's slate. Controls that fly, fight or switch systems need presence at the console, or an order to the crew member manning it.
- **A dark ship is a healthy ship.** When nothing needs attention, nothing is lit: no "ALL SYSTEMS NOMINAL", no empty "No target" rows, no 100% bars. Information that is normal is available on demand, not on display.
- **Alerts pull information forward.** A fire lights an annunciator tile, the engineer calls it out by name, the engineering console glows on deck, and a seated player's right-hand display turns to the damage page by itself. When the fire is out, the display turns back.
- **Ship condition (Green, Yellow, Red, Docked) sets the defaults.** Each condition decides which symbology is drawn on the outside view and which pages the displays start on.

The result is a screen that holds, in normal flight at the helm, one instrument cluster along the bottom edge, two small displays either side of it, a station board under them, and the world. On deck it holds the world and a prompt.

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
- **Read**: any station's status pages can be viewed from any command seat (on a repeater display) or from the captain's slate, wherever the avatar is.
- **Act**: controls that move the ship, fire weapons or switch systems need the avatar at a console that hosts the station, or an order to the crew member manning it.
- **Exception, docked services**: while docked, the station's own staff do the work, so the market, yard, shipyard, contract board, hiring and infirmary can be used from anywhere aboard.

### 3.4 Dark ship: nominal is silent
Borrowed from the "dark cockpit" philosophy of airliner flight decks: when every system is normal, no light is lit. A widget whose content is nominal, empty or unchanged is hidden or collapsed. The player learns that anything on screen means something.

### 3.5 Alerts pull information forward
Information moves up the disclosure tiers (below) when its state becomes abnormal and back down when it recovers. The player should never have to go looking for something that is going wrong; they should have to go looking for things that are going right.

Disclosure tiers, from least to most intrusive:

| Tier | Where | Example |
|---|---|---|
| T0 Ambient | In the world and in sound only | A console's screen glows amber on deck; a door's light shows vacuum beyond; the alarm sounds |
| T1 Notice | Annunciator tile, callout line | "FIRE GALLEY" tile; "ENG  Okafor: fire in the galley, I'm on it" |
| T2 Instrument | Seated cluster, MFD page | The damage page on the right display |
| T3 Panel | A station panel | The engineering panel at the reactor console |
| T4 Record | The ship's log | The log line, kept after it ends |

### 3.6 One fact, one home
Each fact has one home widget. Other widgets may link to it or summarise it (an annunciator tile summarises a fire; the damage page is its home), but the same full readout never appears twice on one screen.

### 3.7 Decision-ready numbers
Show the number the player decides with, not the one the simulation stores:

| Instead of | Show |
|---|---|
| Propellant 2,000 kg | Δv 1,840 m/s, and the margin over what the trip home needs |
| Speed 7,672.5 m/s (orbital) | Speed in the frame that matters now: relative to the target, the docking port or the orbit, with the frame named |
| Heading 0.0 deg | Nothing, normally. Before a burn: the alignment error to the burn |
| Distance to a contact | Range, closing speed, and closest approach (how near, how soon) |
| Missile reload 3 s | Ready, or seconds to ready, on the launcher's own chip |
| Power 100% 3.0 MW | Generation against demand; only shown when demand is near or over |
| Oxygen 100% | Hours of air at the current crew; only shown when it falls under a day |

Precision fits the decision: one decimal below 100, whole numbers below 10,000, then km or km/s with two significant decimals.

### 3.8 The world is the main display
In a top-down game the outside view is the best display there is. Symbology drawn into the world (markers, brackets, tracks, corridors) is preferred over panels that describe the world in words. Panels hold what the world cannot show: numbers, lists and orders.

---

## 4. Access: where the player is decides what they can do

Five ways the avatar can be in relation to a station:

| Presence | How | What they get |
|---|---|---|
| **Seated** | V at a helm, weapons or gun console (as now) | The outside view, that console's **cluster** (section 8), its **symbology** layer, two **MFDs** (helm and weapons only), the **station board**, and full control of the hosted stations |
| **Standing at** | E at any console (as now) | That console's **station panel** (section 10), as a side panel over the deck view; full control of the hosted stations |
| **Repeater** | An MFD at a command seat (helm or weapons) showing another station's page | That station's status, read-only, plus **orders** to whoever mans it |
| **Slate** | Anywhere aboard, the captain's personal slate | Command station in full (crew, contracts, career, log); read-only summaries of every other station; docked services |
| **Away** | Avatar off the ship (boarding party, station deck) | Slate only. Station repeaters are gone until back aboard |

Control rules, by station (current keys in brackets):

| Station | Read | Act |
|---|---|---|
| Helm | Anywhere | Seated at a console hosting Helm: thrust, turn, strafe, assist, warp, undock, ports. Helm orders (1 to 6, 0) from any command seat, and from the slate when a crew member is at the helm (new) |
| Navigation | Anywhere (read-only plot) | Seated at a console hosting Navigation: plan, move and delete burns, plan to target, accept, warp to burn |
| Tactical | Anywhere | Seated at the weapons console (all guns) or a gun (that gun). Shields raise and trim at helm or weapons, as now |
| Engineering | Anywhere | Standing at the reactor or life support console: switches, priorities. Damage-control priorities from the slate (new) |
| Comms & Cargo | Anywhere | Clearance and ports seated at Helm; distress beacon at Helm or from the slate; docked services from anywhere aboard |
| Command | Anywhere (it is the slate) | Anywhere aboard: crew orders, EVA, boarding, prisoners, contracts, abandon ship |

When the player presses a key for something they cannot do where they are, they get a **response** (section 13.6) next to the prompt, naming where to go: "Undock from the helm: V at the helm, then U." This replaces the current notices for the same purpose.

---

## 5. The six stations

### 5.1 Hosting: which console hosts which station

Default hosting, as data (see [19](#19-implementation-notes)) so hulls and mods can change it:

| Console (`SubsystemType`) | Seat | Hosts | Notes |
|---|---|---|---|
| `HELM` | Yes | **Helm, Navigation, Comms & Cargo** | The command seat. Comms moves off it on hulls with a comms console |
| `WEAPONS` | Yes | **Tactical** | The second command seat: helm orders and shields work here, as now |
| `TURRET_MOUNT` (gun) | Yes | **Tactical (Gunner)**, that gun only | No MFDs: a gunner sees their gun |
| `REACTOR` | No | **Engineering** (opens on the Power page) | |
| `LIFE_SUPPORT` | No | **Engineering** (opens on the Atmosphere page) | |
| Captain's slate | n/a | **Command**, plus read-only repeaters of the rest | Not a tile; carried by the avatar |
| Future: nav table, comms, damage control, medbay | Optional | Navigation, Comms & Cargo, Engineering (Damage), Command (Medical) | For larger hulls; each takes its station off the helm |

Fallback rules, so no hull loses a function:
1. If no console hosts a station, the **helm** hosts it (Engineering included: its panel opens from the helm as a station panel).
2. If the hosting console is **destroyed**, the station falls back to the helm for reading, and acting needs the console repaired, except Helm itself, which falls back to nothing (the ship drifts, as `Bridge` already rules).
3. If the hosting console is **unpowered or stunned**, it shows a dead screen: the station's pages read "NO POWER" on every repeater, and its controls do nothing. Annunciated as a caution.

### 5.2 Helm

**Job:** fly the ship, moment to moment: attitude, thrust, docking, time warp, and the helmsman's orders.

| Item | Content |
|---|---|
| Cluster | Primary flight instrument, reference block, Δv gauge, next-event strip, chronometer (section 8.1). Docking approach mode (8.2) |
| Symbology | Prograde and retrograde markers in the active frame; planned burn arrows with countdown; navigation target bracket; docking corridor; collision cue (new); threat tracks always (safety) |
| MFD pages it provides | HELM (state of the helm and its order), DOCK (port list, clearance, strain) |
| Panel | None of its own: the helm is a seat |
| Controls | `THRUST`, `REVERSE`, `TURN_*`, `STRAFE_*`, `ROTATION_ASSIST`, `WARP_UP`/`DOWN`, `WARP_TO_BURN`, `NEXT_PORT`, `ARM_PORT`, `UNDOCK`, `VIEW` (stand), helm orders `HELM_*`, `AUTOPILOT` |
| Manned by crew | The helmsman holds the heading or flies an order (`HelmOrders`). Callouts: order taken, order finished, order dropped under fire, burn started and done, "nobody at the helm, the ship drifts" |
| Alerts owned | Helm unmanned with an order waiting; helm unpowered; burn missed or cut short; collision risk (new); docking: closing too fast, clearance refused, collar strain, torn loose |
| Data | `FlightReadoutSystem`, `NavMarkerSystem`, `Navigation`, `HelmOrders`, `PlayerDocking`, `TimeWarpController` |

### 5.3 Navigation

**Job:** where the ship is going over hours and days: orbits, burns, transfers, traffic.

| Item | Content |
|---|---|
| Cluster | None of its own; it feeds the helm cluster's next-event strip and Δv margin |
| Symbology | Burn arrows (shared with Helm); contract waypoint marker; nav target bracket |
| MFD pages | NAV (orbit: body, altitude, Ap, Pe, period, next SOI change or impact), PLAN (burns: number, time to, Δv, total against tanks; transfer proposal; autopilot state) |
| Panel | **Plot**: the full-screen map (today's `ManeuverMap` with `TrafficLayer`), unchanged in function; its side panel reorganised into the page grammar of section 10 |
| Controls | `MAP`, `PLAN_BURN`, `BURN_*`, `NAV_TARGET`, `PLAN_TO_TARGET`, `ACCEPT_PLAN`, `DISCARD_PLAN`, `WARP_TO_BURN`, map mouse planning |
| Manned by crew | No navigator role today. The helmsman flies the plan (`HELM_FLY_BURNS`) |
| Alerts owned | Impact trajectory (warning); plan needs more Δv than the tanks hold; Δv margin for the trip home low; burn due in 60 s (advisory); entering or leaving a sphere of influence (advisory); warp dropped (response) |
| Data | `OrbitInfoProvider`, `OrbitPathProvider`, `ManeuverPlannerProvider`, `TrafficMapProvider`, `Navigation`, `PirateSightings`, `FactionTension` (traffic tension readout) |

### 5.4 Tactical (and Gunner)

**Job:** see, track and fight: contacts, guns, missiles, decoys, point defence, shields.

| Item | Content |
|---|---|
| Cluster (weapons console) | Fire-control strip: a chip per mount, launcher chips, decoys, point defence mode, shield state and trim, target block with hit chance (section 8.3) |
| Cluster (gun) | Gunner sight: that gun only (section 8.4) |
| Symbology | Hostile brackets with class; lead reticle and intercept lines in the gun's hit-chance colour (`FireArcOverlay`); fire arcs of guns in hand; threat tracks with impact cross (`ThreatOverlay`); point-defence kill marks; picture-in-picture optics on the target |
| MFD pages | SENSORS (radar scope plus contact list), WEAPONS (mount list: weapon, ammunition, ready, who works it, firing at will or held), DEFENSE (shield coverage around the hull, mass, trim; decoys; point defence) |
| Panel | **Tactical board** (standing at the weapons console, or from the helm in Red): contact table and weapon assignments |
| Controls | `FIRE`, `MISSILE`, `DECOY`, `NEXT_TARGET`, `SHIELDS`, `SHIELD_TRIM`; click a contact to lock |
| Manned by crew | Gunners fire at will on hostiles in range (`GunCrews`). Callouts: hostile in range, launching, dart down, target struck, gun out of action |
| Alerts owned | Missile inbound (warning, with the soonest impact); fired upon (caution, raises condition Red); shield emitter tripped or overheating; shield cloud below 25%; launcher empty; gun destroyed or stunned |
| Data | `TargetingProvider` (`CombatTargetingProvider`), `ThreatProvider`, `RadarSystem`, `PlayerGunnery`, `GunCrews`, `ShieldModel` (new provider), `HostileTargeting` |

The DEFENSE page is the "HUD gauge and coverage overlay" that the combat design lists as not built: a ring of the 96 shield segments around a hull outline, thickness by `ShieldCloudComponent.line`, weak spots where an emitter is out, and the trim side marked.

### 5.5 Engineering

**Job:** keep the ship alive: power, air, heat, fire, hull, tanks, damage control.

| Item | Content |
|---|---|
| Cluster | None: engineering consoles are stood at, not sat at |
| MFD pages | DAMAGE (the ship plan: hull integrity, breaches, fires, smoke, venting, patches left, repairs under way and by whom; today's `WireframeMinimap`), POWER (generation against demand, store charge, systems off, stunned with countdown, browned out), ATMOS (rooms by pressure, oxygen and smoke; tank levels; hours of air) |
| Panel | **Engineering** at the reactor console (Power first) or life support (Atmosphere first): adds Systems (every subsystem with health, state and an on/off switch, replacing today's console window), Tanks and Propulsion (engines, feed, tanks), Shields (emitter heat and trip state) |
| Controls | Switch systems on and off (as the console window does now); damage-control priority per room (new) |
| Manned by crew | Engineers patch leaks, fight fires and repair (`LeakPatches`, crew task queue). Callouts: fire, breach, patched, out of patches, short circuit, tank burst, system back |
| Alerts owned | Fire; breach venting; out of patches; oxygen tanks dry; crew air in suits running out (`AirWatch`); no power or brownout; reactor stunned; short circuit; tank burst; hull integrity below thresholds |
| Data | `ShipStatusProvider` (`PlayerShipStatus`), `WireframeSyncSystem`, `DamageControl`, `AirWatch`, `PowerRoutingSystem`, `GridHazardSystem`, `IonStun` |

### 5.6 Comms & Cargo

**Job:** the ship's dealings with others: clearance, hails, the beacon, the hold, and the station's services while docked.

| Item | Content |
|---|---|
| Cluster | None; docking clearance shows in the helm's docking mode |
| MFD pages | COMMS (clearance state, ports and their status, beacon state, standing with the nearest station's faction), CARGO (hold: volume used, each cargo and its value; latched ship's hold when latched) |
| Panel | **Comms** (at the helm, or slate): ports, clearance, beacon with fee and standing cost, hails log. **Station link** (docked only, anywhere aboard): Market, Yard, Shipyard, Contract board, Hiring, Infirmary as tabs of one panel (section 10.3) |
| Controls | `ARM_PORT` (ask clearance), `NEXT_PORT`, `CUT` twice (tow) from the slate or helm, `TRADE`, `YARD`, `MISSIONS` |
| Manned by crew | No comms role today; a crew member at the helm answers hails in callouts |
| Alerts owned | Clearance given (advisory) or refused (caution); stations will refuse you after a hostile act (caution); tow called (response); hold full (advisory) |
| Data | `PlayerDocking`, `DistressBeacon`, `TradeProvider`, `YardProvider`, `ShipyardProvider`, `MissionProvider` (offers), `CrewProvider` (hires), `StationInfirmary` |

### 5.7 Command

**Job:** the captain's own work: the crew, the career, the contracts, the record. Lives on the slate.

| Item | Content |
|---|---|
| MFD pages | CREW (station board in detail: who mans what, wounded, outside), CONTRACTS (held: what, time left, where) |
| Panel | **Slate**, with tabs: Crew, Medical, EVA, Boarding (latched only), Prisoners (when held), Contracts, Career, Log; plus Ship (read-only summary of every station) |
| Controls | `CREW`, `MISSIONS` (held contracts when not docked), `BOARD`, `LOG`, `ABANDON` (twice), ship condition (new), helm orders when a helmsman is at the helm (new), suits at battle stations, EVA hold |
| Alerts owned | Crew critically wounded with bleed-out time; crew adrift or left behind; crew in the engines' exhaust; promotion and payday (advisory); contract done, failed or called off; career over |
| Data | `CrewProvider`, `BoardingProvider`, `MissionProvider` (held), `Spacewalks`, `CrewMedical`, `NoticeLog`, budget and standing |

---

## 6. Layers and screen zones

### 6.1 Layer stack

From the bottom up. Higher layers never hide the annunciator.

| # | Layer | Contents |
|---|---|---|
| 1 | World | Outside view or deck view |
| 2 | World symbology | Markers, brackets, tracks, corridors, nameplates, PiP optics (section 11) |
| 3 | Seated cluster | Bottom band when seated: cluster, MFDs, station board (section 8) |
| 4 | Station panel | One at a time: side panel or full screen (section 10) |
| 5 | Overlay | Annunciator, callout ticker, prompt, suit readout, warp strip, guide card (section 7) |
| 6 | Out of fiction | Pause menu, settings, controls (F1), crash and career-over screens, mod trust dialog, developer console |

### 6.2 Zones

In UI units on the 1280 x 800 layout grid that `UiScale` already scales. Margins 16 units.

```
 0                                   1280
 +----------------------------------------+ 0
 | Z5 guide   |  Z1 annunciator  | Z1 mst |      Z1  annunciator, top centre, 640 x 40 (two rows at most)
 | card       |  Z1b warp strip  |        |      Z1b warp strip under it, only in warp
 |------------                            |
 | Z2 callouts (3 lines)                  |      Z2  callout ticker, top left, 520 x 66
 |                                        |
 |                                        |
 |              Z0 world + symbology      |  Z4 station panel, right side,
 |                                        |  up to 512 wide, full height
 |                                        |  (full screen for Plot and Yard)
 |                                        |
 |  Z6 suit                               |      Z6  suit readout, left, above the band (deck only)
 |----------------------------------------| 600
 | Z3a MFD L  |  Z3b cluster    | Z3c MFD R|      Z3  seated band, 184 tall:
 | 288 x 160  |  480 x 160      | 288 x 160|          MFDs 288 x 160, cluster 480 x 160
 |  Z3d station board, 1248 x 24          |          station board 24 under them
 +----------------------------------------+ 800
```

The ship sits in the middle of the view, and the band takes the bottom 23% only while seated. The camera's centre shifts up by half the band's height while seated, so the ship stays in the middle of what is visible (new, `CameraFollowSystem`).

### 6.3 Collision rules

1. The annunciator is never covered.
2. A station panel on the right pushes the callout ticker and annunciator to the centre of what is left, never under the panel.
3. The seated band yields to a full-screen panel (Plot, Yard, Shipyard), which brings its own copy of the annunciator.
4. Nameplates never overlap a panel or the band: a label that would is moved to the edge marker for its contact.
5. World labels that overlap each other merge (section 11.2).
6. Only one station panel at a time. Opening another replaces it; Esc closes it.

---

## 7. The always-on overlay

What is on screen in every context, including on deck. When nothing is wrong, all of it is empty.

### 7.1 Annunciator and master lamps

**Purpose:** the one place where everything abnormal is listed, briefly.

| Element | Content | Visible |
|---|---|---|
| Tiles | Short tag (10 characters or fewer) and a value: "MSL 2  0:37", "FIRE  GALLEY", "BREACH  CARGO 2", "O2 TANKS DRY", "Δv MARGIN", "SHLD TRIP  PORT" | While the condition holds. Up to 8 in a row; a second row if more; past 16, "+3" |
| Order | Warnings first, then cautions; within each, newest first. Advisories never appear here | |
| Master WARNING lamp | Left end. Lit and flashing (at most 2 Hz) while any unacknowledged warning exists | Only when lit |
| Master CAUTION lamp | Right end, same for cautions | Only when lit |
| Interaction | Acknowledge (new key, section 16) stops the flashing and the repeating alarm; the tile stays while the condition holds. Clicking a tile opens its home page (slate on deck, MFD when seated, the panel when standing at its console) | |

A condition that clears removes its tile at once, with a short fade, and leaves a T4 log line.

### 7.2 Callout ticker

**Purpose:** what the crew and the ship say, in words, with who said it.

| Element | Content |
|---|---|
| Line | Station tag, speaker, text: "ENG  Okafor: fire in the galley, I'm on it." / "HELM  Ruiz: holding your heading, Captain." / "SHIP  Clearance granted, Highport port 2." |
| Speaker | The crew member at the owning station; "SHIP" when nobody is (automated) |
| Stack | 3 lines, newest at the bottom; each holds 6 s (advisories) or 10 s (cautions and warnings), with a fade; a repeat within the hold counts ("×3") instead of stacking, as `NoticeLog` does now |
| Placement | Z2, top left. On deck, a line spoken by a crew member in view also shows as a short bubble over them for its first 3 s (new) |
| Interaction | Click a line to open its home page. The full history is the Log tab on the slate |

The ticker replaces the notices stack in today's status column. What goes into it is defined by the alert catalogue (section 13.4).

### 7.3 Prompt

**Purpose:** what the avatar can do here.

| Element | Content | Visible |
|---|---|---|
| Console prompt | Over the console tile: the console's name and its verbs with their keys: "HELM  [E] Use  [V] Sit" | Within `INTERACT_RADIUS` of a console |
| Door prompt | "[F] Force  [B] Cut" | At a shut door that can be forced |
| Response | The answer to a key pressed where it can't work: "Undock from the helm: [V] at the helm, then [U]" | 4 s, in the prompt's place (or bottom centre when there is no prompt) |
| Learning hints | The key line of today, cut to at most 3 contextual hints, each shown until the player has used that action 3 times (new, stored in settings) | Bottom centre, above the band when seated |

### 7.4 Suit readout

**Purpose:** the avatar's own survival, as their suit's display would show it. Deck only.

| Field | Shown when |
|---|---|
| Air in the suit (mm:ss) | The avatar is suited and their room's air is not breathable |
| Room pressure (kPa) and oxygen | The room's air is not breathable or falling |
| Temperature or "FIRE" | The room is on fire or heating |
| Captain's condition ("Light wound", "Critical, 4:10") | The avatar is wounded |

Hidden otherwise. Placement Z6, left, above where the band would be.

### 7.5 Warp strip

Only while time warp is above 1x: "WARP 100x  rails   drops out in 4:12 for burn 2". When warp is refused or held back, its reason ("held: crew outside", "held: hull venting") replaces the drop-out text (`WarpNotices`). Z1b, under the annunciator.

### 7.6 Guide card

The tutorial layer (First Play objectives and the opening walkthrough) is **out of fiction** and looks it: one card, top left, with the current objective and its keys. It never shares the callout ticker (today the guide text, notices and status lines share one column). Hidden when no guide is running.

---

## 8. Seated clusters

### 8.1 Helm cluster

Z3b, centre bottom, while seated at a console hosting Helm. Five rows at most; empty rows collapse.

| Row | Content | Visible |
|---|---|---|
| **Velocity** | Speed in the active frame and its tag: "ORBIT 7.67 km/s", "TARGET 3.9 m/s closing", "PORT 2.4 m/s closing". Rotation rate. Assist ON or OFF. Throttle and RCS activity as small indicators | Always while seated |
| **Reference** | The locked target or nav target: name, range, closing speed, closest approach ("CPA 140 m in 2:10") | Only with a target or nav target |
| **Δv** | Δv left in the tanks (m/s), with a notch at what the trip home needs (`Navigation`'s return estimate) and the planned burns' total shown as a bracket | Always while seated |
| **Next event** | The next thing due: "Burn 1 in 12:04  210 m/s prograde", "Helm order: APPROACH, holding at 200 m", "Autopilot: transfer to Shackleton Ice, leg 2", "SOI change in 2.4 d (Moon)". Within 60 s of a burn: the alignment error and the time to ignition | When there is a next event |
| **Chronometer** | Day and time; warp level if not 1x | Always while seated |

The frame follows the existing marker rule (`NavMarkerSystem`): the orbit, or the locked target when it has a body in the bubble, plus a new PORT frame during a docking approach.

### 8.2 Docking approach mode

Replaces the Reference and Next event rows when the selected port is armed, or a free port is within `PlayerDocking.READOUT_RANGE` (5 km) of a station that is the nav target.

| Field | Content |
|---|---|
| Port | Own port selected (n of m, size) and the target port ("Highport port 2, S") |
| Clearance | Not asked, asked, granted, refused (with reason) |
| Range and closing | Range to the port; closing speed against the safe closing speed for that range, as a bar with the limit marked |
| Alignment | Lateral offset (m) and angle error (degrees) |
| Docked | Once latched: "Docked at Highport, strain 0%"; collar strain becomes a caution above a threshold |

World symbology: the approach corridor (`NavOverlay`), coloured by safety as now.

### 8.3 Tactical cluster

Z3b while seated at the weapons console.

| Row | Content |
|---|---|
| **Mount chips** | One chip per gun: name or position (Nose, Port...), state (ready, reloading, no power, stunned, destroyed), who works it (you, a gunner by initial, the console), firing at will or held. Point defence mounts marked PD with AUTO or yours |
| **Launchers** | Each launcher: missiles left, ready or seconds to ready |
| **Countermeasures** | Decoys left |
| **Shields** | Raised, lowered or stowing; cloud mass as a share of capacity; trim (even, bow, port, stern, starboard) on a small rose |
| **Target** | Locked target: name, class, range, closing, time of flight, hit chance with the guns in hand (`HitChance`), its known condition (struck, disabled, venting) |

### 8.4 Gunner sight

Seated at a gun. No MFDs, no station board beyond its own tile; a deliberately narrow view.

| Field | Content |
|---|---|
| Sight | The gun's arc and lead reticle in the world (symbology) |
| Gun | State, ammunition or heat, time to ready |
| Target | Name, range, time of flight, hit chance |

### 8.5 Station board

Z3d, a single strip under the band while seated at a command console; a row on the slate's Ship tab otherwise.

```
 1 HELM  you    2 NAV  you    3 TAC  Chen  3/4 guns    4 ENG  Okafor ▲    5 COMMS  you    6 CMD
```

| Tile state | Meaning |
|---|---|
| you | The player is at a console hosting it |
| initials | A crew member mans it |
| empty | Nobody; automated only |
| ▲ caution, ◆ warning | The station has an active alert; the tile is lit in that role |
| struck through | Its console is destroyed or unpowered |

A tile is a soft key: clicking it (or its number with the modifier, section 16) puts that station's main page on the right MFD; again cycles its pages.

---

## 9. Multi-function displays and pages

### 9.1 How the MFDs behave

- Two MFDs, left and right, on each command seat (helm and weapons). None at a gun, none on deck.
- An MFD shows one **page**. Each page belongs to a station. Pages of the hosted stations are **owned** (with their controls); others are **repeaters** (read-only, with order buttons where the station takes orders).
- **Soft keys** along each MFD's lower edge name the pages of the station on show; a station key (from the station board) swaps stations.
- **Defaults** come from the console and the ship condition:

| Console | Condition | Left MFD | Right MFD |
|---|---|---|---|
| Helm | Green | NAV | SENSORS |
| Helm | Yellow | SENSORS | NAV |
| Helm | Red | SENSORS | DAMAGE |
| Helm | Docked | DOCK | CARGO |
| Weapons | Green, Yellow | SENSORS | WEAPONS |
| Weapons | Red | SENSORS | DEFENSE |

- **Auto-paging**: when a caution or warning is raised, the right MFD turns to the alert's home page unless the player **pinned** it (a soft key). When the alert clears, after 10 s, the MFD goes back to the page it showed before. Several alerts: the most severe, then the newest, has the MFD; the others wait on the annunciator.
- A station whose console has no power shows a dead page ("ENG: NO SIGNAL") rather than a stale one.

### 9.2 Page catalogue

| Page | Station | Content | Owned controls | Repeater orders |
|---|---|---|---|---|
| NAV | Navigation | Body, altitude, Ap, Pe, period; next SOI change or impact; small orbit drawing | Open Plot | None |
| PLAN | Navigation | Burns (number, time to, Δv), total against Δv left; transfer proposal; autopilot | Accept, discard, warp to burn | Helm: fly burns (1) |
| HELM | Helm | Who has the helm, order in force, how it is going | Orders 1 to 6, 0 | Orders 1 to 6, 0 |
| DOCK | Helm | Ports with size and state; clearance; latched or docked, strain | Next port, arm, undock | None |
| SENSORS | Tactical | Radar scope (`RadarScope`: range steps 0.5 to 1,000 km) and the contact list: class, name, range, closing, CPA, hostile or not, sorted by threat | Lock target, set nav target, range | None |
| WEAPONS | Tactical | Mounts: weapon, ammunition, state, who works it, at will or held | Hold or free each gun | Hold all guns, free all guns |
| DEFENSE | Tactical | Shield ring (96 segments), mass, trim, emitters; decoys; point defence | Raise, lower, trim | None |
| DAMAGE | Engineering | Ship plan: integrity, breaches, fires, smoke, venting, patches, repairs and who | Damage-control priority (new) | Same |
| POWER | Engineering | Generation, demand, store; systems off, stunned (countdown), browned out | Switches (at the console only) | None |
| ATMOS | Engineering | Rooms: pressure, oxygen, smoke; tanks; hours of air | None | None |
| COMMS | Comms & Cargo | Clearance, ports, beacon, standing with the local faction | Ask clearance, beacon | None |
| CARGO | Comms & Cargo | Hold: volume, each cargo, value | None | None |
| CREW | Command | Station board in detail; wounded with their timers; who is outside | Assign (slate) | Suits at battle stations |
| CONTRACTS | Command | Contracts held: what, time left, where, range | Set as nav target | Same |

---

## 10. Station panels

### 10.1 Panel behaviour

- **Side panel**: up to 512 units wide, full height, on the right. The world keeps running and stays visible on the left, so a player at the reactor console can watch their crew fight the fire they are routing power around.
- **Full screen**: Plot (the map), Yard, Shipyard and the ship editor. They bring their own annunciator.
- Every panel has the same grammar: a title bar (station, console, and for Station link the station's name and the player's credits), tabs, a body of tables and controls, and a footer of soft keys with their key caps. The footer replaces the per-window "Close [Y]" buttons: Esc always closes.
- Opening a panel stops the seated band from taking keys; the panel's own focus navigation (`FocusNav`) has them, as now.
- Panels open on the page that matters: an alert's link opens its tab, a console opens its focus tab, a shortcut key opens its tab.

### 10.2 Panels

| Panel | Opened from | Tabs | Replaces |
|---|---|---|---|
| **Plot** | M at a console hosting Navigation (full); M elsewhere opens it read-only | Map (with traffic), Plan, Targets | `ManeuverMap` side panel, orbit minimap, nav readout lines |
| **Tactical board** | E at the weapons console; seated at the helm in Red, from the TAC tile | Contacts, Weapons, Defense | Status column's target and missile lines |
| **Engineering** | E at the reactor (Power) or life support (Atmosphere) | Power, Atmosphere, Damage, Systems, Tanks & propulsion, Shields | `ConsoleWindow` default layout, ship gauges, ship minimap |
| **Comms** | At the helm (COMMS tile), or the slate | Ports & clearance, Beacon, Hails | Docking readout lines, tow prompts |
| **Station link** | Docked only, anywhere aboard: T, Y, J or the COMMS tile | Market, Yard, Shipyard, Contract board, Hiring, Infirmary | `TradeWindow`, `YardWindow`, `ShipyardWindow`, `MissionWindow` offers, `CrewWindow` hires |
| **Slate** | Anywhere aboard: I, J (not docked), K, L, or the CMD tile | Ship, Crew, Medical, EVA, Boarding, Prisoners, Contracts, Career, Log | `CrewWindow`, `BoardingWindow`, `MissionWindow` held contracts, notice log, budget line |

### 10.3 Station link in detail

One panel for everything a station offers, with the berth on its title bar: "HIGHPORT  Terran Patrol Authority   Credits 5,000   Hold 12 / 40 m³".

| Tab | Content (from today's windows, reorganised) |
|---|---|
| Market | Commodities table: stock, buy, sell, carried; quantity control (1, 10, all) instead of four buttons per row; trade total before confirming |
| Yard | Repairs (priced, by damage, Repair all), tanks (oxygen, nitrogen, propellant, shield media when built), refits per hardpoint (fitted, choice, new, used, salvaged), furnishings; "Rebuild decks" opens the editor |
| Shipyard | Ships for sale with price after trade-in and why locked; prize in tow with its terms |
| Contract board | Offers: pay, standing, time, where; take, or why not yet. Held contracts stay on the slate |
| Hiring | People looking for a berth, wages, sign-on |
| Infirmary | Who is being treated, how long |

Only tabs the station offers appear. On docking, a **berth card** goes up for 8 s under the annunciator: "Docked at Highport.  [T] Market  [Y] Yard  [J] Board", then the COMMS tile carries a small badge. This replaces the permanent "Docked at Highport [T] Trade [Y] Yard [J] Missions" world label.

### 10.4 The slate in detail

The slate is the captain's personal panel, available anywhere aboard.

| Tab | Content |
|---|---|
| Ship | One line per station: its state and the person at it; the station board; condition |
| Crew | Roster: name, role, skills, condition, morale, wage, record; station assignment; let go (docked) |
| Medical | The wounded: wound, bleed-out timer, who is treating them; medical stores |
| EVA | Who is outside, tethered or adrift, their air; hold everyone in; send out, call in |
| Boarding | When latched: the other ship's state, the boarding party (send, recall), then what can be stripped and carried over (`BoardingWindow`) |
| Prisoners | When held: who, faction, what can be done with them here |
| Contracts | Held: what, time left, where; set as nav target; give up |
| Career | Rank, sponsor, standing per faction, weekly budget and next payday, record; faction tension |
| Log | Every alert and callout, newest first, filterable by station and severity (today's notice log, kept longer than 50) |

---

## 11. World symbology

### 11.1 Layers by station and condition

| Symbology | Helm | Weapons | Gun | Deck |
|---|---|---|---|---|
| Prograde / retrograde markers | ● | ○ (Red: hidden) | – | – |
| Burn arrows with countdown | ● | – | – | – |
| Nav target bracket | ● | ● | – | – |
| Docking corridor | ● when approaching | – | – | – |
| Collision cue (new) | ● when CPA under 200 m within 60 s | ● | – | – |
| Hostile brackets | Yellow, Red | ● | ● | – |
| Lead reticle, intercept lines | Red, with a target locked | ● | ● (own gun) | – |
| Fire arcs | – | Guns in hand | Own gun | – |
| Threat tracks, impact cross, PD kills | ● (safety) | ● | ● | – |
| Contract waypoint | ● | – | – | – |
| Picture-in-picture optics | Target and nav target | Target | Target | – |
| Edge markers | Nav target, target, hostiles, waypoint | Target, hostiles, missiles | Target | – |

● shown; ○ optional; – hidden.

### 11.2 Contact labels

Today every marked contact carries a name and a range. New rules:

| Contact | Label |
|---|---|
| Locked target, nav target | Name, range, closing; always |
| Hostile | Class and range in Yellow and Red; icon only in Green |
| Contract objective | Name; always |
| Station | Name; range only when the nav target |
| Neutral traffic | Icon only; name and range on hover or when within 2 km |
| Own crew adrift | Name and range, always (`Spacewalks` marker) |

Labels that would overlap within 48 units merge into one cluster label: "Highport + 2", expandable on hover or click (as the PiP's "[+] 3 others" already does). Distances under 1 km in metres, above in km with one decimal.

### 11.3 Picture-in-picture optics

The PiP bubbles (`PictureInPicture`) are the ship's long-range optics: they belong to Tactical and Helm. Keep them for the locked target and nav target. The player's own ship gets a bubble only when the camera is zoomed out so far its hull is under 12 pixels. A bubble yields to the band and panels (collision rule 4).

### 11.4 Edge markers

Off-screen items that matter (see the table in 11.1) get an edge marker with a pointer and range; incoming missiles get the seconds to impact (as `ThreatOverlay` does). Everything else off screen is on the SENSORS page, not on the edge.

### 11.5 In-world cues on deck (art and lighting)

These are T0 cues that need art or lighting work; listed so the artists can plan for them.

- A console's screen glows in the alert role (caution or warning) while its station has an active alert, so the player can see which console to walk to.
- Door indicator lights: vacuum, fire or smoke on the far side.
- Rotating alarm beacons in rooms with a fire or breach.
- Crew speech bubbles for callouts by a crew member in view (section 7.2).

---

## 12. Ship condition

A ship-wide state that sets the defaults for symbology, MFD pages, labels and crew behaviour. Derived automatically; the player can raise it (new key) and it can then only be lowered by the player or by docking.

| Condition | Entered when | Effect on the UI |
|---|---|---|
| **Green** | No hostiles detected within 50 km, no cautions or warnings | Minimum: nav symbology, hostiles as icons, MFDs NAV and SENSORS |
| **Yellow** | A hostile contact within 50 km, or any caution | Hostile brackets and class labels; MFDs swap to SENSORS first |
| **Red** | Fired upon, a missile inbound, a hostile within its weapons' range, or set by the player | Tactical symbology at the helm too (lead reticle on the locked target, hostile brackets, threat tracks); MFDs SENSORS and DAMAGE; the TAC tile opens the Tactical board from the helm; suits at battle stations, where that setting is on (`CrewProvider.setSuits`) |
| **Docked** | Latched to a station | Combat symbology off (guns hold when docked, as now); MFDs DOCK and CARGO; berth card and Station link |
| **Emergency** | Ship disabled, abandon ship ordered, or impact certain | A banner across the annunciator row with the cause and the player's options ("SHIP DISABLED: no power.  [P] twice abandons ship.  [B] twice calls a tow"); everything else stays as in Red |

Condition shows as a word on the station board's right end and as the frame role of the band (artists choose how).

Thresholds (50 km, weapons range) are tunables, not design commitments.

---

## 13. Alerts and callouts

### 13.1 Severities

Today's notices have three kinds (`INFO`, `IMPORTANT`, `WARNING`) chosen by how they look. The new levels are chosen by what the player must do:

| Severity | Meaning | Annunciator | Master lamp | Ticker | Auto-page | Sound |
|---|---|---|---|---|---|---|
| **Warning** | Act now or lose the ship, a life or the career | Tile | Warning | Yes, 10 s | Yes | Alarm, repeats until acknowledged |
| **Caution** | Act soon, or something you rely on is degraded | Tile | Caution | Yes, 10 s | Yes | Chime once |
| **Advisory** | Worth knowing; nothing to do now | – | – | Yes, 6 s | No | Soft tick (important ones: `UI_NOTICE`) |
| **Response** | The answer to the player's own input | – | – | No: shown at the prompt | No | None |
| **Record** | Kept for the log only | – | – | No | No | None |

About half of today's notices are **responses** ("Lighting on", "Navigation target: Highport", "Take the helm to undock"). They move out of the alert stream to where the player is looking: the prompt, or the soft key they pressed.

### 13.2 The alert record

Every alert carries:

| Field | Example |
|---|---|
| `id` | `fire`, keyed by room for one per room |
| `severity` | Caution |
| `station` | Engineering |
| `tag` | "FIRE" |
| `value` | "GALLEY" or a countdown supplier |
| `text` | "Fire in the galley" |
| `crewText` | "{name}: fire in the galley, I'm on it." when the station is manned and someone is on it |
| `home` | Engineering / Damage, focused on the room |
| `active` | A condition: the alert clears itself when it goes false |
| `rearm` | Hysteresis, as `FlightWarnings` re-arms low propellant past 25% |

### 13.3 Lifecycle

1. **Raised** when its condition turns true (or posted, for events). Warning and caution: tile, lamp, callout, auto-page, sound.
2. **Acknowledged** by the player: lamp stops flashing, alarm stops. The tile stays.
3. **Updated** in place: countdowns tick in the tile; `AirWatch`-style revisions replace the callout rather than adding one.
4. **Cleared** when the condition goes false: tile fades, the MFD reverts after 10 s, a record goes to the log ("Fire in the galley out, 1:42").
5. **Re-armed** only past its hysteresis.

Events with no lasting condition (contract done, payday, a door forced) are advisories with no tile.

### 13.4 Catalogue

Today's warnings and notices, with their new level. Tags are suggestions.

| Source | Today | Severity | Tag | Station | Home |
|---|---|---|---|---|---|
| `FlightWarnings` missile | Red banner, notice | Warning | MSL n  mm:ss | Tactical | DEFENSE |
| Impact trajectory (`OrbitInfoProvider`) | Orbit panel text | Warning | IMPACT  mm:ss | Navigation | NAV |
| Ship disabled (`ShipDisablement`) | Red status line | Emergency banner | DISABLED | Command | Slate Ship |
| Abandon ship (`AbandonShip`) | Red status line | Emergency banner | ABANDON | Command | Slate Crew |
| Fire (`GridHazardSystem`) | Minimap alert list | Warning | FIRE  room | Engineering | DAMAGE |
| Breach venting (`DamageControl`) | Notice, minimap | Warning, Caution once patched | BREACH  room | Engineering | DAMAGE |
| Out of patches (`DamageControl`) | Notice | Caution | NO PATCHES | Engineering | DAMAGE |
| Short circuit, tank burst | Notice | Caution | SHORT  room, TANK BURST | Engineering | DAMAGE |
| Oxygen tanks dry (`AirWatch`) | Warning notice | Warning | O2 TANKS DRY | Engineering | ATMOS |
| Crew air running out (`AirWatch`) | Revised warning | Warning | AIR  name  m:ss | Command | Slate Crew |
| Crew critically wounded (`CrewMedical`) | Notice | Warning | CRIT  name  m:ss | Command | Slate Medical |
| Crew wounded, unconscious | Notice | Caution | WOUNDED n | Command | Slate Medical |
| Low propellant, Δv margin (`FlightWarnings`) | Warning notice | Caution | Δv MARGIN | Navigation | PLAN |
| No power, brownout (`PowerRoutingSystem`) | Power gauge | Warning (no power), Caution (brownout) | NO POWER, POWER LOW | Engineering | POWER |
| System stunned (`IonStun`) | None | Caution | STUN  system  s | Engineering | POWER |
| Shield emitter tripped (`ShieldModel`) | None | Caution | SHLD TRIP  side | Tactical | DEFENSE |
| Shield cloud under 25% | None | Caution | SHIELD LOW | Tactical | DEFENSE |
| Fired upon, hostile act at you | Status line "FIRING ON YOU" | Caution (and Red) | UNDER FIRE | Tactical | SENSORS |
| Pirates intercepting (`NoticeFeeds`) | Warning notice | Caution | INTERCEPT | Tactical | SENSORS |
| Crew adrift, left behind (`Spacewalks`) | Notice, marker | Warning | ADRIFT  name | Command | Slate EVA |
| Crew in the exhaust (`Spacewalks`) | Orange status line | Caution | EVA EXHAUST | Command | Slate EVA |
| Helm unmanned with an order, or unpowered (`Bridge`) | Grey status line | Caution | HELM EMPTY, HELM DOWN | Helm | HELM |
| Burn missed or cut short (`HelmOrders`) | Notice | Caution | BURN MISSED | Navigation | PLAN |
| Collar strain high, torn loose (`PlayerDocking`) | Notice | Caution | STRAIN | Helm | DOCK |
| Clearance refused | Notice | Caution | NO CLEARANCE | Comms | COMMS |
| Stations will refuse you (hostile act) | Warning notice | Caution | REFUSED | Comms | Slate Career |
| Clearance granted, latched, undocked | Notice | Advisory | | Comms | |
| Burn due in 60 s; SOI change; warp dropped | Notices | Advisory | | Navigation | |
| Contract taken | Info notice | Response | | | |
| Contract done, failed, called off | Important notice | Advisory | | Command | Slate Contracts |
| Promotion, payday | Important notice | Advisory | | Command | Slate Career |
| Yard work done, salvage, boarding progress | Notices | Advisory | | Comms, Command | |
| Door forcing progress | Notice | Response | | | |
| Navigation target picked, lighting, reload refused | Notices | Response | | | |
| Career over, ship lost | Crash screen | Out of fiction | | | |

### 13.5 Crew callouts

- A callout's speaker is the crew member at the alert's station, or the crew member dealing with it (the engineer heading to the fire), else "SHIP".
- With a speaker, the line uses `crewText` and can carry what they are doing about it. Without one, it is the bare fact.
- **Safety never depends on manning.** Every warning and caution is raised whether anyone is at the station or not: the ship's own sensors raise them. Manning changes the words, not the warning.
- Callouts are rate-limited per station (one per 3 s) so a battle doesn't flood the ticker; the rest go to the log.

### 13.6 Responses

A response is shown where the input happened: next to the prompt on deck, beside the soft key or cluster row it concerns when seated, in the panel footer when a panel is open. 4 s, no sound beyond the UI click.

---

## 14. Visibility matrix

● shown; A shown only while an alert or the condition holds; C on demand (key or click); – hidden.

| Widget | Deck | Helm | Weapons | Gun | Panel open (side) | Plot | Docked | Warp |
|---|---|---|---|---|---|---|---|---|
| Annunciator, master lamps | A | A | A | A | A | A | A | A |
| Callout ticker | A | A | A | A | A | A | A | A |
| Prompt / response | ● | Response | Response | Response | Footer | Footer | ● | – |
| Suit readout | A | – | – | – | A (deck) | – | A | – |
| Warp strip | When warp | When warp | When warp | – | When warp | When warp | – | ● |
| Guide card | When a guide runs | same | same | same | same | same | same | same |
| Berth card | – | – | – | – | – | – | 8 s on docking | – |
| Helm cluster | – | ● | – | – | – | – | ● (DOCK) | ● |
| Docking approach mode | – | A | – | – | – | – | – | – |
| Tactical cluster | – | – | ● | – | – | – | ● (guns hold) | – |
| Gunner sight | – | – | – | ● | – | – | – | – |
| MFDs | – | ● | ● | – | – | – | ● | ● |
| Station board | Slate | ● | ● | Own tile | – | – | ● | ● |
| Nav symbology | – | ● | Target only | – | ● (helm) | In map | – | – |
| Tactical symbology | – | Yellow, Red | ● | ● | ● (seated) | – | – | – |
| Threat tracks | – | A | A | A | A | A | – | – |
| PiP optics | – | ● | ● | ● | – | – | – | – |
| Slate | C | C | C | C | Replaces | C | C | C |
| Station link | – | C | C | – | C | – | C (anywhere aboard) | – |

Warp: at more than 1x, the seated band collapses to the cluster's Next event and Chronometer rows and the MFDs fade to 50% (nothing on them changes fast enough to read); any caution or warning restores the band.

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
|                                                                                          |
+------------------------------------------------------------------------------------------+
```

Nothing else is on screen. Today the same moment has the flight panel, orbit, four gauges, the status column and a key line.

### B. On deck, fire and missiles inbound

```
+------------------------------------------------------------------------------------------+
| (W)  [MSL 2  0:37] [FIRE  GALLEY] [BREACH  CARGO 2]                                (C)   |
| ENG   Okafor: fire in the galley, I'm on it.                                             |
| TAC   SHIP: two missiles inbound, port side, 37 seconds.                                 |
| ENG   SHIP: cargo 2 venting.                                                             |
|                                                                                          |
|                     (deck view: galley console glows red, door lights,                   |
|                      alarm beacons in the galley and cargo 2)                            |
|                                       [avatar]                                           |
|   SUIT                                                                                   |
|   Air 4:10                                                                               |
|   Room 31 kPa  O2 low                                                                    |
+------------------------------------------------------------------------------------------+
```

Clicking FIRE opens the slate on Ship with the damage plan; walking to the reactor console and pressing E opens Engineering on Damage, beside the live deck.

### C. Seated at the helm, cruising (Green)

```
+------------------------------------------------------------------------------------------+
|                                                                                          |
|                                                                                          |
|                              o                                                          |
|                                [ship]                      [ Highport ]  1.2 km          |
|                                     x                                                   |
|                                                                                          |
|                         -> 1  12:04                                                     |
+---------------------+---------------------------------------------+----------------------+
| NAV                 | ORBIT 7.67 km/s     rot 0.0°/s    ASSIST ON  | SENSORS              |
| Earth  alt 400 km   | Highport  1.2 km  closing 3.9  CPA 140 m 2:10 |   (radar scope,      |
| Ap 403  Pe 397      | Δv 1,840  |=========|====      home 1,210   |    contact list)     |
| T 1:32:25           | Burn 1 in 12:04   210 m/s prograde          |                      |
| [NAV] [PLAN]   [pin]| Day 3  14:22:05                              | [SNS] [WPN] [DEF]    |
+---------------------+---------------------------------------------+----------------------+
| 1 HELM you   2 NAV you   3 TAC --   4 ENG Okafor   5 COMMS you   6 CMD          GREEN    |
+------------------------------------------------------------------------------------------+
```

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
| DOCK                | PORT 2.4 m/s closing    rot 0.1°/s   ASSIST  | SENSORS              |
| Port 1/1  S  armed  | Highport port 2 (S)    clearance GRANTED     |                      |
| Clearance granted   | 312 m   closing |=====|==  safe 3.1 m/s     |                      |
|                     | lateral +0.8 m    angle -1.5°                |                      |
| [DOCK] [HELM]       | Day 3  14:41:10                              | [SNS] [WPN] [DEF]    |
+---------------------+---------------------------------------------+----------------------+
| 1 HELM you   2 NAV you   3 TAC --   4 ENG Okafor   5 COMMS you   6 CMD          GREEN    |
+------------------------------------------------------------------------------------------+
```

### E. Seated at the weapons console, in a fight (Red)

```
+------------------------------------------------------------------------------------------+
| (W)  [MSL 2  0:37] [UNDER FIRE]                                                    (C)   |
| TAC   Chen: dart down.                                                                   |
| HELM  Ruiz: holding position under fire.                                                 |
|                           .  .  .  .  . (threat track, impact cross)                     |
|                [ship]  ----- lead ---->  <Raider>  7.7 km  64%                           |
|                                                                                          |
+---------------------+---------------------------------------------+----------------------+
| SENSORS             | NOSE ready  PORT reload 1.2  STBD Chen AUTO | DEFENSE              |
| Raider    7.7 km  H | PD AUTO   MSL 4 ready   DECOY 6             | (shield ring around  |
| Dart      2.1 km  H | SHIELD UP 82%   trim BOW                    |  hull, weak port)    |
| Dart      3.0 km  H | Raider  7.7 km  closing 2  ToF 1.3 s  64%   |                      |
| [SNS] [WPN] [DEF]   | Day 3  15:02:44                              | [SNS] [WPN] [DEF]    |
+---------------------+---------------------------------------------+----------------------+
| 1 HELM Ruiz   2 NAV --   3 TAC you   4 ENG Okafor ▲   5 COMMS --   6 CMD            RED  |
+------------------------------------------------------------------------------------------+
```

A hull hit would turn the right MFD to DAMAGE unless the player pinned DEFENSE.

### F. Standing at the reactor console

```
+------------------------------------------------------------------------------------------+
| (W)  [FIRE  GALLEY] [POWER LOW]          |  ENGINEERING  Reactor                          |
| ENG   Okafor: fire in the galley...      |  [Power] [Atmos] [Damage] [Systems] [Tanks]    |
|                                          |                                                |
|      (deck view stays live:              |  Reactor  3.0 MW    Demand 3.4 MW   Store 62%  |
|       crew fighting the fire)            |  -------------------------------------------   |
|                                          |  Helm           0.2 MW   ON    100%            |
|              [avatar]                    |  Shield emitter 1.1 MW   ON     80%  hot       |
|                                          |  Life support   0.4 MW   ON    100%            |
|                                          |  Turret nose    0.6 MW   STUN 14 s             |
|                                          |  Galley         0.1 MW   OFF                   |
|                                          |                                                |
|                                          |  [Switch]  [Priority]            [Esc] Close   |
+------------------------------------------------------------------------------------------+
```

### G. Docked: Station link

```
+------------------------------------------------------------------------------------------+
|                                          |  HIGHPORT  Terran Patrol Authority             |
|                                          |  Credits 5,000    Hold 12 / 40 m³              |
|      (world: the ship at its berth)      |  [Market] [Yard] [Shipyard] [Board] [Hiring]   |
|                                          |                                                |
|                                          |  Commodity  Stock  Buy  Sell  Held             |
|                                          |  Ice          420   12    10     0             |
|                                          |  Alloys        80   85    80     4             |
|                                          |  ...                                           |
|                                          |  Qty [1] [10] [All]   Buy 120 cr   [Confirm]   |
|                                          |                                   [Esc] Close  |
+------------------------------------------------------------------------------------------+
```

### H. Time warp

```
+------------------------------------------------------------------------------------------+
|                       WARP 1,000x  rails   drops out in 4:12 for burn 2                  |
|                                                                                          |
|                                  (outside view)                                          |
|                                                                                          |
+---------------------+---------------------------------------------+----------------------+
|      (faded)        | Burn 2 in 4:12   410 m/s retrograde         |       (faded)        |
|                     | Day 5  02:10:44   1,000x                     |                      |
+---------------------+---------------------------------------------+----------------------+
```

---

## 16. Input

### 16.1 Keys kept

All current actions keep their default keys and their meaning. The single-key shortcuts that opened windows now open the panel tab that holds the same thing:

| Key | Opens |
|---|---|
| M | Plot (full at the helm, read-only elsewhere) |
| T, Y | Station link: Market, Yard (docked) |
| J | Station link: Contract board (docked); slate Contracts (not docked) |
| I | Slate: Crew |
| K | Slate: Boarding (latched) |
| L | Slate: Log |
| E | The console's station panel |
| F1 | Controls (out of fiction, unchanged) |

### 16.2 New actions (rebindable; suggested defaults)

| Action | Suggested key | Notes |
|---|---|---|
| Acknowledge alerts | O | O is Lighting today, a render toggle that belongs in Settings and the developer console |
| Station page to right MFD | Alt+1 to Alt+6 | Stations numbered as on the board; again cycles that station's pages |
| Station page to left MFD | Shift+Alt+1 to 6 | |
| Pin right MFD | Alt+0 | Stops auto-paging |
| Raise ship condition | 7 | Cycles Green, Yellow, Red; lowered back to automatic with Shift+7 |

### 16.3 Gamepad

Keep today's layout. Add: L3 acknowledges; with Back held, the right stick flicks pick the station page (up NAV, right SENSORS, down DAMAGE, left CREW) for the right MFD. Panels keep `FocusNav`.

### 16.4 Mouse

Everything with a key cap is also clickable: annunciator tiles, ticker lines, station board tiles, MFD soft keys, contacts in the world and on SENSORS.

---

## 17. Notes for the artists

The artists change how this looks, not what it says or where it is. A style may turn a soft key into a physical switch, a gauge into a segment readout or a dial, or the annunciator into a row of backlit lenses, as long as:

1. **Every semantic role stays distinguishable**, and not by colour alone (also by shape, position, outline, fill or text):

| Role | Used for |
|---|---|
| Nominal | Normal values (rare, since nominal is mostly hidden) |
| Advisory | Advisory callouts, informational marks |
| Caution | Caution tiles and lamp, amber-level states |
| Warning | Warning tiles and lamp, threat tracks, impact cross |
| Own ship | Own markers, own fire arcs, own missiles |
| Friendly / Neutral / Hostile | Contacts |
| Navigation | Nav target, burn arrows, waypoint, corridor |
| Target | The locked target |
| Selected / focused | Focus navigation, the active soft key, pinned MFD |
| Unavailable | Dead screens, struck-through tiles, disabled controls |
| Crewed / Automated / Player | Station board tiles, mount chips |
| Out of fiction | Guide card, controls panel, menus |

2. **Zone budgets hold** (section 6.2). A style can redraw the band's frame but not grow it past 184 units.
3. **Text stays legible at 1x**: body at least 13 units, tags at least 12, numbers in tabular figures so they don't jitter.
4. **Motion is limited**: flashing at 2 Hz or slower, only the master lamps and new tiles; a "reduce motion" setting turns flashing into a steady lit state.
5. **Elements to design** (the full kit): annunciator tile (4 states: raised, acknowledged, clearing, overflow); master lamps; ticker line with station tag; prompt and response; suit readout; warp strip; guide card; berth card; cluster rows (velocity, reference, Δv bar with home notch and plan bracket, next event, chronometer); docking approach (closing-speed bar with limit, alignment); mount chip; launcher chip; shield rose; MFD frame with title, soft keys and pin; dead MFD; radar scope; ship plan; shield ring; station board tile (5 states); side panel and full panel frames with tabs and footer; table rows; quantity control; world kit (markers, brackets, lead reticle, intercept lines, fire arcs, threat track, impact cross, PD kill, corridor, nameplate, cluster label, edge marker, PiP ring, collision cue); in-world cues (console glow, door lights, beacons, speech bubble).

---

## 18. What happens to every current element

| Current element | Fate | Where it goes |
|---|---|---|
| FLIGHT: Speed | Changed | Helm cluster Velocity row, in the active frame (orbital speed only in the ORBIT frame) |
| FLIGHT: Heading | Dropped | Replaced by burn alignment error before a burn |
| FLIGHT: Markers frame line | Moved | The Velocity row's frame tag |
| FLIGHT: View EXTERIOR [V] | Dropped | The view is self-evident; the prompt says "[V] Sit" / the band's footer "[V] Stand" |
| TARGET block | Moved | Helm Reference row; Tactical Target row; only when locked. "No target [Tab]" dropped |
| ORBIT block (flight panel) | Moved | NAV page |
| SHIP STATUS: Propellant | Changed | Δv row (m/s, home notch); tank kg on Engineering Tanks |
| SHIP STATUS: Structure | Moved | DAMAGE page; caution tiles by threshold |
| SHIP STATUS: Power | Moved | POWER page; NO POWER / POWER LOW tiles |
| SHIP STATUS: Oxygen | Moved | ATMOS page; O2 tiles; suit readout on deck |
| RADAR | Moved | SENSORS page (default on the weapons console, and at the helm in Yellow and Red) |
| ORBIT minimap and its hints | Moved | NAV page; the hints dropped (learning hints cover them) |
| SHIP wireframe minimap, HULL %, ALL SYSTEMS NOMINAL | Moved | DAMAGE page, auto-paged on damage; "nominal" text dropped |
| Status column: clock and warp | Moved | Chronometer row; warp strip |
| Status column: Target line, "FIRING ON YOU" | Dropped / changed | Duplicate dropped; UNDER FIRE caution |
| Status column: Missile line | Moved | Tactical launcher chips |
| Status column: rank and budget | Moved | Slate Career |
| Status column: notices | Changed | Annunciator, ticker or responses, by severity |
| Status column: docking lines | Moved | Docking approach mode, DOCK page |
| Status column: navigation lines | Moved | Next event row, PLAN page, Plot |
| Status column: contracts | Moved | Slate Contracts, CONTRACTS page, waypoint marker |
| Status column: First Play objective, opening guide | Moved | Guide card |
| Status column: helm line | Moved | Station board HELM tile; HELM EMPTY / HELM DOWN cautions |
| Status column: EVA line | Changed | EVA EXHAUST caution; slate EVA |
| Status column: SHIP DISABLED, ABANDON SHIP | Changed | Emergency banner |
| MISSILE WARNING banner (outside and on deck) | Changed | MSL warning tile with countdown, master warning, alarm, threat tracks |
| Bottom key hint line | Changed | Up to 3 learning hints that retire; key caps on soft keys; F1 panel kept |
| "Press [E] for the Helm panel [V] sit and fly" | Changed | Console prompt "HELM [E] Use [V] Sit" |
| "Docked at Highport [T] Trade [Y] Yard [J] Missions" | Changed | Berth card for 8 s, then the COMMS tile badge |
| Nameplates on every contact | Changed | Label rules and cluster labels (11.2) |
| Prograde / retrograde markers | Kept | Helm symbology |
| Lead reticle, fire arcs, intercept lines | Kept | Tactical symbology, plus Helm in Red |
| Threat overlay | Kept | Threat tracks (safety, every seat) |
| Burn arrows, docking corridor | Kept | Helm symbology |
| Picture-in-picture | Kept, limited | Target and nav target; own ship only when tiny |
| Orbit map (M) | Kept | Plot panel; side panel reorganised |
| Market (T) | Merged | Station link: Market |
| Yard (Y), Ships for sale | Merged | Station link: Yard, Shipyard |
| Mission board (J) | Split | Offers in Station link: Board; held in slate Contracts |
| Boarding window (K) | Moved | Slate: Boarding |
| Crew window (I) | Split | Slate: Crew, Medical, EVA, Prisoners; hires in Station link: Hiring |
| Console window (generic) | Replaced | Engineering: Systems |
| Notice log (L) | Moved | Slate: Log, filterable |
| Controls panel (F1) | Kept | Out of fiction |
| Crash, career over | Kept | Out of fiction |
| Pause menu, settings, main menu, load and save | Kept | Out of fiction; restyled by the artists |
| Lighting toggle (O) | Moved | Settings and the developer console |
| Developer console (~), debug menu (F3), overlays | Kept | Developer layer, not part of this design |
| Mod trust dialog | Kept | Out of fiction |
| Ship editor | Kept | Full-screen; should adopt the panel grammar later |

---

## 19. Implementation notes

For whoever builds this; nothing here is binding on the art.

**What stays.** The provider interfaces in `com.ringsofiron.uihud.providers` are the right seam and carry almost all the data this design needs. The world-space systems (`HudRenderSystem`, `NavMarkerSystem`, `ThreatOverlay`, `FireArcOverlay`, `NavOverlay`, `PictureInPicture`) stay and gain a visibility gate. `UiScale`, `HudFonts`, `FocusNav` and `KeyBindings` stay.

**New pieces.**

| Piece | Role |
|---|---|
| `Station` enum and `StationHosts` | The six stations; which console ids host which, read from data (a `[consoles]` table in modding-data) with the fallback rules of 5.1 |
| `Presence` | Where the avatar is: seated (console), standing (console), deck, away; from `Bridge` and `InteractPromptSystem` |
| `ShipCondition` | Green, Yellow, Red, Docked, Emergency, derived each frame from `ThreatProvider`, hostile contacts, docking and disablement, plus the player's override |
| `AlertBus` | Grows out of `NoticeLog`: alerts with severity, station, tag, value, active condition and re-arm (13.2). `NoticeLog.post(text)` stays as an advisory so no system breaks; systems move over one at a time |
| `Annunciator`, `CalloutTicker` | Widgets on `AlertBus` |
| `MfdPage`, `Mfd` | A page per row of 9.2; the two displays with soft keys, pin and auto-paging. `RadarScope`, `WireframeMinimap` and a small `OrbitMap` become pages |
| `HelmCluster`, `TacticalCluster`, `GunnerSight` | Section 8, replacing `FlightReadoutSystem`'s panel and `ShipGauges` |
| `StationPanel` | The shared panel frame (10.1); today's windows become its tabs |
| `Visibility` | A pure function from (presence, condition, warp, open panel) to the set of visible widgets: section 14 as code, unit-tested row by row |
| `ShieldStatusProvider` | Shield mass, segments, trim and emitters for DEFENSE (new data path; the model has it) |

**New data needed.** Collision risk (CPA of contacts against own path, short horizon); per-room damage-control priority; learning-hint use counts (settings); crew assignment per station (from ai-crew's `ManConsoleTask`); hours of air at current crew.

**Order of work.** Each step ships a playable game:

1. `AlertBus`, annunciator, ticker, responses; the status column goes.
2. `Presence`, `Visibility`; on deck, everything but the overlay goes.
3. Helm cluster, MFD frame with NAV, SENSORS and DAMAGE pages from today's widgets, station board; the old flight, gauge, radar and minimap panels go.
4. Tactical cluster, gunner sight, WEAPONS and DEFENSE pages; `ShipCondition`.
5. Engineering panel; the console window goes.
6. Station link and slate; the trade, yard, shipyard, mission, boarding and crew windows become tabs.
7. Label rules, cluster labels, in-world cues.

`tools/playtest/ui-tour.sh` should take a screenshot per walkthrough in section 15, so each step can be compared with this document.

---

## 20. Open questions for Joe

1. **Should manning a station give more information, not just different words?** For example a sensor operator classifying contacts sooner, or an engineer's callout naming the cause. Recommendation: words only at first (13.5), and keep information-by-manning as a later gameplay lever.
2. **Should the plot be readable from anywhere?** Recommendation: yes, read-only, with planning only at the helm. Without it the player walks to the helm to check a contract's destination.
3. **Helm orders from the slate when a helmsman is at the helm?** Recommendation: yes; it fits "order the crew" and saves a walk when the player is fighting a fire.
4. **Dedicated consoles on bigger hulls** (nav table, comms, damage control, medbay)? Recommendation: yes, as data, after the overhaul; the hosting table already allows it.
5. **Manual ship condition**: worth a key, or automatic only? Recommendation: automatic with a manual raise, since Red also sends suited crew to battle stations.
6. **Lighting toggle (O)** to Settings, freeing O for acknowledge? Recommendation: yes.

# Stations & Docking Design

## Overview
Stations are physical places, not menus. A station is a full interior grid that the player and crew walk through, built from a station blueprint, with its own NPC crew. Ships reach a station (or another ship) by flying a docking port onto a matching port until the two latch. Everything that crosses the connection (cargo, refit parts, people) is carried through the joined airlocks tile by tile, and it takes time.

This document collects the design decisions for stations, docking ports, the docked connection, moving goods across it, forcing doors, and refitting while docked. It spans several modules; the sections below name which module owns each part.

## Implementation Dependencies
- **`interior-grid`**: station grids, the docking port subsystem and its footprint, doors and power.
- **`physics-exterior`**: magnets, spring joints, weld joints, break limits.
- **`macro-sim`**: stations on rails, markets, the abstract dock for freighters off-bubble, docked pairs on rails.
- **`ai-crew`**: station crew and loaders, carrying cargo and parts, forcing doors, refit work.
- **`modding-data`**: station blueprints and docking port definitions.
- **`ui-hud`**: arming ports, docking alignment readouts, the refit UI.

## What Exists Today
Stages 1 (station grids and docking ports) and 2 (walking and air across a dock) are in. The new `stations-docking` module holds the docking logic; the other modules gained the pieces it needs.

- **Station grids.** `ChassisDef.station` marks a station layout and `StationTypeDef.blueprint` names it (`station_outpost` and `station_hub` in `chassis.toml`). `DockingData.buildStation` gives a station entity its walkable grid, systems, cargo bays and docking ports. In the bubble, `StationBodies` gives each station a kinematic Box2D body shaped like its grid, which `KinematicTransientSystem` moves along the station's orbit, so ships collide and dock with it. Stations still stay on rails; they have no crew yet.
- **Docking ports.** `D` in a chassis map is a port of any of the three sizes (`DockingPortLayout`), a `DOCKING_PORT` subsystem with a frame, an opening onto the deck and a sealed door. Ports are passive unless the chassis lists them as active. Ships get their chassis' ports when they spawn; macro-sim's freighters, which fly a default hull, get a passive Medium port on the nose.
- **Docking physics** (`DockingSystem`). Each port has a density-0 collar fixture half a tile deep. An armed port's magnets pull its face towards a same-size free port within a few meters (two magnets across the face, so they also square it up, with eddy damping) and latch on contact within tolerance. An armed active port puts out a collar head (1 m for Small, 1.5 m Medium, 2 m Large) on a guide and a soft spring. The head carries the magnet, slides along what it touches (collars have almost no friction) and tilts up to 20 degrees on a centering spring, so it lies flat on a port that is turned away. Once it sits face on face, square to within 2 degrees and centered, it captures that port and is pulled flush onto it; it then draws in while its tilt spring turns the craft into line, and locks only when home and square. Fully drawn in, the two craft's own collars meet: that contact is the hard stop, so thrusting into a collar pushes the other craft rather than the collar into the hull. Passive magnets latch under the same 2 degree tolerance. A latch seats the pair with a motor joint (rotation assist stands down meanwhile) and puts them exactly face to face before welding, so docked decks always line up as one. Undocking drops the weld and pushes the craft apart; pulling past a port size's break force or torque tears both collars off and damages the hull and door around each port. Tuning per size is in `DockingTuning`.
- **Clearance.** `requestClearance` asks the nearest station. One that grants it arms its nearest free port of the same size for the asking craft (for 10 minutes), and that port's collar reaches out unless the craft brings its own active collar. One that refuses keeps its ports unarmed and its collar in. A craft can still latch with its own magnets or collar, but is then docked without leave (`DockingPort.permitted` is false).
- **Rails warp.** A collar still drawing a ship in, or a latch still settling, holds rails warp back. A docked pair goes onto the rails as one body (the follower rides the leader's orbit; a station always leads) and comes back latched (`DockingBubbleHandoff`).
- **Saves.** Port state and latches are saved with the entity; the joints are rebuilt on load, so a docked ship stays docked.
- **Walking and air across** (stage 2). When the weld forms, `DockLinks` pairs each opening tile of one port with the opening tile facing it on the other as an interior-grid `GridLink` (one per pair, so a Small dock has one link and a Large dock five). Each port door opens only if its own side agreed to the dock (`DockingPort.consented`: the port was armed, and for that craft if it was armed for one); a station that refused clearance keeps its door shut. While both doors are open, air moves between the two craft's rooms through the link and the avatar walks across (`CrewMovementSystem`); a linked edge never vents to space. Undocking, tearing loose or losing the weld removes the links and shuts both doors (a door torn away stays open, and that port vents). Links are runtime only and are rebuilt whenever the weld is, so they come back after a load or rails warp.
- **The game.** Every station in the starting scenario has its interior and ports; Highport is turned so its Small ports face the player. A freighter that comes into the bubble while sitting at a station is latched to a free Medium port there (`StationTraffic`) instead of appearing inside the hull. G arms the selected port and asks for clearance, H picks the next port, U undocks, and a readout shows how the port lines up with the nearest free port of its size. In deck view the camera follows the avatar and draws every docked craft's deck, so the player can walk onto a station; U refuses to undock while the avatar is aboard the other craft. Trading needs the ship docked at the station with its clearance; `MacroSimulation.dockableStation`'s range check is no longer used for the player.
- **Demo.** `demos/Docking demo.bat` (`gradlew :lwjgl3:runDockingDemo`) has seven scenes: passive latch, active collar, station clearance, clearance refused, tearing loose, a hub with a hauler berthed, and walking aboard a station. P flies the approach until the magnets or collar take over; V switches to deck view, where WASD walks the avatar and port doors show green open and red shut.
- **Not yet:** AI crew planning paths across a link (only the avatar crosses today), line of sight, sound and fire across a link, station crew and loaders, carrying cargo across, forcing doors, refit, and NPC freighters docking under their own power in the bubble.

## Decisions on the Open Questions
- **Ports per station type:** every station carries all three sizes, so any ship can berth. The outpost layout has one of each, the hub two of each. All station ports are active, so a station that grants clearance reaches out with its collar.
- **Which door opens:** each side's own, by its own consent. A craft that armed its port (for anyone, or for this craft) opens its door; one that did not keeps it shut. So docking without clearance opens your door onto the station's shut one, and only forcing it (stage 4) gets you in.
- **The collar space:** no compartment of its own. Each pair of opening tiles is linked door edge to door edge, and the doors are what holds the air; a torn-off collar shuts both doors unless a door was torn away too.
- **Station crew:** moved to stage 3 with the loaders, since both need crew planning paths across a link.
- **Hostile acts and faction standing:** deferred to stage 4, with forcing doors. Proposed default: each hostile act at a station costs standing with the station's faction, more for cutting a door than for prying or hacking it, and docking without clearance costs nothing by itself.

## Stages
1. Station grids, docking ports, docking physics, clearance, the docked pair in rails warp, the demo. **Done.**
2. `DockLink` and doors: port doors opening for the sides that agreed, air and walking across, the demo's walk-aboard scene. **Done.**
3. Carrying cargo: station crew, crew paths across a link, and loaders and crew moving market goods tile by tile; trades complete as units arrive.
4. Forcing doors and hostile acts (pry, cut, hack) and faction standing.
5. Refit and repair while docked.
6. NPC freighters docking physically in the bubble under their own power.

## Stations
- A station is an interior grid like a ship's: rooms, corridors, hull, conduits, systems, docking ports, cargo bays and vendor areas.
- Its layout comes from a chassis blueprint marked as a station. Each `StationTypeDef` names its blueprint, so every station type has its own layout and ports.
- Stations have an NPC crew run by `ai-crew`. Some of them work as **loaders** who move cargo between the station's bays and docked ships. Station crew are only simulated while the station shares the player's physics bubble.
- Stations can be damaged and boarded exactly like ships: the same grid damage, air and fire hazards, breaches and internal combat rules.
- Station markets keep their stock in physical cargo-bay tiles on the station grid, so goods bought or sold move between real tiles.

## Docking Ports
A docking port is a new subsystem: an airlock built into the hull edge, with a facing direction and a latch collar.

### Sizes
| Size | Footprint | Opening width |
|---|---|---|
| Small | 3×1 | 1 tile |
| Medium | 5×2 | 3 tiles |
| Large | 7×3 | 5 tiles |

- Ports only attract and dock with ports **of the same size**.
- The latch collar sticks out half a tile past the hull. Two mated collars form one full tile between the craft.
- The opening is the airlock's door into the grid; once docked, the two openings join into one passage.

### Passive and Active Ports
- **Passive** ports, while armed, have a small magnetic attraction toward same-sized ports on the other vessel. Most ships only have passive ports.
- **Active** ports, while armed, extend a **telescoping latch collar**. The collar head is magnetically attracted to the other port (the collar of an active port, or the latching surface of a passive one). It rides on Box2D spring joints that absorb impact and transfer torque.
- When an active port captures a target, it contracts its springs until it locks into the same profile as a passive port, forming a rigid connection.

### Docking Sequence
1. Arm a port. The pilot flies the ship to line up port with port.
2. Magnets pull the ports together (weakly for passive pairs, through the collar for active ports).
3. On contact within tolerance, the ports latch. An active collar then contracts on its springs.
4. The connection becomes rigid: a weld joint between the two bodies.

### Undocking
The active port's magnets disengage and the spring force pushes the two craft apart.

## Permission
Docking permission is not required to dock. Without it:
- The target's active ports will not arm, so docking relies on your own port and is harder (most ships have no active ports).
- Once docked, the port on the other ship or station will not open unless forced (see Forcing Doors).

## The Docked Connection
- In the bubble, Box2D keeps simulating the ship, held to the station or other ship by a **weld joint**.
- During rails warp, the docked pair goes onto rails as **one body**, and splits back into two welded bodies when the bubble resumes.
- Each port size has a **break force and break torque**. Going past either tears the collar and damages the hull around the port, and the two craft separate.
- Tolerances, magnet strengths, spring constants and break limits are tuning values to set during the prototype.

## Representing the Half-Tile Collar
The grid works in whole tiles, but each collar sticks out half a tile past the hull, and two mated collars make one full tile between two separate grids. The collar is modeled as part of the port, and joined collars as a portal:

- The collar is part of the port subsystem: its integrity, extension and magnet state live on the port, and its Box2D shape is an extra fixture half a tile deep on the hull side of the port's footprint (for active ports, a separate body on the spring joints until it locks).
- Neither grid gets a tile for it. Once locked, a `DockLink` pairs the opening tiles of the two ports one to one, so pathfinding, line of sight and air treat each pair as neighbours across the link.
- The joined collar space has no compartment of its own (see Decisions above): air moves straight from the room behind one door to the room behind the other, and only while both doors are open.
- Fits the existing edge-wall and compartment model with the fewest grid changes. Cross-grid links are needed for cargo carrying anyway.

Rejected alternatives: giving each port a full collar tile that overlaps its mate when docked (two copies of the same space), and spawning a bridge grid on docking (every system would have to handle a third grid that comes and goes).

## Moving Cargo Across
- Cargo crosses physically: ship crew or station loaders carry each unit from cargo-bay tiles, through the joined airlocks, to cargo-bay tiles on the other side. It takes time.
- Buying and selling through the station market sets up these carry jobs; the trade completes as units arrive.

## Forcing Doors and Ports
Crew can force doors and docking ports with tools. All of these take time:

| Door state | Method | Damage | Hostile act |
|---|---|---|---|
| Unpowered | Pry open | No | Yes |
| Locked | Cut through | Damages the door | Yes |
| Powered and locked | Hack | No | Yes |

Hacking takes longer than cutting. All three count as hostile acts.

## Refit and Repair While Docked
- Refit parts are bought used or new from vendors on stations, as physical cargo.
- The player picks the install tile in the refit UI. Crew carry the part there and do the work tile by tile.
- Repairs work the same way: crew work through damaged tiles.
- Skipping forward in time completes refit and repair work in the background, in proportion to the time passed.

## NPC Freighters
- Inside the bubble, freighters dock physically with ports, like the player.
- Out on rails they keep the abstract, time-based load and unload in `FreighterController`.

## Open Questions
- Station attitude: stations hold a fixed attitude in the bubble (`StationComponent.angle`). Whether some should spin, and how docking works with a spinning station, is open.

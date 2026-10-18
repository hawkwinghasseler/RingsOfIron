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
- `macro-sim`: stations are economy nodes on rails with markets (`StationMarket`) and can be destroyed. `MacroSimulation.dockableStation(shipUuid)` treats any ship within 500 m and 10 m/s of a station as "docked"; this range check is replaced by physical docking.
- Stations always stay on rails and show in the bubble as kinematic transients with no interior.
- `StationTypeDef` holds population, production, consumption and storage only.
- `interior-grid` has an `AIRLOCK` auxiliary subsystem but no docking port and no station grid.
- Cargo sits physically in cargo-bay tiles on the ship's interior grid (Physical cargo holds work).

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
The grid works in whole tiles, but each collar sticks out half a tile past the hull, and two mated collars make one full tile between two separate grids. Three ways to model it:

**A. Collar on the port, joined collars as a portal (recommended).**
- The collar is part of the port subsystem: its integrity, extension and magnet state live on the port, and its Box2D shape is an extra fixture half a tile deep on the hull side of the port's footprint (for active ports, a separate body on the spring joints until it locks).
- Neither grid gets a tile for it. Once locked, a `DockLink` pairs the opening tiles of the two ports one to one, so pathfinding, line of sight and air treat each pair as neighbours across the link.
- The joined collar space is one small compartment of its own (opening width × 1 tile) between the two port doors, so a torn collar vents just that space until the doors seal.
- Fits the existing edge-wall and compartment model with the fewest grid changes. Cross-grid links are needed for cargo carrying anyway.

**B. A full collar tile in each grid, overlapping when docked.**
- Each port's footprint includes one extra tile row outside the hull, and docked ships overlap by one tile with collision between the collar fixtures filtered off.
- Each grid keeps its own complete tile, so damage and hazards need no new code. But the two grids hold two copies of the same space, and the hull outline gains a notch when undocked.

**C. A bridge grid spawned on docking.**
- On lock, a tiny third grid (opening width × 1) is created and welded between the two craft. Crew walk ship → bridge → station.
- The bridge is a real grid with real tiles, so it takes damage and holds air with no special cases. But every system that works on one grid at a time must also handle a third grid that comes and goes, and that grid has to be saved and restored.

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
- Which collar representation to use (A, B or C above).
- How many docking ports each station type carries.
- Whether a hostile act at a station changes faction standing, and by how much.

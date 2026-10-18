# Interior Grid & Construction Module Design

## Overview
The `interior-grid` module handles the 2D tile-based internal structure of ships and stations. It defines how hull, walls, rooms, corridors, conduits and system mounts are laid out, and simulates the air, heat and fire inside them.

The construction rules here are the ones combat's damage model reads (`combat-sys/Design.md`, section 5, "Construction model the damage code reads"). The two documents must stay in agreement.

## Implementation Dependencies
- **`core-engine`**: Requires Ashley ECS to attach grid components to ship entities.
- **`physics-exterior`**: (Soft Dependency) When rendering the grid in world space, the interior tiles must be offset and rotated by the parent ship's Box2D transform. The grid logic itself runs purely in local coordinates. The grid's structural mass properties feed the ship body's mass and inertia.
- **`modding-data`**: (Soft Dependency) Chassis blueprints and the material table (mass, hit points and armor per weight class) are data. Until that module exists, `MaterialTable.createDefault()` holds the illustrative `hull_steel` values.

## Coordinate System
The grid uses **Box2D's orientation**. Tile [0][0] is the **bottom-left**, +x is the ship body's local +x and **+y points up**. Grid coordinates are body-local coordinates offset by `gridOriginLocal` and divided by `tileSize`, with no axis flip anywhere:

```
tile = floor((local − gridOriginLocal) / tileSize)
```

`gridOriginLocal` is the body-local position (meters) of tile [0][0]'s bottom-left corner. It comes from the chassis blueprint. Blueprint character maps are written top row first so they read naturally on screen; the loader stores text row `r` at `gridY = height − 1 − r`.

## Construction Model

### Tiles
Every tile has a floor type (`NONE`, `FLOOR`, `POWER_CONDUIT`), a wall type (`NONE`, `HULL`, `INTERNAL`), a **weight class**, a material id, a condition (`integrity`), and hazard state (floor covering and fuel, fire intensity, temperature, bulkhead seal, power). Air is not stored on tiles; each air-holding tile points at its **compartment** (see Atmosphere below).

### Floors and coverings
Every floor (including power conduits) is a **steel deck plate**. Steel does not burn. A `FloorCovering` can be laid on top of the deck: `CARPET` carries 1.5 kg of fuel per m². Fire burns the covering away and leaves the bare steel floor behind (`covering` returns to `NONE`).

The deck is also the pressure boundary under the room: in this top-down model the space beneath a deck is open space, so a damaged deck plate leaks to space like damaged hull.

### Two kinds of wall
- **Full-tile walls** fill a whole tile: `HULL` for the outer hull and `INTERNAL` for interior walls.
- **Edge walls** are thin walls on the boundary *between* two tiles, so a room can be divided without spending a tile on the wall. They are stored per edge on `ShipGridComponent`:
  - `edgeH[y][x]` is the bottom edge of tile (x, y); `edgeH[height][x]` is the top edge of the top row.
  - `edgeV[y][x]` is the left edge of tile (x, y); `edgeV[y][width]` is the right edge of the right column.
  - Each `EdgeWall` holds a weight class, material, integrity, and an optional bulkhead door (`hasBulkhead`, `bulkheadSealed`). An open door lets crew, air and fire through; a sealed door blocks like solid wall.
  - An edge wall can serve as hull: a floor tile whose outer edge carries an intact edge wall is sealed from the void beyond it.

### Weight classes
Every tile, edge wall and hull piece is **light, medium or heavy**. The class and material select a row in the material table:

| Stat | Used for |
|---|---|
| `mass` (kg per full tile) | Structural mass, centre of mass and inertia for the ship's Box2D body |
| `hitPoints` (J) | Energy needed to take the piece from integrity 1 to 0 |
| `armor` (J) | Penetration stripped from a round before the piece absorbs it |

An edge wall weighs `EDGE_WALL_MASS_FRACTION` (0.25) of a full tile of the same class. `ShipGridComponent.computeMassProperties(MaterialTable)` sums tiles and edge walls into mass, body-local centre of mass and moment of inertia about it. Installed systems carry their own mass and are not included.

### Multi-tile hull pieces
Hull and armor can be pieces bigger than one tile (`HullPiece`). A piece covers several tiles but is **one object with one integrity pool**: every covered tile is a `HULL` tile pointing at the piece, and `Tile.getIntegrity()` / `setIntegrity()` read and write the piece's pool. Destroying any part of a piece breaches all of its tiles.

- `BLOCK` fills its rectangular footprint; its faces are the tile edges.
- `WEDGE` is a right triangle. At rotation 0 its legs run along the footprint's left and bottom edges and its sloped face looks toward +x/+y. The standard piece is a **1×2 wedge** for sloped armor.
- Rotation is in counter-clockwise quarter turns (0–3) and swaps width and height on odd values.
- `faceNormal()` returns the sloped face's outward unit normal in body-local axes. Combat uses it for incidence and ricochet checks instead of the tile's square edge. A wedge is half solid, so it counts half a tile's mass per covered tile.
- Pieces go on void or plain hull tiles and may not overlap other pieces or mounts.

### System mounts
Ship systems install into fixed-size mounts (`MountFootprint`):

| Size | Primary mount (square) | Auxiliary / utility slot (rectangle) |
|---|---|---|
| S | 1×1 | 1×2 |
| M | 2×2 | 2×3 |
| L | 3×3 | 3×7 |
| XL | 5×5 | 5×12 |

- **Primary** systems (generators/reactors, shield emitters, turret mounts) use square mounts. **Auxiliary and utility** systems (life support, helm, weapons console, cargo, airlock) use rectangular slots, which can be placed in either orientation. Each `SubsystemType` declares which kind it needs, and a system cannot be installed in the wrong kind.
- A mount's footprint is its origin (bottom-left tile), size, kind and rotation. A rotation of 1 or 3 turns a rectangular slot on its side; on a square mount it only sets the system's facing.
- **Every tile in the footprint belongs to the installed system**, so a hit on any of them damages it. `Tile.mount` points at the footprint, and `MountFootprint.installed` at the system entity; `ShipGridComponent.subsystemAt(x, y)` is the tile→subsystem index combat uses.
- A footprint must lie entirely on interior floor, may not overlap another mount, and may not have an edge wall running through it.

## Damage, Leaks and Zero-Integrity Rules
Hull, wall and floor tiles, hull pieces and edge walls all take damage to their condition (`integrity`, 1 = intact).

**Leaks.** Below 50% condition a piece starts to leak air. `Tile.leakFactor(condition)` gives the leak as a share of the flow through an opening one tile wide with no wall at all:

| Condition | Leak |
|---|---|
| 50% and above | 0 |
| 50% → 25% | rises linearly from 0 to 0.5 |
| 25% → just above 0 | capped at 0.5 |
| 0 (destroyed) | 1.0, a full opening |

- A leaking **hull or internal wall tile** joins every air volume it touches across its edges (a room, space, the room on its other side).
- A leaking **edge wall** joins the tiles on its two sides, even while its door is sealed.
- A leaking **floor** joins its room to space.
- A sealed bulkhead on a tile stops that tile's leak.

**At integrity 0** a piece is destroyed and stops doing its job:
- A **`HULL`** tile (or hull piece) at 0 is a breach: `isHullBreach()` is true and it counts as open space, so `GridHazardSystem` vents the adjoining room on its next tick. Sealing a bulkhead on the breached tile patches it.
- An **`INTERNAL`** wall tile at 0 stops blocking fire, air and walking. It becomes an open tile with no fuel.
- An **edge wall** at 0 blocks nothing, whatever the state of its door.
- A **floor or conduit** at 0 is a hole to space and stops carrying power; `PowerRoutingSystem` routes around it.

`Tile.isOpen()` is the single test for "crew and fire can be here" (floors and destroyed internal walls), and `Tile.holdsAir()` adds "and not sealed off by a bulkhead". `ShipGridComponent.canPass(x, y, nx, ny)` is the test for walking between two adjacent tiles (both open, no closed edge wall between them) and `canFlow(x, y, nx, ny)` the test for air, smoke and fire (both hold air, no closed edge wall). Hazards, power and crew pathfinding all use these.

## Chassis Blueprints
`ChassisBlueprint` holds the character map plus everything a character cannot express, all in grid coordinates:
- `tileSize` and `gridOriginLocal`
- edge walls (tile, side, weight class, material, bulkhead)
- hull pieces
- explicitly placed mounts
- floor coverings, as rectangles: `covering(FloorCovering.CARPET, x, y, w, h)` lays carpet on every floor tile inside

Character map:

| Char | Meaning |
|---|---|
| `#` | Outer hull, medium |
| `1` `2` `3` | Outer hull, light / medium / heavy |
| `I` | Full-tile internal wall |
| `.` | Steel floor |
| `+` | Power conduit (steel floor) |
| ` ` | Void |
| `R` `S` `T` | Primary systems: reactor, shield emitter, turret mount |
| `L` `H` `W` `C` `A` | Auxiliary systems: life support, helm, weapons console, cargo, airlock |

System letters are floor tiles. Each solid rectangle of one letter becomes one mount, and its size must be a legal mount for that system's kind (`RR/RR` is an M reactor, `LL` an S life-support slot on its side). Two adjacent mounts of the same type cannot be told apart in the map, so those go in the explicit mount list. The loader throws on any illegal mount, piece or wall.

## Atmosphere
Air is simulated per **compartment**, not per tile. A compartment is one airtight volume: a room, a corridor, the gap of a door. Its air is a single well-mixed volume with one pressure and one composition (oxygen, nitrogen, CO2 in moles, plus suspended smoke in kg). Its volume is tiles × `tileSize²` × `DECK_HEIGHT` (2.5 m), and pressure follows the ideal gas law at the mean temperature of its tiles, so a fire also raises the pressure of its room. Rooms start at 101.3 kPa, 21% oxygen.

### What counts as one room
`CompartmentBuilder` divides the air-holding tiles. Openings one tile wide (doors, 1-wide corridors, a single missing hull tile) count as **closed** when deciding what a room is:
- A tile is *wide* if it lies in some 2×2 block of air-holding tiles that are all connected to each other. Every other air-holding tile is *narrow*.
- Two adjacent wide tiles are in the same room only if the edge between them is inside such a block. An opening two or more tiles across joins two areas; a single open door does not.
- Adjacent narrow tiles join each other, so a 1-wide corridor is a compartment of its own. A narrow tile never joins a wide one.

The compartments are rebuilt whenever a tile starts or stops holding air or an edge opens or closes (a door, a seal, a wall destroyed). Air is carried over: each old room's contents are spread evenly over its old tiles and each new room takes the share of the tiles it now contains. A newly opened tile brings no air of its own, so the room it joins thins out, and the air of a tile that was sealed off goes to the rest of its old room.

### Flow and venting
Atmosphere is **conserved**: it only leaves the ship through openings to space. Every tick, `GridHazardSystem` sums the openings between each pair of compartments, and between each compartment and space, in tile widths:
- each open edge between two compartments, or between a compartment and space (out of bounds, void, a breach): 1
- each leak (see Damage above): its `leakFactor`

Air flows from the higher-pressure side at `FLOW_COEFFICIENT` (2 mol/s per kPa per tile width), carrying that side's mix of gases and smoke, and never more than halfway to equal pressure in one step. A 54-tile room vents through one breached hull tile with a time constant of about 30 s; two rooms joined by an open door equalize in about 15 s.

There is no "vacuum" state that spreads. A vented room is simply a compartment with no air left. A sealed bulkhead or closed door is airtight, so sealing between a breach and a room stops that room's loss at once.

## Heat
Every tile (floors and walls) has a temperature. Each hazard tick:
- neighbouring tiles exchange heat by conduction (`CONDUCTION`, 60 W/K) and, where air connects them, by convection (`CONVECTION`, 300 W/K at standard pressure, scaled down as the room vents);
- every tile sheds heat into the ship's structure (`AMBIENT_LOSS`, 150 W/K above ambient 293 K);
- a tile holds `TILE_HEAT_CAPACITY` (20 kJ/K).

Heat therefore spreads out from a fire to the tiles around it, also through walls into the next room, and the ship cools back down once the fire is out. Crew and systems can read tile temperature for burns and heat damage.

## Fire
A fire is an **intensity** on a tile: 0 is no fire, a new fire starts at 0.05 (`Tile.ignite()`) and grows toward 1, fully involved. `Tile.fireSize()` reports SMALL, MEDIUM or LARGE.
- **Fuel.** Only a floor covering burns. A tile without one cannot be lit.
- **Growth.** With standard air a fire gains `FIRE_GROWTH_RATE` (1/30 per s), so a small fire takes about 30 s to become a large one. Growth slows as the room's oxygen falls; below 60% of standard oxygen partial pressure (`FIRE_MIN_OXYGEN`, about 12.6% O2 at 1 atm) the fire shrinks instead, at up to `FIRE_SMOTHER_RATE` (0.25 per s, in vacuum). As the last of its fuel goes, the fire dies down with it.
- **Burning.** A fire burns `BURN_RATE × intensity` (0.04 kg/s per m² at full size). Each kg of fuel uses 70 mol of oxygen and gives 55 mol of CO2, 0.08 kg of smoke and 25 MJ of heat (60% into the burning tile). A carpeted tile burns out in about a minute at full size, and the carpet is then gone. A fire in a sealed room uses up the room's oxygen and starves itself; an open door or a vent decides how long it can keep going.
- **Spreading.** Each second, a burning tile has a chance to jump to each neighbouring flammable tile that air connects it to: `SPREAD_RATE × intensity × (0.15 + 0.85 × heat)`, where `heat` runs from 0 at ambient to 1 at `IGNITION_TEMPERATURE` (600 K). Big fires spread to hot tiles quickly; a small fire barely reaches a cold one.
- **Auto-ignition.** A flammable tile at 600 K or more catches on its own (`AUTO_IGNITION_RATE`, 0.5 per s) if its room has the oxygen, even when the heat reached it through a wall.

Random draws use a seeded generator per `GridHazardSystem`, so runs are reproducible. The hazard tick is 0.25 s; all rates are per second.

## Power & Subsystem Routing
`PowerRoutingSystem` runs a breadth-first flood from every footprint tile of each reactor that is switched on and not destroyed, across floor and conduit tiles:
- Intact full-tile walls and tiles at integrity 0 do not conduct.
- Closed edge walls block power, except where a conduit runs on both sides of the wall (a conduit passes through a thin wall).
- A system is powered if any tile of its footprint is powered. It is operational when it is switched on (`SubsystemComponent.switchedOn`), powered and not destroyed (`health > 0`). Switching a reactor off is not damage: it powers back up as soon as it is switched on.
- It runs on a 1 s tick, or on the next frame when damage code sets `ShipGridComponent.gridDirty`.

## Components
- `ShipGridComponent`: tiles, edge walls, hull pieces, mounts, compartments, `gridOriginLocal`, coordinate conversion, placement validation, mass properties.
- `SubsystemComponent`: tags an entity as a ship system installed in a `MountFootprint`.
- `ShipSubsystemRegistryComponent`: the ship's list of subsystem entities.

Plain data classes: `Tile`, `EdgeWall`, `HullPiece`, `MountFootprint`, `Compartment`, `FloorCovering`, `WeightClass`, `Side`, `MaterialTable`, `ChassisBlueprint`.

## Systems
- `GridHazardSystem`: air flow between compartments and to space, heat, and fire.
- `CompartmentBuilder` (helper, not an ECS system): divides the interior into compartments and carries the air over when the layout changes.
- `PowerRoutingSystem`: calculates subsystem power connectivity.

## Open Questions
- **Life support.** Nothing removes CO2 or smoke or tops up oxygen yet. Powered life support scrubbing its compartment (and what it can reach through open doors) is the natural next step.
- **Fire damage.** Fire now burns only the covering and heats tiles. How heat hurts installed systems and crew belongs to those modules, reading `Tile.temperature`.
- **Walking over mounts.** Crew pathfinding treats mount footprints as blocked (crew work a system from an adjacent tile). If some systems, such as cargo, should be walkable, that becomes a per-type flag.

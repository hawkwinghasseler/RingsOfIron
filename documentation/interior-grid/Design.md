# Interior Grid & Construction Module Design

## Overview
The `interior-grid` module handles the 2D tile-based internal structure of ships and stations. It defines how hull, walls, rooms, corridors, conduits and system mounts are laid out, and simulates localized hazards like fire and decompression.

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
Every tile has a floor type (`NONE`, `FLOOR`, `POWER_CONDUIT`), a wall type (`NONE`, `HULL`, `INTERNAL`), a **weight class**, a material id, and hazard state (fire, vacuum, oxygen, integrity, power).

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

## Zero-Integrity Rules
A structural piece at integrity 0 is destroyed and stops doing its job:
- A **`HULL`** tile (or hull piece) at 0 is a breach: `isHullBreach()` is true and it counts as open space, so `GridHazardSystem` vents the adjoining room on its next tick.
- An **`INTERNAL`** wall tile at 0 stops blocking fire, vacuum and walking. It becomes an open (non-flammable) tile that holds air.
- An **edge wall** at 0 blocks nothing, whatever the state of its door.
- A **floor or conduit** at 0 stops carrying power, and `PowerRoutingSystem` routes around it.

`Tile.isOpen()` is the single test for "air, fire and crew can be here" (floors and destroyed internal walls), and `ShipGridComponent.canPass(x, y, nx, ny)` is the single test for moving between two adjacent tiles (both open, no closed edge wall between them). Hazards, power and crew pathfinding all use these.

## Chassis Blueprints
`ChassisBlueprint` holds the character map plus everything a character cannot express, all in grid coordinates:
- `tileSize` and `gridOriginLocal`
- edge walls (tile, side, weight class, material, bulkhead)
- hull pieces
- explicitly placed mounts

Character map:

| Char | Meaning |
|---|---|
| `#` | Outer hull, medium |
| `1` `2` `3` | Outer hull, light / medium / heavy |
| `I` | Full-tile internal wall |
| `.` | Floor |
| `+` | Power conduit |
| ` ` | Void |
| `R` `S` `T` | Primary systems: reactor, shield emitter, turret mount |
| `L` `H` `W` `C` `A` | Auxiliary systems: life support, helm, weapons console, cargo, airlock |

System letters are floor tiles. Each solid rectangle of one letter becomes one mount, and its size must be a legal mount for that system's kind (`RR/RR` is an M reactor, `LL` an S life-support slot on its side). Two adjacent mounts of the same type cannot be told apart in the map, so those go in the explicit mount list. The loader throws on any illegal mount, piece or wall.

## Hazard Simulation (Cellular Automata)
`GridHazardSystem` runs on a slow fixed tick (1.0 s) over open tiles:
- **Fire spread:** a burning tile consumes oxygen and wears down its own integrity. It ignites adjacent flammable tiles with enough oxygen that it can reach through `canPass` (no intact wall, closed edge wall or sealed tile bulkhead in the way). Fire dies at zero oxygen.
- **Vacuum spread:** an open tile that borders space (out of bounds, void, a breach, or a vacuum tile) across an edge that is not blocked by a closed edge wall bleeds oxygen. Vacuum then equalizes into neighbours it can reach through `canPass`. Sealed bulkheads and closed edge doors stop it.

## Power & Subsystem Routing
`PowerRoutingSystem` runs a breadth-first flood from every footprint tile of each operational reactor across floor and conduit tiles:
- Intact full-tile walls and tiles at integrity 0 do not conduct.
- Closed edge walls block power, except where a conduit runs on both sides of the wall (a conduit passes through a thin wall).
- A system is powered if any tile of its footprint is powered. Unpowered or zero-health systems are not operational.
- It runs on a 1 s tick, or on the next frame when damage code sets `ShipGridComponent.gridDirty`.

## Components
- `ShipGridComponent`: tiles, edge walls, hull pieces, mounts, `gridOriginLocal`, coordinate conversion, placement validation, mass properties.
- `SubsystemComponent`: tags an entity as a ship system installed in a `MountFootprint`.
- `ShipSubsystemRegistryComponent`: the ship's list of subsystem entities.

Plain data classes: `Tile`, `EdgeWall`, `HullPiece`, `MountFootprint`, `WeightClass`, `Side`, `MaterialTable`, `ChassisBlueprint`.

## Systems
- `GridHazardSystem`: processes the cellular spread of fire and vacuum.
- `PowerRoutingSystem`: calculates subsystem power connectivity.

## Open Questions
- **Walking over mounts.** Crew pathfinding treats mount footprints as blocked (crew work a system from an adjacent tile). If some systems, such as cargo, should be walkable, that becomes a per-type flag.

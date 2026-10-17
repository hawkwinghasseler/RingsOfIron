# Interior Grid & Construction Module Design

## Overview
The `interior-grid` module handles the 2D tile-based internal structure of ships and stations. It defines how rooms, corridors, conduits, and functional subsystems are laid out, and simulates localized hazards like fire and decompression.

## Implementation Dependencies
- **`core-engine`**: Requires Ashley ECS to attach grid components to ship entities.
- **`physics-exterior`**: (Soft Dependency) When rendering the grid in world space, the interior tiles must be offset and rotated by the parent ship's Box2D transform. The grid logic itself, however, runs purely in local coordinates.

## Implementation Guide
To implement this module independently:

1. **Tile & Grid Data Structures:**
   - Define a `Tile` POJO holding state: `wallType`, `floorType`, `hasFire`, `hasVacuum`, `oxygenLevel`.
   - Create a `ShipGridComponent` containing a 2D array `Tile[][]` (or a flat 1D array mapped to 2D coordinates) representing the ship's local layout.
   - Define coordinate conversion utility methods (`worldToGrid` and `gridToWorld`) that take the parent entity's `TransformComponent` to map local tile indices to global rendering coordinates.

2. **Ship Chassis Definition:**
   - Build a parser that loads a chassis blueprint (defining the max grid width/height and immutable outer hull tiles).
   - Implement logic to validate player construction (e.g., ensuring placed subsystems are within the valid interior bounds and don't overlap walls).

3. **Hazard Simulation (Cellular Automata):**
   - Create a `GridHazardSystem` that runs on a slow fixed tick (e.g., once every 1.0 seconds).
   - **Fire Spread:** If a tile `hasFire`, check adjacent tiles. If the adjacent tile is flammable and doesn't have a closed bulkhead, ignite it. Consume `oxygenLevel` and generate damage to subsystem entities occupying the tile.
   - **Vacuum Spread:** If a tile borders space (hull breach) and lacks a closed bulkhead, pull oxygen from adjacent tiles until pressure equalizes to vacuum.

4. **Power & Subsystem Routing:**
   - Create a `PowerConduitComponent`.
   - Implement a graph traversal algorithm (like Breadth-First Search) starting from reactor tiles. Mark connected functional tiles as `powered`. Unpowered subsystems disable their corresponding ECS components.

## Proposed Components
- `ShipGridComponent`: Holds the local 2D array of `Tile` objects.
- `SubsystemComponent`: Tags an entity as an internal machine (reactor, console) occupying specific local grid coordinates.

## Proposed Systems
- `GridHazardSystem`: Processes the cellular spread of fire, smoke, and vacuum.
- `PowerRoutingSystem`: Calculates subsystem power connectivity.

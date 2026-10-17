# Combat & Damage Resolution Module Design

## Overview
The `combat-sys` module bridges the gap between exterior space combat (Box2D impacts) and interior grid damage (module destruction, crew death). It handles weapon firing, projectile tracking, and the mathematical translation of external hull hits to internal consequences.

## Implementation Dependencies
- **`core-engine`**: Ashley ECS for entity and component tracking.
- **`physics-exterior`**: **Hard Dependency.** Relies on Box2D collision events (the `ContactListener`) to detect when a projectile strikes a ship.
- **`interior-grid`**: **Hard Dependency.** Needs to query the internal 2D grid to cast rays and apply damage to specific tiles.

## Implementation Guide
To implement this module independently (requires mocking Box2D impacts and a dummy 2D grid):

1. **Weapon and Firing Logic:**
   - Create a `WeaponMountComponent` detailing hardpoint coordinates (relative to the ship center), firing arcs, cooldowns, and projectile prefab IDs.
   - Implement a `WeaponControlSystem` that checks targets. If a target is within the firing arc and range, and cooldown is 0, spawn a projectile entity. Give it a Box2D body with high initial velocity.

2. **Collision Detection & Impact Calculation:**
   - Hook into the Box2D `ContactListener` (from `physics-exterior`).
   - When a projectile fixture hits a ship fixture, capture the world coordinates of the impact point and the relative velocity vector.
   - Queue an `ImpactEvent` containing the ship entity UUID, local impact coordinates (converted from world using the ship's transform), impact angle, and weapon penetration value.

3. **Penetration Raycasting (The Core Algorithm):**
   - Create an `ImpactResolutionSystem` that processes `ImpactEvents`.
   - Take the local impact coordinates (which sit on the outer hull boundary) and the angle. Cast a geometric ray (using Bresenham's or a digital differential analyzer/DDA algorithm) across the `ShipGridComponent`'s 2D array.
   - Step through the grid along the ray path. At each tile:
     - Check if it contains armor, a subsystem, or crew.
     - Subtract the object's "hardness" from the projectile's remaining penetration energy.
     - Apply damage to the object/tile.
     - If penetration energy <= 0, terminate the raycast.
     - If the ray destroys an outer hull tile, flag the tile as a vacuum breach.

## Proposed Components
- `WeaponMountComponent`: Defines fire rate, weapon type, arc.
- `ProjectileComponent`: Tracks damage type (kinetic, energy, explosive) and penetration depth.

## Proposed Systems
- `WeaponControlSystem`: Aims turrets towards targets and spawns projectile entities.
- `ImpactResolutionSystem`: Processes Box2D collisions and executes 2D grid raycasting.

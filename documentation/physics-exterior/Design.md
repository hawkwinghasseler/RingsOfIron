# Exterior Physics Module Design

## Overview
The `physics-exterior` module handles all rigid-body dynamics for objects in space (ships, asteroids, missiles) using LibGDX's Box2D wrapper. It manages the critical "Floating Origin" system to prevent physics degradation over massive distances.

## Implementation Dependencies
- **`core-engine`**: Requires the Ashley ECS `Engine` and `FixedLogicSystem` to step the Box2D physics on a fixed timestep.
- **External Libraries:** LibGDX Box2D.

## Implementation Guide
To implement this module independently (mocking the core-engine if necessary):

1. **Box2D World Initialization:**
   - Create a `PhysicsManager` that holds the `com.badlogic.gdx.physics.box2d.World`.
   - Define collision bitmasks/categories (e.g., `CATEGORY_SHIP`, `CATEGORY_ASTEROID`, `CATEGORY_PROJECTILE`).
   - Implement a `ContactListener` to handle physical collisions and dispatch events.

2. **ECS Integration:**
   - Create a `PhysicsBodyComponent` wrapping a Box2D `Body`.
   - Create a `PhysicsUpdateSystem` (extending `FixedLogicSystem`).
   - **Update Loop:** Step the Box2D World. Then, iterate over entities with `PhysicsBodyComponent` and sync their Box2D transform (position/angle) back to the base `TransformComponent`.

3. **Flight Dynamics & Controls:**
   - Create a `ThrusterComponent` with arrays for directional thrust power and rotational torque.
   - Implement a `FlightControlSystem` that reads player/AI input vectors and applies forces/torques via `body.applyForceToCenter()` and `body.applyTorque()`. Atmospheric drag should be set to 0.

4. **Floating Origin Shift Mechanism:**
   - Track the distance of the entity with `PlayerControllerComponent` from `(0,0)`.
   - If distance > `THRESHOLD` (e.g., 5000 meters), calculate a `shiftVector`.
   - Iterate through *every* `Body` in the Box2D world and apply `body.setTransform(body.getPosition().sub(shiftVector), body.getAngle())`.
   - Fire a `WorldShiftEvent` so other systems (like rendering cameras or macro-sim tracking) can adjust their coordinates by the `shiftVector`.

## Proposed Components
- `PhysicsBodyComponent`: Holds the Box2D `Body`.
- `ThrusterComponent`: Defines maximum thrust vectors and fuel consumption rates.

## Proposed Systems
- `PhysicsUpdateSystem`: Syncs Box2D state to ECS state.
- `FloatingOriginSystem`: Monitors player distance and triggers coordinate shifting.

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

## Implementation Status
Implemented in `physics-exterior/src/main/java/com/ringsofiron/physicsexterior/`, with tests in `src/test` (`gradlew :physics-exterior:test`) and a playable demo (`Flight demo.bat`, or `gradlew :lwjgl3:runFlightDemo`).

Where the code differs from the guide above, it follows `documentation/orbital-mechanics/Design.md`:
- **Box2D unit = 10 m.** Box2D moves a body at most 2 units per step, so at 1 unit = 1 m nothing could exceed 120 m/s. At 10 m per unit the cap is 1,200 m/s relative to the bubble. All public APIs are in SI units; use the static helpers on `PhysicsManager` (`getPosition`, `getVelocity`, `applyForce`, `getInertia`, ...) rather than raw `Body` calls so the scale stays hidden.
- **Floating origin shifts velocity too.** `FloatingOriginSystem` rebases when the player is more than 2,000 m from the origin or moving faster than 200 m/s relative to it. The origin takes the player's position and velocity, and `PhysicsManager` tracks the origin's universe position and velocity in doubles. The frame is inertial for now (no gravity); the orbital work can later replace it with a free-falling anchor.
- **`WorldShiftEvent`** carries both the position and velocity deltas plus the new origin state. It and `CollisionEvent` are delivered through Ashley `Signal`s on `PhysicsManager` (`worldShifts`, `collisions`). Collisions are queued during the step and dispatched after it, so listeners may create or destroy bodies.
- **Force application** happens in `FlightControlSystem.beforeStep`, called by `PhysicsManager` once per fixed tick, so forces are applied exactly once per Box2D step however many ticks run in a frame.

Conventions:
- `TransformComponent` holds the bubble-frame pose: meters, rotation in degrees counter-clockwise. Entities with a transform but no body are treated as fixed in the universe and are moved back by the origin's travel each tick.
- Ship local frame: +X is the nose, +Y is port. `FlightControlComponent` holds stick input in [-1, 1] (`forward`, `strafe`, `turn`, plus optional `rotationAssist`); `ThrusterComponent` holds the force, torque and fuel limits.
- Bodies made by `PhysicsManager.createPolygonBody/createCircleBody` never sleep and have zero damping, so a slow drift stays a drift. Density is in kg per square meter.

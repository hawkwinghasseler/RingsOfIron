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
   - Track the entity with `PlayerControllerComponent` relative to the bubble origin, in both position and velocity.
   - If it is more than 2,000 m from the origin or moving faster than 200 m/s relative to it, rebase: the origin takes the player's position and velocity (kept in doubles), and every `Body` is shifted by `-position` and `-velocity`.
   - Fire a `WorldShiftEvent` carrying both deltas so other systems (rendering cameras, HUD, audio, macro-sim tracking) can adjust.
   - See `documentation/orbital-mechanics/Design.md` section 3 for why velocity is shifted too, and how the origin becomes a free-falling orbital anchor.

## Proposed Components
- `PhysicsBodyComponent`: Holds the Box2D `Body`.
- `ThrusterComponent`: Defines maximum thrust vectors and fuel consumption rates.

## Proposed Systems
- `PhysicsUpdateSystem`: Syncs Box2D state to ECS state.
- `FloatingOriginSystem`: Monitors player distance and relative speed and rebases the origin.
- `TidalGravitySystem` (planned, from the orbital plan): applies the differential gravity across the bubble each tick.
- `KinematicTransientSystem` (planned, from the orbital plan): moves bodies passing faster than the Box2D cap along their rails orbits and sweeps them for collisions.

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

## Orbital Integration (planned)
The bubble is currently an inertial frame with no gravity. `documentation/orbital-mechanics/Design.md` defines how it becomes part of a 1:1 real-scale orbital world. The changes that land in this module:
- **Free-falling anchor.** The origin's double-precision universe state in `PhysicsManager` becomes an orbital anchor: Keplerian elements around the current SOI body, evaluated at sim time. A rebase converts the player's true state back into new anchor elements instead of just copying position and velocity.
- **Tidal gravity.** World gravity stays zero. Each fixed tick, before the step (alongside `FlightControlSystem.beforeStep`), every dynamic body gets `g(R_anchor + r) − g(R_anchor)`, computed in doubles. Roughly 0.1 m/s² at 25 km in low orbit.
- **Bubble size.** Rails bodies enter at 20 km from the anchor and leave at 25 km. Entry requires relative speed below about 1,000 m/s; faster bodies are kinematic transients (rendered and swept for collisions, never Box2D bodies) until they slow down.
- **SOI changes.** When the player crosses into another body's sphere of influence, the anchor is re-parented. Box2D bodies do not move.
- **Time warp.** Physics warp (1× to 10×) runs more fixed ticks per frame. Rails warp (50× and up) collapses the bubble: every body, the player included, goes onto rails and Box2D idles until warp ends.


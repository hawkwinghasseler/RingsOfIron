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

## Implemented Components
- `PhysicsBodyComponent`: Holds the Box2D `Body`.
- `ThrusterComponent`: force, torque and fuel limits.
- `FlightControlComponent`: stick input in [-1, 1] and rotation assist. When `towing` names a second body welded to the ship, thrust is applied at the pair's combined centre of mass and rotation assist uses the pair's combined inertia, so a towed prize neither spins the ship nor shakes the weld.
- `BodyShapeComponent`: a body's fixtures and exact mass data, kept while it is on rails or in a save.
- `KinematicTransientComponent`: a fast-pass body's true trajectory and sweep radius.

### Propellant feed
A ship whose layout has an engine (`ENGINE`, see Tanks and Pipes in `documentation/interior-grid/Design.md`) pays for thrust in propellant. Core's `PropellantFeed` runs each frame after the interior systems: it sets `ThrusterComponent.fuel` to what the propellant tanks piped to the ship's working engines hold, and `fuelCapacity` to what all its propellant tanks hold full, and the next frame takes what flight control burned out of those tanks. Thrust costs force / exhaust velocity in kg/s, and torque costs torque / (3 m x exhaust velocity). The exhaust velocity is the ship's `exhaust_velocity` in `ships.toml`, 100 km/s when left out. Planned burns (the maneuver planner's and in-bubble burns) pay by the rocket equation through `PropellantFeed.payForBurn`, and a burn the tanks cannot cover is cut short. A ship with no engine in its layout keeps free thrust. The ship's mass counts the propellant aboard, so it gets lighter as it burns (see Tanks and Pipes).

## Implemented Systems
- `PhysicsUpdateSystem`: Steps Box2D and syncs its state to the ECS.
- `FlightControlSystem`: turns stick input into thrust before each step.
- `FloatingOriginSystem`: Monitors player distance and relative speed and rebases the origin.
- `TidalGravitySystem`: applies the differential gravity across the bubble each tick.
- `KinematicTransientSystem`: moves bodies passing faster than the Box2D cap along their true trajectories and sweeps them for collisions.

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

## Orbital Integration
Built so far (see `OrbitalBubbleTest`):
- **Anchor.** `PhysicsManager` keeps the origin's true state through an `AnchorModel` (package `anchor`): `InertialAnchor` by default (no gravity), `KeplerAnchor` (an exact conic from `orbital-mechanics`' `KeplerOrbit`, the one to use in the game), or `PointMassAnchor` (RK4 free fall, kept as an independent check). Swap one in with `setAnchorModel`. A rebase calls `AnchorModel.reset` with the player's true state, so a `KeplerAnchor` restarts on the conic through it.
- **Sim time.** Under a `GameEngine`, `PhysicsUpdateSystem` hands the engine's `SimClock` to `PhysicsManager`, so physics, rails and the anchor share one tick. After rails warp, `catchUp` moves the anchor to the new time. Without a `GameEngine`, `PhysicsManager` counts its own ticks.
- **Saving.** `PhysicsSaveParticipant.install(physics, gameEngine)` skips the live `PhysicsBodyComponent` and `KinematicTransientComponent`, and saves the anchor's true state and each bubble body's parent-relative true state, angle and spin by UUID. It also saves each body's fixtures and exact mass data as a `BodyShapeComponent` snapshot. The anchor's parent and mu are saved too. On load, the anchor model restarts from the saved state; if its mu differs from the saved one (a fresh world on its inertial default, or a save made around another body), it is replaced by a `KeplerAnchor` through the saved state, and the change is announced so rails tracking follows it. Each body is rebuilt on its entity from the snapshot and placed at its saved state, so the ship's assembler does not run again and macro-sim's freighters come back as well. Saves made before snapshots existed hold only states; each one is applied once something else rebuilds that entity's body.
- **Ships from data.** `ShipPhysicsAssembler.register(ctx, physics)` (in `ModdingBootstrap.beforeScripts`) gives `[ship.*]` spawns a body weighing the def's mass, and thrusters from `max_thrust` and `max_torque`. In the game a ship with engines in its layout then has its main engine set by them instead (core's `EngineThrust`): each `ENGINE` system adds its component's `thrust` for its size in `components.toml`, or the standard 1.2, 3.6, 12 or 36 MN for S to XL, scaled down by its damage, and none while it is unpowered, switched off or disabled. Retro and lateral thrust stay the usual fractions of the engines' undamaged sum, unless the ship has RCS blocks on its hull (core's `RcsThrust`, below). `max_thrust` is the fallback for ships without engines. The corvette's two Small engines make its 2.4 MN and the freighter's two Medium freight drives (1.5 MN each) its 3.0 MN, as before. Its shape comes from a `HullSource` when one is registered and has a shape for the ship, else from the def's `hull` polygon, else a circle. The game's source (`ShipHulls` in core) uses the collision layer of the ship's hull art when it has one, otherwise the layout: interior-grid's `HullShape` covers every tile that is not void with a few boxes, and each wedge with its triangle. A shape of several convex pieces becomes one body with a fixture for each (`createPolygonBody` with a list), weighed on all of them together and inset by the skin like a single polygon. Stations' kinematic bodies (`StationBodies`) are built from the same `HullShape` pieces.
- **Tidal gravity.** `TidalField.accelAt(localX, localY, out)` (from `physics.getTidalField()`) is the one shared field; `TidalGravitySystem` applies it to every dynamic body before each step. A box released 2 km from the anchor in a 400 km orbit stays within 1 m of its own two-body orbit over a quarter orbit, rebases included. In this non-rotating frame the radial term is 2·n²·d (about 0.06 m/s² at 25 km in low orbit); Hill's 3·n²·d includes the centrifugal term of a rotating frame.
- **Fast passes.** `KinematicTransientComponent` holds a `TrueTrajectory` (any `stateAt(t)`, e.g. a rails orbit) and a sweep radius. `KinematicTransientSystem` places it each tick from the trajectory minus the anchor, in doubles, sweeps rays from last tick's position (center line plus ±radius), and fires one `FastPassImpactEvent` per body passed through on `physics.fastPassImpacts`. It gets no Box2D body unless the system's `BodyFactory` builds one (stations-docking's `StationBodies` does for stations): such a body is kinematic, created when the component is added and destroyed when it goes, and each tick it is placed exactly on the trajectory and given the velocity that carries it to the next tick's point, so dynamic bodies can rest against it and be welded to it. It is not swept. The handoff owner removes the component to promote it or send it back to rails. Collar fixtures carry `CATEGORY_COLLAR` on top of their owner's category.
- **Handoffs.** `getTrueState(body, out)` and `setTrueState(body, x, y, vx, vy)` convert between Box2D and parent-relative doubles; `trueToLocal` for anything else. `BubbleLimits` holds the enter/exit radii and speed limits.
- **Combat and grid helpers, in SI.** `rayCast` and `queryAABB` in meters, `applyImpulse` and `applyForce` at a point, `getPreviousPose(body, out)` for swept hit tests (kept in step with rebases), and `setMassData` for mass, center of mass and inertia from `ShipGridComponent.computeMassProperties`. `StepListener.afterStep` runs after each step, before collisions are dispatched.

**RCS.** A ship whose layout bolts RCS blocks onto its hull (`rcs` in `chassis.toml`; interior-grid's `RcsThruster`) turns, strafes and backs up on them: core's `RcsThrust` runs after `EngineThrust` and sums each working nozzle's push (away from its exhaust) into retro thrust, port and starboard thrust, and counter-clockwise and clockwise torque about the body's centre of mass, and sets the ship's `reverseThrust`, `lateralThrust` (the weaker side) and `maxTorque` (the weaker way) from them. A block stops when its hull tile is destroyed, so a ship that loses its bow blocks turns slower. Flight control still pushes through the centre of mass; the blocks set how hard. `RcsThrust.firing` says which nozzles fire for the pilot's controls (and for rotation assist stopping a spin), which the exterior art draws as puffs and the lighting turns into light. The ships' blocks are tuned to keep what they flew on before: their `max_torque`, and a quarter of their engines' thrust sideways and 40% backing up. Every off-centre nozzle can turn the ship, so the blocks that back it up sit on the bow by the centreline; the freighter's docking port fills that spot, so it backs up at about 40% of what it did.

The flight demo shows all of it, on the game's own world and corvette (`FlightDemoScene`, drawn by the game's `ExteriorRenderer` and `DeckRenderer`): `[O]` puts the bubble in a 400 km orbit around the Earth, `[,]`/`[.]` step the game's physics warp, `[F]` sends a 2.5 km/s fast pass past the ship, and `[V]` shows the corvette's deck, whose grid gives the ship its mass. The corvette's RCS blocks puff as they fire, so turning, strafing and backing up show on the hull. The asteroids, the reference grid and the 5-second velocity line are the demo's own.

- **Rails warp.** `RailsBubbleHandoff` implements orbital-mechanics' `BubbleHandoff`. Collapsing gives every body, the player's included, an `OrbitComponent` around the anchor's parent (`PhysicsManager.getAnchorParentUuid`) and a `BodyShapeComponent` holding its fixtures and exact mass, then drops the Box2D body. Rebuilding re-anchors a `KeplerAnchor` on the player's orbit and brings back every recorded body within the entry radius and speed; the rest stay on rails, shape kept, for macro-sim's `BubbleTransitionSystem` to bring in later with `rebuild(entity)`.

- **SOI changes.** `OrbitalAnchorBridge(physics, orbitalEventSystem)` keeps both sides' anchor in step. Each `anchorChanges` event is forwarded: SET and REBASED call `OrbitalEventSystem.setAnchor` with the anchor's conic (a non-Kepler anchor around a named body is fitted with the conic through its current state), and CLEARED calls `clearAnchor`. When the event system reports the anchor crossing an SOI boundary, the bubble adopts the new parent and conic. Box2D bodies keep their local positions, and the change is not echoed back to rails.

The original plan, for reference:
The bubble was an inertial frame with no gravity. `documentation/orbital-mechanics/Design.md` defines how it becomes part of a 1:1 real-scale orbital world. The changes that land in this module:
- **Free-falling anchor.** The origin's double-precision universe state in `PhysicsManager` becomes an orbital anchor: Keplerian elements around the current SOI body, evaluated at sim time. A rebase converts the player's true state back into new anchor elements instead of just copying position and velocity.
- **Tidal gravity.** World gravity stays zero. Each fixed tick, before the step (alongside `FlightControlSystem.beforeStep`), every dynamic body gets `g(R_anchor + r) − g(R_anchor)`, computed in doubles. Roughly 0.1 m/s² at 25 km in low orbit.
- **Bubble size.** Rails bodies enter at 20 km from the anchor and leave at 25 km. Entry requires relative speed below about 1,000 m/s; faster bodies are kinematic transients (rendered and swept for collisions, never Box2D bodies) until they slow down.
- **SOI changes.** When the player crosses into another body's sphere of influence, the anchor is re-parented. Box2D bodies do not move.
- **Time warp.** Physics warp (1× to 10×) runs more fixed ticks per frame. Rails warp (50× and up) collapses the bubble: every body, the player included, goes onto rails and Box2D idles until warp ends.


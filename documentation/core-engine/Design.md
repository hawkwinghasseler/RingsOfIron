# Core Engine & ECS Module Design

## Overview
The `core-engine` module is the foundation of the Space Battle Sim. It defines the main game loop, manages the lifecycle of all entities through an Entity-Component-System (ECS), and handles the persistence (save/load) of game state.

## Implementation Dependencies
- **Independent Module:** This module sits at the very bottom of the dependency graph. It does not depend on any other game modules.
- **External Libraries:** Depends heavily on LibGDX and LibGDX's Ashley ECS extension, plus a JSON library (e.g., LibGDX's native Json or Jackson).

## Implementation Guide
To implement this module independently:

1. **ECS Initialization:** 
   - Create a central `GameEngine` class that instantiates an Ashley `PooledEngine`.
   - Expose methods to register `EntitySystem` instances and add/remove entities.

2. **Game Loop:**
   - Implement a fixed-timestep accumulator in the main render loop.
   - Separate systems into two categories: `FixedLogicSystem` (runs on fixed tick, e.g. 60hz) and `RenderSystem` (runs every frame with interpolation alpha).
   - **Sim time** is a single authoritative `long tick` counter at 60 Hz, owned by `GameEngine`. Seconds are derived as `tick / 60.0` (double) when needed, e.g. by orbital code. Never accumulate game time in a float.
   - **Time warp** (see `documentation/orbital-mechanics/Design.md` section 6): physics warp (1× to 10×) runs more fixed ticks per frame. Rails warp (50× to 1,000,000×) advances the tick counter in large jumps; per-tick systems such as interior hazards and crew AI must not be run once per skipped tick but get a coarse catch-up update instead. Rails warp is refused while any fire or atmosphere leak is active aboard the player's ship.

3. **UUID System Setup:**
   - Create a `UuidComponent` containing a `String id`.
   - Create a `UuidRegistry` (singleton or passed via dependency injection) that maintains a `Map<String, Entity>` for O(1) lookups.
   - Implement an `EntityListener` added to the Ashley Engine that automatically registers entities with a `UuidComponent` when added, and removes them from the map when removed.

4. **Serialization Architecture:**
   - Create a `SerializationService`.
   - Save the sim `tick`. Bubble bodies are saved as absolute double state vectors relative to their SOI parent, never as bubble-frame floats, so a save does not depend on where the floating origin was.
   - When saving: Iterate over all entities, skip entities marked `TransientComponent`, serialize their components to JSON, replacing direct object references with UUID strings.
   - When loading: Parse JSON, recreate entities, and run a secondary pass to resolve UUID strings back into live Entity references where necessary.

## Proposed Components
- `TransformComponent`: Base position/rotation `x, y, rotation` in the **bubble frame** (metres, floats, relative to the floating origin). True universe positions are not stored here: they live in `OrbitComponent` for bodies on rails, or in the bubble anchor plus Box2D state for bodies in the bubble.
- `UuidComponent`: `String id`.
- `PlayerControllerComponent`: Empty tag component identifying the player's primary actor.

## Proposed Systems
- `FixedLogicSystem`: Abstract base class implementing the fixed accumulator logic.
- `SerializationSystem`: Handles traversing the ECS and converting components to/from JSON maps.

## Implemented API (prototype)
What other modules can code against today, all in `com.ringsofiron.coreengine`:

- **`SimClock`**: `long getTick()`, `double getSimSeconds()`, `TICKS_PER_SECOND = 60`, `TICK_SECONDS`. `GameEngine` implements it.
- **`GameEngine.update(frameDelta)`**: one rendered frame. Runs one fixed tick per 1/60 s of accumulated real time (times physics warp, at most 15 ticks per frame per warp step, frame delta capped at 0.25 s), then one normal Ashley pass for every other system. `stepTicks(n)` runs ticks with no frame pass, for headless sims and tests.
- **`FixedLogicSystem(priority, timeStep)`**: `timeStep` is rounded to whole ticks (0.25 s = every 15 ticks, 1 s = every 60). Under `GameEngine` the shared tick drives all fixed systems in priority order and other systems are paused during ticks. Override `catchUp(ticks, seconds)` for rails warp. In a plain Ashley `Engine` it still runs on its own accumulator, so existing demos and tests keep working.
- **`RenderSystem`**: `render(delta, alpha)` once per frame with interpolation alpha.
- **Time warp**: `setPhysicsWarp(1..10)`; `advanceRails(ticks)` jumps the tick and calls each fixed system's `catchUp` once. Called during an update, the jump applies right after that frame. Refused (returns false) while any `RailsWarpGuard` returns a reason; `getRailsWarpBlockedReason()` gives it for the HUD. `TimeWarpController` (orbital-mechanics) decides when to warp and handles the bubble.
- **Entity references**: `EntityRef` (a UUID string; `EntityRef.of(entity)`, `ref.get(registry)`). Components must not hold raw `Entity` fields; saving one throws. `createEntityWithUuid()` and `getEntity(uuid)` are on `GameEngine`.
- **`SerializationService`**: `saveGame()` / `loadGame(json)` (plus file variants). Format: `{version, tick, entities: [{<component class>: {fields}}], modules: {<key>: {...}}}`. Skips `TransientComponent` entities and classes passed to `skipComponent`. `registerSerializer` for custom types, `SaveParticipant` for module-global state (read after entities, so refs resolve). Unknown component classes and dangling refs are skipped and listed in `getLastLoadWarnings()`.

Note: Ashley keeps one system per class, so adding a second instance of the same system class replaces the first.

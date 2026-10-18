# Macro Economy & Factions Module Design

## Overview
The `macro-sim` module is the backbone of the "living universe". It tracks commodity supply/demand across star systems and simulates the physical movement of thousands of NPC ships outside of the player's localized Box2D physics bubble. Movement follows `documentation/orbital-mechanics/Design.md`: every body outside the bubble is on a Keplerian orbit at 1:1 real-world scale.

## Implementation Dependencies
- **`core-engine`**: Uses the Ashley ECS, but heavily leverages background threads.
- **`orbital` math package** (shared with `physics-exterior`): `stateAt(t)`, `elementsFrom(r, v)`, Kepler solver, SOI prediction.
- **`physics-exterior`**: **Hard Dependency for Transitions.** The system needs the bubble's orbital anchor state (kept by `PhysicsManager`) to handle the injection of macro-ships into the active physics bubble when they get too close.

## Implementation Guide
To implement this module independently (mocking the Box2D transition logic):

1. **On-Rails Movement and Background Prediction:**
   - Every macro entity carries an `OrbitComponent` (2D Keplerian elements around a parent body, with an epoch). Its position at any sim time is `stateAt(t)`, computed analytically in doubles. Nothing is stepped per tick, so there is no drift and time warp costs nothing extra.
   - Changes of course are events in a sim-time priority queue: maneuver nodes (impulsive burns), SOI transitions and predicted surface impacts. Elements change only when an event fires, on the main thread.
   - A dedicated `ExecutorService` handles the expensive *predictions*, not stepping: finding which bodies may enter the player bubble soon, the warp drop-out point for the next encounter, and positions for map/HUD rendering. It reads immutable snapshots of the elements and hands results back to the main thread at a tick boundary.
   - NPC AI plans travel as maneuver nodes (e.g. a transfer burn and a circularisation burn). When the NPC is in the player bubble, the same plan is flown by a controller with real thrust in Box2D.

2. **Economy Simulation:**
   - Create `EconomyNode` POJOs attached to stations. These contain maps of `CommodityType` to `InventoryQuantity`.
   - On a slow timer (e.g., every 5 minutes), run a pass to consume resources (e.g., population consumes food/water) and produce goods based on station type. Update base prices based on supply/demand curves.
   - Generate `TradeOrder` objects. Idle macro-freighters will pick up orders, set their destination, and travel.

3. **Bubble Transitions (Main Thread Sync):**
   - Create a `BubbleTransitionSystem` in the main ECS loop. Distances are measured to the bubble's orbital **anchor**, not to the player.
   - Candidates come from the background prediction (bodies that could reach the bubble before the next check), so the main thread checks only a short list.
   - **Entry:** within 20 km of the anchor and relative speed below about 1,000 m/s: evaluate `stateAt(t)`, subtract the anchor state in doubles, and create the Box2D body at that relative position and velocity. The entity and its UUID persist; only the physics representation changes. Its pending rails events are cancelled (maneuver nodes become AI goals).
   - **Fast pass:** within 20 km but faster than about 1,000 m/s: the body becomes a kinematic transient (drawn and swept for collisions by `physics-exterior`) and stays on rails until it slows down or leaves.
   - **Exit:** beyond 25 km from the anchor: convert the Box2D state back to absolute doubles, compute new orbital elements, destroy the body, and enqueue the body's next rails events. The 5 km gap between entry and exit stops bodies flipping at the boundary.

## Proposed Components
- `OrbitComponent`: Keplerian elements, parent UUID and epoch for every body outside the bubble.
- `ManeuverNodeComponent`: Scheduled impulsive burns (time, prograde/radial Δv).
- `EconomyNodeComponent`: Attached to celestial bodies/stations, holding inventory maps.

## Proposed Systems
- `OrbitalEventSystem`: Fires due events from the sim-time queue (maneuver nodes, SOI transitions, impacts).
- `EncounterPredictionTask`: Background predictions for bubble entry, warp drop-out and map rendering.
- `BubbleTransitionSystem`: Moves ships between the background math simulation and the active Box2D simulation.

## Prototype Status
The first prototype lives in `macro-sim/src/main/java/com/ringsofiron/macrosim`. Run `gradlew :macro-sim:runMacroDemo` (optionally `-Pdays=60`) for a headless 30-day report, and `gradlew :macro-sim:test` for the checks.

- **Rails** are reached through the `RailsModel` interface. `StubRailsModel` and `StubKeplerOrbit` implement it with a one-level Kepler model (no SOI changes) until `orbital-mechanics` lands; then an adapter over `OrbitComponent`/`OrbitalEventSystem` replaces them.
- **Economy:** `EconomySimulation` runs the 5-minute pass (production cycles limited by inputs and storage, daily upkeep, price curve `base · (target / stock)^0.7` clamped to 0.25–4×) and rebuilds the `TradeOrder` board. Definitions come through `CommodityCatalog`: `DefRegistryCommodityCatalog` reads modding-data's `CommodityDef`/`StationTypeDef` (`assets/data/economy.json` plus mods, one def cycle = one production cycle, 4 per day), and `DefaultCommodityCatalog` is a small built-in set used by the demo and tests.
- **Freighters:** `FreighterController` scores open orders by profit per hour, then flies each leg as maneuver nodes from `TransferPlanner` (phased Hohmann transfer, or a phasing orbit on the same radius) and loads or unloads on arrival. Routes are within one parent body for now.
- **Bubble:** `EncounterPredictionTask` (background `ExecutorService`) culls by radius band and then uses conservative advancement, so it cannot step over an encounter; its earliest entry is the warp drop-out time. `BubbleTransitionSystem` does entry, fast pass and exit against the anchor through the `BubbleGateway` interface: `PhysicsBubbleGateway` is the real one over physics-exterior (`setTrueState`/`getTrueState`, `KinematicTransientComponent`, body building via a `BodyBuilder` that `RailsBubbleHandoff.rebuild` can fill), and `MockBubbleGateway` is a Box2D-free stand-in for the headless demo. Stations always stay on rails and show in the bubble as kinematic transients.
- **Time:** `MacroSimSystem` is a `FixedLogicSystem` on core-engine's `SimClock`; each tick and each rails-warp `catchUp` calls `MacroSimulation.advanceTo(simSeconds)`, which fires rails and macro events in time order, so one rails-warp jump gives the same economy as many small steps.

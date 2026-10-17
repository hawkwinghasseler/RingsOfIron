# Macro Economy & Factions Module Design

## Overview
The `macro-sim` module is the backbone of the "living universe". It tracks commodity supply/demand across star systems and simulates the physical movement of thousands of NPC ships outside of the player's localized Box2D physics bubble.

## Implementation Dependencies
- **`core-engine`**: Uses the Ashley ECS, but heavily leverages background threads.
- **`physics-exterior`**: **Hard Dependency for Transitions.** The system needs to know where the Box2D origin is to handle the injection of macro-ships into the active physics bubble when they get too close.

## Implementation Guide
To implement this module independently (mocking the Box2D transition logic):

1. **Background Simulation Thread:**
   - Create a dedicated `ExecutorService` or thread loop.
   - Maintain a thread-safe data structure representing "Macro Entities" (ships not currently in the player bubble).
   - This loop recalculates ship positions mathematically: `position += velocityVector * deltaTime`. Because it is purely mathematical, it can update thousands of ships per tick.

2. **Economy Simulation:**
   - Create `EconomyNode` POJOs attached to stations. These contain maps of `CommodityType` to `InventoryQuantity`.
   - On a slow timer (e.g., every 5 minutes), run a pass to consume resources (e.g., population consumes food/water) and produce goods based on station type. Update base prices based on supply/demand curves.
   - Generate `TradeOrder` objects. Idle macro-freighters will pick up orders, set their destination, and travel.

3. **Bubble Transitions (Main Thread Sync):**
   - Create a `BubbleTransitionSystem` in the main ECS loop.
   - Periodically check the distance of all Macro Entities against the player's world position.
   - **Entry:** If `distance < BUBBLE_RADIUS` and the entity is not in the ECS: 
     - Lock the macro data structure briefly, remove the macro entity, and spawn a full ECS Entity with a Box2D `PhysicsBodyComponent` at the calculated relative coordinate.
   - **Exit:** If a Box2D ship flies beyond the `BUBBLE_RADIUS`:
     - Destroy its Box2D body and ECS entity, and push its state back into the background macro data structure.

## Proposed Components
- `MacroFleetTrackerComponent`: A tag on the player indicating they are the anchor for bubble checks.
- `EconomyNodeComponent`: Attached to celestial bodies/stations, holding inventory maps.

## Proposed Systems
- `BackgroundSimulationThread`: Updates positions of non-instantiated ships.
- `BubbleTransitionSystem`: Moves ships between the background math simulation and the active Box2D simulation.

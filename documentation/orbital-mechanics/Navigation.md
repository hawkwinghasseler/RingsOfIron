# Navigation and Flight Aids

Round 3, category 2. The player can pick a navigation target, see the closest approach to it on the orbit map, have a transfer to it planned with the same planners the freighters use, warp to the next burn, and give the helmsman orders to fly while away from the helm. Docking guidance starts 5 km out, and known rails pirates show on the traffic map. Everything hangs off `Navigation`, an `EntitySystem` installed in `GameWorld` as `world.nav`.

## Implemented

### Navigation target (`NavTarget`)
- Q (`Action.NAV_TARGET`) cycles through the candidates and Shift+Q goes back; the list wraps round to "none". A click on a contact on the traffic map (`TrafficMap.setPickListener`, within 14 px) also selects it.
- Candidates, in order: active contract waypoints, every station, pirates the player knows of, marked craft in the bubble, then the freighters on the rails. The player's own ship is never one.
- The target is stored by uuid on the ship in a `NavTargetComponent`. A target whose entity is gone is cleared on the next read.
- `NavTarget.stateAt` places a rails craft from its orbit and a bubble craft by carrying it along the conic it is on now.

### Closest approach and the orbit map
- `ClosestApproach.find` samples the player's path (the current conic, then the conic after each later burn) against the target 360 times, then refines with a golden-section search. The path uses the proposal when one is waiting, otherwise the planned burns.
- The search runs one period of the last conic (at most 30 days), or 2 days on an open orbit, and is redone at most every 0.25 s.
- `PlayerOrbitPath` now adds stations around the same body as small squares, the target as a larger magenta square (pulled to the map edge when off it), the target's orbit when it is elliptic and shares the parent, a line between where ship and target will be at closest approach, and a proposed transfer in green. The caption gives "closest X in T" and the navigation keys.
- The screen readout (`Navigation.readout`) shows the target's name, range and closing or opening speed, and the closest approach with its time and relative speed.

### Plan to target (`PlayerTransfer`)
- I (`PLAN_TO_TARGET`) plans with `TransferPlanner` and `RoutePlanner` from the macro sim: Hohmann or phasing around the same body, or out to the Moon. A ship with eccentricity over 0.01 circularizes first and the transfer starts 60 s later. The first burn is at least 300 s away (`LEAD`).
- The plan is a `Proposal` drawn on the orbit map until Enter (`ACCEPT_PLAN`) accepts it, which replaces every planned burn through the new `BurnPlanner.replace`, or Backspace (`DISCARD_PLAN`) throws it away. After accepting, the player edits the burns with the usual burn planner.
- A leg to another body's station plans only the burn out of the current sphere of influence. Once the ship is in the next one with no burns left, `PlayerTransfer.update` proposes the capture and the rest by itself through `RoutePlanner.afterCrossing` and posts a notice.
- A target in the bubble is not planned to; the player is told to fly there or order an approach.

### Warp to burn and the burn marker
- / (`WARP_TO_BURN`) sets rails warp by how far off the next burn is: 100,000x past 2 days, 10,000x past 3 hours, 1,000x past 10 minutes, otherwise 100x. `Navigation` is a `DropOutSource` and stops warp `WARP_LEAD` (60 s) before the burn. It refuses when the burn is 5 s or less away.
- While the helm is ordered to fly burns, warp always drops out in time for that, with an extra `HelmOrders.TURN_LEAD` (120 s) plus half the burn time.
- The readout shows "Next burn in T: N m/s". `NavOverlay` draws an amber arrow and ring from the ship along the next burn on the outside view.

### Helm autopilot (`HelmOrders`)
- Keys: 1 fly the planned burns, 2 match the target's velocity, 3 hold position relative to the target, 4 approach the target, 0 cancel. Pressing 4 again steps the distance off the hull through 500, 1,000, 2,000 and 250 m. The order lives in a `HelmOrderComponent` on the ship.
- An order needs a conscious crew member aboard other than the player. It is flown by `Bridge` only while a helmsman has the helm and the player is not at it; otherwise the readout says it waits. An order nobody comes to fly for 60 sim seconds (`UNMANNED_LIMIT`) is dropped.
- Match, hold and approach work only in the bubble and fly relative to the target's Box2D body when both are there. Approach is refused beyond 50 km and closes at most 50 m/s, slow enough to brake on half the weaker of main and retro engines. The hull distance counts from half the target's deck-plan diagonal. Hold closes at 0.1 m/s per meter off, at most 5 m/s.
- Flying burns: the helmsman turns along the burn 120 s ahead, removes the node so it no longer fires as an impulse, and fires the main engine centered on the burn time until within 0.05 m/s, only with the nose within 5 degrees. Running out of propellant cuts the burn short with a warning. While a burn is being flown rails warp is blocked.
- The autopilot never fires weapons. A shot at the ship, a hit on it, or the target vanishing drops the order with a warning notice.

### Docking approach guidance
- `PlayerDocking.READOUT_RANGE` is now 5,000 m instead of 500 m.
- A new readout line gives the safe closing speed, sqrt(2 x 0.1 m/s² x distance) clamped to 0.5 to 50 m/s, with "SLOW DOWN" when the ship is faster.
- `NavOverlay` draws an approach corridor out from the target port's face (new `DockingSystem.Approach.faceX/faceY/normalX/normalY`), widening with distance and marked every tenth: green within the safe speed, amber up to 1.5 times it, red past that. Port alignment remains the existing "turn N deg" readout.

### Pirates on the traffic map (`PirateSightings`)
- A rails pirate is seen while within 300 km (`SENSOR_RANGE`) of the player or of a sponsor-faction patrol on the rails. A pirate the player holds a bounty on is known as soon as the contract is taken.
- `RailsTrafficMap` adds them as the new `ContactKind.HOSTILE`: red triangles, hollow and dimmed at a last known position, with "your bounty" and "last seen ... ago" details. The header counts pirates. The navigation target is bracketed in magenta.

### Fuel estimate
- `PropellantFeed.deltaVLeft` uses the rocket equation on the propellant the working engines draw on; a ship with no engine counts as unlimited.
- The readout shows "Delta-v needed N / left M m/s", with "NOT ENOUGH PROPELLANT" when short. Needed is the proposal's total, or else the sum of the planned burns.
- `Navigation.returnDeltaV` estimates the delta-v to the nearest station that would give clearance (0 when docked at one), with `RoutePlanner.estimate`.

### Messages and hints
Navigation posts to `world.notices`: dropped orders and cut burns as warnings, the rest as info. Its keys are registered with `world.hints` in the helm and weapons console contexts, with accept and discard put first while a proposal waits. Messages name keys from the player's bindings.

## Saves
- `NavTargetComponent` and `HelmOrderComponent` are plain components on the player's ship, so the serialization service writes them field by field: the target, the order, its target, approach distance, hold point, and a burn in progress with its remaining delta-v.
- Not saved: a waiting proposal, the pending continuation of a multi-body leg, the warp-to-burn flag, and `PirateSightings`. After a load pirates are known again when seen, and bounty targets at once.

## Tests
`NavigationTest` covers:
- closest approach of two circular orbits and of a Hohmann transfer meeting its target;
- cycling targets through the stations and back to none;
- planning to the depot (2 burns, about 900 m/s), the proposal on the orbit map, accepting and discarding;
- planning to the lunar mine as one trans-lunar burn of about 3,100 m/s with the Moon next, and refusing a bubble craft;
- helm orders needing a target and a conscious crew; the helmsman approaching Highport and holding at 1,000 m off the hull; matching velocity; flying a planned burn on the main engine to the impulse's apoapsis within 3 km; a shot dropping the order with a warning;
- the target and the order surviving save and load;
- pirates within sensor range appearing on the traffic map and stations being pickable;
- warp to burn stopping 60 s short; the fuel estimate and return delta-v; the docking safe-speed curve and 5 km range.

## Choices made without Joe
- **The target key is Q**, not T from the plan. The branch first used O, then deck lights took O.
- **Enter and Backspace** accept and discard a plan, and the digits 1 to 4 and 0 give helm orders.
- **Warp to burn stops 60 s short**, longer when the helm flies the burns.
- **Approach distances** of 500, 1,000, 2,000 and 250 m off the hull, refused beyond 50 km.
- **Sensor range 300 km**, for both the player and sponsor patrols.
- **Safe closing speed** for braking at 0.1 m/s², between 0.5 and 50 m/s.
- **Unmanned orders drop after 60 s**, and a burn the helmsman takes over no longer fires by itself.
- All numbers are placeholders.

## Not built
- Nothing reads `returnDeltaV` yet; category 1's low-propellant warning does not use it.
- Only the next burn gets a countdown in the readout and a marker on the outside view; the orbit map caption still counts down only the selected burn.
- Target orbits for craft around another body are not drawn, though their closest approach is found.
- The autopilot does not fly rails legs, and no autopilot docks.

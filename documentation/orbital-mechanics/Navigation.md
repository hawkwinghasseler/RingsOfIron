# Navigation and Flight Aids

Round 3, category 2. The player can pick a navigation target, see the closest approach to it on the orbit map, have a transfer to it planned with the same planners the freighters use, warp to the next burn, and give the helmsman orders to fly while away from the helm. Docking guidance starts 5 km out, and known rails pirates show on the map. Everything hangs off `Navigation`, an `EntitySystem` installed in `GameWorld` as `world.nav`.

## Implemented

### Navigation target (`NavTarget`)
- Q (`Action.NAV_TARGET`) cycles through the candidates and Shift+Q goes back; the list wraps round to "none". A click on a contact on the map (`ManeuverMap.setPickListener`, within 14 px) also selects it.
- Candidates, in order: active contract waypoints, every station, pirates the player knows of, marked craft in the bubble, then the freighters on the rails. The player's own ship is never one.
- The target is stored by uuid on the ship in a `NavTargetComponent`. A target whose entity is gone is cleared on the next read.
- `NavTarget.stateAt` places a rails craft from its orbit and a bubble craft by carrying it along the conic it is on now.

### Closest approach and the orbit map
- `ClosestApproach.find` samples the player's path (the current conic, then the conic after each later burn) against the target 360 times, then refines with a golden-section search. The path uses the proposal when one is waiting, otherwise the planned burns.
- The search runs one period of the last conic (at most 30 days), or 2 days on an open orbit, and is redone at most every 0.25 s.
- `PlayerOrbitPath` now adds stations around the same body as small squares, the target as a larger magenta square (pulled to the map edge when off it), the target's orbit when it is elliptic and shares the parent, a line between where ship and target will be at closest approach, and a proposed transfer in green. The caption gives "closest X in T" and the navigation keys.
- The screen readout (`Navigation.readout`) shows the target's name, range and closing or opening speed, and the closest approach with its time and relative speed.

### Plan to target (`PlayerTransfer`)
- ; (`PLAN_TO_TARGET`, I on the navigation branch, moved to ; during integration) plans with `TransferPlanner` and `RoutePlanner` from the macro sim: Hohmann or phasing around the same body, or out to the Moon. A ship with eccentricity over 0.01 circularizes first and the transfer starts 60 s later. The first burn is at least 300 s away (`LEAD`).
- The plan is a `Proposal` drawn on the orbit map until Enter (`ACCEPT_PLAN`) accepts it, which replaces every planned burn through the new `BurnPlanner.replace`, or Backspace (`DISCARD_PLAN`) throws it away. After accepting, the player edits the burns with the usual burn planner.
- A leg to another body's station plans only the burn out of the current sphere of influence. Once the ship is in the next one with no burns left, `PlayerTransfer.update` proposes the capture and the rest by itself through `RoutePlanner.afterCrossing` and posts a notice.
- A target in the bubble is not planned to; the player is told to fly there or order an approach.

### Warp to burn and the burn marker
- / (`WARP_TO_BURN`) sets rails warp by how far off the next burn is: 100,000x past 2 days, 10,000x past 3 hours, 1,000x past 10 minutes, otherwise 100x. `Navigation` is a `DropOutSource` and stops warp `WARP_LEAD` (60 s) before the burn. It refuses when the burn is 5 s or less away.
- While the helm is ordered to fly burns, warp always drops out in time for that, with an extra `HelmOrders.TURN_LEAD` (120 s) plus half the burn time.
- The readout shows "Next burn in T: N m/s", then "Then burns in ..." for up to three burns after it (round 5). `NavOverlay` draws an amber arrow from the ship along every planned burn on the outside view (round 6, below).

### Helm autopilot (`HelmOrders`)
- Keys: 1 fly the planned burns, 2 match the target's velocity, 3 hold position relative to the target, 4 approach the target, 0 cancel. Pressing 4 again steps the distance off the hull through 500, 1,000, 2,000 and 250 m. The order lives in a `HelmOrderComponent` on the ship.
- An order needs a conscious crew member aboard other than the player. It is flown by `Bridge` only while a helmsman has the helm and the player is not at it; otherwise the readout says it waits. An order nobody comes to fly for 60 sim seconds (`UNMANNED_LIMIT`) is dropped.
- Match, hold and approach work only in the bubble and fly relative to the target's Box2D body when both are there. Approach is refused beyond 50 km and closes at most 50 m/s, slow enough to brake on half the weaker of main and retro engines. The hull distance counts from half the target's deck-plan diagonal. Hold closes at 0.1 m/s per meter off, at most 5 m/s.
- Flying burns: the helmsman turns along the burn 120 s ahead, removes the node so it no longer fires as an impulse, and fires the main engine centered on the burn time until within 0.05 m/s, only with the nose within 5 degrees. Running out of propellant cuts the burn short with a warning. While a burn is being flown rails warp is blocked.
- The autopilot never fires weapons. A shot at the ship, a hit on it, or the target vanishing drops the order with a warning notice.

### Dock order (round 5)
- 5 (`HELM_DOCK`) orders the helm to dock at the navigation target with the ship's selected port, from up to 50 km. The helmsman flies it the way freighters berth, through `BerthingSystem.pilot`. Far out, or while the target has no body in the bubble yet, the helm closes to 1,000 m off its hull as the approach order does. At a station it asks for clearance once in range and flies round the hull to the port it is given. At another ship it makes for the nearest free, intact port of the same size there without clearance (`BerthingComponent.uninvited`), so it docks without leave unless that ship armed its port. The order ends with "Helm: docked at X" when the ports latch.
- It is refused while docked, with no port or a torn collar, at a craft with no free port of the selected port's size, or beyond 50 km. Like every helm order it needs a helmsman and is dropped by a threat. Taking the helm makes it wait, and the controls the helmsman had set are cleared (`Bridge`). 0 cancels it.
- Its state (own port, the target's port, phase, status) is on `HelmOrderComponent`, so it is saved.

### Transfer autopilot (round 5, `Autopilot`)
- 6 (`AUTOPILOT`) flies a transfer to the navigation target under time warp. It accepts the waiting proposal, or plans one when nothing is planned. It refuses while docked or with nobody to take the helm.
- While engaged it sets the warp after every world update (`GameWorld.update`, outside the engine's update). It picks the level from the time to the next stop (the next burn, the next orbital event such as a sphere of influence crossing, or else the closest approach less 120 s): 1,000,000x past 20 days, then the warp-to-burn levels, physics warp 10x inside a minute, and 1x in the last 2 s. Burns fire on time as every planned burn does, and rails warp drops there.
- In the next sphere of influence it accepts the capture and the rest of the leg (`PlayerTransfer`) by itself. Once every burn is flown, with the target within 50 km in the bubble, it hands over to the helm's dock order (or approach, for a craft with no ports) and switches off. If the transfer misses, it says how far off the target is and switches off.
- A drop it can't account for holds it at 1x with a warning, until the player raises warp or presses 6. Holds come from the player slowing time, a guard vetoing warp, another craft coming within the encounter radius, or pirates about on an external drop. The target itself coming close is the arrival, not a hold. With the helm flying the burns, it leaves warp to `Navigation`'s lead before each burn.
- Its state is `AutopilotComponent` on the player's ship: the target, and whether it holds and why. It is saved.
- `TransferAutopilotTest` flies the Moon trip on it. From open space off Highport, it docks at Shackleton Ice after 4.3 days with one hold, for the raider near the start.

### Docking approach guidance
- `PlayerDocking.READOUT_RANGE` is now 5,000 m instead of 500 m.
- A new readout line gives the safe closing speed, sqrt(2 x 0.1 m/s² x distance) clamped to 0.5 to 50 m/s, with "SLOW DOWN" when the ship is faster.
- `NavOverlay` draws an approach corridor out from the target port's face (new `DockingSystem.Approach.faceX/faceY/normalX/normalY`), widening with distance and marked every tenth: green within the safe speed, amber up to 1.5 times it, red past that. Port alignment remains the existing "turn N deg" readout.

### Pirates on the traffic map (`PirateSightings`)
- A rails pirate is seen while within 300 km (`SENSOR_RANGE`) of the player or of a sponsor-faction patrol on the rails. A pirate the player holds a bounty on is known as soon as the contract is taken.
- `RailsTrafficMap` adds them as the new `ContactKind.HOSTILE`: red triangles, hollow and dimmed at a last known position, with "your bounty" and "last seen ... ago" details. The header counts pirates. The navigation target is bracketed in magenta.

### Fuel estimate
- `PropellantFeed.deltaVLeft` uses the rocket equation on the propellant the working engines draw on; a ship with no engine counts as unlimited.
- The readout shows "Delta-v needed N / left M m/s", with "NOT ENOUGH PROPELLANT" when short. Needed is the proposal's total, or else the sum of the planned burns. Plan to target refuses a plan the tanks can't cover ("Not enough propellant: N m/s left").
- Warship drives (the corvette, pirate corvette, light raider, gunship and frigate) run at an exhaust velocity of 700 km/s, set in `ships.toml` during integration. At the 100 km/s default the corvette had about 1,030 m/s against a 3,877 m/s trans-lunar burn; it now has about 7.2 km/s. `MoonTransferTest` flies the Moon plan at 1,000x, 10,000x and 100,000x warp.
- `Navigation.returnDeltaV` estimates the delta-v to the nearest station that would give clearance (0 when docked at one), with `RoutePlanner.estimate`. `returnPropellantKg` turns it into kg for `FlightWarnings` (round 6, below).

### Messages and hints
Navigation posts to `world.notices`: dropped orders and cut burns as warnings, the rest as info. Its keys are registered with `world.hints` in the helm and weapons console contexts, with accept and discard put first while a proposal waits. Messages name keys from the player's bindings.

## Saves
- `NavTargetComponent` and `HelmOrderComponent` are plain components on the player's ship, so the serialization service writes them field by field: the target, the order, its target, approach distance, hold point, and a burn in progress with its remaining delta-v.
- The rest of an accepted multi-body leg (`continueIn`, `continueTo`) is in `TransferLegComponent` on the player's ship, so after a save past the trans-lunar burn the capture is still proposed on reaching the Moon.
- The transfer autopilot's state is `AutopilotComponent`, and its target and the navigation target keep pointing at the same ship after a load (round 6).
- Not saved: a waiting proposal, the warp-to-burn flag, and `PirateSightings`. After a load pirates are known again when seen, and bounty targets at once.

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

### Round 5 display
- The orbit map labels every planned burn with its number and countdown (`OrbitPath.nodeLabels`, drawn by `OrbitMap`).
- A target on rails around another body has its orbit drawn about where that body is now: the lunar mine's from Earth orbit, or a planet station's from the Moon.

### Round 6
- **A marker for every planned burn.** `Navigation.plannedBurnMarkers` gives each burn still to come as a direction, delta-v and countdown, each after the first aimed on the orbit the burns before it leave (as the orbit map draws it). `NavOverlay` draws the next burn's arrow and ring as before, and each later one fainter and 22 px further out, all labelled "2: 3,084 m/s in 4m 10s" by their heads (`NavOverlay.drawLabels`, from the status pass).
- **Low propellant means short of home.** `FlightWarnings` warns when the tanks hold less than 1.25 times the propellant navigation's trip home takes, whatever share of the tanks that is, and re-arms at 1.5 times; the warning names what home takes. Without a finite estimate (no friendly station in reach) it falls back on a fifth of the tanks. The estimate is refreshed once a second, not every frame, since it plans a route to every station.
- **Targets across a save.** A ship keeps its UUID through a save and load (freighters on the rails and in the bubble, hostiles, the player), so the transfer autopilot and the helm hold their targets by UUID as before. `AutopilotTargetSaveTest` aims the autopilot at a freighter, saves, loads and flies its next burn, and checks the helm's approach to a ship in the bubble holds.
- **Seed 7's Moon trip** stopped because the ship was latched to Highport twice (carried home by its crew, then docked again by the script) and the first latch's weld stayed after undocking, holding the ship through its burn. `DockingSystem.dockInPlace` now lets go of a port's old latch, and `GameScenario.dockAt` does nothing when already docked there (stations-docking Design).

## Not built
- The transfer autopilot flies burns as impulses. It does not fly them on the main engine unless the helm's fly-burns order is also given.

### Every bubble burn flown with real thrust
Joe: "All maneuvers performed within the physics bubble should be flown with real thrust." `BubbleBurnSystem`, which fired a planned burn in the bubble as one impulse when nobody flew it, is gone.
- `HelmOrders.flyPlannedBurn` flies the next planned burn whatever the helm's order: turning onto it `TURN_LEAD` before it starts, then the main engine through it, centered on its time. The helmsman flies it ahead of any other order (the order resumes after). With the player at the helm, the flight computer flies it while their hands are off the stick (`Bridge.playerStick`); a key on the stick takes over, and the computer picks up again when it is let go.
- Nothing fires by itself. A burn nobody has started by its time is missed, with a warning; one nobody flies on for `UNFLOWN_LIMIT` (5 s) with nobody at the helm is cut short.
- Any rails warp drops out `WARP_LEAD` before the helm is to turn onto the next burn (`Navigation.nextDropOutTime`), and rails warp is refused from then until the burn is flown ("a planned burn is coming up"), so the burn is never fired on the rails as an impulse. The autopilot leaves warp to Navigation near every burn.
- The "fly burns" order still stands for the helmsman and reports when every burn is flown; a threat dropping an order no longer cuts a burn under way short.
- `GameInput` lets go of the player ship's stick each frame only while the player flies it, so a frame with no sim tick in it (above 60 fps) no longer draws a burn the helm flies with the engines cold.

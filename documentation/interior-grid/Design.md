# Interior Grid & Construction Module Design

## Overview
The `interior-grid` module handles the 2D tile-based internal structure of ships and stations. It defines how hull, walls, rooms, corridors, conduits and system mounts are laid out, and simulates the air, heat and fire inside them.

The construction rules here are the ones combat's damage model reads (`combat-sys/Design.md`, section 5, "Construction model the damage code reads"). The two documents must stay in agreement.

## Implementation Dependencies
- **`core-engine`**: Requires Ashley ECS to attach grid components to ship entities.
- **`physics-exterior`**: (Soft Dependency) When rendering the grid in world space, the interior tiles must be offset and rotated by the parent ship's Box2D transform. The grid logic itself runs purely in local coordinates. The grid's structural mass properties feed the ship body's mass and inertia.
- **`modding-data`**: (Hard Dependency) Chassis blueprints and the material table come from data. `InteriorGridData.toBlueprint(ChassisDef)` and `toMaterialTable(DefRegistry)` adapt the definitions. `InteriorGridData.install(ctx)`, run from `ModdingBootstrap.beforeScripts`, registers the "ship" assembler, which gives every ship spawned from data its grid, subsystem registry and one entity per installed system. `MaterialTable.createDefault()` remains for tests.

## Coordinate System
The grid uses **Box2D's orientation**. Tile [0][0] is the **bottom-left**, +x is the ship body's local +x and **+y points up**. Grid coordinates are body-local coordinates offset by `gridOriginLocal` and divided by `tileSize`, with no axis flip anywhere:

```
tile = floor((local − gridOriginLocal) / tileSize)
```

`gridOriginLocal` is the body-local position (meters) of tile [0][0]'s bottom-left corner. It comes from the chassis blueprint. Blueprint character maps are written top row first so they read naturally on screen; the loader stores text row `r` at `gridY = height − 1 − r`.

## Construction Model

### Tiles
Every tile has a floor type (`NONE`, `FLOOR`, `POWER_CONDUIT`), a wall type (`NONE`, `HULL`, `INTERNAL`), a **weight class**, a material id, a condition (`integrity`), and hazard state (floor covering and fuel, fire intensity, temperature, bulkhead seal, power). Air is not stored on tiles; each air-holding tile points at its **compartment** (see Atmosphere below).

### Floors and coverings
Every floor (including power conduits) is a **steel deck plate**. Steel does not burn. A `FloorCovering` can be laid on top of the deck: `CARPET` carries 1.5 kg of fuel per m². Fire burns the covering away and leaves the bare steel floor behind (`covering` returns to `NONE`).

The deck is also the pressure boundary under the room: in this top-down model the space beneath a deck is open space, so a damaged deck plate leaks to space like damaged hull.

### Furnishings
Furniture and decor (`Furnishing`, a kind of `FurnishingType` from `[furnishing.*]` in `assets/data/furnishings.toml`) stand anywhere on open floor, not on the tile grid: each belongs to the tile its middle is over (`Tile.furnishings`) but sits at an offset from that tile's middle (`offsetX`, `offsetY`, each in [-0.5, 0.5) tiles) and is turned by any angle (`rotation`, degrees counter-clockwise; at 0 its front faces -y). `Furnishing.at(type, x, y, rotation)` makes one with its middle at an exact deck point; `Furnishings.place` sets it down if its whole footprint is over floor (no wall, void or system mount under any of it), no thin wall or door runs across it, and it is clear of every other furnishing, except that anything can stand on a `flat` one (a rug). `Furnishings.at` finds the one under a point, `Furnishings.all` lists a deck's.

Each kind has a size (`width` across, `depth` front to back), a combustible mass (`fuel`), a price, and a `use` saying what the crew do with it in their downtime (`LOUNGE`, `GAME`, `MUSIC`, `SCREEN`, `EXERCISE`, `READING`, or `DECOR` for nothing), with `seats` and the morale an hour of use gives (`relax`). The game sells eighteen, from couches and beanbags to a pool table, an arcade cabinet and an aquarium (see ai-crew's downtime and stations-docking's yard).

A furnishing is fuel for a fire on its tile, alongside the covering (see Fire). A save writes each one with its kind's numbers, so it loads the same whatever the data says later.

### Two kinds of wall
- **Full-tile walls** fill a whole tile: `HULL` for the outer hull and `INTERNAL` for interior walls.
- **Edge walls** are thin walls on the boundary *between* two tiles, so a room can be divided without spending a tile on the wall. They are stored per edge on `ShipGridComponent`:
  - `edgeH[y][x]` is the bottom edge of tile (x, y); `edgeH[height][x]` is the top edge of the top row.
  - `edgeV[y][x]` is the left edge of tile (x, y); `edgeV[y][width]` is the right edge of the right column.
  - Each `EdgeWall` holds a weight class, material, integrity, and an optional bulkhead door (`hasBulkhead`, `bulkheadSealed`). An open door lets crew, air and fire through; a sealed door blocks like solid wall.
  - An edge wall can serve as hull: a floor tile whose outer edge carries an intact edge wall is sealed from the void beyond it.

### Weight classes
Every tile, edge wall and hull piece is **light, medium or heavy**. The class and material select a row in the material table:

| Stat | Used for |
|---|---|
| `mass` (kg per full tile) | Structural mass, centre of mass and inertia for the ship's Box2D body |
| `hitPoints` (J) | Energy needed to take the piece from integrity 1 to 0 |
| `armor` (J) | Penetration stripped from a round before the piece absorbs it |

An edge wall weighs `EDGE_WALL_MASS_FRACTION` (0.25) of a full tile of the same class. `ShipGridComponent.computeMassProperties(MaterialTable)` sums tiles and edge walls into mass, body-local centre of mass and moment of inertia about it. Installed systems carry their own mass and are not included.

### Multi-tile hull pieces
Hull and armor can be pieces bigger than one tile (`HullPiece`). A piece covers several tiles but is **one object with one integrity pool**: every covered tile is a `HULL` tile pointing at the piece, and `Tile.getIntegrity()` / `setIntegrity()` read and write the piece's pool. Destroying any part of a piece breaches all of its tiles.

- `BLOCK` fills its rectangular footprint; its faces are the tile edges.
- `WEDGE` is a right triangle. At rotation 0 its legs run along the footprint's left and bottom edges and its sloped face looks toward +x/+y. The standard piece is a **1×2 wedge** for sloped armor.
- Rotation is in counter-clockwise quarter turns (0–3) and swaps width and height on odd values.
- `faceNormal()` returns the sloped face's outward unit normal in body-local axes. Combat uses it for incidence and ricochet checks instead of the tile's square edge. A wedge is half solid, so it counts half a tile's mass per covered tile.
- Pieces go on void or plain hull tiles and may not overlap other pieces or mounts.

### System mounts
Ship systems install into fixed-size mounts (`MountFootprint`):

| Size | Primary mount (square) | Auxiliary / utility slot (rectangle) |
|---|---|---|
| S | 1×1 | 1×2 |
| M | 2×2 | 2×3 |
| L | 3×3 | 3×7 |
| XL | 5×5 | 5×12 |

- **Primary** systems (generators/reactors, shield emitters, turret mounts) use square mounts. **Auxiliary and utility** systems (life support, helm, weapons console, cargo, airlock, tanks, engines, escape pods) use rectangular slots, which can be placed in either orientation. Each `SubsystemType` declares which kind it needs, and a system cannot be installed in the wrong kind.
- A mount's footprint is its origin (bottom-left tile), size, kind and rotation. A rotation of 1 or 3 turns a rectangular slot on its side; on a square mount it only sets the system's facing.
- **Every tile in the footprint belongs to the installed system**, so a hit on any of them damages it. `Tile.mount` points at the footprint, and `MountFootprint.installed` at the system entity; `ShipGridComponent.subsystemAt(x, y)` is the tile→subsystem index combat uses.
- A footprint must lie entirely on interior floor, may not overlap another mount, and may not have an edge wall running through it.
- What crew cannot walk through is `CrewObstacles`: the footprints of the big systems (`SOLID`: reactor, shield emitter, turret mount, life support) and cargo bay tiles with goods stacked on them (`CARGO_FILL`). Consoles, airlocks, docking ports, escape pods and empty bays stay walkable. Per-part colliders from the art could refine this later; until then a whole footprint blocks or none of it does.

- **Escape pods** (round 2): `ESCAPE_POD` (`B` in a chassis map) seats 4, 8, 20 or 48 crew for sizes S to XL. A pod sits through the outer hull (`EscapePodLayout`): its hatch end, the end it faces (+y at rotation 0, turning like a docking port), overlaps the hull, whose tiles stay hull walls under it so the ship keeps its air, and the rest of the pod lies inside on interior floor. Beyond the hatch end it needs `HullMounts.CLEARANCE` (5) tiles of open space, as wide as the pod: no hull, no other system's mount, and no RCS block on the hatch end. The loader refuses a layout that breaks either rule, and checks the launch path once every mount is placed. A pod drawn in the rows faces the side with only space beyond it and gets the hull beside it in that row under its hatch; the data lists pods under `mounts` instead, so the hull under them is kept as drawn. Crew bound for a pod make for its bay tile behind the hatch (`EscapePodLayout.bayTile`). A pod is hard to penetrate (1.5e7, the same as an airlock). Crew reach one through an `EvacuateTask` (ai-crew), and the game launches it (`AbandonShip` in core; see `documentation/progression/Design.md`). A launched pod is switched off for good.
- **Crew tasks** (round 2): `CrewTask.Kind` gains `EVACUATE` (to a pod, priority 1000) and `BOARD` (a boarding party sweeping another craft, priority 30). Both are defined in ai-crew.

### Cargo bays
Cargo is physical: goods sit on the floor tiles of the ship's cargo bays (the footprints of its `CARGO` systems), not in an abstract number. `CargoHoldComponent` on the ship lists the bay tiles and the stack on each:

- Each bay tile holds one stack of a single commodity, piled to `STACK_HEIGHT` (2.5 m, a 3 m deck less headroom), so a 1 m tile holds 2.5 m³. The hold's capacity is the sum over its bay tiles; a tile already holding one good has no room for another.
- Loading tops up stacks of the same good, then starts new stacks on empty tiles in bay order; unloading takes from the newest stacks first. Each stack keeps its good's m³ and kg per unit.
- `addMassTo` adds the cargo to the structural mass properties as point masses at the loaded tiles, so a full aft bay moves the center of mass aft and raises the inertia. The game recomputes the ship's Box2D mass whenever `revision` changes.
- `InteriorGridData` gives every data-spawned ship with bays an empty hold, and `InteriorGridData.cargoBayVolume(defs, shipId)` sizes a ship's hold from its chassis without spawning it (used for rails freighters).
- The corvette has a 4 m hold aft of the crew spaces with two 3×2 bays, plus two 2-tile bays forward (40 m³), with the gas tanks in the stern behind it and the engines on the stern outside; the light freighter two 12×5 bays (300 m³). The deck view draws a crate on each loaded tile, colored by good and filled as far as the stack fills the tile.

## Damage, Leaks and Zero-Integrity Rules
Hull, wall and floor tiles, hull pieces and edge walls all take damage to their condition (`integrity`, 1 = intact).

**Leaks.** Below 50% condition a piece starts to leak air. `Tile.leakFactor(condition)` gives the leak as a share of the flow through an opening one tile wide with no wall at all:

| Condition | Leak |
|---|---|
| 50% and above | 0 |
| 50% → 25% | rises linearly from 0 to 0.5 |
| 25% → just above 0 | capped at 0.5 |
| 0 (destroyed) | 1.0, a full opening |

- A leaking **hull or internal wall tile** joins every air volume it touches across its edges (a room, space, the room on its other side).
- A leaking **edge wall** joins the tiles on its two sides, even while its door is sealed.
- A leaking **floor** joins its room to space.
- A sealed bulkhead on a tile stops that tile's leak.

**At integrity 0** a piece is destroyed and stops doing its job:
- A **`HULL`** tile (or hull piece) at 0 is a breach: `isHullBreach()` is true and it counts as open space, so `GridHazardSystem` vents the adjoining room on its next tick. Sealing a bulkhead on the breached tile patches it.
- **Field patches** (`LeakPatches`, round 4). A wall, hull tile, deck plate or thin wall between 0 and `LEAK_START` leaks without being a breach. The crew can patch such a leak to space from the ship's stores (`ShipGridComponent.patchKits`, 8 from the yard): the patch stops the leak (`leakFactor()` 0) but leaves the condition where it was, so the hull still needs a yard. It records the condition it went on at (`patchedAt` on the tile, hull piece or edge wall) and holds while the condition stays at or above that and below `LEAK_START`, so a new hit breaks it and a yard repair past `LEAK_START` takes it off. Patches and the kits left are saved. A yard's repair order restocks the kits at no charge. **Chosen while Joe was away.**
- An **`INTERNAL`** wall tile at 0 stops blocking fire, air and walking. It becomes an open tile with no fuel.
- An **edge wall** at 0 blocks nothing, whatever the state of its door.
- A **floor or conduit** at 0 is a hole to space and stops carrying power; `PowerRoutingSystem` routes around it.

**Crushed tiles** (collisions, combat-sys 5.12). A collision can crush a tile flat (`Tile.crushed`, or `HullPiece.crushed` for every tile of a piece; read through `isCrushed()`). A crushed tile at 0 is a breach whatever it was (`isHullBreach()` is true for it, so it vents and the crew can seal it like a hull breach), it is never open to crew (`isOpen()` false, sealed or not), and it is no longer part of the hull's outside: `HullShape` leaves crushed tiles and wedges out, and the game collides a hull with any crushed tile on its layout rather than its art's collision layer. Crushing a tile sets `ShipGridComponent.hullShapeDirty` (not saved) so the body is reshaped before the next physics step. A yard repair that takes a crushed tile back to whole rebuilds it (`uncrush`): it collides again and its corners go back where they were built.

**Dents.** `ShipGridComponent.dents` holds how far collisions have pushed each tile corner, in tiles, `{x, y}` for each of the `(width + 1) x (height + 1)` corners (null while undented; at most `MAX_DENT`, 4 tiles, from where it was built). The game draws the exterior hull art warped through it. `crushed` (per tile and piece) and `dents` are saved.

`Tile.isOpen()` is the single test for "crew and fire can be here" (floors and destroyed internal walls), and `Tile.holdsAir()` adds "and not sealed off by a bulkhead". `ShipGridComponent.canPass(x, y, nx, ny)` is the test for walking between two adjacent tiles (both open, no closed edge wall between them) and `canFlow(x, y, nx, ny)` the test for air, smoke and fire (both hold air, no closed edge wall). Hazards, power and crew pathfinding all use these.

## Damage states (round 5)
GDD section 3's short circuits, and tank bursts. `DamageStates` holds the rules; combat rolls them on each hit (see `documentation/combat-sys/Design.md` 5.8), and the crew answer them (see `documentation/ai-crew/Design.md`).

**Short circuits.**
- A damaging hit on a **powered** system can short it, with the component's `short_chance` (reactor 0.08, shield emitter 0.06, tanks, cargo and escape pods never, the rest 0.04), scaled down for a hit that takes less than 5% of its health. A hit on a powered conduit tile that leaves it standing shorts it with chance 0.05, scaled down for a hit that takes less than half its condition.
- A short **trips the circuit** it was on (its store empties at once) and **faults the part**: a shorted system (`SubsystemComponent.shorted`) is not operational and a shorted reactor feeds nothing; a shorted conduit tile (`Tile.shorted`) carries no power. Power routing leaves them out, so consumers drop or reroute.
- Its sparks light the tile, or a flammable tile beside it that air reaches, with chance 0.3.
- It stays faulted until the crew **reset** it (`ResetShortTask`), a repair takes the system back to full health, or a yard mends it. Failing all of those, its breaker **recloses by itself** after 45 s (`DamageStates.AUTO_RESET`), so a ship with nobody aboard, or a short nobody can reach, does not stay dark for good. **Chosen while Joe was away.**
- Like an ion stun, a short is not a ship knocked out: `ShipDisablement` doesn't call a ship with a shorted reactor `NO_POWER`, and First Play's `beaten` waits, while anything aboard is shorted. Guns a short has cut off still count toward a pirate's surrender. The flags are saved; the 45 s wait is counted again after a load.

**Tank bursts.**
- A hit that leaves a tank with anything in it below its `burst_threshold` (0.25) bursts it with its `burst_chance` (propellant 0.35, oxygen 0.3, nitrogen 0.2). Above the threshold a damaged tank only leaks.
- A burst destroys the tank and lets everything go at once. Oxygen and nitrogen go into the room its footprint stands in (300 kg of a gas is a spike that can blow a light door); an oxygen burst's sparks light a flammable tile with chance 0.5. Propellant pools on the deck of a pressurised room (at least 30% of standard): 2 kg a m² on the footprint and the room's tiles round it, as `Tile.fuel`, and the rest boils off. The rupture lights one wet tile with chance 0.5; a fire then burns the spill like carpet. In thin air nothing pools.
- A burst of 500 kg or more is a small blast as well, which combat sets off (combat-sys 5.8).
- Every short and burst goes on `ShipGridComponent.incidents` (the game's `DamageControl` posts each as a notice for the player's ship) and is counted in `shortCount` and `burstCount` for surveys.
- Art: `effects/short_circuit` over a shorted conduit and the middle of a shorted system, `effects/propellant_spill` on bare deck holding spilled fuel.
- Tuning (round 5, the scripted career on seeds 2026, 3, 7, 11 and 42): 0 to 2 shorts a fight, nearly all on the pirate ship, no bursts and no fires from either. A burst needs a tank already wrecked to a quarter, which these fights don't reach.

## Chassis Blueprints
`ChassisBlueprint` holds the character map plus everything a character cannot express, all in grid coordinates:
- `tileSize` and `gridOriginLocal`
- edge walls (tile, side, weight class, material, bulkhead)
- hull pieces
- explicitly placed mounts
- floor coverings, as rectangles: `covering(FloorCovering.CARPET, x, y, w, h)` lays carpet on every floor tile inside
- RCS thruster blocks (`rcsThrusters`), each an `RcsThruster` on one side of a hull tile, a side that faces space: `LINEAR` with one nozzle out through that side, `QUAD` with one each way along the hull too, at its kind's standard thrust a nozzle unless it gives its own. `RcsThruster.placement` says where one can go; a block takes no room on its tile, works while that tile's wall stands, and is saved with the grid. Core's `RcsThrust` turns them into the ship's turning, strafing and retro thrust

Character map:

| Char | Meaning |
|---|---|
| `#` | Outer hull, medium |
| `1` `2` `3` | Outer hull, light / medium / heavy |
| `I` | Full-tile internal wall |
| `.` | Steel floor |
| `+` | Power conduit (steel floor) |
| ` ` | Void |
| `R` `S` `T` | Primary systems: reactor, shield emitter, turret mount |
| `L` `H` `W` `C` `A` `E` | Auxiliary systems: life support, helm, weapons console, cargo, airlock, engine |
| `D` | Docking port, drawn over the hull edge (3×1 Small, 5×2 Medium, 7×3 Large) |

These are the built-in characters. The characters a map can use are really **tile types** (`[tile.*]` in `assets/data/tiles.toml`) and **ship components** (`[component.*]` in `assets/data/components.toml`), held in a `ShipPartCatalog`: a tile type names a base (void, floor, conduit, hull or internal wall), weight class, material and covering; a component names a subsystem behavior, its sizes and settings (reactor output, life support throughput), which each installed `SubsystemComponent` keeps. The data files restate the built-ins, and new ones are made there or in the ship editor (see `documentation/ship-editor/Design.md`). A character that is neither is an error.

A docking port (`DockingPortLayout`, a `PORT` mount of the `DOCKING_PORT` subsystem) is drawn over the hull where it should open: its long side lies along the hull, and it faces the one side with only space beyond it. On loading, its end tiles become hull frame and the tiles between them steel floor (the opening, 1, 3 or 5 tiles wide), with the port door, a sealed medium bulkhead, on the opening's outer edge. The opening must lead onto floor inside. Ports are passive unless the chassis lists them as active (`ports = [{ x, y, active = true }]`, any tile of the port); stations-docking turns each port into a `DockingPort` with its collar and magnets. A chassis marked `station = true` is a station layout, named by a station type's `blueprint`.

Every ship type in `ships.toml` (the audio demo's hull aside) is built with the interior systems a ship is expected to have, and core's `ShipLayoutsTest` holds them to it: RCS blocks on the hull; thin walls with doors in them dividing the ship into rooms, each wall with an edge conduit in it so every system stays powered with all the doors shut; conduit tiles; life support piped to oxygen and nitrogen and every engine to propellant; and every tile crew can stand on walkable from the helm. The ship types are the corvette, picket, interceptor, courier, light raider, corsair, gun platform, gunship, salvage tug, surveyor, ore barge, marauder, frigate, freighter, hospital ship, propellant tanker, destroyer, reaver, heavy cruiser, bulk carrier, passenger liner and carrier. The thirteen from the interceptor on are shaped hulls (a cross, a hammerhead, a sawtooth, a U with a scoop, hangars on pylons) rather than rectangles, with 1 x 1 wedges bevelling every outer corner; `tools/art/generate_svgs.py` draws their hull art from the layout, wedges included.

System letters are floor tiles. Each solid rectangle of one letter becomes one mount, and its size must be a legal mount for that system's kind (`RR/RR` is an M reactor, `LL` an S life-support slot on its side). Two adjacent mounts of the same type cannot be told apart in the map, so those go in the explicit mount list. The loader throws on any illegal mount, piece or wall.

## Atmosphere
Air is simulated per **compartment**, not per tile. A compartment is one airtight volume: a room, a corridor, the gap of a door. Its air is a single well-mixed volume with one pressure and one composition (oxygen, nitrogen, CO2 in moles, plus suspended smoke in kg). Its volume is tiles × `tileSize²` × `DECK_HEIGHT` (2.5 m), and pressure follows the ideal gas law at the mean temperature of its tiles, so a fire also raises the pressure of its room. Rooms start at 101.3 kPa, 21% oxygen.

### What counts as one room
`CompartmentBuilder` divides the air-holding tiles. Openings one tile wide (doors, 1-wide corridors, a single missing hull tile) count as **closed** when deciding what a room is:
- A tile is *wide* if it lies in some 2×2 block of air-holding tiles that are all connected to each other. Every other air-holding tile is *narrow*.
- Two adjacent wide tiles are in the same room only if the edge between them is inside such a block. An opening two or more tiles across joins two areas; a single open door does not.
- Adjacent narrow tiles join each other, so a 1-wide corridor is a compartment of its own. A narrow tile never joins a wide one.

The compartments are rebuilt whenever a tile starts or stops holding air or an edge opens or closes (a door, a seal, a wall destroyed). Air is carried over: each old room's contents are spread evenly over its old tiles and each new room takes the share of the tiles it now contains. A newly opened tile brings no air of its own, so the room it joins thins out, and the air of a tile that was sealed off goes to the rest of its old room.

### Flow and venting
Atmosphere is **conserved**: it only leaves the ship through openings to space. Every tick, `GridHazardSystem` sums the openings between each pair of compartments, and between each compartment and space, in tile widths:
- each open edge between two compartments, or between a compartment and space (out of bounds, void, a breach): 1
- each leak (see Damage above): its `leakFactor`

Air flows from the higher-pressure side at `FLOW_COEFFICIENT` (2 mol/s per kPa per tile width), carrying that side's mix of gases and smoke, and never more than halfway to equal pressure in one step. A 54-tile room vents through one breached hull tile with a time constant of about 30 s; two rooms joined by an open door equalize in about 15 s.

On top of the bulk flow, rooms joined by an opening **diffuse** into each other. Each swaps an equal number of moles with the other, carrying its own mix, so composition (oxygen, CO2, smoke) evens out while pressure is left alone. The exchange is `DIFFUSION_COEFFICIENT` (0.6 m³/s each way per tile width). Two 54-tile rooms joined by one open door at equal pressure mix most of the way in about 2 minutes. Bulk flow moves that much air at about a 12 kPa difference, and fires and breaches drive far larger ones, so bulk flow dominates whenever pressures differ. Diffusion does not carry air to space, where bulk flow already does.

After each step the net flow through every opening is published on `ShipGridComponent.airflow` as `AirflowEdge`s: the air tile on one side, the direction of the opening (or none for a hole in the deck), the compartments on both sides, and the air and smoke leaving through it per second. An opening between two rooms is listed once from each side. Effects, crew and anything else that reacts to draughts read this list.

### Links to another grid
A `GridLink` joins the outer edge of one tile to the outer edge of a tile on another craft's grid, as a pair (one on each grid, each the other's `mate`, listed in `ShipGridComponent.links`). Docking makes one per pair of facing port-opening tiles. A linked edge is never an opening to space. When the edge walls on both sides are open, air moves between the two compartments behind them with the same bulk flow and diffusion as an opening between rooms, one tile width per link (or a damaged closed door's leak), after every grid has taken its own step; time-warp catch-up pools the linked rooms. The player's avatar walks over a link when neither door blocks it and lands just inside the facing tile on the other grid, at the same spot along the edge (`GridLink.carryAcross`), now aboard the other craft. AI crew paths, line of sight, sound and fire do not cross links yet. Links are runtime only and not saved; whoever made them makes them again after a load.

There is no "vacuum" state that spreads. A vented room is simply a compartment with no air left. A sealed bulkhead or closed door is airtight, so sealing between a breach and a room stops that room's loss at once.

## Heat
Every tile (floors and walls) has a temperature. Each hazard tick:
- neighbouring tiles exchange heat by conduction (`CONDUCTION`, 60 W/K) and, where air connects them, by convection (`CONVECTION`, 300 W/K at standard pressure, scaled down as the room vents);
- every tile sheds heat into the ship's structure (`AMBIENT_LOSS`, 150 W/K above ambient 293 K);
- every tile radiates heat into the surrounding structure, `σ × RADIATING_AREA × (T⁴ − 293⁴)` per m² of floor (`RADIATING_AREA` 4, for floor, ceiling and a share of the walls). Radiation grows with T⁴, so it is what holds a fully involved fire near 1400 K;
- a tile holds `TILE_HEAT_CAPACITY` (20 kJ/K).

Heat therefore spreads out from a fire to the tiles around it, also through walls into the next room, and the ship cools back down once the fire is out. Crew and systems can read tile temperature for burns and heat damage.

## Fire
A fire is an **intensity** on a tile: 0 is no fire, a new fire starts at 0.05 (`Tile.ignite()`) and grows toward 1, fully involved. `Tile.fireSize()` reports SMALL, MEDIUM or LARGE.
- **Fuel.** A floor covering burns, and so do the furnishings on the tile. A tile with neither cannot be lit; a couch on bare steel can. The fire burns the covering and each furnishing its share of the tile's fuel. A furnishing burned to `Furnishing.BURNT` (a tenth of its fuel) is gone: it leaves its tile and is listed in `ShipGridComponent.burntFurnishings` for the game to report. One a fire only scorched stays, drawn darker, and below `Furnishing.USABLE` (half) the crew no longer use it.
- **Growth.** With standard air a fire gains `FIRE_GROWTH_RATE` (1/60 per s), so a small fire takes about a minute to become a large one. Growth slows as the room's oxygen falls; below 60% of standard oxygen partial pressure (`FIRE_MIN_OXYGEN`, about 12.6% O2 at 1 atm) the fire shrinks instead, at up to `FIRE_SMOTHER_RATE` (0.25 per s, in vacuum). As the last of its fuel goes, the fire dies down with it.
- **Burning.** A fire burns `BURN_RATE × intensity` (0.01 kg/s per m² at full size). Each kg of fuel uses 70 mol of oxygen and gives 55 mol of CO2 and 0.08 kg of smoke. Heat is set separately, so fires burn slowly but hot: a fully involved fire puts `FIRE_HEAT_OUTPUT` (1.2 MW per m²) into its tile, scaled down when fuel or oxygen limits the burn. A carpeted tile burns out in about two and a half minutes at full size, and the carpet is then gone. A fire in a sealed room uses up the room's oxygen and starves itself; an open door or a vent decides how long it can keep going.
- **Spreading.** Each second, a burning tile has a chance to jump to each neighbouring flammable tile that air connects it to: `SPREAD_RATE × intensity × (0.15 + 0.85 × heat)`, with `SPREAD_RATE` 0.05, where `heat` runs from 0 at ambient to 1 at `IGNITION_TEMPERATURE` (600 K). Big fires spread to hot tiles quickly; a small fire barely reaches a cold one. A fire in a carpeted 9 × 6 room reaches a quarter of it in about two minutes and half of it in three to five.
- **Auto-ignition.** A flammable tile at 600 K or more catches on its own (`AUTO_IGNITION_RATE`, 0.05 per s) if its room has the oxygen, even when the heat reached it through a wall.

Random draws use a seeded generator per `GridHazardSystem`, so runs are reproducible. The hazard tick is 0.25 s; all rates are per second.

## Heat and Pressure Damage
`GridHazardSystem` applies damage on each hazard tick, after heat:
- **Walls and doors.** A full-tile wall (hull or internal) or an edge wall hotter than `STRUCTURE_DAMAGE_TEMPERATURE` (900 K) loses `STRUCTURE_HEAT_DAMAGE` (0.0005) integrity per second for every 100 K above it. An edge wall takes the temperature of the hotter tile beside it. A wall held at 1400 K beside a fully involved fire starts to leak after about 200 s and is destroyed after about 400 s. Floors are left alone, so a fire never burns a hole to space through its own deck.
- **Systems.** An installed system loses `SYSTEM_HEAT_DAMAGE` (0.00005) health per second for every kelvin its hottest footprint tile is above `SYSTEM_DAMAGE_TEMPERATURE` (450 K). A system in a burning room fails (below 0.3 health) within a few minutes.
- **Overpressure.** A closed edge wall bursts (integrity 0) when the pressure difference across it exceeds its limit: 150, 300 or 600 kPa for a light, medium or heavy door (`burstPressure(WeightClass)`), and twice that for a plain edge wall. Vacuum counts as 0 kPa, so ordinary venting (101 kPa) never bursts a door. Only one edge bursts per tick, so a pressure spike blows the weakest door and the rooms then even out instead of the whole wall line going at once. A fire in a sealed room can push its pressure past 240 kPa and blow a light door.
- A wall or system that fails sets `gridDirty`, so the compartments and power routing are rebuilt.

Crew damage from heat and smoke belongs to ai-crew (`CrewHealthSystem`), which reads tile temperature and room air.

## Sounds
audio-spatial's `InteriorHazardAudioSystem` reads every grid after the hazard tick and keeps one emitter entity (marked `TransientComponent`, never saved) per sound:
- a fire loop per burning compartment, at the middle of its flames, louder with the fire's size;
- a breach hiss per opening losing air to space, on the edge of the hole, louder with the flow (full at 150 mol/s);
- an alarm loop per compartment that is burning, venting, below 70% pressure or below 75% oxygen;
- a door one-shot whenever a door opens or closes (not when it breaks).

## Life Support
`LifeSupportSystem` runs every operational `LIFE_SUPPORT` unit on the hazard tick (0.25 s):
- A unit serves the compartment it stands in, plus every compartment reachable from there through fully open openings (open doors, gaps a tile wide). Air behind a closed door, a seal or a leaking wall is out of reach.
- It draws 3 m³/s of air, spread over the whole served volume, and removes 90% of the CO2 and smoke in that air.
- It releases up to 20 mol/s of standard air (21% oxygen) into served compartments below standard pressure, shared by how far each one is short. A vented 9 × 6 room, once sealed, is back to full pressure in a few minutes.
- It then releases up to 1.5 mol/s of oxygen into served compartments whose air is short of the standard 21% share (and below standard oxygen partial pressure), such as after a fire.
- Nothing is released into a compartment still venting to space.
- That is far slower than a large fire uses oxygen, so life support cleans up after a fire rather than holding one back.
- The air and oxygen come out of the oxygen and nitrogen tanks piped to the unit (see Tanks and Pipes): fresh air takes both gases at the 21/79 mix, so it stops when either runs dry, and the oxygen top-up takes only oxygen. A unit with no tank piped to it releases nothing. A ship with no tank of a gas at all (a layout from before tanks, or a station) is not limited by that gas.

## Tanks and Pipes
Consumables live in tanks in the ship grid and reach the systems that use them through pipes, laid like conduits.
- **Tanks** are `TANK` systems in auxiliary slots. Each holds one `Fluid` (`OXYGEN`, `NITROGEN` or `PROPELLANT`); the built-in components are `oxygen_tank` (`O`), `nitrogen_tank` (`N`) and `tank` (`P`, propellant). `SubsystemComponent.capacity` and `contents` are kilograms: by default 150 kg of a gas or 1,000 kg of propellant a tile, so an S tank holds 300 or 2,000 kg. A component can set `capacity` by size. Tanks start full. A tank supplies only while switched on (its valve) and not disabled by damage; it needs no power.
- **Engines** (`ENGINE`, built-in `engine`, `E`) are powered auxiliary systems that burn propellant for the ship's thrusters (see the propellant feed in `documentation/physics-exterior/Design.md`). An engine mounts on the outside of the outer hull (`EngineLayout`): its nozzle faces the way its rotation says (0 +y, 1 -x, 2 -y, 3 +x) and the row at the other end is its flange. The flange stands just outside, against the outer hull: the whole engine lies out in space, overlapping none of the hull, and the tiles past the nozzle (as wide as the engine, 5 for each tile of its width: 5 for a Small engine, 10 Medium, 15 Large, 25 Extra Large) must stay open space for the exhaust, other parts and engines included. In the chassis rows an engine is drawn out in the void: the loader points its nozzle at the end with only space beyond it. Built, the engine's tiles are hull: its casing is light hull, or the weight class its component's `casing` names (struck, crushed and collided with like the rest of the hull). Engine components also carry a `thrust` table, an `efficiency` that multiplies the exhaust velocity, a `maker`, a `description` and a yard `price` (see `components.toml`): Halvorsen Propulsion's bells (`engine` HR-40, `freight_drive` HR-25F), Imbrium Heavy Foundry's cased drives (`gunship_drive` Rampart, `imbrium_bulwark` Bulwark, medium-hull casing, rarely shorted, thirsty) and Aster Dynamics' aerospike (`aster_lyra` Lyra 7, frugal, easily shorted). Power, pipes and the crew reach it from its feed tiles: the deck just inside the wall behind its flange (a pipe across a feed tile, power reaching one, and a crew member standing on one to repair it).
- **Pipes** (`Pipe`) lie across a tile's middle (`ShipGridComponent.pipes`) or along a tile edge (`pipeH` / `pipeV`, indexed like the edge walls), inside any wall, as edge conduits do. Pipes are generic: one run carries every fluid. A pipe across a tile joins the pipes across the four tiles beside it (through a thin wall between them) and the edge pipes on its own tile's edges; an edge pipe joins the edge pipes that meet it at either end and the pipes across the tiles on both sides. A pipe across a tile is cut when the tile is destroyed, an edge pipe when the wall it runs in is.
- `PipeRouting` floods the pipes into `PipeNetwork`s (on `ShipGridComponent.pipeNetworks`) whenever `PowerRoutingSystem` reroutes, so pipes follow damage as quickly as power. A system is on every network that crosses one of its footprint tiles or runs along their edges, and `SubsystemComponent.pipedTanks` lists every tank on those networks.
- `TankSupply` reads and draws on them: `available(system, fluid)` is what a system's working piped tanks hold, and `draw` takes from several tanks in proportion to what each holds.
- A tank damaged below 0.6 health leaks (`TankSupply.leak`, on the life support tick): a destroyed tank loses 2% of its contents a second, a less damaged one in proportion. Oxygen and nitrogen escape into the air of the room the tank stands in (an oxygen leak enriches the room, which feeds fires); propellant is lost overboard. The crew isolate a leaking tank (shut its valve), which stops the leak unless the tank is wrecked; an isolated tank supplies nothing until a repair to full health opens it again. Below its burst threshold a hit can burst it instead (see Damage states).
- What the tanks hold weighs on the ship: `TankSupply.addMassTo` adds each tank's contents as a point mass at the middle of its footprint, and the game reweighs the ship whenever its tanks have gained or lost 5 kg.
- Layouts list pipes as `pipes = [{ x = 3, y = 2 }, { x = 3, y = 2, side = "TOP" }]` (no side: across the tile). They are saved with the grid, and each tank's contents with its `SubsystemComponent`. Station yards refill the tanks for credits (see `documentation/stations-docking/Design.md`).

## Power & Subsystem Routing
`PowerRoutingSystem` runs a breadth-first flood from every footprint tile of each reactor that is switched on and not disabled by damage, across floor and conduit tiles:
- Intact full-tile walls and tiles at integrity 0 do not conduct.
- Closed edge walls block power, except where a conduit runs on both sides of the wall (a conduit passes through a thin wall), and a docking port's inner hatch: the port is fed through its opening, so shutting the hatch for an airlock cycle never cuts the pump's power.
- **Edge conduits** (`EdgeConduit`, on `ShipGridComponent.conduitH` / `conduitV`, indexed like the edge walls) run along a tile edge instead of across a tile, and may lie inside any wall: a thin wall on that edge, or a full-tile wall (hull included) on either side. One carries power to the edge conduits that meet it at either end of its edge and to the conducting tiles on both sides, whatever wall stands there, so a run of them can take power around or through a wall and feed both rooms it divides. It is cut when the wall it runs in is destroyed. Layouts list them as `edge_conduits = [{ x = 3, y = 2, side = "TOP" }]`; they are saved with the grid.
- A system is powered if any tile of its footprint is powered. It is operational when it is switched on (`SubsystemComponent.switchedOn`), powered and not disabled by damage (`health >= SubsystemComponent.DISABLE_THRESHOLD`, 0.3). Switching a reactor off is not damage: it powers back up as soon as it is switched on.
- It reroutes on a 1 s fixed tick, or on the next frame when damage code sets `ShipGridComponent.gridDirty`.

### Circuits and power draw
Each connected part of the grid with a working reactor in it is a `PowerCircuit`, listed on `ShipGridComponent.circuits` and set on each powered system as `SubsystemComponent.circuit`.
- A reactor adds its rated output to its circuit (S 1 MW, M 3 MW, L 10 MW, XL 30 MW), scaled by its health, so a reactor at half health gives half the power.
- The circuit stores `STORE_SECONDS` (2 s) of its output and recharges every 0.25 s.
- Burst loads such as weapons call `SubsystemComponent.tryDrawPower(joules)`. It takes the energy all or nothing and is false when the system is not operational or the store is short. `PowerCircuit.draw(joules)` takes what it can. For a weapon, a shot's energy is its `power_draw` times its cooldown, so the corvette's 3 MW reactor keeps its railgun (2 MW) firing at full rate but not at half health.
- A cut conduit that strands a turret on a part of the grid with no reactor leaves it with no circuit and no power.
- Rerouting keeps the same circuit object, and its stored energy, while the layout is unchanged. Circuits are not saved; a loaded game starts with full stores. Rails warp ends with full stores.

## Ticks and Time Warp
All three interior systems are core-engine `FixedLogicSystem`s, run after exterior physics in this order: `PowerRoutingSystem` (priority 30, every 1 s), `GridHazardSystem` (31, every 0.25 s), then `LifeSupportSystem` (32, every 0.25 s).

`InteriorHazardWarpGuard` is a core-engine `RailsWarpGuard` for one ship, normally the player's. It refuses rails warp with "Fire aboard" while any tile burns (`ShipGridComponent.hasFire()`), or "Atmosphere venting" while the ship as a whole loses air to space faster than 0.01 mol/s (`isVenting()`, summed over every opening since round 4). A sealed or patched leak does not count. Round 6: a leak the crew have patches for holds warp back however slowly it vents, even from a room already empty, because warp skips the crew's work while the tanks drain into space (on seed 2026 the gunship's nitrogen ran out over two warped days and the crew suffocated). While a patchable leak vents, the reason says what will clear it: "Atmosphere venting: the crew are patching the leak", or "...: no hull patches left, a yard must repair the leak". The game's `DamageControl` posts the same as notices, and a notice when the patches hold.

A rails warp jump therefore only ever starts with no fire and no venting. Each system gets one `catchUp` call for the whole jump:
- `GridHazardSystem` cools every tile toward ambient by the exact exponential decay of its loss to the structure, and pools the air of every group of rooms joined by any opening. A group that still reaches space through a slow leak empties.
- `PowerRoutingSystem` reroutes once.
- `LifeSupportSystem` runs one long step. Scrubbing decays exponentially over the jump, and the air and oxygen supply is capped by what the rooms are short. A room with any opening to space gets nothing.

- `ShipGridComponent`: tiles, edge walls, hull pieces, mounts, compartments, `gridOriginLocal`, coordinate conversion, placement validation, mass properties.
  - `layoutRevision` is bumped each time the compartments are rebuilt, on the first hazard tick after a wall, door, seal or tile opens or closes. Caches built on `canPass`/`canFlow`, such as crew pathfinding, compare it against the value they were built at.
  - `hasActiveHazard()` is true while any fire burns or air vents to space faster than 0.01 mol/s through an opening. Time warp is refused while it holds.
- `SubsystemComponent`: tags an entity as a ship system installed in a `MountFootprint`.
- `ShipSubsystemRegistryComponent`: the ship's list of subsystem entities.

### Saving
Register `InteriorGridSaveParticipant` with the `SerializationService`. It installs `ShipGridSerializer`, which writes each grid as plain state: tiles, edge walls, hull pieces, mounts, pipes, furnishings, and each compartment's air keyed by one of its tiles. On load it places the pieces and mounts again, rebuilds the compartments and pours the saved air back in.
- `ShipSubsystemRegistryComponent` saves `EntityRef`s (`ownerRef`, `subsystemRefs`). Its live `ownerEntity` and `subsystems` are transient.
- `MountFootprint.installed` is transient too, so a save never drags in a system entity.
- After every entity is loaded, the participant calls `registry.relink(uuids)` and `grid.relinkMounts(registry.subsystems)` to restore both.

Plain data classes: `Tile`, `EdgeWall`, `HullPiece`, `MountFootprint`, `Compartment`, `AirflowEdge`, `FloorCovering`, `WeightClass`, `Side`, `MaterialTable`, `ChassisBlueprint`.

## Systems
- `GridHazardSystem`: air flow between compartments and to space, heat, and fire.
- `CompartmentBuilder` (helper, not an ECS system): divides the interior into compartments and carries the air over when the layout changes.
- `PowerRoutingSystem`: calculates subsystem power connectivity, then pipe networks (`PipeRouting`).
- `LifeSupportSystem`: scrubs CO2 and smoke, refills sealed rooms with air and replenishes oxygen, out of the piped tanks.
- `InteriorHazardWarpGuard` (not an ECS system): refuses rails warp during a fire or venting.

## Open Questions
- **Tank damage** (answered in round 5, see Damage states): a tank past a quarter health can burst, and burst propellant feeds a fire. A tank that only leaks still loses its propellant overboard rather than onto the deck.

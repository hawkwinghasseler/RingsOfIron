# Combat & Damage Resolution Module Design

## Overview
The `combat-sys` module bridges the gap between exterior space combat and interior grid damage (module destruction, crew death). It handles weapon firing, projectile and missile flight under gravity, fire control (lead targeting for AI gunners and the HUD lead reticle), and the mathematical translation of external hull hits to internal consequences.

Design pillars:
- **Newtonian all the way down.** Projectiles inherit the shooter's velocity, closing speed decides how hard a round hits, recoil and impacts push ships around, and gravity bends long shots.
- **One solver, three users.** The same firing-solution math aims AI turrets, drives missile guidance, and draws the player's lead reticle, so what the HUD shows is what the AI is doing.
- **The hull hit is the start of the story.** Every hit becomes a ray through the interior grid that breaches hull, cuts conduits, starts fires and wounds crew, which then feeds the existing hazard, power and crew task systems.

All units are SI: meters, seconds, kilograms, joules.

## Implementation Dependencies
- **`core-engine`**: Ashley ECS for entity and component tracking; `FixedLogicSystem` for the 60 Hz combat tick; `UuidRegistry` for owner/target references.
- **`physics-exterior`**: **Hard Dependency.** Ships are Box2D bodies; combat uses `World.rayCast` and AABB queries against ship fixtures, applies impulses for recoil and impacts, and uses the bubble frame, 10 m Box2D unit and tidal field defined in `orbital-mechanics/Design.md` (summarized in section 1).
- **`interior-grid`**: **Hard Dependency.** Reads and mutates `ShipGridComponent` tiles and `SubsystemComponent` health via `ShipSubsystemRegistryComponent`.
- **`ai-crew`**: **Soft Dependency, through interfaces only.** `combat-sys` does not depend on `ai-crew` in `build.gradle`. It needs three things from it, all expressed as interfaces owned by `combat-sys` and implemented by `ai-crew`: a `GridOccupantQuery` (which crew stand on tile x,y), a `CrewDamageSink` (apply injury, see 5.9) and a `DamageTaskSink` (post `RepairTask`, `SealBreachTask`, `ExtinguishTask`, `RescueTask`). Gunner skill is read from a plain `GunnerySkill` value set on the weapon's track, so crew stats can drive accuracy without a compile-time dependency.
- **`modding-data`**: Weapon, projectile, missile, warhead and material stats come from TOML defs (section 6).

---

## 1. Gravity, Units and the Bubble Frame
Combat follows the frame, units and gravity model set out in [`orbital-mechanics/Design.md`](../orbital-mechanics/Design.md). This section only records what combat relies on from it.

### 1.1 What combat takes from the orbital plan
- **Bubble frame.** Everything in combat is expressed relative to the free-falling **anchor**. Orbital velocities never appear in combat math. Ships in the same orbit sit still relative to each other, and only thrust, tidal gravity and impacts move them.
- **Units.** Combat math runs in SI meters. **Box2D uses 1 unit = 10 m** (`B2D_METERS_PER_UNIT`), so every value crossing into or out of Box2D (positions, velocities, impulses, ray endpoints) is converted at that boundary in one helper, never ad hoc. Directions such as hull normals need no conversion.
- **Speed cap.** At 10 m per unit and 60 Hz, Box2D bodies top out at about **1,200 m/s** in the bubble frame. That is why the fastest weapons do not use Box2D bodies (section 2.1).
- **Gravity.** Box2D world gravity is zero. Dynamic bodies get the tidal term from `TidalGravitySystem`. Projectiles and missiles that are not Box2D bodies apply the same term in their own integrator by calling the same function:

  ```
  a_tidal(r) = g(R_a + r) − g(R_a),   g(X) = −mu · X / |X|³   (SOI parent only, patched conics)
  ```

  Combat should call one shared `TidalField.accelAt(localPosMeters)` owned by `physics-exterior`, so that guns, missiles, the HUD and Box2D bodies always agree.
- **Rebase.** A `WorldShiftEvent(deltaPos, deltaVel)` must also shift every combat-owned entity: live projectiles, missiles, tracks (their `p` and `v`), and pending impact events.
- **Bubble edge.** Projectiles and missiles that pass `BUBBLE_EXIT_RADIUS` are despawned rather than handed to rails. A miss that leaves the bubble is gone.

### 1.2 How big is the effect?
Near the anchor, the tidal acceleration at separation `d` is at most about `3·n²·d`, where `n = 2π / orbital period`. The table shows the sideways drift of a shot over its flight, at real-world periods:

| Orbit period | Separation | Tidal accel | Drift over 10 s | Drift over 30 s |
|---|---|---|---|---|
| 90 min | 1 km | 0.004 m/s² | 0.2 m | 1.8 m |
| 90 min | 10 km | 0.04 m/s² | 2 m | 18 m |
| 90 min | 25 km (bubble edge) | 0.10 m/s² | 5 m | 46 m |
| 24 h (geostationary) | 25 km (bubble edge) | 0.0004 m/s² | 0.02 m | 0.2 m |

The game world is **1:1 with real orbital periods**, so these are the real numbers. Low orbit is the tightest case anyone will fight in: knife-fights look like flat Newtonian space, and only shots across most of the bubble drift by meters to tens of meters. Higher orbits are flatter still. Gravity's bigger effect on combat at real scale is indirect: it sets the closing speeds between ships (section 1.3).

### 1.3 Closing speeds at real scale
Real low-orbit speeds are about 7.8 km/s. Ships whose orbits merely cross will pass each other at hundreds of m/s to several km/s, up to about 15 km/s for opposite-direction orbits. That shapes combat:
- **Matched-orbit fights** (after a rendezvous) are slow and close, and this whole design applies without strain.
- **Crossing-orbit fights** are brief **firing passes**. Lead times are short, closing speed makes every hit hit hard (section 5.3), and missiles mostly need lateral Δv to stay on the line of approach rather than speed to catch up.
- Projectiles are fine at any closing speed, because fast ones are raycast (2.1).
- **Ships** passing faster than Box2D's cap are handled by the orbital plan's **fast passes** (its section 4). A body that enters the bubble faster than about 1,000 m/s relative (`B2D_ENTRY_SPEED`) stays on its rails orbit as a **kinematic transient**: it is drawn and collides via raycasts, and becomes a normal Box2D body only if it slows below that speed inside the bubble. Section 1.4 covers what combat must do about them.

### 1.4 Fighting fast-pass ships
A fast-pass ship is a full combat participant with no Box2D body. Combat treats it as follows:
- **As a target.** Sensors and tracks read its position and velocity from its rails state each tick, so the lead solver works unchanged. Its rails path has no thrust, so its true acceleration is just gravity, and a good tracker will lead it almost perfectly. Fast passes are brief, but the shooting is accurate.
- **Hit tests.** A fast-pass ship is not in the Box2D world, so `World.QueryAABB` will not find it. The swept tests in 2.3 must also check every kinematic transient in the bubble against the same relative-motion segment, using its fixture shapes from the chassis def. The same goes for missile proximity fuses.
- **Being hit.** Impacts on it resolve into its interior grid exactly as for any ship (section 5), with `relVelocity` taken from its rails velocity. Momentum transfer (5.7) cannot go to a Box2D body. Instead it becomes a Δv on its orbit, handed to the orbital plan's collision handling, which already recomputes rails orbits after a collision.
- **Firing from it.** A fast-pass ship can still fire. Projectiles spawn with its rails velocity as `v_ship`, and recoil becomes a small Δv on its orbit, the same as for momentum transfer.
- **Ramming.** When `KinematicTransientSystem` finds a fast pass hitting a ship or asteroid, it emits a normal `ImpactEvent` whose `mass` is the smaller body's mass. At km/s, ramming is resolved by section 5 like a very large slug and will usually gut both ships along the contact line.

---

## 2. Projectile Ballistics

### 2.1 Decision: fast projectiles are not Box2D bodies
This revises the original guide, which gave every projectile a Box2D body, and follows the split in the orbital plan (its section 5). Each projectile and missile def is assigned to one of two flight models by its **maximum speed relative to the bubble frame**:

| Flight model | Used for | How it flies | How it hits |
|---|---|---|---|
| **Box2D bullet** | Defs that can never exceed ~800 m/s: torpedoes, slow cannon shells, boarding pods | Box2D body with `bullet = true`, tidal force from `TidalGravitySystem` | Box2D `ContactListener` |
| **Raycast** | Everything faster: railgun and coilgun slugs, autocannon rounds, and **any missile whose Δv lets it pass ~800 m/s** | Combat's own integrator (2.3) | Swept raycast (2.3) |

The 800 m/s threshold leaves headroom under Box2D's 1,200 m/s cap for the launcher's own speed. Most missiles land in the raycast model because of their Δv (section 4.1). This refines the orbital plan's shorthand that missiles are Box2D bodies. Missiles never switch models mid-flight, which avoids a handoff on every burn. Both models emit the same `ImpactEvent`, so everything from section 5 on is indifferent to which one produced a hit. Ships stay Box2D bodies.

### 2.2 Spawning (muzzle)
When a weapon fires:
- `v_proj = v_ship_at_hardpoint + muzzleSpeed · aimDir + spread`, where `v_ship_at_hardpoint = v_ship + ω × r_hardpoint`. Rounds inherit the ship's motion, including spin.
- `p_proj = hardpointWorld + aimDir · muzzleOffset`.
- **Recoil:** apply `−m_proj · muzzleSpeed · aimDir` as a linear impulse at the hardpoint on the firing ship's body. Off-center mounts will torque the ship, which the flight computer has to counter.
- `ownerUuid` is set and the owner is ignored for `armingTime` (e.g. 0.05 s) so a round cannot hit its own hull on exit.

### 2.3 Integration (each 60 Hz tick, after `PhysicsUpdateSystem`)
Raycast-model projectiles use semi-implicit (symplectic) Euler, the same integrator Box2D uses, so both flight models agree. All values are in meters here and converted to Box2D units only for queries:

```
a      = tidalField.accelAt(p)
v     += a · dt
p_new  = p + v · dt
```

Then the swept hit test from `p` to `p_new`:
1. **Broadphase:** `World.QueryAABB` over the segment's AABB, expanded by the fastest ship's per-tick motion, filtered to `CATEGORY_SHIP | CATEGORY_ASTEROID`, plus every fast-pass kinematic transient in the bubble (1.4).
2. **Narrowphase in the target's frame:** for each candidate body, transform `p` with the body's *previous-tick* transform and `p_new` with its *current* transform, then raycast that relative segment against the fixture shapes locally. This catches hits even when the ship moved a meaningful distance during the tick.
3. Take the earliest hit (smallest fraction). Record world hit point, surface normal and the fraction to emit an `ImpactEvent` (section 4).
4. **Point defense vs missiles:** missiles are circles of radius `hitRadius`; test segment against circle, again in relative motion.
5. Projectile vs projectile is ignored.

Each projectile despawns at `maxLifetime` or when it leaves the bubble. Rounds that leave the bubble are dropped rather than handed to `macro-sim`.

### 2.4 Projectile data
`ProjectileComponent`: `defId`, `mass`, `pos`, `prevPos`, `vel`, `age`, `ownerUuid`, `damageType` (`KINETIC`, `EXPLOSIVE`, `ENERGY`), `penetrationFactor`, `fuse` (`CONTACT`, `DELAYED(depthTiles)`, `PROXIMITY(radius)`).

### 2.5 Energy weapons (beams)
Lasers are instant raycasts with no travel time and no lead, so they bypass ballistics. Each tick a beam deposits `power · dt` joules on the hull spot it touches (section 5.6). Relative motion smears the spot across tiles, so holding a beam steady on one tile is a gunnery skill in its own right.

---

## 3. Fire Control and Lead Targeting

### 3.1 Tracks
A `TrackComponent` on the shooter (one per sensed contact) holds the filtered estimate of target position, velocity and acceleration, in frame-local coordinates. It is updated by an **alpha-beta-gamma tracker** from sensor observations:
- Sensor observations carry noise (`sigma` grows with range) and arrive with latency.
- The gamma term (acceleration estimate) is what lets a good crew lead a thrusting target. A green crew runs with gamma = 0 and only leads on velocity, so targets that keep burning slip out from under their fire.

### 3.2 The firing-solution solver
`FiringSolutionSolver` is a pure, allocation-free utility with no ECS state, so the AI, the HUD reticle and missile guidance all call it.

Inputs (frame-local): shooter `p_s, v_s`; target estimate `p_t, v_t, a_t`; projectile muzzle speed `s`; max flight time.

Let `d = p_t − p_s`, `u = v_t − v_s`, and `k = a_t − a_proj`, where `a_proj` is the projectile's gravity differential sampled at the predicted path midpoint. Intercept requires:

```
| d + u·t + ½·k·t² | = s·t
```

1. **Seed** with the `k = 0` closed form, the quadratic `(u·u − s²)·t² + 2(d·u)·t + d·d = 0`. Take the smallest positive root. No positive root means no solution (the target is outrunning the round).
2. **Refine** with fixed-point iteration: `t ← |d + u·t + ½·k·t²| / s`, re-sampling `a_proj` and `a_grav` at the target's predicted position each pass. Stop when `|Δt| < 1 ms` (usually 2–4 passes). Bail out after 8 passes or if `t` exceeds max flight time.
3. **Output:** `aimDir = (d + u·t + ½·k·t²) / (s·t)`, `timeToImpact = t`, `interceptPoint = p_t + v_t·t + ½·a_t·t²`.

For long shots (`t` above roughly 10 s), an optional **shooting-method pass** forward-simulates one test round with the real integrator for that `t`, measures the miss vector at closest approach, and nudges `aimDir` against it. This catches non-uniform gravity that the midpoint sample misses. Use it for HUD and spinal guns only, not every point-defense turret.

### 3.3 Turrets and the fire decision
`WeaponControlSystem`, per mount, per tick:
1. Ask the solver for `aimDir` on the assigned track.
2. Slew the turret toward it at `slewRate`, clamped to the mount's `firingArc`. Spinal mounts have zero slew, so the solver's `aimDir` becomes a heading request to the flight AI instead.
3. **Fire when** cooldown is ready, power is available, and the predicted miss distance is under `target radius + weapon spread · range`. Predicted miss is the turret's current pointing error times range.
4. Apply **gunnery error** from the crew: aim jitter (`sigma` scaled by skill), reaction delay before engaging a new track, and burst "walking", where each shot in a burst corrects by a fraction of the previous miss. These are tuning knobs, not hard rules.

A mount is only usable while its `WEAPONS` subsystem is operational and powered (`SubsystemComponent.isOperational && powered`). Interior damage directly blinds or silences guns.

### 3.4 The HUD lead reticle
`ui-hud` draws the reticle at the solver's `interceptPoint`, for the player's selected weapon, using the player's own track (which has the same sensor noise as anyone else's). The reticle jitters at long range, and it is wrong against a target that starts or stops burning after the estimate was made. Both are intended.

---

## 4. Missiles

### 4.1 Model
Missiles are self-propelled projectiles with a heading, a rocket and a seeker. A missile whose Δv can carry it past ~800 m/s relative (most of them) flies on the raycast model from section 2; slow torpedoes are Box2D bullets (2.1). Guidance below is the same for both.

`MissileComponent`:
- **Propulsion:** `dryMass`, `propellantMass`, `exhaustVelocity`, `maxThrust`. Mass drops as propellant burns (`dm/dt = thrust / exhaustVelocity`), so acceleration climbs through the burn. Total delta-v follows Tsiolkovsky: `Δv = v_e · ln(m0 / m_dry)`.
- **Attitude:** `heading`, `turnRate` (RCS-limited). Thrust only acts along the nose. To change course, a missile must point and burn. There are no wings and no lift.
- **Guidance:** `seekerType` (`RADAR`, `THERMAL`, `COMMAND`), `seekerFov`, `seekerRange`, `navGain N` (3–5), `terminalReserve` (fraction of Δv held back for the endgame).
- **Fusing:** `armingDelay`, `fuse` (`CONTACT` or `PROXIMITY(radius)`), `warheadDefId`, `hitRadius` (target size for point defense), `hitPoints`.
- **State:** `LAUNCH → BOOST → MIDCOURSE → TERMINAL → (COAST | DEAD)`.

### 4.2 Flight phases
1. **Launch (cold).** The missile is ejected at `ejectSpeed` on top of the launcher's velocity, and its motor stays dark until `armingDelay`. This keeps it from torching or hitting its own ship. Ejection reaction is applied to the launcher as an impulse.
2. **Boost / midcourse.** The missile steers on the **launching ship's track**, received by datalink. The datalink only works while the launcher's `WEAPONS` subsystem is operational and powered. If it drops, the missile switches to its own seeker early or, with no lock, coasts.
3. **Terminal.** Once the target is inside `seekerRange` and `seekerFov`, with line of sight not blocked by an asteroid (Box2D raycast), the missile steers on its own seeker. If it loses lock, it holds the last estimate for `memoryTime` and then coasts.
4. **Coast.** With no fuel or no guidance, the missile is a ballistic slug. It is still armed, and still dangerous at closing speed.

### 4.3 Guidance law: zero-effort-miss
Reusing the solver's time-to-go (`t_go`) keeps missile guidance and gun lead consistent:

```
ZEM        = d + u·t_go + ½·(a_t − a_grav_missile)·t_go²
a_lateral  = N · (ZEM − (ZEM·LOŜ)·LOŜ) / t_go²      // component perpendicular to line of sight
a_closing  = closingBurn · LOŜ                       // only while Δv > terminalReserve
a_cmd      = a_lateral + a_closing
```

- `heading` turns toward `a_cmd` at no more than `turnRate`. Throttle is `min(|a_cmd| · m / maxThrust, 1)`, scaled by how well the nose is aligned with `a_cmd` (`cos` of the angle, floored at 0). A missile pointing the wrong way does not burn.
- `t_go` comes from `FiringSolutionSolver` with the missile's current speed as `s`, falling back to `|d| / closingSpeed` when the solver has no root.
- The gravity term is small for close shots but reaches meters to tens of meters across the bubble in low orbit (section 1.2), so it is always included.
- `closingBurn` is a per-def choice. "Sprint" missiles burn everything early for maximum closing speed (harder to point-defend, worse terminal agility). "Hunter" missiles hold Δv for the terminal phase.

### 4.4 Countermeasures and point defense
- **Decoys** are short-lived entities with a `SignatureComponent` (radar/thermal strength). In terminal, a seeker locks the strongest signature in its FOV, weighted by a per-seeker `discrimination` value. Decoys that outshine the ship steal the lock.
- **Point defense** is ordinary gunfire on a missile track. The solver handles accelerating targets via `a_t`, which is why the tracker's gamma term matters. A missile dies when hits reduce `hitPoints` to 0.
- **Burnout is a weakness.** A coasting missile flies a predictable ballistic path, so PD solutions on it are near-perfect.

### 4.5 Detonation
On contact or proximity fuse, the missile emits an `ImpactEvent` (contact) or a `DetonationEvent` (proximity, a point in space) carrying its warhead def. Section 5 resolves both.

### 4.6 HUD missile arcs
`ui-hud` draws predicted missile paths by forward-simulating each tracked missile with the same integrator and guidance law for a few seconds, a few dozen steps per missile. This is cheap at the missile counts expected. The same prediction drives threat warnings ("impact in 6 s, port side").

---

## 5. Hull Hits into the Interior Grid
This section expands the original "Penetration Raycasting" algorithm into a full damage model.

### 5.1 `ImpactEvent`
| Field | Meaning |
|---|---|
| `shipUuid` | Struck ship |
| `worldPoint`, `worldNormal` | From the swept raycast (2.3) |
| `relVelocity` | `v_proj − (v_ship + ω × r_hit)`: projectile velocity relative to the hull surface it hit |
| `mass`, `defId` | Projectile or missile mass and its def (damage type, penetration factor, warhead) |
| `tick` | For deterministic ordering |

### 5.2 World to grid
1. Transform `worldPoint` and `relVelocity` into the ship's local frame using the ship body's transform (inverse rotation for the velocity).
2. Map local meters to tile coordinates: `tile = floor((local − gridOriginLocal) / tileSize)`.
   - **The grid uses Box2D's orientation:** tile [0][0] is the **bottom-left**, +x is the body's local +x, and **+y points up**, the same as Box2D. Grid coordinates are body-local coordinates offset and divided by tile size, with no axis flip anywhere.
   - `gridOriginLocal` is the body-local position (meters) of tile [0][0]'s bottom-left corner. `ShipGridComponent` has no such field yet, so the chassis blueprint should provide it.
3. The Box2D hull polygon and the tile grid will not match exactly. Start the traversal one tile *outside* the impact point, step inward along the ray, and skip `isVoid()` tiles until the first non-void tile. That first tile is the entry tile.

### Construction model the damage code reads
These are Joe's decisions on how ships are built. Damage resolution depends on all of them.

- **Two kinds of wall.** A **full-tile wall** fills a whole tile, the same as today's `HULL` and `INTERNAL` walls. An **edge wall** is a thin wall that sits on the boundary *between* two tiles, so a room can be divided without spending a tile on the wall. The grid needs edge storage for this: one wall slot per tile edge (`edgeH[y][x]` for horizontal edges and `edgeV[y][x]` for vertical ones), each holding a weight class, integrity and an optional bulkhead.
- **Weight classes.** Every tile and wall piece is **light, medium or heavy**. The class sets its mass (which feeds the ship's Box2D mass and inertia), its hit points and its armor. In the penetration model (5.4), hit points set how much damage the piece absorbs before it is destroyed, and armor sets how much penetration it removes from a round. Values come from the material table (section 6), one entry per class per material.
- **Multi-tile hull pieces.** Hull and armor can be pieces bigger than one tile, such as a **1×2 wedge** that gives a sloped face. A piece covers several tiles but is one object with one integrity pool. Its outer face has its own surface normal, and the incidence and ricochet checks (5.3) use that normal instead of the tile's square edge. A wedge therefore really does deflect shots and present more armor to them.
- **System mounts.** Ship systems install into fixed-size mounts:

| Size | Primary mount (square) | Auxiliary / utility slot (rectangle) |
|---|---|---|
| S | 1×1 | 1×2 |
| M | 2×2 | 2×3 |
| L | 3×3 | 3×7 |
| XL | 5×5 | 5×12 |

  Primary systems are things like shield emitters, generators and turret mounts. Auxiliary and utility systems go in the rectangular slots. A rectangular slot can be placed in either orientation. Every tile inside a mount's footprint belongs to the installed system, so a hit on any of those tiles damages it. This replaces today's single anchor tile on `SubsystemComponent`, which needs a footprint (origin, size, rotation) instead.

### 5.3 Penetration budget
- Kinetic energy at impact: `E = ½ · m · |v_rel|²`. Closing speed is everything: a head-on pass hits far harder than a tail chase with the same gun.
- Penetration budget: `P = E · penetrationFactor`. Armor-piercing rounds have a high factor, while HE and fragments have a low one.
- **Incidence:** `θ` is the angle between `−v̂_rel` and the hull normal (a wedge's sloped face normal where the hit lands on one). Effective hull resistance is `R_hull / max(cos θ, 0.2)`, so sloped hits face more armor.
- **Ricochet:** if `θ > ricochetAngle` (per def, ~70°) and `P` is below the effective hull resistance, the round glances. Reflect `v_rel` about the normal, scale it by the def's `ricochetRestitution`, put it back in world frame, and respawn it as a projectile. The hull tile takes a small integrity hit and nothing goes inside.

### 5.4 Traversal (DDA)
Use **Amanatides & Woo grid traversal (DDA)** along `v̂_rel` in tile space. Unlike Bresenham, it visits *every* tile the ray crosses, including corner-clipped ones, so thin diagonal walls cannot be skipped.

**Edge walls:** whenever the DDA crosses a tile boundary, it first checks that boundary's edge wall. An intact edge wall is resolved exactly like a tile, with its own hardness and integrity, before the ray enters the next tile.

At each tile, resistance comes from a material table (section 6), set by the piece's material and weight class and scaled by its current integrity. A tile that is part of a multi-tile piece or mount draws on that piece's single integrity pool:

```
R_tile   = hardness(tile) × tile.integrity
absorbed = min(P, R_tile)
tile.integrity -= absorbed / hardness(tile)     // reaches 0 exactly when P ≥ R_tile
P -= absorbed
```

Then apply the tile's contents, each taking part of `absorbed` as damage:
- **Subsystem:** `SubsystemComponent.health -= absorbed / subsystemHardness(type)`. Below `disableThreshold` (e.g. 0.3) set `isOperational = false`, and at 0 it is destroyed. The lookup uses a per-ship tile→subsystem index built from `ShipSubsystemRegistryComponent`. Subsystems currently only record an anchor tile, so this index needs multi-tile footprints from the blueprint to be accurate.
- **Crew:** each occupant from `GridOccupantQuery.at(x, y)` takes damage scaled by `absorbed`, applied through `CrewDamageSink` (5.9). Crew have low hardness, so a round passes through people with little energy loss.
- **Power conduit:** a `POWER_CONDUIT` tile at integrity 0 stops conducting. `PowerRoutingSystem` must skip zero-integrity tiles (see 5.8), and combat requests a reroute on the next routing tick.
- **Fire:** with probability `(fireChance(damageType) + material.fire_chance) × absorbed / R_tile`, call `tile.ignite()`, which starts a small fire if the tile has a flammable covering (`isFlammable()`). There is no fire in vacuum: `GridHazardSystem` smothers a fire whose compartment is short of oxygen.

Stop when `P ≤ 0`, or apply the def's fuse first (`DELAYED(depthTiles)` detonates at that depth; see 5.5).

**Breaches and exit wounds:**
- A `HULL` tile driven to 0 integrity already reads as `isHullBreach()`. The existing `GridHazardSystem` vents it on its next tick with no extra wiring.
- If the ray reaches void beyond the far hull with `P > 0`, the round **over-penetrates**. The exit hull tile is breached too, and the round respawns in space on the far side with `|v_exit| = |v_rel| · sqrt(P_left / P_start)` in the same direction, converted back to world velocity. It can hit something behind the ship.
- **Along the skin** (round 2 playtest): the hull is a thin skin drawn a tile thick, so a ray that steps from one hull tile to the next along the hull (both facing open space on the same side, square to the step) is grazing the plating. It leaves the ship there with what it has left rather than boring down the row. Before this, a railgun slug fired down a corvette's length took out the whole top or bottom row of hull, and two such slugs made a ship surrender.
- **How much machinery one ray wrecks** (`SUBSYSTEM_TILE_SHARE`): a ray crossing a system's tile takes at most a tenth of the system's hardness from it (scaled by its health), however much energy it carries: a slug drills through machinery rather than wrecking all of it. At a quarter, three railgun slugs through a corvette's nose shut its reactor down and fights ended in seconds; at a tenth a ship takes many hits, and its crew have fires, breaches and repairs to deal with meanwhile. Rounds too weak to reach the cap are unchanged.
- **Spall (optional, per def):** when a hull tile is penetrated, spawn `spallCount` short secondary rays in a cone of `±spallAngle` around the shot line. They share `spallFraction × absorbed` and run the same traversal. This is how a "small" hit still shreds the compartment behind the armor.

### 5.5 Explosive damage
Blast behaves differently inside and outside the hull. This is a hard-sci-fi feature worth leaning into:
- **In vacuum** (proximity-fused missile, or contact HE on the outer hull), there is no air and so no overpressure. Damage is **fragments only**: `fragmentCount` small penetrator rays cast from the detonation point toward the ship within the warhead's `fragmentCone`. Each is a mini-projectile with its own `P`, resolved as in 5.3–5.4. Near misses pepper a whole flank with shallow hits.
- **Inside the ship** (delayed-fuse or after penetration), add **overpressure**. Flood-fill from the detonation tile out to `blastRadius` tiles, blocked by intact walls and sealed bulkheads. Each reached tile takes `blastDamage × falloff(distance) × pressure`, where `pressure` is `grid.pressureRatioAt(x, y)` (1 at standard, 0 in vacuum). Air pressure carries the blast, so **a compartment that is already vented barely feels it.** That gives crews a real tactical choice: vent a section before taking hits.

### 5.6 Energy weapons (burn-through)
A beam does not cast a penetration ray up front. Each tick it adds `power · dt` to the struck hull tile's absorbed energy, using the same `R_tile` accounting with a per-material `ablationFactor`. When the tile reaches 0, the beam advances to the next tile along its line and keeps burning inward. Beams set fires readily (high `fireChance`) and rarely over-penetrate. Their counter is motion: a spinning or juking target spreads the heat across many tiles. As built, a wall loses `energy × ablation_factor / (hit_points + armor)` of its integrity with no armor strip, so armor slows a beam rather than stopping it. Machinery or a person in the beam's path stops it, open air and holes let it through, and heat soaking through a wall can light the open tile behind it. Beams don't hit missiles or push the hull.

### 5.7 Momentum transfer
Momentum is conserved. On every impact, apply `m · (v_in − v_out)` as a linear impulse at `worldPoint` on the ship's Box2D body, where `v_out` is zero, the ricochet velocity, or the exit velocity. Off-center heavy hits spin a ship. Big kinetic hits are felt as well as seen, and the helm AI has to correct for them.

### 5.8 Knock-on effects in other modules
Some interior rules need small changes in `interior-grid` for damage to read correctly. They are listed here so the owning module can pick them up:
- An `INTERNAL` wall at integrity 0 must stop blocking fire, vacuum and walking. Today the code checks `wallType` only.
- `PowerRoutingSystem` floods through `POWER_CONDUIT` and `FLOOR` tiles. It should skip tiles with integrity 0.
- `ShipGridComponent`'s comment says tile [0][0] is the top-left. It should say bottom-left with +y up, to match Box2D (5.2). Grid rendering and the `Main.java` demo should follow.
- Combat sets a `gridDirty` flag so power can reroute immediately after heavy damage, without waiting for the 1 s tick (optional).

Crew-facing outputs, through `DamageTaskSink`:
- `SealBreachTask` per new hull breach.
- `RepairTask` per subsystem below 1.0 health.
- `ExtinguishTask` per new fire. `GridHazardSystem` will also post these as fire spreads.

Outputs to `ui-hud` and `audio-spatial`: a `HullHitEvent` per impact (tile, severity), so the wireframe minimap flashes the hit and audio plays a located, muffled impact.

### 5.9 Crew injury
Penetrating hits, spall, fragments and interior blast all damage crew. Combat only applies the damage. The crew states below belong to `ai-crew`, and combat relies on them behaving this way:

| State | Entered when | Effect |
|---|---|---|
| **Conscious** | Health above `unconsciousThreshold` | Normal |
| **Unconscious** | Health at or below `unconsciousThreshold` | Drops its current task, cannot act or move on its own, still occupies its tile |
| **Dead** | Health reaches 0 | Removed from duty |

- Unconscious crew are still on their tile, so **further hits can kill them**. A second round through the same corridor is deadlier than the first.
- **Asphyxiation** is the other route to death. Crew on a tile with too little oxygen lose health over time (as the oxygen ratio of their compartment, `grid.oxygenRatioAt(x, y)`, falls; the hazard system tracks it), so they pass out first and then die. A hull breach therefore threatens crew who were never in the line of fire, and unconscious crew in a venting room need rescue.
- Combat posts a `RescueTask` (move an unconscious crew member to a pressurized tile) alongside its other damage tasks. Whether and how unconscious crew recover is `ai-crew`'s call.
- `CrewDamageSink.applyDamage(crewEntity, amount, cause)` is the third interface combat owns and `ai-crew` implements, next to `GridOccupantQuery` and `DamageTaskSink`.

### 5.10 Ordering and determinism
Per 60 Hz tick:

1. `TidalGravitySystem`
2. Box2D step (`PhysicsUpdateSystem`)
3. `BallisticsSystem` / `MissileGuidanceSystem`
4. `ImpactResolutionSystem` drains that tick's events in `tick, then shipUuid` order

Hazards then evolve on their own tick (0.25 s). All random draws (fire, spall, gunnery jitter) come from a per-ship seeded RNG, so replays, saves and unit tests are reproducible.

---

## 6. Data Definitions (TOML)
All tuning lives in data so modders can add weapons without code. These are illustrative shapes for `modding-data`'s `WeaponDef` family:

```toml
[weapon.railgun_mk1]
mount = "turret"            # turret | spinal | launcher | beam
slew_rate_deg = 40
firing_arc_deg = 270
cooldown = 1.5
power_draw = 2.0e6          # W while charging
projectile = "slug_tungsten_20kg"

[projectile.slug_tungsten_20kg]
mass = 20.0
muzzle_speed = 6000.0
damage_type = "kinetic"
penetration_factor = 1.0
ricochet_angle_deg = 72
ricochet_restitution = 0.6
spall = { count = 6, angle_deg = 25, fraction = 0.3 }
max_lifetime = 30.0

[missile.harpoon]
dry_mass = 150.0
propellant_mass = 250.0
exhaust_velocity = 3000.0   # Δv ≈ 2.9 km/s
max_thrust = 60000.0
turn_rate_deg = 90
seeker = { type = "radar", fov_deg = 30, range = 20000, discrimination = 0.6 }
nav_gain = 4.0
terminal_reserve = 0.3
arming_delay = 0.6
fuse = { type = "proximity", radius = 15 }
warhead = "frag_heavy"

[warhead.frag_heavy]
fragment_count = 40
fragment_mass = 0.2
fragment_cone_deg = 60
blast_radius_tiles = 3      # only matters if detonated inside a pressurized hull
blast_damage = 400000.0

[material.hull_steel]
fire_chance = 0.0
ablation_factor = 1.0
# per weight class: mass per tile (kg), hit points (J to destroy), armor (penetration removed, J)
light  = { mass = 800.0,  hit_points = 2.0e6, armor = 1.0e6 }
medium = { mass = 2000.0, hit_points = 5.0e6, armor = 3.0e6 }
heavy  = { mass = 5000.0, hit_points = 1.2e7, armor = 8.0e6 }
```

Hardness values per tile (`HULL`, `INTERNAL`, `FLOOR`, conduit, each subsystem type, crew) come from `material.*` entries referenced by the chassis blueprint. Armor upgrades are a matter of pointing hull tiles at a harder material.

---

## Implemented Components
All in `combat-sys/src/main/java/com/ringsofiron/combatsys` (`components` package unless noted).
- `WeaponMountComponent`: one weapon: hardpoint offset, mount type, firing arc, slew rate, cooldown, power draw, assigned track, def ID. A ship holds its mounts in a `WeaponsComponent`, since Ashley allows one component of each type per entity.
- `ProjectileComponent`: ballistic state and damage properties (2.4).
- `MissileComponent`: propulsion, attitude, seeker, fusing and phase state (4.1).
- `SensorComponent`: range, noise, fix interval and `trackingSkill`, the locked target, and its `Track`s (`fire` package: filtered `p, v, a`, confidence, last observation time). Tracks live on the sensor rather than as a component of their own.
- `SignatureComponent`: radar and thermal signature for seekers and decoys.
- `DecoyComponent`: a countermeasure flare in flight (4.4).

## Implemented Systems
- From the orbital plan, not owned here: `TidalGravitySystem`, `FloatingOriginSystem` (anchor rebase), and the shared `TidalField.accelAt` that combat calls (all in physics-exterior).
- `SensorTrackingSystem`: produces noisy observations and runs the alpha-beta-gamma trackers.
- `WeaponControlSystem`: solves, slews, applies gunnery error, fires, and applies recoil.
- `BallisticsSystem`: integrates projectiles and runs swept hit tests.
- `BeamSystem`: traces energy weapons each step (2.5, 5.6).
- `MissileGuidanceSystem`: phases, ZEM guidance, burn and mass flow, seeker lock, fusing, and decoy flight.
- `ImpactResolutionSystem`: ricochet, DDA penetration, explosive and beam damage, momentum transfer, and knock-on events.

- **In the game** (round 2, core): `ShipDisablement` reads what combat leaves of a ship and marks it disabled (hull below 0.45, no reactor running, or no crew conscious) or wrecked (hull below 0.25). It also makes an NPC surrender when it is losing: hull below 0.55, fewer than half its crew conscious, or every gun silenced. A disabled ship stops fighting but stays in the world as a target for boarding and salvage. `HostileTraffic.firepower` rates a ship by its weapons' prices, with point defense at half; pirates compare it before picking a fight. A pirate or patrol shot at fights back. The details are in `documentation/progression/Design.md`.
- **New weapon** (round 2): `chaingun_mk1`, the light raider's turret: 30 mm autocannon rounds every 0.15 s, 270° arc, 3 km range, 18,000 cr.

## Utilities
- `FiringSolutionSolver`: pure intercept math, shared by AI, HUD and missiles.
- `GridRayTraversal`: Amanatides & Woo DDA over `ShipGridComponent` (in interior-grid, so ai-crew's line of sight shares it).

## Building and Testing This Module in Isolation
Mock `TidalField` (zero, or a constant gradient), use a dummy `ShipGridComponent` with hand-placed tiles, and stub `GridOccupantQuery` / `DamageTaskSink`. Unit tests worth having early:
- **Solver:** stationary target gives a straight shot; a crossing target matches the closed-form lead; no root when the target is faster and receding; iteration converges with constant `k`.
- **DDA:** visits every crossed tile, including exact-corner cases.
- **Energy accounting:** total absorbed + exit energy never exceeds `P_start`, and no energy is created.
- **Breach integration:** a HULL tile at 0 integrity makes `GridHazardSystem` vent the adjoining room on its next tick.
- **Vacuum blast:** the same warhead does less overpressure damage in a vented room than a pressurized one.

Run `gradlew :combat-sys:test` for the solver, traversal, penetration, fire-control, data and integration tests.

## Open Questions
None in this module right now. The construction decisions in section 5 (edge walls, weight classes, multi-tile pieces and mount footprints) are now in the `interior-grid` design too.

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
- **Ramming.** When `KinematicTransientSystem` finds a fast pass hitting a ship or asteroid, it emits a normal `ImpactEvent` whose `mass` is the smaller body's mass. At km/s, ramming is resolved by section 5 like a very large slug and will usually gut both ships along the contact line. Hulls meeting below 1 km/s are Box2D bodies, and crash as section 5.12 describes.

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

Lasers are point defense too (Joe, Oct 2026). A beam firing at will turns on the most urgent incoming missile inside its reach before its target, picked as a point-defense mount picks one, with point defense's reaction time and no effective-range check; with no missile coming it goes back to its target. A trigger, a hand aim or a target given to the mount keeps it on its target. `BeamSystem` tests the beam against every missile's `hitRadius` circle as well as the hulls, and a missile nearer than any hull takes the burn off its `hitPoints` (`BallisticsSystem.burnMissile`). The beam publishes an `InterceptEvent` (`beam = true`) when it first touches a missile and when it breaks one up, not every tick it burns. A 4 MW laser takes a harpoon's 0.2 MJ in a tick and a torpedo's 30 MJ in about 8 s, so it is worth most against single heavy missiles; how lasers and point defense weigh against missiles overall is left for a later pass.

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

**The trigger and the hand** (round 11). A pulled trigger fires only once the barrel is on its solution. A turret with no target holds rather than firing down its resting barrel (the starter corvette's laser rests on its starboard beam, and burned its pulses off to the side whenever the raider was past 3 km). Only a spinal gun with no target fires straight ahead. A target past the gun's max range is no bar to the trigger: the turret turns onto it and fires, a gun as far as its round flies and a beam burning short of it. A mount can also be aimed by hand (`WeaponMountComponent.manualAim`, `aimPoint`): it slews onto that point with no lead, and its trigger fires once the barrel is within `max(0.01 rad, 3 · spread)` of it.

**Hit chance and range** (round 11, `fire.HitChance`). A weapon's `max_range` is a guide, not a limit (point defense still picks its threats inside it). A shot misses sideways by the barrel's error, `2 · spread` widened for a green gunner as in the shot itself, times the range. A target that dodges may be anywhere up to `a t² / 2` off its aim point while the round flies, `a` its strongest thrust over its mass, `t` the flight time. It is hit if the shot passes within its half-width, 0.6 of its signature radius. A beam has no flight time to dodge in, and hits nothing past its reach. A gun firing more than a round a second counts a second's rounds together: its chance times its rounds a second, up to 1. Firing at will, a gunner opens up inside the mount's effective range on its target, where that chance, against the target dodging, falls to `HitChance.OPEN_FIRE` (0.5). It is worked out once per target and refreshed every second. At 0.5 the guns open up on a corvette about where their max ranges used to put them: roughly 3.7 km for a heavy cannon, 4.4 for a coilgun and 9 for a railgun. Core's `FireArcOverlay` shows the player this chance without covering the play area. Each gun's firing arc is two faint edges a short way out from its muzzle. With a target locked, two lines run from the muzzle past either side of the predicted intercept point, square to the line of fire, at the target's half-width plus `a t² / 2`: where it would be if it burned that way to dodge while the round flew. They are drawn in the color of the gun's chance on it now: green where it is 0.9 even dodging, red where it is 0.01 even holding still, yellow between, on a log scale.

**Crewed guns** (round 11, core's `GunCrews`). On the player's ship a gun fires at will only while somebody works it: a crew member manning its own turret mount (crew now man turret mounts as they man consoles; marines and crewmen take them after the weapons console), at that gunner's skill, or else one at the weapons console, which works every gun at half skill (`GunCrews.REMOTE_SKILL`). It engages the locked target if that is fighting the player and inside the gun's effective range on it, else the nearest ship that is. The crew don't open a fight: a ship counts once its captain is after the player, or it is marked hostile and has them locked. Guns hold while docked. Seated at the weapons console the player has every gun and launcher in hand; seated at a gun (V beside its turret mount, or "Take the gun" in its console window), only that one. Guns in hand follow the pointer, leading a contact under it, and fire only on the trigger (Space or the left button). Point defense stays automatic at the console, but goes in hand seated at its own mount: while held it leaves the missiles to the player, and once let go it picks them again (`WeaponControlSystem` hunts missiles only for a point-defense mount with no target and no hand aim, and a beam does so first only firing at will, so a laser on the trigger stays on its target). Launchers always wait for the player.

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
- **Magazines and salvos** (round 11): a launcher holds `magazine` missiles (`harpoon_launcher` 4, a second apart; `dart_rack` 6, 0.6 s apart) and is silent once empty; a station restocks the player's launchers while docked, one missile every 2 s. The player's missile key launches a salvo of up to 4 from one launcher. Each missile a launcher throws gets its own `approachAngle` (0, +15°, −15°, +30°, −30°, round again): it steers for a point that far off its target's bearing, which closes onto the target inside 1.5–3 km, so a salvo spreads on the way and comes in from several sides.

### 4.2 Flight phases
1. **Launch (cold).** The missile is ejected at `ejectSpeed` on top of the launcher's velocity, and its motor stays dark until `armingDelay`. This keeps it from torching or hitting its own ship. Ejection reaction is applied to the launcher as an impulse. As built (round 6), a missile also never strikes its own launcher at all: an aft launcher's missile turning onto a target ahead flew up its own ship's hull and blew the frigate's bow railguns off.
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
- **Who a mount firing at will leaves alone** (round 9, `WeaponControlSystem.setSpared`): a mount that isn't on the trigger never takes a spared ship as its target, though the trigger still fires on it. The game spares a ship that has struck, for the player's guns, so their point defense and crewed guns never pick a prize. Round 10: a fallen pirate base's silenced batteries count as struck too (`Struck` in core), since they never surrender; before, our guns firing at will went on hitting them after the fall. `MissileGuidanceSystem.setSpared` does the same for seekers: a missile never locks a ship its launcher spares unless it was fired at it. The game wires it for the fallen base's batteries only, so a heavy missile fired at a raider no longer bursts on a battery beside it. Point defense only ever takes missiles; its rounds that fly on past a dart and hit a ship behind it are stray fire (`ImpactEvent.pointDefense`).

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
| `aimedAt`, `pointDefense` | What the weapon was engaging when it fired (null for a shot down the barrel) and whether it was a point-defense mount. Kept by a missile's fragments, by its `DetonationEvent` and by ricochets, so the game can tell a stray hit from an aimed one (round 9) |

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
- **Power conduit:** a round or fragment crossing the tile strikes the conduit across it (`TileConduit`) one time in four (`CONDUIT_HIT_CHANCE`: a cable run fills about a quarter of a tile, where a person fills a bit less than half, `CREW_HIT_CHANCE` 0.4). A strike takes what the deck fittings take (or, laid on a wall, what got through the wall's armor) out of the conduit's own `CONDUIT_HIT_POINTS` (0.4 MJ, two strikes across open deck), so about eight rounds cross its tile before it is cut: the deck and the conduit each keep their own condition. A cut conduit stops conducting and combat requests a reroute on the next routing tick. A beam crossing the deck strikes it on the same odds and burns into it; a crush crushes it apart from the tile, always; a blast's overpressure does not reach it.
- **Fire:** with probability `(fireChance(damageType) + material.fire_chance) × absorbed / R_tile`, call `tile.ignite()`, which starts a small fire if the tile has a flammable covering (`isFlammable()`). There is no fire in vacuum: `GridHazardSystem` smothers a fire whose compartment is short of oxygen.

Stop when `P ≤ 0`, or apply the def's fuse first (`DELAYED(depthTiles)` detonates at that depth; see 5.5).

**Breaches and exit wounds:**
- A `HULL` tile driven to 0 integrity already reads as `isHullBreach()`. The existing `GridHazardSystem` vents it on its next tick with no extra wiring.
- If the ray reaches void beyond the far hull with `P > 0`, the round **over-penetrates**. The exit hull tile is breached too, and the round respawns in space on the far side with `|v_exit| = |v_rel| · sqrt(P_left / P_start)` in the same direction, converted back to world velocity. It can hit something behind the ship.
- **Along the skin** (round 2 playtest): the hull is a thin skin drawn a tile thick, so a ray that steps from one hull tile to the next along the hull (both facing open space on the same side, square to the step) is grazing the plating. It leaves the ship there with what it has left rather than boring down the row. Before this, a railgun slug fired down a corvette's length took out the whole top or bottom row of hull, and two such slugs made a ship surrender.
- **How much machinery one ray wrecks** (`SUBSYSTEM_TILE_SHARE`): a ray crossing a system's tile takes at most a tenth of the system's hardness from it (scaled by its health), however much energy it carries: a slug drills through machinery rather than wrecking all of it. At a quarter, three railgun slugs through a corvette's nose shut its reactor down and fights ended in seconds; at a tenth a ship takes many hits, and its crew have fires, breaches and repairs to deal with meanwhile. Rounds too weak to reach the cap are unchanged.
- **Spall (optional, per def):** when a hull tile is penetrated, spawn `spallCount` short secondary rays in a cone of `±spallAngle` around the shot line. They share `spallFraction × massScale × P_left`, where `P_left` is what the round still carries past that tile, and that energy comes out of the round. This is how a heavy, slow shell shreds the compartment behind the armor. A light round throws less: `massScale = m / (m + 5 kg)` (`SpallDef.massScale`) scales both the share and the ray count (at least one), so a 0.67 kg railgun slug keeps nearly all it has for the line it drills, and a missile's fragments throw almost none. (Before Joe's rule of Oct 2026, spall was a share of what the hull tile absorbed, whatever the round weighed.)

### 5.5 Explosive damage
Blast behaves differently inside and outside the hull. This is a hard-sci-fi feature worth leaning into:
- **In vacuum** (proximity-fused missile, or contact HE on the outer hull), there is no air and so no overpressure. Damage is **fragments only**: `fragmentCount` small penetrator rays cast from the detonation point toward the ship within the warhead's `fragmentCone`. Each is a mini-projectile with its own `P`, resolved as in 5.3–5.4. Near misses pepper a whole flank with shallow hits.
- **Inside the ship** (delayed-fuse or after penetration), add **overpressure**. Flood-fill from the detonation tile out to `blastRadius` tiles, blocked by intact walls and sealed bulkheads. Each reached tile takes `blastDamage × falloff(distance) × pressure`, where `pressure` is `grid.pressureRatioAt(x, y)` (1 at standard, 0 in vacuum). Air pressure carries the blast, so **a compartment that is already vented barely feels it.** That gives crews a real tactical choice: vent a section before taking hits.

### 5.6 Energy weapons (burn-through)
A beam does not cast a penetration ray up front. Each tick it adds `power · dt` to the struck hull tile's absorbed energy, using the same `R_tile` accounting with a per-material `ablationFactor`. When the tile reaches 0, the beam advances to the next tile along its line and keeps burning inward. Beams set fires readily (high `fireChance`) and rarely over-penetrate. Their counter is motion: a spinning or juking target spreads the heat across many tiles. As built, a wall loses `energy × ablation_factor / (hit_points + armor)` of its integrity with no armor strip, so armor slows a beam rather than stopping it. Machinery or a person in the beam's path stops it, open air and holes let it through, and heat soaking through a wall can light the open tile behind it. Beams don't push the hull. A beam does burn a missile it crosses (section 2.5).

### 5.7 Momentum transfer
Momentum is conserved. On every impact, apply `m · (v_in − v_out)` as a linear impulse at `worldPoint` on the ship's Box2D body, where `v_out` is zero, the ricochet velocity, or the exit velocity. Off-center heavy hits spin a ship. Big kinetic hits are felt as well as seen, and the helm AI has to correct for them.

### 5.8 Knock-on effects in other modules
Some interior rules need small changes in `interior-grid` for damage to read correctly. They are listed here so the owning module can pick them up:
- An `INTERNAL` wall at integrity 0 must stop blocking fire, vacuum and walking. Today the code checks `wallType` only.
- `PowerRoutingSystem` floods through live conduits only, never bare floor, so a cut or shorted conduit is left out (done).
- `ShipGridComponent`'s comment says tile [0][0] is the top-left. It should say bottom-left with +y up, to match Box2D (5.2). Grid rendering and the interior demo should follow.
- Combat sets a `gridDirty` flag so power can reroute immediately after heavy damage, without waiting for the 1 s tick (optional).

**Shorts and bursts (round 5).** Every hit on a system, by a round, spall, a blast or a beam, rolls for a short circuit (`DamageStates.maybeShort`), and every hit on a powered conduit across a tile that leaves it uncut rolls too (`maybeShortConduit`). Every hit that leaves a tank below its burst threshold rolls for a burst instead. Bursts go off once the round and its secondaries are done: the tank vents (`DamageStates.burst`), and a large one (500 kg or more) sets off a small blast of its own, `min(1 MJ, 500 J/kg)` at its tile out to 1 tile, through the same overpressure flood as a warhead, so it can hurt crew (`HitCause.BLAST`) and burst another tank (up to 8 in a chain). A burst's blast is the tank's energy, not the round's, so `Result.absorbed` leaves it out. `DamageReport.shorts` and `bursts` list the tiles.

Crew-facing outputs, through `DamageTaskSink`:
- `SealBreachTask` per new hull breach.
- `RepairTask` per subsystem below 1.0 health.
- `ExtinguishTask` per new fire. `GridHazardSystem` will also post these as fire spreads.

Outputs to `ui-hud`, `audio-spatial` and the deck view: a `HullHitEvent` per impact (tile, severity, damage type, and where it struck, which way it was going and the hull's normal there, in the grid's tile space; where an over-penetrating round left; the tile a warhead went off in inside), so the wireframe minimap flashes the hit, audio plays a located, muffled impact, and the deck shows it struck (lighting's "Hits seen from the deck"). The console's `blast()` publishes one too.

### 5.9 Crew injury
Penetrating hits, spall, fragments and interior blast all damage crew. Combat only applies the damage. The crew states below belong to `ai-crew`, and combat relies on them behaving this way:

| State | Entered when | Effect |
|---|---|---|
| **Conscious** | Health above `unconsciousThreshold` | Normal |
| **Unconscious** | Health at or below `unconsciousThreshold` | Drops its current task, cannot act or move on its own, still occupies its tile |
| **Dead** | Health reaches 0 | Removed from duty |

- Unconscious crew are still on their tile, so **further hits make their wounds worse**. A second round through the same corridor is deadlier than the first.
- **Round 5: hits wound.** Since round 5 `ai-crew` turns hit damage into wounds (light, serious, critical) rather than taking health, and only a critical wound left untreated kills (`ai-crew/Design.md` section 5). A crew member takes at most `CREW_RESISTANCE` / `CREW_JOULES_PER_HP` (100 points) from a round, a critical wound; and a round loses half its crew injury for every open tile it crosses after the first (`CREW_FALLOFF_PER_TILE`), so a slug through three crew in a corridor leaves the first critically wounded and the others lightly (50 and 25 points). The round's budget and what it does to machinery and structure are unchanged. A laser's burn arrives as `BURN`, apart from the `FIRE` of standing in a burning room.
- **Asphyxiation** is the other route to death. Crew on a tile with too little oxygen lose health over time (as the oxygen ratio of their compartment, `grid.oxygenRatioAt(x, y)`, falls; the hazard system tracks it), so they pass out first and then die. A hull breach therefore threatens crew who were never in the line of fire, and unconscious crew in a venting room need rescue.
- Combat posts a `RescueTask` (move an unconscious crew member to a pressurized tile) alongside its other damage tasks. Whether and how unconscious crew recover is `ai-crew`'s call.
- `CrewDamageSink.applyDamage(crewEntity, amount, cause)` is the third interface combat owns and `ai-crew` implements, next to `GridOccupantQuery` and `DamageTaskSink`.

### 5.11 Ion hits and shields
- **Ion** (`damage_type = "ion"`, the `ion_bolt` of `ion_mk1`): an ion hit breaks nothing. It stuns every subsystem within `stun.radius_tiles` (2) of where it struck for a random 10 to 30 s (`stun.min_seconds`, `max_seconds`), longer of what is left and the new roll (`IonStun`). Cargo, tanks and docking ports are not stunned. A stunned system is not operational, a stunned reactor feeds no circuit, and `PowerRoutingSystem` counts stuns down and reroutes when one ends. An ion hit is a hull hit with `ion = true` and no severity, and starts no fire.
- **What a stun means:** a ship whose reactor is stunned goes dark (`NO_POWER`) and can be boarded in that window, but a stun is never a surrender or a beaten ship. `FirstPlay.beaten` is false while anything aboard is stunned unless the ship is wrecked, surrendered or abandoned, or disabled for its hull or its crew (round 8: a frigate broken at the raid was kept in the fight by the defence's ion raider and shot to a wreck; only `NO_POWER` waits out a stun), and `ShipDisablement.gunsSilenced` ignores stunned guns, so a bounty is never paid for an ion hit.
- **Shields: the particle cloud** (package `shields`, prototype). A ship's shield is one cloud of charged particles pooled between all its `SHIELD_EMITTER`s, held on a closed curve round the hull (`ShieldPerimeter`): halfway between the ellipse through the corners of the hull's box and a line 2 m off the hull itself, smoothed, and never nearer the hull than 1 m. The full design, worked numbers and Joe's decisions are in the Claude Doc "Shields: the particle cloud"; what the code does:
  - **Mass.** Each emitter adds its `EmitterSpec.capacity` to the pool (S 300 kg, M 900, L 2,700, XL 8,000) and the stock starts full; shield media is bought at stations (not built yet). The stock is in four places: out round the hull (`cloud()`, what protects the ship), `scattered` by hits and being pulled back, `stowed` packed inside the emitters, and `adrift`. The working emitters (on, powered, not broken, stunned or tripped, and paid up) hold as much as their capacities add up to; whatever is out beyond that, the cloud first, goes adrift. Adrift mass is lost over 6 s (e-folding) and pulled back in over 1 s while working emitters have room, so an emitter back within a second or two saves most of it and one gone for good loses it all. A ship that comes into the world with no emitter working starts with its cloud stowed. The masses and the raised order are saved; emitters, regions and heat start afresh on load.
  - **Raising and lowering.** `ShieldModel.raise(ship, up)` is the safe shutdown: lowered, the working emitters pack the cloud into themselves over `STOW_TIME` (3 s), scattered and recaptured mass goes in too, they gather no regions, and stowed mass costs nothing and can't be lost, power or no power. Raised, it comes back out as fast. Docking lowers a ship's shields and undocking raises them again (core's `ShieldDocking`), unless the crew lowered them themselves; a lowered ship can't raise them while docked. The player raises and lowers them with 9 at the helm or the weapons console, and the console's `shields([on], [ship])` does it for any ship and reports where its mass is.
  - **Several emitters.** The cloud is one pool, but its resting layer (what isn't in regions) is not spread evenly. Each emitter holds the curve fully within its `radius` and less beyond, falling off over one radius (e-folding) to no less than a fifth, weighted by its capacity; `ShieldCloudComponent.line` keeps the layer's mass along each of the curve's 96 segments, and it settles toward that weighting over `SPREAD_TIME` (2 s), so it is thicker where emitters overlap. `anchored` is each segment's share held by working emitters. When an emitter stops working, the mass that goes adrift leaves from the stretches only it held first, and the neighbors re-anchor what they have room for, so a dead emitter opens a weak spot where it was instead of thinning the whole bubble. A hit plows the layer out of the stretch it crossed, which refills as scattered mass rejoins and the layer settles. **Trim** (`ShieldModel.trim(ship, Trim)`) leans the resting layer toward the bow, port, stern or starboard: the side it leans to weighs 1.75 and the far side 0.25, within what the emitters there reach, moving mass rather than making any. The player cycles it with 8 at the helm or the weapons console, and the console's `trim([side], [ship])` sets it for any ship. Heat stays per emitter, so the weak one shows. This was the recommendation of the "Multiple shield emitters on one ship" proposal (pool with local anchoring, plus trim); letting overlapping emitters stack their `maxDensity` into a harder face is held back until playtests show it is needed, and one bubble per emitter was rejected because it breaks the hull-hugging curve.
  - **Regions.** `ShieldSystem` (after ballistics, before impacts) looks 2 s ahead along each round's and missile's path relative to the ship and finds where it will cross the curve. The ship's own rounds are not threats and pass out through the cloud. Threats are taken heaviest (kinetic energy) first; each goes to the working emitter in reach (`radius`) closest in bearing from the center of mass with a region slot free (`regions`: S 1 to XL 4), else the next. The emitters may pull 60% of the cloud into regions, shared by weight and capped at `maxDensity` over the region's `width` and the 2 m depth; mass streams in at `2 x clusterSpeed x` the cloud's mass per meter. A region is a raised cosine one width either side of its center, and stands 0.1 s after its round passes inside the curve.
  - **A round** meets the density where its path crossed the curve: the cloud takes `1 - exp(-23 sigma / s_p)` of its energy, `sigma` the areal density (kg/m^2) and `s_p` the round's sectional density (mass over frontal area, from `ProjectileDef.caliber` or a steel slug three calibers long). It plows a 1 m wide channel; that mass is thrown at `sqrt(E_absorbed / m)`, and `exp(-retention / v)` of it is lost for good (retention 400 m/s); the rest is scattered and rejoins over 5 s (e-folding; 20 s if recapture goes unpaid). An unseen round meets only the thin base layer where it crosses: about 5% of an autocannon round. A gathered region takes most of one; a 20 kg slug gets mostly through and tears mass away.
  - **Beams** can't be seen coming: the first touch meets only the base layer and calls up a region there, held 0.5 s after the beam leaves, that thickens while it burns. The cloud takes `1 - exp(-0.012 sigma)` of each tick, and burns off 2e-7 kg per joule taken.
  - **Ion strips the shield** (Joe, Oct 2026), into the mass that comes back, never the mass lost for good. A bolt meeting the cloud discharges it instead of throwing it: one kg for every `ION_STRIP_COST` (4 kJ) it carries, out of the regions within `ION_WIDTH` (3 m) of where it crossed first and then the resting layer there, opening a hole. All of it goes to `scattered` and none to `lost`, and the emitter answering there owes the charge (the same 4 kJ a kg) to pull it back, so it rejoins over 5 s like any scattered mass (20 s unpaid). An ion cannon's bolt (225 kJ) strips some 56 kg, a disruptor's (460 kJ) about 115. A stunned emitter, or one cut off or starved while a reactor aboard is stunned, likewise keeps hold of its share, but only as scattered mass: out of the cloud and protecting nothing while the stun lasts, and back as soon as it wears off. Scattered mass rejoins only as far as the working emitters have room. Before this, a stunned emitter's share went adrift and was lost within seconds, so the disruptor stripped a gunship's whole cloud for good. The bolt itself is still softened as any round, and still stuns only with the chance of what got through.
  - **Power and heat.** Holding costs 1 kW per kg (each emitter its capacity's share of the pool), 0.5 kW more per kg in its regions, and recapturing scattered mass twice its kinetic energy at up to the retention speed. An emitter that can't pay drops its share; with the cloud all gone, it is asked to pay for what it would have to hold to bring it back, so one with nothing to hold doesn't flicker back on. All of it is heat; each emitter sheds its `cooling` and trips at `heatTrip`, coming back at half.
  - Rounds, fragments, missiles, beams and ion bolts are softened; rams are not. An ion bolt soaked by a shield stuns only with the chance of what got through. `HullHitEvent.shielded` carries what the cloud took, for the shimmer sound; the cloud's own hit log (`ShieldCloudComponent.hits`) drives the particles (lighting's "Shield particles"). The gunship carries one small emitter.
  - **Against each weapon** (`ShieldBenchmark`, Oct 2026: a 30 s volley at a pinned ship, shields up against down). A Small emitter (the gunship's 300 kg) takes 10 to 30% off the Small guns' hull damage and nothing off the Medium ones, and a heavy cannon's volley strips it to 5 kg. The reaver's four Medium emitters cut a chaingun's and a coilgun's hull damage to a third, a railgun's and a laser's to about 0.6, and the heavy guns' by 4 to 19%. Light fast rounds (low sectional density) lose the most energy per hit, but the light guns lose the most hull damage, since a round the cloud slows no longer gets through the armor. Ion bolts are still mostly soaked: the reaver's cloud took the stuns from 4 systems to 0 (ion cannon) and 7 to 2 (disruptor). But each bolt now strips the cloud where it lands: an ion cannon's volley thins the gunship's 300 kg to 131 at its lowest and the reaver's 900 to 731, the disruptor's the reaver's to 611, and none of it is lost for good. The disruptor's 3-tile stun can catch a Small emitter and drop the whole cloud, which comes back once the stun wears off. Missiles lose 5 to 30% to the cloud. The full review is the Claude Doc "Weapons against the particle shields".
  - **Not built yet:** buying and refilling media, the HUD gauge and coverage overlay, AI ships trimming toward their attacker, saving heat, heat spilling into the emitter's tile, and moving `EmitterSpec`'s figures into components.toml.

### 5.12 Hull on hull: collisions
Ships are small and slow next to orbital speeds, but two hulls meeting at tens of m/s carry a lot of energy: two 195 t starter corvettes closing at 50 m/s have 122 MJ of relative kinetic energy, about 24 medium hull tiles' worth of hit points. Before this, Box2D bounced every contact under 1 km/s off without damage. Now (`CollisionDamageSystem`, with physics-exterior's crash handling):

**When it is a crash.** A new contact whose closing speed along the contact normal, `v`, is over `NO_DAMAGE_SPEED` (25 m/s), between two bodies at least one of which has an interior grid, is a crash. Contacts on sensors, docking collars and projectiles never are, and a pair that just crashed meets as plain Box2D for a quarter second after. At 25 m/s and under the hulls bump as before.

**The pass-through.** For the tick they meet in, Box2D does not solve the contact: the hulls move on into each other. After the step, `physics.crashes` reports the contact (each body's contact point, the normal, the closing speed), and the system settles it in one go.

**Impulse.** The crash is plastic down to the floor: one impulse `J = mu (v - v0)` along the normal at the contact, with `v0` = 25 m/s and `mu = 1 / (1/mA + 1/mB + (rA x n)^2/IA + (rB x n)^2/IB)` the pair's effective mass there, spin included. A kinematic body (a station) counts as infinitely heavy. Momentum is conserved. What is left of the hulls then meets at 25 m/s as an ordinary Box2D contact and bounces off a little (restitution 0.2).

**Energy.** That impulse takes `E = mu (v^2 - v0^2) / 2` out of the pair's motion. `DAMAGE_SHARE` (0.2) of it goes into the hulls' structure; the rest is the frames flexing, heat, noise and spin, which a tile grid has no way to hold. The hulls split the damage by how hard each is where they met (the hit points of the first solid tile in from the contact): ship A takes `hpB / (hpA + hpB)`, so an armored bow does more to a freighter's flank than the other way round. A body with no grid (a rock) counts as medium hull steel and takes its share into nothing.

**Crush** (`PenetrationResolver.crush`). Each ship's share spreads from the contact over the solid tiles joined to the one first struck, out to three spreads, weighted by a Gaussian `SPREAD_TILES` (1.25 tiles) wide. Each tile takes its share out of its hit points (`hitPoints`, no armor strip: armor is for rounds; a conduit across it takes the same share out of its own 0.4 MJ), and a share bigger than what is left pushes on into the tile behind it along the inward normal, so a hard hit wrecks a column in rather than the ring around it. Machinery on a tile takes the same energy out of its hardness, crew on it are wounded by `100 * E / 5 MJ` (`HitCause.IMPACT`, which wounds), and it can catch fire like any kinetic hit. Tanks can burst.

A hull tile taken to 0 is a breach like any other (`DamageReport.breaches`, so the crew seal it). Collisions never change a hull's shape: no tile is crushed flat or taken out of the collision shape, and the exterior hull art is not deformed.

**Jolt.** Each ship's speed changes by `J / m` in an instant. Everyone aboard takes `JOLT_WOUND_PER_MPS` (1.5) injury for each m/s of it past `JOLT_SAFE_SPEED` (10 m/s), as `IMPACT`.

**What it comes to**, two starter corvettes nose to nose (`CollisionDamageTest`):

| Closing speed | Energy taken | Into each hull | Each hull | Crew (jolt) |
|---|---|---|---|---|
| 25 m/s and under | 0 | 0 | a bump | unhurt |
| 30 m/s | 13 MJ | 1.3 MJ | 22 tiles scuffed, worst 4% | unhurt |
| 50 m/s | 91 MJ | 9.1 MJ | 22 tiles damaged, worst 30%, no breach | a scratch (12.5 m/s) |
| 100 m/s | 457 MJ | 46 MJ | 22 tiles damaged, the bow wrecked and breached, 9 tiles' worth lost | light wounds (37.5 m/s) |
| 200 m/s | 1.9 GJ | 192 MJ | 44 tiles damaged, 38 tiles' worth lost | critical (87.5 m/s) |

Shields don't act on crashes. A crash publishes a `HullHitEvent` per struck ship with `ram = true` (audio plays its own collision sound from physics), and a `DamageReport`. `CollisionDamageSystem.last` keeps the last crash's numbers for tests, demos and the console. The game's debug ram (`WorldDebug.ram`) under 1 km/s flies a Box2D rock at the hull, so it crashes the same way.

### 5.10 Ordering and determinism
Per 60 Hz tick:

1. `TidalGravitySystem`
2. Box2D step (`PhysicsUpdateSystem`)
3. `BallisticsSystem` / `MissileGuidanceSystem`
4. `ImpactResolutionSystem` drains that tick's events in `tick, then shipUuid` order
5. Crashes found in the step are settled after it (`CollisionDamageSystem`, 5.12), in the order Box2D found them

Hazards then evolve on their own tick (0.25 s). All random draws (fire, spall, gunnery jitter) come from a per-ship seeded RNG, so replays and unit tests are reproducible. The RNG's position is not saved: after a load each ship's stream starts again from its seed, so a loaded game rolls the same as itself but not as the run it was saved from. The RNG (`ImpactResolutionSystem.rngFor`) is seeded from the game's combat seed and the ship's UUID while the engine's UUIDs are seeded (a seeded career, and since round 6 the designed start), and from the combat seed and the ship's spawn index otherwise (a world on random UUIDs), so every game rolls the same shorts and bursts each run.

---

## 6. Data Definitions (TOML)
All tuning lives in data so modders can add weapons without code. These are illustrative shapes for `modding-data`'s `WeaponDef` family (the shipped numbers in `weapons.toml` differ: see 6.1):

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

### 6.1 Speed against damage (Joe's rule, Oct 2026)
Every weapon has a size, its `min_mount_size` (S, M, L or XL), the smallest turret mount it fits. Within a size, a gun's sustained muzzle power (its round's `½ m v²` over its cooldown) times its muzzle speed is at most `WeaponDef.speedBudget(size)`: `SMALL_SPEED_BUDGET` (1.8e10 W·m/s) for Small, times the mount's tiles for the others (4 Medium, 9 Large, 25 Extra Large). So a fast round is easy to land at range, with a short flight that leaves the target little time to move off the lead, but its gun does less damage per second. A slow cannon is hard to land from afar and wins a close brawl. Fast guns also fire slowly, so each shot still carries enough to punch a hull. `DataDefsTest` holds every shipped gun to it. Flak (proximity-fused point defense) sits well inside its budget, and ion bolts do no kinetic damage.

| Gun | Size | Round | Every | Sustained | Was |
|---|---|---|---|---|---|
| Chaingun Mk1, Mk2 | S | 3.7 kg at 1.5 km/s, 4.2 MJ | 0.35 s | 12 MW | Mk1 0.4 kg at 1.5 km/s every 0.15 s (3 MW); Mk2 1.75 kg at 1.8 km/s every 0.12 s (24 MW) |
| Coilgun Mk1 (new) | S | 2 kg at 3 km/s, 9 MJ | 1.5 s | 6 MW | |
| Autocannon Mk1 (PD) | S | 0.45 kg at 2 km/s, 0.9 MJ | 0.1 s | 9 MW | 0.4 kg at 1.5 km/s (4.5 MW) |
| Railgun Mk1, Mk2 | S | 0.67 kg at 6 km/s, 12 MJ | 4 s | 3 MW | 20 kg, every 1.5 s (Mk1) and 1 s (Mk2): 240 and 360 MW |
| Heavy railgun | M | 3.45 kg at 5.5 km/s, 52 MJ | 4 s | 13 MW | 60 kg every 3 s (302 MW) |
| Heavy cannon (new) | M | 12 kg at 1.5 km/s, 13.5 MJ | 0.4 s | 34 MW | |

Damage is not linear in energy: a round that cannot get through a tile's armor only wears it (`ARMOR_WEAR`), so each gun's shot has to be heavy enough to punch the hulls it is for. The chaingun's first restatement, 1.6 kg rounds every 0.15 s, only scuffed a raider's medium hull and lost a brawl to the coilgun; 3.7 kg shells at the same 12 MW punch it. `WeaponDuelBenchmark` checks the order that comes out: from 4 km the railgun beats the First Play raider soonest and the chaingun never does, from 1.5 km the other way round, the coilgun between both times.

The patrol gunship and frigate carried railguns, which had been the best gun at any range. Under the rule a heavy railgun does 13 MW, so on the enemy survey a frigate with two (or one and a heavy cannon) lost most raids on the pirate base, whose raiders and platforms brawl. Both carry heavy cannons now: the frigate wins 7 base raids in 10 (6 before the rule) and the gunship 9 (7). The heavy cannon runs at 34 MW, under the 48 its budget allows, because at 48 the frigate won every raid with hull to spare.

A Mk2 keeps its Mk1's round and budget and is the better mount: it slews faster, holds tighter and draws less power. There are no rare weapons any more: every weapon with a price is sold at yards. A beaten ace leaves a spare of its most valuable gun crated in its hold (`PirateAces.dropPart`).

### 6.2 The Oct 2026 weapons
Five weapons from the rebalance proposal, each with turret art, a round's art and a synth patch. Pirates field them from difficulty 1.5 (`loadouts.toml`); the heavy laser is sold at yards only.

| Weapon | What | Fits | Price |
|---|---|---|---|
| `breaching_cannon` | 20 kg `breach_shell` at 1.2 km/s every 2 s (7.2 MW, inside the Medium budget): a delayed fuse goes off one tile inside the hull, 12 fragments and a 1.5 MJ blast over 2 tiles, so its hits wound crew in a pressurized ship | M turret | 55,000 |
| `laser_heavy` | An 8 MW beam for 2 s every 6 s, 4 km, drawing 10 MW: it needs a frigate's reactor | M beam | 110,000 |
| `torpedo_tube` | Two `torpedo`es, 6 s apart: 425 kg, thermal seeker, 3e7 J of armor, a hardened nose (`penetration_factor` 1.0) and a delayed fuse two tiles in, `torpedo_charge` 3 MJ over 4 tiles | S launcher | 75,000 |
| `lance_rack` | Four `lance` sprint missiles 0.8 s apart: radar, 200 m/s^2 closing burn, a light frag warhead on an 8 m proximity fuse | S launcher | 28,000 |
| `ion_disruptor` | A heavier ion bolt every 2.5 s, 4 km: stuns within 3 tiles for 15 to 40 s | S turret | 70,000 |

**Torpedoes punch in.** A missile body that strikes the hull no longer glances off (`MissileGuidanceSystem.bodyDef`: ricochet only past 89 degrees), and one with a delayed fuse goes through the hull on its own kinetic energy (`MissileDef.penetrationFactor`, 0.3 unless set) and detonates `depth_tiles` in. Everything else still goes off on contact. A torpedo needs its run-in: launched 600 m out it is still slow and its warhead goes off on the skin; from 4 km it goes in and puts about 50 MJ into the ship. Its 3e7 J of armor is about thirty flak hits, so a lone pair gets through a gunship's two flak mounts (`ShieldBenchmark`; at 3e6 both were shot down every time).

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

- **In the game** (round 2, core): `ShipDisablement` reads what combat leaves of a ship and marks it disabled (hull below 0.45, no reactor running, or no crew conscious) or wrecked (hull below 0.25). It also makes an NPC surrender when it is losing: hull below 0.55, fewer than half its crew conscious, or every gun silenced. Round 7: a pirate still further from its enemy than its shortest gun reaches (`closingToReach`) presses on past the crew line; the hull line still holds. Round 8: while closing to reach, a gun that a short has only cut off doesn't count as silenced (the short resets within 45 s), and a hostile is "dry" only once its propellant tanks are empty (`ShipDisablement.dry`), not because its engines have no power. Round 10: in reach too, a plain pirate whose crew have a reset in hand waits for it before striking for silenced guns (`ShipDisablement.resettingInReach`). A disabled ship stops fighting but stays in the world as a target for boarding and salvage. `HostileTraffic.firepower` rates a ship by its weapons' prices, with point defense at half; pirates compare it before picking a fight. A pirate or patrol shot at fights back. The details are in `documentation/progression/Design.md`.
- **New weapon** (round 2): `chaingun_mk1`, the light raider's turret: 30 mm autocannon rounds every 0.15 s, 270° arc, 3 km range, 18,000 cr.
- **New weapons** (round 3), each with turret art, a round's art and a synth patch:

| Weapon | What | Fits | Price |
|---|---|---|---|
| `flak_mk1` | Point defense: 1.5 kg shells with a 25 m proximity fuse every 0.35 s, 3 km. A proximity fuse widens the point-defense sweep by its radius, so flak kills missiles a gun would miss | any | 16,000 |
| `dart_rack` | Light missile rack: a 6e4 J `dart` with a light frag warhead every 4 s, 8 km (round 11: six of them, 0.6 s apart) | launcher | 15,000 |
| `railgun_heavy` | 60 kg tungsten slugs every 3 s, 3 MW | `min_mount_size = "M"` | 140,000 |
| `ion_mk1` | Ion bolts every 2 s, 3.5 km, 1.5 MW (5.11) | any | 40,000 |
| `railgun_mk2`, `chaingun_mk2` | Better mounts for the Mk1's rounds, an ace's refits (round 3 made them rare, salvage only; since Oct 2026 yards sell them, see 6.1) | any | 95,000, 32,000 |

  `WeaponDef.fitsMount` ranks mount sizes S < M < L < XL; the yard refuses a weapon too big for a hardpoint's mount (`StationYard.tooSmall`).
- **Loadouts** (round 3, `loadouts.toml`, `[loadout.*]`): a weighted list of weapon sets per ship, each with a difficulty range and an `ace` flag. `Loadouts.pick` draws one by weight from those the difficulty allows, and `Loadouts.apply` fits it to the hardpoints in order, skipping empty entries and weapons too big for a mount.

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

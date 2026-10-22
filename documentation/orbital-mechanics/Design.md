# Orbital Mechanics Module Design

## Overview
This document defines how orbits work in Rings of Iron: how bodies outside the player bubble move "on rails", how they hand off to Box2D when they enter the bubble and back when they leave, which integrator runs where, how time warp works, and how the float/double precision split fits the Floating Origin already chosen in `Technical Design Decisions.md`.

It is a plan, not an implementation. It would live in `physics-exterior` (bubble side) and `macro-sim` (rails side), with the shared math in a small `orbital` package both can depend on. Several points below supersede parts of the existing `physics-exterior`, `macro-sim` and `combat-sys` docs; those are collected in **Changes to Existing Docs** at the end.

## Core Idea
There are two simulations and one source of truth for time:

1. **Rails (double precision, analytic).** Every body outside the bubble (planets, moons, stations, NPC ships, coasting debris) is a Keplerian conic around one parent body. Its position at any time `t` is a pure function `stateAt(t)`. Nothing is integrated, so there is no drift and any amount of time warp costs the same.
2. **Bubble (float precision, Box2D).** A small region around the player is simulated by Box2D in a local frame that is itself *in free fall* along a rails orbit (the **anchor**). Because the frame falls with the player, the huge planetary gravity term cancels out and Box2D only sees the tiny *difference* in gravity across the bubble (tidal force), plus thrust and collisions.
3. **Sim time** is a `long tick` counter at 60 Hz. Seconds are derived as `tick / 60.0` (double) whenever rails code needs them. Never accumulate time in a float.

The key rule: **the large numbers (planet-centred positions, orbital velocities of km/s) never enter Box2D.** They live in doubles on the anchor. Box2D only holds small, relative positions and velocities.

## 1. Units and Scale
- **SI internally.** Metres, seconds, kilograms everywhere in rails code.
- **Box2D length unit = 10 m** (`B2D_METERS_PER_UNIT = 10`). Ships of 20 to 200 m become 2 to 20 Box2D units, inside Box2D's sweet spot of 0.1 to 10 for moving bodies. The reason is Box2D's hard per-step travel limit (`b2_maxTranslation = 2` units per step, a compile-time constant in the native library; worth confirming against the gdx-box2d build in use). At 60 Hz that caps any body's speed in the bubble frame at 2 units/step, i.e. 120 m/s if 1 unit = 1 m, or **1,200 m/s** at 1 unit = 10 m.
- **World scale is 1:1 with real orbital periods.** Low orbit around an Earth-sized planet is about 7.8 km/s with a ~90-minute period, a geostationary orbit takes a day, a lunar transfer about 3 days, and an interplanetary Hohmann transfer months. Nothing in the orbital math is shortened. Three consequences shape the rest of this doc:
  - **Closing speeds can be km/s.** Ships in neighbouring circular orbits drift slowly relative to each other, but crossing orbits (different eccentricity, different direction, or a target on an escape path) close at several km/s, far above Box2D's speed cap. Section 4 covers how such fast passes are handled.
  - **Combat happens after a rendezvous.** For relative speeds Box2D can handle, ships have to match orbits first. The rendezvous itself is long and is mostly flown under rails warp.
  - **Time warp carries the game.** Hours to months between engagements means rails warp needs a very high ceiling and the interior/crew sims need a defined behaviour while it runs (section 6).

## 2. Rails: On-Rails Orbits Outside the Bubble

### Representation (2D Keplerian elements)
Each rails body stores an `OrbitComponent`:

| Field | Type | Meaning |
|---|---|---|
| `parentUuid` | String | Body whose sphere of influence (SOI) it is in |
| `mu` | double | Parent's gravitational parameter G·M (cached) |
| `a` | double | Semi-major axis (negative for hyperbolic) |
| `e` | double | Eccentricity |
| `argPeriapsis` | double | Angle of periapsis from parent's +X axis |
| `meanAnomalyAtEpoch` | double | M₀ |
| `epoch` | double | Sim seconds at which M₀ holds |
| `direction` | int | +1 counter-clockwise, -1 clockwise |

In 2D there is no inclination or ascending node, so these six numbers fully describe the conic. The same struct describes planets around the star, moons around planets, and ships around anything.

### Evaluating position
`stateAt(t)` returns position and velocity *relative to the parent* in doubles:
1. `M = M₀ + n·(t − epoch)`, with `n = sqrt(mu / |a|³)`; wrap elliptic `M` into [0, 2π).
2. Solve Kepler's equation for eccentric anomaly (Newton–Raphson, starting from `E = M` for e < 0.8 and `E = π` otherwise; converge to 1e-12, cap at ~15 iterations). Hyperbolic orbits use the hyperbolic form `M = e·sinh(H) − H`.
3. Convert to position/velocity in the perifocal frame and rotate by `argPeriapsis`.

If near-parabolic orbits turn out to be common (escape trajectories, comets), switch the solver to **universal variables** (Stumpff functions), which handle every conic with one code path. Start with the classic elliptic/hyperbolic split; it is simpler to debug.

The inverse, `elementsFrom(r, v, mu, t)`, converts a state vector back to elements. It is used at every handoff and every impulsive burn.

### Hierarchy and absolute positions
A body's position relative to the star is the sum up its parent chain. Most code should never need that: comparisons are done in the frame of a common parent (usually the SOI body the player is in), which keeps numbers small and precise.

### Sphere of influence (patched conics)
Each massive body has `soiRadius = a_body · (m_body / m_parent)^(2/5)`. A rails orbit only ever feels its parent's gravity. When a trajectory crosses an SOI boundary, the body is re-parented: convert state to the new parent's frame, recompute elements. SOI crossing times are **predicted once** when an orbit is created (root-find on distance-to-SOI-body along the conic) and placed in an event queue, rather than checked every tick.

### Scheduled maneuvers
The GDD calls for "scheduled orbital maneuvers". Out of the bubble these are **impulsive burns**: a `ManeuverNode { time, deltaV (prograde/radial) }` in the same event queue. When it fires, the body's velocity at that instant gets the Δv and its elements are recomputed. Finite-duration burns are approximated as one impulse at the burn midpoint; out of the player's sight the difference is invisible and it keeps rails fully analytic. (If a burn is long enough to matter, split it into a few impulses.)

### The event queue
A single priority queue ordered by sim time holds: SOI transitions, maneuver nodes, predicted surface impacts, and predicted bubble encounters (section 3). Rails code only does work when an event is due or when someone asks for a position. This replaces the `position += velocity * deltaTime` loop in the current `macro-sim` doc.

### Cost and threading
Evaluating one Kepler orbit is on the order of a microsecond, so even 10,000 ships evaluate in ~10 ms. Since positions are a pure function of time, there is nothing to step every tick. Two uses need bulk evaluation:
- **Encounter prediction** for the bubble (below) and for warp drop-out.
- **Map/HUD rendering** of far objects.
Both can run on the existing background thread from the `macro-sim` design, reading immutable `OrbitComponent` snapshots and writing results back for the main thread to apply at a tick boundary. Elements are only changed on the main thread (by events or handoffs).

## 3. The Bubble and the Anchor

### The anchor
The bubble's origin is the **anchor**: a virtual point following a rails orbit around the player's current SOI body. Box2D coordinates are positions and velocities *relative to the anchor*, in Box2D units.

- Anchor world state at time t: `R_a(t), V_a(t) = anchor.stateAt(t)` (doubles, parent-relative).
- Any bubble body's true state: `R = R_a + r·10`, `V = V_a + v·10` (r, v from Box2D).

### Gravity inside the bubble
Box2D's world gravity is set to zero. Every tick, each dynamic body gets
```
a_tidal = g(R_a + r) − g(R_a)        where g(X) = −mu · X / |X|³
body.applyForceToCenter(mass · a_tidal / 10)
```
computed in doubles, cast to float only at the end. Because the anchor is itself falling freely along an exact orbit, this differential term is all that is left. It is small: roughly `3·n²·d` at distance d from the anchor (n = 2π / orbital period), about 0.1 m/s² at 25 km in a 90-minute orbit. It grows with the square of orbital frequency, so low orbit is the worst case; at geostationary altitude it is about 300× smaller. It is still worth applying, because it is what makes two ships parked a few km apart in different orbits slowly drift, and what makes a bubble body's motion agree with its rails orbit at handoff.

This is the "integrator choice" in one sentence: the big Newtonian term is solved analytically on the anchor, and Box2D's built-in **semi-implicit (symplectic) Euler** integrator handles only the small residual. Semi-implicit Euler is symplectic, so it does not leak energy over long runs, and with a residual this small its truncation error is negligible at 60 Hz.

### Floating origin, extended to velocity
The existing `FloatingOriginSystem` only shifts position. With orbits it must also shift velocity (a Galilean shift), because the player's thrust takes them away from the anchor's orbit in velocity as well as position, and Box2D's speed cap cares about velocity in the bubble frame.

**Rebase the anchor** when the player is more than `REBASE_DISTANCE` (2 km is a reasonable start) or `REBASE_SPEED` (200 m/s) away from it:
1. In doubles: `R_new = R_a + r_player·10`, `V_new = V_a + v_player·10`.
2. `anchor = elementsFrom(R_new, V_new, mu, t)`.
3. For every Box2D body: `setTransform(pos − r_player, angle)` and `setLinearVelocity(vel − v_player)`.
4. Fire `WorldShiftEvent(deltaPos, deltaVel)` so cameras, particles, HUD markers and audio adjust.

Rebasing is exact (it is a change of reference frame), so it can happen as often as needed. Doing it only when thresholds are crossed avoids touching every body each tick.

When the player crosses an SOI boundary, the anchor is re-parented the same way rails bodies are; Box2D bodies do not move.

### Why not just put the anchor on the player every tick?
Because the player thrusts. A frame glued to a thrusting ship is accelerating, and every other body would need a fictitious force added. A free-falling anchor that is rebased occasionally is simpler and exact.

## 4. Handoff Between Rails and Box2D

### Entry (rails → bubble)
Triggered when a rails body's distance from the anchor drops below `BUBBLE_ENTER_RADIUS` (e.g. 20 km):
1. On a tick boundary, on the main thread, evaluate `R, V = body.stateAt(t)` and the anchor at the same t, both relative to the same parent (re-express if they differ).
2. `r = (R − R_a) / 10`, `v = (V − V_a) / 10`, computed in doubles, then cast to float.
3. Create the Box2D body and `PhysicsBodyComponent` there, copy angle and angular velocity from the rails entity's saved attitude, remove `OrbitComponent` (or mark it inactive), and cancel the body's pending rails events. Pending maneuver nodes become AI goals instead.

### Fast passes (relative speed above the Box2D cap)
At real orbital scale a rails body can reach the bubble moving several km/s relative to the anchor, which Box2D cannot represent (section 1). Such a body does **not** become a Box2D body. Instead it becomes a **kinematic transient**:
- Its position each tick still comes from `stateAt(t)` (exact, no integration), converted into the bubble frame for rendering, HUD markers and audio.
- Collisions are found by sweeping it against the Box2D world each tick (`world.rayCast` along its path for small objects, or a few rays across its width for large ones), the same way fast projectiles are handled in section 5. A hit produces an impact event for `combat-sys` and, if both bodies survive, both leave on new rails orbits computed from the collision.
- If its relative speed later drops below `B2D_ENTRY_SPEED` (about 1,000 m/s, leaving margin under the cap) while still inside the bubble, it is promoted to a normal Box2D body by the usual entry handoff.

A fast pass crosses a 25 km bubble in seconds, so this is mainly about drawing it, letting weapons engage it, and not letting it pass through ships unnoticed.

The entry rule is therefore: within `BUBBLE_ENTER_RADIUS` **and** relative speed below `B2D_ENTRY_SPEED` → Box2D body; within radius but faster → kinematic transient.

### Exit (bubble → rails)
Triggered when a Box2D body's distance from the anchor exceeds `BUBBLE_EXIT_RADIUS` (e.g. 25 km). The gap between enter and exit radius is hysteresis, so a body skimming the boundary does not flip every tick.
1. `R = R_a + r·10`, `V = V_a + v·10` in doubles.
2. `orbit = elementsFrom(R, V, mu, t)`, then destroy the Box2D body, keep the entity and its UUID.
3. Predict its next events (SOI change, impact) and enqueue them.

The entity keeps the same UUID and all non-physics components across handoffs; only the physics representation changes. This matches the UUID registry approach in `core-engine`.

### Finding bodies about to enter
Checking every rails body every tick is wasteful. Instead, the background thread periodically (e.g. every 0.5 s of sim time) computes, for each candidate, whether it can reach `BUBBLE_ENTER_RADIUS` before the next check, using `distance − maxClosingSpeed·interval`. Cheap culling first: skip any body whose periapsis–apoapsis band around the same parent does not overlap the anchor's band plus the bubble radius. The main thread then does exact checks only on the short list.

### Bodies that are thrusting when they leave
If an NPC is mid-burn as it exits, its remaining burn becomes a maneuver node (impulsive approximation) on the rails side. The player ship never leaves the bubble; the bubble follows it.

## 5. Weapons and Box2D's Speed Cap
At 1 unit = 10 m and 60 Hz, nothing in the bubble frame may exceed about 1,200 m/s. Ships are fine. Fast projectiles (railguns, kinetic slugs at several km/s) are not, and Box2D bodies at that speed would also tunnel through thin hulls. Recommendation:
- **Slow projectiles** (below roughly 800 m/s relative): ordinary Box2D bodies with `bullet = true`. `combat-sys` picks per projectile type by its maximum relative speed, so many missiles end up on the raycast path.
- **Fast projectiles**: not Box2D bodies. Each tick, advance them analytically (straight line in the bubble frame plus tidal term) and use `world.rayCast` from last position to new position to find hits. The resulting impact point and direction feed `combat-sys`'s penetration raycast exactly as a contact would.

## 6. Time Warp
Two kinds of warp, because Box2D cannot be fast-forwarded the way rails can.

| Mode | Speeds | What runs | Allowed when |
|---|---|---|---|
| Physics warp | 1× up to 10× | Box2D steps several fixed 1/60 s ticks per frame (up to 600 steps per second at 10×); rails and anchor evaluate at the new time | Always, CPU permitting |
| Rails warp | 50× up to 1,000,000× | Bubble collapsed: every bubble body (player included) converted to rails; Box2D idle; ticks jump straight to the target time | No thrust, no hostiles within weapons range, no active fire or atmosphere leak aboard, player seated at console |

**Entering rails warp** runs the exit handoff for every bubble body, including the player. **Leaving it** re-anchors on the player's current rails orbit and runs the entry handoff for anything now within range.

**Automatic drop-out.** Rails warp drops to 1× just before the next due event that matters to the player: their own SOI change, a maneuver node, predicted surface impact, or a predicted encounter (a rails body due to come within `BUBBLE_ENTER_RADIUS` of the player's orbit). Because these are predicted and queued, warp can jump straight to them instead of stepping toward them.

**Why the ceilings are this high.** With real periods, a 3-day lunar transfer at 100,000× still takes about 2.5 minutes of real time and a 260-day Mars transfer about 4 minutes at 1,000,000×. Physics warp goes to 10× because real burns at realistic ship thrust run for minutes, and the player will want to speed those up without collapsing the bubble. Rails warp starts at 50× so there is no overlap.

**Other fixed-tick systems.** `GridHazardSystem`, crew AI and similar `FixedLogicSystem`s would run thousands of ticks per frame at high warp. At real scale most of a play session may be spent in rails warp, so this is decided: **rails warp is refused while any fire or atmosphere leak is active aboard the player's ship** (`TimeWarpController` asks `interior-grid` for an active-hazard flag), and warp drops to 1× at once if a hazard starts during warp. With no hazards active, the interior and crew systems run a coarse catch-up step during rails warp (for example oxygen regeneration, crew needs, repairs progressing) rather than every 1/60 s tick. The exact catch-up rules belong to those modules.

**Time keeping.** At warp the tick counter advances by `warp × frameTicks`. Rails code reads `tick / 60.0`. A double of seconds holds sub-microsecond resolution for over 30 years of game time, so precision is not a concern.

## 7. Precision Budget
| Quantity | Type | Range | Resolution |
|---|---|---|---|
| Rails positions (parent-relative) | double | up to ~4.5e12 m (out to ~30 AU) | ~1 mm |
| Anchor state | double | same | same |
| Box2D positions | float, units of 10 m | within ±2,500 units (25 km) | ~2.5 mm worst case at the bubble edge |
| Box2D velocities | float | ±120 units/s | far below anything visible |
| Sim time | long ticks, double seconds | decades | sub-microsecond |

`Box2dPrecisionTest` already shows trouble at 200 km in float (200,000 units at 1 m per unit, about 1.6 cm resolution). With the bubble capped at 25 km and 10 m units, the farthest Box2D coordinate is 2,500 units, so error stays at a few millimetres even at the edge and far finer near the player.

**Rendering.** The world camera lives in the bubble frame. Anything not in Box2D (planets, distant stations, orbit lines on the map) is positioned by computing `R_object − R_a` in doubles and casting the *difference* to float for the renderer. Never cast an absolute planet-centred position to float. For the zoomed-out map view, use a separate camera whose unit is kilometres or megametres and do the same subtraction against the map's centre.

**HUD.** Prograde/retrograde markers and the predicted orbit line use the player's true velocity `V_a + v_player·10` relative to the current SOI body. The predicted path is just the player's own conic from `elementsFrom`, recomputed each frame while thrusting and drawn from the cached elements otherwise.

## 8. Persistence
- Save `tick`, the anchor's elements, and every rails body's `OrbitComponent` plus pending event queue entries.
- Save bubble bodies as **absolute double state vectors relative to their SOI parent**, not as Box2D coordinates. On load, re-create the anchor and run the entry handoff. A save therefore never depends on where the bubble happened to be.
- Event queue entries reference entities by UUID, as the serialization design already requires.

## Implemented Components
In `orbital-mechanics/src/main/java/com/ringsofiron/orbital` unless noted.
- `OrbitComponent`: Keplerian elements, parent UUID, epoch (rails bodies only).
- `CelestialBodyComponent`: `mu`, radius, `soiRadius`, for bodies with mass that others orbit.
- `ManeuverNodeComponent`: scheduled impulsive burns (time, prograde/radial Δv).
- The bubble anchor is not an entity component: physics-exterior's `PhysicsManager` keeps it as an `AnchorModel` (`KeplerAnchor` in the game), and `OrbitalEventSystem.setAnchor` holds its conic on the rails side.
- `UniverseStateComponent` was not built: `RailsUniverse.stateInFrame` and physics-exterior's `getTrueState` give a double state when one is needed.

## Implemented Systems
- `OrbitalEventSystem` (fixed tick, main thread): pops due events from the queue and applies them, using `SoiPredictor` to schedule each body's next SOI change and impact.
- `TidalGravitySystem` (physics-exterior, fixed tick, before the Box2D step): applies `a_tidal` to every dynamic body in the bubble.
- `FloatingOriginSystem` (physics-exterior): rebases the anchor on distance or speed thresholds and shifts both position and velocity.
- `BubbleTransitionSystem` (macro-sim): entry/exit handoffs against the anchor with hysteresis.
- `KinematicTransientSystem` (physics-exterior, fixed tick, after the Box2D step): moves fast-pass bodies along their rails conics in the bubble frame and sweeps them against the Box2D world for collisions.
- `EncounterPredictionTask` (macro-sim, background thread): culls and predicts bubble entries and warp drop-out points. `EncounterPredictor` here does the same search on the main thread for `TimeWarpController`'s own drop-out check.
- `TimeWarpController`: owns warp level, collapses and rebuilds the bubble for rails warp (through `BubbleHandoff`, which physics-exterior's `RailsBubbleHandoff` implements), enforces drop-out.

The game's Orbit scenario (debug menu, F3; `Orbit demo.bat` or `gradlew :lwjgl3:runGame -Pscenario=orbit`) shows rails orbits, SOI changes and time warp in a career's world, drawn with the game's orbit map (`PlayerOrbitPath` into ui-hud's `OrbitMap`, full screen); only the other craft's orbits and the spheres of influence are drawn by the scenario itself (`OrbitScenario`); `gradlew :orbital-mechanics:test` runs the Kepler and rails tests.

## Build Order (done)
Every step below is built; the list is kept for the reasoning behind the order.
1. `orbital` math package with unit tests: `stateAt`, `elementsFrom`, round-trip tests, Kepler solver at e = 0, 0.5, 0.99, 1.5.
2. Rails-only prototype: a planet, a moon and a few ships drawn as orbit lines, with rails warp and SOI transitions.
3. Anchor plus `TidalGravitySystem` with two Box2D ships; verify a ship released from the anchor follows its rails conic for an orbit or more.
4. Entry/exit handoff with hysteresis; verify a body that leaves and re-enters lands where Box2D alone would have put it (within centimetres).
5. Velocity-aware floating origin and rebase.
6. Physics warp, then rails warp with bubble collapse and drop-out.

## Changes to Existing Docs
Done: `physics-exterior`, `macro-sim` and `core-engine` design docs now match this plan (bubble-frame `TransformComponent`, long-tick sim time and warp rules, velocity-aware floating origin with a planned free-falling anchor, analytic rails movement with anchor-based entry/exit and fast passes). `combat-sys` is maintained by the combat design work and already handles fast projectiles by raycast.

## Settled Defaults
- **Map view:** a few discrete zoom levels, each with its own camera unit (e.g. km, Mm, AU), rather than a logarithmic zoom.
- **Planetary surfaces and atmospheres:** out of scope for now. No drag and no surface colliders in the bubble.
- **NPC burns:** NPC AI plans maneuvers as rails maneuver nodes; inside the bubble a controller flies the same plan with real thrust, so there is one planner.
- **Single bubble:** one bubble, around the player. No off-screen Box2D battles.

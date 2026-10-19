# Technical Design Decisions & Architecture Supplements

Based on our design discussions, here are the finalized technical decisions to supplement the First Draft Game Design Document for "Space Battle Sim":

## 1. Core Architecture & Entity Management
- **Entity-Component-System (ECS):** We will utilize LibGDX's **Ashley** framework. This modular approach perfectly handles the overlapping complexities of exterior Box2D physics, 2D interior grids, and AI crew behavior without deep, fragile inheritance trees.

## 2. Physics & Simulation
- **Interior Crew Navigation:** Crew movement on the 2D interior grid will rely on **pure logic-based discrete A* pathfinding** with interpolation. Crew members will *not* be Box2D dynamic bodies, preventing physics glitches when the ship accelerates or rotates rapidly.
- **Large Space Coordinates:** To prevent single-precision floating-point jitter ("Far Lands" effect), we will implement a **Floating Origin (Player-Centric Bubble)**. The player ship remains near `(0,0)`, and the entire active Box2D world is shifted backwards when a distance threshold is crossed.

## 3. UI & Viewports
- **Interior Visibility:** When piloting from the exterior view, the ship's interior state is represented via a **subtle wireframe / blueprint outline** in the lower-left corner of the screen. This UI element depicts damage levels across ship regions and highlights alerts (fires, depressurization) without the performance overhead of full dual-rendering.

## 4. World Economy & Macro-Simulation
- **Fully Physicalized NPC Fleets:** Operating similarly to the *X series (X4)*, every NPC ship in the universe is a physical entity flying through space. Out-of-bubble entities will run on a highly optimized, multi-threaded "on-rails" background simulation to support the intense computational load.

## 5. Modding & Persistence
- **Modding Security:** Mod scripts will run with **unrestricted JVM permissions** (Trust the User). This maximizes the power available to modders (similar to Minecraft), at the cost of the player needing to trust the mods they download.
- **Serialization:** Game states will be serialized to JSON using a **UUID Registry**. Components will reference other entities via UUID strings rather than direct object references, keeping the JSON flat, human-readable (as requested in the GDD), and easy to parse on load.

## 6. Progression & Death Mechanics
- **Rescue & Requisition:** When a player abandons ship in an escape pod, there is a time-skip. The player is "rescued" and awakens at a faction station. They must then use their faction rank's budget to requisition a new, potentially lower-tier chassis, maintaining consequences without permanent character deletion.

## 7. Ship Construction & Combat Damage
- **Pre-designed Hulls:** Ship construction utilizes fixed chassis (e.g., Corvette, Freighter) determining the Box2D shape and max grid size. The player's freedom lies in customizing the internal tile layout and exterior hardpoints.
- **Penetration Raycasting:** External weapons that pierce the hull will spawn a raycast onto the 2D interior grid based on impact angle. This ray physically damages rooms, conduits, and crew in its path until its energy is fully spent.

## 8. Audio Engine
- **OpenAL Soft, no proprietary middleware:** libGDX's own sound API cannot filter or place sounds, so the game drives **OpenAL Soft** (LGPL) directly through LWJGL, on the same OpenAL context libGDX's desktop backend already opens. Its EFX extension supplies the low-pass filters for interior "muffling" and its HRTF the headphone spatialization for the "Spacer Ears" mechanic. FMOD and Wwise were considered and dropped to keep the project's dependencies free and open source. The mix curves live in the game's own code (`AcousticModel`), not in an authoring tool, and a procedural synth fallback plays where OpenAL is not available. See `documentation/audio-spatial/Design.md`.

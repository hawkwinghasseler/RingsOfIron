# Spatial Audio Module Design

## Overview
The `audio-spatial` module manages all sound reproduction. Due to the hard sci-fi nature of the game, audio plays a massive role in immersion, utilizing DSP low-pass filters to contrast the muffled, pressurized ship interior with the silence of the vacuum of space.

## Implementation Dependencies
- **`core-engine`**: Ashley ECS for tracking audio emitters.
- **`physics-exterior`**: Needs Box2D world coordinates for 3D spatialization of exterior sounds.
- **`interior-grid`**: **Hard Dependency for Muffling.** Needs to calculate lines-of-sight and wall obstructions on the 2D grid to apply acoustic low-pass filters.
- **External Libraries:** FMOD Studio API (via a JNI wrapper) or Wwise.

## Implementation Guide
To implement this module independently (requires mocking world coordinates and a dummy grid for obstruction testing):

1. **FMOD Integration:**
   - Initialize the FMOD Core and Studio systems natively via JNI.
   - Load the compiled FMOD Master Banks (`.bank` files containing compressed audio and DSP rules).
   - Create a global `AudioManager` wrapper to abstract FMOD calls (e.g., `playEvent("event:/weapons/laser")`).

2. **3D Listener Positioning:**
   - Create an `AudioListenerSystem` that updates every frame.
   - Sync the FMOD 3D Listener position (`FMOD_3D_ATTRIBUTES`) to the current active Camera position.
   - In *Exterior Mode*, the listener is at the ship's center. In *Interior Mode*, it tracks the player character's grid coordinate.

3. **Emitter Tracking:**
   - Create an `AudioEmitterComponent` holding an active FMOD `EventInstance`.
   - Create an `AudioUpdateSystem` that syncs the `EventInstance`'s 3D position to the entity's `TransformComponent`.

4. **Acoustic Occlusion (Muffling) Algorithm:**
   - When the player is in *Interior Mode*:
     - For every active `AudioEmitterComponent` on the ship, draw a Bresenham line on the 2D grid from the emitter to the player listener.
     - Count the number of walls/closed bulkheads intersected by the line.
     - Set an FMOD Studio parameter (e.g., `setParamaterByName("Occlusion", value)`) based on the wall count. In FMOD Studio, this parameter should drive a Low-Pass Filter, effectively cutting high frequencies and "muffling" the sound based on how many rooms away it is.
   - When the player is in *Exterior Mode*:
     - Internal sounds are aggressively clamped to maximum occlusion.
     - External sounds (like weapon impacts on the hull) use standard distance attenuation without occlusion, representing sound vibrating through the ship's frame (the "Spacer Ears" mechanic).

## Proposed Components
- `AudioEmitterComponent`: Attached to entities (e.g., engines, weapons, broken pipes) holding the FMOD Event path and current playback state.

## Proposed Systems
- `AudioUpdateSystem`: Syncs FMOD listener and emitter 3D positions.
- `AcousticOcclusionSystem`: Calculates the "muffling" value based on grid wall intersections.

## Prototype Status
The module builds and is tested headlessly (`gradlew :audio-spatial:test`). FMOD is not wired in yet: the game talks to an `AudioBackend` interface, and two built-in backends stand in until the JNI wrapper and banks exist.

![Spatial audio demo](audio-demo.png)

**Run the demo:** `Audio demo.bat` (or `gradlew :lwjgl3:runAudioDemo`). Walk a small ship with WASD and hear the reactor, alarm, fire and breach hiss muffle as walls and doors come between you. E opens or seals the nearest door, TAB switches between interior and exterior listening, SPACE fires a laser and H hits the hull. Headphones help with the panning.

What exists (`com.ringsofiron.audiospatial`):
- **`AudioManager`** wraps the backend: `playOneShot(path, x, y[, intensity])`, `createInstance`, `setGlobalParameter`, `loadBank`. `AudioManager.createDefault()` picks the synth backend when libGDX audio runs, otherwise headless. The FMOD backend slots in here.
- **Backends** (`backend` package): `AudioBackend` / `AudioEventInstance` mirror FMOD Studio's system and `EventInstance`. `HeadlessAudioBackend` plays nothing but records every instance and its computed mix, for tests and servers. `SynthAudioBackend` plays procedural placeholder sounds through libGDX's `AudioDevice` on a mixer thread with distance rolloff, stereo pan and a real 12 dB/octave low-pass for occlusion.
- **`AcousticModel`** is the one place the mix curves live: inverse distance rolloff faded to silence at max distance, a low-pass cutoff falling exponentially from 20 kHz (clear) to 350 Hz (fully muffled), 65 % volume loss at full occlusion, and volume ∝ √pressure. The FMOD project's Occlusion and Pressure automation should follow the same curves.
- **`AudioEventCatalog`** maps event paths (`event:/ship/reactor_hum`, `event:/weapons/laser`, ...) to loop/one-shot, duration and distances. FMOD reads these from banks instead; modding-data's `[audio_event]` table can feed it later.
- **Components:** `AudioEmitterComponent` (event path, `EXTERIOR` at the entity's transform or `INTERIOR` at a ship-local position aboard a ship, extra parameters, auto-start, self-removing one-shots) and `AudioListenerComponent` (`mode` EXTERIOR/INTERIOR, `ship`, ship-local position).
- **Systems:** `AcousticOcclusionSystem` (priority 900) then `AudioUpdateSystem` (priority 1000).
- **Bridges:** `ImpactAudio` listens to `PhysicsManager.collisions` and plays hull impacts scaled by approach speed. `AudioUpdateSystem.worldShiftListener()` listens to `PhysicsManager.worldShifts` so a floating-origin rebase is not heard as a burst of speed. `AudioEmitters.spawnInteriorOneShot` plays a muffled one-shot (a door, a crack) inside a ship.

Decisions made while building it:
- **Wall counting** (`GridOcclusion`) walks a Bresenham line from emitter tile to listener tile. Entering an intact wall tile, a sealed bulkhead tile, void or off-grid counts one barrier (a run of wall tiles counts once, so a thick wall is one wall); crossing a blocking edge wall counts one. Open doors and destroyed walls let sound through. On a diagonal step the quieter of the two orthogonal routes is used, so sound leaks around corners. Occlusion is `min(walls, 4) / 4`.
- **Sound needs air.** An interior emitter's Pressure parameter is the lower of the pressure at the emitter and at the listener (from interior-grid's compartments), so a fire in a vented room goes silent. An emitter inside a wall tile uses its best-pressurised neighbour. Before compartments are built the pressure is taken as 1.
- **Exterior mode** clamps every interior sound to full occlusion; so does listening from inside a different ship. Exterior emitters are never occluded ("Spacer Ears").
- **Velocity** for Doppler is derived from position change per frame; no physics body is read.
- Positions are world (bubble) meters; interior positions go through the ship's `TransformComponent` (rotation in degrees CCW, as physics-exterior writes it).

Integration owed by other modules (agreed through the scheduler):
- **ui-hud** sets `AudioListenerComponent.mode` and `ship` when the view switches.
- **ai-crew / core wiring** copies the player avatar's ship-local position into `localX` / `localY`.
- **combat-sys, interior-grid, ai-crew** attach `AudioEmitterComponent`s (weapons, fires, breaches via `grid.airflow`, alarms, voices) or call `AudioManager` for one-shots.
- **FMOD backend:** add the JNI binding as a third `AudioBackend`; nothing else changes.

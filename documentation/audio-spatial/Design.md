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

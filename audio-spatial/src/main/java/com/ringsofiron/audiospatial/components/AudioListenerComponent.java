package com.ringsofiron.audiospatial.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;

/**
 * Marks the entity that hears. There should be one. Its own TransformComponent places it in
 * {@link Mode#EXTERIOR}; in {@link Mode#INTERIOR} it stands at ship-local ({@link #localX},
 * {@link #localY}) aboard {@link #ship}.
 *
 * The view-mode owner (ui-hud) sets {@link #mode} and {@link #ship}; the player avatar owner
 * (ai-crew, through core wiring) keeps the local position up to date.
 */
public class AudioListenerComponent implements Component {

    public enum Mode {
        /** Piloting: hearing from the ship's center (the listener entity's transform). */
        EXTERIOR,
        /** Walking the decks: hearing from the player character's spot on the grid. */
        INTERIOR
    }

    public Mode mode = Mode.EXTERIOR;
    /** The ship the listener is aboard. Must have a TransformComponent, and a ShipGridComponent for muffling. */
    public transient Entity ship;
    /** Ship-local position in meters while in {@link Mode#INTERIOR}. */
    public float localX, localY;

    // Written by AudioUpdateSystem
    /** Where the listener was placed this frame, in world (bubble) meters. */
    public float worldX, worldY;
}

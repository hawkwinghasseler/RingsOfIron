package com.ringsofiron.interiorgrid;

import com.badlogic.ashley.core.Component;

/**
 * Tags an entity as an internal machine or system occupying specific grid coordinates
 * on its parent ship's ShipGridComponent.
 *
 * Examples: reactor, life support console, turret control, cargo storage.
 */
public class SubsystemComponent implements Component {

    public enum SubsystemType {
        REACTOR,        // Provides power. Always the root of the power graph.
        LIFE_SUPPORT,   // Generates/maintains oxygen. Requires power.
        HELM,           // Piloting controls. Requires power.
        WEAPONS,        // Weapon targeting console. Requires power.
        CARGO,          // Passive storage - no power requirement.
        AIRLOCK         // Can be opened to space. Requires power to cycle.
    }

    /** Grid-local X coordinate of this subsystem's anchor tile. */
    public int gridX;
    /** Grid-local Y coordinate of this subsystem's anchor tile. */
    public int gridY;
    /** The type of subsystem. */
    public SubsystemType type;
    /** Whether this subsystem is currently operational. */
    public boolean isOperational = true;
    /** Structural health 0.0 = destroyed, 1.0 = fully intact. */
    public float health = 1.0f;
    /** Whether this subsystem is currently receiving power. Set by PowerRoutingSystem. */
    public boolean powered = false;

    public SubsystemComponent(int gridX, int gridY, SubsystemType type) {
        this.gridX = gridX;
        this.gridY = gridY;
        this.type = type;
    }
}

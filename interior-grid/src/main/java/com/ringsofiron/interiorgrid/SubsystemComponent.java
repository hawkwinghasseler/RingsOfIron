package com.ringsofiron.interiorgrid;

import com.badlogic.ashley.core.Component;

/**
 * Tags an entity as a ship system installed in a {@link MountFootprint} on its parent
 * ship's ShipGridComponent. The system occupies every tile of the footprint.
 *
 * Examples: reactor, life support, turret mount, cargo storage.
 */
public class SubsystemComponent implements Component {

    public enum SubsystemType {
        REACTOR(MountFootprint.MountKind.PRIMARY),          // Generator. Always the root of the power graph.
        SHIELD_EMITTER(MountFootprint.MountKind.PRIMARY),   // Projects shields. Requires power.
        TURRET_MOUNT(MountFootprint.MountKind.PRIMARY),     // Weapon turret hardpoint. Requires power.
        LIFE_SUPPORT(MountFootprint.MountKind.AUXILIARY),   // Generates/maintains oxygen. Requires power.
        HELM(MountFootprint.MountKind.AUXILIARY),           // Piloting controls. Requires power.
        WEAPONS(MountFootprint.MountKind.AUXILIARY),        // Weapon targeting console. Requires power.
        CARGO(MountFootprint.MountKind.AUXILIARY),          // Passive storage - no power requirement.
        AIRLOCK(MountFootprint.MountKind.AUXILIARY);        // Can be opened to space. Requires power to cycle.

        /** The kind of mount this system installs into. */
        public final MountFootprint.MountKind mountKind;

        SubsystemType(MountFootprint.MountKind mountKind) {
            this.mountKind = mountKind;
        }
    }

    /** The mount this system is installed in; its tiles are the system's footprint. */
    public final MountFootprint footprint;
    /** The type of subsystem. */
    public SubsystemType type;
    /** Whether this subsystem is currently operational. */
    public boolean isOperational = true;
    /** Structural health 0.0 = destroyed, 1.0 = fully intact. */
    public float health = 1.0f;
    /** Whether this subsystem is currently receiving power. Set by PowerRoutingSystem. */
    public boolean powered = false;

    public SubsystemComponent(MountFootprint footprint, SubsystemType type) {
        if (footprint.kind != type.mountKind) {
            throw new IllegalArgumentException(type + " needs a " + type.mountKind + " mount, not " + footprint.kind);
        }
        this.footprint = footprint;
        this.type = type;
    }

    /** True if tile (x, y) is part of this system's footprint. */
    public boolean covers(int x, int y) {
        return footprint.covers(x, y);
    }
}

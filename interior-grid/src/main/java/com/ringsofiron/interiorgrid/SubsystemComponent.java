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
        AIRLOCK(MountFootprint.MountKind.AUXILIARY),        // Can be opened to space. Requires power to cycle.
        ENGINE(MountFootprint.MountKind.AUXILIARY),         // Main engine; its nozzle faces +y at rotation 0 (-x at 1). Requires power.
        DOCKING_PORT(MountFootprint.MountKind.PORT);        // Airlock in the hull edge with a latch collar. Arming needs power.

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
    /**
     * Whether the crew have this system switched on. A reactor that is switched off
     * produces no power; it is not damaged and comes back as soon as it is switched on.
     */
    public boolean switchedOn = true;
    /** Whether this subsystem is currently operational: switched on, powered and not disabled by damage. Set by PowerRoutingSystem. */
    public boolean isOperational = true;
    /** Structural health 0.0 = destroyed, 1.0 = fully intact. Only damage changes it. */
    public float health = 1.0f;
    /** Whether this subsystem is currently receiving power. Set by PowerRoutingSystem. */
    public boolean powered = false;
    /** Id of the ship component this system was built from (see {@link ShipPartCatalog}); null for one made in code. */
    public String component;
    /**
     * REACTOR: watts at full health, set from its component. 0 means the standard output for
     * its mount size (see {@link PowerRoutingSystem#standardOutput}).
     */
    public double ratedOutput;
    /** LIFE_SUPPORT: multiplier on scrubbing, air and oxygen supply, set from its component. */
    public float throughput = 1f;
    /** The power circuit feeding this system, or null when unpowered. Set by PowerRoutingSystem; not saved. */
    public transient PowerCircuit circuit;

    /** For deserialization only; the loader fills in the fields. */
    @SuppressWarnings("unused")
    private SubsystemComponent() {
        this.footprint = null;
    }

    public SubsystemComponent(MountFootprint footprint, SubsystemType type) {
        if (footprint.kind != type.mountKind) {
            throw new IllegalArgumentException(type + " needs a " + type.mountKind + " mount, not " + footprint.kind);
        }
        this.footprint = footprint;
        this.type = type;
    }

    /**
     * Takes {@code joules} from this system's power circuit, all or nothing. False when the
     * system is not operational or its circuit's store is short, as it is on a circuit fed
     * by a damaged reactor or shared with other heavy loads. For a weapon, a shot's energy
     * is its power draw times its cooldown.
     */
    public boolean tryDrawPower(double joules) {
        return isOperational && circuit != null && circuit.tryDraw(joules);
    }

    public boolean isDestroyed() {
        return health <= 0f;
    }

    /**
     * Below this health a system is too damaged to work, even when switched on and
     * powered. Repairs above it bring the system back.
     */
    public static final float DISABLE_THRESHOLD = 0.3f;

    /** True when damage has taken the system below {@link #DISABLE_THRESHOLD}, destroyed included. */
    public boolean isDisabled() {
        return health < DISABLE_THRESHOLD;
    }

    /** True if tile (x, y) is part of this system's footprint. */
    public boolean covers(int x, int y) {
        return footprint.covers(x, y);
    }
}

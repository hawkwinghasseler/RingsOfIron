package com.ringsofiron.moddingdata.defs;

import java.util.ArrayList;
import java.util.List;

/**
 * A spawnable ship: a chassis plus exterior tuning and loadout. {@code [ship.<id>]}.
 *
 * <pre>
 * [ship.corvette_patrol]
 * name = "Patrol Corvette"
 * chassis = "corvette"
 * texture = "tex_corvette"
 * mass = 120000.0              # kg, dry, excluding interior tiles
 * max_thrust = 2.4e6           # N, main engine
 * max_torque = 8.0e5           # N*m, RCS
 * crew = 4
 * hardpoints = [ { weapon = "railgun_mk1", x = 4.0, y = 0.0, rotation = 0.0 } ]
 * </pre>
 */
public class ShipDef {
    public String id;
    public String name;
    public String chassis;
    public String texture;
    public float mass;
    public float maxThrust;
    public float maxTorque;
    /**
     * Exterior collision polygon, body-local metres, counter-clockwise: only for a ship without a
     * chassis. A ship with one collides on its layout, or on its hull art's collision layer.
     */
    public float[][] hull = new float[0][];
    public int crew;
    public List<Hardpoint> hardpoints = new ArrayList<>();

    public static class Hardpoint {
        public String weapon;
        public float x, y;
        /** Degrees, body-local. */
        public float rotation;
    }
}

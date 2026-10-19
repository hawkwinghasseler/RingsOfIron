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
 * exhaust_velocity = 1.0e5     # m/s; propellant burned is thrust over this (default 100 km/s)
 * hull = [[-5.0, -3.0], [5.0, -3.0], [6.0, 0.0], [5.0, 3.0], [-5.0, 3.0]]  # local metres
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
     * m/s. Thrust burns propellant from the ship's tanks at force / exhaust velocity, once its
     * layout has an engine; 0 means the standard 100 km/s.
     */
    public float exhaustVelocity;
    /** Exterior collision polygon, body-local metres, counter-clockwise. */
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

package com.ringsofiron.physicsexterior.components;

import com.badlogic.ashley.core.Component;

/**
 * A ship's propulsion limits and fuel. Thrust axes are in the ship's local frame: +X is the nose,
 * +Y is the port (left) side.
 */
public class ThrusterComponent implements Component {
    /** Maximum force from the main engine along +X, in newtons. */
    public float forwardThrust;
    /** Maximum force from retro thrusters along -X, in newtons. */
    public float reverseThrust;
    /** Maximum force from lateral RCS thrusters along +/-Y, in newtons. */
    public float lateralThrust;
    /** Maximum turning torque from RCS thrusters, in newton-meters. */
    public float maxTorque;

    /** Remaining fuel, in kg. */
    public float fuel;
    public float fuelCapacity;
    /** Fuel burned per newton-second of linear thrust. 0 means thrust is free. */
    public float fuelPerNewtonSecond;
    /** Fuel burned per newton-meter-second of torque. 0 means turning is free. */
    public float fuelPerTorqueSecond;
    /**
     * The drive's exhaust velocity, m/s, for code that feeds this thruster from tanks and prices
     * thrust in propellant (force / exhaust velocity). 0 means unknown.
     */
    public float exhaustVelocity;
    /**
     * How much further the main engines fitted get from their propellant than the standard drive:
     * multiplies {@link #exhaustVelocity}. 1 for standard engines, or with none fitted.
     */
    public float efficiency = 1f;

    public ThrusterComponent() {
    }

    public ThrusterComponent(float forwardThrust, float reverseThrust, float lateralThrust, float maxTorque) {
        this.forwardThrust = forwardThrust;
        this.reverseThrust = reverseThrust;
        this.lateralThrust = lateralThrust;
        this.maxTorque = maxTorque;
    }

    public ThrusterComponent withFuel(float capacity, float perNewtonSecond, float perTorqueSecond) {
        this.fuel = capacity;
        this.fuelCapacity = capacity;
        this.fuelPerNewtonSecond = perNewtonSecond;
        this.fuelPerTorqueSecond = perTorqueSecond;
        return this;
    }

    /** True when this thruster burns fuel and has none left. */
    public boolean isOutOfFuel() {
        boolean consumesFuel = fuelPerNewtonSecond > 0f || fuelPerTorqueSecond > 0f;
        return consumesFuel && fuel <= 0f;
    }
}

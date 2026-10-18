package com.ringsofiron.orbital.components;

import com.badlogic.ashley.core.Component;

/**
 * A massive body others can orbit: a star, planet or moon. The root of the hierarchy (the star)
 * has no {@link OrbitComponent}; every other celestial body orbits its parent.
 */
public class CelestialBodyComponent implements Component {
    public String name;
    /** Gravitational parameter G*M, m^3/s^2. */
    public double mu;
    /** Surface radius, m. */
    public double radius;
    /** Sphere of influence radius, m. Infinite for the root body. See {@code OrbitMath.soiRadius}. */
    public double soiRadius = Double.POSITIVE_INFINITY;

    public CelestialBodyComponent() {
    }

    public CelestialBodyComponent(String name, double mu, double radius, double soiRadius) {
        this.name = name;
        this.mu = mu;
        this.radius = radius;
        this.soiRadius = soiRadius;
    }
}

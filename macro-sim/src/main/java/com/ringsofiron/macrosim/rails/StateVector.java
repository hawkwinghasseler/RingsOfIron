package com.ringsofiron.macrosim.rails;

/** Position (m) and velocity (m/s) in doubles, relative to some parent body. Mutable scratch value. */
public final class StateVector {
    public double x;
    public double y;
    public double vx;
    public double vy;

    public StateVector() {
    }

    public StateVector(double x, double y, double vx, double vy) {
        set(x, y, vx, vy);
    }

    public StateVector set(double x, double y, double vx, double vy) {
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.vy = vy;
        return this;
    }

    public StateVector set(StateVector other) {
        return set(other.x, other.y, other.vx, other.vy);
    }

    /** Sets this to {@code a - b}, component-wise. */
    public StateVector setDifference(StateVector a, StateVector b) {
        return set(a.x - b.x, a.y - b.y, a.vx - b.vx, a.vy - b.vy);
    }

    public double distance() {
        return Math.hypot(x, y);
    }

    public double speed() {
        return Math.hypot(vx, vy);
    }

    @Override
    public String toString() {
        return String.format("(%.1f, %.1f | %.2f, %.2f)", x, y, vx, vy);
    }
}

package com.ringsofiron.orbital.math;

/**
 * Position (m) and velocity (m/s) in double precision, relative to some parent body.
 * Mutable so hot paths can reuse one instance instead of allocating.
 */
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

    public StateVector add(StateVector other) {
        return set(x + other.x, y + other.y, vx + other.vx, vy + other.vy);
    }

    public StateVector sub(StateVector other) {
        return set(x - other.x, y - other.y, vx - other.vx, vy - other.vy);
    }

    public double radius() {
        return Math.sqrt(x * x + y * y);
    }

    public double speed() {
        return Math.sqrt(vx * vx + vy * vy);
    }

    public StateVector copy() {
        return new StateVector(x, y, vx, vy);
    }

    @Override
    public String toString() {
        return String.format("r=(%.3f, %.3f) v=(%.6f, %.6f)", x, y, vx, vy);
    }
}

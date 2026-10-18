package com.ringsofiron.physicsexterior;

/** A straight line at constant velocity in the parent's frame. Useful for tests and demos. */
public class LinearTrajectory implements TrueTrajectory {
    private final double x, y, vx, vy, epoch;

    public LinearTrajectory(double x, double y, double vx, double vy, double epoch) {
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.vy = vy;
        this.epoch = epoch;
    }

    @Override
    public void stateAt(double simSeconds, double[] out) {
        double dt = simSeconds - epoch;
        out[0] = x + vx * dt;
        out[1] = y + vy * dt;
        out[2] = vx;
        out[3] = vy;
    }
}

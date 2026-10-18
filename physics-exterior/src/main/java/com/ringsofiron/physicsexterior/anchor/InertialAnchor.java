package com.ringsofiron.physicsexterior.anchor;

/** An anchor with no gravity: it moves in a straight line at constant velocity. */
public class InertialAnchor implements AnchorModel {
    private double x, y, vx, vy, epoch;

    public InertialAnchor() {
    }

    public InertialAnchor(double x, double y, double vx, double vy, double t) {
        reset(x, y, vx, vy, t);
    }

    @Override
    public double getMu() {
        return 0.0;
    }

    @Override
    public void stateAt(double t, double[] out) {
        double dt = t - epoch;
        out[0] = x + vx * dt;
        out[1] = y + vy * dt;
        out[2] = vx;
        out[3] = vy;
    }

    @Override
    public void reset(double x, double y, double vx, double vy, double t) {
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.vy = vy;
        this.epoch = t;
    }
}

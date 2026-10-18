package com.ringsofiron.combatsys.fire;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;

/**
 * A filtered estimate of one contact's motion (combat-sys/Design.md 3.1), updated by an
 * alpha-beta-gamma tracker from noisy position fixes. The gamma term estimates acceleration,
 * which is what lets a good crew lead a burning target; with gamma 0 the track only leads on
 * velocity and a target that keeps thrusting slips out from under the fire.
 */
public class Track {
    public transient Entity target;
    /** Estimated position, velocity and acceleration, local meters. */
    public final Vector2 p = new Vector2();
    public final Vector2 v = new Vector2();
    public final Vector2 a = new Vector2();
    /** Filter gains. */
    public float alpha = 0.5f, beta = 0.25f, gamma = 0f;
    /** Sim time of the last fix, seconds. */
    public float lastFixTime;
    /** Fixes taken so far. */
    public int fixes;
    /** Last fix's noise, 1 sigma, meters: a confidence measure for the HUD. */
    public float sigma;

    /** Extrapolates the estimate {@code dt} seconds ahead without a fix. */
    public void predict(float dt) {
        p.mulAdd(v, dt).mulAdd(a, 0.5f * dt * dt);
        v.mulAdd(a, dt);
    }

    /** Folds in a position fix {@code dt} seconds after the previous one. */
    public void update(float zx, float zy, float dt, float time, float sigma) {
        this.sigma = sigma;
        lastFixTime = time;
        if (fixes == 0) {
            p.set(zx, zy);
            v.setZero();
            a.setZero();
            fixes = 1;
            return;
        }
        predict(dt);
        float rx = zx - p.x, ry = zy - p.y;
        if (fixes == 1) {
            // Second fix: take velocity straight from the difference.
            v.mulAdd(new Vector2(rx, ry), 1f / dt);
            p.set(zx, zy);
        } else {
            p.add(alpha * rx, alpha * ry);
            v.add(beta / dt * rx, beta / dt * ry);
            a.add(2f * gamma / (dt * dt) * rx, 2f * gamma / (dt * dt) * ry);
        }
        fixes++;
    }

    /** Where the estimate puts the target {@code dt} seconds after its last fix. */
    public Vector2 positionAt(float dt, Vector2 out) {
        return out.set(p).mulAdd(v, dt).mulAdd(a, 0.5f * dt * dt);
    }

    public Vector2 velocityAt(float dt, Vector2 out) {
        return out.set(v).mulAdd(a, dt);
    }
}

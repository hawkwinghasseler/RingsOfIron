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

    /**
     * Sets the gains for a crew of the given skill, 0 (green) .. 1 (veteran), using the
     * fading-memory filter's critically damped gains for smoothing factor {@code theta}. A green
     * crew runs a plain alpha-beta filter (gamma 0) and only leads on velocity; skill brings in
     * the acceleration term and a faster response.
     */
    public void setSkill(float skill) {
        skill = Math.max(0f, Math.min(1f, skill));
        if (skill < 0.25f) {
            float theta = 0.8f;
            alpha = 1f - theta * theta;
            beta = (1f - theta) * (1f - theta);
            gamma = 0f;
        } else {
            float theta = 0.85f - 0.2f * skill;
            float q = 1f - theta;
            alpha = 1f - theta * theta * theta;
            beta = 1.5f * q * q * (1f + theta);
            gamma = 0.5f * q * q * q;
        }
    }

    /** Clears the estimate so the next fix starts a fresh track. */
    public void reset() {
        p.setZero();
        v.setZero();
        a.setZero();
        fixes = 0;
        sigma = 0f;
    }

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
            v.add(rx / dt, ry / dt);
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

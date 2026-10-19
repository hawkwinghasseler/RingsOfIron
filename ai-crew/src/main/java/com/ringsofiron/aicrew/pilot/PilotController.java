package com.ringsofiron.aicrew.pilot;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.ringsofiron.aicrew.pilot.NpcPilotComponent.Mode;

/**
 * The pilot's decision logic, free of Box2D so it can be tested with a point mass: from
 * the ship's state it writes stick inputs (forward, strafe, turn) in [-1, 1]. The ship
 * turns its nose where it needs to go and splits the velocity change it still needs
 * between main engine, retro thrusters and lateral RCS.
 */
public class PilotController {

    /** Snapshot of the ship, in bubble axes and SI units. */
    public static class ShipState {
        public final Vector2 position = new Vector2();
        public final Vector2 velocity = new Vector2();
        /** Parent-relative position and velocity, for the burn frame. */
        public final Vector2 parentPosition = new Vector2();
        public final Vector2 parentVelocity = new Vector2();
        /** Nose direction, radians (nose is local +x). */
        public float angle;
        public float angularVelocity;
        /** Main-engine acceleration at full throttle, m/s². */
        public float maxAccel;
        /** Retro-thrust acceleration at full throttle, m/s². */
        public float maxReverseAccel;
        /** Lateral RCS acceleration at full thrust, m/s². */
        public float maxLateralAccel;
        /** Angular acceleration at full RCS torque, rad/s². */
        public float maxAngularAccel;
    }

    /** Stick output. */
    public static class Controls {
        public float forward, strafe, turn;
        public boolean rotationAssist;
    }

    /** A burn is done when less than this much velocity change remains, m/s. */
    public static final float BURN_TOLERANCE = 0.25f;
    /** Distance from a seek target inside which the pilot stops turning, m. */
    public static final float FINAL_APPROACH = 20f;
    /** Share of the ideal stopping-distance speed used, for margin. */
    private static final float SAFETY = 0.8f;

    private final Vector2 tmp = new Vector2(), tmp2 = new Vector2();

    public void update(NpcPilotComponent pilot, ShipState s, double time, float dt, Controls out) {
        out.forward = 0f;
        out.strafe = 0f;
        out.turn = 0f;
        out.rotationAssist = false;
        if (s.maxAccel <= 0f) {
            out.rotationAssist = true;
            return;
        }

        PilotBurn burn = pilot.burns.peek();
        if (burn != null) {
            flyBurn(pilot, burn, s, time, dt, out);
            return;
        }
        if (pilot.seekTarget != null) {
            pilot.mode = Mode.SEEKING;
            Vector2 to = tmp.set(pilot.seekTarget).sub(s.position);
            float dist = to.len();
            if (dist < 2f && s.velocity.len() < 0.3f) {
                pilot.mode = Mode.COASTING;
                out.rotationAssist = true;
                return;
            }
            // Face the target and brake with the retro thrusters on the way in. Close in, hold
            // the heading and ease off in proportion to distance so the ship settles.
            float facing = dist > FINAL_APPROACH ? to.angleRad() : s.angle;
            float brake = s.maxReverseAccel > 0f ? s.maxReverseAccel : s.maxAccel * 0.5f;
            float speed = Math.min(pilot.seekMaxSpeed, (float) Math.sqrt(2f * brake * dist) * SAFETY);
            speed = Math.min(speed, dist * 0.5f);
            Vector2 desired = dist > 1e-3f ? to.scl(speed / dist) : to.setZero();
            steer(desired.sub(s.velocity), facing, s, dt, out);
            return;
        }
        // Depart: away from the bubble origin (or the way it was given) until fast enough outward
        Vector2 away = tmp.set(pilot.departDirection != null ? pilot.departDirection : s.position);
        if (away.isZero(1e-3f)) away.set(MathUtils.cos(s.angle), MathUtils.sin(s.angle));
        away.nor();
        if (s.velocity.dot(away) < pilot.departSpeed) {
            pilot.mode = Mode.DEPARTING;
            float facing = away.angleRad();
            steer(away.scl(pilot.departSpeed).sub(s.velocity), facing, s, dt, out);
        } else {
            pilot.mode = Mode.COASTING;
            out.rotationAssist = true;
        }
    }

    private void flyBurn(NpcPilotComponent pilot, PilotBurn burn, ShipState s, double time, float dt, Controls out) {
        if (!pilot.burnActive) {
            Vector2 dv = burnVector(burn, s, tmp);
            if (time < burn.time - pilot.alignLead || dv.isZero(1e-4f)) {
                if (dv.isZero(1e-4f) && time >= burn.time) pilot.burns.poll();
                pilot.mode = Mode.WAITING;
                out.rotationAssist = true;
                return;
            }
            if (time < burn.time) {
                pilot.mode = Mode.ALIGNING;
                pointAt(dv.angleRad(), s, dt, out);
                return;
            }
            // Fix the burn's velocity change in bubble axes as it starts
            pilot.burnActive = true;
            pilot.burnTarget.set(dv);
            pilot.burnStartVelocity.set(s.velocity);
        }
        pilot.mode = Mode.BURNING;
        Vector2 remaining = tmp.set(pilot.burnTarget).sub(tmp2.set(s.velocity).sub(pilot.burnStartVelocity));
        if (remaining.len() < BURN_TOLERANCE) {
            pilot.burns.poll();
            pilot.burnActive = false;
            out.rotationAssist = true;
            return;
        }
        steer(remaining, remaining.angleRad(), s, dt, out);
    }

    /** The burn's velocity change in bubble axes from the current prograde and radial directions. */
    Vector2 burnVector(PilotBurn burn, ShipState s, Vector2 out) {
        Vector2 pro = tmp2.set(s.parentVelocity);
        if (pro.isZero(1e-4f)) pro.set(MathUtils.cos(s.angle), MathUtils.sin(s.angle));
        pro.nor();
        float px = pro.x, py = pro.y;
        Vector2 rad = tmp2.set(s.parentPosition);
        if (rad.isZero(1e-4f)) rad.set(py, -px); // no parent position: use the right-hand normal
        rad.nor();
        return out.set((float) (px * burn.prograde + rad.x * burn.radial), (float) (py * burn.prograde + rad.y * burn.radial));
    }

    /**
     * Turns the nose to {@code facing} and fires whatever thrusters push along
     * {@code dv}: main engine or retro along the nose, RCS across it, each scaled to what
     * is left so the ship does not overshoot in one tick.
     */
    void steer(Vector2 dv, float facing, ShipState s, float dt, Controls out) {
        pointAt(facing, s, dt, out);
        float step = Math.max(dt, 1e-3f);
        float cos = MathUtils.cos(s.angle), sin = MathUtils.sin(s.angle);
        float along = dv.x * cos + dv.y * sin;
        float across = -dv.x * sin + dv.y * cos;
        if (along > 0f) out.forward = Math.min(1f, along / (s.maxAccel * step));
        else if (s.maxReverseAccel > 0f) out.forward = -Math.min(1f, -along / (s.maxReverseAccel * step));
        if (s.maxLateralAccel > 0f) out.strafe = MathUtils.clamp(across / (s.maxLateralAccel * step), -1f, 1f);
    }

    /**
     * Turns toward {@code target} radians, braking the spin to stop on it. Returns the
     * remaining heading error.
     */
    float pointAt(float target, ShipState s, float dt, Controls out) {
        float err = wrap(target - s.angle);
        if (s.maxAngularAccel <= 0f) return err;
        float wanted = Math.signum(err) * (float) Math.sqrt(2f * s.maxAngularAccel * Math.abs(err)) * SAFETY;
        out.turn = MathUtils.clamp((wanted - s.angularVelocity) / (s.maxAngularAccel * Math.max(dt, 1e-3f)), -1f, 1f);
        return err;
    }

    static float wrap(float a) {
        a = (a + MathUtils.PI) % MathUtils.PI2;
        if (a < 0) a += MathUtils.PI2;
        return a - MathUtils.PI;
    }
}

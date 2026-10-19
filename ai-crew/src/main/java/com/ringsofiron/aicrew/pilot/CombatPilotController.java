package com.ringsofiron.aicrew.pilot;

import com.badlogic.gdx.math.Vector2;
import com.ringsofiron.aicrew.pilot.NpcCombatComponent.Mode;
import com.ringsofiron.aicrew.pilot.PilotController.Controls;
import com.ringsofiron.aicrew.pilot.PilotController.ShipState;

/**
 * The combat pilot's decision logic, free of Box2D like {@link PilotController}, whose
 * thruster mixing it shares. Every tick it picks a velocity relative to the target and
 * steers toward it with the nose on the target, so forward guns bear and turrets keep arc:
 * <ul>
 *   <li>Range: toward or away from the target in proportion to the range error, capped at
 *       {@code maxClosingSpeed}, plus a sideways drift around it.</li>
 *   <li>Missile: if one homing on the ship is closing inside {@code evadeRange}, all thrust
 *       goes across its line of approach, the way that most stretches its turn.</li>
 *   <li>Flank ({@link NpcCombatComponent.Stance#FLANK}): toward the point {@code preferredRange}
 *       behind the target, off its stern where the engines are, and hold there, nose still on it.</li>
 *   <li>Kite ({@link NpcCombatComponent.Stance#KITE}): the range band without the drift around the
 *       target, and {@link #KITE_GAIN} times as quick to open the range when the target comes inside it.</li>
 * </ul>
 */
public class CombatPilotController {
    /** How much harder a kiting ship answers the target closing inside its range. */
    public static final float KITE_GAIN = 4f;

    private final PilotController mixer = new PilotController();
    private final Vector2 los = new Vector2(), side = new Vector2(), desired = new Vector2(), tmp = new Vector2();

    /**
     * @param missilePos the nearest inbound missile's position, or null if none
     */
    public void update(NpcCombatComponent combat, ShipState s, Vector2 targetPos, Vector2 targetVel,
                       Vector2 missilePos, Vector2 missileVel, float dt, Controls out) {
        update(combat, s, targetPos, targetVel, Float.NaN, missilePos, missileVel, dt, out);
    }

    /**
     * @param targetAngle the target's heading, radians, for a flank; NaN when unknown (a flanking
     *                    ship then holds its range like any other)
     * @param missilePos  the nearest inbound missile's position, or null if none
     */
    public void update(NpcCombatComponent combat, ShipState s, Vector2 targetPos, Vector2 targetVel, float targetAngle,
                       Vector2 missilePos, Vector2 missileVel, float dt, Controls out) {
        out.forward = 0f;
        out.strafe = 0f;
        out.turn = 0f;
        out.rotationAssist = false;
        if (targetPos == null || s.maxAccel <= 0f) {
            combat.mode = Mode.IDLE;
            out.rotationAssist = true;
            return;
        }
        los.set(targetPos).sub(s.position);
        float dist = los.len();
        if (dist < 1e-3f) los.set(1f, 0f);
        else los.scl(1f / dist);
        float facing = los.angleRad();

        if (missilePos != null && evade(combat, s, missilePos, missileVel, facing, dt, out)) return;
        if (combat.stance == NpcCombatComponent.Stance.FLANK && !Float.isNaN(targetAngle)) {
            flank(combat, s, targetPos, targetVel, targetAngle, facing, dt, out);
            return;
        }

        // Hold the range band: closing speed in proportion to the range error, and a drift
        // around the target that grows as the range settles.
        float error = dist - combat.preferredRange;
        boolean kite = combat.stance == NpcCombatComponent.Stance.KITE;
        float gain = kite && error < 0f ? combat.rangeGain * KITE_GAIN : combat.rangeGain;
        float closing = Math.max(-combat.maxClosingSpeed, Math.min(combat.maxClosingSpeed, error * gain));
        float settled = Math.max(0f, 1f - Math.abs(closing) / combat.maxClosingSpeed);
        combat.mode = settled > 0.5f ? Mode.HOLDING : Mode.CLOSING;
        side.set(-los.y, los.x).scl(kite ? 0f : combat.side * combat.circleSpeed * settled);
        desired.set(targetVel).mulAdd(los, closing).add(side);
        mixer.steer(desired.sub(s.velocity), facing, s, dt, out);
    }

    /**
     * Flies for the point {@code preferredRange} astern of the target, closing on it in proportion
     * to the distance as the range band does, with the nose on the target.
     */
    private void flank(NpcCombatComponent combat, ShipState s, Vector2 targetPos, Vector2 targetVel, float targetAngle,
                       float facing, float dt, Controls out) {
        Vector2 stern = tmp.set((float) Math.cos(targetAngle), (float) Math.sin(targetAngle))
                .scl(-combat.preferredRange).add(targetPos).sub(s.position);
        float off = stern.len();
        float speed = Math.min(combat.maxClosingSpeed, off * combat.rangeGain);
        combat.mode = off < combat.preferredRange * 0.25f ? Mode.HOLDING : Mode.CLOSING;
        if (off > 1e-3f) stern.scl(speed / off);
        desired.set(targetVel).add(stern);
        mixer.steer(desired.sub(s.velocity), facing, s, dt, out);
    }

    /** Breaks across an inbound missile's line; false if it is not a threat right now. */
    private boolean evade(NpcCombatComponent combat, ShipState s, Vector2 missilePos, Vector2 missileVel,
                          float facing, float dt, Controls out) {
        Vector2 toShip = tmp.set(s.position).sub(missilePos);
        float range = toShip.len();
        if (range > combat.evadeRange || range < 1e-3f) return false;
        toShip.scl(1f / range);
        float closingSpeed = (missileVel.x - s.velocity.x) * toShip.x + (missileVel.y - s.velocity.y) * toShip.y;
        if (closingSpeed <= 0f) return false;
        combat.mode = Mode.EVADING;
        // Keep breaking the way the ship is already sliding across the line, so it never reverses into it
        float across = s.velocity.x * -toShip.y + s.velocity.y * toShip.x
                - (missileVel.x * -toShip.y + missileVel.y * toShip.x);
        if (Math.abs(across) > 1f) combat.side = Math.signum(across);
        desired.set(-toShip.y, toShip.x).scl(combat.side * 1e4f);
        mixer.steer(desired, facing, s, dt, out);
        return true;
    }
}

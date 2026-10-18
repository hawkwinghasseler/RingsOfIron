package com.ringsofiron.aicrew.pilot;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.ringsofiron.aicrew.FixedIteratingSystem;
import com.ringsofiron.coreengine.SimClock;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.FlightControlComponent;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.physicsexterior.components.ThrusterComponent;

/**
 * Flies every ship with an {@link NpcCombatComponent} through its
 * {@link FlightControlComponent}, using {@link CombatPilotController}. Add it before the
 * physics systems, like {@link NpcPilotSystem}.
 */
public class NpcCombatSystem extends FixedIteratingSystem {

    private final ComponentMapper<NpcCombatComponent> combatM = ComponentMapper.getFor(NpcCombatComponent.class);
    private final ComponentMapper<PhysicsBodyComponent> bodyM = ComponentMapper.getFor(PhysicsBodyComponent.class);
    private final ComponentMapper<FlightControlComponent> controlM = ComponentMapper.getFor(FlightControlComponent.class);
    private final ComponentMapper<ThrusterComponent> thrusterM = ComponentMapper.getFor(ThrusterComponent.class);

    private final CombatPilotController controller = new CombatPilotController();
    private final PilotController.ShipState state = new PilotController.ShipState();
    private final PilotController.Controls controls = new PilotController.Controls();
    private final Vector2 targetPos = new Vector2(), targetVel = new Vector2();
    private final Vector2 missilePos = new Vector2(), missileVel = new Vector2();
    private ThreatSensor threats = ThreatSensor.NONE;

    public NpcCombatSystem() {
        super(Family.all(NpcCombatComponent.class, PhysicsBodyComponent.class, FlightControlComponent.class,
                ThrusterComponent.class).get(), -9, (float) SimClock.TICK_SECONDS);
    }

    public void setThreatSensor(ThreatSensor threats) {
        this.threats = threats == null ? ThreatSensor.NONE : threats;
    }

    @Override
    protected void processEntity(Entity ship, float dt) {
        Body body = bodyM.get(ship).body;
        if (body == null) return;
        NpcCombatComponent combat = combatM.get(ship);
        ThrusterComponent thrusters = thrusterM.get(ship);
        PhysicsManager.getPosition(body, state.position);
        PhysicsManager.getVelocity(body, state.velocity);
        state.angle = body.getAngle();
        state.angularVelocity = body.getAngularVelocity();
        float mass = PhysicsManager.getMass(body), inertia = PhysicsManager.getInertia(body);
        boolean canThrust = !thrusters.isOutOfFuel() && mass > 0f;
        state.maxAccel = canThrust ? thrusters.forwardThrust / mass : 0f;
        state.maxReverseAccel = canThrust ? thrusters.reverseThrust / mass : 0f;
        state.maxLateralAccel = canThrust ? thrusters.lateralThrust / mass : 0f;
        state.maxAngularAccel = inertia <= 0f ? 0f : thrusters.maxTorque / inertia;

        Vector2 tp = null;
        PhysicsBodyComponent tb = combat.target == null ? null : bodyM.get(combat.target);
        if (tb != null && tb.body != null) {
            PhysicsManager.getPosition(tb.body, targetPos);
            PhysicsManager.getVelocity(tb.body, targetVel);
            tp = targetPos;
        }
        boolean threatened = threats.nearestInbound(ship, missilePos, missileVel);
        controller.update(combat, state, tp, targetVel, threatened ? missilePos : null, missileVel, dt, controls);

        FlightControlComponent fc = controlM.get(ship);
        fc.forward = controls.forward;
        fc.strafe = controls.strafe;
        fc.turn = controls.turn;
        fc.rotationAssist = controls.rotationAssist;
    }
}

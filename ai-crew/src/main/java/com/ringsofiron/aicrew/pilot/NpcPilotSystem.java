package com.ringsofiron.aicrew.pilot;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.physics.box2d.Body;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.FlightControlComponent;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.physicsexterior.components.ThrusterComponent;

/**
 * Flies every ship with an {@link NpcPilotComponent}: reads its body, runs the
 * {@link PilotController} and writes the stick into its {@link FlightControlComponent},
 * which physics-exterior's FlightControlSystem turns into thrust each physics tick.
 * Add it before the physics systems so the controls are fresh for the step.
 */
public class NpcPilotSystem extends IteratingSystem {

    private final ComponentMapper<NpcPilotComponent> pilotM = ComponentMapper.getFor(NpcPilotComponent.class);
    private final ComponentMapper<PhysicsBodyComponent> bodyM = ComponentMapper.getFor(PhysicsBodyComponent.class);
    private final ComponentMapper<FlightControlComponent> controlM = ComponentMapper.getFor(FlightControlComponent.class);
    private final ComponentMapper<ThrusterComponent> thrusterM = ComponentMapper.getFor(ThrusterComponent.class);

    private final PilotReference reference;
    private final PilotController controller = new PilotController();
    private final PilotController.ShipState state = new PilotController.ShipState();
    private final PilotController.Controls controls = new PilotController.Controls();

    public NpcPilotSystem(PilotReference reference) {
        super(Family.all(NpcPilotComponent.class, PhysicsBodyComponent.class, FlightControlComponent.class,
                ThrusterComponent.class).get(), -10);
        this.reference = reference;
    }

    @Override
    protected void processEntity(Entity ship, float dt) {
        Body body = bodyM.get(ship).body;
        if (body == null) return;
        ThrusterComponent thrusters = thrusterM.get(ship);
        PhysicsManager.getPosition(body, state.position);
        PhysicsManager.getVelocity(body, state.velocity);
        reference.parentRelativePosition(ship, state.position, state.parentPosition);
        reference.parentRelativeVelocity(ship, state.velocity, state.parentVelocity);
        state.angle = body.getAngle();
        state.angularVelocity = body.getAngularVelocity();
        float mass = PhysicsManager.getMass(body), inertia = PhysicsManager.getInertia(body);
        boolean canThrust = !thrusters.isOutOfFuel() && mass > 0f;
        state.maxAccel = canThrust ? thrusters.forwardThrust / mass : 0f;
        state.maxReverseAccel = canThrust ? thrusters.reverseThrust / mass : 0f;
        state.maxLateralAccel = canThrust ? thrusters.lateralThrust / mass : 0f;
        state.maxAngularAccel = inertia <= 0f ? 0f : thrusters.maxTorque / inertia;

        controller.update(pilotM.get(ship), state, reference.simSeconds(), dt, controls);
        FlightControlComponent fc = controlM.get(ship);
        fc.forward = controls.forward;
        fc.strafe = controls.strafe;
        fc.turn = controls.turn;
        fc.rotationAssist = controls.rotationAssist;
    }
}

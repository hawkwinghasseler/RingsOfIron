package com.ringsofiron.physicsexterior.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.FlightControlComponent;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.physicsexterior.components.ThrusterComponent;

/**
 * Turns each ship's {@link FlightControlComponent} input into forces and torque on its body,
 * limited by its {@link ThrusterComponent} and remaining fuel.
 *
 * Forces are applied from {@link PhysicsManager.StepListener#beforeStep} so each fixed physics
 * tick gets exactly one application, however many ticks run in a frame.
 */
public class FlightControlSystem extends EntitySystem implements PhysicsManager.StepListener {
    private static final Family FAMILY =
            Family.all(PhysicsBodyComponent.class, ThrusterComponent.class, FlightControlComponent.class).get();

    private final ComponentMapper<PhysicsBodyComponent> bodyMapper = ComponentMapper.getFor(PhysicsBodyComponent.class);
    private final ComponentMapper<ThrusterComponent> thrusterMapper = ComponentMapper.getFor(ThrusterComponent.class);
    private final ComponentMapper<FlightControlComponent> controlMapper = ComponentMapper.getFor(FlightControlComponent.class);

    private final PhysicsManager physics;
    private final Vector2 force = new Vector2(), center = new Vector2();
    private ImmutableArray<Entity> entities;

    public FlightControlSystem(PhysicsManager physics) {
        this.physics = physics;
    }

    @Override
    public void addedToEngine(Engine engine) {
        entities = engine.getEntitiesFor(FAMILY);
        physics.addStepListener(this);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        physics.removeStepListener(this);
        entities = null;
    }

    @Override
    public void beforeStep(float deltaTime) {
        for (Entity entity : entities) {
            Body body = bodyMapper.get(entity).body;
            if (body != null) {
                applyControls(body, thrusterMapper.get(entity), controlMapper.get(entity), deltaTime);
            }
        }
    }

    private void applyControls(Body body, ThrusterComponent thruster, FlightControlComponent control, float deltaTime) {
        if (thruster.isOutOfFuel()) return;

        float forward = MathUtils.clamp(control.forward, -1f, 1f);
        float strafe = MathUtils.clamp(control.strafe, -1f, 1f);
        float turn = MathUtils.clamp(control.turn, -1f, 1f);

        // Local frame: +X is the nose, +Y is port.
        float localX = forward >= 0f ? forward * thruster.forwardThrust : forward * thruster.reverseThrust;
        float localY = strafe * thruster.lateralThrust;
        float torque = turn * thruster.maxTorque;

        Body tow = control.towing;
        if (tow != null) {
            // The pair's centre of mass, in meters
            float m1 = body.getMass(), m2 = tow.getMass();
            Vector2 c1 = body.getWorldCenter(), c2 = tow.getWorldCenter();
            center.set(c1).scl(m1).mulAdd(c2, m2).scl(PhysicsManager.METERS_PER_UNIT / (m1 + m2));
        }

        if (turn == 0f && control.rotationAssist && !control.assistHeld) {
            // Torque that would cancel the current spin in one tick, within RCS limits.
            float stopTorque = -inertia(body, tow) * body.getAngularVelocity() / deltaTime;
            torque = MathUtils.clamp(stopTorque, -thruster.maxTorque, thruster.maxTorque);
        }

        float throttleScale = burnFuel(thruster, Vector2.len(localX, localY), Math.abs(torque), deltaTime);
        if (throttleScale <= 0f) return;
        localX *= throttleScale;
        localY *= throttleScale;
        torque *= throttleScale;

        if (localX != 0f || localY != 0f) {
            force.set(localX, localY).rotateRad(body.getAngle());
            if (tow != null) PhysicsManager.applyForce(body, force.x, force.y, center.x, center.y);
            else PhysicsManager.applyForce(body, force.x, force.y);
        }
        if (torque != 0f) {
            PhysicsManager.applyTorque(body, torque);
        }
    }

    /** Rotational inertia of {@code body}, with {@code tow} welded to it if not null, about their common centre of mass, kg*m^2. */
    private float inertia(Body body, Body tow) {
        if (tow == null) return PhysicsManager.getInertia(body);
        float m1 = body.getMass(), m2 = tow.getMass();
        float d = body.getWorldCenter().dst(tow.getWorldCenter()) * PhysicsManager.METERS_PER_UNIT;
        // Each about its own centre, plus each one's offset from the common one (parallel axes)
        return PhysicsManager.getInertia(body) + PhysicsManager.getInertia(tow) + m1 * m2 / (m1 + m2) * d * d;
    }

    /** Deducts fuel for this tick and returns the fraction of the requested output that could be paid for. */
    private float burnFuel(ThrusterComponent thruster, float forceMagnitude, float torqueMagnitude, float deltaTime) {
        float cost = (forceMagnitude * thruster.fuelPerNewtonSecond
                + torqueMagnitude * thruster.fuelPerTorqueSecond) * deltaTime;
        if (cost <= 0f) return 1f;
        if (thruster.fuel >= cost) {
            thruster.fuel -= cost;
            return 1f;
        }
        float scale = thruster.fuel / cost;
        thruster.fuel = 0f;
        return scale;
    }
}

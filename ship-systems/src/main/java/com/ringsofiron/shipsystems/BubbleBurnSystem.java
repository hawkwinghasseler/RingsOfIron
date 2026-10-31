package com.ringsofiron.shipsystems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.physics.box2d.Body;
import com.ringsofiron.orbital.components.CelestialBodyComponent;
import com.ringsofiron.orbital.components.ManeuverNodeComponent;
import com.ringsofiron.orbital.math.KeplerOrbit;
import com.ringsofiron.orbital.math.StateVector;
import com.ringsofiron.orbital.rails.RailsUniverse;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.FlightControlComponent;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.physicsexterior.components.ThrusterComponent;

/**
 * Fires planned burns for ships flying in the bubble, the way the orbital event system does on
 * rails: when a node is due, its delta-v is added to the ship's true state in one impulse, prograde
 * and radial taken from the ship's conic around the body the bubble falls around. A ship that burns
 * propellant pays for it from its tanks ({@link PropellantFeed#payForBurn}), and gets only the
 * delta-v they can pay for.
 *
 * <p>Nothing is flown, but the burn is shown as if it were: the ship's main engine and RCS fire
 * along it ({@link FlightControlComponent#showImpulse}) for as long as the main engine would take
 * to give that delta-v, from {@link #MIN_SHOWN} to {@link #MAX_SHOWN} seconds, so a ship never
 * changes course with every nozzle cold.
 */
public class BubbleBurnSystem extends EntitySystem {
    private static final Family FAMILY = Family.all(ManeuverNodeComponent.class, PhysicsBodyComponent.class).get();
    private static final ComponentMapper<ManeuverNodeComponent> NODES = ComponentMapper.getFor(ManeuverNodeComponent.class);
    private static final ComponentMapper<PhysicsBodyComponent> BODIES = ComponentMapper.getFor(PhysicsBodyComponent.class);

    /** Fewest and most sim seconds a burn fired as an impulse is shown burning. */
    public static final float MIN_SHOWN = 0.6f, MAX_SHOWN = 3f;

    private static final ComponentMapper<FlightControlComponent> CONTROLS = ComponentMapper.getFor(FlightControlComponent.class);
    private static final ComponentMapper<ThrusterComponent> THRUSTERS = ComponentMapper.getFor(ThrusterComponent.class);

    private final PhysicsManager physics;
    private final RailsUniverse universe;
    private final double[] trueState = new double[4];
    private final StateVector state = new StateVector();
    private ImmutableArray<Entity> ships;

    public BubbleBurnSystem(PhysicsManager physics, RailsUniverse universe) {
        this.physics = physics;
        this.universe = universe;
    }

    @Override
    public void addedToEngine(Engine engine) {
        ships = engine.getEntitiesFor(FAMILY);
    }

    @Override
    public void update(float deltaTime) {
        String parentUuid = physics.getAnchorParentUuid();
        Entity parent = parentUuid == null ? null : universe.entity(parentUuid);
        CelestialBodyComponent body = parent == null ? null : universe.celestial(parent);
        if (body == null) return;
        double now = universe.now();
        for (int i = 0; i < ships.size(); i++) {
            Entity ship = ships.get(i);
            ManeuverNodeComponent m = NODES.get(ship);
            PhysicsBodyComponent pb = BODIES.get(ship);
            if (pb.body == null) continue;
            while (m.next() != null && m.next().time <= now) {
                ManeuverNodeComponent.Node n = m.nodes.removeIndex(0);
                physics.getTrueState(pb.body, trueState);
                state.set(trueState[0], trueState[1], trueState[2], trueState[3]);
                // The burn is paid for out of the ship's propellant tanks; a short one is cut short
                double dv = Math.hypot(n.prograde, n.radial);
                double scale = dv > 0 ? PropellantFeed.payForBurn(ship, PhysicsManager.getMass(pb.body), dv) / dv : 1;
                KeplerOrbit after = KeplerOrbit.fromState(state, body.mu, now)
                        .withBurn(now, n.prograde * scale, n.radial * scale);
                after.stateAt(now, state);
                physics.setTrueState(pb.body, state.x, state.y, state.vx, state.vy);
                if (dv > 0 && scale > 0) show(ship, pb.body, state.vx - trueState[2], state.vy - trueState[3]);
            }
        }
    }

    /**
     * Shows {@code ship}'s engines and RCS firing along the change in velocity ({@code dvx},
     * {@code dvy}) m/s, world frame, just given it as one impulse.
     */
    static void show(Entity ship, Body body, double dvx, double dvy) {
        FlightControlComponent controls = CONTROLS.get(ship);
        double dv = Math.hypot(dvx, dvy);
        if (controls == null || body == null || !(dv > 0)) return;
        // Into the ship's frame: +X the nose, +Y port
        double a = body.getAngle(), c = Math.cos(a), s = Math.sin(a);
        float forward = (float) ((dvx * c + dvy * s) / dv), strafe = (float) ((-dvx * s + dvy * c) / dv);
        ThrusterComponent thruster = THRUSTERS.get(ship);
        float mass = PhysicsManager.getMass(body);
        double accel = thruster == null || mass <= 0f ? 0 : thruster.forwardThrust / mass;
        float seconds = accel > 0 ? (float) Math.min(MAX_SHOWN, Math.max(MIN_SHOWN, dv / accel)) : MIN_SHOWN;
        controls.showImpulse(forward, strafe, seconds);
    }
}

package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.ringsofiron.orbital.components.CelestialBodyComponent;
import com.ringsofiron.orbital.components.ManeuverNodeComponent;
import com.ringsofiron.orbital.math.KeplerOrbit;
import com.ringsofiron.orbital.math.StateVector;
import com.ringsofiron.orbital.rails.RailsUniverse;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;

/**
 * Fires planned burns for ships flying in the bubble, the way the orbital event system does on
 * rails: when a node is due, its delta-v is added to the ship's true state in one impulse, prograde
 * and radial taken from the ship's conic around the body the bubble falls around.
 */
public class BubbleBurnSystem extends EntitySystem {
    private static final Family FAMILY = Family.all(ManeuverNodeComponent.class, PhysicsBodyComponent.class).get();
    private static final ComponentMapper<ManeuverNodeComponent> NODES = ComponentMapper.getFor(ManeuverNodeComponent.class);
    private static final ComponentMapper<PhysicsBodyComponent> BODIES = ComponentMapper.getFor(PhysicsBodyComponent.class);

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
                KeplerOrbit after = KeplerOrbit.fromState(state, body.mu, now).withBurn(now, n.prograde, n.radial);
                after.stateAt(now, state);
                physics.setTrueState(pb.body, state.x, state.y, state.vx, state.vy);
            }
        }
    }
}

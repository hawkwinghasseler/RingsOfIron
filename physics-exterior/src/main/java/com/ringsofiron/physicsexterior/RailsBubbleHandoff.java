package com.ringsofiron.physicsexterior;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.coreengine.components.PlayerControllerComponent;
import com.ringsofiron.orbital.components.OrbitComponent;
import com.ringsofiron.orbital.math.KeplerOrbit;
import com.ringsofiron.orbital.math.StateVector;
import com.ringsofiron.orbital.warp.BubbleHandoff;
import com.ringsofiron.physicsexterior.anchor.KeplerAnchor;
import com.ringsofiron.physicsexterior.components.BodyShapeComponent;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;

/**
 * Collapses the bubble onto rails for rails warp, and rebuilds it on drop-out.
 *
 * <p><b>Collapse:</b> every Box2D body, the player's included, gets an {@link OrbitComponent} from
 * its true state (around the anchor's parent, {@link PhysicsManager#getAnchorParentUuid}) plus a
 * {@link BodyShapeComponent} recording its fixtures and mass, and loses its
 * {@link PhysicsBodyComponent} (so {@code PhysicsUpdateSystem} destroys the body). Box2D is then
 * empty and idle. Angle and spin are kept on the orbit component.
 *
 * <p><b>Rebuild:</b> the anchor becomes a {@link KeplerAnchor} on the player's current orbit, and
 * every body with a recorded shape around the same parent that is within
 * {@link BubbleLimits#BUBBLE_ENTER_RADIUS} and slower than {@link BubbleLimits#B2D_ENTRY_SPEED}
 * relative to the player gets its Box2D body back at its true state. The rest stay on rails, shape
 * kept, for macro-sim's {@code BubbleTransitionSystem} to bring in later with {@link #rebuild}.
 *
 * <p>Needs {@code PhysicsUpdateSystem} in the same engine, which owns body destruction.
 */
public class RailsBubbleHandoff implements BubbleHandoff {
    private static final Family BODIES = Family.all(PhysicsBodyComponent.class).get();
    private static final Family ON_RAILS = Family.all(OrbitComponent.class, BodyShapeComponent.class).get();
    private static final Family PLAYER = Family.all(PlayerControllerComponent.class).get();

    private final ComponentMapper<PhysicsBodyComponent> bodyMapper = ComponentMapper.getFor(PhysicsBodyComponent.class);
    private final ComponentMapper<OrbitComponent> orbitMapper = ComponentMapper.getFor(OrbitComponent.class);
    private final ComponentMapper<BodyShapeComponent> shapeMapper = ComponentMapper.getFor(BodyShapeComponent.class);

    private final PhysicsManager physics;
    private final Engine engine;
    private final double[] state = new double[4];
    private final StateVector rails = new StateVector();
    private final Array<Entity> buffer = new Array<>();

    public RailsBubbleHandoff(PhysicsManager physics, Engine engine) {
        this.physics = physics;
        this.engine = engine;
    }

    @Override
    public void collapseToRails() {
        double mu = physics.getAnchorModel().getMu();
        if (mu == 0.0) throw new IllegalStateException("The bubble is not in orbit; there are no rails to collapse onto");
        double t = physics.getSimSeconds();
        String parent = physics.getAnchorParentUuid();

        for (Entity entity : engine.getEntitiesFor(BODIES)) buffer.add(entity);
        for (Entity entity : buffer) {
            Body body = bodyMapper.get(entity).body;
            if (body == null) continue;
            physics.getTrueState(body, state);
            OrbitComponent orbit = new OrbitComponent(parent,
                    KeplerOrbit.fromState(state[0], state[1], state[2], state[3], mu, t));
            orbit.attitude = body.getAngle();
            orbit.angularVelocity = body.getAngularVelocity();
            entity.add(BodyShapeComponent.capture(body));
            entity.add(orbit);
            entity.remove(PhysicsBodyComponent.class);
        }
        buffer.clear();
        physics.clearAnchor();
    }

    @Override
    public void rebuildFromRails() {
        double t = physics.getSimSeconds();
        Entity player = null;
        for (Entity e : engine.getEntitiesFor(PLAYER)) {
            if (orbitMapper.has(e)) {
                player = e;
                break;
            }
        }
        if (player != null) {
            OrbitComponent playerOrbit = orbitMapper.get(player);
            playerOrbit.stateAt(t, rails);
            physics.setAnchorModel(new KeplerAnchor(KeplerOrbit.fromState(
                    rails.x, rails.y, rails.vx, rails.vy, playerOrbit.mu, t)), playerOrbit.parentUuid);
        }

        String parent = physics.getAnchorParentUuid();
        for (Entity entity : engine.getEntitiesFor(ON_RAILS)) buffer.add(entity);
        for (Entity entity : buffer) {
            OrbitComponent orbit = orbitMapper.get(entity);
            if (entity != player && !sameParent(parent, orbit.parentUuid)) continue;
            orbit.stateAt(t, rails);
            double dx = rails.x - physics.getOriginX(), dy = rails.y - physics.getOriginY();
            double dvx = rails.vx - physics.getOriginVx(), dvy = rails.vy - physics.getOriginVy();
            boolean inRange = Math.hypot(dx, dy) < BubbleLimits.BUBBLE_ENTER_RADIUS
                    && Math.hypot(dvx, dvy) < BubbleLimits.B2D_ENTRY_SPEED;
            if (entity == player || inRange) rebuild(entity);
        }
        buffer.clear();
    }

    /**
     * Brings one rails body with a recorded shape back into Box2D at its current true state, and
     * removes its orbit and shape components. Returns the new body.
     */
    public Body rebuild(Entity entity) {
        OrbitComponent orbit = orbitMapper.get(entity);
        BodyShapeComponent shape = shapeMapper.get(entity);
        double t = physics.getSimSeconds();
        orbit.stateAt(t, rails);
        Body body = shape.build(physics, entity, 0f, 0f, (float) orbit.attitudeAt(t));
        physics.setTrueState(body, rails.x, rails.y, rails.vx, rails.vy);
        body.setAngularVelocity((float) orbit.angularVelocity);
        entity.remove(OrbitComponent.class);
        entity.remove(BodyShapeComponent.class);
        entity.add(new PhysicsBodyComponent(body));
        return body;
    }

    private static boolean sameParent(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }
}

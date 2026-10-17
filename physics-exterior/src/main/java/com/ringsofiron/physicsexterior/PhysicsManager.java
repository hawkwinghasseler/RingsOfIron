package com.ringsofiron.physicsexterior;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.signals.Signal;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.Box2D;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.Contact;
import com.badlogic.gdx.physics.box2d.ContactImpulse;
import com.badlogic.gdx.physics.box2d.ContactListener;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.Manifold;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.Shape;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.physics.box2d.WorldManifold;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.ringsofiron.physicsexterior.events.CollisionEvent;
import com.ringsofiron.physicsexterior.events.WorldShiftEvent;

/**
 * Owns the Box2D world for the local "bubble" around the player.
 *
 * <h2>Units</h2>
 * Everything outside this class is SI: meters, kilograms, seconds, newtons. Inside Box2D one length
 * unit is {@link #METERS_PER_UNIT} meters, because Box2D moves a body at most 2 units per step;
 * at 10 m per unit and 60 Hz that allows {@link #MAX_SPEED} = 1,200 m/s relative to the bubble.
 * Always read and write body state through the static helpers here ({@link #getPosition},
 * {@link #applyForce}, ...) rather than calling {@code Body} directly, so the scale stays hidden.
 *
 * <h2>Frame</h2>
 * There is no gravity and no drag. Local positions are relative to a floating origin that moves
 * through the universe at a constant velocity (an inertial frame). Its universe position and
 * velocity are kept in doubles; see {@link #getOriginX()} and {@link #rebaseOrigin}.
 */
public class PhysicsManager implements Disposable {
    public static final float TIME_STEP = 1f / 60f;
    public static final int VELOCITY_ITERATIONS = 8;
    public static final int POSITION_ITERATIONS = 3;

    /** Size of one Box2D length unit, in meters. */
    public static final float METERS_PER_UNIT = 10f;
    public static final float UNITS_PER_METER = 1f / METERS_PER_UNIT;
    /** Fastest a body can move relative to the bubble, in m/s (Box2D's 2-unit-per-step limit). */
    public static final float MAX_SPEED = 2f * METERS_PER_UNIT / TIME_STEP;

    /** Called once per fixed tick, right before the world steps. Forces applied here are used for that tick. */
    public interface StepListener {
        void beforeStep(float deltaTime);
    }

    /** New contacts, dispatched after the step so listeners may safely create or destroy bodies. */
    public final Signal<CollisionEvent> collisions = new Signal<>();
    /** Floating origin rebases, dispatched after every body has been shifted. */
    public final Signal<WorldShiftEvent> worldShifts = new Signal<>();

    private final World world;
    private final Array<StepListener> stepListeners = new Array<>();
    private final Array<CollisionEvent> pendingCollisions = new Array<>();
    private final Array<Body> bodyBuffer = new Array<>();
    private final WorldShiftEvent shiftEvent = new WorldShiftEvent();

    private double originX;
    private double originY;
    private double originVx;
    private double originVy;

    public PhysicsManager() {
        Box2D.init();
        world = new World(new Vector2(0f, 0f), true);
        world.setContactListener(new CollisionCollector());
    }

    /** Runs step listeners, advances the world and the origin by {@code deltaTime}, then dispatches new collisions. */
    public void step(float deltaTime) {
        for (int i = 0; i < stepListeners.size; i++) {
            stepListeners.get(i).beforeStep(deltaTime);
        }
        world.step(deltaTime, VELOCITY_ITERATIONS, POSITION_ITERATIONS);
        originX += originVx * deltaTime;
        originY += originVy * deltaTime;

        for (int i = 0; i < pendingCollisions.size; i++) {
            collisions.dispatch(pendingCollisions.get(i));
        }
        pendingCollisions.clear();
    }

    public void addStepListener(StepListener listener) {
        if (!stepListeners.contains(listener, true)) stepListeners.add(listener);
    }

    public void removeStepListener(StepListener listener) {
        stepListeners.removeValue(listener, true);
    }

    /**
     * Moves the origin by {@code (dx, dy)} meters and changes its velocity by {@code (dvx, dvy)} m/s.
     * Every body is moved and re-velocitied by the opposite amount, so universe positions and
     * velocities are unchanged. Must not be called during a step.
     */
    public void rebaseOrigin(float dx, float dy, float dvx, float dvy) {
        float ux = dx * UNITS_PER_METER;
        float uy = dy * UNITS_PER_METER;
        float uvx = dvx * UNITS_PER_METER;
        float uvy = dvy * UNITS_PER_METER;

        world.getBodies(bodyBuffer);
        for (int i = 0; i < bodyBuffer.size; i++) {
            Body body = bodyBuffer.get(i);
            Vector2 p = body.getPosition();
            body.setTransform(p.x - ux, p.y - uy, body.getAngle());
            if (body.getType() != BodyDef.BodyType.StaticBody) {
                Vector2 v = body.getLinearVelocity();
                body.setLinearVelocity(v.x - uvx, v.y - uvy);
            }
        }
        bodyBuffer.clear();

        originX += dx;
        originY += dy;
        originVx += dvx;
        originVy += dvy;

        shiftEvent.deltaX = dx;
        shiftEvent.deltaY = dy;
        shiftEvent.deltaVx = dvx;
        shiftEvent.deltaVy = dvy;
        shiftEvent.originX = originX;
        shiftEvent.originY = originY;
        shiftEvent.originVx = originVx;
        shiftEvent.originVy = originVy;
        worldShifts.dispatch(shiftEvent);
    }

    /** Universe X of the local origin, in meters. Universe position = origin + local position. */
    public double getOriginX() {
        return originX;
    }

    public double getOriginY() {
        return originY;
    }

    /** Universe velocity of the local origin, in m/s. Universe velocity = origin velocity + local velocity. */
    public double getOriginVx() {
        return originVx;
    }

    public double getOriginVy() {
        return originVy;
    }

    /** Raw Box2D world, in Box2D units. Prefer the meter-based helpers on this class. */
    public World getWorld() {
        return world;
    }

    // ------------------------------------------------------------------
    // Body creation
    // ------------------------------------------------------------------

    /**
     * Creates a free-flying dynamic body with one convex polygon fixture: 3 to 8 vertices,
     * counter-clockwise, in meters around the body origin. Density is in kg per square meter.
     * The body never sleeps and has no damping. {@code owner} becomes the body's user data.
     */
    public Body createPolygonBody(Entity owner, float x, float y, float angleRad, float[] vertices,
                                  float density, short category, short mask) {
        float[] scaled = new float[vertices.length];
        for (int i = 0; i < vertices.length; i++) scaled[i] = vertices[i] * UNITS_PER_METER;
        PolygonShape shape = new PolygonShape();
        shape.set(scaled);
        return createDynamicBody(owner, x, y, angleRad, shape, density, category, mask);
    }

    /** Creates a free-flying dynamic body with one circle fixture. See {@link #createPolygonBody}. */
    public Body createCircleBody(Entity owner, float x, float y, float radius,
                                 float density, short category, short mask) {
        CircleShape shape = new CircleShape();
        shape.setRadius(radius * UNITS_PER_METER);
        return createDynamicBody(owner, x, y, 0f, shape, density, category, mask);
    }

    private Body createDynamicBody(Entity owner, float x, float y, float angleRad, Shape shape,
                                   float density, short category, short mask) {
        BodyDef def = new BodyDef();
        def.type = BodyDef.BodyType.DynamicBody;
        def.position.set(x * UNITS_PER_METER, y * UNITS_PER_METER);
        def.angle = angleRad;
        def.linearDamping = 0f;
        def.angularDamping = 0f;
        // A slow drift must stay a drift; a sleeping body would stop dead.
        def.allowSleep = false;

        FixtureDef fixture = new FixtureDef();
        fixture.shape = shape;
        // kg/m^2 -> kg/unit^2, so Box2D's mass comes out in kilograms.
        fixture.density = density * METERS_PER_UNIT * METERS_PER_UNIT;
        fixture.friction = 0.3f;
        fixture.restitution = 0.2f;
        fixture.filter.categoryBits = category;
        fixture.filter.maskBits = mask;

        Body body = world.createBody(def);
        body.createFixture(fixture);
        body.setUserData(owner);
        shape.dispose();
        return body;
    }

    public void destroyBody(Body body) {
        world.destroyBody(body);
    }

    @Override
    public void dispose() {
        world.dispose();
    }

    // ------------------------------------------------------------------
    // Body state in SI units
    // ------------------------------------------------------------------

    /** Body position in local meters. */
    public static Vector2 getPosition(Body body, Vector2 out) {
        return out.set(body.getPosition()).scl(METERS_PER_UNIT);
    }

    /** Body velocity relative to the bubble, in m/s. */
    public static Vector2 getVelocity(Body body, Vector2 out) {
        return out.set(body.getLinearVelocity()).scl(METERS_PER_UNIT);
    }

    public static void setPosition(Body body, float x, float y, float angleRad) {
        body.setTransform(x * UNITS_PER_METER, y * UNITS_PER_METER, angleRad);
    }

    public static void setVelocity(Body body, float vx, float vy) {
        body.setLinearVelocity(vx * UNITS_PER_METER, vy * UNITS_PER_METER);
    }

    /** Applies a force in newtons, in local (bubble) axes, at the center of mass. */
    public static void applyForce(Body body, float fx, float fy) {
        body.applyForceToCenter(fx * UNITS_PER_METER, fy * UNITS_PER_METER, true);
    }

    /** Applies a torque in newton-meters, counter-clockwise positive. */
    public static void applyTorque(Body body, float torque) {
        body.applyTorque(torque * UNITS_PER_METER * UNITS_PER_METER, true);
    }

    /** Body mass in kilograms. */
    public static float getMass(Body body) {
        return body.getMass();
    }

    /** Rotational inertia about the center of mass, in kg*m^2. */
    public static float getInertia(Body body) {
        return body.getInertia() * METERS_PER_UNIT * METERS_PER_UNIT;
    }

    // ------------------------------------------------------------------

    /** Records new contacts during the step; they are dispatched once the world is unlocked. */
    private class CollisionCollector implements ContactListener {
        private final Vector2 velocityA = new Vector2();

        @Override
        public void beginContact(Contact contact) {
            Body bodyA = contact.getFixtureA().getBody();
            Body bodyB = contact.getFixtureB().getBody();

            CollisionEvent event = new CollisionEvent();
            event.entityA = bodyA.getUserData() instanceof Entity ? (Entity) bodyA.getUserData() : null;
            event.entityB = bodyB.getUserData() instanceof Entity ? (Entity) bodyB.getUserData() : null;

            WorldManifold manifold = contact.getWorldManifold();
            Vector2 point = manifold.getNumberOfContactPoints() > 0
                    ? manifold.getPoints()[0]
                    : bodyA.getWorldCenter();
            event.pointX = point.x * METERS_PER_UNIT;
            event.pointY = point.y * METERS_PER_UNIT;

            if (manifold.getNumberOfContactPoints() > 0) {
                velocityA.set(bodyA.getLinearVelocityFromWorldPoint(point));
                Vector2 velocityB = bodyB.getLinearVelocityFromWorldPoint(point);
                // The manifold normal points from A to B, so closing speed is (vA - vB) . n
                event.approachSpeed = velocityA.sub(velocityB).dot(manifold.getNormal()) * METERS_PER_UNIT;
            }
            pendingCollisions.add(event);
        }

        @Override
        public void endContact(Contact contact) {
        }

        @Override
        public void preSolve(Contact contact, Manifold oldManifold) {
        }

        @Override
        public void postSolve(Contact contact, ContactImpulse impulse) {
        }
    }
}

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
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.Manifold;
import com.badlogic.gdx.physics.box2d.MassData;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.QueryCallback;
import com.badlogic.gdx.physics.box2d.RayCastCallback;
import com.badlogic.gdx.physics.box2d.Shape;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.physics.box2d.WorldManifold;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.IdentityMap;
import com.ringsofiron.coreengine.SimClock;
import com.ringsofiron.physicsexterior.anchor.AnchorModel;
import com.ringsofiron.physicsexterior.anchor.InertialAnchor;
import com.ringsofiron.physicsexterior.events.AnchorChangeEvent;
import com.ringsofiron.physicsexterior.events.CollisionEvent;
import com.ringsofiron.physicsexterior.events.FastPassImpactEvent;
import com.ringsofiron.physicsexterior.events.WorldShiftEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
 * Box2D has no world gravity and no drag. Local positions are relative to a floating origin, the
 * anchor, whose true state relative to the parent body is kept in doubles by an
 * {@link AnchorModel}: an {@link InertialAnchor} by default (straight line, no gravity), or a
 * free-falling orbit. Bodies then feel only the {@link TidalField} on top of their own forces.
 * See {@link #getOriginX()}, {@link #rebaseOrigin} and {@link #setAnchorModel}.
 *
 * <h2>Time</h2>
 * Sim time is a {@code long} tick counter at 60 Hz; seconds are {@code tick / 60.0}. Under a
 * {@code GameEngine} the game's {@link SimClock} is the clock ({@link #setClock}, which
 * {@code PhysicsUpdateSystem} does on its first tick). Without one, this class counts its own
 * ticks, one per {@link #step}.
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

    /** Fixed ticks per sim second. */
    public static final int TICKS_PER_SECOND = SimClock.TICKS_PER_SECOND;

    /** Called once per fixed tick around the world step. */
    public interface StepListener {
        /** Right before the world steps. Forces applied here are used for that tick. */
        void beforeStep(float deltaTime);

        /**
         * Right after the world has stepped and sim time and the anchor have advanced, before
         * collisions are dispatched. The world is unlocked, so bodies may be created or destroyed.
         */
        default void afterStep(float deltaTime) {
        }
    }

    /** One fixture hit by {@link #rayCast}, in meters. */
    public interface RayHitCallback {
        /**
         * @param fraction how far along the ray the hit is, 0 at the start and 1 at the end
         * @return as Box2D: -1 to ignore this fixture, 0 to stop, {@code fraction} to clip the ray
         *         to this hit (closest hit wins), 1 to carry on and report every hit
         */
        float hit(Fixture fixture, float pointX, float pointY, float normalX, float normalY, float fraction);
    }

    /** New contacts, dispatched after the step so listeners may safely create or destroy bodies. */
    public final Signal<CollisionEvent> collisions = new Signal<>();
    /** Floating origin rebases, dispatched after every body has been shifted. */
    public final Signal<WorldShiftEvent> worldShifts = new Signal<>();
    /** Kinematic transients sweeping through bodies, dispatched after the step. */
    public final Signal<FastPassImpactEvent> fastPassImpacts = new Signal<>();
    /** The anchor's model, parent or orbit changed, or the bubble collapsed onto rails. */
    public final Signal<AnchorChangeEvent> anchorChanges = new Signal<>();

    private final World world;
    private final Array<StepListener> stepListeners = new Array<>();
    private final Array<CollisionEvent> pendingCollisions = new Array<>();
    private final Array<Body> bodyBuffer = new Array<>();
    private final WorldShiftEvent shiftEvent = new WorldShiftEvent();
    private final AnchorChangeEvent anchorEvent = new AnchorChangeEvent();
    private final TidalField tidalField = new TidalField(this);
    /** Each body's pose at the start of the last step, {x, y, angle} in meters and radians. */
    private final IdentityMap<Body, float[]> previousPoses = new IdentityMap<>();
    private final RayCastAdapter rayCastAdapter = new RayCastAdapter();

    private AnchorModel anchor = new InertialAnchor();
    private String anchorParentUuid;
    private final double[] origin = new double[4];
    /** Sim second {@link #origin} was evaluated at. */
    private double originTime;
    private long tick;
    private SimClock clock;

    public PhysicsManager() {
        Box2D.init();
        world = new World(new Vector2(0f, 0f), true);
        world.setContactListener(new CollisionCollector());
    }

    /**
     * Runs step listeners, advances the world, sim time and the anchor by one fixed tick, then
     * dispatches new collisions. {@code deltaTime} should be {@link #TIME_STEP}.
     */
    public void step(float deltaTime) {
        for (int i = 0; i < stepListeners.size; i++) {
            stepListeners.get(i).beforeStep(deltaTime);
        }
        recordPreviousPoses();
        world.step(deltaTime, VELOCITY_ITERATIONS, POSITION_ITERATIONS);
        // An external clock has already advanced for this tick.
        if (clock == null) tick++;
        updateOrigin();

        for (int i = 0; i < stepListeners.size; i++) {
            stepListeners.get(i).afterStep(deltaTime);
        }
        for (int i = 0; i < pendingCollisions.size; i++) {
            collisions.dispatch(pendingCollisions.get(i));
        }
        pendingCollisions.clear();
    }

    private void recordPreviousPoses() {
        world.getBodies(bodyBuffer);
        for (int i = 0; i < bodyBuffer.size; i++) {
            Body body = bodyBuffer.get(i);
            float[] pose = previousPoses.get(body);
            if (pose == null) {
                pose = new float[3];
                previousPoses.put(body, pose);
            }
            Vector2 p = body.getPosition();
            pose[0] = p.x * METERS_PER_UNIT;
            pose[1] = p.y * METERS_PER_UNIT;
            pose[2] = body.getAngle();
        }
        bodyBuffer.clear();
    }

    /**
     * The body's pose at the start of the last step, {x, y, angle} in local meters and radians,
     * already moved by any rebase since. Used for swept hit tests against moving targets.
     * Returns false and leaves {@code out} alone for a body created since the last step.
     */
    public boolean getPreviousPose(Body body, float[] out) {
        float[] pose = previousPoses.get(body);
        if (pose == null) return false;
        System.arraycopy(pose, 0, out, 0, 3);
        return true;
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
        // Previous poses move into the new frame too: the new frame's origin was at
        // (new origin - new velocity * dt) one tick ago, so add back dv * dt.
        for (float[] pose : previousPoses.values()) {
            pose[0] += dvx * TIME_STEP - dx;
            pose[1] += dvy * TIME_STEP - dy;
        }

        // The anchor restarts its free fall from the new state, e.g. the player's true state.
        anchor.reset(origin[0] + dx, origin[1] + dy, origin[2] + dvx, origin[3] + dvy, getSimSeconds());
        updateOrigin();
        dispatchAnchorChange(AnchorChangeEvent.Kind.REBASED);

        shiftEvent.deltaX = dx;
        shiftEvent.deltaY = dy;
        shiftEvent.deltaVx = dvx;
        shiftEvent.deltaVy = dvy;
        shiftEvent.originX = origin[0];
        shiftEvent.originY = origin[1];
        shiftEvent.originVx = origin[2];
        shiftEvent.originVy = origin[3];
        worldShifts.dispatch(shiftEvent);
    }

    private void updateOrigin() {
        originTime = getSimSeconds();
        anchor.stateAt(originTime, origin);
    }

    /**
     * Sim second the origin's state belongs to: the end of the last step, or when the anchor last
     * changed. Under an external clock that has already moved on to the next tick, it is one tick
     * behind {@link #getSimSeconds} until that tick's step runs. Placing something from its true
     * state at this time puts it where the bodies in the world are now.
     */
    public double getOriginTime() {
        return originTime;
    }

    /**
     * True X of the local origin (the anchor), in meters relative to the parent body.
     * True position = origin + local position.
     */
    public double getOriginX() {
        return origin[0];
    }

    public double getOriginY() {
        return origin[1];
    }

    /** True velocity of the anchor, in m/s. True velocity = origin velocity + local velocity. */
    public double getOriginVx() {
        return origin[2];
    }

    public double getOriginVy() {
        return origin[3];
    }

    public AnchorModel getAnchorModel() {
        return anchor;
    }

    /**
     * Replaces the anchor's motion, e.g. with a free-falling orbit when the bubble enters orbit
     * or changes SOI. Bodies are not moved; the new model is evaluated at the current sim time.
     */
    public void setAnchorModel(AnchorModel anchor) {
        this.anchor = anchor;
        updateOrigin();
        dispatchAnchorChange(AnchorChangeEvent.Kind.SET);
    }

    /**
     * As {@link #setAnchorModel(AnchorModel)}, also naming the body the bubble falls around. Also
     * how an SOI change re-parents the anchor: pass the new parent and the anchor's orbit around it.
     */
    public void setAnchorModel(AnchorModel anchor, String parentUuid) {
        this.anchorParentUuid = parentUuid;
        setAnchorModel(anchor);
    }

    /** Tells listeners the bubble has collapsed onto rails; the anchor is not live until it is set again. */
    public void clearAnchor() {
        dispatchAnchorChange(AnchorChangeEvent.Kind.CLEARED);
    }

    private void dispatchAnchorChange(AnchorChangeEvent.Kind kind) {
        anchorEvent.kind = kind;
        anchorEvent.model = anchor;
        anchorEvent.parentUuid = anchorParentUuid;
        anchorEvent.time = getSimSeconds();
        anchorChanges.dispatch(anchorEvent);
    }

    /** UUID of the celestial body the bubble falls around, or null if none was named. */
    public String getAnchorParentUuid() {
        return anchorParentUuid;
    }

    public void setAnchorParentUuid(String parentUuid) {
        this.anchorParentUuid = parentUuid;
    }

    /** The gravity felt inside the bubble. Shared by Box2D bodies, projectiles and the HUD. */
    public TidalField getTidalField() {
        return tidalField;
    }

    /** Current sim tick: the game clock's if one is set, otherwise the ticks stepped so far. */
    public long getTick() {
        return clock != null ? clock.getTick() : tick;
    }

    /** Sets the internal tick when there is no game clock. Re-evaluates the anchor. */
    public void setTick(long tick) {
        this.tick = tick;
        syncToClock();
    }

    /**
     * Uses the game's clock for sim time from now on (null to count ticks here again). Each fixed
     * tick's {@link #step} then runs after the clock has advanced to that tick.
     */
    public void setClock(SimClock clock) {
        this.clock = clock;
        syncToClock();
    }

    public SimClock getClock() {
        return clock;
    }

    /** Re-evaluates the anchor at the current sim time, e.g. after the clock jumped under rails warp or on load. */
    public void syncToClock() {
        updateOrigin();
    }

    /** Sim time in seconds, derived from the tick so it never drifts. */
    public double getSimSeconds() {
        return getTick() / (double) TICKS_PER_SECOND;
    }

    // ------------------------------------------------------------------
    // True (parent-relative, double) state for handoffs to and from rails
    // ------------------------------------------------------------------

    /** The body's true state {x, y, vx, vy} relative to the parent body, in doubles. */
    public void getTrueState(Body body, double[] out) {
        Vector2 p = body.getPosition();
        Vector2 v = body.getLinearVelocity();
        out[0] = origin[0] + (double) p.x * METERS_PER_UNIT;
        out[1] = origin[1] + (double) p.y * METERS_PER_UNIT;
        out[2] = origin[2] + (double) v.x * METERS_PER_UNIT;
        out[3] = origin[3] + (double) v.y * METERS_PER_UNIT;
    }

    /**
     * Places a body at a true state relative to the parent body: the subtraction from the anchor
     * is done in doubles and only the small difference is cast to float. Keeps the body's angle.
     */
    public void setTrueState(Body body, double x, double y, double vx, double vy) {
        body.setTransform((float) ((x - origin[0]) * UNITS_PER_METER),
                (float) ((y - origin[1]) * UNITS_PER_METER), body.getAngle());
        body.setLinearVelocity((float) ((vx - origin[2]) * UNITS_PER_METER),
                (float) ((vy - origin[3]) * UNITS_PER_METER));
    }

    /** Converts a true state {x, y, vx, vy} to bubble-frame meters and m/s. */
    public void trueToLocal(double[] trueState, float[] out) {
        out[0] = (float) (trueState[0] - origin[0]);
        out[1] = (float) (trueState[1] - origin[1]);
        out[2] = (float) (trueState[2] - origin[2]);
        out[3] = (float) (trueState[3] - origin[3]);
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
     * It collides on the outline given ({@link PolygonSkin}) and weighs what that outline would.
     */
    public Body createPolygonBody(Entity owner, float x, float y, float angleRad, float[] vertices,
                                  float density, short category, short mask) {
        return createPolygonBody(owner, x, y, angleRad, Collections.singletonList(vertices), density, category, mask);
    }

    /**
     * Creates a free-flying dynamic body with a convex polygon fixture for each of {@code pieces}
     * (each as {@link #createPolygonBody(Entity, float, float, float, float[], float, short, short)}
     * takes one), for a shape no single convex polygon can be. It weighs what all of them together
     * would, and collides on their outlines.
     */
    public Body createPolygonBody(Entity owner, float x, float y, float angleRad, List<float[]> pieces,
                                  float density, short category, short mask) {
        List<float[]> scaled = new ArrayList<>(pieces.size());
        for (float[] vertices : pieces) {
            float[] s = new float[vertices.length];
            for (int i = 0; i < vertices.length; i++) s[i] = vertices[i] * UNITS_PER_METER;
            scaled.add(s);
        }
        PolygonShape shape = new PolygonShape();
        shape.set(scaled.get(0));
        Body body = createDynamicBody(owner, x, y, angleRad, shape, density, category, mask);
        // Weighed on the full outlines, then given the slightly smaller shapes that collide on them
        Fixture first = body.getFixtureList().first();
        float friction = first.getFriction(), restitution = first.getRestitution(), fixtureDensity = first.getDensity();
        for (int i = 1; i < scaled.size(); i++) {
            PolygonShape piece = new PolygonShape();
            piece.set(scaled.get(i));
            body.createFixture(fixture(piece, fixtureDensity, friction, restitution, category, mask));
            piece.dispose();
        }
        MassData mass = body.getMassData();
        MassData kept = new MassData();
        kept.mass = mass.mass;
        kept.center.set(mass.center);
        kept.I = mass.I;
        while (body.getFixtureList().size > 0) body.destroyFixture(body.getFixtureList().first());
        for (float[] s : scaled) {
            PolygonShape smaller = new PolygonShape();
            smaller.set(PolygonSkin.inset(s, PolygonSkin.SKIN_UNITS));
            body.createFixture(fixture(smaller, fixtureDensity, friction, restitution, category, mask));
            smaller.dispose();
        }
        body.setMassData(kept);
        return body;
    }

    private static FixtureDef fixture(Shape shape, float density, float friction, float restitution,
                                      short category, short mask) {
        FixtureDef def = new FixtureDef();
        def.shape = shape;
        def.density = density;
        def.friction = friction;
        def.restitution = restitution;
        def.filter.categoryBits = category;
        def.filter.maskBits = mask;
        return def;
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
        previousPoses.remove(body);
        world.destroyBody(body);
    }

    // ------------------------------------------------------------------
    // Queries in meters
    // ------------------------------------------------------------------

    /** Casts a ray between two local points in meters. See {@link RayHitCallback} for the return value. */
    public void rayCast(float x1, float y1, float x2, float y2, RayHitCallback callback) {
        if (x1 == x2 && y1 == y2) return; // Box2D asserts on zero-length rays
        rayCastAdapter.target = callback;
        rayCastAdapter.ray(x2 - x1, y2 - y1);
        world.rayCast(rayCastAdapter, x1 * UNITS_PER_METER, y1 * UNITS_PER_METER,
                x2 * UNITS_PER_METER, y2 * UNITS_PER_METER);
        rayCastAdapter.target = null;
    }

    /** Reports every fixture whose bounding box overlaps the given local box, in meters. */
    public void queryAABB(float minX, float minY, float maxX, float maxY, QueryCallback callback) {
        world.QueryAABB(callback, minX * UNITS_PER_METER, minY * UNITS_PER_METER,
                maxX * UNITS_PER_METER, maxY * UNITS_PER_METER);
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

    /** Applies a force in newtons at a local point in meters, so off-center pushes also turn the body. */
    public static void applyForce(Body body, float fx, float fy, float pointX, float pointY) {
        body.applyForce(fx * UNITS_PER_METER, fy * UNITS_PER_METER,
                pointX * UNITS_PER_METER, pointY * UNITS_PER_METER, true);
    }

    /** Applies an impulse in newton-seconds at a local point in meters, e.g. a hit or recoil. */
    public static void applyImpulse(Body body, float ix, float iy, float pointX, float pointY) {
        body.applyLinearImpulse(ix * UNITS_PER_METER, iy * UNITS_PER_METER,
                pointX * UNITS_PER_METER, pointY * UNITS_PER_METER, true);
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
        // Box2D reports inertia about the body origin; move it to the center of mass.
        float aboutOrigin = body.getInertia() - body.getMass() * body.getLocalCenter().len2();
        return aboutOrigin * METERS_PER_UNIT * METERS_PER_UNIT;
    }

    /**
     * Overrides the mass the fixtures' densities would give, e.g. with mass properties computed
     * from a ship's interior grid. Center of mass is body-local meters; inertia is about it.
     */
    public static void setMassData(Body body, float massKg, float centerX, float centerY, float inertiaKgM2) {
        MassData data = new MassData();
        data.mass = massKg;
        data.center.set(centerX * UNITS_PER_METER, centerY * UNITS_PER_METER);
        // Box2D wants inertia about the body origin, in kg*unit^2.
        float aboutOrigin = inertiaKgM2 + massKg * (centerX * centerX + centerY * centerY);
        data.I = aboutOrigin * UNITS_PER_METER * UNITS_PER_METER;
        body.setMassData(data);
    }

    // ------------------------------------------------------------------

    /**
     * Converts Box2D's ray results to meters for a {@link RayHitCallback}. Box2D casts against a
     * polygon's vertices, not the skin around them it collides on ({@link PolygonSkin}), so a
     * polygon hit is moved back along the ray to the skin.
     */
    private static class RayCastAdapter implements RayCastCallback {
        RayHitCallback target;
        private float dirX, dirY, length;

        void ray(float dx, float dy) {
            length = (float) Math.sqrt(dx * dx + dy * dy);
            dirX = dx / length;
            dirY = dy / length;
        }

        @Override
        public float reportRayFixture(Fixture fixture, Vector2 point, Vector2 normal, float fraction) {
            float x = point.x * METERS_PER_UNIT, y = point.y * METERS_PER_UNIT;
            if (fixture.getShape() instanceof PolygonShape) {
                float facing = -(dirX * normal.x + dirY * normal.y);
                float back = Math.min(fraction * length, PolygonSkin.SKIN / Math.max(0.2f, facing));
                x -= dirX * back;
                y -= dirY * back;
                fraction -= back / length;
            }
            return target.hit(fixture, x, y, normal.x, normal.y, fraction);
        }
    }

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

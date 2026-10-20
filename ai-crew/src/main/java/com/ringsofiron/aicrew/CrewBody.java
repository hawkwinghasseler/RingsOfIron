package com.ringsofiron.aicrew;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.FixtureDef;

/**
 * One crew member's body in their deck's {@link InteriorWorld}: a circle of {@link #RADIUS} that
 * walls stop, and inside it a solid core of {@link #CORE_RADIUS} that other crew cannot get into.
 * Between the core and {@link #SOFT_RADIUS} crew are pushed apart softly, harder the closer they
 * get. Two closing on each other are also turned a little to each one's right, so two meeting head
 * on in a corridor sidestep each other; crew who aren't closing (standing on the same spot) are only
 * pushed straight apart, so they settle side by side rather than circling each other.
 *
 * Crew walk by force: each tick {@link #steer} pulls the body's velocity toward the one wanted,
 * up to {@link #ACCEL}. Positions are in the grid's tile units.
 */
public final class CrewBody {

    /** How close a body comes to a wall, tiles. */
    public static final float RADIUS = 0.3f;
    /** The solid core no other crew member gets into, tiles. */
    public static final float CORE_RADIUS = 0.15f;
    /** Where the push between two crew begins, tiles from each one's middle: the edge of the dot drawn for them. */
    public static final float SOFT_RADIUS = RADIUS;
    /** Push between two crew per tile they overlap, tiles/s². */
    public static final float PUSH = 40f;
    /** The share of the push turned to each one's right, when closing at {@link #SIDESTEP_SPEED} or faster. */
    public static final float SIDESTEP = 0.5f;
    /** How fast two crew close on each other for the full sidestep, tiles/s; slower closes sidestep less. */
    static final float SIDESTEP_SPEED = 0.5f;
    /** Most a crew member's own legs can change their velocity, tiles/s². */
    public static final float ACCEL = 25f;
    /** How quickly the velocity closes on the one wanted, per second. */
    static final float GAIN = 12f;
    /** How hard a crew member standing still holds their spot, per second of offset. */
    static final float HOLD = 4f;

    final Entity entity;
    final InteriorWorld world;
    final Body body;
    private final float mass;
    /** The position last written back to the crew member, to notice when something else moved them. */
    float lastX, lastY;
    /** Where a crew member who isn't walking stands. */
    float anchorX, anchorY;
    /** Progress toward the waypoint being walked to, to notice being stuck. */
    int waypoint = -1;
    float bestDistance, stalled;

    private static final Vector2 v = new Vector2();

    CrewBody(Entity entity, InteriorWorld world, float x, float y) {
        this.entity = entity;
        this.world = world;
        BodyDef def = new BodyDef();
        def.type = BodyDef.BodyType.DynamicBody;
        def.fixedRotation = true;
        def.position.set(x, y);
        body = world.world.createBody(def);
        CircleShape shape = new CircleShape();
        FixtureDef fd = new FixtureDef();
        fd.shape = shape;
        fd.friction = 0f;
        shape.setRadius(RADIUS);
        fd.density = 1f;
        fd.filter.categoryBits = InteriorWorld.BODY;
        fd.filter.maskBits = InteriorWorld.WALL;
        body.createFixture(fd);
        shape.setRadius(CORE_RADIUS);
        fd.density = 0f;
        fd.filter.categoryBits = InteriorWorld.CORE;
        fd.filter.maskBits = InteriorWorld.CORE;
        body.createFixture(fd);
        shape.dispose();
        mass = body.getMass();
        placed(x, y);
    }

    public float x() {
        return body.getPosition().x;
    }

    public float y() {
        return body.getPosition().y;
    }

    /** Moves the body to (x, y) at rest, standing there. */
    void teleport(float x, float y) {
        body.setTransform(x, y, 0f);
        body.setLinearVelocity(0f, 0f);
        body.setAwake(true);
        placed(x, y);
    }

    private void placed(float x, float y) {
        lastX = anchorX = x;
        lastY = anchorY = y;
        waypoint = -1;
    }

    /** Pulls the velocity toward (vx, vy), tiles/s, as hard as the legs allow. */
    void steer(float vx, float vy) {
        Vector2 now = body.getLinearVelocity();
        v.set(vx - now.x, vy - now.y).scl(GAIN).limit(ACCEL).scl(mass);
        if (!v.isZero()) body.applyForceToCenter(v, true);
    }

    /** Walks toward (tx, ty) at {@code speed}, slowing into it if {@code stop}. */
    void seek(float tx, float ty, float speed, boolean stop) {
        float dx = tx - x(), dy = ty - y();
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        if (d < 1e-4f) {
            steer(0f, 0f);
            return;
        }
        float s = stop ? Math.min(speed, d * GAIN * 0.5f) : speed;
        steer(dx / d * s, dy / d * s);
    }

    /** Stands at the anchor, drifting back to it when pushed off. */
    void hold(float speed) {
        float dx = anchorX - x(), dy = anchorY - y();
        v.set(dx, dy).scl(HOLD).limit(speed);
        steer(v.x, v.y);
    }

    void anchor(float x, float y) {
        anchorX = x;
        anchorY = y;
        waypoint = -1;
    }

    /** The soft push between this crew member and {@code other}, equal and opposite. */
    void pushApart(CrewBody other) {
        Vector2 a = body.getPosition(), b = other.body.getPosition();
        float dx = a.x - b.x, dy = a.y - b.y;
        float d2 = dx * dx + dy * dy, reach = 2f * SOFT_RADIUS;
        if (d2 >= reach * reach) return;
        float d = (float) Math.sqrt(d2);
        float nx, ny;
        if (d < 1e-4f) {
            nx = 1f;
            ny = 0f;
        } else {
            nx = dx / d;
            ny = dy / d;
        }
        float push = PUSH * (reach - d) * mass;
        // Sidestep only while closing: two holding the same spot would otherwise chase each other round it
        Vector2 va = body.getLinearVelocity(), vb = other.body.getLinearVelocity();
        float closing = -((va.x - vb.x) * nx + (va.y - vb.y) * ny);
        float side = SIDESTEP * Math.max(0f, Math.min(1f, closing / SIDESTEP_SPEED));
        float fx = push * (nx - side * ny), fy = push * (ny + side * nx);
        body.applyForceToCenter(fx, fy, true);
        other.body.applyForceToCenter(-fx, -fy, true);
    }
}

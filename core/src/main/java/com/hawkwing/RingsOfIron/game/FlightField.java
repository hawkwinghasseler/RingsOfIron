package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.orbital.math.KeplerOrbit;
import com.ringsofiron.physicsexterior.CollisionCategories;
import com.ringsofiron.physicsexterior.LinearTrajectory;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.TrueTrajectory;
import com.ringsofiron.physicsexterior.anchor.AnchorModel;
import com.ringsofiron.physicsexterior.anchor.KeplerAnchor;
import com.ringsofiron.physicsexterior.components.KinematicTransientComponent;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;

import java.util.Locale;
import java.util.Random;
import java.util.function.Consumer;

/**
 * What the flight scenario adds to a game's world: a field of drifting, tumbling asteroids around
 * the player's ship, rocks sent across the bubble at km/s (fast passes), and a line each time one
 * hits the ship. The flying, the world and the warp are the game's own. Headless: the debug menu's
 * flight scenario sets it up, tests run it.
 */
public class FlightField {
    public static final int ASTEROID_COUNT = 40;
    static final float ASTEROID_FIELD = 3000f;
    /** No asteroid starts nearer the ship than this, meters. */
    static final float ASTEROID_CLEARANCE = 150f;
    public static final float FAST_PASS_SPEED = 2500f;
    /** Seconds a fast pass lives: long enough to cross the bubble past the ship. */
    static final float FAST_PASS_LIFETIME = 4f;
    /** How far out a fast pass starts, meters, aimed to pass within a few meters of the ship. */
    static final double FAST_PASS_START = 4000.0;
    /** The ship's drift and tumble when the field is set up, m/s and rad/s, relative to the bubble. */
    static final float START_VX = 25f, START_VY = 8f, START_SPIN = 0.35f;

    public final GameWorld world;
    public final Entity player;
    public final Array<Entity> asteroids = new Array<>();
    /** Rocks crossing the bubble at km/s, swept for hits rather than simulated by Box2D. */
    public final Array<Entity> fastPasses = new Array<>();
    private final Array<float[]> fastPassAges = new Array<>();
    private final Random dice = new Random(7);

    /** A field around {@code player}'s ship in {@code world}; {@code impacts} hears "IMPACT 3.2 m/s" when something hits it. */
    public FlightField(GameWorld world, Entity player, Consumer<String> impacts) {
        this.world = world;
        this.player = player;
        world.physics.collisions.add((signal, hit) -> {
            if (hit.entityA == player || hit.entityB == player) impacts.accept(impact("IMPACT", Math.abs(hit.approachSpeed)));
        });
        world.physics.fastPassImpacts.add((signal, hit) -> {
            if (hit.target == player) impacts.accept(impact("FAST PASS HIT", hit.relativeSpeed));
        });
    }

    /** The player's ship's body. */
    public Body body() {
        return world.docking.bodyOf(player);
    }

    /**
     * Sets the ship drifting and tumbling where it is, and scatters {@link #ASTEROID_COUNT} rocks
     * around it, a dozen of them close by. Rocks from an earlier call are taken away first.
     */
    public void scatter() {
        clear();
        dice.setSeed(7);
        Body body = body();
        Vector2 at = PhysicsManager.getPosition(body, new Vector2());
        PhysicsManager.setVelocity(body, START_VX, START_VY);
        body.setAngularVelocity(START_SPIN);
        for (int i = 0; i < ASTEROID_COUNT; i++) spawnAsteroid(at.x, at.y, i < 12 ? 600f : ASTEROID_FIELD);
    }

    /** Takes away the field's rocks and fast passes. */
    public void clear() {
        for (Entity e : asteroids) world.engine.removeEntity(e);
        for (Entity e : fastPasses) world.engine.removeEntity(e);
        asteroids.clear();
        fastPasses.clear();
        fastPassAges.clear();
    }

    /** Ages the fast passes by the sim seconds that went by, and takes away the ones that have crossed. */
    public void update(float simDelta) {
        for (int i = fastPasses.size - 1; i >= 0; i--) {
            float[] age = fastPassAges.get(i);
            age[0] += simDelta;
            if (age[0] > FAST_PASS_LIFETIME) {
                world.engine.removeEntity(fastPasses.removeIndex(i));
                fastPassAges.removeIndex(i);
            }
        }
    }

    /**
     * Sends a rock across the bubble at {@link #FAST_PASS_SPEED}, aimed to pass within a few meters
     * of the ship. In orbit it falls along its own conic around the bubble's body; out of orbit it
     * flies straight.
     */
    public Entity spawnFastPass() {
        double[] ship = new double[4];
        world.physics.getTrueState(body(), ship);
        float angle = dice.nextFloat() * MathUtils.PI2;
        float dirX = MathUtils.cos(angle), dirY = MathUtils.sin(angle);
        float miss = -12f + dice.nextFloat() * 24f;
        double startX = ship[0] - dirX * FAST_PASS_START - dirY * miss;
        double startY = ship[1] - dirY * FAST_PASS_START + dirX * miss;
        double vx = ship[2] + dirX * FAST_PASS_SPEED;
        double vy = ship[3] + dirY * FAST_PASS_SPEED;
        double now = world.physics.getSimSeconds();
        AnchorModel anchor = world.physics.getAnchorModel();
        TrueTrajectory path = anchor instanceof KeplerAnchor
                ? new KeplerAnchor(KeplerOrbit.fromState(startX, startY, vx, vy, ((KeplerAnchor) anchor).getMu(), now))::stateAt
                : new LinearTrajectory(startX, startY, vx, vy, now);
        Entity rock = world.engine.createEntity();
        rock.add(new TransformComponent());
        rock.add(new KinematicTransientComponent(path, 4f));
        world.engine.addEntity(rock);
        fastPasses.add(rock);
        fastPassAges.add(new float[]{0f});
        return rock;
    }

    static String impact(String label, float speed) {
        return String.format(Locale.ROOT, "%s  %.1f m/s", label, speed);
    }

    /** A drifting, tumbling rock within {@code range} meters of (x, y), but not on top of the ship. */
    private void spawnAsteroid(float x0, float y0, float range) {
        float x, y;
        do {
            x = range * (dice.nextFloat() * 2f - 1f);
            y = range * (dice.nextFloat() * 2f - 1f);
        } while (Vector2.len(x, y) < ASTEROID_CLEARANCE);
        Entity rock = world.engine.createEntity();
        rock.add(new TransformComponent());
        float size = 6f + dice.nextFloat() * 29f;
        Body body = dice.nextBoolean()
                ? world.physics.createCircleBody(rock, x0 + x, y0 + y, size, 200f,
                        CollisionCategories.CATEGORY_ASTEROID, CollisionCategories.MASK_ALL)
                : world.physics.createPolygonBody(rock, x0 + x, y0 + y, dice.nextFloat() * MathUtils.PI2, lumpyPolygon(size), 200f,
                        CollisionCategories.CATEGORY_ASTEROID, CollisionCategories.MASK_ALL);
        PhysicsManager.setVelocity(body, -6f + dice.nextFloat() * 12f, -6f + dice.nextFloat() * 12f);
        body.setAngularVelocity(-0.4f + dice.nextFloat() * 0.8f);
        rock.add(new PhysicsBodyComponent(body));
        world.engine.addEntity(rock);
        asteroids.add(rock);
    }

    private float[] lumpyPolygon(float radius) {
        int n = 7;
        float[] v = new float[n * 2];
        for (int i = 0; i < n; i++) {
            float a = MathUtils.PI2 * i / n;
            float r = radius * (0.8f + dice.nextFloat() * 0.2f);
            v[i * 2] = MathUtils.cos(a) * r;
            v[i * 2 + 1] = MathUtils.sin(a) * r;
        }
        return v;
    }
}

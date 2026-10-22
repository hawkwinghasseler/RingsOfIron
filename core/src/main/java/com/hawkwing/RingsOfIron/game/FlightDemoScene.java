package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.coreengine.components.PlayerControllerComponent;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.coreengine.components.UuidComponent;
import com.ringsofiron.orbital.components.CelestialBodyComponent;
import com.ringsofiron.orbital.math.KeplerOrbit;
import com.ringsofiron.orbital.warp.TimeWarpController;
import com.ringsofiron.physicsexterior.CollisionCategories;
import com.ringsofiron.physicsexterior.LinearTrajectory;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.TrueTrajectory;
import com.ringsofiron.physicsexterior.anchor.KeplerAnchor;
import com.ringsofiron.physicsexterior.components.FlightControlComponent;
import com.ringsofiron.physicsexterior.components.KinematicTransientComponent;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.physicsexterior.components.ThrusterComponent;

import java.nio.file.Path;
import java.util.Random;

/**
 * The flight demo's world, headless: the game's own {@link GameWorld} and corvette, in empty space
 * among drifting asteroids, or with the whole bubble in a 400 km orbit around the Earth. Nothing
 * slows the ship down: let go of the controls and it keeps its speed and spin. The asteroids, the
 * orbit toggle, the fast passes and the warp keys are the demo's; the flying is the game's.
 * A screen draws it; tests run it.
 */
public class FlightDemoScene {
    public static final int ASTEROID_COUNT = 40;
    static final float ASTEROID_FIELD = 3000f;
    /** No asteroid starts nearer the ship than this, meters. */
    static final float ASTEROID_CLEARANCE = 150f;
    public static final float FAST_PASS_SPEED = 2500f;
    /** Seconds a fast pass lives: long enough to cross the bubble past the ship. */
    static final float FAST_PASS_LIFETIME = 4f;
    /** How far out a fast pass starts, meters, aimed to pass within a few meters of the ship. */
    static final double FAST_PASS_START = 4000.0;
    /** Seconds the impact notice stays up. */
    static final float IMPACT_SECONDS = 2f;
    /** The ship's drift and tumble at the start, m/s and rad/s. */
    static final float START_VX = 25f, START_VY = 8f, START_SPIN = 0.35f;

    public final Path assetsDir;
    public GameWorld world;
    public Entity player;
    public final Array<Entity> asteroids = new Array<>();
    /** Rocks crossing the bubble at km/s, swept for hits rather than simulated by Box2D. */
    public final Array<Entity> fastPasses = new Array<>();
    private final Array<float[]> fastPassAges = new Array<>();
    /** The bubble is in orbit around the Earth rather than in empty space. */
    public boolean orbitMode;
    /**
     * Where the bubble's local origin is in the grid's frame, and how fast it moves: carried along
     * by every rebase, so a grid drawn in this frame never jumps. Out of orbit the grid's frame is
     * the universe's; in orbit it falls with the bubble.
     */
    public double frameX, frameY, frameVx, frameVy;
    private String impact;
    private float impactTimer;
    private final Random dice = new Random(7);

    public FlightDemoScene(Path assetsDir) {
        this.assetsDir = assetsDir;
    }

    /** Throws away the current world and sets the scene up again, in orbit or not. */
    public void load(boolean orbit) {
        dispose();
        orbitMode = orbit;
        asteroids.clear();
        fastPasses.clear();
        fastPassAges.clear();
        impact = null;
        impactTimer = 0f;
        frameX = frameY = frameVx = frameVy = 0.0;
        dice.setSeed(7);

        world = new GameWorld();
        world.loadContent(assetsDir, assetsDir.resolve("no-mods-here"));
        if (orbit) {
            Entity earth = world.engine.createEntity();
            earth.add(new UuidComponent(GameScenario.EARTH));
            earth.add(new CelestialBodyComponent("Earth", GameScenario.MU_EARTH, GameScenario.R_EARTH, Double.POSITIVE_INFINITY));
            world.engine.addEntity(earth);
            world.macroSim.registerMassiveBody(GameScenario.EARTH);
            world.setAnchor(GameScenario.EARTH, KeplerOrbit.circular(GameScenario.MU_EARTH, GameScenario.ORBIT_RADIUS,
                    0.0, world.engine.getSimSeconds(), 1));
        }
        world.physics.worldShifts.add((signal, shift) -> {
            frameX += shift.deltaX;
            frameY += shift.deltaY;
            frameVx += shift.deltaVx;
            frameVy += shift.deltaVy;
        });
        world.physics.collisions.add((signal, hit) -> {
            if (hit.entityA == player || hit.entityB == player) showImpact("IMPACT", Math.abs(hit.approachSpeed));
        });
        world.physics.fastPassImpacts.add((signal, hit) -> {
            if (hit.target == player) showImpact("FAST PASS HIT", hit.relativeSpeed);
        });

        player = world.getContent().factory.spawnShip(GameScenario.PLAYER_SHIP, 0f, 0f, 0f);
        player.add(new PlayerControllerComponent());
        // Already drifting and tumbling when the demo starts
        Body body = body();
        PhysicsManager.setVelocity(body, START_VX, START_VY);
        body.setAngularVelocity(START_SPIN);
        for (int i = 0; i < ASTEROID_COUNT; i++) spawnAsteroid(i < 12 ? 600f : ASTEROID_FIELD);
    }

    /** One frame: the world at its warp, then the fast passes age and the impact notice fades. */
    public void update(float delta) {
        world.update(delta);
        float simDelta = delta * warp();
        for (int i = fastPasses.size - 1; i >= 0; i--) {
            float[] age = fastPassAges.get(i);
            age[0] += simDelta;
            if (age[0] > FAST_PASS_LIFETIME) {
                world.engine.removeEntity(fastPasses.removeIndex(i));
                fastPassAges.removeIndex(i);
            }
        }
        if (impactTimer > 0f && (impactTimer -= delta) <= 0f) impact = null;
    }

    /** The player's ship's body. */
    public Body body() {
        return world.docking.bodyOf(player);
    }

    public FlightControlComponent controls() {
        return player.getComponent(FlightControlComponent.class);
    }

    public ThrusterComponent thrusters() {
        return player.getComponent(ThrusterComponent.class);
    }

    /** Turns rotation assist on or off; returns whether it is now on. */
    public boolean toggleRotationAssist() {
        FlightControlComponent c = controls();
        c.rotationAssist = !c.rotationAssist;
        return c.rotationAssist;
    }

    /** The physics warp the world runs at. */
    public int warp() {
        return world.timeWarp.getWarp();
    }

    /** One step up or down the game's physics warp levels; the demo never goes onto the rails. */
    public int stepWarp(int direction) {
        int[] levels = TimeWarpController.PHYSICS_LEVELS;
        int at = 0;
        for (int i = 0; i < levels.length; i++) if (levels[i] <= warp()) at = i;
        world.timeWarp.setWarp(levels[MathUtils.clamp(at + direction, 0, levels.length - 1)]);
        return warp();
    }

    /** The impact notice ("IMPACT 3.2 m/s"), or null when nothing hit the ship lately. */
    public String impact() {
        return impact;
    }

    /** The ship's velocity in the grid's frame (see {@link #frameX}), m/s. */
    public Vector2 gridVelocity(Vector2 out) {
        Vector2 v = PhysicsManager.getVelocity(body(), out);
        double ox = orbitMode ? frameVx : world.physics.getOriginVx();
        double oy = orbitMode ? frameVy : world.physics.getOriginVy();
        return out.set((float) (ox + v.x), (float) (oy + v.y));
    }

    /** Where the grid's frame puts the bubble's local origin, meters. */
    public double gridOriginX() {
        return orbitMode ? frameX : world.physics.getOriginX();
    }

    public double gridOriginY() {
        return orbitMode ? frameY : world.physics.getOriginY();
    }

    /** Sends a rock across the bubble at {@link #FAST_PASS_SPEED}, aimed to pass within a few meters of the ship. */
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
        // In orbit the rock falls too, along its own conic; otherwise it flies straight
        TrueTrajectory path = orbitMode
                ? new KeplerAnchor(KeplerOrbit.fromState(startX, startY, vx, vy, GameScenario.MU_EARTH, now))::stateAt
                : new LinearTrajectory(startX, startY, vx, vy, now);
        Entity rock = world.engine.createEntity();
        rock.add(new TransformComponent());
        rock.add(new KinematicTransientComponent(path, 4f));
        world.engine.addEntity(rock);
        fastPasses.add(rock);
        fastPassAges.add(new float[]{0f});
        return rock;
    }

    public void dispose() {
        if (world != null) world.dispose();
        world = null;
    }

    // ------------------------------------------------------------------

    private void showImpact(String label, float speed) {
        impact = String.format(java.util.Locale.ROOT, "%s  %.1f m/s", label, speed);
        impactTimer = IMPACT_SECONDS;
    }

    /** A drifting, tumbling rock somewhere within {@code range} meters of the start, but not on top of the ship. */
    private void spawnAsteroid(float range) {
        float x, y;
        do {
            x = range * (dice.nextFloat() * 2f - 1f);
            y = range * (dice.nextFloat() * 2f - 1f);
        } while (Vector2.len(x, y) < ASTEROID_CLEARANCE);
        Entity rock = world.engine.createEntity();
        rock.add(new TransformComponent());
        float size = 6f + dice.nextFloat() * 29f;
        Body body = dice.nextBoolean()
                ? world.physics.createCircleBody(rock, x, y, size, 200f,
                        CollisionCategories.CATEGORY_ASTEROID, CollisionCategories.MASK_ALL)
                : world.physics.createPolygonBody(rock, x, y, dice.nextFloat() * MathUtils.PI2, lumpyPolygon(size), 200f,
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

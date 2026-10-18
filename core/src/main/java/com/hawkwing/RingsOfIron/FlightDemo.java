package com.hawkwing.RingsOfIron;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.Shape;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.ringsofiron.coreengine.components.PlayerControllerComponent;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.interiorgrid.ChassisBlueprint;
import com.ringsofiron.interiorgrid.ChassisBlueprintLoader;
import com.ringsofiron.interiorgrid.MaterialTable;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.physicsexterior.CollisionCategories;
import com.ringsofiron.physicsexterior.LinearTrajectory;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.TrueTrajectory;
import com.ringsofiron.physicsexterior.anchor.PointMassAnchor;
import com.ringsofiron.physicsexterior.components.FlightControlComponent;
import com.ringsofiron.physicsexterior.components.KinematicTransientComponent;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.physicsexterior.components.ThrusterComponent;
import com.ringsofiron.physicsexterior.systems.FlightControlSystem;
import com.ringsofiron.physicsexterior.systems.FloatingOriginSystem;
import com.ringsofiron.physicsexterior.systems.KinematicTransientSystem;
import com.ringsofiron.physicsexterior.systems.PhysicsUpdateSystem;
import com.ringsofiron.physicsexterior.systems.TidalGravitySystem;

import java.util.Locale;

/**
 * Interactive demo for the physics-exterior module: a ship drifting under inertia among asteroids.
 *
 * Nothing slows the ship down. Let go of the controls and it keeps its speed and spin forever.
 *
 * Orbit mode puts the whole bubble in a 400 km orbit around an Earth-sized planet. The bubble falls
 * freely, so flying feels the same, but tidal gravity slowly pulls neighbors apart: try it at 10x.
 * Fast passes are rocks crossing the bubble at km/s, too fast for Box2D, swept for hits instead.
 *
 * Controls:
 *   W / S        - Main engine / retro thrusters
 *   A / D        - Turn left / right
 *   Q / E        - Strafe left / right
 *   X            - Toggle rotation assist (cancels spin when not turning)
 *   O            - Toggle orbit mode (restarts the scene)
 *   , / .        - Physics time warp down / up (1x to 10x)
 *   F            - Send a fast pass (a rock at 2.5 km/s) past the ship
 *   Mouse wheel  - Zoom
 *   R            - Reset
 *   ESC          - Exit
 */
public class FlightDemo extends ApplicationAdapter {

    private static final float MIN_VIEW = 60f;      // meters across the screen when zoomed in
    private static final float MAX_VIEW = 8000f;
    private static final float GRID_STEP = 100f;    // meters between faint grid lines
    private static final float GRID_MAJOR = 1000f;  // meters between bright grid lines
    private static final int ASTEROID_COUNT = 40;
    private static final float ASTEROID_FIELD = 3000f;

    // Ship hull, nose along +X, in meters. Convex, counter-clockwise.
    private static final float[] SHIP_HULL = {14f, 0f, 2f, 7f, -10f, 6f, -10f, -6f, 2f, -7f};
    private static final float SHIP_DENSITY = 40f;  // kg per square meter of hull plan
    // Interior layout of the same hull, 2 m tiles; the body takes its mass from it.
    // Text rows read top-to-bottom; tile [0][0] is bottom-left at (-10, -6) in ship-local meters.
    private static final String[] SHIP_INTERIOR = {
        "######     ",
        "#....###   ",
        "#..I....## ",
        "#..I....## ",
        "#....###   ",
        "######     "
    };
    private static final float INTERIOR_TILE = 2f;

    private static final double PLANET_MU = 3.986004418e14;   // Earth, m^3/s^2
    private static final double PLANET_RADIUS = 6_371_000.0;
    private static final double ORBIT_RADIUS = PLANET_RADIUS + 400_000.0;
    private static final int[] WARP_LEVELS = {1, 2, 5, 10};
    private static final float FAST_PASS_SPEED = 2500f;
    private static final float FAST_PASS_LIFETIME = 4f;

    // --- Simulation ---
    private PhysicsManager physics;
    private Engine engine;
    private FloatingOriginSystem floatingOrigin;
    private Entity player;
    private Body playerBody;
    private FlightControlComponent controls;
    private ThrusterComponent thrusters;
    private ShipGridComponent shipGrid;
    private boolean orbitMode;
    private int warpIndex;
    private final Array<Entity> fastPasses = new Array<>();
    private final Array<float[]> fastPassAges = new Array<>();
    /** Grid-frame position of the local origin, moved by every rebase so the grid never jumps. */
    private double frameX, frameY, frameVx, frameVy;

    // --- Rendering ---
    private OrthographicCamera camera;
    private ExtendViewport viewport;
    private OrthographicCamera hudCamera;
    private ShapeRenderer shapes;
    private SpriteBatch batch;
    private BitmapFont font;
    private float viewMeters = 300f;

    // --- HUD state ---
    private final Vector2 tmp = new Vector2();
    private final Vector2 interiorPos = new Vector2();
    private final Vector2 velocity = new Vector2();
    private float impactTimer;
    private float impactSpeed;
    private String impactLabel = "IMPACT";

    @Override
    public void create() {
        camera = new OrthographicCamera();
        viewport = new ExtendViewport(viewMeters, viewMeters, camera);
        hudCamera = new OrthographicCamera();
        shapes = new ShapeRenderer();
        batch = new SpriteBatch();
        font = new BitmapFont();

        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean scrolled(float amountX, float amountY) {
                viewMeters = MathUtils.clamp(viewMeters * (float) Math.pow(1.15f, amountY), MIN_VIEW, MAX_VIEW);
                viewport.setMinWorldWidth(viewMeters);
                viewport.setMinWorldHeight(viewMeters);
                viewport.update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
                return true;
            }
        });

        buildScene();
    }

    private void buildScene() {
        if (physics != null) {
            engine.removeAllEntities();
            physics.dispose();
        }
        physics = new PhysicsManager();
        engine = new Engine();
        engine.addSystem(new FlightControlSystem(physics));
        engine.addSystem(new TidalGravitySystem(physics));
        engine.addSystem(new PhysicsUpdateSystem(physics));
        engine.addSystem(new KinematicTransientSystem(physics));
        floatingOrigin = new FloatingOriginSystem(physics);
        engine.addSystem(floatingOrigin);
        fastPasses.clear();
        fastPassAges.clear();
        if (orbitMode) {
            physics.setAnchorModel(PointMassAnchor.circular(PLANET_MU, ORBIT_RADIUS, 0.0));
        }
        frameX = frameY = frameVx = frameVy = 0.0;
        physics.worldShifts.add((signal, shift) -> {
            frameX += shift.deltaX;
            frameY += shift.deltaY;
            frameVx += shift.deltaVx;
            frameVy += shift.deltaVy;
        });

        physics.collisions.add((signal, hit) -> {
            if (hit.entityA == player || hit.entityB == player) {
                showImpact("IMPACT", Math.abs(hit.approachSpeed));
            }
        });
        physics.fastPassImpacts.add((signal, hit) -> {
            if (hit.target == player) showImpact("FAST PASS HIT", hit.relativeSpeed);
        });

        spawnPlayer();
        MathUtils.random.setSeed(7);
        for (int i = 0; i < ASTEROID_COUNT; i++) spawnAsteroid(i < 12 ? 600f : ASTEROID_FIELD);
    }

    private void spawnPlayer() {
        player = new Entity();
        player.add(new TransformComponent());
        player.add(new PlayerControllerComponent());
        playerBody = physics.createPolygonBody(player, 0f, 0f, 0f, SHIP_HULL, SHIP_DENSITY,
                CollisionCategories.CATEGORY_SHIP, CollisionCategories.MASK_ALL);
        player.add(new PhysicsBodyComponent(playerBody));

        // Mass, center of mass and inertia come from the interior grid, as they will in the game.
        ChassisBlueprint blueprint = new ChassisBlueprint(SHIP_INTERIOR).tileSize(INTERIOR_TILE).origin(-10f, -6f);
        shipGrid = ChassisBlueprintLoader.load(blueprint).grid;
        player.add(shipGrid);
        ShipGridComponent.MassProperties massProps = shipGrid.computeMassProperties(MaterialTable.createDefault());
        PhysicsManager.setMassData(playerBody, massProps.mass, massProps.center.x, massProps.center.y, massProps.inertia);

        // Size the engines from the hull's mass so handling is independent of the numbers above.
        float mass = PhysicsManager.getMass(playerBody);
        float inertia = PhysicsManager.getInertia(playerBody);
        thrusters = new ThrusterComponent(mass * 20f, mass * 8f, mass * 6f, inertia * 2.5f);
        // Enough fuel for about two minutes of full main engine.
        thrusters.withFuel(1000f, 1000f / (thrusters.forwardThrust * 120f), 1000f / (thrusters.maxTorque * 600f));
        player.add(thrusters);
        controls = new FlightControlComponent();
        player.add(controls);
        engine.addEntity(player);

        // Already drifting and tumbling when the demo starts.
        PhysicsManager.setVelocity(playerBody, 25f, 8f);
        playerBody.setAngularVelocity(0.35f);
    }

    /** Spawns a drifting, tumbling rock somewhere within {@code range} meters of the start, but not on top of the ship. */
    private void spawnAsteroid(float range) {
        float x;
        float y;
        do {
            x = MathUtils.random(-range, range);
            y = MathUtils.random(-range, range);
        } while (Vector2.len(x, y) < 150f);

        Entity rock = new Entity();
        rock.add(new TransformComponent());
        float size = MathUtils.random(6f, 35f);
        Body body;
        if (MathUtils.randomBoolean()) {
            body = physics.createCircleBody(rock, x, y, size, 200f,
                    CollisionCategories.CATEGORY_ASTEROID, CollisionCategories.MASK_ALL);
        } else {
            body = physics.createPolygonBody(rock, x, y, MathUtils.random(MathUtils.PI2), lumpyPolygon(size), 200f,
                    CollisionCategories.CATEGORY_ASTEROID, CollisionCategories.MASK_ALL);
        }
        PhysicsManager.setVelocity(body, MathUtils.random(-6f, 6f), MathUtils.random(-6f, 6f));
        body.setAngularVelocity(MathUtils.random(-0.4f, 0.4f));
        rock.add(new PhysicsBodyComponent(body));
        engine.addEntity(rock);
    }

    private static float[] lumpyPolygon(float radius) {
        int n = 7;
        float[] v = new float[n * 2];
        for (int i = 0; i < n; i++) {
            float a = MathUtils.PI2 * i / n;
            float r = radius * MathUtils.random(0.8f, 1f);
            v[i * 2] = MathUtils.cos(a) * r;
            v[i * 2 + 1] = MathUtils.sin(a) * r;
        }
        return v;
    }

    private void showImpact(String label, float speed) {
        impactLabel = label;
        impactSpeed = speed;
        impactTimer = 2f;
    }

    /** Sends a rock across the bubble at {@link #FAST_PASS_SPEED}, aimed to pass within a few meters of the ship. */
    private void spawnFastPass() {
        double[] ship = new double[4];
        physics.getTrueState(playerBody, ship);
        float angle = MathUtils.random(MathUtils.PI2);
        float dirX = MathUtils.cos(angle);
        float dirY = MathUtils.sin(angle);
        float miss = MathUtils.random(-12f, 12f);
        double startX = ship[0] - dirX * 4000.0 - dirY * miss;
        double startY = ship[1] - dirY * 4000.0 + dirX * miss;

        double vx = ship[2] + dirX * FAST_PASS_SPEED;
        double vy = ship[3] + dirY * FAST_PASS_SPEED;
        double now = physics.getSimSeconds();
        // In orbit the rock falls too, along its own conic; otherwise it flies straight.
        TrueTrajectory path = orbitMode
                ? new PointMassAnchor(PLANET_MU, startX, startY, vx, vy, now)::stateAt
                : new LinearTrajectory(startX, startY, vx, vy, now);

        Entity rock = new Entity();
        rock.add(new TransformComponent());
        rock.add(new KinematicTransientComponent(path, 4f));
        engine.addEntity(rock);
        fastPasses.add(rock);
        fastPassAges.add(new float[]{0f});
    }

    private void ageFastPasses(float delta) {
        for (int i = fastPasses.size - 1; i >= 0; i--) {
            float[] age = fastPassAges.get(i);
            age[0] += delta;
            if (age[0] > FAST_PASS_LIFETIME) {
                engine.removeEntity(fastPasses.removeIndex(i));
                fastPassAges.removeIndex(i);
            }
        }
    }

    // -----------------------------------------------------------------------
    @Override
    public void render() {
        float delta = Math.min(Gdx.graphics.getDeltaTime(), 0.25f);
        handleInput();
        float simDelta = delta * WARP_LEVELS[warpIndex];
        engine.update(simDelta);
        ageFastPasses(simDelta);
        if (impactTimer > 0f) impactTimer -= delta;

        TransformComponent pt = player.getComponent(TransformComponent.class);
        camera.position.set(pt.x, pt.y, 0f);
        camera.update();

        Gdx.gl.glClearColor(0.02f, 0.02f, 0.05f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        shapes.setProjectionMatrix(camera.combined);
        drawGrid();
        drawBodies();
        drawInterior();
        drawFastPasses();
        drawVelocityVector();
        drawPlanetDirection();
        drawHud();
    }

    private void handleInput() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) Gdx.app.exit();
        if (Gdx.input.isKeyJustPressed(Input.Keys.R)) {
            buildScene();
            return;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.O)) {
            orbitMode = !orbitMode;
            buildScene();
            return;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.X)) controls.rotationAssist = !controls.rotationAssist;
        if (Gdx.input.isKeyJustPressed(Input.Keys.PERIOD)) warpIndex = Math.min(warpIndex + 1, WARP_LEVELS.length - 1);
        if (Gdx.input.isKeyJustPressed(Input.Keys.COMMA)) warpIndex = Math.max(warpIndex - 1, 0);
        if (Gdx.input.isKeyJustPressed(Input.Keys.F)) spawnFastPass();

        controls.clear();
        if (Gdx.input.isKeyPressed(Input.Keys.W)) controls.forward += 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.S)) controls.forward -= 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.A)) controls.turn += 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.D)) controls.turn -= 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.Q)) controls.strafe += 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.E)) controls.strafe -= 1f;
    }

    // -----------------------------------------------------------------------
    // Rendering
    // -----------------------------------------------------------------------

    /**
     * Grid that slides past as the ship drifts and never jumps on a rebase. Out of orbit it is fixed
     * in universe coordinates. In orbit the universe frame moves at km/s, so the grid instead
     * follows the falling bubble, carried along by each rebase.
     */
    private void drawGrid() {
        float halfW = viewport.getWorldWidth() / 2f;
        float halfH = viewport.getWorldHeight() / 2f;
        double originX = orbitMode ? frameX : physics.getOriginX();
        double originY = orbitMode ? frameY : physics.getOriginY();
        double left = originX + camera.position.x - halfW;
        double right = originX + camera.position.x + halfW;
        double bottom = originY + camera.position.y - halfH;
        double top = originY + camera.position.y + halfH;
        float step = viewport.getWorldWidth() > 3000f ? GRID_MAJOR : GRID_STEP;

        shapes.begin(ShapeRenderer.ShapeType.Line);
        for (double gx = Math.floor(left / step) * step; gx <= right; gx += step) {
            shapes.setColor(isMajor(gx) ? GRID_MAJOR_COLOR : GRID_MINOR_COLOR);
            float x = (float) (gx - originX);
            shapes.line(x, (float) (bottom - originY), x, (float) (top - originY));
        }
        for (double gy = Math.floor(bottom / step) * step; gy <= top; gy += step) {
            shapes.setColor(isMajor(gy) ? GRID_MAJOR_COLOR : GRID_MINOR_COLOR);
            float y = (float) (gy - originY);
            shapes.line((float) (left - originX), y, (float) (right - originX), y);
        }
        shapes.end();
    }

    private static final Color GRID_MINOR_COLOR = new Color(0.08f, 0.1f, 0.16f, 1f);
    private static final Color GRID_MAJOR_COLOR = new Color(0.16f, 0.2f, 0.32f, 1f);

    private static boolean isMajor(double coordinate) {
        return Math.abs(Math.IEEEremainder(coordinate, GRID_MAJOR)) < 0.5;
    }

    private final Array<Body> bodies = new Array<>();
    private final float[] polygon = new float[16];

    private void drawBodies() {
        physics.getWorld().getBodies(bodies);
        shapes.begin(ShapeRenderer.ShapeType.Line);
        for (Body body : bodies) {
            shapes.setColor(body == playerBody ? Color.CYAN : Color.LIGHT_GRAY);
            for (Fixture fixture : body.getFixtureList()) {
                drawFixture(body, fixture.getShape());
            }
        }
        if (!thrusters.isOutOfFuel()) drawThrusterFlames();
        shapes.end();
        bodies.clear();
    }

    private static final Color HULL_TILE_COLOR = new Color(0.3f, 0.55f, 0.65f, 1f);
    private static final Color FLOOR_TILE_COLOR = new Color(0.12f, 0.25f, 0.3f, 1f);

    /** The ship's interior grid drawn in world space from the body's transform, when zoomed in. */
    private void drawInterior() {
        if (viewport.getWorldWidth() > 400f) return;
        Vector2 p = PhysicsManager.getPosition(playerBody, interiorPos);
        float cos = MathUtils.cos(playerBody.getAngle());
        float sin = MathUtils.sin(playerBody.getAngle());
        float half = shipGrid.tileSize * 0.5f * 0.92f;
        shapes.begin(ShapeRenderer.ShapeType.Line);
        for (int ty = 0; ty < shipGrid.height; ty++) {
            for (int tx = 0; tx < shipGrid.width; tx++) {
                Tile tile = shipGrid.getTile(tx, ty);
                if (tile.isVoid()) continue;
                shapes.setColor(tile.wallType == Tile.WallType.NONE ? FLOOR_TILE_COLOR : HULL_TILE_COLOR);
                Vector2 c = shipGrid.tileToLocal(tx, ty);
                for (int i = 0; i < 4; i++) {
                    float lx = c.x + (i == 1 || i == 2 ? half : -half);
                    float ly = c.y + (i >= 2 ? half : -half);
                    polygon[i * 2] = p.x + lx * cos - ly * sin;
                    polygon[i * 2 + 1] = p.y + lx * sin + ly * cos;
                }
                shapes.polygon(polygon, 0, 8);
            }
        }
        // Center of mass from the grid.
        Vector2 com = tmp.set(playerBody.getWorldCenter()).scl(PhysicsManager.METERS_PER_UNIT);
        shapes.setColor(Color.YELLOW);
        shapes.circle(com.x, com.y, 0.6f, 8);
        shapes.end();
    }

    /** Fast passes as a bright streak covering the last tenth of a second of travel. */
    private void drawFastPasses() {
        shapes.begin(ShapeRenderer.ShapeType.Line);
        for (Entity rock : fastPasses) {
            KinematicTransientComponent kt = rock.getComponent(KinematicTransientComponent.class);
            if (!kt.hasPrevious) continue;
            float speed = Math.max(kt.relativeSpeed, 1f);
            float tail = Math.min(0.1f * speed, 250f);
            shapes.setColor(Color.ORANGE);
            shapes.line(kt.x, kt.y, kt.x - kt.vx / speed * tail, kt.y - kt.vy / speed * tail);
            shapes.setColor(Color.YELLOW);
            shapes.circle(kt.x, kt.y, kt.radius, 12);
        }
        shapes.end();
    }

    /** In orbit, a short blue line from the ship toward the planet's center. */
    private void drawPlanetDirection() {
        if (!orbitMode) return;
        double ox = physics.getOriginX();
        double oy = physics.getOriginY();
        double r = Math.hypot(ox, oy);
        Vector2 p = PhysicsManager.getPosition(playerBody, tmp);
        float length = viewport.getWorldWidth() * 0.2f;
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(Color.SKY);
        shapes.line(p.x, p.y, p.x - (float) (ox / r) * length, p.y - (float) (oy / r) * length);
        shapes.end();
    }

    private void drawFixture(Body body, Shape shape) {
        if (shape instanceof CircleShape) {
            Vector2 c = PhysicsManager.getPosition(body, tmp);
            float r = shape.getRadius() * PhysicsManager.METERS_PER_UNIT;
            shapes.circle(c.x, c.y, r, 24);
            // A spoke so spin is visible.
            float a = body.getAngle();
            shapes.line(c.x, c.y, c.x + MathUtils.cos(a) * r, c.y + MathUtils.sin(a) * r);
        } else if (shape instanceof PolygonShape) {
            PolygonShape poly = (PolygonShape) shape;
            int n = poly.getVertexCount();
            for (int i = 0; i < n; i++) {
                poly.getVertex(i, tmp);
                Vector2 w = body.getWorldPoint(tmp);
                polygon[i * 2] = w.x * PhysicsManager.METERS_PER_UNIT;
                polygon[i * 2 + 1] = w.y * PhysicsManager.METERS_PER_UNIT;
            }
            shapes.polygon(polygon, 0, n * 2);
        }
    }

    private void drawThrusterFlames() {
        float flicker = MathUtils.random(0.8f, 1.2f);
        shapes.setColor(Color.ORANGE);
        if (controls.forward > 0f) flame(-10f, 0f, -1f, 0f, 14f * flicker);
        if (controls.forward < 0f) { flame(2f, 7f, 1f, 0f, 6f * flicker); flame(2f, -7f, 1f, 0f, 6f * flicker); }
        if (controls.strafe > 0f) flame(-2f, -6.5f, 0f, -1f, 6f * flicker);
        if (controls.strafe < 0f) flame(-2f, 6.5f, 0f, 1f, 6f * flicker);
        float spin = controls.turn;
        if (spin == 0f && controls.rotationAssist && Math.abs(playerBody.getAngularVelocity()) > 0.01f) {
            spin = -Math.signum(playerBody.getAngularVelocity());
        }
        if (spin > 0f) { flame(10f, -2f, 0f, -1f, 4f * flicker); flame(-9f, 5f, 0f, 1f, 4f * flicker); }
        if (spin < 0f) { flame(10f, 2f, 0f, 1f, 4f * flicker); flame(-9f, -5f, 0f, -1f, 4f * flicker); }
    }

    /** Draws a flame from a hull point (ship-local meters) pointing along a ship-local direction. */
    private void flame(float lx, float ly, float dx, float dy, float length) {
        float a = playerBody.getAngle();
        float cos = MathUtils.cos(a);
        float sin = MathUtils.sin(a);
        Vector2 p = PhysicsManager.getPosition(playerBody, tmp);
        float sx = p.x + lx * cos - ly * sin;
        float sy = p.y + lx * sin + ly * cos;
        float ex = sx + (dx * cos - dy * sin) * length;
        float ey = sy + (dx * sin + dy * cos) * length;
        shapes.line(sx, sy, ex, ey);
    }

    /** Where the ship will be in 5 seconds if nothing changes, relative to the grid. */
    private void drawVelocityVector() {
        PhysicsManager.getVelocity(playerBody, velocity);
        Vector2 p = PhysicsManager.getPosition(playerBody, tmp);
        float vx = (float) ((orbitMode ? frameVx : physics.getOriginVx()) + velocity.x);
        float vy = (float) ((orbitMode ? frameVy : physics.getOriginVy()) + velocity.y);
        // Draw relative to the grid (universe frame), which is what the eye reads as motion.
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(Color.LIME);
        shapes.line(p.x, p.y, p.x + vx * 5f, p.y + vy * 5f);
        shapes.end();
    }

    private void drawHud() {
        hudCamera.update();
        batch.setProjectionMatrix(hudCamera.combined);
        batch.begin();

        float x = 10f;
        float y = hudCamera.viewportHeight - 10f;
        float line = 18f;

        PhysicsManager.getVelocity(playerBody, velocity);
        double vx = physics.getOriginVx() + velocity.x;
        double vy = physics.getOriginVy() + velocity.y;
        Vector2 local = PhysicsManager.getPosition(playerBody, tmp);
        double ux = physics.getOriginX() + local.x;
        double uy = physics.getOriginY() + local.y;
        float heading = ((playerBody.getAngle() * MathUtils.radiansToDegrees) % 360f + 360f) % 360f;

        font.setColor(Color.WHITE);
        font.draw(batch, orbitMode ? "=== Newtonian Flight Demo: 400 km orbit ===" : "=== Newtonian Flight Demo ===", x, y);
        y -= line * 1.5f;
        font.setColor(Color.LIME);
        if (orbitMode) {
            font.draw(batch, fmt("Altitude   %8.3f km", (Math.hypot(ux, uy) - PLANET_RADIUS) / 1000.0), x, y); y -= line;
            font.draw(batch, fmt("Orbit spd  %8.1f m/s", Math.hypot(vx, vy)), x, y); y -= line;
            font.setColor(Color.WHITE);
            font.draw(batch, fmt("Grid speed %8.1f m/s", Math.hypot(frameVx + velocity.x, frameVy + velocity.y)), x, y); y -= line;
            Vector2 tidal = physics.getTidalField().accelAt(local.x, local.y, new Vector2());
            font.draw(batch, fmt("Tidal acc  %8.4f m/s^2", tidal.len()), x, y); y -= line;
        } else {
            font.draw(batch, fmt("Speed      %8.1f m/s", Math.hypot(vx, vy)), x, y); y -= line;
        }
        font.setColor(Color.WHITE);
        font.draw(batch, fmt("Velocity   %8.1f, %8.1f m/s", vx, vy), x, y); y -= line;
        font.draw(batch, fmt("Heading    %8.1f deg", heading), x, y); y -= line;
        font.draw(batch, fmt("Spin       %8.1f deg/s", playerBody.getAngularVelocity() * MathUtils.radiansToDegrees), x, y); y -= line;
        font.draw(batch, fmt("Fuel       %8.1f %%", 100f * thrusters.fuel / thrusters.fuelCapacity), x, y); y -= line;
        font.draw(batch, "Rot assist " + (controls.rotationAssist ? "      ON" : "     OFF"), x, y); y -= line;
        font.draw(batch, fmt("Mass       %8.1f t (from interior grid)", PhysicsManager.getMass(playerBody) / 1000f), x, y); y -= line;
        font.draw(batch, fmt("Time warp  %8dx", WARP_LEVELS[warpIndex]), x, y); y -= line * 1.5f;

        font.setColor(Color.LIGHT_GRAY);
        font.draw(batch, fmt(orbitMode ? "Planet-centered pos %12.1f, %12.1f m" : "Universe pos  %10.1f, %10.1f m", ux, uy), x, y); y -= line;
        font.draw(batch, fmt("Local pos     %10.1f, %10.1f m", local.x, local.y), x, y); y -= line;
        font.draw(batch, fmt("Origin rebases %d (at %.0f m or %.0f m/s)", floatingOrigin.getRebaseCount(),
                floatingOrigin.getRebaseDistance(), floatingOrigin.getRebaseSpeed()), x, y); y -= line * 1.5f;

        font.setColor(Color.GRAY);
        font.draw(batch, "[W/S] main / retro   [A/D] turn   [Q/E] strafe", x, y); y -= line;
        font.draw(batch, "[X] rotation assist   [wheel] zoom   [R] reset   [ESC] exit", x, y); y -= line;
        font.draw(batch, "[O] orbit mode   [,/.] time warp   [F] fast pass", x, y); y -= line;
        font.draw(batch, "Green line: where you will be in 5 s if you do nothing.", x, y); y -= line;
        if (orbitMode) font.draw(batch, "Blue line: toward the planet. Tidal gravity slowly pulls nearby rocks apart.", x, y);

        if (impactTimer > 0f) {
            font.setColor(Color.RED);
            font.draw(batch, fmt("%s  %.1f m/s", impactLabel, impactSpeed), hudCamera.viewportWidth / 2f - 50f, hudCamera.viewportHeight - 40f);
        }
        batch.end();
    }

    private static String fmt(String format, Object... args) {
        return String.format(Locale.ROOT, format, args);
    }

    // -----------------------------------------------------------------------
    @Override
    public void resize(int width, int height) {
        viewport.update(width, height);
        hudCamera.setToOrtho(false, width, height);
    }

    @Override
    public void dispose() {
        engine.removeAllEntities();
        physics.dispose();
        shapes.dispose();
        batch.dispose();
        font.dispose();
    }
}

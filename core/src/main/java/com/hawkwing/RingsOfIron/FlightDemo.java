package com.hawkwing.RingsOfIron;

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
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.hawkwing.RingsOfIron.art.ArtLibrary;
import com.hawkwing.RingsOfIron.art.DeckArt;
import com.hawkwing.RingsOfIron.game.FlightDemoScene;
import com.hawkwing.RingsOfIron.game.GameScenario;
import com.hawkwing.RingsOfIron.render.DeckRenderer;
import com.hawkwing.RingsOfIron.render.ExteriorRenderer;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.FlightControlComponent;
import com.ringsofiron.physicsexterior.components.ThrusterComponent;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * Newtonian flight demo: the game's corvette in the game's world ({@link FlightDemoScene}),
 * drifting under inertia among asteroids. Nothing slows the ship down: let go of the controls and
 * it keeps its speed and spin. Run with {@code gradlew :lwjgl3:runFlightDemo}.
 *
 * <p>Orbit mode puts the whole bubble in a 400 km orbit around the Earth. The bubble falls freely,
 * so flying feels the same, but tidal gravity slowly pulls neighbors apart: try it at 10x. Fast
 * passes are rocks crossing the bubble at km/s, too fast for Box2D, swept for hits instead.
 *
 * <p>W/S main engine and retro, A/D turn, Z/C strafe, X rotation assist (the game's keys); V deck
 * view; O orbit mode (restarts); , and . step the game's physics warp; F sends a fast pass past
 * the ship; mouse wheel zooms; R restarts; Esc quits.
 *
 * <p>Drawn: hulls, rocks and fast passes as the game draws them ({@link ExteriorRenderer}), and in
 * deck view the deck as the game draws it ({@link DeckRenderer}); under them the demo's own
 * reference grid, which slides past as the ship drifts, and over them a line to where the ship
 * will be in 5 s if nothing changes.
 */
public class FlightDemo extends ApplicationAdapter {
    private static final float MIN_VIEW = 30f;      // meters across the screen when zoomed in
    private static final float MAX_VIEW = 8000f;
    private static final float GRID_STEP = 100f;    // meters between faint grid lines
    private static final float GRID_MAJOR = 1000f;  // meters between bright grid lines
    private static final Color GRID_MINOR_COLOR = new Color(0.08f, 0.1f, 0.16f, 1f);
    private static final Color GRID_MAJOR_COLOR = new Color(0.16f, 0.2f, 0.32f, 1f);

    private final Path assetsDir;
    private FlightDemoScene demo;
    private boolean deckView;

    private OrthographicCamera camera;
    private ExtendViewport viewport;
    private OrthographicCamera hudCamera;
    private ShapeRenderer shapes;
    private SpriteBatch batch;
    private BitmapFont font;
    private DeckRenderer deckRenderer;
    private ExteriorRenderer exterior;
    private float viewMeters = 300f;

    private final Array<Entity> decks = new Array<>();
    private final Vector2 tmp = new Vector2();
    private final Vector2 velocity = new Vector2();

    public FlightDemo() {
        this(Paths.get("").toAbsolutePath());
    }

    public FlightDemo(Path assetsDir) {
        this.assetsDir = assetsDir;
    }

    @Override
    public void create() {
        camera = new OrthographicCamera();
        viewport = new ExtendViewport(viewMeters, viewMeters, camera);
        hudCamera = new OrthographicCamera();
        shapes = new ShapeRenderer();
        batch = new SpriteBatch();
        font = new BitmapFont();
        demo = new FlightDemoScene(assetsDir);
        demo.load(false);
        ArtLibrary art = ArtLibrary.load(ArtLibrary.DEFAULT_PATH);
        deckRenderer = new DeckRenderer(() -> demo.world, shapes, batch, new DeckArt(art), null, () -> null);
        exterior = new ExteriorRenderer(() -> demo.world, shapes, batch, art, null, null, () -> demo.player);

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
    }

    @Override
    public void render() {
        float delta = Math.min(Gdx.graphics.getDeltaTime(), 0.25f);
        handleInput();
        demo.update(delta);

        Vector2 p = PhysicsManager.getPosition(demo.body(), tmp);
        camera.position.set(p.x, p.y, 0f);
        camera.update();

        Gdx.gl.glClearColor(0.02f, 0.02f, 0.05f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        shapes.setProjectionMatrix(camera.combined);
        drawGrid();
        // As in the game: outside, the hulls with their art and every rock as its outline; on
        // deck, the deck plan, with only what is in flight (the fast passes) drawn over it
        float px = viewport.getWorldWidth() / 400f;
        if (deckView) {
            deckRenderer.draw(DeckRenderer.decksAround(demo.player, decks), camera, delta);
            exterior.drawFlight(camera, px, delta);
        } else {
            exterior.draw(camera, px, delta);
        }
        drawVelocityLine();
        drawPlanetDirection();
        drawHud();
    }

    private void handleInput() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) Gdx.app.exit();
        if (Gdx.input.isKeyJustPressed(Input.Keys.R)) demo.load(demo.orbitMode);
        if (Gdx.input.isKeyJustPressed(Input.Keys.O)) demo.load(!demo.orbitMode);
        if (Gdx.input.isKeyJustPressed(Input.Keys.V)) deckView = !deckView;
        if (Gdx.input.isKeyJustPressed(Input.Keys.X)) demo.toggleRotationAssist();
        if (Gdx.input.isKeyJustPressed(Input.Keys.PERIOD)) demo.stepWarp(1);
        if (Gdx.input.isKeyJustPressed(Input.Keys.COMMA)) demo.stepWarp(-1);
        if (Gdx.input.isKeyJustPressed(Input.Keys.F)) demo.spawnFastPass();

        FlightControlComponent controls = demo.controls();
        controls.clear();
        if (Gdx.input.isKeyPressed(Input.Keys.W)) controls.forward += 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.S)) controls.forward -= 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.A)) controls.turn += 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.D)) controls.turn -= 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.Z)) controls.strafe += 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.C)) controls.strafe -= 1f;
    }

    // -----------------------------------------------------------------------
    // Rendering
    // -----------------------------------------------------------------------

    /**
     * Grid that slides past as the ship drifts and never jumps on a rebase. Out of orbit it is fixed
     * in universe coordinates; in orbit the universe frame moves at km/s, so the grid instead falls
     * with the bubble (see {@link FlightDemoScene#frameX}).
     */
    private void drawGrid() {
        float halfW = viewport.getWorldWidth() / 2f;
        float halfH = viewport.getWorldHeight() / 2f;
        double originX = demo.gridOriginX();
        double originY = demo.gridOriginY();
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

    private static boolean isMajor(double coordinate) {
        return Math.abs(Math.IEEEremainder(coordinate, GRID_MAJOR)) < 0.5;
    }

    /** Where the ship will be in 5 seconds if nothing changes, in the grid's frame. */
    private void drawVelocityLine() {
        Vector2 v = demo.gridVelocity(velocity);
        Vector2 p = PhysicsManager.getPosition(demo.body(), tmp);
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(Color.LIME);
        shapes.line(p.x, p.y, p.x + v.x * 5f, p.y + v.y * 5f);
        shapes.end();
    }

    /** In orbit, a short blue line from the ship toward the planet's center. */
    private void drawPlanetDirection() {
        if (!demo.orbitMode) return;
        double ox = demo.world.physics.getOriginX();
        double oy = demo.world.physics.getOriginY();
        double r = Math.hypot(ox, oy);
        Vector2 p = PhysicsManager.getPosition(demo.body(), tmp);
        float length = viewport.getWorldWidth() * 0.2f;
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(Color.SKY);
        shapes.line(p.x, p.y, p.x - (float) (ox / r) * length, p.y - (float) (oy / r) * length);
        shapes.end();
    }

    private void drawHud() {
        hudCamera.update();
        batch.setProjectionMatrix(hudCamera.combined);
        batch.begin();
        float x = 10f;
        float y = hudCamera.viewportHeight - 10f;
        float line = 18f;

        Body body = demo.body();
        FlightControlComponent controls = demo.controls();
        ThrusterComponent thrusters = demo.thrusters();
        PhysicsManager.getVelocity(body, velocity);
        double vx = demo.world.physics.getOriginVx() + velocity.x;
        double vy = demo.world.physics.getOriginVy() + velocity.y;
        Vector2 local = PhysicsManager.getPosition(body, tmp);
        double ux = demo.world.physics.getOriginX() + local.x;
        double uy = demo.world.physics.getOriginY() + local.y;
        float heading = ((body.getAngle() * MathUtils.radiansToDegrees) % 360f + 360f) % 360f;

        font.setColor(Color.WHITE);
        font.draw(batch, demo.orbitMode ? "=== Newtonian Flight Demo: 400 km orbit ===" : "=== Newtonian Flight Demo ===", x, y);
        y -= line * 1.5f;
        font.setColor(Color.LIME);
        if (demo.orbitMode) {
            font.draw(batch, fmt("Altitude   %8.3f km", (Math.hypot(ux, uy) - GameScenario.R_EARTH) / 1000.0), x, y); y -= line;
            font.draw(batch, fmt("Orbit spd  %8.1f m/s", Math.hypot(vx, vy)), x, y); y -= line;
            font.setColor(Color.WHITE);
            font.draw(batch, fmt("Grid speed %8.1f m/s", demo.gridVelocity(new Vector2()).len()), x, y); y -= line;
            Vector2 tidal = demo.world.physics.getTidalField().accelAt(local.x, local.y, new Vector2());
            font.draw(batch, fmt("Tidal acc  %8.4f m/s^2", tidal.len()), x, y); y -= line;
        } else {
            font.draw(batch, fmt("Speed      %8.1f m/s", Math.hypot(vx, vy)), x, y); y -= line;
        }
        font.setColor(Color.WHITE);
        font.draw(batch, fmt("Velocity   %8.1f, %8.1f m/s", vx, vy), x, y); y -= line;
        font.draw(batch, fmt("Heading    %8.1f deg", heading), x, y); y -= line;
        font.draw(batch, fmt("Spin       %8.1f deg/s", body.getAngularVelocity() * MathUtils.radiansToDegrees), x, y); y -= line;
        if (thrusters != null && thrusters.fuelCapacity > 0f) {
            font.draw(batch, fmt("Fuel       %8.1f %%", 100f * thrusters.fuel / thrusters.fuelCapacity), x, y); y -= line;
        }
        font.draw(batch, "Rot assist " + (controls.rotationAssist ? "      ON" : "     OFF"), x, y); y -= line;
        font.draw(batch, fmt("Mass       %8.1f t", PhysicsManager.getMass(body) / 1000f), x, y); y -= line;
        font.draw(batch, fmt("Time warp  %8dx", demo.warp()), x, y); y -= line * 1.5f;

        font.setColor(Color.LIGHT_GRAY);
        font.draw(batch, fmt(demo.orbitMode ? "Planet-centered pos %12.1f, %12.1f m" : "Universe pos  %10.1f, %10.1f m", ux, uy), x, y); y -= line;
        font.draw(batch, fmt("Local pos     %10.1f, %10.1f m", local.x, local.y), x, y); y -= line * 1.5f;

        font.setColor(Color.GRAY);
        font.draw(batch, "[W/S] main / retro   [A/D] turn   [Z/C] strafe   [X] rotation assist", x, y); y -= line;
        font.draw(batch, "[V] deck view   [wheel] zoom   [R] reset   [ESC] exit", x, y); y -= line;
        font.draw(batch, "[O] orbit mode   [,/.] time warp   [F] fast pass", x, y); y -= line;
        font.draw(batch, "Green line: where you will be in 5 s if you do nothing.", x, y); y -= line;
        if (demo.orbitMode) font.draw(batch, "Blue line: toward the planet. Tidal gravity slowly pulls nearby rocks apart.", x, y);

        if (demo.impact() != null) {
            font.setColor(Color.RED);
            font.draw(batch, demo.impact(), hudCamera.viewportWidth / 2f - 50f, hudCamera.viewportHeight - 40f);
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
        demo.dispose();
        shapes.dispose();
        batch.dispose();
        font.dispose();
    }
}

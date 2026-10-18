package com.hawkwing.RingsOfIron;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.Shape;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.coreengine.components.PlayerControllerComponent;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.interiorgrid.ChassisBlueprint;
import com.ringsofiron.interiorgrid.ChassisBlueprintLoader;
import com.ringsofiron.interiorgrid.CompartmentBuilder;
import com.ringsofiron.interiorgrid.FloorCovering;
import com.ringsofiron.interiorgrid.GridHazardSystem;
import com.ringsofiron.interiorgrid.PowerRoutingSystem;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.physicsexterior.CollisionCategories;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.FlightControlComponent;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.physicsexterior.components.ThrusterComponent;
import com.ringsofiron.physicsexterior.systems.FlightControlSystem;
import com.ringsofiron.physicsexterior.systems.FloatingOriginSystem;
import com.ringsofiron.physicsexterior.systems.PhysicsUpdateSystem;
import com.ringsofiron.uihud.UiHud;
import com.ringsofiron.uihud.ViewMode;
import com.ringsofiron.uihud.ViewportManager;
import com.ringsofiron.uihud.components.HudMarkerComponent;
import com.ringsofiron.uihud.components.InteractableConsoleComponent;
import com.ringsofiron.uihud.minimap.TileShade;
import com.ringsofiron.uihud.providers.InteractorProvider;
import com.ringsofiron.uihud.providers.SimpleTargetingProvider;

/**
 * Interactive demo for the ui-hud module: the flight demo's ship, now with its interior grid,
 * a few contacts to mark, and the piloting HUD on top.
 *
 * Controls:
 *   W / S        - Main engine / retro thrusters
 *   A / D        - Turn left / right
 *   Z / C        - Strafe left / right
 *   X            - Toggle rotation assist
 *   Tab          - Cycle target lock (lead reticle shows where to aim)
 *   V            - Toggle exterior / interior view
 *   I J K L      - Walk your crew avatar around the deck
 *   E            - Use the console you are standing at (Esc closes it)
 *   F / B / H    - Start a fire / breach the hull / take a hit (watch the minimap)
 *   Mouse wheel  - Zoom
 *   R            - Reset
 *   ESC          - Exit (when no console is open)
 */
public class HudDemo extends ApplicationAdapter {

    // Interior deck plan. Text rows read top-to-bottom; the grid stores them bottom-left first.
    private static final String[] DECK = {
        "######################",
        "#....................#",
        "#.R+++++++++++++++HH.#",
        "#.+..........+.......#",
        "#.+..........+.......#",
        "#.++++++WW...++++LL..#",
        "#............+.......#",
        "######################",
    };
    private static final float TILE = 1f;
    private static final float GRID_ORIGIN_X = -11f;
    private static final float GRID_ORIGIN_Y = -4f;
    // Hull outline around the deck, nose along +X, meters, counter-clockwise.
    private static final float[] SHIP_HULL = {15f, 0f, 11f, 4.4f, -11.4f, 4.4f, -11.4f, -4.4f, 11f, -4.4f};
    private static final float SHIP_DENSITY = 40f;
    private static final float AVATAR_SPEED = 4f;       // tiles per second
    private static final float PROJECTILE_SPEED = 900f; // m/s, stand-in until combat-sys supplies weapons
    private static final float INTERIOR_VIEW = 40f;     // meters across in the interior view

    private static final ComponentMapper<TransformComponent> TRANSFORMS = ComponentMapper.getFor(TransformComponent.class);
    private static final ComponentMapper<HudMarkerComponent> MARKERS = ComponentMapper.getFor(HudMarkerComponent.class);
    private static final ComponentMapper<SubsystemComponent> SUBSYSTEMS = ComponentMapper.getFor(SubsystemComponent.class);

    private PhysicsManager physics;
    private Engine engine;
    private Entity player;
    private Body playerBody;
    private FlightControlComponent controls;
    private ShipGridComponent grid;
    private SimpleTargetingProvider targeting;
    private final Array<Entity> targetable = new Array<>();
    private final DemoAvatar avatar = new DemoAvatar();

    private UiHud hud;
    private ViewportManager viewports;
    private ShapeRenderer shapes;
    private float exteriorViewMeters = ViewportManager.DEFAULT_VIEW_METERS;

    private final Vector2 tmp = new Vector2();
    private final Color color = new Color();
    private final Array<Body> bodies = new Array<>();
    private final float[] polygon = new float[16];

    @Override
    public void create() {
        shapes = new ShapeRenderer();
        hud = new UiHud();
        viewports = hud.getViewports();
        viewports.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        hud.getViewMode().addListener((previous, current) -> {
            if (previous == current) return;
            if (current == ViewMode.INTERIOR) {
                exteriorViewMeters = viewports.getViewMeters();
                viewports.setViewMeters(INTERIOR_VIEW);
            } else {
                viewports.setViewMeters(exteriorViewMeters);
            }
        });

        InputMultiplexer input = new InputMultiplexer(hud.getInputProcessor(), new InputAdapter() {
            @Override
            public boolean scrolled(float amountX, float amountY) {
                viewports.zoom((float) Math.pow(1.15f, amountY));
                return true;
            }
        });
        Gdx.input.setInputProcessor(input);

        buildScene();
    }

    private void buildScene() {
        if (physics != null) {
            hud.getConsoleWindow().close();
            hud.removeFrom(engine);
            engine.removeAllEntities();
            physics.dispose();
        }
        physics = new PhysicsManager();
        engine = new Engine();
        engine.addSystem(new FlightControlSystem(physics));
        engine.addSystem(new PhysicsUpdateSystem(physics));
        engine.addSystem(new FloatingOriginSystem(physics));
        engine.addSystem(new GridHazardSystem());
        engine.addSystem(new PowerRoutingSystem());

        spawnPlayer();
        targetable.clear();
        targetable.add(spawnShip("Raider", HudMarkerComponent.Kind.HOSTILE, 900f, -350f, -22f, 38f));
        targetable.add(spawnShip("Raider Wing", HudMarkerComponent.Kind.HOSTILE, -1400f, 1100f, 45f, -10f));
        targetable.add(spawnShip("Escort", HudMarkerComponent.Kind.FRIENDLY, -60f, 70f, 25f, 8f));
        spawnFixedMarker("Haven Station", HudMarkerComponent.Kind.STATION, 2600f, -1500f, 80f);
        spawnFixedMarker("Nav Point Alpha", HudMarkerComponent.Kind.WAYPOINT, -4200f, 3100f, 5f);
        MathUtils.random.setSeed(11);
        for (int i = 0; i < 25; i++) spawnAsteroid();

        targeting = new SimpleTargetingProvider(player, PROJECTILE_SPEED);
        targeting.setTarget(targetable.first());

        hud.addTo(engine);
        hud.setPhysics(physics);
        hud.setTargeting(targeting);
        hud.setInteractor(avatar);
        hud.getViewMode().setMode(ViewMode.EXTERIOR);
        avatar.reset();
    }

    private void spawnPlayer() {
        player = new Entity();
        player.add(new TransformComponent());
        player.add(new PlayerControllerComponent());
        playerBody = physics.createPolygonBody(player, 0f, 0f, 0f, SHIP_HULL, SHIP_DENSITY,
                CollisionCategories.CATEGORY_SHIP, CollisionCategories.MASK_ALL);
        player.add(new PhysicsBodyComponent(playerBody));
        float mass = PhysicsManager.getMass(playerBody);
        float inertia = PhysicsManager.getInertia(playerBody);
        player.add(new ThrusterComponent(mass * 20f, mass * 8f, mass * 6f, inertia * 2.5f));
        controls = new FlightControlComponent();
        controls.rotationAssist = true;
        player.add(controls);

        ChassisBlueprint blueprint = new ChassisBlueprint(DECK).tileSize(TILE).origin(GRID_ORIGIN_X, GRID_ORIGIN_Y);
        blueprint.covering(FloorCovering.CARPET, 1, 1, DECK[0].length() - 2, DECK.length - 2);
        ChassisBlueprintLoader.LoadResult deck = ChassisBlueprintLoader.load(blueprint);
        grid = deck.grid;
        CompartmentBuilder.rebuildIfChanged(grid);
        player.add(grid);
        ShipSubsystemRegistryComponent registry = new ShipSubsystemRegistryComponent(player);
        player.add(registry);
        engine.addEntity(player);

        for (ChassisBlueprintLoader.SubsystemPlacement placement : deck.subsystems) {
            Entity system = new Entity();
            SubsystemComponent sub = new SubsystemComponent(placement.footprint, placement.type);
            system.add(sub);
            placement.footprint.installed = system;
            String title = placement.type.name().replace('_', ' ');
            title = title.charAt(0) + title.substring(1).toLowerCase();
            system.add(new InteractableConsoleComponent(placement.type.name().toLowerCase(), title, player,
                    placement.footprint.originX, placement.footprint.originY));
            engine.addEntity(system);
            registry.registerSubsystem(system);
        }
    }

    private Entity spawnShip(String name, HudMarkerComponent.Kind kind, float x, float y, float vx, float vy) {
        Entity ship = new Entity();
        ship.add(new TransformComponent());
        Body body = physics.createPolygonBody(ship, x, y, MathUtils.atan2(vy, vx),
                new float[]{9f, 0f, -6f, 5f, -6f, -5f}, SHIP_DENSITY,
                CollisionCategories.CATEGORY_SHIP, CollisionCategories.MASK_ALL);
        PhysicsManager.setVelocity(body, vx, vy);
        ship.add(new PhysicsBodyComponent(body));
        ship.add(new HudMarkerComponent(name, kind).radius(9f));
        engine.addEntity(ship);
        return ship;
    }

    /** A marker on a point fixed in the universe (bodyless transforms are held still by physics). */
    private void spawnFixedMarker(String name, HudMarkerComponent.Kind kind, float x, float y, float radius) {
        Entity e = new Entity();
        e.add(new TransformComponent(x, y, 0f));
        e.add(new HudMarkerComponent(name, kind).radius(radius));
        engine.addEntity(e);
    }

    private void spawnAsteroid() {
        float x, y;
        do {
            x = MathUtils.random(-2500f, 2500f);
            y = MathUtils.random(-2500f, 2500f);
        } while (Vector2.len(x, y) < 200f);
        Entity rock = new Entity();
        rock.add(new TransformComponent());
        Body body = physics.createCircleBody(rock, x, y, MathUtils.random(6f, 30f), 200f,
                CollisionCategories.CATEGORY_ASTEROID, CollisionCategories.MASK_ALL);
        PhysicsManager.setVelocity(body, MathUtils.random(-5f, 5f), MathUtils.random(-5f, 5f));
        rock.add(new PhysicsBodyComponent(body));
        engine.addEntity(rock);
    }

    // -----------------------------------------------------------------------
    @Override
    public void render() {
        float delta = Math.min(Gdx.graphics.getDeltaTime(), 0.25f);
        handleInput(delta);

        // Physics and game logic first, then the camera, then the HUD systems read both.
        TransformComponent pt = TRANSFORMS.get(player);
        engine.update(delta);
        viewports.follow(pt.x, pt.y);

        Gdx.gl.glClearColor(0.02f, 0.02f, 0.05f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        viewports.applyWorld();
        OrthographicCamera cam = viewports.getWorldCamera();
        shapes.setProjectionMatrix(cam.combined);
        if (hud.getViewMode().getMode() == ViewMode.INTERIOR) {
            drawInterior();
        } else {
            drawBodies();
        }
        hud.draw(delta);
    }

    private void handleInput(float delta) {
        boolean consoleOpen = hud.getConsoleWindow().isOpen();
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) && !consoleOpen) Gdx.app.exit();
        if (Gdx.input.isKeyJustPressed(Input.Keys.R)) {
            buildScene();
            return;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.X)) controls.rotationAssist = !controls.rotationAssist;
        if (Gdx.input.isKeyJustPressed(Input.Keys.TAB)) cycleTarget();
        if (Gdx.input.isKeyJustPressed(Input.Keys.F)) startFire();
        if (Gdx.input.isKeyJustPressed(Input.Keys.B)) breachHull();
        if (Gdx.input.isKeyJustPressed(Input.Keys.H)) takeHit();

        controls.clear();
        if (Gdx.input.isKeyPressed(Input.Keys.W)) controls.forward += 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.S)) controls.forward -= 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.A)) controls.turn += 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.D)) controls.turn -= 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.Z)) controls.strafe += 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.C)) controls.strafe -= 1f;

        float ax = 0f, ay = 0f;
        if (Gdx.input.isKeyPressed(Input.Keys.J)) ax -= 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.L)) ax += 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.K)) ay -= 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.I)) ay += 1f;
        if (!consoleOpen) avatar.walk(ax * AVATAR_SPEED * delta, ay * AVATAR_SPEED * delta);
    }

    private void cycleTarget() {
        Entity current = targeting.getLockedTarget();
        int i = targetable.indexOf(current, true);
        // Cycle through every contact, then "no target".
        int next = i + 1;
        targeting.setTarget(next < targetable.size ? targetable.get(next) : null);
    }

    private void startFire() {
        Array<Tile> candidates = new Array<>();
        for (int y = 0; y < grid.height; y++)
            for (int x = 0; x < grid.width; x++)
                if (grid.getTile(x, y).isFlammable() && !grid.getTile(x, y).isBurning()) candidates.add(grid.getTile(x, y));
        if (candidates.size == 0) return;
        Tile t = candidates.random();
        t.ignite();
        t.fireIntensity = 0.3f;
    }

    private void breachHull() {
        Array<int[]> candidates = new Array<>();
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                Tile t = grid.getTile(x, y);
                if (t.wallType == Tile.WallType.HULL && !t.isHullBreach() && touchesFloor(x, y)) candidates.add(new int[]{x, y});
            }
        }
        if (candidates.size == 0) return;
        int[] p = candidates.random();
        grid.getTile(p[0], p[1]).setIntegrity(0f);
        grid.gridDirty = true;
        hud.flashHullHit(p[0], p[1]);
    }

    private void takeHit() {
        int x = MathUtils.random(grid.width - 1);
        int y = MathUtils.random(grid.height - 1);
        Tile t = grid.getTile(x, y);
        if (t.isWall()) {
            t.setIntegrity(t.getIntegrity() - 0.35f);
            grid.gridDirty = true;
        } else if (MathUtils.randomBoolean(0.4f)) {
            t.ignite();
        }
        Entity sys = grid.subsystemAt(x, y);
        if (sys != null) {
            SubsystemComponent s = SUBSYSTEMS.get(sys);
            s.health = Math.max(0f, s.health - 0.4f);
            grid.gridDirty = true;
        }
        hud.flashHullHit(x, y);
    }

    private boolean touchesFloor(int x, int y) {
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            Tile n = grid.getTile(x + d[0], y + d[1]);
            if (n != null && n.isFloor()) return true;
        }
        return false;
    }

    // -----------------------------------------------------------------------
    private void drawBodies() {
        physics.getWorld().getBodies(bodies);
        shapes.begin(ShapeRenderer.ShapeType.Line);
        for (Body body : bodies) {
            Object owner = body.getUserData();
            HudMarkerComponent marker = owner instanceof Entity ? MARKERS.get((Entity) owner) : null;
            if (body == playerBody) shapes.setColor(Color.CYAN);
            else if (marker != null) shapes.setColor(marker.color);
            else shapes.setColor(Color.LIGHT_GRAY);
            for (Fixture fixture : body.getFixtureList()) drawFixture(body, fixture.getShape());
        }
        shapes.end();
        bodies.clear();
    }

    private void drawFixture(Body body, Shape shape) {
        if (shape instanceof CircleShape) {
            Vector2 c = PhysicsManager.getPosition(body, tmp);
            shapes.circle(c.x, c.y, shape.getRadius() * PhysicsManager.METERS_PER_UNIT, 24);
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

    /** The deck plan drawn at the ship's pose, using the same colors as the minimap. */
    private void drawInterior() {
        TransformComponent pt = TRANSFORMS.get(player);
        shapes.identity();
        shapes.translate(pt.x, pt.y, 0f);
        shapes.rotate(0f, 0f, 1f, pt.rotation);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        boolean blink = hud.getMinimap().blinkOn();
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                TileShade.fill(grid.getTile(x, y), blink, color);
                if (color.a <= 0f) continue;
                shapes.setColor(color);
                Vector2 p = grid.tileToLocal(x, y);
                shapes.rect(p.x - TILE / 2f + 0.03f, p.y - TILE / 2f + 0.03f, TILE - 0.06f, TILE - 0.06f);
            }
        }
        // Avatar
        shapes.setColor(Color.WHITE);
        shapes.circle(grid.gridOriginLocal.x + avatar.x * TILE, grid.gridOriginLocal.y + avatar.y * TILE, 0.3f, 12);
        shapes.end();
        shapes.identity();
    }

    // -----------------------------------------------------------------------
    @Override
    public void resize(int width, int height) {
        hud.resize(width, height);
    }

    @Override
    public void dispose() {
        engine.removeAllEntities();
        physics.dispose();
        shapes.dispose();
        hud.dispose();
    }

    /**
     * Stand-in for ai-crew's player avatar: a point that walks over open tiles of the player
     * ship's grid. Implements the HUD's {@link InteractorProvider}.
     */
    private class DemoAvatar implements InteractorProvider {
        float x, y;

        void reset() {
            // Start beside the helm.
            x = 17.5f;
            y = 4.5f;
        }

        void walk(float dx, float dy) {
            if (canStand(x + dx, y)) x += dx;
            if (canStand(x, y + dy)) y += dy;
        }

        private boolean canStand(float tx, float ty) {
            Tile t = grid.getTile(MathUtils.floor(tx), MathUtils.floor(ty));
            return t != null && t.isOpen();
        }

        @Override
        public Entity getAvatarShip() {
            return player;
        }

        @Override
        public boolean getAvatarTile(Vector2 out) {
            out.set(x, y);
            return true;
        }
    }
}

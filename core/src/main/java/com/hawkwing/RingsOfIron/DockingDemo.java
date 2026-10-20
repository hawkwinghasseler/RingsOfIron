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
import com.hawkwing.RingsOfIron.art.ArtLibrary;
import com.hawkwing.RingsOfIron.art.DeckArt;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.ashley.core.Family;
import com.hawkwing.RingsOfIron.game.DeckView;
import com.hawkwing.RingsOfIron.game.DockingDemoScene;
import com.hawkwing.RingsOfIron.game.DoorForcing;
import com.hawkwing.RingsOfIron.game.PlayerDocking;
import com.hawkwing.RingsOfIron.game.StationCrew;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.aicrew.PlayerAvatarComponent;
import com.ringsofiron.interiorgrid.CargoHoldComponent;
import com.ringsofiron.interiorgrid.DockingPortLayout;
import com.ringsofiron.interiorgrid.EdgeWall;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Side;
import com.ringsofiron.physicsexterior.CollisionCategories;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.PolygonSkin;
import com.ringsofiron.physicsexterior.components.FlightControlComponent;
import com.ringsofiron.stationsdocking.DockingPort;
import com.ringsofiron.stationsdocking.DockingPortsComponent;
import com.ringsofiron.stationsdocking.ForceMethod;
import com.ringsofiron.uihud.components.HudMarkerComponent;
import com.ringsofiron.uihud.minimap.TileShade;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Docking demo: the game's stations-docking module on its own, one feature per scene, with the
 * game's own corvette, stations and keys. Run with {@code gradlew :lwjgl3:runDockingDemo}, or
 * {@code demos/Docking demo.bat} on Windows.
 *
 * <p>1-9 and 0 pick a scene, R restarts it. W/S thrust, A/D turn, Z/C strafe. G arms your docking port
 * and asks a station for clearance (again to disarm), H picks the next port, U undocks, P flies
 * the approach for you until the magnets or the collar take over. V switches to deck view, where
 * WASD walks your crew avatar (through an open port onto whatever you are docked with) and the
 * view follows it (W is up the screen aboard either craft). In the loading scene L orders water
 * carried aboard and K sends it back. In deck view, standing in your port's opening, F pries or
 * hacks the shut door across the dock and B cuts it; in the forcing scene O switches the
 * outpost's reactor off and on. In the hub scene a hauler flies itself in to dock and another
 * flies off. In the yard scene J orders repairs, N refits the nose hardpoint
 * with a new weapon and M with a used one. Mouse wheel zooms, Esc quits.
 *
 * <p>Drawn: hulls and deck plans, docking collars (yellow), port faces (green while armed, cyan
 * latched, red torn off), the collar head of an active port while it is out (orange), port doors
 * (green open, red shut), mounted systems (grey blocks: crew walk over them), cargo (a crate
 * per bay tile, colored by commodity) and crew (white, station hands orange, you in yellow; a
 * crate on anyone carrying a load).
 */
public class DockingDemo extends ApplicationAdapter {
    private static final float PANEL_WIDTH = 430f;
    /** Walking pace in deck view, tiles per second (as in the game). */
    private static final float WALK_SPEED = 1f;
    private static final float DECK_ZOOM = 0.3f;

    private static final Family CREW = Family.all(CrewMemberComponent.class, GridPositionComponent.class).get();

    private final Path assetsDir;
    private DockingDemoScene demo;
    private OrthographicCamera camera;
    private OrthographicCamera ui;
    private ShapeRenderer shapes;
    private SpriteBatch batch;
    private ArtLibrary art;
    private DeckArt deckArt;
    private final com.badlogic.gdx.math.Matrix4 pose = new com.badlogic.gdx.math.Matrix4();
    private BitmapFont font;
    private float viewMeters = 90f;

    private final Array<Body> bodies = new Array<>();
    private final Vector2 tmp = new Vector2();
    private final Color color = new Color();
    private final float[] polygon = new float[16];
    private final DoorForcing.Target target = new DoorForcing.Target();

    public DockingDemo() {
        this(Paths.get("").toAbsolutePath());
    }

    public DockingDemo(Path assetsDir) {
        this.assetsDir = assetsDir;
    }

    @Override
    public void create() {
        shapes = new ShapeRenderer();
        batch = new SpriteBatch();
        font = new BitmapFont();
        art = ArtLibrary.load(ArtLibrary.DEFAULT_PATH);
        deckArt = new DeckArt(art);
        camera = new OrthographicCamera();
        ui = new OrthographicCamera();
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        demo = new DockingDemoScene(assetsDir);
        demo.load(DockingDemoScene.Scene.PASSIVE);
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean scrolled(float amountX, float amountY) {
                viewMeters = Math.max(15f, Math.min(600f, viewMeters * (float) Math.pow(1.15f, amountY)));
                resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
                return true;
            }
        });
    }

    @Override
    public void render() {
        float delta = Math.min(Gdx.graphics.getDeltaTime(), 0.25f);
        handleInput();
        demo.update(delta);

        Body ship = demo.world.docking.bodyOf(demo.player);
        Vector2 walker = demo.deckView ? demo.avatarWorld(tmp) : null;
        if (walker != null) {
            camera.position.set(walker.x, walker.y, 0f);
        } else if (ship != null) {
            Vector2 p = PhysicsManager.getPosition(ship, tmp);
            camera.position.set(p.x, p.y, 0f);
        }
        // Closer in on deck, to see the avatar walk
        camera.zoom = walker != null ? DECK_ZOOM : 1f;
        camera.update();
        Gdx.gl.glClearColor(0.02f, 0.02f, 0.05f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        shapes.setProjectionMatrix(camera.combined);
        drawDecks();
        drawBodies();
        drawPorts();
        drawDoorsAndCrew();
        drawPanel();
    }

    private void handleInput() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) Gdx.app.exit();
        DockingDemoScene.Scene[] scenes = DockingDemoScene.Scene.values();
        for (int i = 0; i < scenes.length; i++) {
            // 1-9, then 0 for the tenth
            int key = i < 9 ? Input.Keys.NUM_1 + i : i == 9 ? Input.Keys.NUM_0 : -1;
            if (key >= 0 && Gdx.input.isKeyJustPressed(key)) demo.load(scenes[i]);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.R)) demo.load(demo.scene);
        if (Gdx.input.isKeyJustPressed(Input.Keys.V)) demo.deckView = !demo.deckView;

        FlightControlComponent controls = demo.player.getComponent(FlightControlComponent.class);
        PlayerAvatarComponent walker = demo.avatar.getComponent(PlayerAvatarComponent.class);
        walker.moveX = 0f;
        walker.moveY = 0f;
        if (controls != null) {
            controls.forward = 0f;
            controls.turn = 0f;
            controls.strafe = 0f;
        }
        if (demo.deckView) {
            // Up the screen is W whichever way the craft the avatar is aboard is turned
            float sx = 0f, sy = 0f;
            if (Gdx.input.isKeyPressed(Input.Keys.W)) sy += 1f;
            if (Gdx.input.isKeyPressed(Input.Keys.S)) sy -= 1f;
            if (Gdx.input.isKeyPressed(Input.Keys.A)) sx -= 1f;
            if (Gdx.input.isKeyPressed(Input.Keys.D)) sx += 1f;
            Entity aboard = demo.aboard();
            DeckView.walk(walker, aboard == null ? null : demo.world.docking.bodyOf(aboard), sx, sy, WALK_SPEED);
        } else if (controls != null) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.X)) controls.rotationAssist = !controls.rotationAssist;
            if (Gdx.input.isKeyPressed(Input.Keys.W)) controls.forward += 1f;
            if (Gdx.input.isKeyPressed(Input.Keys.S)) controls.forward -= 1f;
            if (Gdx.input.isKeyPressed(Input.Keys.A)) controls.turn += 1f;
            if (Gdx.input.isKeyPressed(Input.Keys.D)) controls.turn -= 1f;
            if (Gdx.input.isKeyPressed(Input.Keys.Z)) controls.strafe += 1f;
            if (Gdx.input.isKeyPressed(Input.Keys.C)) controls.strafe -= 1f;
            // Taking the stick back from the autopilot
            if (controls.forward != 0f || controls.turn != 0f || controls.strafe != 0f) demo.autopilot = false;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.G)) demo.docking.toggleArm();
        if (Gdx.input.isKeyJustPressed(Input.Keys.H)) demo.docking.nextPort();
        if (Gdx.input.isKeyJustPressed(Input.Keys.U)) demo.docking.undock();
        if (Gdx.input.isKeyJustPressed(Input.Keys.P)) demo.autopilot = !demo.autopilot;
        if (demo.deckView) {
            // Force the door across the dock: pry it or hack it as its power allows, or cut it
            if (Gdx.input.isKeyJustPressed(Input.Keys.F)) {
                DoorForcing.Target t = demo.world.forcing.target(demo.avatar, target);
                demo.force(t.state == ForceMethod.DoorState.UNPOWERED ? ForceMethod.PRY : ForceMethod.HACK);
            }
            if (Gdx.input.isKeyJustPressed(Input.Keys.B)) demo.force(ForceMethod.CUT);
        }
        if (demo.scene == DockingDemoScene.Scene.FORCE && Gdx.input.isKeyJustPressed(Input.Keys.O)) demo.toggleStationPower();
        if (demo.scene == DockingDemoScene.Scene.LOADING) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.L)) demo.orderWater(true);
            if (Gdx.input.isKeyJustPressed(Input.Keys.K)) demo.orderWater(false);
        }
        if (demo.scene == DockingDemoScene.Scene.YARD) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.J)) demo.orderRepairs();
            if (Gdx.input.isKeyJustPressed(Input.Keys.N)) demo.refitNose(false);
            if (Gdx.input.isKeyJustPressed(Input.Keys.M)) demo.refitNose(true);
        }
    }

    /**
     * Each grid's deck plan under its hull: the art pack's deck when it is built, else the
     * minimap's colors, faintly.
     */
    private void drawDecks() {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        demo.world.physics.getWorld().getBodies(bodies);
        if (deckArt.available()) {
            batch.setProjectionMatrix(camera.combined);
            for (Body body : bodies) {
                Entity e = body.getUserData() instanceof Entity ? (Entity) body.getUserData() : null;
                ShipGridComponent grid = e == null ? null : e.getComponent(ShipGridComponent.class);
                if (grid == null || demo.world.docking.bodyOf(e) != body) continue;
                Vector2 at = PhysicsManager.getPosition(body, new Vector2());
                batch.setTransformMatrix(pose.idt().translate(at.x, at.y, 0f)
                        .rotate(0f, 0f, 1f, body.getAngle() * com.badlogic.gdx.math.MathUtils.radiansToDegrees));
                batch.begin();
                deckArt.draw(batch, grid, e.getComponent(CargoHoldComponent.class));
                batch.end();
            }
            batch.setTransformMatrix(pose.idt());
        }
        // The batch's end turned blending off; the walkways drawn below are translucent
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (Body body : bodies) {
            Entity e = body.getUserData() instanceof Entity ? (Entity) body.getUserData() : null;
            ShipGridComponent grid = e == null ? null : e.getComponent(ShipGridComponent.class);
            if (grid == null || demo.world.docking.bodyOf(e) != body) continue;
            Vector2 at = PhysicsManager.getPosition(body, new Vector2());
            shapes.identity();
            shapes.translate(at.x, at.y, 0f);
            shapes.rotate(0f, 0f, 1f, body.getAngle() * com.badlogic.gdx.math.MathUtils.radiansToDegrees);
            if (deckArt.available()) {
                if (!deckArt.hasWalkways()) DeckView.walkways(shapes, grid, color, 0.6f);
                continue;
            }
            float s = grid.tileSize;
            for (int y = 0; y < grid.height; y++) {
                for (int x = 0; x < grid.width; x++) {
                    TileShade.fill(grid.getTile(x, y), false, color);
                    if (color.a <= 0f) continue;
                    color.a *= 0.45f;
                    shapes.setColor(color);
                    Vector2 p = grid.tileToLocal(x, y);
                    shapes.rect(p.x - s / 2f + 0.05f, p.y - s / 2f + 0.05f, s - 0.1f, s - 0.1f);
                }
            }
            DeckView.walkways(shapes, grid, color, 0.45f);
            DeckView.walls(shapes, grid, color);
            DeckView.cargo(shapes, grid, e.getComponent(CargoHoldComponent.class), color);
        }
        bodies.clear();
        shapes.end();
        shapes.identity();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    private void drawBodies() {
        demo.world.physics.getWorld().getBodies(bodies);
        shapes.begin(ShapeRenderer.ShapeType.Line);
        for (Body body : bodies) {
            Entity e = body.getUserData() instanceof Entity ? (Entity) body.getUserData() : null;
            boolean head = e != null && demo.world.docking.bodyOf(e) != body;
            HudMarkerComponent marker = e == null ? null : e.getComponent(HudMarkerComponent.class);
            for (Fixture f : body.getFixtureList()) {
                boolean collar = (f.getFilterData().categoryBits & CollisionCategories.CATEGORY_COLLAR) != 0;
                if (head) shapes.setColor(Color.ORANGE);
                else if (collar) shapes.setColor(Color.YELLOW);
                else if (e == demo.player) shapes.setColor(Color.SKY);
                else if (marker != null) shapes.setColor(marker.color);
                else shapes.setColor(Color.LIGHT_GRAY);
                drawFixture(body, f);
            }
        }
        bodies.clear();
        shapes.end();
    }

    /** A tick on each port face: green while armed, cyan latched, red torn off, grey otherwise. */
    private void drawPorts() {
        shapes.begin(ShapeRenderer.ShapeType.Line);
        demo.world.physics.getWorld().getBodies(bodies);
        for (Body body : bodies) {
            Entity e = body.getUserData() instanceof Entity ? (Entity) body.getUserData() : null;
            DockingPortsComponent ports = e == null ? null : e.getComponent(DockingPortsComponent.class);
            if (ports == null || demo.world.docking.bodyOf(e) != body) continue;
            for (DockingPort p : ports.ports) {
                if (!p.intact()) shapes.setColor(Color.RED);
                else if (p.state != DockingPort.State.IDLE) shapes.setColor(Color.CYAN);
                else if (p.armed) shapes.setColor(Color.GREEN);
                else shapes.setColor(Color.GRAY);
                Vector2 c = world(body, p.faceX(), p.faceY(), new Vector2());
                Vector2 tip = world(body, p.faceX() + p.normalX * 1.5f, p.faceY() + p.normalY * 1.5f, new Vector2());
                shapes.line(c.x, c.y, tip.x, tip.y);
                shapes.circle(c.x, c.y, 0.35f, 10);
            }
        }
        bodies.clear();
        shapes.end();
    }

    /** Every port door (green open, red shut) and every crew member (white, station hands orange, you in yellow). */
    private void drawDoorsAndCrew() {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        demo.world.physics.getWorld().getBodies(bodies);
        for (Body body : bodies) {
            Entity e = body.getUserData() instanceof Entity ? (Entity) body.getUserData() : null;
            ShipGridComponent grid = e == null ? null : e.getComponent(ShipGridComponent.class);
            if (grid == null || demo.world.docking.bodyOf(e) != body) continue;
            float s = grid.tileSize;
            for (int y = 0; y < grid.height; y++) {
                for (int x = 0; x < grid.width; x++) {
                    MountFootprint m = grid.getTile(x, y).mount;
                    if (m == null || m.kind != MountFootprint.MountKind.PORT || !DockingPortLayout.isDoorTile(m, x, y)) continue;
                    EdgeWall door = DockingPortLayout.door(grid, m, x, y);
                    if (door == null) continue;
                    Side side = DockingPortLayout.facing(m);
                    shapes.setColor(door.blocks() ? Color.RED : Color.GREEN);
                    Vector2 c = grid.tileToLocal(x, y);
                    float ex = c.x + side.dx * s / 2f, ey = c.y + side.dy * s / 2f;
                    float hx = side.dy * s * 0.45f, hy = side.dx * s * 0.45f;
                    Vector2 a = world(body, ex - hx, ey - hy, new Vector2());
                    Vector2 b = world(body, ex + hx, ey + hy, new Vector2());
                    shapes.rectLine(a.x, a.y, b.x, b.y, 0.25f);
                }
            }
        }
        bodies.clear();
        for (Entity crew : demo.world.engine.getEcsEngine().getEntitiesFor(CREW)) {
            GridPositionComponent pos = crew.getComponent(GridPositionComponent.class);
            ShipGridComponent grid = pos.ship == null ? null : pos.ship.getComponent(ShipGridComponent.class);
            Body body = pos.ship == null ? null : demo.world.docking.bodyOf(pos.ship);
            if (grid == null || body == null) continue;
            Vector2 local = pos.toShipLocal(grid, new Vector2());
            Vector2 at = world(body, local.x, local.y, local);
            CrewMemberComponent c = crew.getComponent(CrewMemberComponent.class);
            Color mark = crew == demo.avatar ? Color.YELLOW : c.faction == StationCrew.FACTION ? DeckView.STATION_CREW : Color.WHITE;
            DeckView.crew(shapes, c, at.x, at.y, grid.tileSize, mark, color);
        }
        shapes.end();
    }

    private static Vector2 world(Body body, float x, float y, Vector2 out) {
        Vector2 w = body.getWorldPoint(out.set(x * PhysicsManager.UNITS_PER_METER, y * PhysicsManager.UNITS_PER_METER));
        return out.set(w.x * PhysicsManager.METERS_PER_UNIT, w.y * PhysicsManager.METERS_PER_UNIT);
    }

    private void drawFixture(Body body, Fixture fixture) {
        if (fixture.getShape() instanceof CircleShape) {
            Vector2 c = PhysicsManager.getPosition(body, tmp);
            shapes.circle(c.x, c.y, Math.max(0.3f, fixture.getShape().getRadius() * PhysicsManager.METERS_PER_UNIT), 16);
        } else if (fixture.getShape() instanceof PolygonShape) {
            PolygonShape poly = (PolygonShape) fixture.getShape();
            int n = PolygonSkin.outline(body, poly, polygon);
            shapes.polygon(polygon, 0, n * 2);
        }
    }

    private void drawPanel() {
        batch.setProjectionMatrix(ui.combined);
        batch.begin();
        float h = ui.viewportHeight;
        float x = 12f, y = h - 12f;
        font.setColor(Color.WHITE);
        font.draw(batch, "DOCKING DEMO  -  " + (demo.scene.ordinal() + 1) + ". " + demo.scene.title, x, y);
        y -= 22f;
        font.setColor(Color.LIGHT_GRAY);
        y -= font.draw(batch, demo.scene.help, x, y, PANEL_WIDTH, Align.left, true).height + 12f;
        font.setColor(Color.ORANGE);
        if (!demo.notice().equals(demo.scene.help)) {
            y -= font.draw(batch, demo.notice(), x, y, PANEL_WIDTH, Align.left, true).height + 12f;
        }
        DoorForcing.Job job = demo.world.forcing.jobOf(demo.avatar);
        DoorForcing.Target door = demo.world.forcing.target(demo.avatar, target);
        if (job != null || door.craft != null && door.state != ForceMethod.DoorState.OPEN
                || demo.scene == DockingDemoScene.Scene.FORCE) {
            font.setColor(Color.WHITE);
            String line = job != null ? String.format(java.util.Locale.ROOT, "%s the door: %.0f%%",
                    Character.toUpperCase(job.method.doing.charAt(0)) + job.method.doing.substring(1), job.progress() * 100f)
                    : door.craft == null ? "Not at a docked door" : doorLine(door);
            font.draw(batch, line + String.format(java.util.Locale.ROOT, "   standing with the stations %+.0f",
                    demo.world.standing.standing(StationCrew.FACTION)), x, y);
            y -= 18f;
            if (!demo.world.forcing.lastOutcome.isEmpty()) {
                font.setColor(Color.ORANGE);
                font.draw(batch, demo.world.forcing.lastOutcome, x, y);
                y -= 18f;
            }
            y -= 4f;
        }
        if (demo.scene == DockingDemoScene.Scene.LOADING) {
            font.setColor(Color.WHITE);
            font.draw(batch, String.format(java.util.Locale.ROOT, "Water: %.0f aboard, %.0f at the outpost   [L] load  [K] send back",
                    demo.held(demo.player, DockingDemoScene.WATER), demo.held(demo.target, DockingDemoScene.WATER)), x, y);
            y -= 22f;
        }
        if (demo.scene == DockingDemoScene.Scene.HUB) {
            font.setColor(Color.WHITE);
            font.draw(batch, "Osprey: " + berthing(demo.osprey) + "   Kestrel: " + berthing(demo.kestrel), x, y);
            y -= 22f;
        }
        if (demo.scene == DockingDemoScene.Scene.YARD) {
            font.setColor(Color.WHITE);
            font.draw(batch, String.format(java.util.Locale.ROOT, "Credits %,.0f   %d damaged, %,.0f cr to repair   [J] repair  [N] refit nose  [M] used",
                    demo.player.getComponent(com.ringsofiron.macrosim.economy.TraderComponent.class).credits,
                    demo.world.yard.damage(demo.player).size(), demo.world.yard.repairCost(demo.player)), x, y);
            y -= 18f;
            for (String line : demo.world.yard.progress(demo.player)) {
                font.draw(batch, line, x, y);
                y -= 18f;
            }
            y -= 4f;
        }
        font.setColor(Color.SKY);
        for (String line : demo.docking.readout()) {
            font.draw(batch, line, x, y);
            y -= 18f;
        }
        if (demo.autopilot) {
            font.setColor(Color.GREEN);
            font.draw(batch, "Autopilot flying the approach (P to stop)", x, y);
        }
        font.setColor(Color.GRAY);
        StringBuilder scenes = new StringBuilder();
        for (DockingDemoScene.Scene s : DockingDemoScene.Scene.values()) {
            scenes.append((s.ordinal() + 1) % 10).append(' ').append(s.title).append("   ");
        }
        font.draw(batch, scenes.toString(), x, 64f);
        if (demo.deckView) {
            font.setColor(Color.YELLOW);
            Entity aboard = demo.aboard();
            font.draw(batch, "Deck view: WASD walks" + (aboard != null && aboard != demo.player
                    ? ", you're aboard " + PlayerDocking.name(aboard) : "") + "  (V to fly)", x, y - 6f);
            font.setColor(Color.GRAY);
        }
        font.draw(batch, "W/S thrust  A/D turn  Z/C strafe  G arm port + clearance  H next port  U undock"
                + "  P autopilot  V deck view  R restart  wheel zoom  Esc quit", x, 44f);
        font.draw(batch, "Deck view: F pry/hack the door across  B cut it   Scene 8: L load water  K send back"
                + "   9: O outpost power   0: J repair  N/M refit", x, 24f);
        batch.end();
    }

    /** What a hauler flying itself in or out is doing. */
    private static String berthing(Entity hauler) {
        com.ringsofiron.stationsdocking.BerthingComponent b = hauler == null ? null
                : hauler.getComponent(com.ringsofiron.stationsdocking.BerthingComponent.class);
        return b == null || b.status.isEmpty() ? "-" : b.status;
    }

    private static String doorLine(DoorForcing.Target door) {
        switch (door.state) {
            case OPEN:
                return "The door across is open";
            case UNPOWERED:
                return "The door across has no power: [F] pry  [B] cut";
            case LOCKED:
                return "The door across is powered and locked: [F] hack  [B] cut";
            default:
                return "No door across";
        }
    }

    @Override
    public void resize(int width, int height) {
        float aspect = height == 0 ? 1f : (float) width / height;
        camera.setToOrtho(false, viewMeters * aspect, viewMeters);
        ui.setToOrtho(false, width, height);
    }

    @Override
    public void dispose() {
        demo.dispose();
        shapes.dispose();
        batch.dispose();
        font.dispose();
        art.dispose();
    }
}

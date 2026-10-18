package com.hawkwing.RingsOfIron;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.Shape;
import com.badlogic.gdx.utils.Array;
import com.hawkwing.RingsOfIron.game.BurnPlanner;
import com.hawkwing.RingsOfIron.game.CombatTargetingProvider;
import com.hawkwing.RingsOfIron.game.GameHud;
import com.hawkwing.RingsOfIron.game.GameScenario;
import com.hawkwing.RingsOfIron.game.GameWorld;
import com.hawkwing.RingsOfIron.game.PlayerGunnery;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.aicrew.PlayerAvatarComponent;
import com.ringsofiron.combatsys.components.ProjectileComponent;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.orbital.warp.TimeWarpController;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.FlightControlComponent;
import com.ringsofiron.uihud.UiHud;
import com.ringsofiron.uihud.ViewMode;
import com.ringsofiron.uihud.ViewportManager;
import com.ringsofiron.uihud.components.HudMarkerComponent;
import com.ringsofiron.uihud.minimap.TileShade;
import com.ringsofiron.combatsys.components.DecoyComponent;
import com.ringsofiron.combatsys.components.MissileComponent;
import com.ringsofiron.combatsys.components.WeaponMountComponent;
import com.ringsofiron.combatsys.components.WeaponsComponent;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * The game: every module running together through {@link GameWorld}, starting from
 * {@link GameScenario}. Run with {@code gradlew :lwjgl3:runGame}.
 *
 * <p>Controls, outside: W/S main engine, A/D turn, Z/C strafe, X rotation assist, Tab cycle
 * target, Space fire the guns and laser (the turrets lead the locked target themselves), R launch
 * a missile at it, F drop a decoy, comma/period time warp down/up, mouse wheel zoom. Point defense
 * shoots down incoming missiles by itself. The raider fights back once you shoot at it. V switches
 * to the deck view, where WASD walks your character and E uses the console you stand at. F5
 * quick-saves, F9 loads the quick save. Esc quits.
 *
 * <p>Burn planner, shown on the orbit map: N plans a burn, B selects the next one, Up/Down add
 * prograde/retrograde delta-v, Left/Right radial in/out, [ and ] move it along the orbit (Shift for
 * ten times as much), Delete removes it. Burns fire on their own when due.
 */
public class RingsOfIronGame extends ApplicationAdapter {
    private static final ComponentMapper<TransformComponent> TRANSFORMS = ComponentMapper.getFor(TransformComponent.class);
    private static final ComponentMapper<HudMarkerComponent> MARKERS = ComponentMapper.getFor(HudMarkerComponent.class);
    private static final ComponentMapper<ProjectileComponent> PROJECTILES = ComponentMapper.getFor(ProjectileComponent.class);
    private static final ComponentMapper<MissileComponent> MISSILES = ComponentMapper.getFor(MissileComponent.class);
    private static final Family COMBAT_ROUNDS = Family.all(ProjectileComponent.class).get();
    private static final Family DECOYS = Family.all(DecoyComponent.class).get();
    private static final Family ARMED = Family.all(WeaponsComponent.class).get();
    private static final float INTERIOR_VIEW = 40f;
    private static final float START_VIEW = 160f;
    private static final float STATUS_WIDTH = 420f;
    private static final float WALK_SPEED = 1f;

    private final Path assetsDir;
    private final Path modsDir;

    private GameWorld world;
    private GameScenario game;
    private GameHud gameHud;
    private UiHud hud;
    private ViewportManager viewports;
    private PlayerGunnery gun;
    /** Flashes for warheads and missiles shot down: {x, y, age}. */
    private final Array<float[]> explosions = new Array<>();

    private ShapeRenderer shapes;
    private SpriteBatch batch;
    private BitmapFont font;
    private float exteriorViewMeters = START_VIEW;
    private String notice;
    private float noticeTime;

    private final Array<Body> bodies = new Array<>();
    private final Vector2 tmp = new Vector2();
    private final Color color = new Color();
    private final float[] polygon = new float[16];

    /** Base data from {@code assets/data} under the working directory, mods from {@code mods}. */
    public RingsOfIronGame() {
        this(Paths.get("").toAbsolutePath(), Paths.get("mods").toAbsolutePath());
    }

    public RingsOfIronGame(Path assetsDir, Path modsDir) {
        this.assetsDir = assetsDir;
        this.modsDir = modsDir;
    }

    @Override
    public void create() {
        shapes = new ShapeRenderer();
        batch = new SpriteBatch();
        font = new BitmapFont();

        hud = new UiHud();
        viewports = hud.getViewports();
        viewports.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        viewports.setViewMeters(START_VIEW);

        GameWorld.Modules modules = new GameWorld.Modules();
        // Content loads on this thread, so the dialog cannot block: scripts wait, and run once the
        // player allows them. "Remember" answers are kept between launches.
        modules.modTrust = new com.ringsofiron.uihud.modding.ModTrustDialog(hud.getStage(), hud.getSkin(),
                Gdx.app.getPreferences("RingsOfIron"))
                .onDecision(allowed -> {
                    if (allowed && world != null && world.getContent() != null) world.getContent().runDeferredScripts();
                });
        world = new GameWorld(modules);
        game = GameScenario.start(world, assetsDir, modsDir);
        gameHud = new GameHud(world, hud);

        gun = new PlayerGunnery(world, game.player);
        gun.setTarget(game.nextContact(null));
        hud.setTargeting(new CombatTargetingProvider(gun));
        world.combatEvents.intercepts.add((signal, hit) -> {
            if (hit.destroyed && hit.missile != null) explosions.add(new float[]{hit.point.x, hit.point.y, 0f});
        });
        world.combatEvents.detonations.add((signal, boom) ->
                explosions.add(new float[]{boom.worldPoint.x, boom.worldPoint.y, 0f}));

        hud.getViewMode().addListener((previous, current) -> {
            if (previous == current) return;
            if (current == ViewMode.INTERIOR) {
                exteriorViewMeters = viewports.getViewMeters();
                viewports.setViewMeters(INTERIOR_VIEW);
            } else {
                viewports.setViewMeters(exteriorViewMeters);
            }
        });
        world.timeWarp.changes.add((signal, change) -> {
            if (change.blockedReason != null) show("Time warp refused: " + change.blockedReason);
            else if (change.reason != null) show("Dropped out of warp: " + change.reason);
        });

        Gdx.input.setInputProcessor(new InputMultiplexer(hud.getInputProcessor(), new InputAdapter() {
            @Override
            public boolean scrolled(float amountX, float amountY) {
                viewports.zoom((float) Math.pow(1.15f, amountY));
                return true;
            }
        }));
        show(game.content.report.hasErrors() ? "Content loaded with errors; see the log" : "Welcome aboard");
        if (game.content.report.hasErrors()) Gdx.app.error("RingsOfIron", game.content.report.toString());
    }

    @Override
    public void render() {
        float delta = Math.min(Gdx.graphics.getDeltaTime(), 0.25f);
        handleInput(delta);
        if (world.getCrash() == null) gun.update(delta);
        world.update(delta);
        noticeTime -= delta;

        TransformComponent pt = TRANSFORMS.get(game.player);
        if (pt != null) viewports.follow(pt.x, pt.y);

        Gdx.gl.glClearColor(0.02f, 0.02f, 0.05f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        viewports.applyWorld();
        shapes.setProjectionMatrix(viewports.getWorldCamera().combined);
        if (hud.getViewMode().getMode() == ViewMode.INTERIOR) {
            drawInterior();
        } else {
            drawBodies();
        }
        gameHud.draw(delta);
        drawStatus();
        if (world.getCrash() != null) drawCrash();
    }

    private void handleInput(float delta) {
        boolean consoleOpen = hud.getConsoleWindow().isOpen();
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) && !consoleOpen) Gdx.app.exit();
        if (Gdx.input.isKeyJustPressed(Input.Keys.F9)) quickLoad();
        if (world.getCrash() != null) return;
        if (Gdx.input.isKeyJustPressed(Input.Keys.PERIOD)) warp(1);
        if (Gdx.input.isKeyJustPressed(Input.Keys.COMMA)) warp(-1);
        if (Gdx.input.isKeyJustPressed(Input.Keys.F5)) quickSave();

        FlightControlComponent controls = game.player.getComponent(FlightControlComponent.class);
        PlayerAvatarComponent walker = game.avatar == null ? null : game.avatar.getComponent(PlayerAvatarComponent.class);
        if (controls != null) {
            controls.forward = 0f;
            controls.turn = 0f;
            controls.strafe = 0f;
        }
        if (walker != null) {
            walker.moveX = 0f;
            walker.moveY = 0f;
        }
        if (consoleOpen) return;

        if (hud.getViewMode().getMode() == ViewMode.INTERIOR) {
            if (walker == null) return;
            if (Gdx.input.isKeyPressed(Input.Keys.W)) walker.moveY += WALK_SPEED;
            if (Gdx.input.isKeyPressed(Input.Keys.S)) walker.moveY -= WALK_SPEED;
            if (Gdx.input.isKeyPressed(Input.Keys.A)) walker.moveX -= WALK_SPEED;
            if (Gdx.input.isKeyPressed(Input.Keys.D)) walker.moveX += WALK_SPEED;
            return;
        }
        if (controls != null) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.X)) controls.rotationAssist = !controls.rotationAssist;
            if (Gdx.input.isKeyPressed(Input.Keys.W)) controls.forward += 1f;
            if (Gdx.input.isKeyPressed(Input.Keys.S)) controls.forward -= 1f;
            if (Gdx.input.isKeyPressed(Input.Keys.A)) controls.turn += 1f;
            if (Gdx.input.isKeyPressed(Input.Keys.D)) controls.turn -= 1f;
            if (Gdx.input.isKeyPressed(Input.Keys.Z)) controls.strafe += 1f;
            if (Gdx.input.isKeyPressed(Input.Keys.C)) controls.strafe -= 1f;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.SPACE)) gun.fireGuns();
        if (Gdx.input.isKeyJustPressed(Input.Keys.R) && !gun.launchMissile()) {
            show(gun.getTarget() == null ? "No target for the missile" : "Launcher reloading");
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.F) && gun.dropDecoy()) show("Decoy away");
        planBurns();
        if (Gdx.input.isKeyJustPressed(Input.Keys.TAB)) gun.setTarget(game.nextContact(gun.getTarget()));
    }

    private void planBurns() {
        BurnPlanner burns = world.burns;
        boolean shift = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
        double dv = shift ? 10 : 1;
        int steps = shift ? 10 : 1;
        if (Gdx.input.isKeyJustPressed(Input.Keys.N)) burns.addNode();
        if (Gdx.input.isKeyJustPressed(Input.Keys.B)) burns.selectNext();
        if (Gdx.input.isKeyJustPressed(Input.Keys.FORWARD_DEL) || Gdx.input.isKeyJustPressed(Input.Keys.DEL)) {
            burns.deleteSelected();
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.UP)) burns.adjust(dv, 0);
        if (Gdx.input.isKeyJustPressed(Input.Keys.DOWN)) burns.adjust(-dv, 0);
        if (Gdx.input.isKeyJustPressed(Input.Keys.RIGHT)) burns.adjust(0, dv);
        if (Gdx.input.isKeyJustPressed(Input.Keys.LEFT)) burns.adjust(0, -dv);
        if (Gdx.input.isKeyJustPressed(Input.Keys.RIGHT_BRACKET)) burns.shiftTime(steps);
        if (Gdx.input.isKeyJustPressed(Input.Keys.LEFT_BRACKET)) burns.shiftTime(-steps);
    }

    private void drawCrash() {
        GameWorld.Crash crash = world.getCrash();
        viewports.applyUi();
        batch.setProjectionMatrix(viewports.getUiCamera().combined);
        batch.begin();
        float w = viewports.getUiViewport().getWorldWidth();
        float h = viewports.getUiViewport().getWorldHeight();
        font.getData().setScale(3f);
        font.setColor(Color.RED);
        font.draw(batch, "SHIP LOST", 0f, h / 2f + 40f, w, com.badlogic.gdx.utils.Align.center, false);
        font.getData().setScale(1.5f);
        font.setColor(Color.WHITE);
        font.draw(batch, "You crashed into " + crash.bodyName + " on day " + (long) (crash.time / 86400) + ".",
                0f, h / 2f - 10f, w, com.badlogic.gdx.utils.Align.center, false);
        font.getData().setScale(1f);
        font.setColor(Color.LIGHT_GRAY);
        font.draw(batch, "F9 loads the quick save   Esc quits", 0f, h / 2f - 50f, w, com.badlogic.gdx.utils.Align.center, false);
        batch.end();
    }

    private void warp(int direction) {
        if (!world.timeWarp.step(direction)) {
            String reason = world.engine.getRailsWarpBlockedReason();
            show("Time warp refused" + (reason == null ? "" : ": " + reason));
        }
    }

    private FileHandle quickSaveFile() {
        return Gdx.files.local("saves/quicksave.json");
    }

    private void quickSave() {
        try {
            String refused = game.quickSave(quickSaveFile());
            show(refused == null ? "Game saved" : "Can't save: " + refused);
        } catch (RuntimeException e) {
            Gdx.app.error("RingsOfIron", "Save failed", e);
            show("Save failed; see the log");
        }
    }

    private void quickLoad() {
        FileHandle file = quickSaveFile();
        if (!file.exists()) {
            show("No quick save yet (F5 saves)");
            return;
        }
        try {
            game.quickLoad(file);
            world.clearCrash();
            // The loaded ship's own weapons; its sensor lock comes back with the save.
            gun = new PlayerGunnery(world, game.player);
            if (gun.getTarget() == null) gun.setTarget(game.nextContact(null));
            hud.setTargeting(new CombatTargetingProvider(gun));
            show("Game loaded");
        } catch (RuntimeException e) {
            Gdx.app.error("RingsOfIron", "Load failed", e);
            show("Load failed; see the log");
        }
    }

    private void show(String message) {
        notice = message;
        noticeTime = 4f;
    }

    // -----------------------------------------------------------------------
    private void drawBodies() {
        world.physics.getWorld().getBodies(bodies);
        shapes.begin(ShapeRenderer.ShapeType.Line);
        for (Body body : bodies) {
            Object owner = body.getUserData();
            Entity e = owner instanceof Entity ? (Entity) owner : null;
            HudMarkerComponent marker = e == null ? null : MARKERS.get(e);
            if (e == game.player) shapes.setColor(Color.CYAN);
            else if (e != null && PROJECTILES.has(e)) shapes.setColor(Color.ORANGE);
            else if (marker != null) shapes.setColor(marker.color);
            else shapes.setColor(Color.LIGHT_GRAY);
            for (Fixture fixture : body.getFixtureList()) drawFixture(body, fixture.getShape());
        }
        shapes.end();
        bodies.clear();
        drawCombat(Gdx.graphics.getDeltaTime());
    }

    /**
     * Rounds as streaks along their last tick's flight, missiles as a nose line with a plume
     * while they burn, beams as lines from the gun to what they touch, decoys as small flares,
     * and a fading ring where a warhead went off or a missile was shot down. Sizes scale with
     * the zoom so they stay visible.
     */
    private void drawCombat(float delta) {
        float px = viewports.getViewMeters() / 400f;
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.begin(ShapeRenderer.ShapeType.Line);
        for (Entity e : world.engine.getEcsEngine().getEntitiesFor(COMBAT_ROUNDS)) {
            ProjectileComponent p = PROJECTILES.get(e);
            MissileComponent m = MISSILES.get(e);
            if (m == null) {
                shapes.setColor(p.owner == game.player ? Color.YELLOW : Color.ORANGE);
                // A streak at least a few pixels long, behind the round.
                tmp.set(p.pos).sub(p.prevPos);
                float len = tmp.len();
                if (len < 3f * px) tmp.setLength(3f * px);
                shapes.line(p.pos.x - tmp.x, p.pos.y - tmp.y, p.pos.x, p.pos.y);
                continue;
            }
            float size = Math.max(m.def.hitRadius * 3f, 6f * px);
            float cos = (float) Math.cos(m.heading), sin = (float) Math.sin(m.heading);
            shapes.setColor(m.launcher == game.player ? Color.CYAN : Color.RED);
            shapes.line(p.pos.x - cos * size, p.pos.y - sin * size, p.pos.x + cos * size, p.pos.y + sin * size);
            if (m.throttle > 0f) {
                shapes.setColor(Color.ORANGE);
                float plume = size * (1f + 2f * m.throttle);
                shapes.line(p.pos.x - cos * size, p.pos.y - sin * size, p.pos.x - cos * plume, p.pos.y - sin * plume);
            }
        }
        for (Entity e : world.engine.getEcsEngine().getEntitiesFor(ARMED)) {
            WeaponsComponent weapons = e.getComponent(WeaponsComponent.class);
            for (WeaponMountComponent m : weapons.mounts) {
                if (!m.beamOn) continue;
                shapes.setColor(e == game.player ? Color.SKY : Color.MAGENTA);
                shapes.line(m.muzzle.x, m.muzzle.y, m.beamEnd.x, m.beamEnd.y);
            }
        }
        shapes.setColor(Color.WHITE);
        for (Entity e : world.engine.getEcsEngine().getEntitiesFor(DECOYS)) {
            DecoyComponent d = e.getComponent(DecoyComponent.class);
            shapes.circle(d.pos.x, d.pos.y, Math.max(1f, 3f * px), 8);
        }
        for (int i = explosions.size - 1; i >= 0; i--) {
            float[] x = explosions.get(i);
            x[2] += delta;
            if (x[2] > 1f) {
                explosions.removeIndex(i);
                continue;
            }
            shapes.setColor(1f, 0.6f, 0.2f, 1f - x[2]);
            shapes.circle(x[0], x[1], Math.max(5f, 20f * px) * (0.3f + x[2]), 20);
        }
        shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    private void drawFixture(Body body, Shape shape) {
        if (shape instanceof CircleShape) {
            Vector2 c = PhysicsManager.getPosition(body, tmp);
            shapes.circle(c.x, c.y, Math.max(0.5f, shape.getRadius() * PhysicsManager.METERS_PER_UNIT), 16);
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

    /** The deck plan at the ship's pose, in the minimap's colors, with everyone aboard. */
    private void drawInterior() {
        TransformComponent pt = TRANSFORMS.get(game.player);
        ShipGridComponent grid = game.player.getComponent(ShipGridComponent.class);
        if (pt == null || grid == null) return;
        float s = grid.tileSize;
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
                shapes.rect(p.x - s / 2f + 0.03f, p.y - s / 2f + 0.03f, s - 0.06f, s - 0.06f);
            }
        }
        for (Entity e : world.engine.getEcsEngine().getEntitiesFor(CrewService.CREW)) {
            GridPositionComponent pos = e.getComponent(GridPositionComponent.class);
            if (pos.ship != game.player) continue;
            shapes.setColor(e == game.avatar ? Color.WHITE : Color.GOLD);
            shapes.circle(grid.gridOriginLocal.x + pos.x * s, grid.gridOriginLocal.y + pos.y * s, 0.3f * s, 12);
        }
        shapes.end();
        shapes.identity();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    private void drawStatus() {
        viewports.applyUi();
        batch.setProjectionMatrix(viewports.getUiCamera().combined);
        batch.begin();
        // Top right: ui-hud's flight panel holds the top left.
        float left = viewports.getUiViewport().getWorldWidth() - STATUS_WIDTH;
        float top = viewports.getUiViewport().getWorldHeight() - 10f;
        TimeWarpController warp = world.timeWarp;
        double t = world.engine.getSimSeconds();
        font.setColor(Color.WHITE);
        font.draw(batch, String.format("Day %d  %02d:%02d:%02d    Warp %dx%s", (long) (t / 86400),
                (long) (t / 3600) % 24, (long) (t / 60) % 60, (long) t % 60, warp.getWarp(),
                warp.isRailsWarp() ? " (rails)" : ""), left, top);
        Entity target = gun.getTarget();
        HudMarkerComponent marker = target == null ? null : MARKERS.get(target);
        font.draw(batch, "Target: " + (marker == null ? "none" : marker.label) + (gun.gunsReady() ? "" : "   (gun reloading)")
                + (target == game.raider && game.raiderHostile() ? "   FIRING ON YOU" : ""), left, top - 20f);
        float reload = gun.missileReloadTime();
        // Inbound missiles are ui-hud's missile warning.
        font.draw(batch, "Missile: " + (reload < 0f ? "offline" : reload > 0f ? String.format("%.0f s", reload) : "ready"),
                left, top - 40f);
        font.setColor(Color.LIGHT_GRAY);
        // Key hints sit to the right of the ship panel, which only shows outside
        Actor shipPanel = hud.getMinimapPanel();
        float hintX = shipPanel.isVisible() ? shipPanel.getX() + shipPanel.getWidth() + 12f : 10f;
        font.draw(batch, hud.getViewMode().getMode() == ViewMode.INTERIOR
                ? "WASD walk   E use console   V outside view   F5 save  F9 load   Esc quit"
                : "W/S thrust  A/D turn  Z/C strafe  Space guns  R missile  F decoy  Tab target  , . warp  V deck view  F5 save  F9 load  Esc quit",
                hintX, 24f);
        if (notice != null && noticeTime > 0f) {
            font.setColor(Color.ORANGE);
            font.draw(batch, notice, left, top - 60f);
        }
        batch.end();
    }

    @Override
    public void resize(int width, int height) {
        gameHud.resize(width, height);
    }

    @Override
    public void dispose() {
        gameHud.dispose();
        world.dispose();
        shapes.dispose();
        batch.dispose();
        font.dispose();
    }
}

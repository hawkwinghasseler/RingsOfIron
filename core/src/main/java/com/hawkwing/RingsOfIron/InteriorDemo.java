package com.hawkwing.RingsOfIron;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
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
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.Array;
import com.hawkwing.RingsOfIron.art.ArtLibrary;
import com.hawkwing.RingsOfIron.art.ArtNames;
import com.hawkwing.RingsOfIron.art.DeckArt;
import com.hawkwing.RingsOfIron.debug.DebugNames;
import com.hawkwing.RingsOfIron.debug.DebugTarget;
import com.hawkwing.RingsOfIron.debug.DevConsole;
import com.hawkwing.RingsOfIron.game.DeckView;
import com.hawkwing.RingsOfIron.game.GameScenario;
import com.hawkwing.RingsOfIron.game.InteriorLab;
import com.hawkwing.RingsOfIron.game.WorldDebug;
import com.hawkwing.RingsOfIron.game.WoundWatch;
import com.hawkwing.RingsOfIron.render.DeckRenderer;
import com.ringsofiron.aicrew.CrewDef;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewRole;
import com.ringsofiron.aicrew.CrewState;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.aicrew.Wound;
import com.ringsofiron.combatsys.components.MissileComponent;
import com.ringsofiron.combatsys.components.ProjectileComponent;
import com.ringsofiron.interiorgrid.GridHazardSystem;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.interiorgrid.crew.CrewDamageSink.DamageCause;
import com.ringsofiron.moddingdata.defs.ShipDef;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.KinematicTransientComponent;
import com.ringsofiron.uihud.HudSkin;
import com.ringsofiron.uihud.minimap.TileShade;
import com.ringsofiron.uihud.widgets.DevConsoleWindow;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * The interior demo: one of the game's ships with its crew, in the game's own world
 * ({@link InteriorLab}), to hurt in every way the game can and watch how the crew and the wound
 * rules answer. Every tool goes through the game's systems ({@link WorldDebug}): a shot is a real
 * round fired at the hull, a ram a real fast pass, a blast the warhead path, a fire the deck's own.
 *
 * <p>Pick a tool with the number keys and click the deck. Weapon fire and rams come in from
 * straight outboard of the tile clicked; drag from outside the hull to the tile to aim them from
 * there instead. Q / E change the tool's setting (amount, weapon, warhead, speed, role), C its
 * second setting where it has one. Right click picks a crew member to follow in the panel.
 *
 * <p>Keys: Tab / Shift+Tab next and previous hull, R restart the hull, A an attacker on or off
 * (Shift+A the next attacker hull), Space pause, - / = slower and faster, N pin the ship in place or
 * let it drift, H heal everyone, F put out every fire, V fresh air, D doors, O reactors, wheel zoom,
 * Esc quit.
 *
 * <p>~ opens the developer console ({@link DevConsole}) over the deck, as in the game: while it is
 * open a left click selects (again to cycle what is under the cursor), the commands act on the
 * selection ({@code wound 95}, {@code shoot 'railgun_mk1'}, {@code ram 3}), Space no longer pauses
 * ({@code pause()} does) and Esc deselects, then closes it.
 */
public class InteriorDemo extends ApplicationAdapter {
    private static final float PANEL_WIDTH = 470f;
    private static final float LOG_HEIGHT = 190f;
    private static final float LINE = 17f;
    /** Tint of a crew figure who is down. */
    /** Height of the help lines at the top of the deck view, pixels. */
    private static final float TOP = 7 * LINE;
    /** How far the cursor must drag, tiles, to aim a shot from where it started. */
    private static final float DRAG_TILES = 1.5f;
    private static final Family PROJECTILES = Family.all(ProjectileComponent.class).get();
    private static final Family ROCKS = Family.all(KinematicTransientComponent.class).get();
    private static final float[] WOUND_AMOUNTS = {10f, 30f, 50f, 70f, 80f, 90f, 100f, 150f, 200f};
    private static final DamageCause[] CAUSES = {DamageCause.SPALL, DamageCause.PENETRATOR, DamageCause.FRAGMENT,
            DamageCause.BLAST, DamageCause.BURN, DamageCause.FIRE, DamageCause.ASPHYXIATION, DamageCause.SMALL_ARMS};
    private static final float[] RAM_SPEEDS = {1_500f, 2_000f, 4_000f, 8_000f};
    private static final float[] RAM_MASSES = {200f, 2_000f, 20_000f};
    private static final CrewRole[] ROLES = {CrewRole.MEDIC, CrewRole.ENGINEER, CrewRole.CREWMAN, CrewRole.MARINE};
    private static final float[] DAMAGE_AMOUNTS = {0.1f, 0.25f, 0.5f, 1f};
    /** Who the attacker comes from, and where: the bearing from the bow, degrees. */
    private static final float ATTACK_BEARING = 90f;
    /** The ai-crew faction boarders spawn in: anyone but the ship's own is hostile to it. */
    private static final int BOARDERS = 1;

    enum Tool {
        WOUND("Wound crew"), WEAPON("Fire weapon"), BLAST("Blast inside"), RAM("Ram"), FIRE("Start fire"),
        BREACH("Breach"), SHORT("Short out"), DAMAGE("Damage tile/system"), SPAWN("Spawn crew"), REMOVE("Remove crew");

        final String title;

        Tool(String title) {
            this.title = title;
        }
    }

    private final Path assetsDir;
    private InteriorLab lab;
    private OrthographicCamera camera, ui;
    private ShapeRenderer shapes;
    private SpriteBatch batch;
    private BitmapFont font;
    private ArtLibrary art;
    private DeckArt deckArt;
    private DeckRenderer deckRenderer;
    private final Array<Entity> decks = new Array<>();
    /** Meters per screen pixel. */
    private float zoom = 0.05f;

    private Tool tool = Tool.WOUND;
    private int woundAmount = 4, cause, weapon, warhead, ramSpeed = 1, ramMass = 1, role, damageAmount = 1;
    private boolean boarder;
    private int attackerHull;
    private Entity selected;
    /** The ship the screen was last set up for. */
    private Entity shown;
    private String message = "";
    /** Where a left-button drag started, ship-local meters, or null while not dragging. */
    private Vector2 pressedAt;
    private int hoverX = -1, hoverY = -1;

    private final Vector2 tmp = new Vector2(), tmp2 = new Vector2();
    private final Color color = new Color();

    /** The developer console, made again for each hull's world, in the one window. */
    private DevConsole dev;
    private DevConsoleWindow devWindow;
    private com.badlogic.gdx.scenes.scene2d.Stage stage;
    private com.badlogic.gdx.scenes.scene2d.ui.Skin skin;
    /** Seconds the console's step() still has to run while paused. */
    private float devStep;

    public InteriorDemo() {
        this(Paths.get("").toAbsolutePath());
    }

    public InteriorDemo(Path assetsDir) {
        this.assetsDir = assetsDir;
    }

    @Override
    public void create() {
        shapes = new ShapeRenderer();
        batch = new SpriteBatch();
        font = new BitmapFont();
        art = ArtLibrary.load(ArtLibrary.DEFAULT_PATH);
        deckArt = new DeckArt(art);
        deckRenderer = new DeckRenderer(() -> lab.world, shapes, batch, deckArt, null, () -> null)
                .inShipFrame().setStyle(crewStyle());
        camera = new OrthographicCamera();
        ui = new OrthographicCamera();
        lab = new InteriorLab(assetsDir);
        skin = HudSkin.create();
        stage = new com.badlogic.gdx.scenes.scene2d.Stage(new com.badlogic.gdx.utils.viewport.ScreenViewport());
        load(GameScenario.PLAYER_SHIP);
        devWindow = new DevConsoleWindow(skin, dev.console, () -> (lab.paused ? "[paused] " : "") + DebugNames.describe(dev.selected()));
        Gdx.input.setInputProcessor(new com.badlogic.gdx.InputMultiplexer(new InputAdapter() {
            // ~ opens and closes the console; with it open, Esc deselects, then closes it
            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.GRAVE) {
                    devWindow.toggle(stage);
                    return true;
                }
                if (keycode == Input.Keys.ESCAPE && devWindow.isOpen()) {
                    if (!dev.deselect()) devWindow.setOpen(false, stage);
                    return true;
                }
                return false;
            }
        }, stage, new InputAdapter() {
            // With the console open a left click on the deck selects what is under it
            @Override
            public boolean touchDown(int screenX, int screenY, int pointer, int button) {
                if (!devWindow.isOpen() || button != Input.Buttons.LEFT || !onDeck(screenX)) return false;
                Vector2 at = WorldDebug.toWorld(body(), local(screenX, screenY, tmp).x, tmp.y, tmp2);
                DebugTarget t = dev.click(at.x, at.y, true, zoom, List.of(lab.ship));
                if (t != null && t.isCrew()) selected = t.entity;
                return true;
            }
        }, new InputAdapter() {
            @Override
            public boolean scrolled(float amountX, float amountY) {
                zoom = MathUtils.clamp(zoom * (float) Math.pow(1.15f, amountY), 0.005f, 2f);
                return true;
            }

            @Override
            public boolean touchDown(int screenX, int screenY, int pointer, int button) {
                if (button == Input.Buttons.LEFT && onDeck(screenX)) pressedAt = local(screenX, screenY, new Vector2());
                if (button == Input.Buttons.RIGHT && onDeck(screenX)) select(screenX, screenY);
                return true;
            }

            @Override
            public boolean touchUp(int screenX, int screenY, int pointer, int button) {
                if (button != Input.Buttons.LEFT || pressedAt == null) return true;
                Vector2 from = pressedAt;
                pressedAt = null;
                apply(local(screenX, screenY, new Vector2()), from);
                return true;
            }
        }));
    }

    private void load(String hull) {
        lab.load(hull);
        shipLoaded();
    }

    /** A new ship in the lab (by a key, or a script): nobody followed, zoomed to fit. */
    private void shipLoaded() {
        shown = lab.ship;
        // The console carries on in the new world: its scrollback, history and variables kept
        dev = new DevConsole(lab.world, null, null, Paths.get("saves", "console-history.txt").toAbsolutePath(), lab.debug,
                dev == null ? null : dev.console);
        dev.setHost(new DevConsole.Host() {
            @Override
            public void setFrozen(boolean frozen) {
                lab.paused = frozen;
                devStep = 0f;
            }

            @Override
            public boolean isFrozen() {
                return lab.paused;
            }

            @Override
            public void step(float seconds) {
                devStep += seconds;
            }
        });
        selected = null;
        ShipGridComponent g = grid();
        float w = Gdx.graphics.getWidth() - PANEL_WIDTH - 60f, h = Gdx.graphics.getHeight() - LOG_HEIGHT - TOP - 40f;
        zoom = Math.max(g.width * g.tileSize / w, g.height * g.tileSize / h);
        message = name(lab.hull) + ": " + lab.debug.crewAboard(lab.ship).size() + " aboard";
    }

    /** The demo's world, for scripts (the smoke test drives its tools). */
    public InteriorLab lab() {
        return lab;
    }

    /** Follows {@code crew} in the panel. */
    public void select(Entity crew) {
        selected = crew;
    }

    private ShipGridComponent grid() {
        return lab.ship.getComponent(ShipGridComponent.class);
    }

    private Body body() {
        return lab.world.docking.bodyOf(lab.ship);
    }

    private String name(String shipId) {
        ShipDef def = lab.world.getContent().defs.get("ship", shipId, ShipDef.class);
        return def == null || def.name == null ? shipId : def.name;
    }

    @Override
    public void resize(int width, int height) {
        ui.setToOrtho(false, width, height);
        if (stage != null) stage.getViewport().update(width, height, true);
    }

    @Override
    public void render() {
        float delta = Math.min(Gdx.graphics.getDeltaTime(), 0.25f);
        if (!devWindow.isTyping()) handleKeys();
        if (lab.paused && devStep > 0f) {
            // step(): the paused world runs on for what is left of the seconds asked for
            float run = Math.min(devStep, delta * lab.speed);
            devStep -= run;
            lab.paused = false;
            lab.update(run / lab.speed);
            lab.paused = true;
        } else {
            lab.update(delta);
        }
        if (lab.ship != shown) shipLoaded();
        if (selected != null && (selected.isScheduledForRemoval() || lab.world.crew.crew(selected) == null)) selected = null;

        placeCamera();
        Gdx.gl.glClearColor(0.03f, 0.03f, 0.06f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_STENCIL_BUFFER_BIT);
        shapes.setProjectionMatrix(camera.combined);
        batch.setProjectionMatrix(camera.combined);
        decks.clear();
        decks.add(lab.ship);
        deckRenderer.draw(decks, camera, lab.paused ? 0f : delta * lab.speed);
        drawFlight();
        drawCrewLabels();
        drawCursor();
        drawDevSelection();
        drawPanels();
        stage.getViewport().apply(true);
        stage.act(delta);
        stage.draw();
    }

    /** A ring round what the console has selected, in the ship's own frame as the camera is. */
    private void drawDevSelection() {
        if (dev.selected() == null) return;
        float r = dev.picker.locate(dev.selected(), tmp);
        if (r < 0f) return;
        Vector2 at = WorldDebug.toLocal(body(), tmp.x, tmp.y, tmp2);
        shapes.setProjectionMatrix(camera.combined);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(1f, 0.3f, 1f, devWindow.isOpen() ? 1f : 0.5f);
        if (dev.selected().isTile()) {
            float s = grid().tileSize * 0.5f;
            shapes.rect(at.x - s, at.y - s, 2f * s, 2f * s);
        } else if (dev.selected().isCraft()) {
            ShipGridComponent g = grid();
            shapes.rect(g.gridOriginLocal.x, g.gridOriginLocal.y, g.width * g.tileSize, g.height * g.tileSize);
        } else {
            shapes.circle(at.x, at.y, Math.max(r * 1.3f, 6f * zoom), 24);
        }
        shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    /** The ship's middle in the middle of what the panels leave: the camera works in the ship's own frame. */
    private void placeCamera() {
        ShipGridComponent g = grid();
        float w = Gdx.graphics.getWidth(), h = Gdx.graphics.getHeight();
        camera.viewportWidth = w * zoom;
        camera.viewportHeight = h * zoom;
        float cx = g.gridOriginLocal.x + g.width * g.tileSize / 2f, cy = g.gridOriginLocal.y + g.height * g.tileSize / 2f;
        camera.position.set(cx + PANEL_WIDTH / 2f * zoom, cy - (LOG_HEIGHT - TOP) / 2f * zoom, 0f);
        camera.update();
    }

    // -----------------------------------------------------------------------
    // Input
    // -----------------------------------------------------------------------

    private void handleKeys() {
        boolean shift = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) Gdx.app.exit();
        if (Gdx.input.isKeyJustPressed(Input.Keys.TAB)) lab.cycle(shift ? -1 : 1);
        if (Gdx.input.isKeyJustPressed(Input.Keys.R)) load(lab.hull);
        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) lab.paused = !lab.paused;
        if (Gdx.input.isKeyJustPressed(Input.Keys.MINUS)) lab.changeSpeed(-1);
        if (Gdx.input.isKeyJustPressed(Input.Keys.EQUALS)) lab.changeSpeed(1);
        if (Gdx.input.isKeyJustPressed(Input.Keys.N)) {
            lab.pinned = !lab.pinned;
            message = lab.pinned ? "Ship pinned in place" : "Ship free to drift";
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.H)) {
            for (Entity e : lab.debug.crewAboard(lab.ship)) lab.debug.heal(e);
            note("Everyone healed");
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.F)) {
            lab.debug.extinguish(lab.ship);
            note("Fires out");
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.V)) {
            lab.debug.freshAir(lab.ship);
            note("Fresh air");
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.D)) note(lab.debug.toggleDoors(lab.ship) ? "Doors sealed" : "Doors open");
        if (Gdx.input.isKeyJustPressed(Input.Keys.O)) note(lab.debug.toggleReactors(lab.ship) ? "Reactors on" : "Reactors off");
        if (Gdx.input.isKeyJustPressed(Input.Keys.A)) {
            List<String> hulls = lab.hulls();
            if (shift) attackerHull++;
            if (lab.attacker != null && !shift) {
                lab.ceaseFire();
                message = "Attacker gone";
            } else {
                message = lab.attack(hulls.get(Math.floorMod(attackerHull + hulls.indexOf(GameScenario.RAIDER_SHIP), hulls.size())),
                        ATTACK_BEARING);
            }
        }
        for (Tool t : Tool.values()) {
            int key = t.ordinal() == 9 ? Input.Keys.NUM_0 : Input.Keys.NUM_1 + t.ordinal();
            if (Gdx.input.isKeyJustPressed(key)) tool = t;
        }
        int step = Gdx.input.isKeyJustPressed(Input.Keys.E) ? 1 : Gdx.input.isKeyJustPressed(Input.Keys.Q) ? -1 : 0;
        if (step != 0) {
            switch (tool) {
                case WOUND: woundAmount = Math.floorMod(woundAmount + step, WOUND_AMOUNTS.length); break;
                case WEAPON: weapon = Math.floorMod(weapon + step, lab.debug.weapons().size()); break;
                case BLAST: warhead = Math.floorMod(warhead + step, lab.debug.warheads().size()); break;
                case RAM: ramSpeed = Math.floorMod(ramSpeed + step, RAM_SPEEDS.length); break;
                case SPAWN: role = Math.floorMod(role + step, ROLES.length); break;
                case DAMAGE: damageAmount = Math.floorMod(damageAmount + step, DAMAGE_AMOUNTS.length); break;
                default: break;
            }
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.C)) {
            if (tool == Tool.WOUND) cause = (cause + 1) % CAUSES.length;
            if (tool == Tool.RAM) ramMass = (ramMass + 1) % RAM_MASSES.length;
            if (tool == Tool.SPAWN) boarder = !boarder;
        }
    }

    private boolean onDeck(int screenX) {
        return screenX < Gdx.graphics.getWidth() - PANEL_WIDTH;
    }

    /** The screen point in the ship's own frame, meters. */
    private Vector2 local(int screenX, int screenY, Vector2 out) {
        Vector3 w = camera.unproject(new Vector3(screenX, screenY, 0f));
        return out.set(w.x, w.y);
    }

    /** Tile coordinates of a ship-local point, as floats (a tile's middle is at +0.5). */
    private Vector2 tileCoords(Vector2 local, Vector2 out) {
        ShipGridComponent g = grid();
        return out.set((local.x - g.gridOriginLocal.x) / g.tileSize, (local.y - g.gridOriginLocal.y) / g.tileSize);
    }

    private void select(int screenX, int screenY) {
        Vector2 t = tileCoords(local(screenX, screenY, tmp), tmp);
        selected = lab.debug.crewAt(lab.ship, t.x, t.y, 0.8f);
    }

    /** Uses the tool at {@code at}, ship-local meters; {@code from} is where the drag started. */
    private void apply(Vector2 at, Vector2 from) {
        Vector2 t = tileCoords(at, new Vector2());
        int x = MathUtils.floor(t.x), y = MathUtils.floor(t.y);
        ShipGridComponent g = grid();
        boolean dragged = from.dst(at) > DRAG_TILES * g.tileSize;
        Vector2 fromWorld = WorldDebug.toWorld(body(), from.x, from.y, new Vector2());
        String result;
        Entity crew = lab.debug.crewAt(lab.ship, t.x, t.y, 0.8f);
        if (!g.inBounds(x, y) && tool != Tool.WOUND && tool != Tool.REMOVE) {
            message = "Off the deck";
            return;
        }
        switch (tool) {
            case WOUND:
                if (crew == null) {
                    result = "Click a crew member";
                    break;
                }
                lab.debug.hurt(crew, WOUND_AMOUNTS[woundAmount], CAUSES[cause]);
                selected = crew;
                result = String.format("%s: %.0f %s", lab.world.crew.crew(crew).name, WOUND_AMOUNTS[woundAmount],
                        cause(CAUSES[cause]));
                break;
            case WEAPON: {
                String id = lab.debug.weapons().get(weapon);
                result = dragged ? lab.debug.fire(lab.ship, id, x, y, fromWorld.x, fromWorld.y) : lab.debug.fire(lab.ship, id, x, y);
                break;
            }
            case BLAST:
                result = lab.debug.blast(lab.ship, lab.debug.warheads().get(warhead), x, y);
                break;
            case RAM:
                if (dragged) {
                    result = lab.debug.ram(lab.ship, x, y, fromWorld.x, fromWorld.y, RAM_MASSES[ramMass], RAM_SPEEDS[ramSpeed]);
                } else {
                    Vector2 c = WorldDebug.toWorld(body(), g.gridOriginLocal.x + g.width * g.tileSize / 2f,
                            g.gridOriginLocal.y + g.height * g.tileSize / 2f, new Vector2());
                    Vector2 a = lab.debug.tileWorld(lab.ship, x, y, new Vector2());
                    Vector2 out = a.cpy().sub(c);
                    if (out.isZero(1e-3f)) out.set(0f, 1f);
                    out.nor().add(a);
                    result = lab.debug.ram(lab.ship, x, y, out.x, out.y, RAM_MASSES[ramMass], RAM_SPEEDS[ramSpeed]);
                }
                break;
            case FIRE:
                result = lab.debug.ignite(lab.ship, x, y);
                break;
            case BREACH:
                result = lab.debug.breach(lab.ship, x, y);
                break;
            case SHORT:
                result = lab.debug.shortOut(lab.ship, x, y);
                break;
            case DAMAGE:
                result = lab.debug.damage(lab.ship, x, y, DAMAGE_AMOUNTS[damageAmount]);
                break;
            case SPAWN: {
                Entity e = boarder ? lab.debug.spawnCrew(lab.ship, ROLES[role], BOARDERS, x, y)
                        : lab.debug.spawnCrew(lab.ship, ROLES[role], x, y);
                result = e == null ? "Spawn on open deck" : lab.world.crew.crew(e).name + (boarder ? " boards" : " comes aboard");
                if (e != null) selected = e;
                break;
            }
            case REMOVE:
                if (crew == null) {
                    result = "Click a crew member";
                    break;
                }
                result = lab.world.crew.crew(crew).name + " taken off";
                lab.debug.removeCrew(crew);
                if (crew == selected) selected = null;
                break;
            default:
                result = "";
        }
        note(tool.title + " at " + x + "," + y + ": " + result);
    }

    /** Shows {@code text} on the status line and puts it in the account. */
    private void note(String text) {
        message = text;
        lab.watch.note(text);
    }

    private static String cause(DamageCause c) {
        return c.name().toLowerCase().replace('_', ' ');
    }

    // -----------------------------------------------------------------------
    // Drawing
    // -----------------------------------------------------------------------

    /**
     * Over the deck: heat, dull red from 350 K to bright at the ignition point; thin air as a blue
     * wash, open to space as near black; and walls marked as they are worn down.
     */
    private void drawWear(ShapeRenderer shapes, ShipGridComponent g) {
        float s = g.tileSize;
        for (int y = 0; y < g.height; y++) {
            for (int x = 0; x < g.width; x++) {
                Tile t = g.getTile(x, y);
                if (t.isVoid()) continue;
                Vector2 p = g.tileToLocal(x, y);
                float heat = (t.temperature - 350f) / (GridHazardSystem.IGNITION_TEMPERATURE - 350f);
                if (heat > 0f) {
                    shapes.setColor(1f, 0.25f, 0.05f, Math.min(0.6f, heat * 0.6f));
                    shapes.rect(p.x - s / 2f, p.y - s / 2f, s, s);
                }
                if (t.holdsAir()) {
                    float air = Math.min(1f, g.oxygenRatioAt(x, y));
                    if (air < 0.9f) {
                        shapes.setColor(0.1f, 0.15f, 0.55f, 0.55f * (1f - air));
                        shapes.rect(p.x - s / 2f, p.y - s / 2f, s, s);
                    }
                }
                if (t.isWall() && t.getIntegrity() < 1f) {
                    shapes.setColor(t.getIntegrity() <= 0f ? TileShade.BREACH : TileShade.wall(t.getIntegrity(), color));
                    float m = s * 0.35f;
                    shapes.rect(p.x - s / 2f + m, p.y - s / 2f + m, s - 2 * m, s - 2 * m);
                }
            }
        }
    }

    /** Rounds, missiles, rocks and beams in flight, and the attacker's bearing. */
    private void drawFlight() {
        Body body = body();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (Entity e : lab.world.engine.getEcsEngine().getEntitiesFor(PROJECTILES)) {
            ProjectileComponent p = e.getComponent(ProjectileComponent.class);
            boolean missile = e.getComponent(MissileComponent.class) != null;
            Vector2 a = WorldDebug.toLocal(body, p.pos.x, p.pos.y, tmp);
            float ax = a.x, ay = a.y;
            Vector2 b = WorldDebug.toLocal(body, p.pos.x - p.vel.x * 0.01f, p.pos.y - p.vel.y * 0.01f, tmp2);
            shapes.setColor(missile ? Color.ORANGE : Color.YELLOW);
            shapes.rectLine(ax, ay, b.x, b.y, missile ? 0.5f : 0.15f);
        }
        for (Entity e : lab.world.engine.getEcsEngine().getEntitiesFor(ROCKS)) {
            KinematicTransientComponent k = e.getComponent(KinematicTransientComponent.class);
            Vector2 a = WorldDebug.toLocal(body, k.x, k.y, tmp);
            shapes.setColor(Color.LIGHT_GRAY);
            shapes.circle(a.x, a.y, Math.max(k.radius, 0.4f), 12);
        }
        for (WorldDebug.Beam beam : lab.debug.beams()) {
            Vector2 to = lab.debug.tileWorld(beam.ship, beam.tileX, beam.tileY, new Vector2());
            Vector2 a = WorldDebug.toLocal(body, to.x, to.y, tmp);
            float ax = a.x, ay = a.y;
            Vector2 b = WorldDebug.toLocal(body, to.x - beam.dirX * 200f, to.y - beam.dirY * 200f, tmp2);
            shapes.setColor(1f, 0.2f, 0.2f, 0.8f);
            shapes.rectLine(ax, ay, b.x, b.y, 0.2f);
        }
        // A drag in progress: the line the shot will come in along
        if (pressedAt != null) {
            Vector2 now = local(Gdx.input.getX(), Gdx.input.getY(), tmp);
            if (pressedAt.dst(now) > DRAG_TILES * grid().tileSize) {
                shapes.setColor(1f, 1f, 1f, 0.5f);
                shapes.rectLine(pressedAt.x, pressedAt.y, now.x, now.y, 2f * zoom);
            }
        }
        shapes.end();
    }

    /**
     * The game's crew, hurt, down and dead as the game draws them, with what this demo adds beneath:
     * a white ring round the one followed and a red ring round boarders.
     */
    private DeckRenderer.Style crewStyle() {
        return new DeckRenderer.Style() {
            @Override
            public ArtNames.Crew figure(Entity crew, CrewMemberComponent c) {
                return c.faction != 0 ? ArtNames.Crew.STATION : ArtNames.Crew.SHIP;
            }

            @Override
            public Color dot(Entity crew, CrewMemberComponent c) {
                return c.faction != 0 ? DeckView.STATION_CREW : Color.GOLD;
            }

            @Override
            public void overDeck(ShapeRenderer shapes, Entity ship, ShipGridComponent g) {
                drawWear(shapes, g);
            }

            @Override
            public void underCrew(ShapeRenderer shapes, Entity e, CrewMemberComponent c, float x, float y,
                                  float tileSize, boolean figures) {
                float r = 0.32f * tileSize;
                if (e == selected) {
                    shapes.setColor(Color.WHITE);
                    shapes.circle(x, y, r * 1.45f, 20);
                }
                if (c.faction != 0) {
                    shapes.setColor(Color.RED);
                    shapes.circle(x, y, r * 1.2f, 20);
                }
            }
        };
    }

    /** Each name over its owner, with the bleed-out clock of the critical. */
    private void drawCrewLabels() {
        ShipGridComponent g = grid();
        List<Entity> aboard = lab.debug.crewAboard(lab.ship);
        batch.setProjectionMatrix(ui.combined);
        batch.begin();
        Vector3 v = new Vector3();
        for (Entity e : aboard) {
            CrewMemberComponent c = lab.world.crew.crew(e);
            Vector2 at = lab.world.crew.position(e).toShipLocal(g, tmp);
            camera.project(v.set(at.x, at.y + 0.45f * g.tileSize, 0f));
            font.setColor(Color.WHITE);
            font.draw(batch, c.name, v.x - 12f, v.y + 14f);
            if (c.wound == Wound.CRITICAL && c.state != CrewState.DEAD) {
                font.setColor(c.stabilised ? Color.CYAN : Color.RED);
                font.draw(batch, c.stabilised ? "stable" : String.format("%.0fs", c.bleedOut), v.x - 12f, v.y + 28f);
            }
        }
        batch.end();
        batch.setProjectionMatrix(camera.combined);
    }

    private void drawCursor() {
        Vector2 t = tileCoords(local(Gdx.input.getX(), Gdx.input.getY(), tmp), tmp);
        hoverX = MathUtils.floor(t.x);
        hoverY = MathUtils.floor(t.y);
        ShipGridComponent g = grid();
        if (!g.inBounds(hoverX, hoverY) || !onDeck(Gdx.input.getX())) return;
        Vector2 p = g.tileToLocal(hoverX, hoverY);
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(1f, 1f, 1f, 0.7f);
        shapes.rect(p.x - g.tileSize / 2f, p.y - g.tileSize / 2f, g.tileSize, g.tileSize);
        shapes.end();
    }

    private void drawPanels() {
        float w = Gdx.graphics.getWidth(), h = Gdx.graphics.getHeight();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.setProjectionMatrix(ui.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0f, 0f, 0f, 0.75f);
        shapes.rect(w - PANEL_WIDTH, 0f, PANEL_WIDTH, h);
        shapes.rect(0f, 0f, w - PANEL_WIDTH, LOG_HEIGHT);
        shapes.end();
        drawAttackerMarker(w, h);
        batch.setProjectionMatrix(ui.combined);
        batch.begin();
        float y = h - 10f;
        font.setColor(Color.WHITE);
        font.draw(batch, String.format("%s   t=%.1fs   x%s%s%s", name(lab.hull), lab.world.engine.getSimSeconds(),
                speed(), lab.paused ? "   PAUSED" : "", lab.pinned ? "" : "   drifting"), 10f, y);
        font.setColor(Color.LIGHT_GRAY);
        font.draw(batch, "Tab / Shift+Tab hull   R restart   A attacker (Shift+A next hull)   Space pause   - / = speed", 10f, y - LINE);
        font.draw(batch, "N pin   H heal all   F fires out   V fresh air   D doors   O reactors   wheel zoom   right click: follow", 10f, y - 2 * LINE);
        font.draw(batch, "1 wound  2 weapon  3 blast  4 ram  5 fire  6 breach  7 short  8 damage  9 spawn  0 remove", 10f, y - 3 * LINE);
        font.setColor(Color.GOLD);
        font.draw(batch, "> " + tool.title + toolSetting() + toolKeys(), 10f, y - 4 * LINE);
        font.setColor(Color.WHITE);
        font.draw(batch, message, 10f, y - 5 * LINE);
        font.setColor(Color.LIGHT_GRAY);
        font.draw(batch, hoverText(), 10f, LOG_HEIGHT + LINE);

        // The account of wounds and what the tools did, newest at the bottom
        List<WoundWatch.Line> lines = lab.watch.lines();
        int rows = (int) ((LOG_HEIGHT - 10f) / LINE);
        float ly = LOG_HEIGHT - 8f;
        for (int i = Math.max(0, lines.size() - rows); i < lines.size(); i++) {
            WoundWatch.Line l = lines.get(i);
            font.setColor(l.crew != null && l.crew == selected ? Color.GOLD : Color.WHITE);
            font.draw(batch, l.toString(), 10f, ly);
            ly -= LINE;
        }
        drawCrewPanel(w - PANEL_WIDTH + 10f, h - 10f);
        batch.end();
    }

    private String speed() {
        return lab.speed >= 1f ? String.format("%.0f", lab.speed) : String.format("%.2f", lab.speed);
    }

    private String toolSetting() {
        switch (tool) {
            case WOUND: return String.format(": %.0f %s", WOUND_AMOUNTS[woundAmount], cause(CAUSES[cause]));
            case WEAPON: return ": " + lab.debug.weapons().get(weapon);
            case BLAST: return ": " + lab.debug.warheads().get(warhead);
            case RAM: return String.format(": %.0f kg at %.1f km/s", RAM_MASSES[ramMass], RAM_SPEEDS[ramSpeed] / 1000f);
            case SPAWN: return ": " + ROLES[role].name().toLowerCase() + (boarder ? ", boarder" : "");
            case DAMAGE: return String.format(": %.0f%%", DAMAGE_AMOUNTS[damageAmount] * 100f);
            default: return "";
        }
    }

    /** The keys that change the tool's settings, and how to aim it. */
    private String toolKeys() {
        switch (tool) {
            case WOUND: return "   (Q/E amount, C cause; click someone)";
            case WEAPON: return "   (Q/E weapon; drag in from outside to aim)";
            case BLAST: return "   (Q/E warhead)";
            case RAM: return "   (Q/E speed, C mass; drag in from outside to aim)";
            case SPAWN: return "   (Q/E role, C crew or boarder)";
            case DAMAGE: return "   (Q/E amount)";
            default: return "";
        }
    }

    /** What is on the tile under the cursor. */
    private String hoverText() {
        ShipGridComponent g = grid();
        if (!g.inBounds(hoverX, hoverY)) return "";
        Tile t = g.getTile(hoverX, hoverY);
        if (t.isVoid()) return hoverX + "," + hoverY + "  space";
        StringBuilder b = new StringBuilder();
        b.append(hoverX).append(',').append(hoverY).append("  ")
                .append(t.isWall() ? (t.isHullBreach() ? "hull breach" : "wall") : t.isFloor() ? "deck" : "open")
                .append(String.format("  %.0f%%  %.0f K", t.getIntegrity() * 100f, t.temperature));
        if (t.holdsAir()) b.append(String.format("  O2 %.0f%%", g.oxygenRatioAt(hoverX, hoverY) * 100f));
        if (t.isBurning()) b.append("  BURNING");
        if (t.isFlammable()) b.append("  flammable");
        if (t.mount != null && t.mount.installed != null) {
            SubsystemComponent s = t.mount.installed.getComponent(SubsystemComponent.class);
            if (s != null) b.append(String.format("  %s %.0f%%%s%s", s.type, s.health * 100f, s.powered ? "" : " unpowered",
                    s.shorted ? " shorted" : ""));
        }
        return b.toString();
    }

    private void drawCrewPanel(float x, float y) {
        font.setColor(Color.WHITE);
        font.draw(batch, "Crew aboard", x, y);
        y -= LINE * 1.6f;
        for (Entity e : lab.debug.crewAboard(lab.ship)) {
            CrewMemberComponent c = lab.world.crew.crew(e);
            font.setColor(e == selected ? Color.GOLD : c.faction != 0 ? Color.SALMON : Color.WHITE);
            String clock = c.wound != Wound.CRITICAL || c.state == CrewState.DEAD ? ""
                    : c.stabilised ? "  stabilised" : String.format("  bleeds out %.0fs", c.bleedOut);
            int cuts = lab.watch.hitsWhileCritical(e);
            font.draw(batch, String.format("%-6s %-8s %s%s%s", c.name, c.role.name().toLowerCase(),
                    c.state == CrewState.DEAD ? "dead" : c.wound.label.toLowerCase(), clock,
                    cuts > 0 ? "  (cut " + cuts + "x)" : ""), x, y);
            y -= LINE;
            if (c.state != CrewState.DEAD) {
                font.setColor(Color.GRAY);
                font.draw(batch, String.format("   %.0f hp  injury %.0f  %s", c.health, c.injury, c.activity), x, y);
                y -= LINE;
            }
            if (y < 220f) break;
        }
        if (selected == null) {
            font.setColor(Color.GRAY);
            font.draw(batch, "Right click someone to follow them", x, 200f);
            return;
        }
        CrewMemberComponent c = lab.world.crew.crew(selected);
        float sy = 200f;
        font.setColor(Color.GOLD);
        font.draw(batch, c.name + " (" + c.role.name().toLowerCase() + ")", x, sy);
        font.setColor(Color.WHITE);
        sy -= LINE;
        font.draw(batch, String.format("%s, %s, health %.0f/%.0f", c.state.name().toLowerCase(), c.wound.label.toLowerCase(),
                c.health, c.def.maxHealth), x, sy);
        sy -= LINE;
        font.draw(batch, String.format("injury %.0f  (serious %.0f, critical %.0f, lethal %.0f)", c.injury,
                c.def.seriousWound, c.def.criticalWound, c.def.lethalWound), x, sy);
        sy -= LINE;
        if (c.wound == Wound.CRITICAL) {
            font.draw(batch, String.format("bleed-out %.1f of %.0f s%s", c.bleedOut, c.def.bleedOutTime,
                    c.stabilised ? ", stabilised" : ""), x, sy);
            sy -= LINE;
        }
        if (c.wound != Wound.NONE) {
            font.draw(batch, String.format("care %.0f of %.0f s to the next level", c.care, CrewDef.careTime(c.wound)), x, sy);
            sy -= LINE;
        }
        font.draw(batch, "doing: " + c.activity + (c.task != null ? " (" + c.task + ")" : ""), x, sy);
        sy -= LINE;
        if (c.treatedBy != null) font.draw(batch, "treated by " + lab.world.crew.crew(c.treatedBy).name, x, sy);
        else if (c.carriedBy != null) font.draw(batch, "carried by " + lab.world.crew.crew(c.carriedBy).name, x, sy);
        else if (c.carrying != null) font.draw(batch, "carrying " + lab.world.crew.crew(c.carrying).name, x, sy);
        sy -= LINE;
        font.draw(batch, c.suited ? String.format("suited, %.0f s of air", c.suitAir) : "no suit", x, sy);
    }

    /** An arrow at the edge of the deck view pointing at the attacker, with its name and range. */
    private void drawAttackerMarker(float w, float h) {
        if (lab.attacker == null) return;
        Body a = lab.world.docking.bodyOf(lab.attacker);
        if (a == null) return;
        Vector2 at = PhysicsManager.getPosition(a, new Vector2());
        Vector2 local = WorldDebug.toLocal(body(), at.x, at.y, new Vector2());
        Vector3 s = camera.project(new Vector3(local.x, local.y, 0f));
        float cx = (w - PANEL_WIDTH) / 2f, cy = (h - TOP + LOG_HEIGHT) / 2f;
        float dx = s.x - cx, dy = s.y - cy, len = (float) Math.hypot(dx, dy);
        if (len < 1f) return;
        float r = Math.min((w - PANEL_WIDTH) / 2f, (h - LOG_HEIGHT - TOP) / 2f) - 30f;
        float px = cx + dx / len * r, py = cy + dy / len * r;
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(Color.RED);
        shapes.triangle(px + dx / len * 14f, py + dy / len * 14f, px - dy / len * 8f, py + dx / len * 8f,
                px + dy / len * 8f, py - dx / len * 8f);
        shapes.end();
        batch.setProjectionMatrix(ui.combined);
        batch.begin();
        font.setColor(Color.SALMON);
        font.draw(batch, String.format("%s %.1f km", name(lab.attackerHull), local.len() / 1000f), px - 40f, py - 12f);
        batch.end();
    }

    @Override
    public void dispose() {
        lab.dispose();
        stage.dispose();
        skin.dispose();
        shapes.dispose();
        batch.dispose();
        font.dispose();
        if (art != null) art.dispose();
    }
}

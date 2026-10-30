package com.hawkwing.RingsOfIron.app;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.hawkwing.RingsOfIron.debug.BuiltInOverlays;
import com.hawkwing.RingsOfIron.debug.DebugMenu;
import com.hawkwing.RingsOfIron.debug.DebugNames;
import com.hawkwing.RingsOfIron.debug.DebugScenario;
import com.hawkwing.RingsOfIron.debug.DebugScenarios;
import com.hawkwing.RingsOfIron.debug.DevConsole;
import com.hawkwing.RingsOfIron.debug.OverlayView;
import com.hawkwing.RingsOfIron.debug.ScenarioPointer;
import com.hawkwing.RingsOfIron.debug.ScenarioSetup;
import com.hawkwing.RingsOfIron.game.GameWorld;
import com.hawkwing.RingsOfIron.render.DeckEdgeBlend;
import com.hawkwing.RingsOfIron.render.HeatOverlay;
import com.hawkwing.RingsOfIron.render.HullShading;
import com.hawkwing.RingsOfIron.render.SpaceSnow;
import com.hawkwing.RingsOfIron.render.StarField;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.uihud.UiHud;
import com.ringsofiron.uihud.ViewMode;
import com.ringsofiron.uihud.ViewportManager;
import com.ringsofiron.uihud.widgets.DevConsoleWindow;

import java.nio.file.Path;
import java.util.ArrayList;
import java.nio.file.Paths;
import java.util.function.BooleanSupplier;

/**
 * The developer's tools over the game: the console (~), Groovy against the world acting on what was
 * clicked, which can stop the world and step it; the debug menu (F3) with its scenarios and
 * overlays, drawn over the world and the HUD; the ring round the selection; and the loaded
 * scenario's own use of the mouse.
 */
public final class DevTools {
    private final GameSession session;
    private final GameWorld world;
    private final UiHud hud;
    private final ViewportManager viewports;
    private final RenderPipeline pipeline;
    private final DevConsole dev;
    private final DevConsoleWindow window;
    private final DebugMenu menu;
    private final BuiltInOverlays builtInOverlays = new BuiltInOverlays();
    private final OverlayView overlayView = new OverlayView();
    /** The scenario this game is, made from {@link GameOptions#scenarioEntry}; null in a career. */
    private final DebugScenario scenario;
    /** The console has stopped the world ({@code pause()}); {@link #step} seconds are still to run. */
    private boolean frozen;
    private float step;
    /** The loaded scenario's use of the mouse, or null; {@link #scenarioPressed} after a press it took. */
    private ScenarioPointer scenarioPointer;
    private boolean scenarioPressed;
    private final Vector2 tmp = new Vector2();

    /** {@code refitOpen}: the ship editor is up, and has the keys that would open the console or menu. */
    DevTools(GameSession session, RenderPipeline pipeline, DebugScenario scenario, BooleanSupplier refitOpen) {
        this.session = session;
        this.world = session.world;
        this.hud = session.hud;
        this.viewports = session.viewports;
        this.pipeline = pipeline;
        this.scenario = scenario;
        Path history = Paths.get("saves", "console-history.txt").toAbsolutePath();
        dev = new DevConsole(world, session.game, null, history);
        dev.setHost(new DevConsole.Host() {
            @Override
            public void setFrozen(boolean frozen) {
                DevTools.this.frozen = frozen;
                step = 0f;
            }

            @Override
            public boolean isFrozen() {
                return frozen;
            }

            @Override
            public void step(float seconds) {
                step += seconds;
            }
        });
        dev.addOverlay(HeatOverlay.NAME, HeatOverlay.DESCRIPTION,
                pipeline.deckRenderer()::heatShown, pipeline.deckRenderer()::setHeatShown);
        builtInOverlays.install(dev.overlays);
        DeckEdgeBlend deckEdge = pipeline.deckEdge();
        dev.addOverlay(DeckEdgeBlend.NAME, DeckEdgeBlend.DESCRIPTION, deckEdge::maskShown, deckEdge::setMaskShown);
        SpaceSnow snow = pipeline.snow();
        dev.addOverlay(SpaceSnow.NAME, SpaceSnow.DESCRIPTION, snow::isShown, snow::setShown);
        StarField stars = pipeline.stars();
        dev.addOverlay(StarField.NAME, StarField.DESCRIPTION, stars::isShown, stars::setShown);
        dev.setScenarioLoader(this::loadScenario);
        DebugScenarios.Entry entry = session.options.scenarioEntry;
        menu = new DebugMenu(hud.getSkin(), dev, () -> entry == null ? null : entry.name);
        dev.setMenuToggle(() -> menu.toggle(hud.getStage()));
        window = new DevConsoleWindow(hud.getSkin(), dev.console,
                () -> (frozen ? "[stopped] " : "") + DebugNames.describe(dev.selected()));
        // ~ opens and closes it; with it open, Esc lets go of the selection, then closes it
        keys = new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.GRAVE && !refitOpen.getAsBoolean()) {
                    window.toggle(hud.getStage());
                    return true;
                }
                if (keycode == Input.Keys.ESCAPE && window.isOpen()) {
                    if (!dev.deselect()) window.setOpen(false, hud.getStage());
                    return true;
                }
                if (keycode == Input.Keys.F3 && !refitOpen.getAsBoolean()) {
                    menu.toggle(hud.getStage());
                    return true;
                }
                if (keycode == Input.Keys.F7) {
                    // Prototype: how the hulls are lit, to compare the baked art with the game's lighting
                    HullShading.mode = HullShading.mode.next();
                    session.show(HullShading.mode.label());
                    return true;
                }
                if (keycode == Input.Keys.ESCAPE && menu.isOpen()) {
                    menu.setOpen(false, hud.getStage());
                    return true;
                }
                return false;
            }
        };
    }

    /** The console's and menu's keys, ahead of everything else. */
    final InputProcessor keys;

    /** A click on the game (not on a window) selects what is under it, again for the next thing there. */
    final InputProcessor clicks = new InputAdapter() {
        @Override
        public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            if (!window.isOpen() || button != Input.Buttons.LEFT) return false;
            Vector2 at = viewports.getWorldViewport().unproject(tmp.set(screenX, screenY));
            boolean deck = session.deckView();
            dev.click(at.x, at.y, deck, viewports.metersPerPixel(), deck ? new ArrayList<>(pipeline.decks()) : null);
            return true;
        }
    };

    /** Clicks on the game (not a window) for the scenario's pointer, while the developer console is closed. */
    final InputProcessor scenarioClicks = new InputAdapter() {
        @Override
        public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            if (scenarioPointer == null || isConsoleOpen()) return false;
            Vector2 at = viewports.getWorldViewport().unproject(tmp.set(screenX, screenY));
            scenarioPressed = scenarioPointer.down(at.x, at.y, button, session.deckView());
            return scenarioPressed;
        }

        @Override
        public boolean touchDragged(int screenX, int screenY, int pointer) {
            if (!scenarioPressed || scenarioPointer == null) return false;
            Vector2 at = viewports.getWorldViewport().unproject(tmp.set(screenX, screenY));
            scenarioPointer.dragged(at.x, at.y);
            return true;
        }

        @Override
        public boolean touchUp(int screenX, int screenY, int pointer, int button) {
            if (!scenarioPressed || scenarioPointer == null) return false;
            scenarioPressed = false;
            Vector2 at = viewports.getWorldViewport().unproject(tmp.set(screenX, screenY));
            scenarioPointer.up(at.x, at.y, button);
            return true;
        }
    };

    public DevConsole console() {
        return dev;
    }

    public DebugMenu menu() {
        return menu;
    }

    /** The scenario this game is, or null in a career. */
    public DebugScenario scenario() {
        return scenario;
    }

    /** The developer console is open over the game. */
    public boolean isConsoleOpen() {
        return window.isOpen();
    }

    /** The developer console has the keyboard: keys are text, not the player's bindings. */
    public boolean isTyping() {
        return window.isTyping();
    }

    /** The debug menu (F3) is open over the game. */
    public boolean isMenuOpen() {
        return menu.isOpen();
    }

    /** True while the console has stopped the world. */
    boolean isFrozen() {
        return frozen;
    }

    /**
     * Of {@code delta} seconds, how many the world runs this frame: stopped from the console, only
     * the seconds step() asks for.
     */
    float run(float delta, boolean paused) {
        if (frozen && !paused) {
            delta = Math.min(step, delta);
            step -= delta;
        }
        return delta;
    }

    /** Throws this game away for a new one set up as {@code entry}, or a new career for null. */
    public void loadScenario(DebugScenarios.Entry entry) {
        if (session.options.scenarioLoader == null) {
            session.show("Scenarios load from the main menu's game (gradlew :lwjgl3:runGame)");
            return;
        }
        session.options.scenarioLoader.accept(entry);
    }

    /** Sets the world up as the scenario asks, in place of a career. */
    void setUpScenario() {
        ScenarioSetup setup = new ScenarioSetup(session.options.scenarioEntry, world, session.game, session.options.assetsDir,
                dev, dev.overlays, dev.menuActions,
                new ScenarioSetup.Screen() {
                    @Override
                    public void setDeckView(boolean deck) {
                        hud.getViewMode().setMode(deck ? ViewMode.INTERIOR : ViewMode.EXTERIOR);
                    }

                    @Override
                    public void playerShipChanged() {
                        session.rearm();
                    }

                    @Override
                    public void setPointer(ScenarioPointer pointer) {
                        scenarioPointer = pointer;
                        scenarioPressed = false;
                    }
                });
        session.matchViewToSeat();
        scenario.setUp(setup);
    }

    /** The scenario's own step, {@code delta} seconds on. */
    void updateScenario(float delta) {
        if (scenario != null) scenario.update(delta);
    }

    /** The overlays toggled on: their world half over the world ({@code inWorld}), else their screen half over the HUD. */
    void drawOverlays(float delta, boolean inWorld) {
        OverlayView v = overlayView;
        v.world = world;
        v.game = session.game;
        v.shapes = pipeline.shapes();
        v.batch = pipeline.batch();
        v.font = pipeline.font();
        v.delta = delta;
        v.deckView = session.deckView();
        v.metersPerPixel = viewports.metersPerPixel();
        v.decks = pipeline.decks();
        v.uiWidth = viewports.getUiViewport().getWorldWidth();
        v.uiHeight = viewports.getUiViewport().getWorldHeight();
        if (v.projection == null) v.projection = viewports::worldToUi;
        ShapeRenderer shapes = pipeline.shapes();
        if (inWorld) {
            viewports.applyWorld();
            OrthographicCamera cam = viewports.getWorldCamera();
            float w = cam.viewportWidth * cam.zoom, h = cam.viewportHeight * cam.zoom;
            v.worldBounds.set(cam.position.x - w / 2f, cam.position.y - h / 2f, w, h);
            v.worldMatrix.set(cam.combined);
            shapes.setProjectionMatrix(cam.combined);
            pipeline.batch().setProjectionMatrix(cam.combined);
            dev.overlays.drawWorld(v);
        } else {
            viewports.applyUi();
            shapes.setProjectionMatrix(viewports.getUiCamera().combined);
            pipeline.batch().setProjectionMatrix(viewports.getUiCamera().combined);
            dev.overlays.drawScreen(v);
        }
    }

    /** A ring round what the developer console has selected, at least a few pixels across. */
    void drawSelection() {
        if (dev.selected() == null) return;
        float r = dev.picker.locate(dev.selected(), tmp);
        if (r < 0f) return;
        float px = viewports.metersPerPixel();
        r = Math.max(r, 6f * px);
        ShapeRenderer shapes = pipeline.shapes();
        viewports.applyWorld();
        shapes.setProjectionMatrix(viewports.getWorldCamera().combined);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(1f, 0.3f, 1f, window.isOpen() ? 1f : 0.5f);
        if (dev.selected().isTile()) {
            ShipGridComponent g = dev.selected().grid();
            Body body = world.docking.bodyOf(dev.selected().ship);
            float s = g.tileSize * 0.5f, a = body.getAngle();
            float cos = MathUtils.cos(a), sin = MathUtils.sin(a);
            // The tile's square, turned with its ship
            for (int i = 0; i < 4; i++) {
                float x0 = i == 0 || i == 3 ? -s : s, y0 = i < 2 ? -s : s;
                float x1 = i == 2 || i == 3 ? -s : s, y1 = i == 0 || i == 3 ? -s : s;
                shapes.line(tmp.x + cos * x0 - sin * y0, tmp.y + sin * x0 + cos * y0,
                        tmp.x + cos * x1 - sin * y1, tmp.y + sin * x1 + cos * y1);
            }
        } else {
            shapes.circle(tmp.x, tmp.y, r * 1.3f, 32);
            shapes.circle(tmp.x, tmp.y, r * 1.3f + 2f * px, 32);
        }
        shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    void dispose() {
        if (scenario != null) scenario.dispose();
        builtInOverlays.dispose();
    }
}

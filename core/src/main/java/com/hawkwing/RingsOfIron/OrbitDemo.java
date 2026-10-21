package com.hawkwing.RingsOfIron;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.hawkwing.RingsOfIron.game.Navigation;
import com.hawkwing.RingsOfIron.game.NoticeLog;
import com.hawkwing.RingsOfIron.game.OrbitDemoScene;
import com.hawkwing.RingsOfIron.game.PlayerOrbitPath;
import com.hawkwing.RingsOfIron.game.WarpNotices;
import com.ringsofiron.orbital.components.CelestialBodyComponent;
import com.ringsofiron.orbital.components.OrbitComponent;
import com.ringsofiron.orbital.math.StateVector;
import com.ringsofiron.uihud.HudSkin;
import com.ringsofiron.uihud.providers.OrbitInfoProvider;
import com.ringsofiron.uihud.providers.OrbitPathProvider;
import com.ringsofiron.uihud.widgets.OrbitMap;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * Orbit demo: the game's rails at real scale. Earth, the Moon, the player's corvette in low orbit and
 * four craft on the rails, in the game's own {@link com.hawkwing.RingsOfIron.game.GameWorld}
 * ({@link OrbitDemoScene}), with the game's time warp, sphere-of-influence changes and warp
 * drop-outs. Run with {@code gradlew :lwjgl3:runOrbitDemo}, or {@code demos/Orbit demo.bat} on Windows.
 *
 * <p>Drawn with the game's orbit map ({@link OrbitMap} filled by {@link PlayerOrbitPath}), here
 * filling the screen with a zoom and a camera focus: the player's orbit, its planned burns and the
 * orbit after them, the body it orbits and that body's moons. Over it, demo-only: every sphere of
 * influence, and each other craft's orbit, position and name.
 *
 * Controls:
 *   , / .        - Warp down / up (1x to 10x is physics warp, 50x and up is rails warp)
 *   W / S        - Burn 10 m/s prograde / retrograde (hold Shift for 100 m/s)
 *   A / D        - Burn 10 m/s radial in / out
 *   T            - Plan a transfer to the Moon from a near-circular orbit
 *   F            - Cycle camera focus: player, Earth, Moon
 *   Mouse wheel  - Zoom
 *   R            - Reset
 *   ESC          - Exit
 */
public class OrbitDemo extends ApplicationAdapter {
    private static final String[] FOCUS = {"you", "Earth", "Moon"};
    /** The other craft: apart from the map's own colours (cyan now, amber planned, magenta target, green proposed). */
    private static final Color TRAFFIC = new Color(0.72f, 0.62f, 0.95f, 1f);

    private final Path assetsDir;
    private OrbitDemoScene demo;

    private Stage stage;
    private Skin skin;
    private OrbitMap map;
    private ShapeRenderer shapes;
    private BitmapFont font;
    /** Meters per screen pixel. */
    private double scale = 20_000;
    private int focusIndex;
    private final StateVector tmp = new StateVector();
    private final FloatArray points = new FloatArray();

    public OrbitDemo() {
        this(Paths.get("").toAbsolutePath());
    }

    public OrbitDemo(Path assetsDir) {
        this.assetsDir = assetsDir;
    }

    @Override
    public void create() {
        shapes = new ShapeRenderer();
        skin = HudSkin.create();
        font = skin.getFont("default-font");
        stage = new Stage(new ScreenViewport());
        map = new OrbitMap(skin.getRegion("white"));
        map.setFont(font);
        map.setFillParent(true);
        stage.addActor(map);

        demo = new OrbitDemoScene(assetsDir);
        demo.load();
        map.setProvider(demo.path);

        Gdx.input.setInputProcessor(new InputMultiplexer(new InputAdapter() {
            @Override
            public boolean scrolled(float amountX, float amountY) {
                scale = Math.max(1, Math.min(5e6, scale * Math.pow(1.2, amountY)));
                return true;
            }

            @Override
            public boolean keyDown(int keycode) {
                onKey(keycode);
                return true;
            }
        }, stage));
    }

    private void onKey(int keycode) {
        boolean big = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
        double dv = big ? 100 : 10;
        switch (keycode) {
            case Input.Keys.PERIOD: WarpNotices.step(demo.world, 1); break;
            case Input.Keys.COMMA: WarpNotices.step(demo.world, -1); break;
            case Input.Keys.W: demo.burn(dv, 0); break;
            case Input.Keys.S: demo.burn(-dv, 0); break;
            case Input.Keys.D: demo.burn(0, dv); break;
            case Input.Keys.A: demo.burn(0, -dv); break;
            case Input.Keys.T:
                if (!demo.lunarTransfer(demo.player)) {
                    demo.world.notices.post("A transfer needs a near-circular Earth orbit well below the Moon");
                }
                break;
            case Input.Keys.F: focusIndex = (focusIndex + 1) % FOCUS.length; break;
            case Input.Keys.R:
                demo.load();
                map.setProvider(demo.path);
                break;
            case Input.Keys.ESCAPE: Gdx.app.exit(); break;
            default: break;
        }
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void render() {
        float delta = Math.min(Gdx.graphics.getDeltaTime(), 0.1f);
        demo.update(delta);

        Gdx.gl.glClearColor(0.02f, 0.02f, 0.05f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        // The game's map fills the frame; then the view is set about the focus in the frame it drew
        stage.act(delta);
        OrbitPathProvider.OrbitPath path = map.getPath();
        Entity frame = demo.path.frame();
        if (map.hasPath() && frame != null) {
            double fx = path.shipX, fy = path.shipY;
            Entity body = focusIndex == 1 ? demo.earth : focusIndex == 2 ? demo.moon : null;
            if (body != null) {
                demo.world.universe.stateInFrame(body, frame, demo.now(), tmp);
                fx = tmp.x;
                fy = tmp.y;
            }
            map.setView(scale, fx, fy);
        }
        stage.draw();
        if (map.hasPath() && frame != null) drawTraffic(frame);
        drawText();
    }

    /** Demo-only, over the game's map: spheres of influence, and the other craft with their orbits. */
    private void drawTraffic(Entity frame) {
        double now = demo.now();
        shapes.setProjectionMatrix(stage.getCamera().combined);
        shapes.begin(ShapeRenderer.ShapeType.Line);
        for (Entity body : new Entity[]{demo.earth, demo.moon}) {
            CelestialBodyComponent c = demo.world.universe.celestial(body);
            if (Double.isInfinite(c.soiRadius)) continue;
            demo.world.universe.stateInFrame(body, frame, now, tmp);
            shapes.setColor(0.3f, 0.3f, 0.5f, 1f);
            shapes.circle(map.screenX(tmp.x), map.screenY(tmp.y), (float) (c.soiRadius * map.getDrawScale()), 96);
        }
        for (Entity ship : demo.traffic) {
            OrbitComponent orbit = demo.world.universe.orbit(ship);
            Entity parent = demo.world.universe.parentOf(ship);
            if (orbit == null || parent == null) continue;
            demo.world.universe.stateInFrame(parent, frame, now, tmp);
            double ox = tmp.x, oy = tmp.y;
            points.clear();
            PlayerOrbitPath.sample(orbit.getOrbit(), now, demo.world.universe.celestial(parent), points);
            shapes.setColor(TRAFFIC.r, TRAFFIC.g, TRAFFIC.b, 0.55f);
            for (int i = 2; i + 1 < points.size; i += 2) {
                shapes.line(map.screenX(ox + points.get(i - 2)), map.screenY(oy + points.get(i - 1)),
                        map.screenX(ox + points.get(i)), map.screenY(oy + points.get(i + 1)));
            }
        }
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(TRAFFIC);
        for (Entity ship : demo.traffic) {
            demo.world.universe.stateInFrame(ship, frame, now, tmp);
            shapes.circle(map.screenX(tmp.x), map.screenY(tmp.y), 3f, 12);
        }
        shapes.end();

        stage.getBatch().begin();
        font.setColor(TRAFFIC);
        for (Entity ship : demo.traffic) {
            demo.world.universe.stateInFrame(ship, frame, now, tmp);
            font.draw(stage.getBatch(), demo.name(ship), map.screenX(tmp.x) + 6f, map.screenY(tmp.y) + 4f);
        }
        stage.getBatch().end();
    }

    private void drawText() {
        stage.getBatch().begin();
        float y = stage.getViewport().getWorldHeight() - 12;
        font.setColor(0.85f, 0.9f, 1f, 1f);
        for (String line : hudLines()) {
            font.draw(stage.getBatch(), line, 12, y);
            y -= 18;
        }
        y = 30;
        for (NoticeLog.Notice n : demo.world.notices.showing()) {
            font.draw(stage.getBatch(), n.label(), 12, y);
            y += 18;
        }
        stage.getBatch().end();
    }

    private String[] hudLines() {
        double t = demo.now();
        String mode = demo.world.timeWarp.isRailsWarp() ? "rails" : "physics";
        String clock = String.format(Locale.ROOT, "T+ %s    warp %,dx (%s)    focus: %s", Navigation.formatTime(t),
                demo.world.timeWarp.getWarp(), mode, FOCUS[focusIndex]);
        String keys = "[,/.] warp  [W/S] prograde/retro  [A/D] radial  [Shift] x10  [T] Moon transfer  [F] focus  [wheel] zoom  [R] reset";
        if (demo.world.getCrash() != null) {
            return new String[]{clock, "You hit " + demo.world.getCrash().bodyName + ". R starts again.", keys};
        }
        OrbitInfoProvider.OrbitInfo o = demo.orbitInfo.getPlayerOrbit();
        if (o == null) return new String[]{clock, "No orbit", keys};
        String apo = Double.isInfinite(o.apoapsis) ? "escape" : km(o.apoapsis);
        return new String[]{
                clock,
                String.format(Locale.ROOT, "Orbiting %s   alt %s   speed %,.0f m/s%s", o.bodyName, km(o.altitude),
                        demo.orbitInfo.state().speed(), o.impactWarning ? "   IMPACT" : ""),
                String.format(Locale.ROOT, "Periapsis %s   apoapsis %s   period %s", km(o.periapsis), apo,
                        Double.isInfinite(o.period) ? "-" : Navigation.formatTime(o.period)),
                keys,
        };
    }

    private static String km(double meters) {
        return String.format(Locale.ROOT, "%,.0f km", meters / 1000);
    }

    @Override
    public void dispose() {
        demo.dispose();
        stage.dispose();
        skin.dispose();
        shapes.dispose();
    }
}

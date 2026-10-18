package com.hawkwing.RingsOfIron;

import com.badlogic.ashley.core.Component;
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
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.coreengine.GameEngine;
import com.ringsofiron.coreengine.components.UuidComponent;
import com.ringsofiron.orbital.components.CelestialBodyComponent;
import com.ringsofiron.orbital.components.ManeuverNodeComponent;
import com.ringsofiron.orbital.components.OrbitComponent;
import com.ringsofiron.orbital.events.OrbitalEvent;
import com.ringsofiron.orbital.events.OrbitalEventQueue;
import com.ringsofiron.orbital.events.OrbitalEventSystem;
import com.ringsofiron.orbital.math.KeplerOrbit;
import com.ringsofiron.orbital.math.OrbitMath;
import com.ringsofiron.orbital.math.StateVector;
import com.ringsofiron.orbital.rails.RailsUniverse;
import com.ringsofiron.orbital.warp.BubbleHandoff;
import com.ringsofiron.orbital.warp.EncounterPredictor;
import com.ringsofiron.orbital.warp.TimeWarpController;

import java.util.Locale;

/**
 * Interactive demo for the orbital-mechanics module: Earth, the Moon and a few ships on rails at
 * real scale, with time warp, SOI transitions and warp drop-out. Time comes from core-engine's
 * {@link GameEngine} clock. There is no Box2D bubble here;
 * every body, the player included, is on rails, and burns are instant (impulsive).
 *
 * Controls:
 *   , / .        - Warp down / up (1x to 10x is physics warp, 50x and up is rails warp)
 *   W / S        - Burn 10 m/s prograde / retrograde (hold Shift for 100 m/s)
 *   A / D        - Burn 10 m/s radial in / out
 *   T            - Schedule a transfer to the Moon from a near-circular orbit
 *   F            - Cycle camera focus: player, Earth, Moon
 *   Mouse wheel  - Zoom
 *   R            - Reset
 *   ESC          - Exit
 */
public class OrbitDemo extends ApplicationAdapter {
    private static final double MU_EARTH = 3.986004418e14;
    private static final double R_EARTH = 6.371e6;
    private static final double MU_MOON = 4.9048695e12;
    private static final double R_MOON = 1.7374e6;
    private static final double MOON_A = 3.844e8;
    private static final double LEO = R_EARTH + 400e3;
    private static final int ORBIT_SEGMENTS = 256;

    // --- Simulation ---
    private GameEngine game;
    private RailsUniverse universe;
    private OrbitalEventSystem events;
    private TimeWarpController warp;
    private Entity earth;
    private Entity moon;
    private Entity player;
    private final Array<Entity> ships = new Array<>();

    // --- Rendering ---
    private OrthographicCamera camera;
    private OrthographicCamera hudCamera;
    private ShapeRenderer shapes;
    private SpriteBatch batch;
    private BitmapFont font;
    /** Meters per screen pixel. */
    private double scale = 20_000;
    private int focusIndex;
    private final StateVector focus = new StateVector();
    private final StateVector tmp = new StateVector();
    private final StateVector tmp2 = new StateVector();
    private String message = "";
    private float messageTimer;

    @Override
    public void create() {
        camera = new OrthographicCamera();
        hudCamera = new OrthographicCamera();
        shapes = new ShapeRenderer();
        batch = new SpriteBatch();
        font = new BitmapFont();

        Gdx.input.setInputProcessor(new InputAdapter() {
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
        });
        buildScene();
    }

    private void buildScene() {
        ships.clear();
        game = new GameEngine();
        universe = new RailsUniverse(game);
        events = new OrbitalEventSystem(universe, new OrbitalEventQueue());
        game.addSystem(events);

        earth = add(new UuidComponent("earth"), new CelestialBodyComponent("Earth", MU_EARTH, R_EARTH, Double.POSITIVE_INFINITY));
        moon = add(new UuidComponent("moon"),
                new CelestialBodyComponent("Moon", MU_MOON, R_MOON, OrbitMath.soiRadius(MOON_A, MU_MOON, MU_EARTH)),
                new OrbitComponent("earth", KeplerOrbit.circular(MU_EARTH, MOON_A, 2.0, 0, 1)));

        player = ship("player", KeplerOrbit.circular(MU_EARTH, LEO, 0, 0, 1));
        // Slightly lower, a little behind: catches up and triggers an encounter drop-out after a few hours.
        ship("Courier", KeplerOrbit.circular(MU_EARTH, LEO - 3_000, -0.15, 0, 1));
        // Eccentric orbit out to 40,000 km.
        ship("Tug", KeplerOrbit.fromApsides(MU_EARTH, R_EARTH + 600e3, 4e7, 1.2, 0, 1));
        // Clockwise high orbit.
        ship("Patrol", KeplerOrbit.circular(MU_EARTH, 4.2164e7, 3.5, 0, -1));
        // A freighter that will burn for the Moon.
        Entity freighter = ship("Freighter", KeplerOrbit.circular(MU_EARTH, R_EARTH + 1_000e3, 1.0, 0, 1));
        scheduleLunarTransfer(freighter);

        warp = new TimeWarpController(game, new NoBubble(), events, universe,
                new EncounterPredictor(game.getEcsEngine(), universe), () -> player);
        warp.changes.add((signal, change) -> {
            if (change.reason == TimeWarpController.DropReason.ENCOUNTER) {
                flash("Warp stopped: " + name(change.encounter) + " is within 20 km");
            } else if (change.reason == TimeWarpController.DropReason.PLAYER_EVENT && change.event != null) {
                flash("Warp stopped: " + describe(change.event));
            }
        });
        events.applied.add((signal, event) -> {
            if (event.type == OrbitalEvent.Type.SOI_ENTRY || event.type == OrbitalEvent.Type.SOI_EXIT) {
                flash(describe(event));
            }
        });
        events.impacts.add((signal, event) -> flash(describe(event)));
    }

    private Entity add(Component... components) {
        Entity e = game.createEntity();
        for (Component c : components) e.add(c);
        game.addEntity(e);
        return e;
    }

    private Entity ship(String id, KeplerOrbit orbit) {
        Entity e = add(new UuidComponent(id), new OrbitComponent("earth", orbit));
        ships.add(e);
        return e;
    }

    private double now() {
        return game.getSimSeconds();
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    private void onKey(int keycode) {
        boolean big = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
        double dv = big ? 100 : 10;
        switch (keycode) {
            case Input.Keys.PERIOD:
                if (!warp.step(1)) flash("Rails warp not allowed right now");
                break;
            case Input.Keys.COMMA:
                warp.step(-1);
                break;
            case Input.Keys.W: burn(dv, 0); break;
            case Input.Keys.S: burn(-dv, 0); break;
            case Input.Keys.D: burn(0, dv); break;
            case Input.Keys.A: burn(0, -dv); break;
            case Input.Keys.T:
                if (!scheduleLunarTransfer(player)) flash("Transfer needs a near-circular Earth orbit below the Moon");
                break;
            case Input.Keys.F: focusIndex = (focusIndex + 1) % 3; break;
            case Input.Keys.R: buildScene(); break;
            case Input.Keys.ESCAPE: Gdx.app.exit(); break;
            default: break;
        }
    }

    /** Instant burn for the player, applied as a maneuver node due now. */
    private void burn(double prograde, double radial) {
        if (warp.isRailsWarp()) warp.setWarp(1);
        ManeuverNodeComponent nodes = player.getComponent(ManeuverNodeComponent.class);
        if (nodes == null) player.add(nodes = new ManeuverNodeComponent());
        nodes.add(now(), prograde, radial);
        events.orbitChanged(player);
        events.processUntil(now());
    }

    /**
     * Plans a Hohmann transfer to the Moon's orbit as a maneuver node, timed so the Moon is there at
     * apoapsis. Only for near-circular, counter-clockwise Earth orbits.
     */
    private boolean scheduleLunarTransfer(Entity ship) {
        OrbitComponent orbit = ship.getComponent(OrbitComponent.class);
        KeplerOrbit k = orbit.getOrbit();
        if (!"earth".equals(orbit.parentUuid) || k.e > 0.05 || k.direction < 0 || k.a > MOON_A * 0.5) return false;
        double t = now();
        double r = k.a;
        double aTransfer = (r + MOON_A) / 2;
        double transferTime = Math.PI * Math.sqrt(aTransfer * aTransfer * aTransfer / MU_EARTH);
        double nShip = k.meanMotion();
        double nMoon = moon.getComponent(OrbitComponent.class).getOrbit().meanMotion();
        // At the burn the Moon must lead the ship by pi - nMoon * transferTime.
        double needed = Math.PI - nMoon * transferTime;
        StateVector s = k.stateAt(t, tmp);
        StateVector m = moon.getComponent(OrbitComponent.class).stateAt(t, tmp2);
        double lead = Math.atan2(m.y, m.x) - Math.atan2(s.y, s.x);
        double wait = wrap(lead - needed) / (nShip - nMoon);
        double dv = Math.sqrt(MU_EARTH * (2 / r - 1 / aTransfer)) - Math.sqrt(MU_EARTH / r);

        ManeuverNodeComponent nodes = ship.getComponent(ManeuverNodeComponent.class);
        if (nodes == null) ship.add(nodes = new ManeuverNodeComponent());
        nodes.add(t + wait, dv, 0);
        events.orbitChanged(ship);
        if (ship == player) flash(String.format(Locale.ROOT, "Transfer burn of %.0f m/s in %s", dv, duration(wait)));
        return true;
    }

    private static double wrap(double angle) {
        double a = angle % (2 * Math.PI);
        return a < 0 ? a + 2 * Math.PI : a;
    }

    // ------------------------------------------------------------------
    // Loop
    // ------------------------------------------------------------------

    @Override
    public void render() {
        float delta = Math.min(Gdx.graphics.getDeltaTime(), 0.1f);
        // Rails warp jumps the clock; at physics warp the engine's fixed tick loop advances it.
        warp.update(delta);
        game.update(delta);
        if (messageTimer > 0) messageTimer -= delta;

        draw();
    }

    private void draw() {
        Gdx.gl.glClearColor(0.02f, 0.02f, 0.05f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        double t = now();
        Entity focusEntity = focusIndex == 0 ? player : focusIndex == 1 ? earth : moon;
        universe.stateInFrame(focusEntity, earth, t, focus);

        camera.setToOrtho(false, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.position.set(0, 0, 0);
        camera.update();
        shapes.setProjectionMatrix(camera.combined);

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        drawBody(earth, new Color(0.2f, 0.4f, 0.9f, 1f), t);
        drawBody(moon, new Color(0.6f, 0.6f, 0.6f, 1f), t);
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        drawSoi(moon, t);
        drawOrbit(moon, new Color(0.4f, 0.4f, 0.4f, 1f), t);
        for (Entity ship : ships) {
            if (ship != player) drawOrbit(ship, new Color(1f, 0.6f, 0.2f, 0.7f), t);
        }
        drawOrbit(player, new Color(0.3f, 1f, 0.4f, 1f), t);
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (Entity ship : ships) {
            float[] p = toScreen(ship, t);
            shapes.setColor(ship == player ? Color.WHITE : Color.ORANGE);
            shapes.circle(p[0], p[1], ship == player ? 4f : 3f, 12);
        }
        shapes.end();

        hudCamera.setToOrtho(false, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        batch.setProjectionMatrix(hudCamera.combined);
        batch.begin();
        float y = Gdx.graphics.getHeight() - 12;
        for (String line : hudLines(t)) {
            font.draw(batch, line, 12, y);
            y -= 18;
        }
        for (Entity ship : ships) {
            if (ship == player) continue;
            float[] p = toScreen(ship, t);
            font.draw(batch, name(ship), p[0] + Gdx.graphics.getWidth() / 2f + 6, p[1] + Gdx.graphics.getHeight() / 2f + 4);
        }
        if (messageTimer > 0) {
            font.draw(batch, message, 12, 30);
        }
        batch.end();
    }

    private String[] hudLines(double t) {
        OrbitComponent orbit = player.getComponent(OrbitComponent.class);
        KeplerOrbit k = orbit.getOrbit();
        CelestialBodyComponent parent = universe.celestial(universe.entity(orbit.parentUuid));
        StateVector s = k.stateAt(t, tmp);
        String apo = k.isElliptic() ? String.format(Locale.ROOT, "%,.0f km", (k.apoapsis() - parent.radius) / 1000) : "escape";
        OrbitalEvent next = events.getQueue().nextFor("player");
        String mode = warp.isRailsWarp() ? "rails" : "physics";
        return new String[]{
                String.format(Locale.ROOT, "T+ %s    warp %,dx (%s)", duration(t), warp.getWarp(), mode),
                String.format(Locale.ROOT, "Orbiting %s   alt %,.0f km   speed %,.0f m/s",
                        parent.name, (s.radius() - parent.radius) / 1000, s.speed()),
                String.format(Locale.ROOT, "Periapsis %,.0f km   apoapsis %s   period %s",
                        (k.periapsis() - parent.radius) / 1000, apo, k.isElliptic() ? duration(k.period()) : "-"),
                next == null ? "No upcoming events" : "Next: " + describe(next) + " in " + duration(next.time - t),
                "[,/.] warp  [W/S] prograde/retro  [A/D] radial  [Shift] x10  [T] Moon transfer  [F] focus  [wheel] zoom  [R] reset",
        };
    }

    private void drawBody(Entity body, Color color, double t) {
        float[] p = toScreen(body, t);
        CelestialBodyComponent c = body.getComponent(CelestialBodyComponent.class);
        shapes.setColor(color);
        shapes.circle(p[0], p[1], Math.max(2f, (float) (c.radius / scale)), 64);
    }

    private void drawSoi(Entity body, double t) {
        float[] p = toScreen(body, t);
        shapes.setColor(0.3f, 0.3f, 0.5f, 1f);
        shapes.circle(p[0], p[1], (float) (body.getComponent(CelestialBodyComponent.class).soiRadius / scale), 96);
    }

    /** Draws the conic in its parent's frame, offset by where the parent is relative to the focus. */
    private void drawOrbit(Entity entity, Color color, double t) {
        OrbitComponent orbit = entity.getComponent(OrbitComponent.class);
        KeplerOrbit k = orbit.getOrbit();
        Entity parent = universe.entity(orbit.parentUuid);
        StateVector parentPos = universe.stateInFrame(parent, earth, t, new StateVector());
        CelestialBodyComponent parentBody = universe.celestial(parent);
        // Elliptic: one period. Hyperbolic: until it leaves the parent's SOI (or a few days).
        double span = k.isElliptic() ? k.period() : Math.min(5 * 86400.0,
                Math.max(1, k.nextTimeAtRadius(Math.min(parentBody.soiRadius, 1e9), t, KeplerOrbit.Crossing.OUTBOUND) - t));
        shapes.setColor(color);
        float px = 0, py = 0;
        for (int i = 0; i <= ORBIT_SEGMENTS; i++) {
            StateVector s = k.stateAt(t + span * i / ORBIT_SEGMENTS, tmp2);
            float x = (float) ((parentPos.x + s.x - focus.x) / scale);
            float y = (float) ((parentPos.y + s.y - focus.y) / scale);
            if (i > 0) shapes.line(px, py, x, y);
            px = x;
            py = y;
        }
    }

    /** Screen position relative to the focus: the subtraction is done in doubles, then cast. */
    private float[] toScreen(Entity entity, double t) {
        StateVector s = universe.stateInFrame(entity, earth, t, tmp);
        return new float[]{(float) ((s.x - focus.x) / scale), (float) ((s.y - focus.y) / scale)};
    }

    private String name(Entity e) {
        if (e == null) return "?";
        CelestialBodyComponent c = e.getComponent(CelestialBodyComponent.class);
        return c != null ? c.name : e.getComponent(UuidComponent.class).id;
    }

    private String describe(OrbitalEvent event) {
        String who = name(universe.entity(event.entityUuid));
        switch (event.type) {
            case MANEUVER: return who + " burn";
            case SOI_ENTRY: return who + " enters " + name(universe.entity(event.targetUuid)) + " SOI";
            case SOI_EXIT: return who + " leaves SOI";
            case IMPACT: return who + " impacts the surface";
            default: return who + " " + event.type;
        }
    }

    private void flash(String text) {
        message = text;
        messageTimer = 5f;
    }

    private static String duration(double seconds) {
        long s = (long) seconds;
        long d = s / 86400;
        long h = (s % 86400) / 3600;
        long m = (s % 3600) / 60;
        long sec = s % 60;
        return d > 0 ? String.format(Locale.ROOT, "%dd %02dh %02dm", d, h, m)
                : String.format(Locale.ROOT, "%02d:%02d:%02d", h, m, sec);
    }

    @Override
    public void dispose() {
        shapes.dispose();
        batch.dispose();
        font.dispose();
    }

    /** No Box2D bubble in this demo: the player is always on rails. */
    private static class NoBubble implements BubbleHandoff {
        @Override
        public void collapseToRails() {
        }

        @Override
        public void rebuildFromRails() {
        }
    }
}

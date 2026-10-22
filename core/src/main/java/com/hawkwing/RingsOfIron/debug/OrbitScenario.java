package com.hawkwing.RingsOfIron.debug;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.hawkwing.RingsOfIron.game.GameScenario;
import com.hawkwing.RingsOfIron.game.GameWorld;
import com.hawkwing.RingsOfIron.game.Navigation;
import com.hawkwing.RingsOfIron.game.NoticeLog;
import com.hawkwing.RingsOfIron.game.PlayerDocking;
import com.hawkwing.RingsOfIron.game.PlayerOrbitInfo;
import com.hawkwing.RingsOfIron.game.PlayerOrbitPath;
import com.ringsofiron.coreengine.components.UuidComponent;
import com.ringsofiron.orbital.components.CelestialBodyComponent;
import com.ringsofiron.orbital.components.ManeuverNodeComponent;
import com.ringsofiron.orbital.components.OrbitComponent;
import com.ringsofiron.orbital.events.OrbitalEvent;
import com.ringsofiron.orbital.math.KeplerOrbit;
import com.ringsofiron.orbital.math.StateVector;
import com.ringsofiron.uihud.HudSkin;
import com.ringsofiron.uihud.components.HudMarkerComponent;
import com.ringsofiron.uihud.providers.OrbitInfoProvider;
import com.ringsofiron.uihud.providers.OrbitPathProvider;
import com.ringsofiron.uihud.widgets.OrbitMap;

import java.util.List;
import java.util.Locale;

/**
 * The game's rails at real scale (was the orbit demo): the career's world as it starts, the corvette in low Earth
 * orbit by Highport, with four more craft on the rails around it: a courier that catches the player up and drops
 * warp, a tug on an eccentric orbit, a clockwise patrol and a freighter that burns for the Moon. Warp with the
 * game's keys; it collapses the bubble onto the rails and drops out at burns, sphere-of-influence changes and
 * encounters, as in a career.
 *
 * <p>The {@code orbit-map} overlay fills the screen with the game's own orbit map ({@link PlayerOrbitPath} into
 * ui-hud's {@link OrbitMap}) at a zoom and focus the menu's buttons set. Scenario-only, over it: every sphere of
 * influence ({@code orbit-soi}) and each other craft's orbit, position and name ({@code orbit-traffic}). Instant
 * burns (maneuver nodes due now, fired by the game's burn systems and paid from the tanks) and a planned flyby of
 * the Moon are buttons too; the game's own burn planner works as always.
 */
public final class OrbitScenario implements DebugScenario {
    /**
     * How far inside the Moon's orbit a transfer is aimed, m. A transfer to the Moon's centre falls straight into
     * it; aimed this far inside, the Moon catches the ship up and swings it past at about 1,300 km.
     */
    public static final double TRANSFER_MISS = 8_000e3;
    /** Delta-v of one burn button, m/s. */
    public static final double BURN = 100;
    /** The map's zoom at load, and its limits, meters per pixel. */
    static final double START_SCALE = 20_000, MIN_SCALE = 1, MAX_SCALE = 5e6;
    private static final String[] FOCUS = {"you", "Earth", "Moon"};
    /** The other craft: apart from the map's own colours (cyan now, amber planned, magenta target, green proposed). */
    private static final Color TRAFFIC = new Color(0.72f, 0.62f, 0.95f, 1f);

    GameWorld world;
    Entity earth, moon;
    /** The rails traffic the scenario adds, the player not included. */
    final Array<Entity> traffic = new Array<>();
    /** The game's orbit map data and readout for the player. */
    PlayerOrbitPath path;
    PlayerOrbitInfo orbitInfo;
    double scale = START_SCALE;
    int focusIndex;

    private final StateVector tmp = new StateVector();
    private final StateVector tmp2 = new StateVector();
    private final FloatArray points = new FloatArray();
    // Drawn with a stage of its own, made on the first frame it shows (none in tests)
    private Stage stage;
    private Skin skin;
    private OrbitMap map;
    private boolean mapDrawn;

    @Override
    public void setUp(ScenarioSetup setup) {
        build(setup.world);
        setup.exteriorView();
        setup.overlay("orbit-map", "The game's orbit map, full screen, at the scenario's zoom and focus", new MapOverlay(), true);
        setup.overlay("orbit-soi", "Spheres of influence on the orbit map", new SoiOverlay(), true);
        setup.overlay("orbit-traffic", "The other craft's orbits and names on the orbit map", new TrafficOverlay(), true);
        setup.action("Map zoom in", "Halves the orbit map's meters per pixel", () -> zoom(0.5));
        setup.action("Map zoom out", "Doubles the orbit map's meters per pixel", () -> zoom(2));
        setup.action("Map focus", "Centres the orbit map on you, Earth, then the Moon", () -> focusIndex = (focusIndex + 1) % FOCUS.length);
        setup.action("Burn prograde", "An instant " + (int) BURN + " m/s burn along your orbit", () -> burn(BURN, 0));
        setup.action("Burn retrograde", "An instant " + (int) BURN + " m/s burn against your orbit", () -> burn(-BURN, 0));
        setup.action("Burn radial out", "An instant " + (int) BURN + " m/s burn away from the body", () -> burn(0, BURN));
        setup.action("Burn radial in", "An instant " + (int) BURN + " m/s burn towards the body", () -> burn(0, -BURN));
        setup.action("Moon flyby", "Plans a transfer that swings past the Moon, from a near-circular Earth orbit", () -> {
            if (!lunarTransfer(world.getPlayer())) setup.notice("A Moon transfer needs a near-circular Earth orbit well below the Moon");
        });
        setup.notice("Orbit: warp with the game's keys. F3 for the map's zoom and focus, burns and a Moon flyby");
    }

    /** The headless half of {@link #setUp}: the traffic and the orbit map's data, on the career's world. */
    void build(GameWorld world) {
        this.world = world;
        earth = world.universe.entity(GameScenario.EARTH);
        moon = world.universe.entity(GameScenario.MOON);
        orbitInfo = new PlayerOrbitInfo(world.universe, world.physics, world::getPlayer);
        path = new PlayerOrbitPath(world, world.burns);

        double now = world.universe.now(), mu = GameScenario.MU_EARTH, rEarth = GameScenario.R_EARTH, leo = GameScenario.ORBIT_RADIUS;
        // The player is at phase 0. Slightly lower, a little behind: catches up and drops warp a few hours in
        craft("orbit-courier", "Courier", KeplerOrbit.circular(mu, leo - 3_000, -0.15, now, 1));
        // Eccentric, out to 40,000 km
        craft("orbit-tug", "Tug", KeplerOrbit.fromApsides(mu, rEarth + 600e3, 4e7, 1.2, now, 1));
        // Clockwise, in a high orbit
        craft("orbit-patrol", "Patrol", KeplerOrbit.circular(mu, 4.2164e7, 3.5, now, -1));
        // Burns for the Moon
        lunarTransfer(craft("orbit-freighter", "Freighter", KeplerOrbit.circular(mu, rEarth + 1_000e3, 1.0, now, 1)));

        // Sphere-of-influence changes of the player and the scenario's own craft (not the career's traffic)
        world.orbitalEvents.applied.add((signal, event) -> {
            if ((event.type == OrbitalEvent.Type.SOI_ENTRY || event.type == OrbitalEvent.Type.SOI_EXIT) && watched(event)) {
                world.notices.post(describe(event));
            }
        });
        world.orbitalEvents.impacts.add((signal, event) -> {
            if (watched(event)) world.notices.post(describe(event));
        });
    }

    private Entity craft(String uuid, String name, KeplerOrbit orbit) {
        Entity e = world.engine.createEntity();
        e.add(new UuidComponent(uuid));
        e.add(new OrbitComponent(GameScenario.EARTH, orbit));
        HudMarkerComponent marker = new HudMarkerComponent();
        marker.label = name;
        e.add(marker);
        world.engine.addEntity(e);
        traffic.add(e);
        return e;
    }

    private double now() {
        return world.universe.now();
    }

    void zoom(double factor) {
        scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale * factor));
    }

    /**
     * An instant burn by the player, m/s along and out from its orbit: a maneuver node due now, fired by the game
     * (in the bubble by its burn system, on rails by the orbital events). Rails warp drops to 1x first.
     */
    void burn(double prograde, double radial) {
        Entity player = world.getPlayer();
        if (player == null) return;
        if (world.timeWarp.isRailsWarp()) world.timeWarp.setWarp(1);
        node(player, now(), prograde, radial);
    }

    /**
     * Plans a Hohmann transfer to the Moon's orbit as a maneuver node, timed so the Moon is there at apoapsis, a
     * flyby {@link #TRANSFER_MISS} inside its orbit. Only from a near-circular, counter-clockwise Earth orbit below
     * the Moon; returns false otherwise.
     */
    boolean lunarTransfer(Entity ship) {
        if (ship == null) return false;
        boolean player = ship == world.getPlayer();
        KeplerOrbit k = player ? orbitInfo.currentConic() : world.universe.orbit(ship).getOrbit();
        Entity parent = player ? orbitInfo.parent() : world.universe.parentOf(ship);
        if (k == null || parent != earth || k.e > 0.05 || k.direction < 0 || k.a > GameScenario.MOON_DISTANCE * 0.5) {
            return false;
        }
        double mu = GameScenario.MU_EARTH, moonA = GameScenario.MOON_DISTANCE;
        double t = now();
        double r = k.a;
        double aTransfer = (r + moonA - TRANSFER_MISS) / 2;
        double transferTime = Math.PI * Math.sqrt(aTransfer * aTransfer * aTransfer / mu);
        double nMoon = world.universe.orbit(moon).getOrbit().meanMotion();
        // At the burn the Moon must lead the ship by pi - nMoon * transferTime
        double needed = Math.PI - nMoon * transferTime;
        StateVector s = k.stateAt(t, tmp);
        StateVector m = world.universe.orbit(moon).stateAt(t, tmp2);
        double lead = Math.atan2(m.y, m.x) - Math.atan2(s.y, s.x);
        double wait = wrap(lead - needed) / (k.meanMotion() - nMoon);
        double dv = Math.sqrt(mu * (2 / r - 1 / aTransfer)) - Math.sqrt(mu / r);
        node(ship, t + wait, dv, 0);
        if (player) {
            world.notices.post(String.format(Locale.ROOT, "Moon flyby: burn %.0f m/s in %s", dv, Navigation.formatTime(wait)));
        }
        return true;
    }

    private void node(Entity ship, double time, double prograde, double radial) {
        ManeuverNodeComponent nodes = ship.getComponent(ManeuverNodeComponent.class);
        if (nodes == null) ship.add(nodes = new ManeuverNodeComponent());
        nodes.add(time, prograde, radial);
        // On rails the orbital events fire it; in the bubble the game's burn system does
        if (world.universe.orbit(ship) != null) world.orbitalEvents.orbitChanged(ship);
    }

    private static double wrap(double angle) {
        double a = angle % (2 * Math.PI);
        return a < 0 ? a + 2 * Math.PI : a;
    }

    /** A craft's or a body's name; "You" for the player. */
    String name(Entity e) {
        if (e == null) return "?";
        if (e == world.getPlayer()) return "You";
        CelestialBodyComponent c = e.getComponent(CelestialBodyComponent.class);
        return c != null ? c.name : PlayerDocking.name(e);
    }

    private boolean watched(OrbitalEvent event) {
        Entity e = world.universe.entity(event.entityUuid);
        return e != null && (e == world.getPlayer() || traffic.contains(e, true));
    }

    private String describe(OrbitalEvent event) {
        Entity e = world.universe.entity(event.entityUuid);
        String who = name(e);
        boolean you = e == world.getPlayer();
        switch (event.type) {
            case SOI_ENTRY: return who + (you ? " enter the " : " enters the ") + name(world.universe.entity(event.targetUuid))
                    + "'s sphere of influence";
            case SOI_EXIT: return who + (you ? " leave" : " leaves") + " a sphere of influence";
            case IMPACT: return who + (you ? " hit" : " hits") + " the surface";
            default: return who + " " + event.type;
        }
    }

    @Override
    public void dispose() {
        if (stage != null) stage.dispose();
        if (skin != null) skin.dispose();
        stage = null;
        skin = null;
    }

    // --- Drawing ------------------------------------------------------------------------

    /** The map's centre this frame, meters from the body it is drawn around: the player, Earth or the Moon. */
    private void viewCentre(Entity frame, OrbitPathProvider.OrbitPath drawn, StateVector out) {
        Entity body = focusIndex == 1 ? earth : focusIndex == 2 ? moon : null;
        if (body == null) out.set(drawn.shipX, drawn.shipY, 0, 0);
        else world.universe.stateInFrame(body, frame, now(), out);
    }

    /** The game's orbit map over the whole screen, with the clock, warp and orbit as text. */
    private final class MapOverlay implements DebugOverlay {
        @Override
        public void drawScreen(OverlayView view) {
            mapDrawn = false;
            if (stage == null) {
                skin = HudSkin.create();
                stage = new Stage(new ScreenViewport());
                map = new OrbitMap(skin.getRegion("white"));
                map.setFont(skin.getFont("default-font"));
                map.setFillParent(true);
                map.setProvider(path);
                stage.addActor(map);
            }
            // In UI units, as the other overlays draw
            ScreenViewport viewport = (ScreenViewport) stage.getViewport();
            viewport.setUnitsPerPixel(view.uiWidth / Math.max(1, Gdx.graphics.getWidth()));
            viewport.update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);
            // The game's view dimmed behind it
            Gdx.gl.glEnable(GL20.GL_BLEND);
            view.shapes.begin(ShapeRenderer.ShapeType.Filled);
            view.shapes.setColor(0.02f, 0.02f, 0.05f, 0.97f);
            view.shapes.rect(0, 0, view.uiWidth, view.uiHeight);
            view.shapes.end();

            stage.act(view.delta);
            Entity frame = path.frame();
            if (map.hasPath() && frame != null) {
                viewCentre(frame, map.getPath(), tmp);
                map.setView(scale, tmp.x, tmp.y);
                stage.draw();
                mapDrawn = true;
            }

            view.batch.begin();
            float y = view.uiHeight - 12;
            view.font.setColor(0.85f, 0.9f, 1f, 1f);
            for (String line : hudLines()) {
                view.font.draw(view.batch, line, 12, y);
                y -= 18;
            }
            y = 30;
            List<NoticeLog.Notice> showing = world.notices.showing();
            for (NoticeLog.Notice n : showing) {
                view.font.draw(view.batch, n.label(), 12, y);
                y += 18;
            }
            view.batch.end();
        }
    }

    private String[] hudLines() {
        String clock = String.format(Locale.ROOT, "T+ %s    warp %,dx (%s)    map: %,.0f m/px, centred on %s",
                Navigation.formatTime(now()), world.timeWarp.getWarp(), world.timeWarp.isRailsWarp() ? "rails" : "physics",
                scale, FOCUS[focusIndex]);
        String keys = "F3: map zoom and focus, burns, Moon flyby; untick orbit-map to fly";
        OrbitInfoProvider.OrbitInfo o = orbitInfo.getPlayerOrbit();
        if (o == null) return new String[]{clock, "No orbit", keys};
        String apo = Double.isInfinite(o.apoapsis) ? "escape" : km(o.apoapsis);
        return new String[]{
                clock,
                String.format(Locale.ROOT, "Orbiting %s   alt %s   speed %,.0f m/s%s", o.bodyName, km(o.altitude),
                        orbitInfo.state().speed(), o.impactWarning ? "   IMPACT" : ""),
                String.format(Locale.ROOT, "Periapsis %s   apoapsis %s   period %s", km(o.periapsis), apo,
                        Double.isInfinite(o.period) ? "-" : Navigation.formatTime(o.period)),
                keys,
        };
    }

    private static String km(double meters) {
        return String.format(Locale.ROOT, "%,.0f km", meters / 1000);
    }

    /** Scenario-only: every body's sphere of influence, in the map's frame. */
    private final class SoiOverlay implements DebugOverlay {
        @Override
        public void drawScreen(OverlayView view) {
            Entity frame = path.frame();
            if (!mapDrawn || frame == null) return;
            view.shapes.begin(ShapeRenderer.ShapeType.Line);
            view.shapes.setColor(0.3f, 0.3f, 0.5f, 1f);
            for (Entity body : new Entity[]{earth, moon}) {
                CelestialBodyComponent c = world.universe.celestial(body);
                if (Double.isInfinite(c.soiRadius)) continue;
                world.universe.stateInFrame(body, frame, now(), tmp);
                view.shapes.circle(map.screenX(tmp.x), map.screenY(tmp.y), (float) (c.soiRadius * map.getDrawScale()), 96);
            }
            view.shapes.end();
        }
    }

    /** Scenario-only: the other craft's orbits, sampled as the map samples the player's, with their names. */
    private final class TrafficOverlay implements DebugOverlay {
        @Override
        public void drawScreen(OverlayView view) {
            Entity frame = path.frame();
            if (!mapDrawn || frame == null) return;
            double t = now();
            Gdx.gl.glEnable(GL20.GL_BLEND);
            view.shapes.begin(ShapeRenderer.ShapeType.Line);
            view.shapes.setColor(TRAFFIC.r, TRAFFIC.g, TRAFFIC.b, 0.55f);
            for (Entity ship : traffic) {
                OrbitComponent orbit = world.universe.orbit(ship);
                Entity parent = world.universe.parentOf(ship);
                if (orbit == null || parent == null) continue;
                world.universe.stateInFrame(parent, frame, t, tmp);
                double ox = tmp.x, oy = tmp.y;
                points.clear();
                PlayerOrbitPath.sample(orbit.getOrbit(), t, world.universe.celestial(parent), points);
                for (int i = 2; i + 1 < points.size; i += 2) {
                    view.shapes.line(map.screenX(ox + points.get(i - 2)), map.screenY(oy + points.get(i - 1)),
                            map.screenX(ox + points.get(i)), map.screenY(oy + points.get(i + 1)));
                }
            }
            view.shapes.end();

            view.shapes.begin(ShapeRenderer.ShapeType.Filled);
            view.shapes.setColor(TRAFFIC);
            for (Entity ship : traffic) {
                world.universe.stateInFrame(ship, frame, t, tmp);
                view.shapes.circle(map.screenX(tmp.x), map.screenY(tmp.y), 3f, 12);
            }
            view.shapes.end();

            view.batch.begin();
            view.font.setColor(TRAFFIC);
            for (Entity ship : traffic) {
                world.universe.stateInFrame(ship, frame, t, tmp);
                view.font.draw(view.batch, name(ship), map.screenX(tmp.x) + 6f, map.screenY(tmp.y) + 4f);
            }
            view.batch.end();
        }
    }
}

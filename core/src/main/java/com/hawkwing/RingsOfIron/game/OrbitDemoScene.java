package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.coreengine.components.PlayerControllerComponent;
import com.ringsofiron.coreengine.components.UuidComponent;
import com.ringsofiron.orbital.components.CelestialBodyComponent;
import com.ringsofiron.orbital.components.ManeuverNodeComponent;
import com.ringsofiron.orbital.components.OrbitComponent;
import com.ringsofiron.orbital.events.OrbitalEvent;
import com.ringsofiron.orbital.math.KeplerOrbit;
import com.ringsofiron.orbital.math.StateVector;
import com.ringsofiron.uihud.components.HudMarkerComponent;

import java.nio.file.Path;
import java.util.Locale;

/**
 * The orbit demo's world, headless: the game's own {@link GameWorld} with Earth and the Moon as the
 * game builds them ({@link GameScenario#addEarthAndMoon}) and the player's corvette in low Earth
 * orbit, flying in the bubble and collapsing onto the rails at rails warp exactly as in the game.
 * Four more craft ride the rails around it for the screen to draw: a courier that catches the
 * player up and drops warp, a tug on an eccentric orbit, a clockwise patrol and a freighter that
 * burns for the Moon. A screen draws it; tests run it.
 *
 * <p>Demo-only: burns are instant (a maneuver node due now, fired by the game's burn systems and
 * paid from the corvette's tanks), and {@link #lunarTransfer} plans a Hohmann transfer to the Moon. The game plans burns with {@link BurnPlanner} instead.
 */
public class OrbitDemoScene {
    /** The player's orbit radius, m: low Earth orbit, as in the game. */
    public static final double LEO = GameScenario.ORBIT_RADIUS;
    /**
     * How far inside the Moon's orbit a transfer is aimed, m. A transfer to the Moon's centre falls
     * straight into it; aimed this far inside, the Moon catches the ship up and swings it past at
     * about 1,300 km.
     */
    public static final double TRANSFER_MISS = 8_000e3;

    public final Path assetsDir;
    public GameWorld world;
    public Entity player;
    public Entity earth;
    public Entity moon;
    /** The rails traffic the demo draws, the player not included. */
    public final Array<Entity> traffic = new Array<>();
    /** The game's orbit map data for the player: what the screen draws. */
    public PlayerOrbitPath path;
    /** The game's orbit readout for the player. */
    public PlayerOrbitInfo orbitInfo;

    private final StateVector tmp = new StateVector();
    private final StateVector tmp2 = new StateVector();

    public OrbitDemoScene(Path assetsDir) {
        this.assetsDir = assetsDir;
    }

    /** Throws away the current world and builds it again from scratch. */
    public void load() {
        dispose();
        traffic.clear();
        world = new GameWorld();
        world.loadContent(assetsDir, assetsDir.resolve("no-mods-here"));
        GameScenario.addEarthAndMoon(world);
        earth = world.universe.entity(GameScenario.EARTH);
        moon = world.universe.entity(GameScenario.MOON);

        double now = world.engine.getSimSeconds();
        world.setAnchor(GameScenario.EARTH, KeplerOrbit.circular(GameScenario.MU_EARTH, LEO, 0, now, 1));
        player = world.getContent().factory.spawnShip(GameScenario.PLAYER_SHIP, 0f, 0f, 90f);
        player.add(new PlayerControllerComponent());
        orbitInfo = new PlayerOrbitInfo(world.universe, world.physics, world::getPlayer);
        path = new PlayerOrbitPath(world, world.burns);

        double mu = GameScenario.MU_EARTH, rEarth = GameScenario.R_EARTH;
        // Slightly lower, a little behind: catches up and drops warp a few hours in
        craft("courier", "Courier", KeplerOrbit.circular(mu, LEO - 3_000, -0.15, now, 1));
        // Eccentric, out to 40,000 km
        craft("tug", "Tug", KeplerOrbit.fromApsides(mu, rEarth + 600e3, 4e7, 1.2, now, 1));
        // Clockwise, in a high orbit
        craft("patrol", "Patrol", KeplerOrbit.circular(mu, 4.2164e7, 3.5, now, -1));
        // Burns for the Moon
        Entity freighter = craft("freighter", "Freighter", KeplerOrbit.circular(mu, rEarth + 1_000e3, 1.0, now, 1));
        lunarTransfer(freighter);

        WarpNotices.attach(world);
        // Every craft's sphere of influence changes, not only the player's
        world.orbitalEvents.applied.add((signal, event) -> {
            if (event.type == OrbitalEvent.Type.SOI_ENTRY || event.type == OrbitalEvent.Type.SOI_EXIT) {
                world.notices.post(describe(event));
            }
        });
        world.orbitalEvents.impacts.add((signal, event) -> world.notices.post(describe(event)));
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

    public void update(float delta) {
        world.update(delta);
        world.notices.update(delta);
    }

    public double now() {
        return world.universe.now();
    }

    /**
     * An instant burn by the player, m/s along and out from its orbit: a maneuver node due now,
     * fired by the game (in the bubble by {@link BubbleBurnSystem}, on rails by the orbital events).
     * Rails warp drops to 1x first, as a burn in the game does.
     */
    public void burn(double prograde, double radial) {
        if (world.timeWarp.isRailsWarp()) world.timeWarp.setWarp(1);
        node(player, now(), prograde, radial);
    }

    /**
     * Plans a Hohmann transfer to the Moon's orbit as a maneuver node, timed so the Moon is there at
     * apoapsis, a flyby {@link #TRANSFER_MISS} inside its orbit. Only from a near-circular, counter-clockwise Earth orbit below the Moon; returns
     * false otherwise. The player's tanks are topped up for it.
     */
    public boolean lunarTransfer(Entity ship) {
        KeplerOrbit k = ship == player ? orbitInfo.currentConic() : world.universe.orbit(ship).getOrbit();
        Entity parent = ship == player ? orbitInfo.parent() : world.universe.parentOf(ship);
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
        if (ship == player) {
            world.notices.post(String.format(Locale.ROOT, "Transfer burn of %.0f m/s in %s", dv, Navigation.formatTime(wait)));
        }
        return true;
    }

    private void node(Entity ship, double time, double prograde, double radial) {
        ManeuverNodeComponent nodes = ship.getComponent(ManeuverNodeComponent.class);
        if (nodes == null) ship.add(nodes = new ManeuverNodeComponent());
        nodes.add(time, prograde, radial);
        // On rails the orbital events fire it; in the bubble BubbleBurnSystem does
        if (world.universe.orbit(ship) != null) world.orbitalEvents.orbitChanged(ship);
    }

    private static double wrap(double angle) {
        double a = angle % (2 * Math.PI);
        return a < 0 ? a + 2 * Math.PI : a;
    }

    /** A craft's or a body's name. */
    public String name(Entity e) {
        if (e == null) return "?";
        if (e == player) return "You";
        CelestialBodyComponent c = e.getComponent(CelestialBodyComponent.class);
        return c != null ? c.name : PlayerDocking.name(e);
    }

    private String describe(OrbitalEvent event) {
        Entity e = world.universe.entity(event.entityUuid);
        String who = name(e);
        boolean you = e == player;
        switch (event.type) {
            case SOI_ENTRY: return who + (you ? " enter the " : " enters the ") + name(world.universe.entity(event.targetUuid))
                    + "'s sphere of influence";
            case SOI_EXIT: return who + (you ? " leave" : " leaves") + " a sphere of influence";
            case IMPACT: return who + (you ? " hit" : " hits") + " the surface";
            default: return who + " " + event.type;
        }
    }

    public void dispose() {
        if (world != null) world.dispose();
        world = null;
    }
}

package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.ringsofiron.coreengine.components.PlayerControllerComponent;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.moddingdata.defs.ChassisDef;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.FlightControlComponent;
import com.ringsofiron.physicsexterior.components.KinematicTransientComponent;
import com.ringsofiron.stationsdocking.DockingData;
import com.ringsofiron.stationsdocking.DockingPort;
import com.ringsofiron.stationsdocking.DockingPortsComponent;
import com.ringsofiron.stationsdocking.DockingSystem;
import com.ringsofiron.stationsdocking.StationComponent;
import com.ringsofiron.uihud.components.HudMarkerComponent;

import java.nio.file.Path;

/**
 * The docking demo's world, headless: the game's own modules and data, without the orbit or the
 * economy. Each {@link Scene} sets up one docking feature with the player's corvette a little way
 * off; the player flies in (or lets {@link #autopilot} do it) and uses the same keys as the game.
 * A screen draws it; tests run it.
 */
public class DockingDemoScene {
    public enum Scene {
        PASSIVE("Passive latch", "Both ports passive. The other ship's crew armed theirs: arm yours (G) and drift in;"
                + " the magnets pull the ports together and latch within a few meters."),
        ACTIVE("Active collar", "Your port has a telescoping collar. Arm it (G) within a few meters: it reaches out,"
                + " catches the other port (armed or not), draws the ships together and locks."),
        CLEARANCE("Station clearance", "G asks the outpost for clearance. It arms its nearest free Small port for you"
                + " and its own collar reaches out for your passive port."),
        REFUSED("Clearance refused", "This outpost refuses clearance (G): its ports stay unarmed and its collar stays in."
                + " Your own magnets can still latch on, but you are docked without leave and its market won't trade."),
        TEAR("Tearing loose", "Docked at the outpost. Hold W to pull away: past its break force the latch tears, the"
                + " collars come off and the hull around both ports is damaged. U undocks cleanly instead."),
        HUB("Hub with traffic", "A hub has two ports of each size; a hauler is berthed at a Medium one. Ask for"
                + " clearance (G) and dock at a Small port beside it.");

        public final String title;
        public final String help;

        Scene(String title, String help) {
            this.title = title;
            this.help = help;
        }
    }

    /** The approach speed the autopilot holds far out, m/s; it slows as the ports close. */
    static final float AUTOPILOT_SPEED = 1.5f;

    public final Path assetsDir;
    public GameWorld world;
    public Scene scene;
    public Entity player;
    /** The station or ship the scene is about. */
    public Entity target;
    public PlayerDocking docking;
    /** Flies the player's selected port in to the nearest free port of its size until the latch takes over. */
    public boolean autopilot;
    private String notice = "";
    private final Vector2 tmp = new Vector2();

    public DockingDemoScene(Path assetsDir) {
        this.assetsDir = assetsDir;
    }

    /** Throws away the current world and sets up {@code next} from scratch. */
    public void load(Scene next) {
        dispose();
        scene = next;
        autopilot = false;
        notice = next.help;
        world = new GameWorld();
        world.loadContent(assetsDir, assetsDir.resolve("no-mods-here"));
        player = world.getContent().factory.spawnShip(GameScenario.PLAYER_SHIP, 0f, 0f, 0f);
        player.add(new PlayerControllerComponent());
        FlightControlComponent controls = player.getComponent(FlightControlComponent.class);
        if (controls != null) controls.rotationAssist = true;
        docking = new PlayerDocking(world, () -> player, message -> notice = message);

        switch (next) {
            case PASSIVE:
            case ACTIVE:
                target = world.getContent().factory.spawnShip(GameScenario.PLAYER_SHIP, 0f, 0f, 0f);
                target.add(new HudMarkerComponent("Tender", HudMarkerComponent.Kind.FRIENDLY).radius(10f));
                port(target, 0).armed = true;
                if (next == Scene.ACTIVE) port(player, 0).active = true;
                park(player, 0, target, 0, next == Scene.ACTIVE ? 9f : 12f, 1.5f, 8f);
                break;
            case CLEARANCE:
            case REFUSED:
            case TEAR:
                target = station("Outpost Kepler", "station_outpost", next != Scene.REFUSED);
                park(player, 0, target, smallPort(target), next == Scene.TEAR ? 0f : 25f, 3f, 12f);
                if (next == Scene.TEAR) world.docking.dockInPlace(player, 0, target, smallPort(target));
                break;
            case HUB:
                target = station("Highport Hub", "station_hub", true);
                Entity hauler = world.getContent().factory.spawnShip(GameScenario.FREIGHTER_SHIP, 0f, -60f, 0f);
                hauler.add(new HudMarkerComponent("Hauler Kestrel", HudMarkerComponent.Kind.FRIENDLY).radius(20f));
                world.docking.dockInPlace(hauler, 0, target, firstPort(target, MountFootprint.MountSize.M));
                park(player, 0, target, smallPort(target), 30f, -4f, -10f);
                break;
            default:
                break;
        }
        // Long enough for the power to be routed aboard, so the ports can arm
        for (int i = 0; i < 75; i++) world.update(1f / 60f);
    }

    /** One frame: the autopilot (if on), then the world. */
    public void update(float delta) {
        if (autopilot) fly();
        world.update(delta);
    }

    public String notice() {
        return notice;
    }

    public void dispose() {
        if (docking != null) docking.dispose();
        if (world != null) world.dispose();
        docking = null;
        world = null;
    }

    // ------------------------------------------------------------------

    /** A station from data at the origin, holding still, with its interior and ports. */
    private Entity station(String name, String blueprint, boolean grantsClearance) {
        Entity e = world.engine.createEntityWithUuid();
        e.add(new TransformComponent());
        world.engine.addEntity(e);
        StationComponent station = DockingData.buildStation(e, world.getContent().defs.require("chassis", blueprint,
                ChassisDef.class), blueprint, world.engine, name, null);
        station.grantsClearance = grantsClearance;
        e.add(new HudMarkerComponent(name, HudMarkerComponent.Kind.STATION).radius(20f));
        // Holding still in the demo's empty space; in the game this is the station's orbit.
        e.add(new KinematicTransientComponent((t, out) -> {
            out[0] = 0.0;
            out[1] = 0.0;
            out[2] = 0.0;
            out[3] = 0.0;
        }, 30f));
        return e;
    }

    private static int smallPort(Entity station) {
        return firstPort(station, MountFootprint.MountSize.S);
    }

    private static int firstPort(Entity station, MountFootprint.MountSize size) {
        DockingPort[] ports = station.getComponent(DockingPortsComponent.class).ports;
        for (int i = 0; i < ports.length; i++) {
            if (ports[i].size == size) return i;
        }
        throw new IllegalStateException("no " + size + " port");
    }

    static DockingPort port(Entity e, int i) {
        return e.getComponent(DockingPortsComponent.class).ports[i];
    }

    /**
     * Puts {@code ship} with its port {@code shipPort} facing {@code other}'s port {@code otherPort},
     * {@code gap} meters out, {@code lateral} meters off its centerline and turned {@code degrees}
     * off square. Before the first step, so it uses the ports' own frames.
     */
    private void park(Entity ship, int shipPort, Entity other, int otherPort, float gap, float lateral, float degrees) {
        DockingPort q = port(other, otherPort);
        DockingPort p = port(ship, shipPort);
        Body ob = world.docking.bodyOf(other);
        float oa = ob == null ? 0f : ob.getAngle();
        Vector2 o = ob == null ? new Vector2() : PhysicsManager.getPosition(ob, new Vector2());
        float c = MathUtils.cos(oa), s = MathUtils.sin(oa);
        float nx = c * q.normalX - s * q.normalY, ny = s * q.normalX + c * q.normalY;
        float fx = o.x + c * q.faceX() - s * q.faceY(), fy = o.y + s * q.faceX() + c * q.faceY();
        float tx = fx + nx * gap - ny * lateral, ty = fy + ny * gap + nx * lateral;
        float angle = MathUtils.atan2(ny, nx) + MathUtils.PI - MathUtils.atan2(p.normalY, p.normalX)
                + degrees * MathUtils.degreesToRadians;
        float ca = MathUtils.cos(angle), sa = MathUtils.sin(angle);
        Body body = world.docking.bodyOf(ship);
        PhysicsManager.setPosition(body, tx - (ca * p.faceX() - sa * p.faceY()), ty - (sa * p.faceX() + ca * p.faceY()), angle);
        PhysicsManager.setVelocity(body, 0f, 0f);
        body.setAngularVelocity(0f);
    }

    /**
     * The autopilot: steers the player's port onto the centerline of the nearest free port of its
     * size, square on, closing slower the nearer it gets, by setting the ship's velocity outright.
     * It lets go once the magnets or a collar can take over, or when nothing is in range.
     */
    private void fly() {
        DockingPortsComponent ports = player.getComponent(DockingPortsComponent.class);
        DockingPort p = ports.selectedPort();
        DockingSystem.Approach a =
                world.docking.approach(player, ports.selected, PlayerDocking.READOUT_RANGE,
                        new DockingSystem.Approach());
        if (a.target == null || !p.isFree()) {
            autopilot = false;
            return;
        }
        Body body = world.docking.bodyOf(player);
        Body ob = world.docking.bodyOf(a.target);
        DockingPort q = port(a.target, a.port);
        Vector2 n = ob.getWorldVector(new Vector2(q.normalX, q.normalY)).cpy();
        float along = (float) Math.sqrt(Math.max(0f, a.distance * a.distance - a.lateral * a.lateral));
        float handOver = p.active && p.armed ? p.tuning().maxExtension * 0.8f : p.tuning().magnetRange * 0.6f;
        if (along < handOver) {
            autopilot = false;
            notice = "Autopilot hands over to the " + (p.active && p.armed ? "collar" : "magnets");
            return;
        }
        float speed = Math.min(AUTOPILOT_SPEED, 0.15f + 0.12f * along);
        Vector2 vo = PhysicsManager.getVelocity(ob, tmp);
        // Lateral is measured along (n.y, -n.x): steer back against it
        float vx = vo.x - n.x * speed - n.y * a.lateral * 0.4f;
        float vy = vo.y - n.y * speed + n.x * a.lateral * 0.4f;
        PhysicsManager.setVelocity(body, vx, vy);
        body.setAngularVelocity(ob.getAngularVelocity() + a.angle * 1.5f);
    }
}

package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.ringsofiron.coreengine.components.PlayerControllerComponent;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.aicrew.CrewFactory;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.interiorgrid.CargoHoldComponent;
import com.ringsofiron.interiorgrid.DockingPortLayout;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Side;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.macrosim.economy.CommodityCatalog;
import com.ringsofiron.macrosim.economy.CommodityType;
import com.ringsofiron.moddingdata.defs.ChassisDef;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.FlightControlComponent;
import com.ringsofiron.physicsexterior.components.KinematicTransientComponent;
import com.ringsofiron.stationsdocking.DockingData;
import com.ringsofiron.stationsdocking.DockingPort;
import com.ringsofiron.stationsdocking.DockingPortsComponent;
import com.ringsofiron.stationsdocking.DockingSystem;
import com.ringsofiron.stationsdocking.ForceMethod;
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
        ACTIVE("Active collar", "Your port has a telescoping collar. Arm it (G) close in: it reaches out, tilts to lie"
                + " flat on the other port (armed or not), catches it, then draws the ships together and turns them square."),
        CLEARANCE("Station clearance", "G asks the outpost for clearance. It arms its nearest free Small port for you"
                + " and its own collar reaches out for your passive port. Once locked, both port doors open (V: deck view)."),
        REFUSED("Clearance refused", "This outpost refuses clearance (G): its ports stay unarmed and its collar stays in."
                + " Your own magnets can still latch on, but its port door stays shut and its market won't trade."),
        TEAR("Tearing loose", "Docked at the outpost. Hold W to pull away: past its break force the latch tears, the"
                + " collars come off and the hull around both ports is damaged. U undocks cleanly instead."),
        HUB("Hub with traffic", "A hub has two ports of each size; a hauler is berthed at a Medium one. Ask for"
                + " clearance (G) and dock at a Small port beside it."),
        WALK("Walk aboard", "Docked at the outpost with clearance and both port doors open. Deck view (V) is on: walk"
                + " (WASD) through the port onto the station and back; air moves between the two. U undocks once you're home."),
        LOADING("Loading cargo", "Docked at the outpost, whose hold is stocked with water and food. L orders "
                + LOAD_UNITS + " water carried aboard, K sends " + LOAD_UNITS + " back: the outpost's dock hands (orange)"
                + " carry it across the dock a crate at a time, and it moves from hold to hold as each crate lands."),
        FORCE("Forcing a door", "Latched onto the outpost without its clearance: its port door stays shut. You stand in"
                + " your port's opening, facing it. It is powered and locked: F hacks it (45 s, no damage), B cuts through"
                + " (20 s, damages it). O switches the outpost's reactor off, so F pries the unpowered door instead (8 s)."
                + " Each is a hostile act: your standing with the stations drops, and after a cut they refuse you clearance.");

        public final String title;
        public final String help;

        Scene(String title, String help) {
            this.title = title;
            this.help = help;
        }
    }

    /** Units the loading scene's keys order carried across. */
    public static final int LOAD_UNITS = 10;
    /** The loading scene's goods. */
    public static final String WATER = "water", FOOD = "food";

    /** The approach speed the autopilot holds far out, m/s; it slows as the ports close. */
    static final float AUTOPILOT_SPEED = 1.5f;

    public final Path assetsDir;
    public GameWorld world;
    public Scene scene;
    public Entity player;
    /** The player's crew avatar, aboard the corvette just inside its port. */
    public Entity avatar;
    /** The station or ship the scene is about. */
    public Entity target;
    public PlayerDocking docking;
    /** Flies the player's selected port in to the nearest free port of its size until the latch takes over. */
    public boolean autopilot;
    /** Deck view: the keys walk {@link #avatar} instead of flying, and the screen follows it. */
    public boolean deckView;
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
        deckView = next == Scene.WALK || next == Scene.LOADING || next == Scene.FORCE;
        notice = next.help;
        world = new GameWorld();
        world.loadContent(assetsDir, assetsDir.resolve("no-mods-here"));
        player = world.getContent().factory.spawnShip(GameScenario.PLAYER_SHIP, 0f, 0f, 0f);
        player.add(new PlayerControllerComponent());
        FlightControlComponent controls = player.getComponent(FlightControlComponent.class);
        if (controls != null) controls.rotationAssist = true;
        int[] home = insidePort(player, 0);
        avatar = CrewFactory.spawnPlayer(world.crew, player, "You", home[0], home[1]);
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
            case WALK:
            case LOADING:
            case FORCE:
                target = station("Outpost Kepler", "station_outpost", next != Scene.REFUSED && next != Scene.FORCE);
                boolean docked = next == Scene.TEAR || next == Scene.WALK || next == Scene.LOADING || next == Scene.FORCE;
                park(player, 0, target, smallPort(target), docked ? 0f : 25f, docked ? 0f : 3f, docked ? 0f : 12f);
                if (docked) world.docking.dockInPlace(player, 0, target, smallPort(target));
                if (next == Scene.LOADING) stock(target);
                if (next == Scene.FORCE) {
                    // Latched on with the ship's own magnets: the outpost never agreed, so its door stays shut
                    port(target, smallPort(target)).consented = false;
                    port(target, smallPort(target)).permitted = false;
                    port(player, 0).permitted = false;
                    int[] door = portTile(player, 0, 0);
                    avatar.getComponent(GridPositionComponent.class).setTile(door[0], door[1]);
                }
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
        // The panel shows the standing itself
        world.standing.acts.clear();
    }

    /**
     * Orders {@link #LOAD_UNITS} of water carried aboard from the station ({@code aboard}) or back
     * to it; returns what the scene says about it.
     */
    public String orderWater(boolean aboard) {
        String what = aboard ? "aboard" : "back to the outpost";
        if (!world.cargo.canCarry(player, target)) notice = "Dock first: the outpost's hands carry cargo only across a dock";
        else if (world.cargo.order(player, target, WATER, LOAD_UNITS, aboard) > 0)
            notice = "Ordered " + LOAD_UNITS + " water carried " + what;
        return notice;
    }

    /** Starts the avatar forcing the door across the dock {@code how}; returns what the scene says. */
    public String force(ForceMethod how) {
        notice = world.forcing.start(avatar, how);
        return notice;
    }

    /** Switches the station's reactors off or back on; its port door loses power with them. */
    public String toggleStationPower() {
        ShipGridComponent grid = target.getComponent(ShipGridComponent.class);
        Boolean on = null;
        java.util.Set<Entity> seen = new java.util.HashSet<>();
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                Entity e = grid.subsystemAt(x, y);
                SubsystemComponent sub = e == null || !seen.add(e) ? null : e.getComponent(SubsystemComponent.class);
                if (sub == null || sub.type != SubsystemComponent.SubsystemType.REACTOR) continue;
                if (on == null) on = !sub.switchedOn;
                sub.switchedOn = on;
            }
        }
        grid.gridDirty = true;
        notice = on == null ? "The station has no reactor" : on ? "The outpost's reactor is back on" : "The outpost's reactor is off";
        return notice;
    }

    /** Units of {@code commodity} in {@code craft}'s hold. */
    public double held(Entity craft, String commodity) {
        CargoHoldComponent hold = craft.getComponent(CargoHoldComponent.class);
        return hold == null ? 0 : hold.amount(commodity);
    }

    /** The loading scene's outpost hold: water and food, moved between holds without a market. */
    private void stock(Entity station) {
        CommodityCatalog catalog = world.macroSim.economy().catalog();
        world.cargo.setSettlement(new HoldTransfer(catalog));
        CargoHoldComponent hold = station.getComponent(CargoHoldComponent.class);
        for (String id : new String[]{WATER, FOOD}) {
            CommodityType c = catalog.commodity(id);
            hold.load(id, 20, c.unitVolume, c.unitMass);
        }
    }

    /** The craft the avatar is aboard: the corvette, or whatever it walked onto. */
    public Entity aboard() {
        return avatar.getComponent(GridPositionComponent.class).ship;
    }

    /** Where the avatar stands, world meters; null if it is aboard nothing. */
    public Vector2 avatarWorld(Vector2 out) {
        GridPositionComponent pos = avatar.getComponent(GridPositionComponent.class);
        ShipGridComponent grid = pos.ship == null ? null : pos.ship.getComponent(ShipGridComponent.class);
        Body body = pos.ship == null ? null : world.docking.bodyOf(pos.ship);
        if (grid == null || body == null) return null;
        Vector2 w = body.getWorldPoint(pos.toShipLocal(grid, out).scl(PhysicsManager.UNITS_PER_METER));
        return out.set(w.x * PhysicsManager.METERS_PER_UNIT, w.y * PhysicsManager.METERS_PER_UNIT);
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
        StationCrew.spawnLoaders(world.crew, e);
        // Holding still in the demo's empty space; in the game this is the station's orbit.
        e.add(new KinematicTransientComponent((t, out) -> {
            out[0] = 0.0;
            out[1] = 0.0;
            out[2] = 0.0;
            out[3] = 0.0;
        }, 30f));
        return e;
    }

    static int smallPort(Entity station) {
        return firstPort(station, MountFootprint.MountSize.S);
    }

    private static int firstPort(Entity station, MountFootprint.MountSize size) {
        DockingPort[] ports = station.getComponent(DockingPortsComponent.class).ports;
        for (int i = 0; i < ports.length; i++) {
            if (ports[i].size == size) return i;
        }
        throw new IllegalStateException("no " + size + " port");
    }

    /** A floor tile just inside port {@code i}: the opening's middle, one tile past its inner end. */
    static int[] insidePort(Entity ship, int i) {
        return portTile(ship, i, -1);
    }

    /**
     * The middle of port {@code i}'s opening, {@code steps} tiles in from its door ({@code -1}: one
     * tile past the port's inner end).
     */
    static int[] portTile(Entity ship, int i, int steps) {
        DockingPort p = port(ship, i);
        ShipGridComponent grid = ship.getComponent(ShipGridComponent.class);
        MountFootprint m = grid.getTile(p.gridX, p.gridY).mount;
        Side out = DockingPortLayout.facing(m);
        int[] door = null;
        for (int y = m.originY; y < m.originY + m.height(); y++) {
            for (int x = m.originX; x < m.originX + m.width(); x++) {
                if (DockingPortLayout.isDoorTile(m, x, y) && (door == null || x + y < door[0] + door[1])) door = new int[]{x, y};
            }
        }
        int along = DockingPortLayout.openingWidth(m) / 2, depth = steps < 0 ? DockingPortLayout.depth(m) : steps;
        // Along the door row runs at right angles to the facing; step in past the port's depth
        int x = door[0] + Math.abs(out.dy) * along - out.dx * depth;
        int y = door[1] + Math.abs(out.dx) * along - out.dy * depth;
        Tile t = grid.getTile(x, y);
        return t != null && t.isOpen() ? new int[]{x, y} : door;
    }

    static DockingPort port(Entity e, int i) {
        return e.getComponent(DockingPortsComponent.class).ports[i];
    }

    /**
     * Puts {@code ship} with its port {@code shipPort} facing {@code other}'s port {@code otherPort},
     * {@code gap} meters out, {@code lateral} meters off its centerline and turned {@code degrees}
     * off square. Before the first step, so it uses the ports' own frames.
     */
    void park(Entity ship, int shipPort, Entity other, int otherPort, float gap, float lateral, float degrees) {
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

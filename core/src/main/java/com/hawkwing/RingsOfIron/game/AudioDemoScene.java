package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.ringsofiron.aicrew.CrewFactory;
import com.ringsofiron.aicrew.CrewRole;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.aicrew.PlayerAvatarComponent;
import com.ringsofiron.audiospatial.backend.AudioEventCatalog;
import com.ringsofiron.audiospatial.components.AudioEmitterComponent;
import com.ringsofiron.audiospatial.components.AudioListenerComponent;
import com.ringsofiron.coreengine.components.PlayerControllerComponent;
import com.ringsofiron.interiorgrid.EdgeWall;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.moddingdata.defs.ChassisDef;
import com.ringsofiron.moddingdata.defs.ShipDef;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.FlightControlComponent;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The audio demo's world, headless: the player's corvette with its crew and the player's avatar,
 * heard through the game's own audio systems (listener, occlusion, crew footsteps and voices, door
 * sounds). On top it places four sounds by hand that can be switched on and off, so the walls and
 * doors between them and the avatar can be heard muffling them. A screen draws it; tests run it.
 *
 * <p>The corvette in the data is one open deck, so the demo fits it with two bulkheads, each with
 * a door, as a player could draw them in the ship editor: the engines and hold aft, the crew
 * spaces amidships and the reactor forward each become a room. They go into this world's copy of
 * the corvette's layout ({@code edge_walls}), and the game builds the ship from it as from any layout.
 */
public class AudioDemoScene {
    /** Meters per second the avatar walks, as in the game. */
    public static final float WALK_SPEED = 1f;
    /** How near the avatar must stand to a door to seal or open it, meters. */
    public static final float DOOR_REACH = 2f;

    /** One of the hand-placed sounds. */
    public static final class Source {
        public final String name;
        public final AudioEmitterComponent emitter;

        Source(String name, AudioEmitterComponent emitter) {
            this.name = name;
            this.emitter = emitter;
        }

        public boolean playing() {
            return emitter.instance != null && emitter.instance.isPlaying();
        }
    }

    /**
     * The bulkheads fitted: on the left side of each column x, from tile y 1 to 4, with the door at
     * the given y. Aft of the crew spaces (x 6, door at y 3) and aft of the reactor (x 13, door at y 2).
     */
    static final int[][] BULKHEADS = {{6, 3}, {13, 2}};
    static final int BULKHEAD_FROM = 1, BULKHEAD_TO = 4;

    public final Path assetsDir;
    public GameWorld world;
    public Entity ship;
    public String hull;
    /** The player's crew avatar: inside, the listener is wherever it stands. */
    public Entity avatar;
    public final List<Source> sources = new ArrayList<>();
    private final Vector2 tmp = new Vector2();

    public AudioDemoScene(Path assetsDir) {
        this.assetsDir = assetsDir;
    }

    /** Throws the world away and starts again aboard the corvette, listening from the deck. */
    public void load() {
        dispose();
        world = new GameWorld();
        world.loadContent(assetsDir, assetsDir.resolve("no-mods-here"));
        hull = GameScenario.PLAYER_SHIP;
        fitBulkheads(world.getContent().defs.require("ship", hull, ShipDef.class).chassis);
        ship = world.getContent().factory.spawnShip(hull, 0f, 0f, 0f);
        ship.add(new PlayerControllerComponent());
        ShipConsoles.install(ship);
        Bridge.install(ship);
        FlightControlComponent controls = ship.getComponent(FlightControlComponent.class);
        if (controls != null) controls.rotationAssist = true;

        // The avatar and crew as a new game puts them aboard, the avatar standing rather than seated
        ShipGridComponent grid = grid();
        List<int[]> floor = GameScenario.floorTiles(grid);
        int[] seat = GameScenario.besideHelm(ship, floor);
        if (seat != null) {
            avatar = CrewFactory.spawnPlayer(world.crew, ship, "You", seat[0], seat[1]);
            floor.remove(seat);
        }
        CrewRole[] roles = {CrewRole.ENGINEER, CrewRole.CREWMAN, CrewRole.MARINE};
        String[] names = {"Ada", "Bea", "Cole"};
        for (int i = 0; i < roles.length && i + 1 < floor.size(); i++) {
            int[] t = floor.get((i + 1) * floor.size() / (roles.length + 1));
            CrewFactory.spawn(world.crew, ship, names[i], roles[i], 0, t[0], t[1]);
        }
        world.audioListener.setMode(AudioListenerComponent.Mode.INTERIOR);
        placeSources(grid, seat);
    }

    /** Adds {@link #BULKHEADS} to this world's copy of {@code chassis}, unless it has walls of its own already. */
    private void fitBulkheads(String chassis) {
        ChassisDef layout = world.getContent().defs.require("chassis", chassis, ChassisDef.class);
        if (!layout.edgeWalls.isEmpty()) return;
        for (int[] b : BULKHEADS) {
            for (int y = BULKHEAD_FROM; y <= BULKHEAD_TO; y++) {
                ChassisDef.EdgeWall w = new ChassisDef.EdgeWall();
                w.x = b[0];
                w.y = y;
                w.side = "LEFT";
                w.bulkhead = y == b[1];
                layout.edgeWalls.add(w);
            }
        }
    }

    /**
     * The reactor hum at the reactor, and an alarm, a fire and a breach hiss spread as far from it,
     * from the avatar and from each other as the deck allows (systems and cargo bays included), so
     * walls stand between most of them.
     */
    private void placeSources(ShipGridComponent grid, int[] start) {
        sources.clear();
        List<int[]> floor = new ArrayList<>();
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                if (grid.getTile(x, y).isFloor()) floor.add(new int[]{x, y});
            }
        }
        if (floor.isEmpty()) return;
        List<float[]> taken = new ArrayList<>();
        if (start != null) taken.add(new float[]{start[0], start[1]});
        SubsystemComponent reactor = reactor();
        float[] hum = reactor == null ? farthest(floor, taken)
                : new float[]{reactor.footprint.originX + (reactor.footprint.width() - 1) / 2f,
                reactor.footprint.originY + (reactor.footprint.height() - 1) / 2f};
        add(grid, "Reactor hum", AudioEventCatalog.REACTOR_HUM, hum, taken);
        add(grid, "Alarm", AudioEventCatalog.ALARM, farthest(floor, taken), taken);
        add(grid, "Fire", AudioEventCatalog.FIRE, farthest(floor, taken), taken);
        add(grid, "Breach hiss", AudioEventCatalog.BREACH_HISS, farthest(floor, taken), taken);
    }

    /** The floor tile farthest from everything in {@code taken}. */
    private static float[] farthest(List<int[]> floor, List<float[]> taken) {
        float[] best = {floor.get(0)[0], floor.get(0)[1]};
        float bestD = -1f;
        for (int[] t : floor) {
            float d = Float.MAX_VALUE;
            for (float[] o : taken) d = Math.min(d, Vector2.dst2(t[0], t[1], o[0], o[1]));
            if (taken.isEmpty()) d = 0f;
            if (d > bestD) {
                bestD = d;
                best = new float[]{t[0], t[1]};
            }
        }
        return best;
    }

    private void add(ShipGridComponent grid, String name, String event, float[] tile, List<float[]> taken) {
        taken.add(tile);
        float x = grid.gridOriginLocal.x + (tile[0] + 0.5f) * grid.tileSize;
        float y = grid.gridOriginLocal.y + (tile[1] + 0.5f) * grid.tileSize;
        Entity e = world.engine.getEcsEngine().createEntity();
        AudioEmitterComponent em = AudioEmitterComponent.interior(event, ship, x, y);
        e.add(em);
        world.engine.getEcsEngine().addEntity(e);
        sources.add(new Source(name, em));
    }

    private SubsystemComponent reactor() {
        ShipSubsystemRegistryComponent registry = ship.getComponent(ShipSubsystemRegistryComponent.class);
        if (registry == null) return null;
        for (Entity e : registry.subsystems) {
            SubsystemComponent s = e.getComponent(SubsystemComponent.class);
            if (s != null && s.type == SubsystemComponent.SubsystemType.REACTOR && s.footprint != null) return s;
        }
        return null;
    }

    public ShipGridComponent grid() {
        return ship.getComponent(ShipGridComponent.class);
    }

    public void update(float delta) {
        world.update(delta);
    }

    public boolean listeningInside() {
        return world.audioListener.getMode() == AudioListenerComponent.Mode.INTERIOR;
    }

    /** Hears from the avatar on the deck, or from the ship's middle as when piloting. */
    public void toggleListening() {
        world.audioListener.setMode(listeningInside() ? AudioListenerComponent.Mode.EXTERIOR : AudioListenerComponent.Mode.INTERIOR);
    }

    /** Walks the avatar toward screen ({@code screenX}, {@code screenY}) as the game's keys do; inside only. */
    public void walk(float screenX, float screenY) {
        PlayerAvatarComponent walker = avatar == null ? null : avatar.getComponent(PlayerAvatarComponent.class);
        if (walker == null) return;
        if (!listeningInside()) screenX = screenY = 0f;
        DeckView.walk(walker, world.docking.bodyOf(ship), screenX, screenY, WALK_SPEED);
    }

    /** Switches hand-placed sound {@code i} on or off. */
    public void toggleSource(int i) {
        if (i < 0 || i >= sources.size()) return;
        AudioEmitterComponent em = sources.get(i).emitter;
        if (em.instance != null && em.instance.isPlaying()) {
            em.instance.stop();
            em.autoStart = false;
        } else {
            em.play();
        }
    }

    /** The avatar's spot in the ship's local meters, or null without one. */
    public Vector2 avatarLocal(Vector2 out) {
        GridPositionComponent p = avatar == null ? null : avatar.getComponent(GridPositionComponent.class);
        if (p == null || p.ship != ship) return null;
        return p.toShipLocal(grid(), out);
    }

    /**
     * Seals the door nearest the avatar, or opens it if sealed; the game's hazard audio plays its
     * sound. Returns what happened, for the screen.
     */
    public String toggleNearestDoor() {
        Vector2 at = avatarLocal(tmp);
        if (at == null) return "No one aboard to reach a door";
        ShipGridComponent g = grid();
        float s = g.tileSize, ox = g.gridOriginLocal.x, oy = g.gridOriginLocal.y;
        EdgeWall best = null;
        float bestD = DOOR_REACH;
        for (int y = 0; y <= g.height; y++) {
            for (int x = 0; x < g.width; x++) {
                EdgeWall w = g.edgeH[y][x];
                if (w == null || !w.hasBulkhead) continue;
                float d = Vector2.dst(at.x, at.y, ox + (x + 0.5f) * s, oy + y * s);
                if (d < bestD) {
                    bestD = d;
                    best = w;
                }
            }
        }
        for (int y = 0; y < g.height; y++) {
            for (int x = 0; x <= g.width; x++) {
                EdgeWall w = g.edgeV[y][x];
                if (w == null || !w.hasBulkhead) continue;
                float d = Vector2.dst(at.x, at.y, ox + x * s, oy + (y + 0.5f) * s);
                if (d < bestD) {
                    bestD = d;
                    best = w;
                }
            }
        }
        if (best == null) return "No door within reach";
        best.bulkheadSealed = !best.bulkheadSealed;
        g.gridDirty = true;
        return best.bulkheadSealed ? "Door sealed" : "Door open";
    }

    /** A laser shot just off the bow: an exterior sound. */
    public void laser() {
        Vector2 at = shipCenter(tmp);
        world.audio.playOneShot(AudioEventCatalog.LASER, at.x + grid().width * grid().tileSize / 2f + 2f, at.y);
    }

    /** Something strikes the hull at a random spot: heard through the frame inside. */
    public void hullHit() {
        Vector2 at = shipCenter(tmp);
        ShipGridComponent g = grid();
        float a = MathUtils.random(MathUtils.PI2);
        world.audio.playOneShot(AudioEventCatalog.HULL_IMPACT, at.x + MathUtils.cos(a) * g.width * g.tileSize / 2f,
                at.y + MathUtils.sin(a) * g.height * g.tileSize / 2f, MathUtils.random(0.4f, 1f));
    }

    private Vector2 shipCenter(Vector2 out) {
        Body body = world.docking.bodyOf(ship);
        return body == null ? out.setZero() : PhysicsManager.getPosition(body, out);
    }

    public void dispose() {
        sources.clear();
        if (world != null) world.dispose();
        world = null;
        ship = avatar = null;
    }
}

package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.audiospatial.backend.AudioEventCatalog;
import com.ringsofiron.audiospatial.components.AudioEmitterComponent;
import com.ringsofiron.interiorgrid.EdgeWall;
import com.ringsofiron.interiorgrid.GridLink;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.moddingdata.defs.ChassisDef;
import com.ringsofiron.moddingdata.defs.ShipDef;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.stationsdocking.StationComponent;
import com.ringsofiron.coreengine.components.UuidComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * The audio scenario's bench, headless: the player's avatar aboard a hull from the data, heard through
 * the game's own audio systems (the listener on the avatar, occlusion through walls and doors, thin
 * air, the crew's footsteps and voices, fires, alarms, breaches and doors). It starts aboard the audio
 * demo ship ({@link #DEMO_HULL}), whose rooms are laid out to show each of these, and cycles through
 * every ship hull (the player's ship is exchanged for one, as a yard does) and every station layout
 * (the player docks at the nearest station built to it, and the avatar steps aboard).
 *
 * <p>Its controls start the game's own hazards rather than playing sounds of their own: a fire, a
 * vented room, the reactors switched off. The reactor's hum is the one sound it places itself, as the
 * game gives reactors none yet; it plays while the reactor is switched on and working.
 */
public class AudioLab {
    /** The hull the scenario starts aboard. */
    public static final String DEMO_HULL = "audio_demo";
    /** How near the avatar must stand to a door to seal or open it, meters. */
    public static final float DOOR_REACH = 2f;
    /** On the demo hull: where the fire is lit (the carpeted quarters) and the hull tile holed (under the lab). */
    static final int[] DEMO_FIRE = {12, 9}, DEMO_VENT = {18, 0};
    /** On the demo hull: the lab's door onto the corridor (the top edge of this tile), shut at the start. */
    static final int[] DEMO_LAB_DOOR = {18, 4};

    private static final Family EMITTERS = Family.all(AudioEmitterComponent.class).get();
    private static final Family STATIONS = Family.all(StationComponent.class, ShipGridComponent.class).get();

    /** A reactor's hum: placed at the reactor, playing while it is switched on and working. */
    public static final class Hum {
        public final Entity craft;
        public final SubsystemComponent reactor;
        public final AudioEmitterComponent emitter;
        final Entity entity;

        Hum(Entity craft, SubsystemComponent reactor, AudioEmitterComponent emitter, Entity entity) {
            this.craft = craft;
            this.reactor = reactor;
            this.emitter = emitter;
            this.entity = entity;
        }
    }

    public final GameWorld world;
    public final GameScenario game;
    public final WorldDebug tools;
    /** The hull shown: a ship id, or a station layout's chassis id. */
    public String hull;
    public final List<Hum> hums = new ArrayList<>();
    private int[] fireTile, ventTile;
    private boolean venting;
    private final Vector2 tmp = new Vector2();

    public AudioLab(GameWorld world, GameScenario game, WorldDebug tools) {
        this.world = world;
        this.game = game;
        this.tools = tools;
    }

    /** The demo hull, then every other ship with a deck plan, then every station layout a station in the world uses. */
    public List<String> hulls() {
        List<String> out = new ArrayList<>();
        if (world.getContent().defs.has("ship", DEMO_HULL)) out.add(DEMO_HULL);
        for (String id : world.getContent().defs.ids("ship")) {
            ShipDef def = world.getContent().defs.get("ship", id, ShipDef.class);
            if (!out.contains(id) && def != null && def.chassis != null && world.getContent().defs.has("chassis", def.chassis)) out.add(id);
        }
        for (String id : world.getContent().defs.ids("chassis")) {
            ChassisDef def = world.getContent().defs.get("chassis", id, ChassisDef.class);
            if (def != null && def.station && station(id) != null) out.add(id);
        }
        return out;
    }

    /** Whether {@code id} names a station layout rather than a ship. */
    public boolean isStation(String id) {
        return !world.getContent().defs.has("ship", id);
    }

    /** Moves on to the next hull in {@link #hulls()} ({@code step} 1) or the one before (-1). Returns what happened. */
    public String cycle(int step) {
        List<String> all = hulls();
        int i = all.indexOf(hull);
        return show(all.get(Math.floorMod(i + step, all.size())));
    }

    /**
     * Puts the avatar aboard {@code id}: a new ship of that hull in place of the player's, or the
     * nearest station built to that layout, with the player's ship docked to it. Returns what happened.
     */
    public String show(String id) {
        if (!hulls().contains(id)) return "No hull " + id;
        Entity avatar = game.avatar;
        venting = false;
        clearHums();
        if (isStation(id)) {
            Entity station = station(id);
            UuidComponent uuid = station.getComponent(UuidComponent.class);
            if (uuid == null || !game.dockAt(uuid.id)) return "Couldn't dock at the " + hullName(id);
            stepAboard(avatar, station);
        } else {
            if (!id.equals(hullOf(game.player))) {
                double credits = game.player.getComponent(com.ringsofiron.macrosim.economy.TraderComponent.class) == null ? 0
                        : game.player.getComponent(com.ringsofiron.macrosim.economy.TraderComponent.class).credits;
                game.player = world.shipyard.exchange(game.player, id, credits);
                Bridge.install(game.player);
            } else {
                world.docking.undock(game.player);
            }
            stepAboard(avatar, game.player);
        }
        hull = id;
        Entity craft = craft();
        ShipGridComponent grid = grid();
        int[] at = avatarTile();
        if (DEMO_HULL.equals(id)) {
            fireTile = DEMO_FIRE;
            ventTile = DEMO_VENT;
            grid.edgeH[DEMO_LAB_DOOR[1] + 1][DEMO_LAB_DOOR[0]].bulkheadSealed = true;
            grid.gridDirty = true;
        } else {
            fireTile = farthestBurnable(grid, at);
            ventTile = hullBeside(grid, farthestFloor(grid, at));
        }
        placeHums(craft);
        return "Aboard the " + hullName(id);
    }

    /** The ship id or station layout of {@code craft}, or null. */
    private String hullOf(Entity craft) {
        StationComponent s = craft.getComponent(StationComponent.class);
        if (s != null) return s.blueprint;
        com.ringsofiron.moddingdata.components.DefRefComponent ref = craft.getComponent(com.ringsofiron.moddingdata.components.DefRefComponent.class);
        return ref == null ? null : ref.defId;
    }

    /** The nearest station built to layout {@code chassis}, or null. */
    private Entity station(String chassis) {
        Entity best = null;
        float bestD = Float.MAX_VALUE;
        Vector2 at = shipCenter(game.player, new Vector2());
        for (Entity e : world.engine.getEcsEngine().getEntitiesFor(STATIONS)) {
            if (!chassis.equals(e.getComponent(StationComponent.class).blueprint)) continue;
            Body body = world.docking.bodyOf(e);
            float d = body == null ? Float.MAX_VALUE / 2 : PhysicsManager.getPosition(body, tmp).dst2(at);
            if (best == null || d < bestD) {
                best = e;
                bestD = d;
            }
        }
        return best;
    }

    /** Puts {@code avatar} aboard {@code craft}: by the port to the player's ship when docked to it, else on the first open floor. */
    private void stepAboard(Entity avatar, Entity craft) {
        if (avatar == null) return;
        ShipGridComponent grid = craft.getComponent(ShipGridComponent.class);
        GridPositionComponent pos = world.crew.position(avatar);
        int[] tile = null;
        for (GridLink link : grid.links) {
            if (link.otherShip == game.player && craft != game.player) tile = new int[]{link.x, link.y};
        }
        if (tile == null) {
            List<int[]> floor = GameScenario.floorTiles(grid);
            tile = craft == game.player ? GameScenario.besideHelm(craft, floor) : floor.isEmpty() ? null : floor.get(0);
        }
        world.crew.releaseTask(avatar);
        pos.stop();
        pos.crossing = null;
        pos.walkway = null;
        pos.setShip(craft);
        if (tile != null) pos.setTile(tile[0], tile[1]);
        world.bridge.stand();
    }

    /** The craft the avatar is aboard. */
    public Entity craft() {
        GridPositionComponent p = game.avatar == null ? null : world.crew.position(game.avatar);
        return p == null || p.ship == null ? game.player : p.ship;
    }

    public ShipGridComponent grid() {
        return craft().getComponent(ShipGridComponent.class);
    }

    private int[] avatarTile() {
        GridPositionComponent p = game.avatar == null ? null : world.crew.position(game.avatar);
        return p == null ? null : new int[]{p.tileX(), p.tileY()};
    }

    /** The name of hull {@code id}, as the data gives it. */
    public String hullName(String id) {
        if (world.getContent().defs.has("ship", id)) return world.getContent().defs.require("ship", id, ShipDef.class).name;
        ChassisDef def = world.getContent().defs.get("chassis", id, ChassisDef.class);
        return def == null || def.name == null ? id : def.name;
    }

    private void clearHums() {
        for (Hum h : hums) {
            if (h.emitter.instance != null) h.emitter.instance.stop();
            world.engine.getEcsEngine().removeEntity(h.entity);
        }
        hums.clear();
    }

    /** A hum at each reactor aboard {@code craft}, in the middle of its footprint. */
    private void placeHums(Entity craft) {
        ShipSubsystemRegistryComponent registry = craft.getComponent(ShipSubsystemRegistryComponent.class);
        if (registry == null) return;
        ShipGridComponent grid = craft.getComponent(ShipGridComponent.class);
        for (Entity e : registry.subsystems) {
            SubsystemComponent s = e.getComponent(SubsystemComponent.class);
            if (s == null || s.type != SubsystemComponent.SubsystemType.REACTOR || s.footprint == null) continue;
            float x = grid.gridOriginLocal.x + (s.footprint.originX + s.footprint.width() / 2f) * grid.tileSize;
            float y = grid.gridOriginLocal.y + (s.footprint.originY + s.footprint.height() / 2f) * grid.tileSize;
            Entity hum = world.engine.getEcsEngine().createEntity();
            AudioEmitterComponent em = AudioEmitterComponent.interior(AudioEventCatalog.REACTOR_HUM, craft, x, y);
            hum.add(em);
            world.engine.getEcsEngine().addEntity(hum);
            hums.add(new Hum(craft, s, em, hum));
        }
    }

    /** Each frame, after the world: every reactor's hum on or off with its reactor. */
    public void update() {
        for (Hum h : hums) {
            boolean on = h.reactor.switchedOn && h.reactor.isOperational;
            boolean playing = h.emitter.instance != null && h.emitter.instance.isPlaying();
            if (on && !playing) h.emitter.play();
            else if (!on && playing) {
                h.emitter.instance.stop();
                h.emitter.autoStart = false;
            }
        }
    }

    /** The burnable floor tile farthest from {@code from}, or null when nothing aboard burns. */
    private static int[] farthestBurnable(ShipGridComponent grid, int[] from) {
        int[] best = null;
        float bestD = -1f;
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                Tile t = grid.getTile(x, y);
                if (!t.isFloor() || t.fuel <= 0f) continue;
                float d = from == null ? 0f : Vector2.dst2(x, y, from[0], from[1]);
                if (d > bestD) {
                    bestD = d;
                    best = new int[]{x, y};
                }
            }
        }
        return best;
    }

    private static int[] farthestFloor(ShipGridComponent grid, int[] from) {
        int[] best = null;
        float bestD = -1f;
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                if (!grid.getTile(x, y).isFloor()) continue;
                float d = from == null ? 0f : Vector2.dst2(x, y, from[0], from[1]);
                if (d > bestD) {
                    bestD = d;
                    best = new int[]{x, y};
                }
            }
        }
        return best;
    }

    /** A hull tile beside floor tile {@code at} with space beyond it, or null. */
    private static int[] hullBeside(ShipGridComponent grid, int[] at) {
        if (at == null) return null;
        int[][] steps = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};
        for (int[] s : steps) {
            int x = at[0] + s[0], y = at[1] + s[1];
            if (!grid.inBounds(x, y) || grid.getTile(x, y).wallType != Tile.WallType.HULL) continue;
            int ox = x + s[0], oy = y + s[1];
            if (!grid.inBounds(ox, oy) || grid.getTile(ox, oy).isVoid()) return new int[]{x, y};
        }
        return null;
    }

    /** Every sound playing aboard the craft the avatar is on: the game's and the hums. */
    public List<AudioEmitterComponent> soundsAboard(List<AudioEmitterComponent> out) {
        out.clear();
        Entity craft = craft();
        for (Entity e : world.engine.getEcsEngine().getEntitiesFor(EMITTERS)) {
            AudioEmitterComponent em = e.getComponent(AudioEmitterComponent.class);
            if (em.ship == craft && em.instance != null && em.instance.isPlaying()) out.add(em);
        }
        return out;
    }

    /** A sound's name for the screen: the last part of its event path, in words. */
    public static String soundName(AudioEmitterComponent em) {
        String p = em.eventPath == null ? "sound" : em.eventPath;
        return p.substring(p.lastIndexOf('/') + 1).replace('_', ' ');
    }

    /** Switches the reactors aboard off, or back on: their hum, and the power to doors and lights. */
    public String toggleReactor() {
        if (hums.isEmpty()) return "No reactor aboard";
        return tools.toggleReactors(craft()) ? "Reactor on" : "Reactor off";
    }

    /** Lights a fire (in the demo hull's quarters), or puts every fire aboard out. */
    public String toggleFire() {
        if (burning()) {
            tools.extinguish(craft());
            return "Fire out";
        }
        if (fireTile == null) return "Nothing aboard burns";
        return tools.ignite(craft(), fireTile[0], fireTile[1]);
    }

    public boolean burning() {
        ShipGridComponent g = grid();
        for (int y = 0; y < g.height; y++) for (int x = 0; x < g.width; x++) if (g.getTile(x, y).isBurning()) return true;
        return false;
    }

    /**
     * Holes the hull (under the demo hull's vacuum lab) so the room vents, or patches it and fills
     * every room with fresh air again.
     */
    public String toggleVent() {
        if (ventTile == null) return "No hull to vent through";
        if (venting) {
            grid().getTile(ventTile[0], ventTile[1]).setIntegrity(1f);
            grid().gridDirty = true;
            tools.freshAir(craft());
            venting = false;
            return "Hull patched, fresh air";
        }
        venting = true;
        return tools.breach(craft(), ventTile[0], ventTile[1]);
    }

    public boolean venting() {
        return venting;
    }

    /** The avatar's spot in its craft's local meters, or null without one. */
    public Vector2 avatarLocal(Vector2 out) {
        GridPositionComponent p = game.avatar == null ? null : world.crew.position(game.avatar);
        if (p == null || p.ship == null) return null;
        return p.toShipLocal(p.ship.getComponent(ShipGridComponent.class), out);
    }

    /**
     * Seals the door nearest the avatar, or opens it if sealed; the game's hazard audio plays its
     * sound. Returns what happened.
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

    /** A laser shot just off the craft: an exterior sound. */
    public void laser() {
        Vector2 at = shipCenter(craft(), tmp);
        world.audio.playOneShot(AudioEventCatalog.LASER, at.x + grid().width * grid().tileSize / 2f + 2f, at.y);
    }

    /** Something strikes the hull at a random spot: heard through the frame inside. */
    public void hullHit() {
        Vector2 at = shipCenter(craft(), tmp);
        ShipGridComponent g = grid();
        float a = MathUtils.random(MathUtils.PI2);
        world.audio.playOneShot(AudioEventCatalog.HULL_IMPACT, at.x + MathUtils.cos(a) * g.width * g.tileSize / 2f,
                at.y + MathUtils.sin(a) * g.height * g.tileSize / 2f, MathUtils.random(0.4f, 1f));
    }

    private Vector2 shipCenter(Entity craft, Vector2 out) {
        Body body = world.docking.bodyOf(craft);
        return body == null ? out.setZero() : PhysicsManager.getPosition(body, out);
    }
}

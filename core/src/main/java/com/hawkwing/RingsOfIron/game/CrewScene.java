package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.aicrew.CrewRole;
import com.ringsofiron.interiorgrid.FloorTiles;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.Tile;

import java.util.List;

/**
 * The crew scenario, in the career itself: the old crew demo's run of trouble played on the
 * player's own corvette and crew, to watch the crew answer it as they do in the game. A fire in a
 * room, then a hit on the reactor, a hull breach, and boarders: each through the game's own systems
 * ({@link WorldDebug}), told as notices. Built on {@link InteriorScene}, so the ship is alone and
 * pinned, a medic is aboard, and every interior tool works on it too.
 *
 * <p>Headless: the game's debug menu loads it and calls {@link #update} after each world step; tests
 * run it the same way. {@link #restart} plays the trouble again on a fresh corvette.
 */
public class CrewScene {
    /** When each piece of trouble comes, sim seconds after the start. */
    public static final float FIRE_AT = 2f, REACTOR_AT = 5f, BREACH_AT = 10f, BOARDERS_AT = 40f;
    /** What the hit leaves of the reactor. */
    public static final float REACTOR_LEFT = 0.4f;
    public static final int BOARDER_COUNT = 2;

    public final InteriorScene interior;
    public final GameWorld world;
    private double start;
    private int next;
    /** Where the trouble struck, tile coordinates; null until it has. */
    public int[] fireAt, breachAt;
    public Entity reactor;
    public final java.util.ArrayList<Entity> boarders = new java.util.ArrayList<>();

    public CrewScene(GameScenario game, WorldDebug tools) {
        this.interior = new InteriorScene(game, GameScenario.PLAYER_SHIP, tools);
        this.world = game.world;
        begin();
    }

    /** A fresh corvette and crew, and the trouble from the top. */
    public void restart() {
        interior.setHull(GameScenario.PLAYER_SHIP);
        begin();
    }

    private void begin() {
        start = world.engine.getSimSeconds();
        next = 0;
        fireAt = breachAt = null;
        reactor = null;
        boarders.clear();
        say("Crew drill: trouble starts in " + (int) FIRE_AT + " s");
    }

    /** Sim seconds since the trouble started. */
    public float time() {
        return (float) (world.engine.getSimSeconds() - start);
    }

    /** After each world step: the next piece of trouble when it is due, then the interior scenario's own update. */
    public void update() {
        float t = time();
        if (next == 0 && t >= FIRE_AT) {
            startFire();
            next++;
        } else if (next == 1 && t >= REACTOR_AT) {
            hitReactor();
            next++;
        } else if (next == 2 && t >= BREACH_AT) {
            breachHull();
            next++;
        } else if (next == 3 && t >= BOARDERS_AT) {
            board();
            next++;
        }
        interior.update();
    }

    /** True once all the trouble has come. */
    public boolean finished() {
        return next > 3;
    }

    private Entity ship() {
        return interior.ship();
    }

    private ShipGridComponent grid() {
        return ship().getComponent(ShipGridComponent.class);
    }

    /** A fire on the flammable tile farthest from the player's character, so the crew go to it. */
    void startFire() {
        ShipGridComponent g = grid();
        int[] avatar = avatarTile();
        int[] best = null;
        float far = -1f;
        for (int[] t : FloorTiles.of(g)) {
            if (!g.getTile(t[0], t[1]).isFlammable()) continue;
            float d = avatar == null ? 0f : (t[0] - avatar[0]) * (t[0] - avatar[0]) + (t[1] - avatar[1]) * (t[1] - avatar[1]);
            if (d > far) {
                far = d;
                best = t;
            }
        }
        if (best == null) {
            say("Crew drill: nothing aboard will burn");
            return;
        }
        interior.debug.ignite(ship(), best[0], best[1]);
        fireAt = best;
        say("Fire at " + best[0] + "," + best[1]);
    }

    /** The reactor knocked down to {@link #REACTOR_LEFT}. */
    void hitReactor() {
        ShipSubsystemRegistryComponent registry = ship().getComponent(ShipSubsystemRegistryComponent.class);
        if (registry != null) {
            for (Entity e : registry.subsystems) {
                SubsystemComponent s = e == null ? null : e.getComponent(SubsystemComponent.class);
                if (s == null || s.type != SubsystemComponent.SubsystemType.REACTOR) continue;
                s.health = Math.min(s.health, REACTOR_LEFT);
                grid().gridDirty = true;
                reactor = e;
                break;
            }
        }
        say(reactor != null ? String.format("Reactor hit: health %.0f%%", REACTOR_LEFT * 100f) : "Crew drill: no reactor aboard");
    }

    /** A hole in the hull beside a room, about midships, to vent it. */
    void breachHull() {
        int[] t = hullBesideDeck(grid());
        if (t == null) {
            say("Crew drill: no hull to breach");
            return;
        }
        interior.debug.breach(ship(), t[0], t[1]);
        breachAt = t;
        say("Hull breach at " + t[0] + "," + t[1]);
    }

    /** Pirates aboard, at the far end of the deck from the middle. */
    void board() {
        List<int[]> floor = FloorTiles.of(grid());
        int pirates = world.standing.factions().index(GameScenario.PIRATES);
        int own = world.crew.board(ship()).faction;
        int faction = pirates >= 0 && pirates != own ? pirates : own + 1;
        for (int i = floor.size() - 1; i >= 0 && boarders.size() < BOARDER_COUNT; i--) {
            int[] t = floor.get(i);
            Entity e = interior.debug.spawnCrew(ship(), CrewRole.MARINE, faction, t[0], t[1]);
            if (e != null) {
                world.crew.crew(e).name = "Boarder " + (boarders.size() + 1);
                boarders.add(e);
            }
        }
        say(boarders.isEmpty() ? "Crew drill: nowhere for boarders" : "Boarders aboard!");
    }

    /**
     * The hull tile nearest the middle of the grid with open deck beside it: a breach there opens a
     * room to space rather than an empty corner.
     */
    static int[] hullBesideDeck(ShipGridComponent g) {
        int[] best = null;
        float near = Float.MAX_VALUE;
        float cx = g.width / 2f, cy = g.height / 2f;
        for (int y = 0; y < g.height; y++) {
            for (int x = 0; x < g.width; x++) {
                Tile t = g.getTile(x, y);
                if (t.wallType != Tile.WallType.HULL || t.getIntegrity() <= 0f || !besideAir(g, x, y)) continue;
                float d = (x - cx) * (x - cx) + (y - cy) * (y - cy);
                if (d < near) {
                    near = d;
                    best = new int[]{x, y};
                }
            }
        }
        return best;
    }

    private static boolean besideAir(ShipGridComponent g, int x, int y) {
        int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : around) {
            int nx = x + d[0], ny = y + d[1];
            if (g.inBounds(nx, ny) && g.getTile(nx, ny).holdsAir()) return true;
        }
        return false;
    }

    private int[] avatarTile() {
        Entity avatar = interior.game.avatar;
        if (avatar == null || world.crew.position(avatar) == null) return null;
        var p = world.crew.position(avatar);
        return p.ship == ship() ? new int[]{p.tileX(), p.tileY()} : null;
    }

    private void say(String text) {
        interior.watch.note(text);
        world.notices.post(text, NoticeLog.Kind.WARNING);
    }
}

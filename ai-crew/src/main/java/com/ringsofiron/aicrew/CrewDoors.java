package com.ringsofiron.aicrew;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.aicrew.nav.GridNavGraph;
import com.ringsofiron.aicrew.nav.GridNode;
import com.ringsofiron.interiorgrid.AirflowEdge;
import com.ringsofiron.interiorgrid.Compartment;
import com.ringsofiron.interiorgrid.EdgeWall;
import com.ringsofiron.interiorgrid.GridLink;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Tile;

/**
 * Bulkhead doors crew open on their way and shut behind them.
 *
 * <p>An AI crew member whose path runs through a shut door between two rooms stops at it, opens it
 * ({@link #OPEN_SECONDS}), walks through, and the door shuts again once no one is in the doorway or
 * about to step through. Only doors a crew member opened are shut again: one left open by hand
 * stays open. Who may open which door is {@link #mayOpen}: not the doors of a hostile craft (those
 * are forced), unless {@link CrewService#doorRights} lets them through (a boarding party), and not a door onto thin air from breathable air, except by a suited crew member
 * into a room that is not open to space (the two rooms share their air, and life support makes
 * it up).
 *
 * <p>Crew whose {@link CrewService#doorRights} have them force their way ({@link Forcing}: boarders
 * storming a craft) pry, cut or hack through a hostile craft's doors and an airlock's held shut for
 * its cycle, for as long as that takes; a door forced open is left open.
 *
 * <p>Owned by the {@link CrewService}; the {@link CrewMovementSystem} opens doors and shuts them
 * each tick. Which doors are held open is not saved: after a load a door that was held stays open.
 */
public final class CrewDoors {

    /** Seconds a crew member spends opening a shut door. */
    public static final float OPEN_SECONDS = 1f;
    /** Seconds a door stays open at least, so the one who opened it gets through. */
    public static final float MIN_OPEN_SECONDS = 0.5f;
    /** How far into a doorway a crew member keeps the door from shutting, tiles from the door. */
    static final float DOORWAY = CrewBody.RADIUS + 0.05f;

    /** How a crew member forces a door: how long it takes, and what they are doing, e.g. "cutting through a door". */
    public static final class Forcing {
        public final float seconds;
        public final String doing;

        public Forcing(float seconds, String doing) {
            this.seconds = seconds;
            this.doing = doing;
        }
    }

    /** A door a crew member opened, to shut once the way is clear. */
    private static final class Held {
        final ShipGridComponent grid;
        final EdgeWall door;
        final int x, y, nx, ny;
        float open;

        Held(ShipGridComponent grid, EdgeWall door, int x, int y, int nx, int ny) {
            this.grid = grid;
            this.door = door;
            this.x = x;
            this.y = y;
            this.nx = nx;
            this.ny = ny;
        }
    }

    private final CrewService service;
    private final Array<Held> held = new Array<>();

    CrewDoors(CrewService service) {
        this.service = service;
    }

    /**
     * True if {@code crew} may open the shut door between (x, y) and (nx, ny) on {@code ship}, going
     * that way: it is not an airlock's door locked for its cycle ({@link EdgeWall#locked}), the
     * craft's crew are not hostile to them, and they are not stepping from breathable
     * air into air too thin to breathe (which would let it out), unless they breathe from a suit and
     * the room beyond is not open to space.
     */
    public boolean mayOpen(Entity crew, Entity ship, int x, int y, int nx, int ny) {
        CrewMemberComponent c = service.crew(crew);
        ShipGridComponent grid = service.grid(ship);
        if (c == null || grid == null) return false;
        // An airlock's door held shut for its cycle opens for no one, and a hostile craft's are forced,
        // by those who force their way
        EdgeWall wall = grid.getEdgeBetween(x, y, nx, ny);
        if (mustForce(crew, ship, c, wall) && service.doorRights.forcing(crew, ship, wall) == null) return false;
        boolean hereThick = grid.pressureRatioAt(x, y) >= TaskRules.SAFE_PRESSURE;
        boolean thereThick = grid.pressureRatioAt(nx, ny) >= TaskRules.SAFE_PRESSURE;
        if (!hereThick || thereThick) return true;
        return c.breathesFromSuit() && !openToSpace(grid, grid.compartmentAt(nx, ny));
    }

    /**
     * True if {@code wall} of {@code ship} is shut against {@code crew}: an airlock's door held for its
     * cycle, or a door of a craft whose crew are hostile to them that they have no rights to.
     */
    private boolean mustForce(Entity crew, Entity ship, CrewMemberComponent c, EdgeWall wall) {
        if (wall != null && wall.locked) return true;
        ShipTaskBoardComponent board = service.board(ship);
        return board != null && service.hostility.hostile(board.faction, c.faction) && !service.doorRights.mayPass(crew, ship);
    }

    /** True if {@code room} has a hole to space (not just a leak), or is no room at all. */
    static boolean openToSpace(ShipGridComponent grid, Compartment room) {
        if (room == null) return true;
        for (int i = 0; i < grid.airflow.size; i++) {
            AirflowEdge e = grid.airflow.get(i);
            if (e.room == room && e.other == null && e.width > Tile.LEAK_CAP) return true;
        }
        return false;
    }

    /**
     * Works on the shut door between (x, y) and (nx, ny) for {@code dt} seconds, opening it once
     * {@link #OPEN_SECONDS} have been spent on it.
     *
     * @return false if it is not a door {@code crew} may open, true while opening and on the tick it opens
     */
    boolean open(Entity crew, GridPositionComponent p, GridNavGraph nav, int x, int y, int nx, int ny, float dt) {
        EdgeWall door = nav.shutDoor(x, y, nx, ny);
        if (door == null || !nav.isWalkable(x, y) || !nav.isWalkable(nx, ny) || !mayOpen(crew, p.ship, x, y, nx, ny)) {
            p.doorWork = 0f;
            return false;
        }
        Forcing how = mustForce(crew, p.ship, service.crew(crew), door) ? service.doorRights.forcing(crew, p.ship, door) : null;
        p.doorWork += dt;
        if (how != null) service.crew(crew).activity = how.doing;
        if (p.doorWork < (how != null ? how.seconds : OPEN_SECONDS)) return true;
        p.doorWork = 0f;
        door.bulkheadSealed = false;
        if (how != null) {
            // Forced open, it is left so
            door.locked = false;
            service.doorRights.forced(crew, p.ship, door, how);
            return true;
        }
        held.add(new Held(nav.grid, door, x, y, nx, ny));
        return true;
    }

    /** Shuts each held door once it has been open long enough and no one is in its doorway or stepping through. */
    void update(float dt) {
        for (int i = held.size - 1; i >= 0; i--) {
            Held h = held.get(i);
            if (h.door.isDestroyed() || h.door.bulkheadSealed || !h.door.hasBulkhead) {
                held.removeIndex(i);
                continue;
            }
            h.open += dt;
            if (h.open < MIN_OPEN_SECONDS || inUse(h)) continue;
            h.door.bulkheadSealed = true;
            held.removeIndex(i);
        }
    }

    /** True if a crew member stands in the doorway, or is walking through it next. */
    private boolean inUse(Held h) {
        ImmutableArray<Entity> all = service.crewEntities();
        // The door's middle, and which way it faces
        float mx = (h.x + h.nx) * 0.5f + 0.5f, my = (h.y + h.ny) * 0.5f + 0.5f;
        boolean vertical = h.x != h.nx;
        for (int i = 0; i < all.size(); i++) {
            Entity e = all.get(i);
            GridPositionComponent p = service.position(e);
            if (service.grid(p.ship) != h.grid || p.walkway != null) continue;
            float across = vertical ? p.x - mx : p.y - my, along = vertical ? p.y - my : p.x - mx;
            if (Math.abs(across) < DOORWAY && Math.abs(along) < 0.5f + CrewBody.RADIUS) return true;
            if (!p.isMoving()) continue;
            GridNode next = p.path.get(p.pathIndex);
            int cx = p.tileX(), cy = p.tileY();
            if (cx == h.x && cy == h.y && next.x == h.nx && next.y == h.ny) return true;
            if (cx == h.nx && cy == h.ny && next.x == h.x && next.y == h.y) return true;
        }
        return false;
    }

    /**
     * True if a crew member of {@code service} is walking through the docked port opening of
     * {@code link} (either way): out on its walkway, or on either opening tile on their way across.
     */
    public static boolean crossingPort(CrewService service, GridLink link) {
        ImmutableArray<Entity> all = service.crewEntities();
        for (int i = 0; i < all.size(); i++) {
            GridPositionComponent p = service.position(all.get(i));
            if (p.walkway == link || p.walkway == link.mate) return true;
            if (p.crossing != link && p.crossing != link.mate) continue;
            ShipGridComponent grid = service.grid(p.ship);
            int x = p.tileX(), y = p.tileY();
            if (grid == link.grid() && x == link.x && y == link.y) return true;
            if (grid == link.other && x == link.ox && y == link.oy) return true;
        }
        return false;
    }

    /** True if a crew member opened this door and it is waiting to be shut. */
    public boolean isHeld(EdgeWall door) {
        for (int i = 0; i < held.size; i++) if (held.get(i).door == door) return true;
        return false;
    }

    /** Forgets every held door, e.g. after a load replaced every grid. */
    void clear() {
        held.clear();
    }
}

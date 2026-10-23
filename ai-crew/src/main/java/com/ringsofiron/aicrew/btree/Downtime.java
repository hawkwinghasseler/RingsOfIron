package com.ringsofiron.aicrew.btree;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewState;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.aicrew.TaskRules;
import com.ringsofiron.interiorgrid.Furnishing;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Tile;

/**
 * What a crew member standing by aboard their own ship does with the time: after
 * {@link #SETTLE} seconds with nothing to do they go to a furnishing with room (a couch, a card
 * table, the exercise bike) and use it for {@link #SESSION} seconds, earning its
 * {@link com.ringsofiron.interiorgrid.FurnishingType#relax} morale an hour
 * ({@link com.ringsofiron.aicrew.CrewRecord#pendingLeisure}), then stand by for {@link #BREAK}
 * seconds before picking another, a different one where they can. Part of {@link CrewTasks.Idle}:
 * any task, a hazard or battle stations takes them off it at once.
 */
final class Downtime {
    /** Seconds standing by before going to find something to do. */
    static final float SETTLE = 5f;
    /** Seconds a crew member uses a furnishing before getting up. */
    static final float SESSION = 180f;
    /** Seconds standing by between one and the next. */
    static final float BREAK = 20f;
    /** What they are doing on the way to one. */
    static final String HEADING = "heading for ";

    private Furnishing using, last;
    private boolean seated;
    private float idle, session;
    private final Vector2 seat = new Vector2();

    /** The furnishing they are using or on their way to, or null. */
    Furnishing using() {
        return using;
    }

    /**
     * A step of downtime for {@code a}, standing by on a safe tile aboard their own ship. Returns
     * true while it has them going to or using something, so Idle leaves them be.
     */
    boolean step(CrewAgent a) {
        ShipGridComponent grid = a.grid();
        if (grid == null) return false;
        if (using != null && !stillThere(grid, using)) stop(a);
        if (using == null) {
            idle += a.dt;
            if (idle < SETTLE || a.pos.isMoving()) return false;
            if (!choose(a, grid)) {
                idle = SETTLE - BREAK; // nothing free: look again after a break
                return false;
            }
        }
        int tx = using.tileX, ty = using.tileY;
        if (a.pos.tileX() != tx || a.pos.tileY() != ty) {
            seated = false;
            a.crew.activity = HEADING + using.type.the();
            if (!a.pos.isMoving() || a.pos.pathBlocked) {
                if (!a.findPath(a.pos.tileX(), a.pos.tileY(), tx, ty, a.pathScratch)) {
                    stop(a);
                    return false;
                }
                a.pos.setPath(a.pathScratch);
            }
            return true;
        }
        if (a.pos.isMoving()) return true;
        if (!seated) {
            int i = Math.max(0, using.users.indexOf(a.entity, true));
            using.seat(i, seat);
            a.service.physics.standAt(a.entity, seat.x, seat.y);
            seated = true;
        }
        a.crew.activity = using.type.verb;
        a.crew.record().pendingLeisure += using.type.relax * a.dt / 3600f;
        session += a.dt;
        if (session >= SESSION) {
            last = using;
            stop(a);
            idle = SETTLE - BREAK;
        }
        return true;
    }

    /** Gets up and lets the furnishing go: called whenever Idle stops or they stop using it. */
    void stop(CrewAgent a) {
        if (using != null) using.users.removeValue(a.entity, true);
        if (using != null && a.pos.tileX() == using.tileX && a.pos.tileY() == using.tileY) {
            // Back to their feet in the middle of the tile
            a.service.physics.standAt(a.entity, using.tileX + 0.5f, using.tileY + 0.5f);
        }
        using = null;
        seated = false;
        session = 0f;
    }

    /** Called when Idle starts: a fresh wait before looking for something to do. */
    void reset(CrewAgent a) {
        if (a != null) stop(a);
        idle = 0f;
    }

    /** The nearest furnishing aboard with room that they can walk to, claimed; false if none. */
    private boolean choose(CrewAgent a, ShipGridComponent grid) {
        int x = a.pos.tileX(), y = a.pos.tileY();
        for (int pass = 0; pass < 2; pass++) {
            Furnishing avoid = pass == 0 ? last : null;
            if (!a.nearest(x, y, (tx, ty) -> free(grid, tx, ty, avoid) != null, a.pathScratch)) continue;
            int tx = a.pathScratch.size == 0 ? x : a.pathScratch.peek().x, ty = a.pathScratch.size == 0 ? y : a.pathScratch.peek().y;
            Furnishing f = free(grid, tx, ty, avoid);
            if (f == null) continue;
            using = f;
            f.users.add(a.entity);
            session = 0f;
            seated = false;
            if (a.pathScratch.size > 0) a.pos.setPath(a.pathScratch);
            return true;
        }
        return false;
    }

    /** A furnishing on tile (x, y) the crew can use now and that has room, other than {@code avoid}; or null. */
    static Furnishing free(ShipGridComponent grid, int x, int y, Furnishing avoid) {
        Tile t = grid.getTile(x, y);
        if (t == null || t.furnishings.size == 0 || t.isBurning() || !TaskRules.isSafe(grid, x, y)) return null;
        for (int i = 0; i < t.furnishings.size; i++) {
            Furnishing f = t.furnishings.get(i);
            if (f == avoid || !f.isUsable()) continue;
            prune(f);
            if (f.hasRoom()) return f;
        }
        return null;
    }

    /** Lets go the seats of crew no longer on their way to or using {@code f}: knocked down, or gone. */
    private static void prune(Furnishing f) {
        for (int i = f.users.size - 1; i >= 0; i--) {
            Object o = f.users.get(i);
            CrewMemberComponent c = o instanceof Entity ? ((Entity) o).getComponent(CrewMemberComponent.class) : null;
            GridPositionComponent p = o instanceof Entity ? ((Entity) o).getComponent(GridPositionComponent.class) : null;
            boolean still = c != null && p != null && c.state == CrewState.CONSCIOUS
                    && (f.type.verb.equals(c.activity) || (HEADING + f.type.the()).equals(c.activity));
            if (!still) f.users.removeIndex(i);
        }
    }

    /** Whether {@code f} is still aboard and fit to use, and its tile safe. */
    private static boolean stillThere(ShipGridComponent grid, Furnishing f) {
        Tile t = grid.getTile(f.tileX, f.tileY);
        return t != null && t.furnishings.contains(f, true) && f.isUsable() && !t.isBurning() && TaskRules.isSafe(grid, f.tileX, f.tileY);
    }
}

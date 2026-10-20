package com.ringsofiron.aicrew;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.aicrew.nav.GridNavGraph;
import com.ringsofiron.aicrew.nav.GridPathfinder;
import com.ringsofiron.interiorgrid.DamageStates;
import com.ringsofiron.interiorgrid.LeakPatches;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Side;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.interiorgrid.crew.CrewTask;
import com.ringsofiron.interiorgrid.crew.IsolateTankTask;
import com.ringsofiron.interiorgrid.crew.PatchLeakTask;
import com.ringsofiron.interiorgrid.crew.RepairTask;
import com.ringsofiron.interiorgrid.crew.RescueTask;
import com.ringsofiron.interiorgrid.crew.ResetShortTask;

/**
 * What each kind of task needs: whether it still needs doing, where a crew member stands
 * to do it, and which tiles are safe to breathe on.
 */
public final class TaskRules {

    /** Minimum pressure ratio of a safe tile. */
    public static final float SAFE_PRESSURE = 0.75f;
    /** Minimum oxygen ratio of a safe tile. */
    public static final float SAFE_OXYGEN = 0.75f;
    /** Above this temperature a tile is not safe, K. */
    public static final float SAFE_TEMPERATURE = 320f;

    private TaskRules() {}

    /**
     * False once the job is done (fire out, breach sealed, leak patched, system repaired, casualty
     * safe or dead), or can't be done (no patch kits left).
     */
    public static boolean isStillNeeded(CrewTask task, ShipGridComponent grid, CrewService crew) {
        Tile t = grid.getTile(task.x, task.y);
        switch (task.kind) {
            case EXTINGUISH:
                return t != null && t.isBurning();
            case SEAL_BREACH:
                return t != null && t.isHullBreach();
            case PATCH_LEAK: {
                PatchLeakTask p = (PatchLeakTask) task;
                return grid.patchKits > 0 && LeakPatches.leaks(grid, p.x, p.y, p.side);
            }
            case REPAIR: {
                SubsystemComponent s = subsystemOf(task);
                return s != null && s.health < 1f;
            }
            case MAN_CONSOLE: {
                SubsystemComponent s = subsystemOf(task);
                return s != null && !s.isDestroyed();
            }
            case RESET_SHORT: {
                if (((ResetShortTask) task).subsystem == null) return t != null && t.shorted;
                SubsystemComponent s = subsystemOf(task);
                return s != null && s.shorted;
            }
            case ISOLATE_TANK: {
                SubsystemComponent s = subsystemOf(task);
                return s != null && DamageStates.needsIsolating(s);
            }
            case RESCUE: {
                CrewMemberComponent c = crew.crew(((RescueTask) task).casualty);
                // Still needed while being carried: the rescue is under way.
                return c != null && c.state == CrewState.UNCONSCIOUS;
            }
            case TREAT: {
                CrewMemberComponent c = crew.crew(((TreatTask) task).patient);
                return c != null && c.state == CrewState.UNCONSCIOUS && c.carriedBy == null;
            }
            case STABILISE: {
                CrewMemberComponent c = crew.crew(((StabiliseTask) task).patient);
                return c != null && c.state != CrewState.DEAD && c.wound == Wound.CRITICAL && !c.stabilised
                        && c.carriedBy == null;
            }
            case CARRY:
                return ((CarryTask) task).job.isOpen();
            case YARD:
                return ((YardTask) task).job.isOpen();
            case FORCE_DOOR:
                return ((ForceDoorTask) task).job.isOpen();
            case EVACUATE:
                return ((EvacuateTask) task).isOpen();
            case BOARD:
                return ((BoardTask) task).job.isOpen();
            default:
                return true;
        }
    }

    static SubsystemComponent subsystemOf(CrewTask task) {
        Entity e = task instanceof RepairTask ? ((RepairTask) task).subsystem
                : task instanceof ManConsoleTask ? ((ManConsoleTask) task).console
                : task instanceof ResetShortTask ? ((ResetShortTask) task).subsystem
                : task instanceof IsolateTankTask ? ((IsolateTankTask) task).tank : null;
        return e == null ? null : e.getComponent(SubsystemComponent.class);
    }

    /** The tile the task is about right now (a casualty moves while carried). */
    public static int targetX(CrewTask task, CrewService crew) {
        Entity e = mobileTarget(task);
        GridPositionComponent p = e == null ? null : crew.position(e);
        return p != null ? p.tileX() : task.x;
    }

    public static int targetY(CrewTask task, CrewService crew) {
        Entity e = mobileTarget(task);
        GridPositionComponent p = e == null ? null : crew.position(e);
        return p != null ? p.tileY() : task.y;
    }

    /**
     * The craft the task's (x, y) is on, when the task names one: yard work and boarding on the
     * craft worked on, a carry's pick-up on the craft it comes from. Null for a task on the crew
     * member's own craft (or wherever they stand).
     */
    public static Entity siteOf(CrewTask task) {
        if (task instanceof YardTask) return ((YardTask) task).ship;
        if (task instanceof CarryTask) return ((CarryTask) task).from;
        if (task instanceof BoardTask) return ((BoardTask) task).ship;
        return null;
    }

    private static Entity mobileTarget(CrewTask task) {
        if (task instanceof RescueTask) return ((RescueTask) task).casualty;
        if (task instanceof TreatTask) return ((TreatTask) task).patient;
        if (task instanceof StabiliseTask) return ((StabiliseTask) task).patient;
        return null;
    }

    /** True if a crew member standing on (x, y) can work the task. */
    public static boolean isWorkSpot(CrewTask task, ShipGridComponent grid, GridNavGraph nav, CrewService crew, int x, int y) {
        if (!nav.isWalkable(x, y)) return false;
        int tx = targetX(task, crew), ty = targetY(task, crew);
        switch (task.kind) {
            case EXTINGUISH: {
                // From the tile itself or a neighbour that air (and so a hose) reaches
                if (x == tx && y == ty) return true;
                Tile here = grid.getTile(x, y);
                return isAdjacent(x, y, tx, ty) && grid.canPass(x, y, tx, ty) && !here.isBurning();
            }
            case SEAL_BREACH:
                // From a tile beside it, or reaching across a machine that stands against it (a
                // breach in the hull behind an engine has no free tile beside it)
                if (isAdjacent(x, y, tx, ty)) return !grid.isEdgeBlocked(x, y, tx, ty);
                return reachesAcrossMount(grid, x, y, tx, ty);
            case PATCH_LEAK: {
                // A thin wall from the air tile beside it; a wall from a tile next to it, or reaching
                // across a machine that stands against it; a deck plate from on it or next to it
                Side side = ((PatchLeakTask) task).side;
                if (side != null) return x == tx && y == ty;
                Tile t = grid.getTile(tx, ty);
                if (t != null && t.isFloor() && x == tx && y == ty) return true;
                if (isAdjacent(x, y, tx, ty)) return !grid.isEdgeBlocked(x, y, tx, ty);
                return reachesAcrossMount(grid, x, y, tx, ty);
            }
            case RESET_SHORT:
                if (((ResetShortTask) task).subsystem == null) {
                    // A conduit tile: from on it, or beside it
                    if (x == tx && y == ty) return true;
                    return isAdjacent(x, y, tx, ty) && !grid.isEdgeBlocked(x, y, tx, ty);
                }
                // fall through: a system is worked like a repair
            case ISOLATE_TANK:
            case REPAIR:
            case MAN_CONSOLE: {
                SubsystemComponent s = subsystemOf(task);
                if (s == null) return false;
                for (Side side : Side.values()) {
                    int nx = x + side.dx, ny = y + side.dy;
                    if (s.covers(nx, ny) && !grid.isEdgeBlocked(x, y, nx, ny)) return true;
                }
                return false;
            }
            case TREAT:
            case STABILISE:
                if (isAdjacent(x, y, tx, ty) && grid.canPass(x, y, tx, ty)) return true;
                return x == tx && y == ty;
            case EVACUATE:
                return ((EvacuateTask) task).inPod(x, y);
            default:
                return x == tx && y == ty;
        }
    }

    /** A predicate for {@link GridPathfinder#nearest} over the task's work spots. */
    public static GridPathfinder.TilePredicate workSpots(CrewTask task, ShipGridComponent grid, GridNavGraph nav, CrewService crew) {
        return (x, y) -> isWorkSpot(task, grid, nav, crew, x, y);
    }

    /** True if the air on (x, y) is fit to breathe: pressure and oxygen enough, whatever the heat. */
    public static boolean isBreathable(ShipGridComponent grid, int x, int y) {
        return grid.pressureRatioAt(x, y) >= SAFE_PRESSURE && grid.oxygenRatioAt(x, y) >= SAFE_OXYGEN;
    }

    /**
     * True if working at (x, y) means being out of breathable air: a tile whose air is too thin or
     * foul, or one with no air (a hull breach, a sealed tile) beside such a tile or open to space.
     */
    public static boolean needsSuit(ShipGridComponent grid, int x, int y) {
        Tile t = grid.getTile(x, y);
        if (t == null) return false;
        if (t.holdsAir()) return !isBreathable(grid, x, y);
        if (t.isSpaceExposed()) return true;
        for (Side s : Side.values()) {
            Tile n = grid.getTile(x + s.dx, y + s.dy);
            if (n != null && n.holdsAir() && !isBreathable(grid, x + s.dx, y + s.dy)) return true;
        }
        return false;
    }

    /** True if a crew member can breathe safely on (x, y): pressurized, oxygenated, not burning or hot. */
    public static boolean isSafe(ShipGridComponent grid, int x, int y) {
        Tile t = grid.getTile(x, y);
        if (t == null || !t.holdsAir() || t.isBurning() || t.temperature > SAFE_TEMPERATURE) return false;
        return grid.pressureRatioAt(x, y) >= SAFE_PRESSURE && grid.oxygenRatioAt(x, y) >= SAFE_OXYGEN;
    }

    /** How far, in tiles, a crew member can reach across machinery to a wall behind it. */
    public static final int REACH_ACROSS = 4;

    /**
     * True if (x, y) lies in a straight line from (tx, ty), at most {@link #REACH_ACROSS} tiles
     * off, with only machines' footprints between them and no thin wall in the way: someone
     * standing there can reach over the machinery to the wall behind it.
     */
    static boolean reachesAcrossMount(ShipGridComponent grid, int x, int y, int tx, int ty) {
        int dx = Integer.signum(tx - x), dy = Integer.signum(ty - y);
        int dist = Math.abs(tx - x) + Math.abs(ty - y);
        if ((dx != 0 && dy != 0) || dist < 2 || dist > REACH_ACROSS) return false;
        int px = x, py = y;
        for (int i = 1; i < dist; i++) {
            int mx = px + dx, my = py + dy;
            Tile mid = grid.getTile(mx, my);
            if (mid == null || mid.mount == null || mid.isWall() || grid.isEdgeBlocked(px, py, mx, my)) return false;
            px = mx;
            py = my;
        }
        return !grid.isEdgeBlocked(px, py, tx, ty);
    }

    private static boolean isAdjacent(int x, int y, int nx, int ny) {
        return Math.abs(x - nx) + Math.abs(y - ny) == 1;
    }
}

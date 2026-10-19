package com.ringsofiron.aicrew;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.aicrew.nav.GridNavGraph;
import com.ringsofiron.aicrew.nav.GridPathfinder;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Side;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.interiorgrid.crew.CrewTask;
import com.ringsofiron.interiorgrid.crew.RepairTask;
import com.ringsofiron.interiorgrid.crew.RescueTask;

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

    /** False once the job is done (fire out, breach sealed, system repaired, casualty safe or dead). */
    public static boolean isStillNeeded(CrewTask task, ShipGridComponent grid, CrewService crew) {
        Tile t = grid.getTile(task.x, task.y);
        switch (task.kind) {
            case EXTINGUISH:
                return t != null && t.isBurning();
            case SEAL_BREACH:
                return t != null && t.isHullBreach();
            case REPAIR: {
                SubsystemComponent s = subsystemOf(task);
                return s != null && s.health < 1f;
            }
            case MAN_CONSOLE: {
                SubsystemComponent s = subsystemOf(task);
                return s != null && !s.isDestroyed();
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
            case CARRY:
                return ((CarryTask) task).job.isOpen();
            case YARD:
                return ((YardTask) task).job.isOpen();
            case FORCE_DOOR:
                return ((ForceDoorTask) task).job.isOpen();
            default:
                return true;
        }
    }

    static SubsystemComponent subsystemOf(CrewTask task) {
        Entity e = task instanceof RepairTask ? ((RepairTask) task).subsystem
                : task instanceof ManConsoleTask ? ((ManConsoleTask) task).console : null;
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

    private static Entity mobileTarget(CrewTask task) {
        if (task instanceof RescueTask) return ((RescueTask) task).casualty;
        if (task instanceof TreatTask) return ((TreatTask) task).patient;
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
                return isAdjacent(x, y, tx, ty) && !grid.isEdgeBlocked(x, y, tx, ty);
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
                if (isAdjacent(x, y, tx, ty) && grid.canPass(x, y, tx, ty)) return true;
                return x == tx && y == ty;
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

    private static boolean isAdjacent(int x, int y, int nx, int ny) {
        return Math.abs(x - nx) + Math.abs(y - ny) == 1;
    }
}

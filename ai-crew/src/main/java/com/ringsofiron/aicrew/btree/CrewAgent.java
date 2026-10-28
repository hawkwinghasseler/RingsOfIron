package com.ringsofiron.aicrew.btree;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectFloatMap;
import com.ringsofiron.aicrew.BoardTask;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.aicrew.ShipTaskBoardComponent;
import com.ringsofiron.aicrew.TaskRules;
import com.ringsofiron.aicrew.TreatTask;
import com.ringsofiron.aicrew.nav.GridNavGraph;
import com.ringsofiron.aicrew.nav.GridNode;
import com.ringsofiron.aicrew.nav.GridPathfinder;
import com.ringsofiron.interiorgrid.GridLink;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.crew.CrewTask;

/**
 * The blackboard a crew member's behavior tree runs on: the entity, its components,
 * the shared {@link CrewService}, this step's delta time and short-term memory (the
 * hostile being fought, tasks found unreachable).
 */
public class CrewAgent {

    /** Seconds a crew member ignores a task they could not reach. */
    public static final float UNREACHABLE_COOLDOWN = 5f;

    public final Entity entity;
    public final CrewMemberComponent crew;
    public final GridPositionComponent pos;
    public final CrewService service;

    /** Delta time of the current tree step, seconds. */
    public float dt;
    /** Simulation clock, seconds. */
    public float time;
    /** The hostile crew member being engaged, or null. */
    public Entity target;

    private final ObjectFloatMap<CrewTask> ignoreUntil = new ObjectFloatMap<>();
    final Array<GridNode> pathScratch = new Array<>();
    /** The shut doors this crew member may open on the craft they are aboard ({@link com.ringsofiron.aicrew.CrewDoors}). */
    public final GridNavGraph.DoorKey doorKey;
    /** Crew aboard this one's ship, gathered while looking for hostiles. */
    final Array<Entity> aboardScratch = new Array<>();
    /** On the way to fill their suit, or filling it ({@link CrewTasks.RefillSuit}). */
    boolean refilling;
    /** Sim time until which no oxygen supply aboard was found reachable. */
    float refillBlockedUntil = Float.NEGATIVE_INFINITY;

    public CrewAgent(Entity entity, CrewMemberComponent crew, GridPositionComponent pos, CrewService service) {
        this.entity = entity;
        this.crew = crew;
        this.pos = pos;
        this.service = service;
        doorKey = (x, y, nx, ny) -> service.doors.mayOpen(entity, pos.ship, x, y, nx, ny);
    }

    /** A path on the craft aboard from (sx, sy) to (tx, ty), through doors this crew member may open; see {@link GridPathfinder#findPath}. */
    public boolean findPath(int sx, int sy, int tx, int ty, Array<GridNode> out) {
        GridPathfinder pf = pathfinder();
        return pf != null && pf.findPath(sx, sy, tx, ty, out, doorKey);
    }

    /** The nearest tile aboard that {@code goal} accepts, through doors this crew member may open; see {@link GridPathfinder#nearest}. */
    public boolean nearest(int sx, int sy, GridPathfinder.TilePredicate goal, Array<GridNode> out) {
        GridPathfinder pf = pathfinder();
        return pf != null && pf.nearest(sx, sy, goal, out, doorKey);
    }

    public ShipGridComponent grid() {
        return service.grid(pos.ship);
    }

    /** The task board of the craft this crew member works for, wherever they stand. */
    public ShipTaskBoardComponent board() {
        return service.board(service.homeOf(entity));
    }

    /** How a {@link #travel} step went. */
    public enum Travel { ARRIVED, GOING, UNREACHABLE }

    /**
     * One step toward a tile of {@code ship} that {@code spots} accepts: a path there on this craft,
     * or to a docked link onto {@code ship} and through it. ARRIVED once standing on such a tile.
     */
    public Travel travel(Entity ship, GridPathfinder.TilePredicate spots) {
        if (pos.crossing != null) return Travel.GOING;
        GridPathfinder pf = pathfinder();
        if (pf == null) return Travel.UNREACHABLE;
        int x = pos.tileX(), y = pos.tileY();
        if (pos.ship == ship) {
            if (!pos.isMoving() && pos.walkway == null && spots.test(x, y)) return Travel.ARRIVED;
            GridNode dest = pos.destination();
            if (dest != null && pos.isMoving() && !pos.pathBlocked && spots.test(dest.x, dest.y)) return Travel.GOING;
            if (!pf.nearest(x, y, spots, pathScratch, doorKey)) return Travel.UNREACHABLE;
            pos.setPath(pathScratch);
            return Travel.GOING;
        }
        GridLink link = linkTo(ship);
        if (link == null) return Travel.UNREACHABLE;
        if (!pos.isMoving() && x == link.x && y == link.y) {
            pos.crossing = link;
            return Travel.GOING;
        }
        GridNode dest = pos.destination();
        if (dest != null && pos.isMoving() && !pos.pathBlocked && dest.x == link.x && dest.y == link.y) return Travel.GOING;
        if (!pf.findPath(x, y, link.x, link.y, pathScratch, doorKey)) return Travel.UNREACHABLE;
        pos.setPath(pathScratch);
        return Travel.GOING;
    }

    /** The open link from the craft this crew member is aboard straight onto {@code ship} nearest them, or null. */
    private GridLink linkTo(Entity ship) {
        ShipGridComponent grid = grid();
        if (grid == null) return null;
        GridLink best = null;
        float bestDist = Float.MAX_VALUE;
        for (GridLink l : grid.links) {
            if (l.otherShip != ship || !l.canCross()) continue;
            float d = Math.abs(l.x + 0.5f - pos.x) + Math.abs(l.y + 0.5f - pos.y);
            if (d < bestDist) {
                bestDist = d;
                best = l;
            }
        }
        return best;
    }

    public GridPathfinder pathfinder() {
        ShipGridComponent g = grid();
        return g == null ? null : service.pathfinder(g);
    }

    public boolean isIgnoring(CrewTask task) {
        return ignoreUntil.get(task, Float.NEGATIVE_INFINITY) > time;
    }

    /** Gives up the current task and skips it for {@link #UNREACHABLE_COOLDOWN} seconds. */
    public void abandonUnreachable() {
        if (crew.task != null) ignoreUntil.put(crew.task, time + UNREACHABLE_COOLDOWN);
        releaseTask();
    }

    public void releaseTask() {
        service.releaseTask(entity);
        pos.stop();
    }

    /** True if an open task on the board suits this crew member. */
    public boolean hasAvailableTask() {
        ShipTaskBoardComponent board = board();
        return board != null && board.faction == crew.faction && board.best(crew.role, pos.tileX(), pos.tileY(), this::mayTake) != null;
    }

    /**
     * True if this crew member would take {@code task} now: not one they just found unreachable, and
     * only carrying, yard work and boarding while away from the craft they work for (other work is
     * done from home), and only a boarding order that is theirs, and nothing where the fire is falling
     * ({@link TaskRules#waitsOutFire}). A crew member held prisoner takes nothing.
     */
    public boolean mayTake(CrewTask task) {
        if (isIgnoring(task) || crew.held) return false;
        // The lightly wounded patch themselves up while idle (CrewHealthSystem), leaving the task to others
        if (task instanceof TreatTask && ((TreatTask) task).patient == entity) return false;
        if (task instanceof BoardTask && !((BoardTask) task).isFor(entity)) return false;
        if (TaskRules.waitsOutFire(task, board(), service)) return false;
        return pos.ship == service.homeOf(entity) || task.kind == CrewTask.Kind.CARRY || task.kind == CrewTask.Kind.YARD
                || task.kind == CrewTask.Kind.BOARD || TaskRules.goesBackAboard(crew, task, board());
    }
}

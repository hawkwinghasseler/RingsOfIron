package com.ringsofiron.aicrew.btree;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectFloatMap;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.aicrew.ShipTaskBoardComponent;
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

    public CrewAgent(Entity entity, CrewMemberComponent crew, GridPositionComponent pos, CrewService service) {
        this.entity = entity;
        this.crew = crew;
        this.pos = pos;
        this.service = service;
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
            if (!pf.nearest(x, y, spots, pathScratch)) return Travel.UNREACHABLE;
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
        if (!pf.findPath(x, y, link.x, link.y, pathScratch)) return Travel.UNREACHABLE;
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
     * only carrying while away from the craft they work for (other work is done from home).
     */
    public boolean mayTake(CrewTask task) {
        if (isIgnoring(task)) return false;
        return pos.ship == service.homeOf(entity) || task.kind == CrewTask.Kind.CARRY;
    }
}

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

    public ShipTaskBoardComponent board() {
        return service.board(pos.ship);
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
        return board != null && board.faction == crew.faction && board.best(crew.role, pos.tileX(), pos.tileY(), t -> !isIgnoring(t)) != null;
    }
}

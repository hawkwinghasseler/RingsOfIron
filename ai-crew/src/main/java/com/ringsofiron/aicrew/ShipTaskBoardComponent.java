package com.ringsofiron.aicrew;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.interiorgrid.crew.CrewTask;

/**
 * The job blackboard of one ship, attached to the ship entity next to its
 * ShipGridComponent. Other systems post tasks; idle crew claim the one that suits them
 * best (see {@link #best}).
 *
 * Finished and cancelled tasks are dropped from {@link #tasks} by {@link #prune()}.
 */
public class ShipTaskBoardComponent implements Component {

    /** A filter for {@link #best}, e.g. to skip tasks a crew member just found unreachable. */
    public interface TaskFilter {
        boolean accept(CrewTask task);
    }

    /** Distance penalty in {@link #best}, priority points per tile. */
    public static final float DISTANCE_WEIGHT = 0.5f;

    public final Array<CrewTask> tasks = new Array<>();
    /** The faction whose crew work this board. Boarders do not take the ship's jobs. */
    public int faction = 0;

    /** Adds the task unless the same job is already active. */
    public boolean post(CrewTask task) {
        for (CrewTask t : tasks) {
            if (t.isActive() && t.sameJob(task)) return false;
        }
        task.status = CrewTask.Status.OPEN;
        task.claimedBy = null;
        tasks.add(task);
        return true;
    }

    /**
     * The open task a crew member of {@code role} at tile (x, y) should take: the highest
     * {@code priority × role affinity − distance × }{@link #DISTANCE_WEIGHT}, ignoring
     * tasks the role never does. Null if there is none.
     */
    public CrewTask best(CrewRole role, int x, int y, TaskFilter filter) {
        CrewTask best = null;
        float bestScore = Float.NEGATIVE_INFINITY;
        for (CrewTask t : tasks) {
            if (t.status != CrewTask.Status.OPEN) continue;
            float affinity = role.affinity(t.kind);
            if (affinity <= 0f) continue;
            if (filter != null && !filter.accept(t)) continue;
            float score = t.priority * affinity - DISTANCE_WEIGHT * (Math.abs(t.x - x) + Math.abs(t.y - y));
            if (score > bestScore) {
                bestScore = score;
                best = t;
            }
        }
        return best;
    }

    public boolean claim(CrewTask task, Entity crew) {
        if (task.status != CrewTask.Status.OPEN) return false;
        task.status = CrewTask.Status.CLAIMED;
        task.claimedBy = crew;
        return true;
    }

    /** Puts a claimed task back on the board for someone else. */
    public void release(CrewTask task) {
        if (task.status == CrewTask.Status.CLAIMED) task.status = CrewTask.Status.OPEN;
        task.claimedBy = null;
    }

    public void complete(CrewTask task) {
        task.status = CrewTask.Status.DONE;
        task.claimedBy = null;
    }

    public void cancel(CrewTask task) {
        task.status = CrewTask.Status.CANCELLED;
        task.claimedBy = null;
    }

    /** Removes finished and cancelled tasks. */
    public void prune() {
        for (int i = tasks.size - 1; i >= 0; i--) {
            if (!tasks.get(i).isActive()) tasks.removeIndex(i);
        }
    }

    public int activeCount() {
        int n = 0;
        for (CrewTask t : tasks) if (t.isActive()) n++;
        return n;
    }
}

package com.ringsofiron.interiorgrid.crew;

import com.badlogic.ashley.core.Entity;

/**
 * Accepts jobs for a ship's crew. Implemented by {@code ai-crew}, which puts them on the
 * ship's task board. Combat posts {@link SealBreachTask}, {@link RepairTask},
 * {@link ExtinguishTask} and {@link RescueTask} as it damages a ship; the hazard
 * simulation can post {@link ExtinguishTask} as fire spreads.
 *
 * Posting is idempotent: a task for the same job as one already open (see
 * {@link CrewTask#sameJob(CrewTask)}) is dropped, so callers can post every tick
 * without flooding the board.
 */
public interface DamageTaskSink {

    /**
     * @param ship the entity holding the ShipGridComponent
     * @return true if the task was added, false if the same job was already open or the
     *         ship has no crew task board
     */
    boolean post(Entity ship, CrewTask task);
}

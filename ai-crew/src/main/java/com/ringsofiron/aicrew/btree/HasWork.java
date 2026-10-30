package com.ringsofiron.aicrew.btree;

/** Succeeds when the agent holds a task or the board has one for them. */
public class HasWork extends CrewLeaf {
    @Override
    public Status execute() {
        CrewAgent a = agent();
        boolean holding = a.crew.task != null && a.crew.task.isActive() && a.crew.task.claimedBy == a.entity;
        // Pirate crews put down what they hold for the power (round 10): the task goes back, the best is claimed anew
        if (holding && a.crew.carrying == null && a.board() != null
                && a.board().yieldsToPower(a.crew.task, a.crew.role, a.pos.tileX(), a.pos.tileY(), a::mayTake)) return Status.FAILED;
        boolean available = a.hasAvailableTask();
        if (!holding && !available && EvaTasks.sentOutForNothing(a)) available = a.hasAvailableTask();
        return holding || available ? Status.SUCCEEDED : Status.FAILED;
    }
}

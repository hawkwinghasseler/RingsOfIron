package com.ringsofiron.aicrew.btree;

/** Succeeds when a conscious hostile is in sight; remembers it as the agent's target. */
public class HostileInSight extends CrewLeaf {
    @Override
    public Status execute() {
        CrewAgent a = agent();
        a.target = CrewTasks.findHostile(a);
        return a.target != null ? Status.SUCCEEDED : Status.FAILED;
    }
}

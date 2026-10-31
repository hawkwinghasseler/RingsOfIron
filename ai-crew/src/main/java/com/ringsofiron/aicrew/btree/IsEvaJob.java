package com.ringsofiron.aicrew.btree;

import com.badlogic.gdx.ai.btree.Task.Status;
import com.ringsofiron.interiorgrid.crew.CrewTask;

/** Succeeds if the agent's task is work outside ({@link CrewTask.Kind#isEva}); negated, if it is any other. */
public class IsEvaJob extends CrewLeaf {
    private final boolean negate;

    public IsEvaJob() {
        this(false);
    }

    public IsEvaJob(boolean negate) {
        this.negate = negate;
    }

    @Override
    public Status execute() {
        CrewTask t = agent().crew.task;
        return t != null && t.kind.isEva() != negate ? Status.SUCCEEDED : Status.FAILED;
    }
}

package com.ringsofiron.aicrew.btree;

import com.ringsofiron.interiorgrid.crew.CrewTask;

/** Succeeds if the agent's task is of the given kind (or, negated, of any other kind). */
public class IsTaskKind extends CrewLeaf {
    private final CrewTask.Kind kind;
    private final boolean negate;

    public IsTaskKind(CrewTask.Kind kind) {
        this(kind, false);
    }

    public IsTaskKind(CrewTask.Kind kind, boolean negate) {
        this.kind = kind;
        this.negate = negate;
    }

    @Override
    public Status execute() {
        CrewTask t = agent().crew.task;
        boolean match = t != null && t.kind == kind;
        return t != null && match != negate ? Status.SUCCEEDED : Status.FAILED;
    }
}

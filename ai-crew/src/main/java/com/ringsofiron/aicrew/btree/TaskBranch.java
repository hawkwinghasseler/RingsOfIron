package com.ringsofiron.aicrew.btree;

import com.badlogic.gdx.ai.btree.Task;
import com.badlogic.gdx.ai.btree.branch.Sequence;
import com.ringsofiron.aicrew.EvaSystem;

/**
 * A sequence that works one board task. If it fails or is interrupted (a hostile came
 * into sight, the crew member was knocked out) the task goes back on the board, unless the
 * crew member has just gone out on EVA for it ({@link EvaTasks.Spacewalk} carries it on).
 */
public class TaskBranch extends Sequence<CrewAgent> {

    @SafeVarargs
    @SuppressWarnings("varargs") // gdx-ai's Sequence(Task...) only reads the children
    public TaskBranch(Task<CrewAgent>... children) {
        super(children);
    }

    @Override
    public void end() {
        CrewAgent a = getObject();
        if (status != Status.SUCCEEDED && a.crew.task != null && EvaSystem.of(a.entity) == null) a.releaseTask();
        super.end();
    }
}

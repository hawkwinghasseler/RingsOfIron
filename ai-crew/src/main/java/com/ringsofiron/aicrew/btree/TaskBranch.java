package com.ringsofiron.aicrew.btree;

import com.badlogic.gdx.ai.btree.Task;
import com.badlogic.gdx.ai.btree.branch.Sequence;

/**
 * A sequence that works one board task. If it fails or is interrupted (a hostile came
 * into sight, the crew member was knocked out) the task goes back on the board.
 */
public class TaskBranch extends Sequence<CrewAgent> {

    @SafeVarargs
    public TaskBranch(Task<CrewAgent>... children) {
        super(children);
    }

    @Override
    public void end() {
        if (status != Status.SUCCEEDED && getObject().crew.task != null) getObject().releaseTask();
        super.end();
    }
}

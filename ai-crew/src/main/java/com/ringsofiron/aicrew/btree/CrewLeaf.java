package com.ringsofiron.aicrew.btree;

import com.badlogic.gdx.ai.btree.LeafTask;
import com.badlogic.gdx.ai.btree.Task;

/**
 * Base for the crew's leaf tasks. Crew trees are built per crew member by
 * {@link CrewBehaviorTrees} and never cloned, so {@link #copyTo} is a no-op.
 */
public abstract class CrewLeaf extends LeafTask<CrewAgent> {

    protected CrewAgent agent() {
        return getObject();
    }

    @Override
    protected Task<CrewAgent> copyTo(Task<CrewAgent> task) {
        return task;
    }
}

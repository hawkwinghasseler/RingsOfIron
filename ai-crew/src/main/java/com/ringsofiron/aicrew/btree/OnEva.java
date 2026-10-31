package com.ringsofiron.aicrew.btree;

import com.badlogic.gdx.ai.btree.Task.Status;
import com.ringsofiron.aicrew.EvaSystem;

/** Succeeds while the agent is on an EVA: from stepping into the airlock to the hatch opening behind them. */
public class OnEva extends CrewLeaf {
    @Override
    public Status execute() {
        return EvaSystem.of(agent().entity) != null ? Status.SUCCEEDED : Status.FAILED;
    }
}

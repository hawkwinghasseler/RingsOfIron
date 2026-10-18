package com.ringsofiron.aicrew;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.ai.btree.BehaviorTree;
import com.ringsofiron.aicrew.btree.CrewAgent;

/** The crew member's gdx-ai behavior tree and its blackboard. */
public class BehaviorTreeComponent implements Component {

    public final BehaviorTree<CrewAgent> tree;
    public final CrewAgent agent;

    public BehaviorTreeComponent(BehaviorTree<CrewAgent> tree, CrewAgent agent) {
        this.tree = tree;
        this.agent = agent;
        tree.setObject(agent);
    }
}

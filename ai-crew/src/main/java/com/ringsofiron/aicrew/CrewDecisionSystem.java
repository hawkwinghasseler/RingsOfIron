package com.ringsofiron.aicrew;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;

/**
 * Steps the behavior tree of every conscious crew member each frame. Unconscious and
 * dead crew, and the player avatar, are skipped.
 */
public class CrewDecisionSystem extends IteratingSystem {

    private final ComponentMapper<BehaviorTreeComponent> btM = ComponentMapper.getFor(BehaviorTreeComponent.class);
    private final ComponentMapper<CrewMemberComponent> crewM = ComponentMapper.getFor(CrewMemberComponent.class);
    private float time;

    public CrewDecisionSystem() {
        super(Family.all(CrewMemberComponent.class, GridPositionComponent.class, BehaviorTreeComponent.class)
                .exclude(PlayerAvatarComponent.class).get(), 5);
    }

    @Override
    public void update(float deltaTime) {
        time += deltaTime;
        super.update(deltaTime);
    }

    @Override
    protected void processEntity(Entity e, float dt) {
        CrewMemberComponent c = crewM.get(e);
        if (!c.isConscious()) return;
        BehaviorTreeComponent bt = btM.get(e);
        bt.agent.dt = dt;
        bt.agent.time = time;
        bt.tree.step();
    }
}

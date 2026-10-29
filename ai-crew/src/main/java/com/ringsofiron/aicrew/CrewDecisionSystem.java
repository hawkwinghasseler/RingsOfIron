package com.ringsofiron.aicrew;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.ringsofiron.coreengine.SimClock;

/**
 * Steps the behavior tree of every conscious crew member each sim tick. Unconscious and
 * dead crew, and the player avatar, are skipped.
 */
public class CrewDecisionSystem extends FixedIteratingSystem {

    private final ComponentMapper<BehaviorTreeComponent> btM = ComponentMapper.getFor(BehaviorTreeComponent.class);
    private final ComponentMapper<CrewMemberComponent> crewM = ComponentMapper.getFor(CrewMemberComponent.class);
    private float time;

    public CrewDecisionSystem() {
        super(Family.all(CrewMemberComponent.class, GridPositionComponent.class, BehaviorTreeComponent.class)
                .exclude(PlayerAvatarComponent.class).get(), 5, (float) SimClock.TICK_SECONDS);
    }

    @Override
    protected void updateFixed(float dt) {
        time += dt;
        super.updateFixed(dt);
    }

    @Override
    protected void processEntity(Entity e, float dt) {
        CrewMemberComponent c = crewM.get(e);
        // Whatever timed work goes on this step shows its progress again
        c.progress = -1f;
        if (!c.isConscious()) return;
        BehaviorTreeComponent bt = btM.get(e);
        bt.agent.dt = dt;
        bt.agent.time = time;
        bt.tree.step();
    }
}

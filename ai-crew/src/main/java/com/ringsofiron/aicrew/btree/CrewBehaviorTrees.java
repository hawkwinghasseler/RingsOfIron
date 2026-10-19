package com.ringsofiron.aicrew.btree;

import com.badlogic.gdx.ai.btree.BehaviorTree;
import com.badlogic.gdx.ai.btree.Task;
import com.badlogic.gdx.ai.btree.branch.DynamicGuardSelector;
import com.badlogic.gdx.ai.btree.branch.Selector;
import com.badlogic.gdx.ai.btree.branch.Sequence;
import com.ringsofiron.interiorgrid.crew.CrewTask;

/**
 * Builds the standard crew behavior tree:
 *
 * <pre>
 * DynamicGuardSelector
 *   [HostileInSight]  EngageHostile
 *   [HasWork]         TaskBranch( ClaimTask,
 *                                 Selector( Sequence( IsTaskKind(RESCUE), SuitUp, MoveToWorkSpot,
 *                                                     PickUpCasualty, CarryToSafety, PutDownCasualty ),
 *                                           Sequence( IsTaskKind(CARRY), PickUpLoad, DeliverLoad,
 *                                                     CompleteTask ),
 *                                           Sequence( IsTaskKind(YARD), WorkOnTile, CompleteTask ),
 *                                           Sequence( IsTaskKind(not RESCUE, CARRY or YARD), SuitUp,
 *                                                     MoveToWorkSpot, DoWork, CompleteTask ) ) )
 *                     Idle (heads home first when aboard another craft; takes a suit off once calm)
 *
 * SuitUp puts a space suit on first when the task's spot is out of breathable air.
 * </pre>
 *
 * Guards are re-checked every step, so a hostile coming into sight interrupts any task
 * (which goes back on the board) and a new task pulls the crew member out of idle.
 */
public final class CrewBehaviorTrees {

    private CrewBehaviorTrees() {}

    @SuppressWarnings("unchecked")
    public static BehaviorTree<CrewAgent> standard(CrewAgent agent) {
        Task<CrewAgent> engage = new CrewTasks.EngageHostile();
        engage.setGuard(new CrewTasks.HostileInSight());

        Task<CrewAgent> doTask = new Selector<>(
                new Sequence<>(new CrewTasks.IsTaskKind(CrewTask.Kind.RESCUE), new CrewTasks.SuitUp(),
                        new CrewTasks.MoveToWorkSpot(),
                        new CrewTasks.PickUpCasualty(), new CrewTasks.CarryToSafety(), new CrewTasks.PutDownCasualty()),
                new Sequence<>(new CrewTasks.IsTaskKind(CrewTask.Kind.CARRY), new CrewTasks.PickUpLoad(),
                        new CrewTasks.DeliverLoad(), new CrewTasks.CompleteTask()),
                new Sequence<>(new CrewTasks.IsTaskKind(CrewTask.Kind.YARD), new CrewTasks.WorkOnTile(),
                        new CrewTasks.CompleteTask()),
                new Sequence<>(new CrewTasks.IsTaskKind(CrewTask.Kind.RESCUE, true),
                        new CrewTasks.IsTaskKind(CrewTask.Kind.CARRY, true),
                        new CrewTasks.IsTaskKind(CrewTask.Kind.YARD, true), new CrewTasks.SuitUp(),
                        new CrewTasks.MoveToWorkSpot(),
                        new CrewTasks.DoWork(), new CrewTasks.CompleteTask()));
        Task<CrewAgent> work = new TaskBranch(new CrewTasks.ClaimTask(), doTask);
        work.setGuard(new CrewTasks.HasWork());

        Task<CrewAgent> root = new DynamicGuardSelector<>(engage, work, new CrewTasks.Idle());
        return new BehaviorTree<>(root, agent);
    }
}

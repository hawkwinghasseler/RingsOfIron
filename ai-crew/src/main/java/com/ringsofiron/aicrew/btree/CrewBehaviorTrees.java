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
 *   [OnEva]           Spacewalk (out on the hull: to the job, the work, back in; see EvaTasks)
 *   [HostileInSight]  EngageHostile
 *   [SuitAirLow]      RefillSuit (at an oxygen tank or working life support)
 *   [CrateToStow]     StowCrate (a crate in hand that no work outside of theirs takes out: back in the hold)
 *   [HasWork]         TaskBranch( ClaimTask,
 *                                 Selector( Sequence( IsTaskKind(RESCUE), SuitUp, MoveToWorkSpot,
 *                                                     PickUpCasualty, CarryToSafety, PutDownCasualty ),
 *                                           Sequence( IsTaskKind(CARRY), SuitUp, PickUpLoad, DeliverLoad,
 *                                                     CompleteTask ),
 *                                           Sequence( IsTaskKind(YARD), SuitUp, WorkOnTile, CompleteTask ),
 *                                           Sequence( IsTaskKind(FORCE_DOOR), ForceDoor, CompleteTask ),
 *                                           Sequence( IsTaskKind(BOARD), Board, CompleteTask ),
 *                                           Sequence( IsTaskKind(STRIP_HULL), CrossToStrip ),
 *                                           Sequence( IsEvaJob, FetchPart, SuitUpForEva, GoOutside ),
 *                                           Sequence( IsTaskKind(not RESCUE, CARRY, YARD, FORCE_DOOR, BOARD
 *                                                     or work outside), SuitUp,
 *                                                     MoveToWorkSpot, DoWork, CompleteTask ) ) )
 *                     Idle (heads home first when aboard another craft; steps out of a docked port's
 *                           opening; takes a suit off once calm)
 *
 * SuitUp puts a space suit on first when the task's spot is out of breathable air, on whichever
 * craft the spot is (a ship latched alongside being stripped, say).
 * </pre>
 *
 * Guards are re-checked every step, so a hostile coming into sight interrupts any task
 * (which goes back on the board) and a new task pulls the crew member out of idle.
 */
public final class CrewBehaviorTrees {

    private CrewBehaviorTrees() {}

    @SuppressWarnings("unchecked")
    public static BehaviorTree<CrewAgent> standard(CrewAgent agent) {
        Task<CrewAgent> engage = new EngageHostile();
        engage.setGuard(new HostileInSight());

        Task<CrewAgent> doTask = new Selector<>(
                new Sequence<>(new IsTaskKind(CrewTask.Kind.RESCUE), new SuitUp(),
                        new MoveToWorkSpot(),
                        new PickUpCasualty(), new CarryToSafety(), new PutDownCasualty()),
                new Sequence<>(new IsTaskKind(CrewTask.Kind.CARRY), new SuitUp(), new PickUpLoad(),
                        new DeliverLoad(), new CompleteTask()),
                new Sequence<>(new IsTaskKind(CrewTask.Kind.YARD), new SuitUp(), new WorkOnTile(),
                        new CompleteTask()),
                new Sequence<>(new IsTaskKind(CrewTask.Kind.FORCE_DOOR), new ForceDoor(),
                        new CompleteTask()),
                new Sequence<>(new IsTaskKind(CrewTask.Kind.BOARD), new Board(),
                        new CompleteTask()),
                new Sequence<>(new IsTaskKind(CrewTask.Kind.STRIP_HULL), new CrossToStrip()),
                new Sequence<>(new IsEvaJob(), new FetchPart(), new SuitUpForEva(),
                        new GoOutside()),
                new Sequence<>(new IsTaskKind(CrewTask.Kind.RESCUE, true), new IsEvaJob(true),
                        new IsTaskKind(CrewTask.Kind.CARRY, true),
                        new IsTaskKind(CrewTask.Kind.YARD, true),
                        new IsTaskKind(CrewTask.Kind.FORCE_DOOR, true),
                        new IsTaskKind(CrewTask.Kind.BOARD, true), new SuitUp(),
                        new MoveToWorkSpot(),
                        new DoWork(), new CompleteTask()));
        Task<CrewAgent> work = new TaskBranch(new ClaimTask(), doTask);
        work.setGuard(new HasWork());

        Task<CrewAgent> refill = new RefillSuit();
        refill.setGuard(new SuitAirLow());

        Task<CrewAgent> spacewalk = new Spacewalk();
        spacewalk.setGuard(new OnEva());

        Task<CrewAgent> stow = new StowCrate();
        stow.setGuard(new CrateToStow());

        Task<CrewAgent> root = new DynamicGuardSelector<>(spacewalk, engage, refill, stow, work, new Idle());
        return new BehaviorTree<>(root, agent);
    }
}

package com.ringsofiron.aicrew.btree;

import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.ShipTaskBoardComponent;
import com.ringsofiron.aicrew.TaskRules;
import com.ringsofiron.interiorgrid.crew.CrewTask;

/** Keeps the agent's claimed task, or claims the best one from the board. */
public class ClaimTask extends CrewLeaf {
    @Override
    public Status execute() {
        CrewAgent a = agent();
        CrewTask held = a.crew.task;
        ShipTaskBoardComponent board = a.board();
        if (held != null && held.isActive() && held.claimedBy == a.entity) {
            // Fire falling where the job is: put it down until the fight is over, unless already
            // carrying someone out; and the player's say on EVA: work outside put down when they
            // may not go out, other work when they are sent out
            boolean sent = a.crew.evaOrder == CrewMemberComponent.EvaOrder.OUT;
            boolean putDown = !(sent && held.kind.isEva()) && TaskRules.waitsOutFire(held, board, a.service)
                    || held.kind.isEva() && !EvaTasks.mayGoOut(a) || sent && !held.kind.isEva();
            if (a.crew.carrying == null && putDown) board.release(held);
            else return Status.SUCCEEDED;
        }
        a.crew.task = null;
        if (board == null || board.faction != a.crew.faction) return Status.FAILED;
        CrewTask t = board.best(a.crew.role, a.pos.tileX(), a.pos.tileY(), a::mayTake);
        if (t == null && EvaTasks.sentOutForNothing(a)) t = board.best(a.crew.role, a.pos.tileX(), a.pos.tileY(), a::mayTake);
        if (t == null || !board.claim(t, a.entity)) return Status.FAILED;
        a.crew.task = t;
        return Status.SUCCEEDED;
    }
}

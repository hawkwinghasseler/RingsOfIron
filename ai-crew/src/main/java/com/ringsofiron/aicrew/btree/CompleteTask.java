package com.ringsofiron.aicrew.btree;

import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.ShipTaskBoardComponent;
import com.ringsofiron.aicrew.TreatTask;

/** Marks the agent's task done on the board. */
public class CompleteTask extends CrewLeaf {
    @Override
    public Status execute() {
        CrewAgent a = agent();
        if (a.crew.task != null) {
            ShipTaskBoardComponent board = a.board();
            if (board != null) board.complete(a.crew.task);
            if (a.crew.task instanceof TreatTask) {
                CrewMemberComponent p = a.service.crew(((TreatTask) a.crew.task).patient);
                if (p != null && p.treatedBy == a.entity) p.treatedBy = null;
            }
            a.crew.task = null;
        }
        a.crew.activity = "idle";
        return Status.SUCCEEDED;
    }
}

package com.ringsofiron.aicrew.btree;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.aicrew.ShipTaskBoardComponent;
import com.ringsofiron.aicrew.TreatTask;

/** Puts the casualty down, completes the rescue and asks for a medic. */
public class PutDownCasualty extends CrewLeaf {
    @Override
    public Status execute() {
        CrewAgent a = agent();
        Entity casualty = a.crew.carrying;
        a.service.drop(a.entity);
        ShipTaskBoardComponent board = a.board();
        if (board != null && a.crew.task != null) board.complete(a.crew.task);
        a.crew.task = null;
        if (casualty != null && board != null) {
            GridPositionComponent cp = a.service.position(casualty);
            board.post(new TreatTask(casualty, cp.tileX(), cp.tileY()));
        }
        a.crew.activity = "idle";
        return Status.SUCCEEDED;
    }
}

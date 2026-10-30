package com.ringsofiron.aicrew.btree;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.aicrew.ShipTaskBoardComponent;
import com.ringsofiron.aicrew.TaskRules;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.crew.CrewTask;

/**
 * Dresses the agent for their task before they go to it: fetches a suit from a suit locker and
 * puts it on ({@link SuitErrand#suitUp}) when the task's spot is out of breathable air (a breach,
 * a room losing its air), at battle stations with suits ordered, or for a post whose room is
 * losing its air ({@link TaskRules#suitsUpAtPost}); and hangs a suit up first, off battle stations
 * and in good air, before sitting with a patient, whose care it would slow (round 8). Succeeds at
 * once if they need none, have it on, or wear none. With no suit to be had they go without where
 * the spot has air, and to seal a breach or carry a casualty out of airless air, a dash of
 * seconds on held breath; other work where there is no air is left for a crewmate with a suit.
 */
public class SuitUp extends CrewLeaf {
    @Override
    public Status execute() {
        CrewAgent a = agent();
        CrewTask t = a.crew.task;
        if (t == null) return Status.FAILED;
        if (!a.crew.wearsSuits()) return Status.SUCCEEDED;
        // The spot may be on another craft: a ship latched alongside being stripped, say
        Entity site = TaskRules.siteOf(t);
        // Or back aboard their own ship, from a docked craft they shelter on
        if (site == null && a.pos.ship != a.service.homeOf(a.entity) && TaskRules.goesBackAboard(a.crew, t, a.board())) site = a.service.homeOf(a.entity);
        ShipGridComponent grid = site == null ? null : a.service.grid(site);
        if (grid == null) grid = a.grid();
        ShipTaskBoardComponent board = a.board();
        int tx = TaskRules.targetX(t, a.service), ty = TaskRules.targetY(t, a.service);
        boolean needs = TaskRules.needsSuit(grid, tx, ty);
        // A hand who left their post for a suit (DoWork) fetches one before going back to it
        boolean wants = needs || board != null && board.battleSuits() || a.fetchSuit;
        if (!wants) {
            if (!a.crew.suited || t.kind != CrewTask.Kind.TREAT || TaskRules.needsSuit(a.grid(), a.pos.tileX(), a.pos.tileY())) {
                return Status.SUCCEEDED;
            }
            // Hung up before sitting with a patient; kept on if there is nowhere to hang it
            return SuitErrand.stow(a) == Status.RUNNING ? Status.RUNNING : Status.SUCCEEDED;
        }
        // An EVA suit only when the air is going: it takes long to get into, and is clumsy indoors
        Status dressed = a.crew.suited ? Status.SUCCEEDED : SuitErrand.suitUp(a, needs);
        if (dressed == Status.RUNNING) return dressed;
        a.fetchSuit = false;
        if (dressed == Status.SUCCEEDED || !needs || t.kind == CrewTask.Kind.SEAL_BREACH || t.kind == CrewTask.Kind.RESCUE) {
            return Status.SUCCEEDED;
        }
        a.abandonUnreachable();
        return Status.FAILED;
    }
}

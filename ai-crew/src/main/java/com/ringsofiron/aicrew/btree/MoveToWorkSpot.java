package com.ringsofiron.aicrew.btree;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.aicrew.TaskRules;
import com.ringsofiron.aicrew.nav.GridNode;
import com.ringsofiron.aicrew.nav.GridPathfinder;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.crew.CrewTask;

/** Walks to the closest tile the task can be worked from. */
public class MoveToWorkSpot extends CrewLeaf {
    @Override
    public Status execute() {
        CrewAgent a = agent();
        CrewTask t = a.crew.task;
        ShipGridComponent grid = a.grid();
        if (t == null) return Status.FAILED;
        Entity home = a.service.homeOf(a.entity);
        // Sheltering aboard a docked craft: back aboard their own for a casualty (TaskRules#goesBackAboard)
        boolean back = (a.pos.ship != home || a.pos.crossing != null) && TaskRules.goesBackAboard(a.crew, t, a.board());
        ShipGridComponent homeGrid = back ? a.service.grid(home) : null;
        if (back && homeGrid == null) return Status.FAILED;
        if (!TaskRules.isStillNeeded(t, back ? homeGrid : grid, a.service)) {
            a.board().cancel(t);
            a.crew.task = null;
            return Status.FAILED;
        }
        if (back) {
            a.crew.activity = "going back aboard for " + CrewTasks.describe(t);
            CrewAgent.Travel go = a.travel(home, TaskRules.workSpots(t, homeGrid, a.service.pathfinder(homeGrid).graph, a.service));
            if (go == CrewAgent.Travel.UNREACHABLE) {
                a.abandonUnreachable();
                return Status.FAILED;
            }
            return Status.RUNNING;
        }
        GridPathfinder pf = a.pathfinder();
        a.crew.activity = "going to " + CrewTasks.describe(t);
        int x = a.pos.tileX(), y = a.pos.tileY();
        if (!a.pos.isMoving() && TaskRules.isWorkSpot(t, grid, pf.graph, a.service, x, y)) return Status.SUCCEEDED;
        GridNode dest = a.pos.destination();
        boolean destValid = dest != null && a.pos.isMoving() && !a.pos.pathBlocked
                && TaskRules.isWorkSpot(t, grid, pf.graph, a.service, dest.x, dest.y);
        if (!destValid) {
            if (!a.nearest(x, y, TaskRules.workSpots(t, grid, pf.graph, a.service), a.pathScratch)) {
                a.abandonUnreachable();
                return Status.FAILED;
            }
            a.pos.setPath(a.pathScratch);
        }
        return Status.RUNNING;
    }
}

package com.ringsofiron.shipops;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.aicrew.BehaviorTreeComponent;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.aicrew.TaskRules;
import com.ringsofiron.interiorgrid.FloorTiles;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.stationsdocking.StationComponent;
import java.util.List;

/**
 * A station's hands (technicians, loaders) never leave with a ship they were working aboard (round
 * 8). Undocked with yard hands still aboard, seed 7's ship flew off with two of Highport's
 * technicians, who idled there for good: their way home was gone. Once the ship is no longer
 * docked to their station, each is put back aboard it at once, on a floor tile where they can
 * breathe. A job finished at the berth needs nothing from here: the hands walk home across the dock.
 */
final class StationHands {
    private StationHands() {
    }

    /** Sends home every station hand aboard a craft no longer docked to their station. */
    static void sendHome(CrewService crew) {
        for (Entity e : crew.crewEntities()) {
            CrewMemberComponent c = crew.crew(e);
            GridPositionComponent p = crew.position(e);
            Entity home = c == null ? null : c.home;
            if (home == null || p == null || home.getComponent(StationComponent.class) == null) continue;
            if (p.ship != null && crew.joined(p.ship, home)) continue;
            putAboard(crew, e, p, home);
        }
    }

    private static void putAboard(CrewService crew, Entity e, GridPositionComponent p, Entity station) {
        ShipGridComponent grid = crew.grid(station);
        List<int[]> floor = FloorTiles.of(grid);
        if (floor.isEmpty()) return;
        int[] to = floor.get(0);
        for (int[] t : floor) {
            if (TaskRules.isSafe(grid, t[0], t[1])) {
                to = t;
                break;
            }
        }
        // What they were doing aboard is over: the tree starts afresh, as for a crew member knocked out
        BehaviorTreeComponent bt = e.getComponent(BehaviorTreeComponent.class);
        if (bt != null) bt.tree.resetTask();
        crew.releaseTask(e);
        p.stop();
        p.crossing = null;
        p.setShip(station);
        p.setTile(to[0], to[1]);
    }
}

package com.ringsofiron.aicrew.btree;

import com.badlogic.gdx.utils.Array;
import com.ringsofiron.aicrew.CrewDef;
import com.ringsofiron.aicrew.nav.GridNavGraph;
import com.ringsofiron.aicrew.nav.GridPathfinder;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Side;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.crew.Suit;

/**
 * Drops whatever they were doing, walks to an oxygen tank or a working life support aboard and
 * fills their suit there over {@link CrewDef#suitRefillTime} from its hose, drawing {@link
 * CrewDef#SUIT_OXYGEN_KG} (an EVA suit's pack, {@link Suit#EVA_OXYGEN_KG}). A suit can't come off
 * on air not fit to breathe, so it is filled where it is worn, not swapped at a locker.
 */
public class RefillSuit extends CrewLeaf {
    private float filled;

    @Override
    public void start() {
        filled = 0f;
        CrewAgent a = agent();
        a.refilling = true;
        if (a.crew.task != null) a.releaseTask();
    }

    @Override
    public Status execute() {
        CrewAgent a = agent();
        ShipGridComponent grid = a.grid();
        Array<SubsystemComponent> supplies = new Array<>();
        if (grid == null || !CrewTasks.oxygenSupplies(a, supplies)) return blocked(a);
        GridNavGraph nav = a.pathfinder().graph;
        GridPathfinder.TilePredicate spots = (x, y) -> nav.isWalkable(x, y) && beside(grid, supplies, x, y) != null;
        CrewAgent.Travel go = a.travel(a.pos.ship, spots);
        if (go == CrewAgent.Travel.UNREACHABLE) return blocked(a);
        if (go == CrewAgent.Travel.GOING) {
            a.crew.activity = "going to fill their suit";
            return Status.RUNNING;
        }
        a.crew.activity = "filling their suit";
        filled += a.dt;
        a.crew.showProgress(filled, a.crew.def.suitRefillTime);
        if (filled < a.crew.def.suitRefillTime) return Status.RUNNING;
        CrewTasks.drawSuitOxygen(beside(grid, supplies, a.pos.tileX(), a.pos.tileY()), a.crew.suitKind);
        a.crew.suitAir = a.crew.suitCapacity();
        a.refilling = false;
        return Status.SUCCEEDED;
    }

    private static Status blocked(CrewAgent a) {
        a.refilling = false;
        a.refillBlockedUntil = a.time + CrewAgent.UNREACHABLE_COOLDOWN * 2f;
        return Status.FAILED;
    }

    /** The supply in {@code supplies} that (x, y) stands beside, open to it, or null. */
    static SubsystemComponent beside(ShipGridComponent grid, Array<SubsystemComponent> supplies, int x, int y) {
        for (SubsystemComponent s : supplies) {
            for (Side side : Side.values()) {
                int nx = x + side.dx, ny = y + side.dy;
                if (s.covers(nx, ny) && !grid.isEdgeBlocked(x, y, nx, ny)) return s;
            }
        }
        return null;
    }
}

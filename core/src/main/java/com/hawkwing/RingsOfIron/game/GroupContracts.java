package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.career.Contract;
import com.ringsofiron.macrosim.fleet.FreighterComponent;
import java.util.Locale;
import com.ringsofiron.stationsdocking.DockingSystem;
import com.ringsofiron.macrosim.MacroSimulation;

import static com.hawkwing.RingsOfIron.game.ContractBoard.*;

/**
 * {@link ContractBoard}'s convoys and station defences: each freighter or raider marked as it comes
 * in or is beaten, and the job settled, paid for those that went well, once all are marked.
 */
final class GroupContracts {
    private final ContractBoard board;
    private final GameContext ctx;
    private final MacroSimulation macroSim;
    private final DockingSystem docking;

    GroupContracts(ContractBoard board) {
        this.board = board;
        this.ctx = board.ctx;
        this.macroSim = board.macroSim;
        this.docking = board.docking;
    }

    /** Each freighter docking at the buyer is marked: with the player close by, or without them, or lost. */
    void checkConvoy(Contract c, Entity player) {
        Entity station = ctx.engine.getEntity(c.destinationUuid);
        for (int i = 0; i < c.targets.length; i++) {
            if (c.marks[i] != Contract.MARK_OPEN) continue;
            FreighterComponent f = macroSim.freighters().get(c.targets[i]);
            if (f == null) {
                c.marks[i] = Contract.MARK_BAD;
                board.notices.add("Convoy: " + c.targetNames[i] + " was lost");
                continue;
            }
            if (f.tripsCompleted <= c.trips[i]) continue;
            if (board.distance(player, station) <= ESCORT_RANGE) {
                c.marks[i] = Contract.MARK_GOOD;
                board.notices.add("Convoy: " + c.targetNames[i] + " docked safely");
            } else if (board.now() - arrival(c, i) > ESCORT_GRACE) {
                c.marks[i] = Contract.MARK_BAD;
                board.notices.add("Convoy: " + c.targetNames[i] + " docked without you");
            }
        }
        if (c.count(Contract.MARK_OPEN) == 0) settleGroup(c, "convoy in");
    }

    /** Sim second the convoy's freighter {@code i} was first seen docked. */
    double arrival(Contract c, int i) {
        if (c.arrivals.length != c.targets.length) {
            c.arrivals = new double[c.targets.length];
            java.util.Arrays.fill(c.arrivals, -1);
        }
        if (c.arrivals[i] < 0) c.arrivals[i] = board.now();
        return c.arrivals[i];
    }

    /** Each raider beaten, or gone, is marked; once all are, the station is safe. */
    void checkDefend(Contract c) {
        for (int i = 0; i < c.targets.length; i++) {
            if (c.marks[i] != Contract.MARK_OPEN) continue;
            Entity raider = ctx.engine.getEntity(c.targets[i]);
            if (raider == null || FirstPlay.beaten(ctx.engine, raider)) {
                c.marks[i] = Contract.MARK_GOOD;
                if (c.count(Contract.MARK_OPEN) > 0) board.notices.add("Defence: " + c.targetNames[i] + " is beaten");
            }
        }
        if (c.count(Contract.MARK_OPEN) == 0) settleGroup(c, c.destinationName + " is safe");
    }

    /** Ends a convoy or defence whose targets are all marked: done if any went well, paid for those. */
    void settleGroup(Contract c, String why) {
        int good = c.count(Contract.MARK_GOOD), n = c.targets.length;
        if (good == 0) {
            board.end(c, Contract.Status.FAILED, c.type == Contract.Type.CONVOY ? "none of the convoy came in with you"
                    : "the raiders got away");
            return;
        }
        if (good < n) {
            c.reward = Math.round(c.reward * good / n);
            c.standing = c.standing * good / n;
        }
        board.end(c, Contract.Status.DONE, String.format(Locale.ROOT, "%s, %d of %d", why, good, n));
    }
}

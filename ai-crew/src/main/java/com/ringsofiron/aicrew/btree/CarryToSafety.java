package com.ringsofiron.aicrew.btree;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.aicrew.CrewDef;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewState;
import com.ringsofiron.aicrew.MedbayCarryTask;
import com.ringsofiron.aicrew.TaskRules;
import com.ringsofiron.aicrew.nav.GridNavGraph;
import com.ringsofiron.aicrew.nav.GridNode;
import com.ringsofiron.aicrew.nav.GridPathfinder;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.crew.Suit;
import com.ringsofiron.interiorgrid.Furnishing;
import com.ringsofiron.interiorgrid.SuitLockers;

/**
 * Carries the casualty to the nearest safe tile, or for a {@link MedbayCarryTask} to the nearest
 * free medbay bed. With nowhere aboard to carry them to (the whole ship holed), the rescuer carries
 * the casualty to a suit locker instead and puts a suit from it on them there, taking their own
 * suit time, and leaves them for the next try. Round 9: once that suit runs low
 * ({@link CrewDef#suitRefillShare}), they carry the casualty to the ship's oxygen and fill it
 * there, as they would their own ({@link RefillSuit}). The wound survey lost six crew who lay
 * down in a holed ship, suited, until their suits ran dry beside full oxygen tanks.
 */
public class CarryToSafety extends CrewLeaf {
    private float suiting, filling;

    @Override
    public void start() {
        suiting = 0f;
        filling = 0f;
    }

    @Override
    public Status execute() {
        CrewAgent a = agent();
        CrewMemberComponent c = a.service.crew(a.crew.carrying);
        if (c == null || c.state != CrewState.UNCONSCIOUS) return Status.FAILED;
        ShipGridComponent grid = a.grid();
        Entity casualty = a.crew.carrying;
        boolean medbay = a.crew.task instanceof MedbayCarryTask;
        GridPathfinder.TilePredicate there = medbay
                ? (tx, ty) -> TaskRules.isSafe(grid, tx, ty) && TaskRules.isMedbay(grid, tx, ty)
                        && !TaskRules.bedTaken(a.service, a.pos.ship, casualty, tx, ty)
                : (tx, ty) -> TaskRules.isSafe(grid, tx, ty);
        a.crew.activity = "carrying " + c.name + (medbay ? " to the medbay" : " to safety");
        int x = a.pos.tileX(), y = a.pos.tileY();
        if (!a.pos.isMoving() && there.test(x, y)) return Status.SUCCEEDED;
        GridNode dest = a.pos.destination();
        boolean destValid = dest != null && a.pos.isMoving() && !a.pos.pathBlocked && there.test(dest.x, dest.y);
        if (!destValid) {
            if (!a.nearest(x, y, there, a.pathScratch)) {
                // Nowhere aboard is safe: aboard the craft it is docked to, a station's berth say
                Entity shelter = medbay ? null : CrewTasks.shelter(a);
                if (shelter != null) {
                    ShipGridComponent ashore = a.service.grid(shelter);
                    CrewAgent.Travel go = a.travel(shelter, (tx, ty) -> TaskRules.isSafe(ashore, tx, ty));
                    if (go != CrewAgent.Travel.UNREACHABLE) {
                        a.crew.activity = "carrying " + c.name + " aboard " + CrewTasks.SHELTER;
                        return Status.RUNNING;
                    }
                }
                // No bed to be reached: they are put down where they are, on safe deck
                if (!medbay && !c.suited && c.wearsSuits()) {
                    Status dressed = dress(a, c, grid);
                    if (dressed == Status.RUNNING) return dressed;
                }
                if (!medbay && c.suited && c.suitAir < c.suitCapacity() * c.def.suitRefillShare && TaskRules.suitCare(a.board())) {
                    Status fill = fillSuit(a, c, grid);
                    if (fill != null) return fill;
                }
                a.abandonUnreachable();
                return Status.FAILED;
            }
            a.pos.setPath(a.pathScratch);
        }
        return Status.RUNNING;
    }

    /**
     * Carries the casualty to a suit locker and puts a suit from it on them there: a pressure
     * suit, or an EVA suit if no pressure suit can be reached. FAILED if there is none to be had.
     */
    private Status dress(CrewAgent a, CrewMemberComponent c, ShipGridComponent grid) {
        Suit.Kind kind = Suit.Kind.IVA;
        Furnishing at = SuitErrand.lockerAtHand(a, grid, kind, true);
        CrewAgent.Travel go = null;
        if (at == null) {
            // A pressure suit a few steps off before an EVA suit at hand, slow to get anyone into
            go = SuitErrand.toLocker(a, grid, Suit.Kind.IVA, true);
            if (go == CrewAgent.Travel.UNREACHABLE) {
                at = SuitErrand.lockerAtHand(a, grid, kind = Suit.Kind.EVA, true);
                if (at == null) go = SuitErrand.toLocker(a, grid, Suit.Kind.EVA, true);
            }
        }
        if (at != null) {
            a.crew.activity = "putting " + SuitErrand.article(kind) + " on " + c.name;
            suiting += a.dt;
            a.crew.showProgress(suiting, a.crew.suitTime(kind));
            if (suiting < a.crew.suitTime(kind)) return Status.RUNNING;
            c.wear(SuitLockers.take(at, kind));
            suiting = 0f;
            return Status.SUCCEEDED;
        }
        suiting = 0f;
        if (go == CrewAgent.Travel.UNREACHABLE) return Status.FAILED;
        a.crew.activity = "carrying " + c.name + " to " + SuitErrand.article(kind);
        return Status.RUNNING;
    }

    /** Takes the casualty to the ship's oxygen and fills their suit; null if there is none to be reached. */
    private Status fillSuit(CrewAgent a, CrewMemberComponent c, ShipGridComponent grid) {
        Array<SubsystemComponent> supplies = new Array<>();
        if (!CrewTasks.oxygenSupplies(a, supplies)) return null;
        GridNavGraph nav = a.pathfinder().graph;
        CrewAgent.Travel go = a.travel(a.pos.ship, (x, y) -> nav.isWalkable(x, y) && RefillSuit.beside(grid, supplies, x, y) != null);
        if (go == CrewAgent.Travel.UNREACHABLE) return null;
        if (go == CrewAgent.Travel.GOING) {
            a.crew.activity = "carrying " + c.name + " to fill their suit";
            return Status.RUNNING;
        }
        a.crew.activity = "filling " + c.name + "'s suit";
        filling += a.dt;
        a.crew.showProgress(filling, c.def.suitRefillTime);
        if (filling < c.def.suitRefillTime) return Status.RUNNING;
        CrewTasks.drawSuitOxygen(RefillSuit.beside(grid, supplies, a.pos.tileX(), a.pos.tileY()), c.suitKind);
        c.suitAir = c.suitCapacity();
        filling = 0f;
        return Status.SUCCEEDED;
    }
}

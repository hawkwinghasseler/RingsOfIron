package com.ringsofiron.aicrew.btree;

import com.ringsofiron.aicrew.CrewDef;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewRecord;
import com.ringsofiron.aicrew.CrewRole;
import com.ringsofiron.aicrew.ManConsoleTask;
import com.ringsofiron.aicrew.ShipTaskBoardComponent;
import com.ringsofiron.aicrew.TaskRules;
import com.ringsofiron.aicrew.StabiliseTask;
import com.ringsofiron.aicrew.TreatTask;
import com.ringsofiron.interiorgrid.DamageStates;
import com.ringsofiron.interiorgrid.LeakPatches;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.interiorgrid.crew.CrewTask;
import com.ringsofiron.interiorgrid.crew.IsolateTankTask;
import com.ringsofiron.interiorgrid.crew.PatchLeakTask;
import com.ringsofiron.interiorgrid.crew.RepairTask;
import com.ringsofiron.interiorgrid.crew.ResetShortTask;
import com.ringsofiron.interiorgrid.crew.Suit;
import com.ringsofiron.interiorgrid.Furnishing;
import com.ringsofiron.interiorgrid.SuitLockers;

/** Does the work of a repair, seal, extinguish or treat task until it is finished. */
public class DoWork extends CrewLeaf {
    private float elapsed;

    @Override
    public void start() {
        elapsed = 0f;
    }

    @Override
    public Status execute() {
        CrewAgent a = agent();
        CrewTask t = a.crew.task;
        ShipGridComponent grid = a.grid();
        if (t == null) return Status.FAILED;
        if (!TaskRules.isStillNeeded(t, grid, a.service)) return Status.SUCCEEDED;
        if (!TaskRules.isWorkSpot(t, grid, a.pathfinder().graph, a.service, a.pos.tileX(), a.pos.tileY())) {
            return Status.FAILED; // target moved (a casualty was carried off); re-plan next step
        }
        // The air going where they work: off for a suit while there is time to get into one, then
        // back to it (a breach they dash in to seal, and a post, which has its own locker, aside)
        if (!a.crew.suited && a.crew.wearsSuits() && t.kind != CrewTask.Kind.SEAL_BREACH && t.kind != CrewTask.Kind.MAN_CONSOLE
                && a.time >= a.suitBlockedUntil && !TaskRules.isBreathable(grid, a.pos.tileX(), a.pos.tileY())) {
            a.fetchSuit = true;
            a.releaseTask();
            return Status.FAILED;
        }
        // A light wound and a suit slow the work
        float dt = a.dt * a.crew.workPace();
        elapsed += dt;
        a.crew.activity = CrewTasks.describe(t);
        if (t.kind == CrewTask.Kind.REPAIR || t.kind == CrewTask.Kind.EXTINGUISH || t.kind == CrewTask.Kind.SEAL_BREACH
                || t.kind == CrewTask.Kind.PATCH_LEAK || t.kind == CrewTask.Kind.RESET_SHORT
                || t.kind == CrewTask.Kind.ISOLATE_TANK) {
            a.crew.record().pendingWork += dt; // engineering experience
        }
        Tile tile = grid.getTile(t.x, t.y);
        switch (t.kind) {
            case EXTINGUISH: case SEAL_BREACH: case PATCH_LEAK: case REPAIR: case RESET_SHORT: case ISOLATE_TANK:
                return engineering(a, t, grid, tile, dt);
            case TREAT:
                return treat(a, t, grid);
            case STABILISE:
                return stabilise(a, t, grid);
            case MAN_CONSOLE:
                return manConsole(a, t, grid);
            case EVACUATE:
                // In the pod: wait there for launch
                a.crew.activity = "waiting in an escape pod";
                return Status.RUNNING;
            default:
                return Status.FAILED;
        }
    }

    /** Fighting a fire, sealing a breach, patching a leak, a repair, resetting a short or isolating a tank. */
    private Status engineering(CrewAgent a, CrewTask t, ShipGridComponent grid, Tile tile, float dt) {
        switch (t.kind) {
            case EXTINGUISH:
                tile.fireIntensity -= a.crew.def.extinguishRate * dt;
                if (tile.fireIntensity <= 0f) {
                    tile.extinguish();
                    return Status.SUCCEEDED;
                }
                return Status.RUNNING;
            case SEAL_BREACH:
                a.crew.showProgress(elapsed, a.crew.def.sealTime);
                if (elapsed < a.crew.def.sealTime) return Status.RUNNING;
                tile.bulkheadSealed = true;
                grid.gridDirty = true;
                return Status.SUCCEEDED;
            case PATCH_LEAK: {
                a.crew.showProgress(elapsed, a.crew.def.patchTime);
                if (elapsed < a.crew.def.patchTime) return Status.RUNNING;
                PatchLeakTask p = (PatchLeakTask) t;
                return LeakPatches.patch(grid, p.x, p.y, p.side) ? Status.SUCCEEDED : Status.FAILED;
            }
            case REPAIR: {
                SubsystemComponent s = ((RepairTask) t).subsystem.getComponent(SubsystemComponent.class);
                s.health = Math.min(1f, s.health + a.crew.def.repairRate * dt);
                // Slow, and nothing on the deck shows it: the bar is the system's health
                a.crew.progress = s.health;
                if (s.health >= 1f) {
                    grid.gridDirty = true; // a repaired system may route power again
                    DamageStates.repaired(grid, s);
                    return Status.SUCCEEDED;
                }
                return Status.RUNNING;
            }
            case RESET_SHORT: {
                a.crew.showProgress(elapsed, a.crew.def.resetTime);
                if (elapsed < a.crew.def.resetTime) return Status.RUNNING;
                ResetShortTask r = (ResetShortTask) t;
                if (r.subsystem == null) DamageStates.reset(grid, t.x, t.y);
                else DamageStates.reset(grid, r.subsystem.getComponent(SubsystemComponent.class));
                return Status.SUCCEEDED;
            }
            case ISOLATE_TANK: {
                a.crew.showProgress(elapsed, a.crew.def.isolateTime);
                if (elapsed < a.crew.def.isolateTime) return Status.RUNNING;
                DamageStates.isolate(((IsolateTankTask) t).tank.getComponent(SubsystemComponent.class));
                return Status.SUCCEEDED;
            }
            default:
                return Status.FAILED;
        }
    }

    /** Sitting with a patient while CrewHealthSystem gives the care, unless something more pressing wants them. */
    private Status treat(CrewAgent a, CrewTask t, ShipGridComponent grid) {
        // A patient isn't sat with while the ship flies with nobody at the helm
        ShipTaskBoardComponent board = a.board();
        if (board != null && board.wantedHelm(a.crew.role, a::mayTake) != null) {
            a.releaseTask();
            return Status.FAILED;
        }
        // A light wound waits for nothing else: anything else on the board comes first
        if (TreatTask.isLight(t, a.service) && board != null && board.best(a.crew.role, a.pos.tileX(), a.pos.tileY(),
                o -> o.kind != CrewTask.Kind.MAN_CONSOLE && !TreatTask.isLight(o, a.service) && a.mayTake(o)) != null) {
            a.releaseTask();
            return Status.FAILED;
        }
        // The fight over, the patient goes to a medbay bed first: whoever treats them carries them
        if (CrewTasks.carryWaits(board, ((TreatTask) t).patient)) {
            a.releaseTask();
            return Status.FAILED;
        }
        CrewMemberComponent p = a.service.crew(((TreatTask) t).patient);
        p.treatedBy = a.entity;
        // CrewHealthSystem gives the care: the bar shows the wound easing toward the next level down
        a.crew.showProgress(p.care, CrewDef.careTime(p.wound));
        // Off battle stations and in good air, the carer slips their suit off to work (round 8):
        // it slowed the care of the wounded long after the fight. SuitUp hangs it up on the
        // way; one still on comes off here only into a locker within reach of the patient
        if (a.crew.suited && (board == null || !board.battleSuits()) && !TaskRules.needsSuit(grid, a.pos.tileX(), a.pos.tileY())
                && !TaskRules.needsSuit(grid, t.x, t.y)) {
            Furnishing locker = SuitLockers.within(grid, a.pos.x, a.pos.y, f -> SuitLockers.hasRoom(f, a.crew.suitKind));
            if (locker != null && a.pos.ship == a.service.homeOf(a.entity)) {
                // On the crew member, so it is saved (round 9)
                a.crew.unsuiting += a.dt;
                a.crew.activity = SuitErrand.undressing(a.crew.suitKind);
                a.crew.showProgress(a.crew.unsuiting, a.crew.suitTime(a.crew.suitKind) * 0.5f);
                if (a.crew.unsuiting >= a.crew.suitTime(a.crew.suitKind) * 0.5f) {
                    Suit off = a.crew.takeOff();
                    if (!SuitLockers.stow(locker, off)) a.crew.wear(off);
                }
            }
        }
        return Status.RUNNING; // CrewHealthSystem heals faster; done when the patient wakes or the wound heals
    }

    /** Stopping a patient's bleeding. */
    private Status stabilise(CrewAgent a, CrewTask t, ShipGridComponent grid) {
        // Quicker for a medic and for skill; slow with no medical stores left
        float time = a.crew.def.stabiliseTime * (a.crew.role == CrewRole.MEDIC ? CrewDef.MEDIC_STABILISE : 1f)
                / CrewRecord.factor(a.crew.record().medicine) / (grid.medicalStores > 0 ? 1f : CrewDef.NO_STORES_CARE);
        a.crew.showProgress(elapsed, time);
        if (elapsed < time) return Status.RUNNING;
        if (grid.medicalStores > 0) grid.medicalStores--;
        a.service.crew(((StabiliseTask) t).patient).stabilised = true;
        return Status.SUCCEEDED;
    }

    /** Holding a post, suiting up at it when needed, until anything else on the board wants them. */
    private Status manConsole(CrewAgent a, CrewTask t, ShipGridComponent grid) {
        ShipTaskBoardComponent board = a.board();
        // The suit goes on where they sit, the post still held, for battle stations or as
        // their room's air goes (round 9): from the locker beside the post. With none within
        // reach the post goes back up while they fetch one (SuitUp), unless none is to be had
        if (!a.crew.suited && a.crew.wearsSuits() && TaskRules.suitsUpAtPost(grid, board, a.pos.tileX(), a.pos.tileY())) {
            Furnishing locker = SuitLockers.within(grid, a.pos.x, a.pos.y, f -> SuitLockers.hasSuit(f, Suit.Kind.IVA));
            if (locker != null) {
                a.crew.activity = "putting on a pressure suit at their post";
                a.crew.suiting += a.dt;
                a.crew.showProgress(a.crew.suiting, a.crew.suitTime(Suit.Kind.IVA));
                if (a.crew.suiting >= a.crew.suitTime(Suit.Kind.IVA)) a.crew.wear(SuitLockers.take(locker, Suit.Kind.IVA));
                return Status.RUNNING;
            }
            if (a.time >= a.suitBlockedUntil) {
                a.fetchSuit = true;
                a.releaseTask();
                return Status.FAILED;
            }
        }
        // At it until anything else on the board wants them: then the console goes back up.
        // The helm the ship needs isn't left to sit with a patient
        boolean wanted = board != null && board.helmWanted && ((ManConsoleTask) t).type() == SubsystemComponent.SubsystemType.HELM;
        if (board != null && board.best(a.crew.role, a.pos.tileX(), a.pos.tileY(),
                o -> o.kind != CrewTask.Kind.MAN_CONSOLE && !(wanted && o.kind == CrewTask.Kind.TREAT)
                        && !TreatTask.isLight(o, a.service) && a.mayTake(o)) != null) {
            a.releaseTask();
            return Status.FAILED;
        }
        return Status.RUNNING;
    }
}

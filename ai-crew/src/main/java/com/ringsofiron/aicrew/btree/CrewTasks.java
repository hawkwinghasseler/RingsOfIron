package com.ringsofiron.aicrew.btree;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.aicrew.CarryTask;
import com.ringsofiron.aicrew.YardTask;
import com.ringsofiron.aicrew.CrewDef;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewRecord;
import com.ringsofiron.aicrew.CrewRole;
import com.ringsofiron.aicrew.CrewState;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.aicrew.ForceDoorTask;
import com.ringsofiron.aicrew.BoardTask;
import com.ringsofiron.aicrew.ManConsoleTask;
import com.ringsofiron.aicrew.MedbayCarryTask;
import com.ringsofiron.aicrew.ShipTaskBoardComponent;
import com.ringsofiron.aicrew.TaskRules;
import com.ringsofiron.aicrew.StabiliseTask;
import com.ringsofiron.aicrew.TreatTask;
import com.ringsofiron.aicrew.nav.GridNavGraph;
import com.ringsofiron.aicrew.nav.GridNode;
import com.ringsofiron.aicrew.nav.GridPathfinder;
import com.ringsofiron.interiorgrid.CargoHoldComponent;
import com.ringsofiron.interiorgrid.DamageStates;
import com.ringsofiron.interiorgrid.GridLink;
import com.ringsofiron.interiorgrid.LeakPatches;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Side;
import com.ringsofiron.interiorgrid.TankSupply;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.Fluid;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.interiorgrid.crew.CrewDamageSink;
import com.ringsofiron.interiorgrid.crew.CrewTask;
import com.ringsofiron.interiorgrid.crew.IsolateTankTask;
import com.ringsofiron.interiorgrid.crew.PatchLeakTask;
import com.ringsofiron.interiorgrid.crew.RepairTask;
import com.ringsofiron.interiorgrid.crew.RescueTask;
import com.ringsofiron.interiorgrid.crew.ResetShortTask;

/** The leaf tasks of the crew behavior tree. See {@link CrewBehaviorTrees} for how they fit together. */
public final class CrewTasks {

    private CrewTasks() {}

    // -----------------------------------------------------------------------
    // Conditions (used as guards)
    // -----------------------------------------------------------------------

    /** Succeeds when a conscious hostile is in sight; remembers it as the agent's target. */
    public static class HostileInSight extends CrewLeaf {
        @Override
        public Status execute() {
            CrewAgent a = agent();
            a.target = findHostile(a);
            return a.target != null ? Status.SUCCEEDED : Status.FAILED;
        }
    }

    /**
     * Succeeds while the agent's suit is running low ({@link CrewDef#suitRefillShare}) out of
     * breathable air and the ship has oxygen to fill it from, and on until the suit is full.
     */
    public static class SuitAirLow extends CrewLeaf {
        @Override
        public Status execute() {
            CrewAgent a = agent();
            CrewMemberComponent c = a.crew;
            if (!c.suited || c.def.suitAir <= 0f || c.held || a.time < a.refillBlockedUntil) return fail(a);
            if (!a.refilling) {
                ShipGridComponent grid = a.grid();
                if (grid == null || TaskRules.isBreathable(grid, a.pos.tileX(), a.pos.tileY())) return fail(a);
                if (c.suitAir >= c.def.suitAir * c.def.suitRefillShare) return fail(a);
            } else if (c.suitAir >= c.def.suitAir) {
                return fail(a);
            }
            return oxygenSupplies(a, null) ? Status.SUCCEEDED : fail(a);
        }

        private static Status fail(CrewAgent a) {
            a.refilling = false;
            return Status.FAILED;
        }
    }

    /**
     * Drops whatever they were doing, walks to an oxygen tank or a working life support aboard and
     * fills their suit there over {@link CrewDef#suitRefillTime}, drawing {@link CrewDef#SUIT_OXYGEN_KG}.
     */
    public static class RefillSuit extends CrewLeaf {
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
            if (grid == null || !oxygenSupplies(a, supplies)) return blocked(a);
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
            if (filled < a.crew.def.suitRefillTime) return Status.RUNNING;
            SubsystemComponent at = beside(grid, supplies, a.pos.tileX(), a.pos.tileY());
            if (at != null) {
                if (at.type == SubsystemComponent.SubsystemType.TANK) {
                    at.contents = Math.max(0, at.contents - CrewDef.SUIT_OXYGEN_KG);
                } else {
                    TankSupply.draw(at, Fluid.OXYGEN, CrewDef.SUIT_OXYGEN_KG);
                }
            }
            a.crew.suitAir = a.crew.def.suitAir;
            a.refilling = false;
            return Status.SUCCEEDED;
        }

        private static Status blocked(CrewAgent a) {
            a.refilling = false;
            a.refillBlockedUntil = a.time + CrewAgent.UNREACHABLE_COOLDOWN * 2f;
            return Status.FAILED;
        }

        /** The supply in {@code supplies} that (x, y) stands beside, open to it, or null. */
        private static SubsystemComponent beside(ShipGridComponent grid, Array<SubsystemComponent> supplies, int x, int y) {
            for (SubsystemComponent s : supplies) {
                for (Side side : Side.values()) {
                    int nx = x + side.dx, ny = y + side.dy;
                    if (s.covers(nx, ny) && !grid.isEdgeBlocked(x, y, nx, ny)) return s;
                }
            }
            return null;
        }
    }

    /**
     * Whether the agent's ship has oxygen to fill a suit from: an oxygen tank holding some, or a
     * working life support with oxygen piped to it (or with no oxygen tanks aboard at all, a layout
     * from before tanks). Adds each to {@code out} when given.
     */
    static boolean oxygenSupplies(CrewAgent a, Array<SubsystemComponent> out) {
        ShipSubsystemRegistryComponent registry = a.pos.ship == null ? null : a.pos.ship.getComponent(ShipSubsystemRegistryComponent.class);
        if (registry == null) return false;
        boolean tanks = TankSupply.hasTanks(registry, Fluid.OXYGEN), any = false;
        for (Entity e : registry.subsystems) {
            SubsystemComponent s = e == null ? null : e.getComponent(SubsystemComponent.class);
            if (s == null) continue;
            boolean supply = s.type == SubsystemComponent.SubsystemType.TANK
                    ? s.fluid == Fluid.OXYGEN && s.canSupply() && s.contents > CrewDef.SUIT_OXYGEN_KG
                    : s.type == SubsystemComponent.SubsystemType.LIFE_SUPPORT && s.isOperational && !s.isDisabled()
                            && (!tanks || TankSupply.available(s, Fluid.OXYGEN) > CrewDef.SUIT_OXYGEN_KG);
            if (!supply) continue;
            any = true;
            if (out == null) return true;
            out.add(s);
        }
        return any;
    }

    /** Succeeds when the agent holds a task or the board has one for them. */
    public static class HasWork extends CrewLeaf {
        @Override
        public Status execute() {
            CrewAgent a = agent();
            boolean holding = a.crew.task != null && a.crew.task.isActive() && a.crew.task.claimedBy == a.entity;
            return holding || a.hasAvailableTask() ? Status.SUCCEEDED : Status.FAILED;
        }
    }

    /** Succeeds if the agent's task is of the given kind (or, negated, of any other kind). */
    public static class IsTaskKind extends CrewLeaf {
        private final CrewTask.Kind kind;
        private final boolean negate;

        public IsTaskKind(CrewTask.Kind kind) {
            this(kind, false);
        }

        public IsTaskKind(CrewTask.Kind kind, boolean negate) {
            this.kind = kind;
            this.negate = negate;
        }

        @Override
        public Status execute() {
            CrewTask t = agent().crew.task;
            boolean match = t != null && t.kind == kind;
            return t != null && match != negate ? Status.SUCCEEDED : Status.FAILED;
        }
    }

    // -----------------------------------------------------------------------
    // Combat
    // -----------------------------------------------------------------------

    /** Closes to weapon range with line of sight and shoots the target until it drops. */
    public static class EngageHostile extends CrewLeaf {
        private float repathTimer;

        @Override
        public void start() {
            repathTimer = 0f;
        }

        @Override
        public Status execute() {
            CrewAgent a = agent();
            CrewMemberComponent t = a.service.crew(a.target);
            if (t == null || !t.isConscious()) return Status.SUCCEEDED;
            GridPositionComponent tp = a.service.position(a.target);
            float dist = a.service.distance(a.pos, tp);
            boolean sight = a.service.canSee(a.pos, tp);
            a.crew.fireCooldown -= a.dt;
            if (sight && dist <= a.crew.def.weaponRange) {
                a.pos.stop();
                a.crew.activity = "shooting " + t.name;
                if (a.crew.fireCooldown <= 0f) {
                    a.crew.fireCooldown = a.crew.def.fireInterval;
                    if (a.service.random.nextFloat() < a.crew.def.hitChance) {
                        a.service.applyDamage(a.target, a.crew.def.weaponDamage, CrewDamageSink.DamageCause.SMALL_ARMS);
                        a.crew.record().pendingHits++;
                        if (!t.isConscious()) a.crew.record().pendingDowns++;
                    }
                }
                return Status.RUNNING;
            }
            a.crew.activity = "closing on " + t.name;
            if (tp.ship != a.pos.ship) {
                // Aboard a docked craft: over to it, to beside them
                CrewAgent.Travel go = a.travel(tp.ship, near(a, tp.ship, tp.tileX(), tp.tileY(), 1, 1));
                return go == CrewAgent.Travel.UNREACHABLE ? Status.FAILED : Status.RUNNING;
            }
            repathTimer -= a.dt;
            if (!a.pos.isMoving() || a.pos.pathBlocked || repathTimer <= 0f) {
                repathTimer = 1f;
                if (a.findPath(a.pos.tileX(), a.pos.tileY(), tp.tileX(), tp.tileY(), a.pathScratch)) {
                    // Stop one tile short: no need to share the target's tile
                    if (a.pathScratch.size > 1) a.pathScratch.pop();
                    a.pos.setPath(a.pathScratch);
                } else {
                    return Status.FAILED;
                }
            }
            return Status.RUNNING;
        }

        @Override
        public void end() {
            agent().pos.stop();
        }
    }

    // -----------------------------------------------------------------------
    // Tasks
    // -----------------------------------------------------------------------

    /** Keeps the agent's claimed task, or claims the best one from the board. */
    public static class ClaimTask extends CrewLeaf {
        @Override
        public Status execute() {
            CrewAgent a = agent();
            CrewTask held = a.crew.task;
            ShipTaskBoardComponent board = a.board();
            if (held != null && held.isActive() && held.claimedBy == a.entity) {
                // Fire falling where the job is: put it down until the fight is over, unless already
                // carrying someone out
                if (a.crew.carrying == null && TaskRules.waitsOutFire(held, board, a.service)) board.release(held);
                else return Status.SUCCEEDED;
            }
            a.crew.task = null;
            if (board == null || board.faction != a.crew.faction) return Status.FAILED;
            CrewTask t = board.best(a.crew.role, a.pos.tileX(), a.pos.tileY(), a::mayTake);
            if (t == null || !board.claim(t, a.entity)) return Status.FAILED;
            a.crew.task = t;
            return Status.SUCCEEDED;
        }
    }

    /** Walks to the closest tile the task can be worked from. */
    public static class MoveToWorkSpot extends CrewLeaf {
        @Override
        public Status execute() {
            CrewAgent a = agent();
            CrewTask t = a.crew.task;
            ShipGridComponent grid = a.grid();
            if (t == null) return Status.FAILED;
            if (!TaskRules.isStillNeeded(t, grid, a.service)) {
                a.board().cancel(t);
                a.crew.task = null;
                return Status.FAILED;
            }
            GridPathfinder pf = a.pathfinder();
            a.crew.activity = "going to " + describe(t);
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

    /**
     * Puts the agent's space suit on before they go to a task whose spot is out of breathable air
     * (a breach, a room losing its air). Succeeds at once if they need none, have it on, or have none.
     */
    public static class SuitUp extends CrewLeaf {
        private float elapsed;

        @Override
        public void start() {
            elapsed = 0f;
        }

        @Override
        public Status execute() {
            CrewAgent a = agent();
            CrewTask t = a.crew.task;
            if (t == null) return Status.FAILED;
            if (a.crew.suited || a.crew.def.suitAir <= 0f) return Status.SUCCEEDED;
            // The spot may be on another craft: a ship latched alongside being stripped, say
            Entity site = TaskRules.siteOf(t);
            ShipGridComponent grid = site == null ? null : a.service.grid(site);
            if (grid == null) grid = a.grid();
            ShipTaskBoardComponent board = a.board();
            if (!TaskRules.needsSuit(grid, TaskRules.targetX(t, a.service), TaskRules.targetY(t, a.service))
                    && (board == null || !board.battleSuits())) {
                return Status.SUCCEEDED;
            }
            a.pos.stop();
            a.crew.activity = "putting on a suit";
            elapsed += a.dt;
            if (elapsed < a.crew.def.suitTime) return Status.RUNNING;
            a.crew.setSuited(true);
            return Status.SUCCEEDED;
        }
    }

    /** Does the work of a repair, seal, extinguish or treat task until it is finished. */
    public static class DoWork extends CrewLeaf {
        private float elapsed, unsuiting;

        @Override
        public void start() {
            elapsed = 0f;
            unsuiting = 0f;
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
            // A light wound and a suit slow the work
            float dt = a.dt * a.crew.workPace();
            elapsed += dt;
            a.crew.activity = describe(t);
            if (t.kind == CrewTask.Kind.REPAIR || t.kind == CrewTask.Kind.EXTINGUISH || t.kind == CrewTask.Kind.SEAL_BREACH
                    || t.kind == CrewTask.Kind.PATCH_LEAK || t.kind == CrewTask.Kind.RESET_SHORT
                    || t.kind == CrewTask.Kind.ISOLATE_TANK) {
                a.crew.record().pendingWork += dt; // engineering experience
            }
            Tile tile = grid.getTile(t.x, t.y);
            switch (t.kind) {
                case EXTINGUISH:
                    tile.fireIntensity -= a.crew.def.extinguishRate * dt;
                    if (tile.fireIntensity <= 0f) {
                        tile.extinguish();
                        return Status.SUCCEEDED;
                    }
                    return Status.RUNNING;
                case SEAL_BREACH:
                    if (elapsed < a.crew.def.sealTime) return Status.RUNNING;
                    tile.bulkheadSealed = true;
                    grid.gridDirty = true;
                    return Status.SUCCEEDED;
                case PATCH_LEAK: {
                    if (elapsed < a.crew.def.patchTime) return Status.RUNNING;
                    PatchLeakTask p = (PatchLeakTask) t;
                    return LeakPatches.patch(grid, p.x, p.y, p.side) ? Status.SUCCEEDED : Status.FAILED;
                }
                case REPAIR: {
                    SubsystemComponent s = ((RepairTask) t).subsystem.getComponent(SubsystemComponent.class);
                    s.health = Math.min(1f, s.health + a.crew.def.repairRate * dt);
                    if (s.health >= 1f) {
                        grid.gridDirty = true; // a repaired system may route power again
                        DamageStates.repaired(grid, s);
                        return Status.SUCCEEDED;
                    }
                    return Status.RUNNING;
                }
                case RESET_SHORT: {
                    if (elapsed < a.crew.def.resetTime) return Status.RUNNING;
                    ResetShortTask r = (ResetShortTask) t;
                    if (r.subsystem == null) DamageStates.reset(grid, t.x, t.y);
                    else DamageStates.reset(grid, r.subsystem.getComponent(SubsystemComponent.class));
                    return Status.SUCCEEDED;
                }
                case ISOLATE_TANK: {
                    if (elapsed < a.crew.def.isolateTime) return Status.RUNNING;
                    DamageStates.isolate(((IsolateTankTask) t).tank.getComponent(SubsystemComponent.class));
                    return Status.SUCCEEDED;
                }
                case TREAT: {
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
                    if (carryWaits(board, ((TreatTask) t).patient)) {
                        a.releaseTask();
                        return Status.FAILED;
                    }
                    CrewMemberComponent p = a.service.crew(((TreatTask) t).patient);
                    p.treatedBy = a.entity;
                    // Off battle stations and in good air, the carer slips their suit off to work (round 8):
                    // it slowed the care of the wounded long after the fight
                    if (a.crew.suited && (board == null || !board.battleSuits()) && !TaskRules.needsSuit(grid, a.pos.tileX(), a.pos.tileY())
                            && !TaskRules.needsSuit(grid, t.x, t.y)) {
                        unsuiting += a.dt;
                        a.crew.activity = "taking off a suit";
                        if (unsuiting >= a.crew.def.suitTime * 0.5f) {
                            a.crew.setSuited(false);
                            unsuiting = 0f;
                        }
                    }
                    return Status.RUNNING; // CrewHealthSystem heals faster; done when the patient wakes or the wound heals
                }
                case STABILISE: {
                    // Quicker for a medic and for skill; slow with no medical stores left
                    float time = a.crew.def.stabiliseTime * (a.crew.role == CrewRole.MEDIC ? CrewDef.MEDIC_STABILISE : 1f)
                            / CrewRecord.factor(a.crew.record().medicine) / (grid.medicalStores > 0 ? 1f : CrewDef.NO_STORES_CARE);
                    if (elapsed < time) return Status.RUNNING;
                    if (grid.medicalStores > 0) grid.medicalStores--;
                    a.service.crew(((StabiliseTask) t).patient).stabilised = true;
                    return Status.SUCCEEDED;
                }
                case MAN_CONSOLE: {
                    // At it until anything else on the board wants them: then the console goes back up.
                    // The helm the ship needs isn't left to sit with a patient
                    ShipTaskBoardComponent board = a.board();
                    boolean wanted = board != null && board.helmWanted && ((ManConsoleTask) t).type() == SubsystemComponent.SubsystemType.HELM;
                    if (board != null && board.best(a.crew.role, a.pos.tileX(), a.pos.tileY(),
                            o -> o.kind != CrewTask.Kind.MAN_CONSOLE && !(wanted && o.kind == CrewTask.Kind.TREAT)
                                    && !TreatTask.isLight(o, a.service) && a.mayTake(o)) != null) {
                        a.releaseTask();
                        return Status.FAILED;
                    }
                    return Status.RUNNING;
                }
                case EVACUATE:
                    // In the pod: wait there for launch
                    a.crew.activity = "waiting in an escape pod";
                    return Status.RUNNING;
                default:
                    return Status.FAILED;
            }
        }
    }

    /** True while an open {@link MedbayCarryTask} on {@code board} waits for {@code patient}. */
    static boolean carryWaits(ShipTaskBoardComponent board, Entity patient) {
        if (board == null) return false;
        for (CrewTask o : board.tasks) {
            if (o.status == CrewTask.Status.OPEN && o instanceof MedbayCarryTask && ((MedbayCarryTask) o).casualty == patient) return true;
        }
        return false;
    }

    /** Marks the agent's task done on the board. */
    public static class CompleteTask extends CrewLeaf {
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

    /** Lifts the rescue task's casualty, who must be on the agent's tile. */
    public static class PickUpCasualty extends CrewLeaf {
        @Override
        public Status execute() {
            CrewAgent a = agent();
            Entity casualty = ((RescueTask) a.crew.task).casualty;
            CrewMemberComponent c = a.service.crew(casualty);
            GridPositionComponent cp = a.service.position(casualty);
            if (c == null || c.state != CrewState.UNCONSCIOUS) return Status.FAILED;
            if (c.carriedBy != null && c.carriedBy != a.entity) return Status.FAILED;
            if (cp.tileX() != a.pos.tileX() || cp.tileY() != a.pos.tileY()) return Status.FAILED;
            a.service.pickUp(a.entity, casualty);
            return Status.SUCCEEDED;
        }
    }

    /**
     * Carries the casualty to the nearest safe tile, or for a {@link MedbayCarryTask} to the nearest
     * free medbay bed. With nowhere aboard to carry them to (the whole
     * ship holed), the rescuer puts the casualty's suit on them where they are instead, taking their
     * own suit time, and leaves them for the next try.
     */
    public static class CarryToSafety extends CrewLeaf {
        private float suiting;

        @Override
        public void start() {
            suiting = 0f;
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
                    Entity shelter = medbay ? null : shelter(a);
                    if (shelter != null) {
                        ShipGridComponent ashore = a.service.grid(shelter);
                        CrewAgent.Travel go = a.travel(shelter, (tx, ty) -> TaskRules.isSafe(ashore, tx, ty));
                        if (go != CrewAgent.Travel.UNREACHABLE) {
                            a.crew.activity = "carrying " + c.name + " aboard " + SHELTER;
                            return Status.RUNNING;
                        }
                    }
                    // No bed to be reached: they are put down where they are, on safe deck
                    if (!medbay && !c.suited && c.def.suitAir > 0f) {
                        a.pos.stop();
                        a.crew.activity = "putting a suit on " + c.name;
                        suiting += a.dt;
                        if (suiting < a.crew.def.suitTime) return Status.RUNNING;
                        c.setSuited(true);
                    }
                    a.abandonUnreachable();
                    return Status.FAILED;
                }
                a.pos.setPath(a.pathScratch);
            }
            return Status.RUNNING;
        }
    }

    /** Puts the casualty down, completes the rescue and asks for a medic. */
    public static class PutDownCasualty extends CrewLeaf {
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

    // -----------------------------------------------------------------------
    // Carrying goods
    // -----------------------------------------------------------------------

    /**
     * Walks to a bay of the carry task's source craft (across a docked link if need be), or to the
     * tile the load waits by ({@link CarryTask#fromTile}), lifts a load off it and takes it in hand. Fails, ending the trip, when nothing more is to go.
     */
    public static class PickUpLoad extends CrewLeaf {
        private float working;
        private boolean anyBay;

        @Override
        public void start() {
            working = 0f;
            anyBay = false;
        }

        @Override
        public Status execute() {
            CrewAgent a = agent();
            CarryTask t = (CarryTask) a.crew.task;
            if (a.crew.load != null) return Status.SUCCEEDED;
            if (!t.job.isOpen()) return finish(a, t);
            a.crew.activity = "going for " + t.job.commodity();
            CrewAgent.Travel go = t.fromTile ? a.travel(t.from, near(a, t.from, t.x, t.y, 1, 1))
                    : a.travel(t.from, beside(a, t.from, anyBay ? null : t.job.commodity(), true));
            if (go == CrewAgent.Travel.UNREACHABLE && !anyBay && !t.fromTile) {
                // No way to the bays it is in: work from whichever bay of the hold can be reached
                anyBay = true;
                go = a.travel(t.from, beside(a, t.from, null, true));
            }
            if (go == CrewAgent.Travel.UNREACHABLE) {
                a.abandonUnreachable();
                return Status.FAILED;
            }
            if (go == CrewAgent.Travel.GOING) return Status.RUNNING;
            a.crew.activity = "loading " + t.job.commodity();
            working += a.dt;
            if (working < a.crew.def.loadTime) return Status.RUNNING;
            double units = t.job.take(a.crew.def.carryVolume);
            if (units <= 0) return finish(a, t);
            a.crew.load = new CrewMemberComponent.Load(t, units);
            return Status.SUCCEEDED;
        }

        /** Nothing (more) to carry: the trip is over. */
        private Status finish(CrewAgent a, CarryTask t) {
            ShipTaskBoardComponent board = a.board();
            if (board != null) board.cancel(t);
            else t.status = CrewTask.Status.CANCELLED;
            a.crew.task = null;
            a.crew.activity = "idle";
            return Status.FAILED;
        }
    }

    /** Carries the load to a bay of the task's destination craft and sets it down there. */
    public static class DeliverLoad extends CrewLeaf {
        private float working;
        private boolean anyBay;

        @Override
        public void start() {
            working = 0f;
            anyBay = false;
        }

        @Override
        public Status execute() {
            CrewAgent a = agent();
            CarryTask t = (CarryTask) a.crew.task;
            CrewMemberComponent.Load load = a.crew.load;
            if (load == null) return Status.FAILED;
            if (!t.job.isOpen()) {
                a.service.dropLoad(a.crew);
                return Status.FAILED;
            }
            a.crew.activity = "carrying " + t.job.commodity();
            CrewAgent.Travel go = t.toX >= 0 ? a.travel(t.to, near(a, t.to, t.toX, t.toY, 1, 1))
                    : a.travel(t.to, beside(a, t.to, anyBay ? null : t.job.commodity(), false));
            if (go == CrewAgent.Travel.UNREACHABLE && !anyBay && t.toX < 0) {
                anyBay = true;
                go = a.travel(t.to, beside(a, t.to, null, false));
            }
            if (go == CrewAgent.Travel.UNREACHABLE) {
                a.abandonUnreachable();
                return Status.FAILED;
            }
            if (go == CrewAgent.Travel.GOING) return Status.RUNNING;
            a.crew.activity = "unloading " + t.job.commodity();
            working += a.dt;
            if (working < a.crew.def.loadTime) return Status.RUNNING;
            a.crew.load = null;
            t.job.deliver(load.units);
            return Status.SUCCEEDED;
        }
    }

    /**
     * Walks onto {@link YardTask#ship} to the task's tile (or beside it, for a tile no one can
     * stand on) and works it until its job says it is done.
     */
    public static class WorkOnTile extends CrewLeaf {
        @Override
        public Status execute() {
            CrewAgent a = agent();
            YardTask t = (YardTask) a.crew.task;
            if (!t.job.isOpen()) return Status.FAILED;
            a.crew.activity = "going to " + t.job.what();
            CrewAgent.Travel go = a.travel(t.ship, near(a, t.ship, t.x, t.y, t.w, t.h));
            if (go == CrewAgent.Travel.UNREACHABLE) {
                a.abandonUnreachable();
                return Status.FAILED;
            }
            if (go == CrewAgent.Travel.GOING) return Status.RUNNING;
            a.crew.activity = t.job.what();
            float dt = a.dt * a.crew.workPace();
            a.crew.record().pendingWork += dt;
            return t.job.work(a.crew.def.repairRate * dt) ? Status.SUCCEEDED : Status.RUNNING;
        }
    }

    /** Walks to the spot a door is forced from and works at it until it opens. */
    public static class ForceDoor extends CrewLeaf {
        @Override
        public Status execute() {
            CrewAgent a = agent();
            ForceDoorTask t = (ForceDoorTask) a.crew.task;
            if (!t.job.isOpen()) {
                ShipTaskBoardComponent board = a.board();
                if (board != null) board.cancel(t);
                a.crew.task = null;
                return Status.FAILED;
            }
            a.crew.activity = "going to " + t.job.what();
            ShipGridComponent grid = a.service.grid(t.ship);
            if (grid == null) return Status.FAILED;
            GridNavGraph nav = a.service.pathfinder(grid).graph;
            CrewAgent.Travel go = a.travel(t.ship, (x, y) -> x == t.x && y == t.y && nav.isWalkable(x, y));
            if (go == CrewAgent.Travel.UNREACHABLE) {
                a.abandonUnreachable();
                return Status.FAILED;
            }
            if (go == CrewAgent.Travel.GOING) return Status.RUNNING;
            a.crew.activity = t.job.what();
            float dt = a.dt * a.crew.workPace();
            a.crew.record().pendingWork += dt;
            return t.job.work(a.entity, dt) ? Status.SUCCEEDED : Status.RUNNING;
        }
    }

    /**
     * Goes aboard {@link BoardTask#ship} to the task's tile, suited (no one boards a hostile craft
     * without a suit), and reports arriving to the job.
     */
    public static class Board extends CrewLeaf {
        private float suiting;

        @Override
        public void start() {
            suiting = 0f;
        }

        @Override
        public Status execute() {
            CrewAgent a = agent();
            BoardTask t = (BoardTask) a.crew.task;
            if (!t.job.isOpen()) return Status.FAILED;
            if (!a.crew.suited && a.crew.def.suitAir > 0f) {
                a.pos.stop();
                a.crew.activity = "suiting up to board";
                suiting += a.dt;
                if (suiting < a.crew.def.suitTime) return Status.RUNNING;
                a.crew.setSuited(true);
            }
            a.crew.activity = t.job.what();
            CrewAgent.Travel go = a.travel(t.ship, near(a, t.ship, t.x, t.y, 1, 1));
            if (go == CrewAgent.Travel.UNREACHABLE) {
                a.abandonUnreachable();
                return Status.FAILED;
            }
            if (go == CrewAgent.Travel.GOING) return Status.RUNNING;
            t.job.arrived(a.entity);
            return Status.SUCCEEDED;
        }
    }

    /**
     * Walkable tiles of {@code ship} from which the {@code w} x {@code h} tiles from (x, y) are
     * worked: inside them, or next to one of them.
     */
    static GridPathfinder.TilePredicate near(CrewAgent a, Entity ship, int x, int y, int w, int h) {
        ShipGridComponent grid = a.service.grid(ship);
        if (grid == null) return (px, py) -> false;
        GridNavGraph nav = a.service.pathfinder(grid).graph;
        return (px, py) -> {
            if (!nav.isWalkable(px, py)) return false;
            int dx = px < x ? x - px : px >= x + w ? px - (x + w - 1) : 0;
            int dy = py < y ? y - py : py >= y + h ? py - (y + h - 1) : 0;
            return dx + dy <= 1;
        };
    }

    /**
     * Tiles beside a cargo bay of {@code ship}, open to it, that a carrier works from: beside a hold
     * (bays touching each other) with a bay holding {@code commodity} to pick it up ({@code source}),
     * or one with room for it to set it down; a carrier reaches in past the bays nearest the floor.
     * Any bay if none fits or {@code commodity} is null, and anywhere aboard if the craft has no bays.
     */
    static GridPathfinder.TilePredicate beside(CrewAgent a, Entity ship, String commodity, boolean source) {
        ShipGridComponent grid = a.service.grid(ship);
        CargoHoldComponent hold = ship.getComponent(CargoHoldComponent.class);
        GridNavGraph nav = a.service.pathfinder(grid).graph;
        if (hold == null || hold.bayTileCount() == 0) return nav::isWalkable;
        int n = hold.bayTileCount();
        boolean[] open = new boolean[n];
        boolean any = false;
        for (int i = 0; i < n; i++) {
            open[i] = bayFits(hold, i, commodity, source);
            any |= open[i];
        }
        if (!any) java.util.Arrays.fill(open, true);
        // Spread through touching bays: the whole hold is worked from its edge
        for (boolean grew = any; grew; ) {
            grew = false;
            for (int i = 0; i < n; i++) {
                if (open[i]) continue;
                for (int j = 0; j < n && !open[i]; j++) {
                    if (open[j] && Math.abs(hold.bayX[i] - hold.bayX[j]) + Math.abs(hold.bayY[i] - hold.bayY[j]) == 1) {
                        open[i] = grew = true;
                    }
                }
            }
        }
        return (x, y) -> {
            if (!nav.isWalkable(x, y)) return false;
            for (Side side : Side.values()) {
                int nx = x + side.dx, ny = y + side.dy;
                for (int i = 0; i < n; i++) {
                    if (hold.bayX[i] != nx || hold.bayY[i] != ny) continue;
                    if (open[i] && !grid.isEdgeBlocked(x, y, nx, ny)) return true;
                }
            }
            return false;
        };
    }

    private static boolean bayFits(CargoHoldComponent hold, int i, String commodity, boolean source) {
        if (commodity == null) return true;
        CargoHoldComponent.CargoStack s = hold.stacks[i];
        if (source) return s != null && commodity.equals(s.commodity);
        return s == null || (commodity.equals(s.commodity) && s.volume() < hold.tileVolume - 1e-6);
    }

    // -----------------------------------------------------------------------
    // Idle
    // -----------------------------------------------------------------------

    /**
     * Stands by. Heads home if aboard another craft, and moves off a tile that is not safe to
     * breathe on. At battle stations ({@link ShipTaskBoardComponent#battleSuits}) they suit up and
     * keep the suit on; otherwise a suited crew member takes the suit off after
     * {@link #SUIT_OFF_DELAY} seconds standing by on a safe tile. Never finishes.
     */
    public static class Idle extends CrewLeaf {
        /** Seconds on a safe tile with nothing to do before a suit comes off. */
        public static final float SUIT_OFF_DELAY = 5f;
        private float calm, suiting;

        @Override
        public void start() {
            calm = 0f;
            suiting = 0f;
        }

        @Override
        public Status execute() {
            CrewAgent a = agent();
            ShipGridComponent grid = a.grid();
            Entity home = a.service.homeOf(a.entity);
            // Sheltering aboard a docked craft while nowhere aboard their own is safe: they stay
            boolean sheltering = home != a.pos.ship && a.pos.crossing == null
                    && TaskRules.isSafe(grid, a.pos.tileX(), a.pos.tileY()) && !anywhereSafe(a.service.grid(home));
            if ((home != a.pos.ship || a.pos.crossing != null) && !sheltering) {
                ShipGridComponent homeGrid = a.service.grid(home);
                GridNavGraph nav = homeGrid == null ? null : a.service.pathfinder(homeGrid).graph;
                if (nav != null && a.travel(home, nav::isWalkable) == CrewAgent.Travel.GOING) {
                    a.crew.activity = "heading back";
                    return Status.RUNNING;
                }
            }
            int x = a.pos.tileX(), y = a.pos.tileY();
            // Nobody idles in a docked port's opening: it is the only way across, one tile wide
            if (TaskRules.isSafe(grid, x, y) && onLink(grid, x, y)) {
                if (!a.pos.isMoving() && a.nearest(x, y, (tx, ty) -> TaskRules.isSafe(grid, tx, ty) && !onLink(grid, tx, ty),
                        a.pathScratch)) {
                    a.pos.setPath(a.pathScratch);
                    a.crew.activity = "stepping clear of the port";
                }
                return Status.RUNNING;
            }
            if (TaskRules.isSafe(grid, x, y)) {
                if (!a.pos.isMoving()) a.crew.activity = "idle";
                ShipTaskBoardComponent board = a.board();
                if (board != null && board.battleSuits() && a.crew.def.suitAir > 0f) {
                    // Battle stations: suit up and keep it on
                    calm = 0f;
                    if (!a.crew.suited) {
                        a.pos.stop();
                        a.crew.activity = "suiting up for battle stations";
                        suiting += a.dt;
                        if (suiting >= a.crew.def.suitTime) {
                            a.crew.setSuited(true);
                            a.crew.activity = "idle";
                            suiting = 0f;
                        }
                    }
                    return Status.RUNNING;
                }
                suiting = 0f;
                if (a.crew.suited && !a.pos.isMoving()) {
                    calm += a.dt;
                    if (calm >= SUIT_OFF_DELAY) a.crew.activity = "taking off a suit";
                    if (calm >= SUIT_OFF_DELAY + a.crew.def.suitTime * 0.5f) {
                        a.crew.setSuited(false);
                        a.crew.activity = "idle";
                        calm = 0f;
                    }
                }
                return Status.RUNNING;
            }
            calm = 0f;
            if (!a.pos.isMoving() || a.pos.pathBlocked) {
                if (a.nearest(x, y, (tx, ty) -> TaskRules.isSafe(grid, tx, ty), a.pathScratch)) {
                    a.pos.setPath(a.pathScratch);
                    a.crew.activity = "leaving hazard";
                    return Status.RUNNING;
                }
                // Nowhere aboard is safe (the ship holed past holding, its air gone): they suit up, and
                // go aboard the craft it is docked to, a station's berth say, until it is
                if (!a.crew.suited && a.crew.def.suitAir > 0f) {
                    a.pos.stop();
                    a.crew.activity = "putting on a suit";
                    suiting += a.dt;
                    if (suiting < a.crew.def.suitTime) return Status.RUNNING;
                    a.crew.setSuited(true);
                    suiting = 0f;
                }
                Entity shelter = shelter(a);
                if (shelter == null) return Status.RUNNING;
                ShipGridComponent there = a.service.grid(shelter);
                if (a.travel(shelter, (tx, ty) -> TaskRules.isSafe(there, tx, ty)) == CrewAgent.Travel.GOING) {
                    a.crew.activity = "going aboard " + SHELTER;
                }
            }
            return Status.RUNNING;
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** What crew call the docked craft they shelter aboard. */
    static final String SHELTER = "the docked craft";

    /** True if a crew member could stand somewhere on {@code grid} and breathe. */
    public static boolean anywhereSafe(ShipGridComponent grid) {
        if (grid == null) return false;
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                if (grid.getTile(x, y).holdsAir() && TaskRules.isSafe(grid, x, y)) return true;
            }
        }
        return false;
    }

    /**
     * A craft docked straight to the one the agent is aboard, through an open port, whose own crew
     * are no enemies of theirs and with somewhere aboard safe to breathe: where to go when nowhere
     * aboard is. Null if there is none.
     */
    static Entity shelter(CrewAgent a) {
        ShipGridComponent grid = a.grid();
        if (grid == null) return null;
        for (int i = 0; i < grid.links.size; i++) {
            GridLink l = grid.links.get(i);
            if (l.canCross() && a.service.friendlyTo(a.crew.faction, l.otherShip) && anywhereSafe(l.other)) return l.otherShip;
        }
        return null;
    }

    /** True if (x, y) is the opening of a port docked to another craft. */
    static boolean onLink(ShipGridComponent grid, int x, int y) {
        for (int i = 0; i < grid.links.size; i++) {
            if (grid.links.get(i).x == x && grid.links.get(i).y == y) return true;
        }
        return false;
    }

    /**
     * The hostile to engage: the nearest conscious crew member of another faction the agent can
     * see, aboard the same ship or through the open ports of a craft docked to it. Marines also
     * hunt hostiles that any conscious crewmate of theirs can see (an intruder alert), and path
     * to them.
     */
    static Entity findHostile(CrewAgent a) {
        ShipGridComponent grid = a.grid();
        if (grid == null) return null;
        // Only crew aboard the same ship or a craft docked to it can be seen: gather them once, in
        // crew order, instead of scanning every crew member in the game for each observer.
        Array<Entity> aboard = a.aboardScratch;
        aboard.clear();
        ImmutableArray<Entity> all = a.service.crewEntities();
        boolean docked = grid.links.size > 0;
        for (int i = 0; i < all.size(); i++) {
            Entity e = all.get(i);
            Entity ship = a.service.position(e).ship;
            // Same craft first: looking up a docked craft's grid only matters while docked
            if (ship == a.pos.ship || docked && a.service.joined(a.pos.ship, ship)) aboard.add(e);
        }
        Entity seen = nearestVisibleHostile(a, a.entity, aboard, grid);
        if (seen != null || a.crew.role != CrewRole.MARINE) return seen;
        Entity best = null;
        float bestDist = Float.MAX_VALUE;
        for (int i = 0; i < aboard.size; i++) {
            Entity ally = aboard.get(i);
            if (ally == a.entity) continue;
            CrewMemberComponent c = a.service.crew(ally);
            if (c.faction != a.crew.faction || !c.isConscious() || a.service.position(ally).ship != a.pos.ship) continue;
            Entity h = nearestVisibleHostile(a, ally, aboard, grid);
            if (h == null) continue;
            GridPositionComponent p = a.service.position(h);
            float d = Math.abs(p.x - a.pos.x) + Math.abs(p.y - a.pos.y);
            if (d < bestDist) {
                best = h;
                bestDist = d;
            }
        }
        return best;
    }

    /**
     * The nearest conscious hostile of {@code a}'s faction that {@code observer} can see, within sight
     * range, out of {@code aboard} (the crew on the observer's craft and the craft docked to it).
     */
    private static Entity nearestVisibleHostile(CrewAgent a, Entity observer, Array<Entity> aboard, ShipGridComponent grid) {
        GridPositionComponent op = a.service.position(observer);
        float range = a.service.crew(observer).def.sightRange;
        Entity best = null;
        float bestDist = Float.MAX_VALUE;
        for (int i = 0; i < aboard.size; i++) {
            Entity e = aboard.get(i);
            if (e == observer) continue;
            CrewMemberComponent c = a.service.crew(e);
            if (!a.service.hostile(a.crew, c) || !c.isConscious()) continue;
            GridPositionComponent p = a.service.position(e);
            float d = a.service.distance(op, p);
            if (d > range || d >= bestDist) continue;
            if (!a.service.canSee(op, p)) continue;
            best = e;
            bestDist = d;
        }
        return best;
    }

    static String describe(CrewTask t) {
        switch (t.kind) {
            case EXTINGUISH: return "fighting fire at " + t.x + "," + t.y;
            case SEAL_BREACH: return "sealing breach at " + t.x + "," + t.y;
            case PATCH_LEAK: return "patching a leak at " + t.x + "," + t.y;
            case REPAIR: {
                SubsystemComponent s = ((RepairTask) t).subsystem.getComponent(SubsystemComponent.class);
                return "repairing " + (s == null ? "system" : s.type.name().toLowerCase());
            }
            case RESET_SHORT: {
                Entity e = ((ResetShortTask) t).subsystem;
                SubsystemComponent s = e == null ? null : e.getComponent(SubsystemComponent.class);
                return "resetting a short in the " + (s == null ? "conduit" : s.type.name().toLowerCase().replace('_', ' '));
            }
            case ISOLATE_TANK: return "isolating a leaking tank";
            case RESCUE: return "rescuing a casualty";
            case CARRY: return "carrying " + ((CarryTask) t).job.commodity();
            case YARD: return ((YardTask) t).job.what();
            case FORCE_DOOR: return ((ForceDoorTask) t).job.what();
            case BOARD: return ((BoardTask) t).job.what();
            case TREAT: return "treating a patient";
            case STABILISE: return "stopping a patient's bleeding";
            case EVACUATE: return "the escape pods";
            case MAN_CONSOLE: {
                SubsystemComponent.SubsystemType type = ((ManConsoleTask) t).type();
                return "manning the " + (type == null ? "console" : type.name().toLowerCase());
            }
            default: return t.kind.name().toLowerCase();
        }
    }
}

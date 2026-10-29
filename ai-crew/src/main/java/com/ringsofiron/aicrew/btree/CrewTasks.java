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
import com.ringsofiron.interiorgrid.crew.Suit;
import com.ringsofiron.interiorgrid.Furnishing;
import com.ringsofiron.interiorgrid.SuitLockers;

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
            if (!c.suited || !c.wearsSuits() || c.held || a.time < a.refillBlockedUntil) return fail(a);
            if (!a.refilling) {
                ShipGridComponent grid = a.grid();
                if (grid == null || TaskRules.isBreathable(grid, a.pos.tileX(), a.pos.tileY())) return fail(a);
                if (c.suitAir >= c.suitCapacity() * c.def.suitRefillShare) return fail(a);
            } else if (c.suitAir >= c.suitCapacity()) {
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
     * fills their suit there over {@link CrewDef#suitRefillTime} from its hose, drawing {@link
     * CrewDef#SUIT_OXYGEN_KG} (an EVA suit's pack, {@link Suit#EVA_OXYGEN_KG}). A suit can't come off
     * on air not fit to breathe, so it is filled where it is worn, not swapped at a locker.
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
            a.crew.showProgress(filled, a.crew.def.suitRefillTime);
            if (filled < a.crew.def.suitRefillTime) return Status.RUNNING;
            drawSuitOxygen(beside(grid, supplies, a.pos.tileX(), a.pos.tileY()), a.crew.suitKind);
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

    /**
     * The oxygen tank or working life support aboard {@code ship} that (x, y) stands beside, open to
     * it, with a suit's fill to give; null if none. For the player's figure, which has no tree to
     * run {@link RefillSuit} (round 10).
     */
    public static SubsystemComponent suitOxygenBeside(Entity ship, ShipGridComponent grid, int x, int y) {
        Array<SubsystemComponent> supplies = new Array<>();
        if (grid == null || !oxygenSupplies(ship, supplies)) return null;
        return RefillSuit.beside(grid, supplies, x, y);
    }

    /** Takes a pressure suit's fill of oxygen ({@link CrewDef#SUIT_OXYGEN_KG}) from {@code at}, a tank or life support; nothing if null. */
    public static void drawSuitOxygen(SubsystemComponent at) {
        drawOxygen(at, CrewDef.SUIT_OXYGEN_KG);
    }

    /** Takes the fill of oxygen of a suit of {@code kind} from {@code at}; nothing if null. */
    public static void drawSuitOxygen(SubsystemComponent at, Suit.Kind kind) {
        drawOxygen(at, suitOxygen(kind));
    }

    /** Kilograms of oxygen a suit of {@code kind} takes from empty to full. */
    public static double suitOxygen(Suit.Kind kind) {
        return kind == Suit.Kind.EVA ? Suit.EVA_OXYGEN_KG : CrewDef.SUIT_OXYGEN_KG;
    }

    /** Takes {@code kg} of oxygen from {@code at}, a tank or life support (from the tanks piped to it); nothing if null. */
    public static void drawOxygen(SubsystemComponent at, double kg) {
        if (at == null || kg <= 0) return;
        if (at.type == SubsystemComponent.SubsystemType.TANK) {
            at.contents = Math.max(0, at.contents - kg);
        } else {
            TankSupply.draw(at, Fluid.OXYGEN, kg);
        }
    }

    /**
     * Whether the agent's ship has oxygen to fill a suit from: an oxygen tank holding some, or a
     * working life support with oxygen piped to it (or with no oxygen tanks aboard at all, a layout
     * from before tanks). Adds each to {@code out} when given.
     */
    static boolean oxygenSupplies(CrewAgent a, Array<SubsystemComponent> out) {
        return oxygenSupplies(a.pos.ship, out);
    }

    /** {@link #oxygenSupplies(CrewAgent, Array)} aboard {@code ship}. */
    public static boolean oxygenSupplies(Entity ship, Array<SubsystemComponent> out) {
        ShipSubsystemRegistryComponent registry = ship == null ? null : ship.getComponent(ShipSubsystemRegistryComponent.class);
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
            // Pirate crews put down what they hold for the power (round 10): the task goes back, the best is claimed anew
            if (holding && a.crew.carrying == null && a.board() != null
                    && a.board().yieldsToPower(a.crew.task, a.crew.role, a.pos.tileX(), a.pos.tileY(), a::mayTake)) return Status.FAILED;
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
                a.crew.activity = "going back aboard for " + describe(t);
                CrewAgent.Travel go = a.travel(home, TaskRules.workSpots(t, homeGrid, a.service.pathfinder(homeGrid).graph, a.service));
                if (go == CrewAgent.Travel.UNREACHABLE) {
                    a.abandonUnreachable();
                    return Status.FAILED;
                }
                return Status.RUNNING;
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
     * Dresses the agent for their task before they go to it: fetches a suit from a suit locker and
     * puts it on ({@link SuitErrand#suitUp}) when the task's spot is out of breathable air (a breach,
     * a room losing its air), at battle stations with suits ordered, or for a post whose room is
     * losing its air ({@link TaskRules#suitsUpAtPost}); and hangs a suit up first, off battle stations
     * and in good air, before sitting with a patient, whose care it would slow (round 8). Succeeds at
     * once if they need none, have it on, or wear none. With no suit to be had they go without where
     * the spot has air, and to seal a breach or carry a casualty out of airless air, a dash of
     * seconds on held breath; other work where there is no air is left for a crewmate with a suit.
     */
    public static class SuitUp extends CrewLeaf {
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

    /** Does the work of a repair, seal, extinguish or treat task until it is finished. */
    public static class DoWork extends CrewLeaf {
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
                    a.crew.showProgress(1f - tile.fireIntensity, 1f);
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
                    a.crew.showProgress(s.health, 1f);
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
                case STABILISE: {
                    // Quicker for a medic and for skill; slow with no medical stores left
                    float time = a.crew.def.stabiliseTime * (a.crew.role == CrewRole.MEDIC ? CrewDef.MEDIC_STABILISE : 1f)
                            / CrewRecord.factor(a.crew.record().medicine) / (grid.medicalStores > 0 ? 1f : CrewDef.NO_STORES_CARE);
                    a.crew.showProgress(elapsed, time);
                    if (elapsed < time) return Status.RUNNING;
                    if (grid.medicalStores > 0) grid.medicalStores--;
                    a.service.crew(((StabiliseTask) t).patient).stabilised = true;
                    return Status.SUCCEEDED;
                }
                case MAN_CONSOLE: {
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
     * free medbay bed. With nowhere aboard to carry them to (the whole ship holed), the rescuer carries
     * the casualty to a suit locker instead and puts a suit from it on them there, taking their own
     * suit time, and leaves them for the next try. Round 9: once that suit runs low
     * ({@link CrewDef#suitRefillShare}), they carry the casualty to the ship's oxygen and fill it
     * there, as they would their own ({@link RefillSuit}). The wound survey lost six crew who lay
     * down in a holed ship, suited, until their suits ran dry beside full oxygen tanks.
     */
    public static class CarryToSafety extends CrewLeaf {
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
            if (!oxygenSupplies(a, supplies)) return null;
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
            drawSuitOxygen(RefillSuit.beside(grid, supplies, a.pos.tileX(), a.pos.tileY()), c.suitKind);
            c.suitAir = c.suitCapacity();
            filling = 0f;
            return Status.SUCCEEDED;
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
            a.crew.showProgress(working, a.crew.def.loadTime);
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
            a.crew.showProgress(working, a.crew.def.loadTime);
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
            boolean done = t.job.work(a.crew.def.repairRate * dt);
            a.crew.progress = t.job.progress();
            return done ? Status.SUCCEEDED : Status.RUNNING;
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
            boolean open = t.job.work(a.entity, dt);
            a.crew.progress = t.job.progress(a.entity);
            return open ? Status.SUCCEEDED : Status.RUNNING;
        }
    }

    /**
     * Goes aboard {@link BoardTask#ship} to the task's tile, suited (no one boards a hostile craft
     * without a suit if there is one to be had: a pressure suit from a locker, or an EVA suit), and
     * reports arriving to the job.
     */
    public static class Board extends CrewLeaf {
        @Override
        public Status execute() {
            CrewAgent a = agent();
            BoardTask t = (BoardTask) a.crew.task;
            if (!t.job.isOpen()) return Status.FAILED;
            if (!a.crew.suited && a.crew.wearsSuits() && a.pos.ship == a.service.homeOf(a.entity)
                    && SuitErrand.suitUp(a, true) == Status.RUNNING) {
                if (a.crew.suiting > 0f) a.crew.activity = "suiting up to board";
                return Status.RUNNING;
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
     * breathe on. At battle stations ({@link ShipTaskBoardComponent#battleSuits}) they fetch a
     * pressure suit from a suit locker and keep it on; otherwise a suited crew member takes the suit
     * back to a locker with room for it after {@link #SUIT_OFF_DELAY} seconds standing by on a safe
     * tile aboard their own ship, and keeps it on while none can be reached ({@link SuitErrand}).
     * With nowhere aboard safe they fetch a suit, a pressure suit or an EVA suit, before anything
     * else. Aboard their own ship, out of their suit and with nothing to do, they use its
     * furnishings ({@link Downtime}). Never finishes.
     */
    public static class Idle extends CrewLeaf {
        /** Seconds on a safe tile with nothing to do before a suit goes back to its locker. */
        public static final float SUIT_OFF_DELAY = 5f;
        private float calm;
        /** On the way to hang their suit up, or taking it off. */
        private boolean stowing;
        private final Downtime downtime = new Downtime();

        @Override
        public void start() {
            calm = 0f;
            stowing = false;
            downtime.reset(getObject());
        }

        @Override
        public void end() {
            if (getObject() != null) downtime.stop(getObject());
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
            // Nobody idles in a docked port's opening: it is the only way across, one tile wide; nor in
            // an airlock's chamber while someone wants it to cycle
            if (TaskRules.isSafe(grid, x, y) && (onLink(grid, x, y) || EvaTasks.inAirlock(grid, x, y))) {
                if (!a.pos.isMoving() && a.nearest(x, y, (tx, ty) -> TaskRules.isSafe(grid, tx, ty) && !onLink(grid, tx, ty)
                        && !EvaTasks.inAirlock(grid, tx, ty), a.pathScratch)) {
                    a.pos.setPath(a.pathScratch);
                    a.crew.activity = "stepping clear of the port";
                }
                return Status.RUNNING;
            }
            if (TaskRules.isSafe(grid, x, y)) {
                ShipTaskBoardComponent board = a.board();
                boolean battle = board != null && board.battleSuits() && a.crew.wearsSuits();
                // Downtime: aboard their own ship, out of their suit, with nothing else to do (never a prisoner)
                if (!battle && !a.crew.suited && !a.crew.held && home == a.pos.ship && a.pos.crossing == null) {
                    if (downtime.step(a)) return Status.RUNNING;
                } else if (downtime.using() != null) {
                    downtime.stop(a);
                }
                if (!a.pos.isMoving()) a.crew.activity = "idle";
                if (battle) {
                    // Battle stations: a suit from a locker, kept on
                    calm = 0f;
                    stowing = false;
                    if (!a.crew.suited) SuitErrand.suitUp(a, false);
                    return Status.RUNNING;
                }
                if (!a.crew.suited || home != a.pos.ship || a.pos.crossing != null) {
                    calm = 0f;
                    stowing = false;
                    return Status.RUNNING;
                }
                if (!stowing && !a.pos.isMoving()) {
                    calm += a.dt;
                    stowing = calm >= SUIT_OFF_DELAY && a.time >= a.stowBlockedUntil;
                }
                if (stowing && SuitErrand.stow(a) != Status.RUNNING) {
                    // Hung up; or nowhere to hang it, and kept on for now
                    stowing = false;
                    calm = 0f;
                }
                return Status.RUNNING;
            }
            calm = 0f;
            stowing = false;
            if (downtime.using() != null) downtime.stop(a);
            if (!a.pos.isMoving() || a.pos.pathBlocked) {
                if (a.nearest(x, y, (tx, ty) -> TaskRules.isSafe(grid, tx, ty), a.pathScratch)) {
                    a.pos.setPath(a.pathScratch);
                    a.crew.activity = "leaving hazard";
                    return Status.RUNNING;
                }
                // Nowhere aboard is safe (the ship holed past holding, its air gone): they fetch a suit,
                // and go aboard the craft it is docked to, a station's berth say, until it is
                if (!a.crew.suited && SuitErrand.suitUp(a, true) == Status.RUNNING) return Status.RUNNING;
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
            case REPAIR_RCS: return "repairing an RCS thruster on EVA";
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
                if (type == SubsystemComponent.SubsystemType.TURRET_MOUNT) return "manning a gun";
                return "manning the " + (type == null ? "console" : type.name().toLowerCase());
            }
            default: return t.kind.name().toLowerCase();
        }
    }
}

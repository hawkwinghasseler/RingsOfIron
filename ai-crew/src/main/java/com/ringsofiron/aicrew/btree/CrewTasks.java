package com.ringsofiron.aicrew.btree;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.utils.ImmutableArray;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewRole;
import com.ringsofiron.aicrew.CrewState;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.aicrew.ShipTaskBoardComponent;
import com.ringsofiron.aicrew.TaskRules;
import com.ringsofiron.aicrew.TreatTask;
import com.ringsofiron.aicrew.nav.GridNode;
import com.ringsofiron.aicrew.nav.GridPathfinder;
import com.ringsofiron.aicrew.nav.LineOfSight;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.interiorgrid.crew.CrewDamageSink;
import com.ringsofiron.interiorgrid.crew.CrewTask;
import com.ringsofiron.interiorgrid.crew.RepairTask;
import com.ringsofiron.interiorgrid.crew.RescueTask;

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
            ShipGridComponent grid = a.grid();
            float dist = Math.abs(tp.x - a.pos.x) + Math.abs(tp.y - a.pos.y);
            boolean sight = LineOfSight.canSee(grid, a.pos.tileX(), a.pos.tileY(), tp.tileX(), tp.tileY());
            a.crew.fireCooldown -= a.dt;
            if (sight && dist <= a.crew.def.weaponRange) {
                a.pos.stop();
                a.crew.activity = "shooting " + t.name;
                if (a.crew.fireCooldown <= 0f) {
                    a.crew.fireCooldown = a.crew.def.fireInterval;
                    if (a.service.random.nextFloat() < a.crew.def.hitChance) {
                        a.service.applyDamage(a.target, a.crew.def.weaponDamage, CrewDamageSink.DamageCause.SMALL_ARMS);
                    }
                }
                return Status.RUNNING;
            }
            a.crew.activity = "closing on " + t.name;
            repathTimer -= a.dt;
            if (!a.pos.isMoving() || a.pos.pathBlocked || repathTimer <= 0f) {
                repathTimer = 1f;
                GridPathfinder pf = a.pathfinder();
                if (pf.findPath(a.pos.tileX(), a.pos.tileY(), tp.tileX(), tp.tileY(), a.pathScratch)) {
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
            if (held != null && held.isActive() && held.claimedBy == a.entity) return Status.SUCCEEDED;
            a.crew.task = null;
            ShipTaskBoardComponent board = a.board();
            if (board == null || board.faction != a.crew.faction) return Status.FAILED;
            CrewTask t = board.best(a.crew.role, a.pos.tileX(), a.pos.tileY(), task -> !a.isIgnoring(task));
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
                if (!pf.nearest(x, y, TaskRules.workSpots(t, grid, pf.graph, a.service), a.pathScratch)) {
                    a.abandonUnreachable();
                    return Status.FAILED;
                }
                a.pos.setPath(a.pathScratch);
            }
            return Status.RUNNING;
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
            elapsed += a.dt;
            a.crew.activity = describe(t);
            Tile tile = grid.getTile(t.x, t.y);
            switch (t.kind) {
                case EXTINGUISH:
                    tile.fireIntensity -= a.crew.def.extinguishRate * a.dt;
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
                case REPAIR: {
                    SubsystemComponent s = ((RepairTask) t).subsystem.getComponent(SubsystemComponent.class);
                    s.health = Math.min(1f, s.health + a.crew.def.repairRate * a.dt);
                    if (s.health >= 1f) {
                        grid.gridDirty = true; // a repaired system may route power again
                        return Status.SUCCEEDED;
                    }
                    return Status.RUNNING;
                }
                case TREAT: {
                    CrewMemberComponent p = a.service.crew(((TreatTask) t).patient);
                    p.treatedBy = a.entity;
                    return Status.RUNNING; // CrewHealthSystem heals faster; done when the patient wakes
                }
                default:
                    return Status.FAILED;
            }
        }
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

    /** Carries the casualty to the nearest safe tile. */
    public static class CarryToSafety extends CrewLeaf {
        @Override
        public Status execute() {
            CrewAgent a = agent();
            CrewMemberComponent c = a.service.crew(a.crew.carrying);
            if (c == null || c.state != CrewState.UNCONSCIOUS) return Status.FAILED;
            ShipGridComponent grid = a.grid();
            a.crew.activity = "carrying " + c.name + " to safety";
            int x = a.pos.tileX(), y = a.pos.tileY();
            if (!a.pos.isMoving() && TaskRules.isSafe(grid, x, y)) return Status.SUCCEEDED;
            GridNode dest = a.pos.destination();
            boolean destValid = dest != null && a.pos.isMoving() && !a.pos.pathBlocked && TaskRules.isSafe(grid, dest.x, dest.y);
            if (!destValid) {
                if (!a.pathfinder().nearest(x, y, (tx, ty) -> TaskRules.isSafe(grid, tx, ty), a.pathScratch)) {
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
    // Idle
    // -----------------------------------------------------------------------

    /** Stands by. Moves off a tile that is not safe to breathe on. Never finishes. */
    public static class Idle extends CrewLeaf {
        @Override
        public Status execute() {
            CrewAgent a = agent();
            ShipGridComponent grid = a.grid();
            int x = a.pos.tileX(), y = a.pos.tileY();
            if (TaskRules.isSafe(grid, x, y)) {
                if (!a.pos.isMoving()) a.crew.activity = "idle";
                return Status.RUNNING;
            }
            if (!a.pos.isMoving() || a.pos.pathBlocked) {
                if (a.pathfinder().nearest(x, y, (tx, ty) -> TaskRules.isSafe(grid, tx, ty), a.pathScratch)) {
                    a.pos.setPath(a.pathScratch);
                    a.crew.activity = "leaving hazard";
                }
            }
            return Status.RUNNING;
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * The hostile to engage: the nearest conscious crew member of another faction aboard
     * the same ship that the agent can see. Marines also hunt hostiles that any conscious
     * crewmate of theirs can see (an intruder alert), and path to them.
     */
    static Entity findHostile(CrewAgent a) {
        ShipGridComponent grid = a.grid();
        if (grid == null) return null;
        Entity seen = nearestVisibleHostile(a, a.entity, grid);
        if (seen != null || a.crew.role != CrewRole.MARINE) return seen;
        Entity best = null;
        float bestDist = Float.MAX_VALUE;
        ImmutableArray<Entity> all = a.service.crewEntities();
        for (int i = 0; i < all.size(); i++) {
            Entity ally = all.get(i);
            if (ally == a.entity) continue;
            CrewMemberComponent c = a.service.crew(ally);
            if (c.faction != a.crew.faction || !c.isConscious() || a.service.position(ally).ship != a.pos.ship) continue;
            Entity h = nearestVisibleHostile(a, ally, grid);
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

    /** The nearest conscious hostile of {@code a}'s faction that {@code observer} can see, within sight range. */
    private static Entity nearestVisibleHostile(CrewAgent a, Entity observer, ShipGridComponent grid) {
        GridPositionComponent op = a.service.position(observer);
        float range = a.service.crew(observer).def.sightRange;
        Entity best = null;
        float bestDist = Float.MAX_VALUE;
        ImmutableArray<Entity> all = a.service.crewEntities();
        for (int i = 0; i < all.size(); i++) {
            Entity e = all.get(i);
            if (e == observer) continue;
            CrewMemberComponent c = a.service.crew(e);
            if (c.faction == a.crew.faction || !c.isConscious()) continue;
            GridPositionComponent p = a.service.position(e);
            if (p.ship != op.ship) continue;
            float d = Math.abs(p.x - op.x) + Math.abs(p.y - op.y);
            if (d > range || d >= bestDist) continue;
            if (!LineOfSight.canSee(grid, op.tileX(), op.tileY(), p.tileX(), p.tileY())) continue;
            best = e;
            bestDist = d;
        }
        return best;
    }

    static String describe(CrewTask t) {
        switch (t.kind) {
            case EXTINGUISH: return "fighting fire at " + t.x + "," + t.y;
            case SEAL_BREACH: return "sealing breach at " + t.x + "," + t.y;
            case REPAIR: {
                SubsystemComponent s = ((RepairTask) t).subsystem.getComponent(SubsystemComponent.class);
                return "repairing " + (s == null ? "system" : s.type.name().toLowerCase());
            }
            case RESCUE: return "rescuing a casualty";
            case TREAT: return "treating a patient";
            default: return t.kind.name().toLowerCase();
        }
    }
}

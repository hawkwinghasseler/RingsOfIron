package com.ringsofiron.aicrew.btree;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.utils.ImmutableArray;
import com.ringsofiron.aicrew.CarryTask;
import com.ringsofiron.aicrew.YardTask;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewRole;
import com.ringsofiron.aicrew.CrewState;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.aicrew.ShipTaskBoardComponent;
import com.ringsofiron.aicrew.TaskRules;
import com.ringsofiron.aicrew.TreatTask;
import com.ringsofiron.aicrew.nav.GridNavGraph;
import com.ringsofiron.aicrew.nav.GridNode;
import com.ringsofiron.aicrew.nav.GridPathfinder;
import com.ringsofiron.interiorgrid.CargoHoldComponent;
import com.ringsofiron.interiorgrid.GridRayTraversal;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Side;
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
            boolean sight = GridRayTraversal.hasLineOfSight(grid, a.pos.tileX(), a.pos.tileY(), tp.tileX(), tp.tileY());
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
            if (held != null && held.isActive() && held.claimedBy == a.entity) return Status.SUCCEEDED;
            a.crew.task = null;
            ShipTaskBoardComponent board = a.board();
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
                if (!a.nearest(x, y, (tx, ty) -> TaskRules.isSafe(grid, tx, ty), a.pathScratch)) {
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
     * Walks to a bay of the carry task's source craft (across a docked link if need be), lifts a
     * load off it and takes it in hand. Fails, ending the trip, when nothing more is to go.
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
            CrewAgent.Travel go = a.travel(t.from, beside(a, t.from, anyBay ? null : t.job.commodity(), true));
            if (go == CrewAgent.Travel.UNREACHABLE && !anyBay) {
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
            return t.job.work(a.crew.def.repairRate * a.dt) ? Status.SUCCEEDED : Status.RUNNING;
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
     * breathe on. Never finishes.
     */
    public static class Idle extends CrewLeaf {
        @Override
        public Status execute() {
            CrewAgent a = agent();
            ShipGridComponent grid = a.grid();
            Entity home = a.service.homeOf(a.entity);
            if (home != a.pos.ship || a.pos.crossing != null) {
                ShipGridComponent homeGrid = a.service.grid(home);
                GridNavGraph nav = homeGrid == null ? null : a.service.pathfinder(homeGrid).graph;
                if (nav != null && a.travel(home, nav::isWalkable) == CrewAgent.Travel.GOING) {
                    a.crew.activity = "heading back";
                    return Status.RUNNING;
                }
            }
            int x = a.pos.tileX(), y = a.pos.tileY();
            if (TaskRules.isSafe(grid, x, y)) {
                if (!a.pos.isMoving()) a.crew.activity = "idle";
                return Status.RUNNING;
            }
            if (!a.pos.isMoving() || a.pos.pathBlocked) {
                if (a.nearest(x, y, (tx, ty) -> TaskRules.isSafe(grid, tx, ty), a.pathScratch)) {
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
            if (!a.service.hostile(a.crew, c) || !c.isConscious()) continue;
            GridPositionComponent p = a.service.position(e);
            if (p.ship != op.ship) continue;
            float d = Math.abs(p.x - op.x) + Math.abs(p.y - op.y);
            if (d > range || d >= bestDist) continue;
            if (!GridRayTraversal.hasLineOfSight(grid, op.tileX(), op.tileY(), p.tileX(), p.tileY())) continue;
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
            case CARRY: return "carrying " + ((CarryTask) t).job.commodity();
            case YARD: return ((YardTask) t).job.what();
            case TREAT: return "treating a patient";
            default: return t.kind.name().toLowerCase();
        }
    }
}

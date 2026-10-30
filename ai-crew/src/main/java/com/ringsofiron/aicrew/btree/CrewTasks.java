package com.ringsofiron.aicrew.btree;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.aicrew.CarryTask;
import com.ringsofiron.aicrew.YardTask;
import com.ringsofiron.aicrew.CrewDef;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewRole;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.aicrew.ForceDoorTask;
import com.ringsofiron.aicrew.BoardTask;
import com.ringsofiron.aicrew.ManConsoleTask;
import com.ringsofiron.aicrew.MedbayCarryTask;
import com.ringsofiron.aicrew.ShipTaskBoardComponent;
import com.ringsofiron.aicrew.TaskRules;
import com.ringsofiron.aicrew.nav.GridNavGraph;
import com.ringsofiron.aicrew.nav.GridPathfinder;
import com.ringsofiron.interiorgrid.CargoHoldComponent;
import com.ringsofiron.interiorgrid.GridLink;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Side;
import com.ringsofiron.interiorgrid.TankSupply;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.Fluid;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.crew.CrewTask;
import com.ringsofiron.interiorgrid.crew.RepairTask;
import com.ringsofiron.interiorgrid.crew.ResetShortTask;
import com.ringsofiron.interiorgrid.crew.Suit;

/**
 * What the leaf tasks of the crew behavior tree share: suit oxygen, where to stand for a job,
 * shelter and hostiles in sight. Each leaf task is its own class in this package; see {@link
 * CrewBehaviorTrees} for how they fit together.
 */
public final class CrewTasks {

    private CrewTasks() {}

    // -----------------------------------------------------------------------
    // Suit oxygen
    // -----------------------------------------------------------------------

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

    // -----------------------------------------------------------------------
    // Tasks
    // -----------------------------------------------------------------------

    /** True while an open {@link MedbayCarryTask} on {@code board} waits for {@code patient}. */
    static boolean carryWaits(ShipTaskBoardComponent board, Entity patient) {
        if (board == null) return false;
        for (CrewTask o : board.tasks) {
            if (o.status == CrewTask.Status.OPEN && o instanceof MedbayCarryTask && ((MedbayCarryTask) o).casualty == patient) return true;
        }
        return false;
    }

    // -----------------------------------------------------------------------
    // Where to stand
    // -----------------------------------------------------------------------

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
            case REPAIR_RCS: case PATCH_HULL: case FREE_COLLAR: case MOUNT_PART: case RETRIEVE: return EvaTasks.doing(t);
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
